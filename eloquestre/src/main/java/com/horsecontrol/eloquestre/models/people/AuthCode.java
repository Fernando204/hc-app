package com.horsecontrol.eloquestre.models.people;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Código de uso único enviado por e-mail (recuperar senha / confirmar e-mail).
 * Guarda só o hash SHA-256; o Mongo apaga sozinho os vencidos (índice TTL).
 */
@Document(collection = "auth_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCode {

    public static final String RESET = "reset";
    public static final String CONFIRM = "confirm";

    @Id
    private String id;
    @Indexed(unique = true)
    private String codeHash;
    private String userId;
    private String purpose;
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
