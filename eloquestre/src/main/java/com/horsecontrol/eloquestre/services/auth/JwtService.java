package com.horsecontrol.eloquestre.services.auth;

import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite e valida o JWT de acesso (subject = id do usuário, "tv" = versão do token).
 * Um token só vale se o usuário ainda existe e a versão bate com a dele
 * (trocar a senha ou sair de todos os aparelhos invalida os antigos).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final UserRepository users;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:1440}") long expirationMinutes,
            UserRepository users) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // mínimo 32 caracteres
        this.ttl = Duration.ofMinutes(expirationMinutes);
        this.users = users;
    }

    public Duration ttl() {
        return ttl;
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("tv", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Id do usuário se o token for válido, não vencido, de um usuário que existe e com a versão atual. */
    public Optional<String> userIdFrom(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String id = c.getSubject();
            if (id == null) return Optional.empty();
            Number tv = c.get("tv", Number.class);
            return users.findById(id)
                    .filter(u -> u.getTokenVersion() == (tv != null ? tv.intValue() : 0))
                    .map(User::getId);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
