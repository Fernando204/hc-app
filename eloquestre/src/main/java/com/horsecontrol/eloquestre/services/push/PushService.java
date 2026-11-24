package com.horsecontrol.eloquestre.services.push;

import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.models.push.PushToken;
import com.horsecontrol.eloquestre.services.internal.Logger;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notificações push pela API da Expo (https://exp.host/--/api/v2/push/send).
 * O envio é assíncrono (não atrasa a resposta da API) e remove os tokens que a Expo
 * diz não existirem mais (DeviceNotRegistered). Desligue com app.push.enabled=false.
 */
@Service
public class PushService {

    private static final String EXPO_URL = "https://exp.host/--/api/v2/push/send";
    private static final Set<String> PLATFORMS = Set.of("ios", "android", "web");

    private final MongoTemplate mongo;
    private final Users users;
    private final boolean enabled;
    private final RestClient http = RestClient.create();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public PushService(MongoTemplate mongo, Users users, @Value("${app.push.enabled:true}") boolean enabled) {
        this.mongo = mongo;
        this.users = users;
        this.enabled = enabled;
    }

    // ------------------------------------------------------------ tokens
    public void register(String me, String token, String platform) {
        if (token == null || !token.matches("^(ExponentPushToken|ExpoPushToken)\\[[^\\]]{10,200}\\]$")) throw Body.bad("Token de push inválido.");
        if (platform == null || !PLATFORMS.contains(platform)) throw Body.bad("platform inválida.");
        mongo.save(PushToken.builder().token(token).userId(me).platform(platform).updatedAt(Instant.now()).build());
    }

    public void removeAll(String me) {
        mongo.remove(Query.query(Criteria.where("userId").is(me)), PushToken.class);
    }

    // ------------------------------------------------------------ envio
    public void notify(String userId, String title, String body, String url) {
        if (!enabled || userId == null) return;
        executor.submit(() -> send(userId, title, body, url));
    }

    /** Nome de exibição para os textos ("Alguém" se não houver). */
    public String nameOf(String userId) {
        User u = users.get(userId);
        String n = u == null ? "" : (Users.nz(u.getFirstName()) + " " + Users.nz(u.getLastName())).trim();
        return n.isEmpty() ? "Alguém" : n;
    }

    @SuppressWarnings("unchecked")
    private void send(String userId, String title, String body, String url) {
        try {
            List<PushToken> tokens = mongo.find(Query.query(Criteria.where("userId").is(userId)), PushToken.class);
            if (tokens.isEmpty()) return;
            List<Map<String, Object>> messages = new ArrayList<>();
            for (PushToken t : tokens) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("to", t.getToken());
                m.put("title", title);
                m.put("body", body.length() > 140 ? body.substring(0, 139) + "…" : body);
                m.put("data", Map.of("url", url));
                m.put("sound", "default");
                messages.add(m);
            }
            Map<String, Object> res = http.post().uri(EXPO_URL).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(messages).retrieve().body(Map.class);
            Object data = res != null ? res.get("data") : null;
            if (data instanceof List<?> results) {
                for (int i = 0; i < results.size() && i < tokens.size(); i++) {
                    if (results.get(i) instanceof Map<?, ?> r && r.get("details") instanceof Map<?, ?> d
                            && "DeviceNotRegistered".equals(d.get("error"))) {
                        mongo.remove(tokens.get(i)); // aparelho desinstalou o app / trocou de token
                    }
                }
            }
        } catch (Exception e) {
            Logger.warn("Push: falha ao enviar para " + userId + ": " + e.getMessage());
        }
    }
}
