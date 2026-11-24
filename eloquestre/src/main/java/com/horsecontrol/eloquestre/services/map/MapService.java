package com.horsecontrol.eloquestre.services.map;

import com.horsecontrol.eloquestre.enums.account.AccountType;
import com.horsecontrol.eloquestre.models.jobs.Establishment;
import com.horsecontrol.eloquestre.models.jobs.Job;
import com.horsecontrol.eloquestre.models.people.CityRef;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.services.ServiceOffer;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Balões do mapa na área visível (sempre no centro da cidade, nunca o endereço):
 *   servico = 1 por prestador (o serviço ativo mais recente)
 *   pessoa  = profissional disponível sem serviço ativo
 *   empresa = vagas abertas agrupadas por contratante + estabelecimento + cidade
 * Exclui o próprio usuário e bloqueados. Máximo de 300.
 */
@Service
public class MapService {

    public static final int MAX_PINS = 300;
    private static final int MAX_ROWS = 2000;

    private final MongoTemplate mongo;
    private final Users users;
    private final BlockService blocks;

    public MapService(MongoTemplate mongo, Users users, BlockService blocks) {
        this.mongo = mongo;
        this.users = users;
        this.blocks = blocks;
    }

    public record Bounds(double south, double west, double north, double east) {
        /** Filtro por faixa em [lng, lat] do GeoJSON (funciona com ou sem índice geográfico). */
        Criteria criteria(String field) {
            return new Criteria().andOperator(
                    Criteria.where(field + ".coordinates.1").gte(south).lte(north),
                    west <= east
                            ? Criteria.where(field + ".coordinates.0").gte(west).lte(east)
                            // área cruzando o antimeridiano (não acontece no Brasil, mas não quebra)
                            : new Criteria().orOperator(Criteria.where(field + ".coordinates.0").gte(west), Criteria.where(field + ".coordinates.0").lte(east)));
        }
    }

    public List<Map<String, Object>> pins(String me, Bounds b, Set<String> kinds, String occupation) {
        Set<String> excluded = new HashSet<>(blocks.hiddenFor(me));
        if (me != null) excluded.add(me);
        boolean occ = occupation != null && !occupation.isBlank();
        List<Map<String, Object>> pins = new ArrayList<>();

        // prestadores com serviço ativo (usados também para tirar essas pessoas dos balões "pessoa")
        Set<String> providersWithActiveService = new HashSet<>();
        if (kinds.contains("servico") || kinds.contains("pessoa")) {
            Criteria c = Criteria.where("active").is(true).and("providerId").nin(excluded);
            for (ServiceOffer s : mongo.find(Query.query(c), ServiceOffer.class)) providersWithActiveService.add(s.getProviderId());
        }

        if (kinds.contains("servico")) {
            Criteria c = Criteria.where("active").is(true).and("providerId").nin(excluded);
            if (occ) c = c.and("occupationId").is(occupation);
            List<ServiceOffer> rows = mongo.find(Query.query(new Criteria().andOperator(c, b.criteria("city.location")))
                    .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(MAX_ROWS), ServiceOffer.class);
            Map<String, ServiceOffer> latestByProvider = new LinkedHashMap<>();
            for (ServiceOffer s : rows) latestByProvider.putIfAbsent(s.getProviderId(), s);
            Map<String, User> providers = users.byIds(latestByProvider.keySet());
            latestByProvider.forEach((pid, s) -> {
                User p = providers.get(pid);
                if (p == null || !p.isOnboarded()) return;
                Map<String, Object> m = base("servico", p, s.getId(), s.getCity());
                m.put("occupation_id", s.getOccupationId());
                m.put("headline", s.getTitle());
                m.put("radius_km", s.getRadiusKm());
                m.put("price_from", s.getPriceFrom());
                m.put("price_unit", s.getPriceUnit());
                pins.add(m);
            });
        }

        if (kinds.contains("pessoa")) {
            Criteria c = Criteria.where("accountType").is(AccountType.PROFESSIONAL).and("onboarded").is(true).and("available").is(true).and("id").nin(excluded);
            if (occ) c = c.orOperator(Criteria.where("occupationId").is(occupation), Criteria.where("otherOccupations").is(occupation));
            for (User p : mongo.find(Query.query(new Criteria().andOperator(c, b.criteria("city.location"))).limit(MAX_ROWS), User.class)) {
                if (providersWithActiveService.contains(p.getId())) continue; // já aparece como prestador
                Map<String, Object> m = base("pessoa", p, p.getId(), p.getCity());
                m.put("radius_km", p.getWorkRadiusKm() != null ? p.getWorkRadiusKm().km() : null);
                pins.add(m);
            }
        }

        if (kinds.contains("empresa")) {
            Criteria c = Criteria.where("status").is("open").and("employerId").nin(excluded);
            if (occ) c = c.and("occupationId").is(occupation);
            List<Job> jobs = mongo.find(Query.query(new Criteria().andOperator(c, b.criteria("city.location")))
                    .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(MAX_ROWS), Job.class);
            Map<String, List<Job>> groups = new LinkedHashMap<>();
            for (Job j : jobs) {
                String key = j.getEmployerId() + "|" + (j.getEstablishmentId() != null ? j.getEstablishmentId() : "") + "|" + (j.getCity() != null ? j.getCity().getCityName() : "");
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(j);
            }
            Map<String, User> employers = users.byIds(jobs.stream().map(Job::getEmployerId).toList());
            Map<String, Establishment> ests = mongo.find(Query.query(Criteria.where("id").in(jobs.stream().map(Job::getEstablishmentId).filter(java.util.Objects::nonNull).toList())), Establishment.class)
                    .stream().collect(Collectors.toMap(Establishment::getId, Function.identity()));
            for (List<Job> group : groups.values()) {
                Job latest = group.get(0); // já ordenado do mais novo
                User p = employers.get(latest.getEmployerId());
                if (p == null) continue;
                Map<String, Object> m = base("empresa", p, latest.getId(), latest.getCity());
                Establishment est = latest.getEstablishmentId() != null ? ests.get(latest.getEstablishmentId()) : null;
                if (est != null) m.put("name", est.getName());
                m.put("occupation_id", latest.getOccupationId());
                m.put("headline", latest.getTitle());
                m.put("contract_types", group.stream().map(Job::getContractType).collect(Collectors.toCollection(LinkedHashSet::new)));
                m.put("willing_to_relocate", false);
                m.put("salary_min", latest.getSalaryMin());
                m.put("salary_max", latest.getSalaryMax());
                m.put("salary_period", latest.getSalaryPeriod());
                m.put("open_jobs", group.size());
                pins.add(m);
            }
        }

        pins.sort(Comparator.<Map<String, Object>>comparingInt(m -> (Boolean) m.get("verified") ? 0 : 1)
                .thenComparing(m -> -((Number) m.get("rating_count")).intValue())
                .thenComparing(m -> (String) m.get("name")));
        return pins.size() > MAX_PINS ? pins.subList(0, MAX_PINS) : pins;
    }

    /** Campos comuns de MapPin (src/lib/types.ts). */
    private static Map<String, Object> base(String kind, User p, String refId, CityRef c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("user_id", p.getId());
        m.put("ref_id", refId);
        m.put("name", (Users.nz(p.getFirstName()) + " " + Users.nz(p.getLastName())).trim());
        m.put("first_name", Users.nz(p.getFirstName()));
        m.put("last_name", Users.nz(p.getLastName()));
        m.put("avatar_url", p.getAvatarUrl());
        m.put("occupation_id", p.getOccupationId());
        m.put("headline", null);
        m.put("contract_types", p.getContractTypes() != null ? p.getContractTypes() : Set.of());
        m.put("willing_to_relocate", p.isWillingToRelocate());
        m.put("radius_km", null);
        m.put("price_from", null);
        m.put("price_unit", null);
        m.put("salary_min", null);
        m.put("salary_max", null);
        m.put("salary_period", null);
        m.put("open_jobs", 0);
        m.put("city_name", c != null ? c.getCityName() : "");
        m.put("state", c != null ? c.getState() : "");
        m.put("lat", c != null && c.getLocation() != null ? c.lat() : null);
        m.put("lng", c != null && c.getLocation() != null ? c.lng() : null);
        m.put("rating_avg", p.getRatingAvg());
        m.put("rating_count", p.getRatingCount());
        m.put("verified", p.isVerified());
        return m;
    }
}
