package com.horsecontrol.eloquestre.services.profile;

import com.horsecontrol.eloquestre.dto.ProfileDto;
import com.horsecontrol.eloquestre.enums.account.AccountType;
import com.horsecontrol.eloquestre.enums.account.ContractType;
import com.horsecontrol.eloquestre.enums.account.Radius;
import com.horsecontrol.eloquestre.models.people.CityRef;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.repositories.UserRepository;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Perfil do usuário (GET/PATCH /me), perfis públicos e busca de profissionais.
 * Regras equivalentes às do modo demonstração do app (src/lib/backend/demo/api.ts).
 */
@Service
public class ProfileService {

    private static final int PAGE_SIZE = 20;
    /** Teto de candidatos lidos do banco antes do filtro fino por distância. */
    private static final int MAX_CANDIDATES = 1000;
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.]{3,30}$");
    private static final Set<String> CITY_KEYS = Set.of("city_ibge", "city_name", "state", "lat", "lng");

    private final UserRepository userRepository;
    private final MongoTemplate mongo;
    private final BlockService blocks;

    public ProfileService(UserRepository userRepository, MongoTemplate mongo, BlockService blocks) {
        this.userRepository = userRepository;
        this.mongo = mongo;
        this.blocks = blocks;
    }

    // ------------------------------------------------------------ /me
    public ProfileDto getMe(String userId) {
        return ProfileDto.mine(requireUser(userId));
    }

    /**
     * Atualização parcial: só as chaves enviadas mudam (null apaga o campo quando ele é opcional).
     * Campos do servidor (plan, rating_*, verified, id, created_at) são ignorados.
     */
    public ProfileDto updateMe(String userId, Map<String, Object> patch) {
        User u = requireUser(userId);

        for (Map.Entry<String, Object> e : patch.entrySet()) {
            Object v = e.getValue();
            switch (e.getKey()) {
                case "account_type" -> u.setAccountType(parseEnum(v, AccountType::fromJson, "account_type"));
                case "first_name" -> u.setFirstName(text(v, 60, "first_name", ""));
                case "last_name" -> u.setLastName(text(v, 60, "last_name", ""));
                case "username" -> u.setUsername(username(v, userId));
                case "avatar_url" -> u.setAvatarUrl(text(v, 2000, "avatar_url", null));
                case "bio" -> u.setBio(text(v, 600, "bio", null));
                case "occupation_id" -> u.setOccupationId(text(v, 60, "occupation_id", null));
                case "other_occupations" -> u.setOtherOccupations(new ArrayList<>(strings(v, "other_occupations")));
                case "experience_years" -> u.setExperienceYears(intRange(v, 0, 80, "experience_years"));
                case "work_radius_km" -> u.setWorkRadiusKm(radius(v, false, "work_radius_km"));
                case "service_radius_km" -> u.setServiceRadiusKm(radius(v, true, "service_radius_km"));
                case "willing_to_relocate" -> u.setWillingToRelocate(bool(v, "willing_to_relocate"));
                case "available" -> u.setAvailable(bool(v, "available"));
                case "offers_services" -> u.setOffersServices(bool(v, "offers_services"));
                case "onboarded" -> u.setOnboarded(bool(v, "onboarded"));
                case "contract_types" -> {
                    Set<ContractType> set = new LinkedHashSet<>();
                    for (String s : strings(v, "contract_types")) set.add(parseEnum(s, ContractType::fromJson, "contract_types"));
                    u.setContractTypes(set);
                }
                case "nationality" -> u.setNationality(text(v, 60, "nationality", null));
                case "languages" -> u.setLanguages(new ArrayList<>(strings(v, "languages")));
                default -> { /* cidade tratada abaixo; campos do servidor e desconhecidos são ignorados */ }
            }
        }

        if (patch.keySet().stream().anyMatch(CITY_KEYS::contains)) {
            u.setCity(city(patch, u.getCity()));
        }
        if (u.isOnboarded()) validateOnboarded(u);

        try {
            return ProfileDto.mine(userRepository.save(u));
        } catch (DuplicateKeyException e) {
            throw bad(HttpStatus.CONFLICT, "Este nome de usuário já está em uso.");
        }
    }

    // ------------------------------------------------------------ perfis públicos
    public ProfileDto getPublic(String id) {
        return userRepository.findById(id).filter(User::isOnboarded).map(ProfileDto::card)
                .orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Perfil não encontrado."));
    }

    public ProfileDto getByUsername(String username) {
        return userRepository.findByUsername(username.toLowerCase()).filter(User::isOnboarded).map(ProfileDto::card)
                .orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Perfil não encontrado."));
    }

    public boolean usernameAvailable(String username, String userId) {
        String u = username.toLowerCase();
        return USERNAME.matcher(u).matches() && !userRepository.existsByUsernameAndIdNot(u, userId);
    }

    // ------------------------------------------------------------ busca de profissionais
    public record PeopleSearch(Double lat, Double lng, Integer radius, String occupation,
                               boolean servicesOnly, boolean includeRelocate, String query, int page) {
    }

    /**
     * Profissionais com cadastro concluído, exceto o próprio usuário.
     * Com lat/lng: entra quem aceita mudar (se includeRelocate) ou quem está a uma distância
     * ≤ min(raio da busca, max(raio de trabalho, raio de atendimento)) do ponto.
     * Ordem: distância, nota, mais recentes.
     */
    public List<ProfileDto> searchProfessionals(String meId, PeopleSearch p) {
        boolean geo = p.lat() != null && p.lng() != null;
        int radius = p.radius() != null && p.radius() > 0 ? Math.min(p.radius(), 500) : 200;

        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("accountType").is(AccountType.PROFESSIONAL));
        and.add(Criteria.where("onboarded").is(true));
        if (meId != null) {
            Set<String> excluded = new java.util.HashSet<>(blocks.hiddenFor(meId)); // bloqueios nos dois sentidos
            excluded.add(meId);
            and.add(Criteria.where("id").nin(excluded));
        }
        if (hasText(p.occupation())) {
            and.add(new Criteria().orOperator(
                    Criteria.where("occupationId").is(p.occupation()),
                    Criteria.where("otherOccupations").is(p.occupation())));
        }
        if (p.servicesOnly()) and.add(Criteria.where("offersServices").is(true));
        if (hasText(p.query())) {
            Pattern rx = Pattern.compile(Pattern.quote(p.query().trim()), Pattern.CASE_INSENSITIVE);
            and.add(new Criteria().orOperator(
                    Criteria.where("firstName").regex(rx),
                    Criteria.where("lastName").regex(rx),
                    Criteria.where("username").regex(rx)));
        }
        if (geo) {
            Circle area = new Circle(new Point(p.lng(), p.lat()), new Distance(radius, Metrics.KILOMETERS));
            Criteria near = Criteria.where("city.location").withinSphere(area);
            and.add(p.includeRelocate()
                    ? new Criteria().orOperator(near, Criteria.where("willingToRelocate").is(true))
                    : near);
        }

        Query query = new Query(new Criteria().andOperator(and)).limit(MAX_CANDIDATES);
        List<User> rows = mongo.find(query, User.class);
//jhgyghghyghjhgyghjhhg
        record Hit(User user, double distance) {
        }
        List<Hit> hits = new ArrayList<>();
        for (User u : rows) {
            double d = geo ? distanceTo(u, p.lat(), p.lng()) : Double.POSITIVE_INFINITY;
            if (geo && !(p.includeRelocate() && u.isWillingToRelocate())) {
                int own = Math.max(km(u.getWorkRadiusKm()), km(u.getServiceRadiusKm()));
                if (Double.isInfinite(d) || d > Math.min(radius, own)) continue;
            }
            hits.add(new Hit(u, d));
        }

        hits.sort(Comparator.<Hit>comparingDouble(Hit::distance)
                .thenComparing(h -> h.user().getRatingAvg(), Comparator.reverseOrder())
                .thenComparing(h -> createdAt(h.user()), Comparator.reverseOrder()));

        int from = Math.max(0, p.page()) * PAGE_SIZE;
        return hits.stream().skip(from).limit(PAGE_SIZE).map(h -> ProfileDto.card(h.user())).toList();
    }

    // ------------------------------------------------------------ auxiliares
    private User requireUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> bad(HttpStatus.UNAUTHORIZED, "not_authenticated"));
    }

    private void validateOnboarded(User u) {
        if (!hasText(u.getFirstName()) || u.getUsername() == null || u.getCity() == null) {
            throw bad(HttpStatus.BAD_REQUEST, "Complete nome, nome de usuário e cidade antes de concluir o cadastro.");
        }
    }

    private String username(Object v, String userId) {
        if (v == null) return null;
        String u = v.toString().trim().toLowerCase();
        if (!USERNAME.matcher(u).matches()) {
            throw bad(HttpStatus.BAD_REQUEST, "Nome de usuário inválido: use 3 a 30 letras minúsculas, números, _ ou .");
        }
        if (userRepository.existsByUsernameAndIdNot(u, userId)) {
            throw bad(HttpStatus.CONFLICT, "Este nome de usuário já está em uso.");
        }
        return u;
    }

    private static CityRef city(Map<String, Object> patch, CityRef current) {
        Object lat = patch.containsKey("lat") ? patch.get("lat") : (current != null && current.getLocation() != null ? current.lat() : null);
        Object lng = patch.containsKey("lng") ? patch.get("lng") : (current != null && current.getLocation() != null ? current.lng() : null);
        if (lat == null || lng == null) return null;
        Object name = patch.containsKey("city_name") ? patch.get("city_name") : (current != null ? current.getCityName() : null);
        Object state = patch.containsKey("state") ? patch.get("state") : (current != null ? current.getState() : null);
        Object ibge = patch.containsKey("city_ibge") ? patch.get("city_ibge") : (current != null ? current.getCityIbge() : null);
        if (name == null || state == null || !state.toString().matches("^[A-Z]{2}$")) {
            throw bad(HttpStatus.BAD_REQUEST, "Cidade inválida.");
        }
        double la = number(lat, "lat").doubleValue();
        double lo = number(lng, "lng").doubleValue();
        if (la < -90 || la > 90 || lo < -180 || lo > 180) throw bad(HttpStatus.BAD_REQUEST, "Coordenadas inválidas.");
        return CityRef.of(ibge != null ? number(ibge, "city_ibge").intValue() : null, name.toString(), state.toString(), la, lo);
    }

    /** Haversine em km até o centro da cidade do usuário. */
    private static double distanceTo(User u, double lat, double lng) {
        CityRef c = u.getCity();
        if (c == null || c.getLocation() == null) return Double.POSITIVE_INFINITY;
        double r = 6371.0;
        double dLat = Math.toRadians(c.lat() - lat);
        double dLng = Math.toRadians(c.lng() - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(c.lat())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private static int km(Radius r) {
        return r != null ? r.km() : 0;
    }

    private static Instant createdAt(User u) {
        return u.getCreatedAt() != null ? u.getCreatedAt() : Instant.EPOCH;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String text(Object v, int max, String field, String ifNull) {
        if (v == null) return ifNull;
        String s = v.toString().trim();
        if (s.length() > max) throw bad(HttpStatus.BAD_REQUEST, field + ": máximo de " + max + " caracteres.");
        return s.isEmpty() ? ifNull : s;
    }

    private static boolean bool(Object v, String field) {
        if (v instanceof Boolean b) return b;
        throw bad(HttpStatus.BAD_REQUEST, field + " deve ser true ou false.");
    }

    private static Number number(Object v, String field) {
        if (v instanceof Number n) return n;
        throw bad(HttpStatus.BAD_REQUEST, field + " deve ser um número.");
    }

    private static Integer intRange(Object v, int min, int max, String field) {
        if (v == null) return null;
        int n = number(v, field).intValue();
        if (n < min || n > max) throw bad(HttpStatus.BAD_REQUEST, field + " deve estar entre " + min + " e " + max + ".");
        return n;
    }

    private static Radius radius(Object v, boolean nullable, String field) {
        if (v == null) {
            if (nullable) return null;
            throw bad(HttpStatus.BAD_REQUEST, field + " é obrigatório.");
        }
        try {
            return Radius.fromKm(number(v, field).intValue());
        } catch (IllegalArgumentException e) {
            throw bad(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static Collection<String> strings(Object v, String field) {
        if (v == null) return List.of();
        if (!(v instanceof Collection<?> list)) throw bad(HttpStatus.BAD_REQUEST, field + " deve ser uma lista.");
        List<String> out = new ArrayList<>();
        for (Object o : list) if (o != null && !o.toString().isBlank()) out.add(o.toString().trim());
        return out;
    }

    private static <T> T parseEnum(Object v, java.util.function.Function<String, T> parser, String field) {
        if (v == null) throw bad(HttpStatus.BAD_REQUEST, field + " é obrigatório.");
        try {
            return parser.apply(v.toString());
        } catch (IllegalArgumentException e) {
            throw bad(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static ResponseStatusException bad(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
