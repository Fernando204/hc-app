package com.horsecontrol.eloquestre.services.reviews;

import com.horsecontrol.eloquestre.dto.jobs.JobViews;
import com.horsecontrol.eloquestre.models.chat.ChatMessage;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.reviews.Review;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.services.push.PushService;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Avaliações. Só avalia quem teve contato real: a outra pessoa já enviou ao menos
 * uma mensagem para você no chat. A nota (rating_avg/rating_count) do avaliado é recalculada.
 */
@Service
public class ReviewService {

    private final MongoTemplate mongo;
    private final Users users;
    private final BlockService blocks;
    private final PushService push;

    public ReviewService(MongoTemplate mongo, Users users, BlockService blocks, PushService push) {
        this.mongo = mongo;
        this.users = users;
        this.blocks = blocks;
        this.push = push;
    }

    /** Últimas 50, mais novas primeiro (sem as de pessoas bloqueadas). */
    public List<Map<String, Object>> of(String me, String userId) {
        Set<String> hidden = blocks.hiddenFor(me);
        List<Review> rows = mongo.find(Query.query(Criteria.where("revieweeId").is(userId)).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(50), Review.class)
                .stream().filter(r -> !hidden.contains(r.getReviewerId())).toList();
        Map<String, User> reviewers = users.byIds(rows.stream().map(Review::getReviewerId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Review r : rows) {
            User u = reviewers.get(r.getReviewerId());
            if (u == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("reviewer_id", r.getReviewerId());
            m.put("reviewee_id", r.getRevieweeId());
            m.put("job_id", r.getJobId());
            m.put("rating", r.getRating());
            m.put("comment", r.getComment());
            m.put("created_at", JobViews.iso(r.getCreatedAt()));
            m.put("reviewer_first_name", Users.nz(u.getFirstName()));
            m.put("reviewer_last_name", Users.nz(u.getLastName()));
            m.put("reviewer_username", u.getUsername());
            m.put("reviewer_avatar_url", u.getAvatarUrl());
            out.add(m);
        }
        return out;
    }

    /** true se não é você, não há bloqueio e a outra pessoa já enviou mensagem para você. */
    public boolean canReview(String me, String userId) {
        if (userId == null || userId.equals(me) || users.get(userId) == null) return false;
        if (blocks.blockedBetween(me, userId)) return false;
        return mongo.exists(Query.query(Criteria.where("senderId").is(userId).and("recipientId").is(me)), ChatMessage.class);
    }

    public Map<String, Object> mine(String me, String userId) {
        Review r = mongo.findOne(Query.query(Criteria.where("reviewerId").is(me).and("revieweeId").is(userId)), Review.class);
        if (r == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rating", r.getRating());
        m.put("comment", r.getComment());
        return m;
    }

    /** Cria ou edita a minha avaliação de `userId`. */
    public void save(String me, String userId, Map<String, Object> raw) {
        Body b = Body.of(raw);
        Integer rating = b.optInt("rating", 1, 5);
        if (rating == null) throw Body.bad("Nota inválida.");
        String comment = b.optText("comment", 1000);

        Review existing = mongo.findOne(Query.query(Criteria.where("reviewerId").is(me).and("revieweeId").is(userId)), Review.class);
        if (existing == null && !canReview(me, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só é possível avaliar depois que a pessoa conversar com você.");
        }
        Instant now = Instant.now();
        Review r = existing != null ? existing : Review.builder().reviewerId(me).revieweeId(userId).createdAt(now).build();
        r.setRating(rating);
        r.setComment(comment);
        r.setUpdatedAt(now);
        mongo.save(r);
        recompute(List.of(userId));
        if (existing == null) push.notify(userId, "Você recebeu uma avaliação", push.nameOf(me) + " deu " + "★".repeat(rating), "/profile/" + userId);
    }

    /** Recalcula média (2 casas) e total das pessoas avaliadas. */
    public void recompute(Collection<String> revieweeIds) {
        for (String id : revieweeIds) {
            List<Review> all = mongo.find(Query.query(Criteria.where("revieweeId").is(id)), Review.class);
            double avg = all.isEmpty() ? 0 : Math.round(all.stream().mapToInt(Review::getRating).average().orElse(0) * 100) / 100.0;
            mongo.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().set("ratingAvg", avg).set("ratingCount", all.size()), User.class);
        }
    }
}
