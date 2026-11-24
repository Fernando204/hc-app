package com.horsecontrol.eloquestre.models.push;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Token Expo de um aparelho. O token é o id (um aparelho = um dono por vez). */
@Document(collection = "push_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {
    @Id
    private String token;
    @Indexed
    private String userId;
    private String platform;
    private Instant updatedAt;
}
