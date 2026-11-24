package com.horsecontrol.eloquestre.services.offers;

import com.horsecontrol.eloquestre.dto.jobs.JobViews;
import com.horsecontrol.eloquestre.models.jobs.Job;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.services.ServiceOffer;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Geo;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serviços de prestadores. Na busca, entra quem atende o ponto (distância ≤ raio do serviço). */
@Service
public class ServiceOfferService {

    public static final int PAGE_SIZE = 20;
    /** Maior raio de atendimento possível: limite do filtro grosso no banco. */
    private static final int MAX_RADIUS = 200;

    private final MongoTemplate mongo;
    private final Users users;
    private final BlockService blocks;

    public ServiceOfferService(MongoTemplate mongo, Users users, BlockService blocks) {
        this.mongo = mongo;
        this.users = users;
        this.blocks = blocks;
    }

    public record ServiceSearch(Double lat, Double lng, String occupation, int page) {
    }

    /** Só ativos. Com lat/lng: dentro do raio do serviço; ordem por distância e nota do prestador. */
    public List<Map<String, Object>> search(String me, ServiceSearch p) {
        boolean geo = p.lat() != null && p.lng() != null;
        Set<String> hidden = blocks.hiddenFor(me);
        Criteria c = Criteria.where("active").is(true);
        if (p.occupation() != null && !p.occupation().isBlank()) c = c.and("occupationId").is(p.occupation());
        if (!hidden.isEmpty()) c = c.and("providerId").nin(hidden);
        Query q = Query.query(geo ? new Criteria().andOperator(c, Geo.within("city.location", p.lat(), p.lng(), MAX_RADIUS)) : c)
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(1000);
        List<ServiceOffer> rows = mongo.find(q, ServiceOffer.class);
        if (geo) rows = rows.stream().filter(s -> Geo.km(s.getCity(), p.lat(), p.lng()) <= s.getRadiusKm()).toList();

        Map<String, User> providers = users.byIds(rows.stream().map(ServiceOffer::getProviderId).toList());
        List<ServiceOffer> sorted = new ArrayList<>(rows.stream().filter(s -> providers.containsKey(s.getProviderId())).toList());
        Comparator<ServiceOffer> byRating = Comparator.comparingDouble(s -> -providers.get(s.getProviderId()).getRatingAvg());
        sorted.sort(geo ? Comparator.<ServiceOffer>comparingDouble(s -> Geo.km(s.getCity(), p.lat(), p.lng())).thenComparing(byRating) : byRating);
        return sorted.stream().skip((long) Math.max(0, p.page()) * PAGE_SIZE).limit(PAGE_SIZE)
                .map(s -> card(s, providers.get(s.getProviderId()))).toList();
    }

    /** Serviços de um prestador (os inativos só para ele mesmo). */
    public List<Map<String, Object>> of(String me, String providerId) {
        if (!providerId.equals(me) && blocks.blockedBetween(me, providerId)) return List.of();
        User u = users.get(providerId);
        if (u == null) return List.of();
        Criteria c = Criteria.where("providerId").is(providerId);
        if (!providerId.equals(me)) c = c.and("active").is(true);
        return mongo.find(Query.query(c).with(Sort.by(Sort.Direction.DESC, "createdAt")), ServiceOffer.class)
                .stream().map(s -> card(s, u)).toList();
    }

    public Map<String, Object> get(String me, String id) {
        ServiceOffer s = mongo.findById(id, ServiceOffer.class);
        if (s == null || (!s.getProviderId().equals(me) && blocks.blockedBetween(me, s.getProviderId()))) throw Body.notFound("Serviço não encontrado.");
        User u = users.get(s.getProviderId());
        if (u == null) throw Body.notFound("Serviço não encontrado.");
        return card(s, u);
    }

    public Map<String, Object> save(String me, String id, Map<String, Object> raw) {
        Body b = Body.of(raw);
        ServiceOffer s;
        if (id == null) {
            User u = users.get(me);
            if (u == null || u.isEmployer()) throw Body.forbidden("Só profissionais oferecem serviços.");
            s = ServiceOffer.builder().providerId(me).createdAt(Instant.now()).build();
        } else {
            s = own(me, id);
        }
        boolean create = id == null;
        if (create || b.has("title")) s.setTitle(b.text("title", 120));
        if (create || b.has("description")) s.setDescription(Users.nz(b.optText("description", 3000))); // opcional no app
        if (create || b.has("occupation_id")) s.setOccupationId(b.text("occupation_id", 60));
        if (b.has("price_from")) s.setPriceFrom(b.optMoney("price_from"));
        if (b.has("price_unit")) s.setPriceUnit(b.oneOf("price_unit", Job.PERIODS, false));
        if (b.has("radius_km")) {
            Integer r = b.optInt("radius_km", 1, 500);
            if (r == null || !ServiceOffer.RADII.contains(r)) throw Body.bad("radius_km deve ser 10, 20, 50, 100 ou 200.");
            s.setRadiusKm(r);
        }
        if (b.has("active")) s.setActive(b.bool("active", true));
        s.setCity(b.city(s.getCity(), true));
        ServiceOffer saved = mongo.save(s);
        return plain(saved);
    }

    public void delete(String me, String id) {
        mongo.remove(own(me, id));
    }

    private ServiceOffer own(String me, String id) {
        ServiceOffer s = mongo.findById(id, ServiceOffer.class);
        if (s == null || !s.getProviderId().equals(me)) throw Body.notFound("Serviço não encontrado.");
        return s;
    }

    // ------------------------------------------------------------ JSON (types.ts: Service / ServiceCard)
    public static Map<String, Object> plain(ServiceOffer s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("provider_id", s.getProviderId());
        m.put("occupation_id", s.getOccupationId());
        m.put("title", s.getTitle());
        m.put("description", s.getDescription());
        m.put("price_from", s.getPriceFrom());
        m.put("price_unit", s.getPriceUnit());
        m.put("radius_km", s.getRadiusKm());
        m.put("active", s.isActive());
        m.put("created_at", JobViews.iso(s.getCreatedAt()));
        JobViews.putCity(m, s.getCity());
        return m;
    }

    public static Map<String, Object> card(ServiceOffer s, User p) {
        Map<String, Object> m = plain(s);
        m.put("provider_first_name", Users.nz(p.getFirstName()));
        m.put("provider_last_name", Users.nz(p.getLastName()));
        m.put("provider_username", p.getUsername());
        m.put("provider_avatar_url", p.getAvatarUrl());
        m.put("provider_verified", p.isVerified());
        m.put("provider_rating_avg", p.getRatingAvg());
        m.put("provider_rating_count", p.getRatingCount());
        return m;
    }
}
