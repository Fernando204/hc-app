package com.horsecontrol.eloquestre.models.feed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Curtida: uma por (publicação, usuário). */
@Document(collection = "post_likes")
@CompoundIndex(name = "post_user_idx", def = "{ 'postId': 1, 'userId': 1 }", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLike {
    @Id
    private String id;
    private String postId;
    private String userId;
    private Instant createdAt;
}
