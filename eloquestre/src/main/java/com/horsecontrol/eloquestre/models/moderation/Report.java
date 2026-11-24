package com.horsecontrol.eloquestre.models.moderation;

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
import java.util.Set;

/** Denúncia. Uma por (denunciante, alvo). Analisadas pela equipe em até 24 h (Termos de uso). */
@Document(collection = "reports")
@CompoundIndexes({
        @CompoundIndex(name = "reporter_target_idx", def = "{ 'reporterId': 1, 'targetType': 1, 'targetId': 1 }", unique = true),
        @CompoundIndex(name = "target_idx", def = "{ 'targetType': 1, 'targetId': 1 }")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    public static final Set<String> TARGETS = Set.of("profile", "post", "comment", "job", "service", "message", "review");

    @Id
    private String id;
    private String reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private String details;
    private Instant createdAt;
}
