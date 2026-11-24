package com.horsecontrol.eloquestre.realtime;

import com.horsecontrol.eloquestre.services.internal.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessões WebSocket abertas por usuário (um usuário pode estar em mais de um aparelho)
 * e envio de eventos JSON para elas.
 */
@Component
public class RealtimeHub {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public RealtimeHub(JsonMapper json) {
        this.json = json;
    }

    /** Registra a sessão e devolve a versão segura para envios concorrentes. */
    public WebSocketSession register(String userId, WebSocketSession raw) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(safe);
        return safe;
    }

    public void unregister(String userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (k, set) -> {
            set.removeIf(s -> s == session || s.getId().equals(session.getId()));
            return set.isEmpty() ? null : set;
        });
    }

    public boolean isOnline(String userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set != null && set.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** Envia o evento para todos os aparelhos conectados do usuário. */
    public void send(String userId, Object event) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return;
        TextMessage msg = new TextMessage(json.writeValueAsString(event));
        for (WebSocketSession s : set) sendTo(s, msg);
    }

    public void sendTo(WebSocketSession session, Object event) {
        sendTo(session, new TextMessage(json.writeValueAsString(event)));
    }

    private void sendTo(WebSocketSession s, TextMessage msg) {
        if (!s.isOpen()) return;
        try {
            s.sendMessage(msg);
        } catch (IOException | IllegalStateException e) {
            Logger.warn("WebSocket: falha ao enviar para a sessão " + s.getId() + ": " + e.getMessage());
        }
    }
}
