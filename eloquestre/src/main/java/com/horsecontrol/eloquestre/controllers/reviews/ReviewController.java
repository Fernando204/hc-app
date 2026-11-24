package com.horsecontrol.eloquestre.controllers.reviews;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.reviews.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/profiles/{id}/reviews")
    public List<Map<String, Object>> of(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return reviews.of(me, id);
    }

    /** Minha avaliação desta pessoa: {id, rating, comment} ou 204 se não houver. */
    @GetMapping("/profiles/{id}/reviews/mine")
    public ResponseEntity<Map<String, Object>> mine(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        Map<String, Object> r = reviews.mine(me, id);
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/profiles/{id}/can-review")
    public Map<String, Boolean> canReview(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return Map.of("can_review", reviews.canReview(me, id));
    }

    /** PUT /profiles/{id}/reviews {rating 1–5, comment} — cria ou edita. */
    @PutMapping("/profiles/{id}/reviews")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, Object> body) {
        reviews.save(me, id, body);
    }
}
