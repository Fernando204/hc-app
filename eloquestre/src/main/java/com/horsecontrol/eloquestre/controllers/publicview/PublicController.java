package com.horsecontrol.eloquestre.controllers.publicview;

import com.horsecontrol.eloquestre.models.feed.Post;
import com.horsecontrol.eloquestre.models.jobs.Establishment;
import com.horsecontrol.eloquestre.models.jobs.Job;
import com.horsecontrol.eloquestre.models.people.CityRef;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.services.ServiceOffer;
import com.horsecontrol.eloquestre.repositories.UserRepository;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dados públicos (sem login) para as páginas de compartilhamento (share-web).
 * Só o mínimo para a prévia: nada de e-mail, plano, telefone ou coordenadas.
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    private static final CacheControl CACHE = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final MongoTemplate mongo;
    private final UserRepository users;

    public PublicController(MongoTemplate mongo, UserRepository users) {
        this.mongo = mongo;
        this.users = users;
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> post(@PathVariable String id) {
        Post p = mongo.findById(id, Post.class);
        User a = p == null || p.isHidden() ? null : visible(p.getAuthorId());
        if (a == null) throw Body.notFound("Não encontrado.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("caption", p.getCaption());
        m.put("media", p.getMedia().stream().map(x -> Map.of("path", x.getPath(), "type", x.getType())).toList());
        m.put("author_first_name", Users.nz(a.getFirstName()));
        m.put("author_last_name", Users.nz(a.getLastName()));
        return ok(m);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Map<String, Object>> job(@PathVariable String id) {
        Job j = mongo.findById(id, Job.class);
        if (j == null || !"open".equals(j.getStatus()) || visible(j.getEmployerId()) == null) throw Body.notFound("Não encontrado.");
        Establishment e = j.getEstablishmentId() != null ? mongo.findById(j.getEstablishmentId(), Establishment.class) : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("title", j.getTitle());
        place(m, j.getCity());
        m.put("establishment_name", e != null ? e.getName() : null);
        m.put("contract_type", j.getContractType());
        m.put("salary_min", j.getSalaryMin());
        m.put("salary_max", j.getSalaryMax());
        m.put("salary_period", j.getSalaryPeriod());
        return ok(m);
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<Map<String, Object>> service(@PathVariable String id) {
        ServiceOffer s = mongo.findById(id, ServiceOffer.class);
        User p = s == null || !s.isActive() ? null : visible(s.getProviderId());
        if (p == null) throw Body.notFound("Não encontrado.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("radius_km", s.getRadiusKm());
        place(m, s.getCity());
        m.put("provider_first_name", Users.nz(p.getFirstName()));
        m.put("provider_last_name", Users.nz(p.getLastName()));
        return ok(m);
    }

    @GetMapping("/profiles/{id}")
    public ResponseEntity<Map<String, Object>> profile(@PathVariable String id) {
        return profileOf(visible(id));
    }

    @GetMapping("/profiles/by-username/{username}")
    public ResponseEntity<Map<String, Object>> byUsername(@PathVariable String username) {
        return profileOf(users.findByUsername(username.toLowerCase()).filter(User::isOnboarded).orElse(null));
    }

    // ------------------------------------------------------------ auxiliares
    private ResponseEntity<Map<String, Object>> profileOf(User u) {
        if (u == null) throw Body.notFound("Não encontrado.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("account_type", u.getAccountType());
        m.put("first_name", Users.nz(u.getFirstName()));
        m.put("last_name", Users.nz(u.getLastName()));
        m.put("bio", u.getBio());
        m.put("avatar_url", u.getAvatarUrl());
        place(m, u.getCity());
        return ok(m);
    }

    /** Só perfis com cadastro concluído aparecem publicamente. */
    private User visible(String userId) {
        return userId == null ? null : users.findById(userId).filter(User::isOnboarded).orElse(null);
    }

    private static void place(Map<String, Object> m, CityRef c) {
        m.put("city_name", c != null ? c.getCityName() : null);
        m.put("state", c != null ? c.getState() : null);
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CACHE).body(body);
    }
}
