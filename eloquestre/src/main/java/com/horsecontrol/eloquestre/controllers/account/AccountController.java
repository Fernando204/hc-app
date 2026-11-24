package com.horsecontrol.eloquestre.controllers.account;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.services.account.AccountService;
import com.horsecontrol.eloquestre.services.internal.Logger;
import com.horsecontrol.eloquestre.services.push.PushService;
import com.horsecontrol.eloquestre.enums.account.Plan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Conta: excluir, tokens de push e webhook de assinatura. */
@RestController
public class AccountController {

    /** Eventos da RevenueCat que deixam a assinatura ativa. */
    private static final Set<String> ACTIVE = Set.of("INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION", "NON_RENEWING_PURCHASE", "SUBSCRIPTION_EXTENDED");

    private final AccountService accounts;
    private final PushService push;
    private final MongoTemplate mongo;
    private final String webhookSecret;

    public AccountController(AccountService accounts, PushService push, MongoTemplate mongo,
                             @Value("${app.revenuecat.webhook-secret:}") String webhookSecret) {
        this.accounts = accounts;
        this.push = push;
        this.mongo = mongo;
        this.webhookSecret = webhookSecret;
    }

    /** DELETE /me — apaga a conta e tudo o que é dela. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        accounts.delete(me);
    }

    /** PUT /me/push-tokens {token, platform} */
    @PutMapping("/me/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerPush(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, String> body) {
        push.register(me, body.get("token"), body.get("platform"));
    }

    @DeleteMapping("/me/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePush(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        push.removeAll(me);
    }

    /**
     * POST /webhooks/revenuecat — a RevenueCat avisa compras/renovações/expirações.
     * Configure na RevenueCat: Authorization = "Bearer <REVENUECAT_WEBHOOK_SECRET>".
     * O app usa o id do usuário (Mongo) como appUserID. O cliente nunca grava o plano.
     */
    @PostMapping("/webhooks/revenuecat")
    public ResponseEntity<String> revenuecat(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @RequestBody Map<String, Object> payload) {
        if (webhookSecret.isBlank() || auth == null
                || !MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8), ("Bearer " + webhookSecret).getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }
        if (!(payload.get("event") instanceof Map<?, ?> event)) return ResponseEntity.ok("ignored: no event");
        String userId = event.get("app_user_id") instanceof String s ? s : null;
        String type = event.get("type") instanceof String s ? s : "";
        if (userId == null || !org.bson.types.ObjectId.isValid(userId)) return ResponseEntity.ok("ignored: anonymous user");

        Update update;
        if (ACTIVE.contains(type)) {
            Instant expires = event.get("expiration_at_ms") instanceof Number n ? Instant.ofEpochMilli(n.longValue()) : null;
            update = new Update().set("plan", Plan.STANDARD).set("planExpiresAt", expires);
        } else if ("EXPIRATION".equals(type)) {
            update = new Update().set("plan", Plan.FREE).set("planExpiresAt", null);
        } else {
            // CANCELLATION / BILLING_ISSUE: o acesso segue até a expiração, que chega como EXPIRATION
            return ResponseEntity.ok("ignored: " + type);
        }
        long n = mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), update, User.class).getMatchedCount();
        Logger.info("RevenueCat " + type + " para " + userId + (n == 0 ? " (usuário não encontrado)" : ""));
        return ResponseEntity.ok("ok");
    }
}
