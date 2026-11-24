package com.horsecontrol.eloquestre.models.reviews;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Avaliação (1–5). Uma por (avaliador, avaliado); editar substitui. */
@Document(collection = "reviews")
@CompoundIndexes({
        @CompoundIndex(name = "pair_idx", def = "{ 'reviewerId': 1, 'revieweeId': 1 }", unique = true),
        @CompoundIndex(name = "reviewee_created_idx", def = "{ 'revieweeId': 1, 'createdAt': -1 }")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    @Id
    private String id;
    private String reviewerId;
    private String revieweeId;
    private String jobId;
    private int rating;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;
}
