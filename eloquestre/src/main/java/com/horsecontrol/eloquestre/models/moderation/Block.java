package com.horsecontrol.eloquestre.models.moderation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** "blocker bloqueou blocked". O efeito vale nos dois sentidos (feed, busca, mapa, chat). */
@Document(collection = "blocks")
@CompoundIndex(name = "pair_idx", def = "{ 'blockerId': 1, 'blockedId': 1 }", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block {
    @Id
    private String id;
    private String blockerId;
    @Indexed
    private String blockedId;
    private Instant createdAt;
}
