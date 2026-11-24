package com.horsecontrol.eloquestre.services.auth;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.horsecontrol.eloquestre.enums.account.AccountType;
import com.horsecontrol.eloquestre.models.people.AuthCode;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.repositories.UserRepository;
import com.horsecontrol.eloquestre.support.Body;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Cadastro e login. Devolve um JWT de acesso.
 *
 * As mensagens de erro "User already registered" e "Invalid login credentials"
 * são as que o app já traduz para o usuário (src/lib/format.ts → errorMessage).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService;
    /** Hash descartável: login com e-mail inexistente gasta o mesmo tempo (evita enumerar contas). */
    private final String dummyHash;
    private final MongoTemplate mongo;
    private final MailService mail;
    /** Destino dos links dos e-mails (rota app/auth/callback.tsx). */
    private final String callbackUrl;
    private final SecureRandom random = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofHours(1);

    public AuthService(UserRepository userRepository, JwtService jwtService, MongoTemplate mongo, MailService mail,
                       @Value("${app.auth.callback-url:eloequestre://auth/callback}") String callbackUrl) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.mongo = mongo;
        this.mail = mail;
        this.callbackUrl = callbackUrl;
        this.dummyHash = passwordEncoder.encode("senha-descartavel");
    }

    // ------------------------------------------------------------ casos de uso
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .accountType(request.accountType() != null ? request.accountType() : AccountType.PROFESSIONAL)
                .build();

        try {
            user = userRepository.save(user);
        } catch (DuplicateKeyException e) { // duas requisições simultâneas com o mesmo e-mail
            throw emailTaken();
        }
        sendConfirmation(user); // não bloqueia o login (a conta já entra)
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordOk) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login credentials");
        }
        return buildResponse(user);
    }

    /** "Sair". Com all=true invalida o token em todos os aparelhos. */
    public void logout(String userId, boolean all) {
        if (all) bumpTokenVersion(userId);
    }

    /** Sempre "ok" (não revela se o e-mail existe). Envia o link de nova senha se houver conta. */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) throw Body.bad("Informe o e-mail.");
        userRepository.findByEmail(normalize(email)).ifPresent(u -> {
            String code = newCode(u.getId(), AuthCode.RESET);
            mail.send(u.getEmail(), "Crie uma nova senha - ELOequestre",
                    "Olá!\n\nRecebemos um pedido para trocar a senha da sua conta no ELOequestre.\n"
                            + "Abra este link no celular em que o app está instalado (vale por 1 hora):\n\n"
                            + link(code, "/reset-password")
                            + "\n\nSe não foi você, ignore este e-mail: sua senha continua a mesma.");
        });
    }

    public void resendConfirmation(String email) {
        if (email == null || email.isBlank()) throw Body.bad("Informe o e-mail.");
        userRepository.findByEmail(normalize(email)).filter(u -> !u.isEmailVerified()).ifPresent(this::sendConfirmation);
    }

    /** Troca o código do link (uso único) por uma sessão. Confirmação também marca o e-mail como verificado. */
    public AuthResponse exchange(String code) {
        if (code == null || code.isBlank()) throw Body.bad("Código inválido.");
        AuthCode c = mongo.findAndRemove(Query.query(Criteria.where("codeHash").is(sha256(code))), AuthCode.class);
        if (c == null || c.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este link é inválido ou já expirou. Peça um novo.");
        }
        User u = userRepository.findById(c.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este link é inválido ou já expirou. Peça um novo."));
        if (AuthCode.CONFIRM.equals(c.getPurpose()) && !u.isEmailVerified()) {
            u.setEmailVerified(true);
            u = userRepository.save(u);
        }
        return buildResponse(u);
    }

    /** Nova senha (logado, ex.: depois do link do e-mail). Invalida as outras sessões e devolve um token novo. */
    public AuthResponse resetPassword(String userId, String password) {
        if (password == null || password.length() < 8 || password.length() > 72) throw Body.bad("A senha deve ter entre 8 e 72 caracteres");
        User u = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not_authenticated"));
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setTokenVersion(u.getTokenVersion() + 1);
        mongo.remove(Query.query(Criteria.where("userId").is(userId).and("purpose").is(AuthCode.RESET)), AuthCode.class);
        return buildResponse(userRepository.save(u));
    }

    // ------------------------------------------------------------ internos
    private void sendConfirmation(User u) {
        String code = newCode(u.getId(), AuthCode.CONFIRM);
        mail.send(u.getEmail(), "Confirme seu e-mail - ELOequestre",
                "Bem-vindo ao ELOequestre!\n\nConfirme seu e-mail abrindo este link no celular (vale por 1 hora):\n\n" + link(code, null));
    }

    private void bumpTokenVersion(String userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setTokenVersion(u.getTokenVersion() + 1);
            userRepository.save(u);
        });
    }

    private String newCode(String userId, String purpose) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        mongo.insert(AuthCode.builder().codeHash(sha256(code)).userId(userId).purpose(purpose).expiresAt(Instant.now().plus(CODE_TTL)).build());
        return code;
    }

    private String link(String code, String next) {
        return callbackUrl + "?code=" + code + (next != null ? "&next=" + next : "");
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "User already registered");
    }

    private AuthResponse buildResponse(User user) {
        return new AuthResponse(jwtService.generate(user), "Bearer", jwtService.ttl().toSeconds(), UserSummary.fromEntity(user));
    }

    // ------------------------------------------------------------ DTOs
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String password,
            /** Opcional: o app define o tipo no onboarding. Padrão: PROFESSIONAL. */
            AccountType accountType) {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    public record UserSummary(
            String id,
            String email,
            @JsonProperty("account_type") AccountType accountType,
            boolean onboarded) {

        public static UserSummary fromEntity(User user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getAccountType(), user.isOnboarded());
        }
    }

    public record AuthResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            /** Validade do token em segundos. */
            @JsonProperty("expires_in") long expiresIn,
            UserSummary user) {
    }
}
