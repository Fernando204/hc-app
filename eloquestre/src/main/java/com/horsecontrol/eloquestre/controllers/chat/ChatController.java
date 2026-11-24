package com.horsecontrol.eloquestre.controllers.chat;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.dto.chat.ChatDtos.ConversationDto;
import com.horsecontrol.eloquestre.dto.chat.ChatDtos.MessageDto;
import com.horsecontrol.eloquestre.dto.chat.ChatDtos.OpenConversationRequest;
import com.horsecontrol.eloquestre.dto.chat.ChatDtos.SendMessageRequest;
import com.horsecontrol.eloquestre.services.chat.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Chat via REST. O tempo real (receber na hora, status, "digitando") é pelo WebSocket /ws;
 * enviar também pode ser pelo WebSocket — estas rotas são o caminho alternativo e o histórico.
 */
@RestController
@RequestMapping("/conversations")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** POST /conversations {peer_id} → {id}. Idempotente por par de usuários. */
    @PostMapping
    public Map<String, String> open(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody OpenConversationRequest req) {
        return Map.of("id", chat.open(me, req.peerId()));
    }

    @GetMapping
    public List<ConversationDto> list(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        return chat.list(me);
    }

    @GetMapping("/{id}/peer")
    public Map<String, String> peer(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return Map.of("peer_id", chat.peer(me, id));
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id,
                                     @RequestParam(required = false) String before) {
        return chat.messages(me, id, before);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id,
                           @RequestBody SendMessageRequest req) {
        return chat.send(me, id, req.body(), req.clientId());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        chat.markRead(me, id);
    }
}
