package com.horsecontrol.eloquestre.realtime;

import com.horsecontrol.eloquestre.services.chat.ChatService;
import com.horsecontrol.eloquestre.services.internal.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Protocolo (JSON, snake_case):
 *
 * cliente → servidor
 *   {"type":"send", "client_id", "conversation_id", "body"}   enviar mensagem
 *   {"type":"read", "conversation_id"}                          marcar conversa como lida
 *   {"type":"typing", "conversation_id"}                        "digitando…"
 *   {"type":"ping"}
 *
 * servidor → cliente
 *   {"type":"ready"}                                            conexão autenticada
 *   {"type":"message", "message":{...}}                         nova mensagem (inclui as próprias, com client_id)
 *   {"type":"status", "conversation_id", "message_ids", "status":"delivered"|"read", "at"}
 *   {"type":"read_sync", "conversation_id"}                     li em outro aparelho
 *   {"type":"typing", "conversation_id", "user_id"}
 *   {"type":"error", "client_id"?, "message"}
 *   {"type":"pong"}
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String SAFE_SESSION = "safeSession";

    private final RealtimeHub hub;
    private final ChatService chat;
    private final JsonMapper json;

    public ChatWebSocketHandler(RealtimeHub hub, ChatService chat, JsonMapper json) {
        this.hub = hub;
        this.chat = chat;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = userId(session);
        WebSocketSession safe = hub.register(userId, session);
        session.getAttributes().put(SAFE_SESSION, safe);
        hub.sendTo(safe, Map.of("type", "ready"));
        chat.markDelivered(userId); // o aparelho voltou a ficar online: o que estava pendente chegou
        Logger.info("WebSocket conectado: usuário " + userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = userId(session);
        WebSocketSession safe = (WebSocketSession) session.getAttributes().get(SAFE_SESSION);
        Map<?, ?> in;
        try {
            in = json.readValue(message.getPayload(), Map.class);
        } catch (JacksonException e) {
            hub.sendTo(safe, Map.of("type", "error", "message", "JSON inválido."));
            return;
        }
        String type = str(in.get("type"));
        String clientId = str(in.get("client_id"));
        String conversationId = str(in.get("conversation_id"));

        try {
            switch (type == null ? "" : type) {
                case "send" -> {
                    // a própria mensagem volta pelo evento "message" (com client_id) enviado pelo ChatService
                    chat.send(userId, conversationId, str(in.get("body")), clientId);
                }
                case "read" -> chat.markRead(userId, conversationId);
                case "typing" -> chat.typing(userId, conversationId);
                case "ping" -> hub.sendTo(safe, Map.of("type", "pong"));
                default -> hub.sendTo(safe, Map.of("type", "error", "message", "Tipo desconhecido: " + type));
            }
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("type", "error");
            err.put("message", e.getReason() != null ? e.getReason() : "Erro");
            if (clientId != null) err.put("client_id", clientId);
            hub.sendTo(safe, err);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object safe = session.getAttributes().get(SAFE_SESSION);
        hub.unregister(userId(session), safe instanceof WebSocketSession s ? s : session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Logger.warn("WebSocket: erro de transporte (" + userId(session) + "): " + exception.getMessage());
    }

    private static String userId(WebSocketSession session) {
        return (String) session.getAttributes().get(WebSocketAuthInterceptor.USER_ID);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
