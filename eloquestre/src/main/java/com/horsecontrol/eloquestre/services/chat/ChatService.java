package com.horsecontrol.eloquestre.services.chat;

import com.horsecontrol.eloquestre.dto.chat.ChatDtos.ConversationDto;
import com.horsecontrol.eloquestre.dto.chat.ChatDtos.MessageDto;
import com.horsecontrol.eloquestre.models.chat.ChatMessage;
import com.horsecontrol.eloquestre.models.chat.Conversation;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.realtime.RealtimeHub;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.services.push.PushService;
import com.horsecontrol.eloquestre.repositories.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conversas 1:1 e mensagens. Toda mudança relevante é empurrada em tempo real
 * pelo {@link RealtimeHub} para os participantes conectados:
 *   {"type":"message", "message":{...}}                                  nova mensagem
 *   {"type":"status", "conversation_id", "message_ids":[...], "status", "at"}  recebida / lida
 */
@Service
public class ChatService {

    public static final int PAGE_SIZE = 50;
    public static final int MAX_BODY = 4000;

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final RealtimeHub hub;
    private final BlockService blocks;
    private final PushService push;

    public ChatService(MongoTemplate mongo, UserRepository users, RealtimeHub hub, BlockService blocks, PushService push) {
        this.mongo = mongo;
        this.users = users;
        this.hub = hub;
        this.blocks = blocks;
        this.push = push;
    }

    // ------------------------------------------------------------ conversas
    /** Idempotente: devolve a conversa existente do par ou cria uma nova. */
    public String open(String me, String peerId) {
        if (peerId == null || peerId.isBlank() || peerId.equals(me)) throw error(HttpStatus.BAD_REQUEST, "invalid_peer");
        if (!users.existsById(peerId)) throw error(HttpStatus.NOT_FOUND, "peer_not_found");
        blocks.ensureNotBlocked(me, peerId);

        String key = Conversation.pairKeyOf(me, peerId);
        Conversation existing = mongo.findOne(Query.query(Criteria.where("pairKey").is(key)), Conversation.class);
        if (existing != null) return existing.getId();

        List<String> members = me.compareTo(peerId) < 0 ? List.of(me, peerId) : List.of(peerId, me);
        try {
            return mongo.insert(Conversation.builder().pairKey(key).members(members).build()).getId();
        } catch (DuplicateKeyException e) { // corrida: o outro abriu ao mesmo tempo
            return mongo.findOne(Query.query(Criteria.where("pairKey").is(key)), Conversation.class).getId();
        }
    }

    /** Conversas com ao menos uma mensagem, da mais recente para a mais antiga. */
    public List<ConversationDto> list(String me) {
        markDelivered(me); // abrir a lista = as mensagens chegaram ao aparelho

        List<Conversation> convs = mongo.find(Query.query(Criteria.where("members").is(me).and("lastMessageAt").ne(null))
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt")).limit(200), Conversation.class);
        if (convs.isEmpty()) return List.of();

        java.util.Set<String> hidden = blocks.hiddenFor(me); // conversas com bloqueados somem da lista
        convs = convs.stream().filter(c -> !hidden.contains(c.peerOf(me))).toList();
        if (convs.isEmpty()) return List.of();
        Map<String, User> peers = users.findAllById(convs.stream().map(c -> c.peerOf(me)).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, ChatMessage> lastMessages = mongo.find(Query.query(Criteria.where("id")
                        .in(convs.stream().map(Conversation::getLastMessageId).filter(java.util.Objects::nonNull).toList())), ChatMessage.class)
                .stream().collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        List<ConversationDto> out = new ArrayList<>();
        for (Conversation c : convs) {
            User peer = peers.get(c.peerOf(me));
            if (peer == null) continue; // conta excluída
            ChatMessage last = lastMessages.get(c.getLastMessageId());
            long unread = mongo.count(Query.query(Criteria.where("conversationId").is(c.getId())
                    .and("recipientId").is(me).and("readAt").is(null)), ChatMessage.class);
            out.add(new ConversationDto(c.getId(), peer.getId(), nz(peer.getFirstName()), nz(peer.getLastName()),
                    peer.getUsername(), peer.getAvatarUrl(), c.getLastMessageAt().toString(), c.getLastMessagePreview(),
                    c.getLastMessageSenderId(), last != null ? last.status() : "sent", unread));
        }
        return out;
    }

    public String peer(String me, String conversationId) {
        return requireMember(me, conversationId).peerOf(me);
    }

    // ------------------------------------------------------------ mensagens
    /** 50 por página, mais novas primeiro. `before` = created_at (ISO) da mais antiga já carregada. */
    public List<MessageDto> messages(String me, String conversationId, String before) {
        requireMember(me, conversationId);
        markDelivered(me);
        Criteria c = Criteria.where("conversationId").is(conversationId);
        if (before != null && !before.isBlank()) {
            try {
                c = c.and("createdAt").lt(Instant.parse(before));
            } catch (DateTimeParseException e) {
                throw error(HttpStatus.BAD_REQUEST, "before inválido (use ISO-8601).");
            }
        }
        return mongo.find(Query.query(c).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(PAGE_SIZE), ChatMessage.class)
                .stream().map(MessageDto::of).toList();
    }

    /**
     * Grava e entrega a mensagem. Se o destinatário estiver conectado, ela já nasce "recebida".
     * Reenvio com o mesmo client_id devolve a mensagem já gravada (sem duplicar).
     */
    public MessageDto send(String me, String conversationId, String body, String clientId) {
        Conversation conv = requireMember(me, conversationId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Mensagem vazia.");
        if (text.length() > MAX_BODY) throw error(HttpStatus.BAD_REQUEST, "Mensagem muito longa (máximo de " + MAX_BODY + " caracteres).");

        if (clientId != null && !clientId.isBlank()) {
            ChatMessage dup = mongo.findOne(Query.query(Criteria.where("senderId").is(me).and("clientId").is(clientId)), ChatMessage.class);
            if (dup != null) return MessageDto.of(dup);
        }

        String recipient = conv.peerOf(me);
        blocks.ensureNotBlocked(me, recipient);
        Instant now = Instant.now();
        ChatMessage m = mongo.insert(ChatMessage.builder()
                .conversationId(conversationId).senderId(me).recipientId(recipient)
                .body(text).clientId(blankToNull(clientId)).createdAt(now)
                .deliveredAt(hub.isOnline(recipient) ? now : null)
                .build());

        String preview = text.replaceAll("\\s+", " ");
        mongo.updateFirst(Query.query(Criteria.where("id").is(conversationId)), new Update()
                .set("lastMessageAt", now).set("lastMessageId", m.getId()).set("lastMessageSenderId", me)
                .set("lastMessagePreview", preview.substring(0, Math.min(120, preview.length()))), Conversation.class);

        MessageDto dto = MessageDto.of(m);
        Map<String, Object> event = Map.of("type", "message", "message", dto);
        hub.send(recipient, event);
        hub.send(me, event); // outros aparelhos de quem enviou (e a confirmação via client_id)
        if (!hub.isOnline(recipient)) push.notify(recipient, push.nameOf(me), text, "/chat/" + conversationId);
        return dto;
    }

    /** Marca como recebidas todas as mensagens pendentes para o usuário e avisa quem enviou. */
    public void markDelivered(String me) {
        List<ChatMessage> pending = mongo.find(Query.query(Criteria.where("recipientId").is(me).and("deliveredAt").is(null)), ChatMessage.class);
        if (pending.isEmpty()) return;
        Instant now = Instant.now();
        mongo.updateMulti(Query.query(Criteria.where("id").in(pending.stream().map(ChatMessage::getId).toList())),
                new Update().set("deliveredAt", now), ChatMessage.class);
        notifyStatus(pending, "delivered", now);
    }

    /** Marca como lidas as mensagens recebidas na conversa e avisa quem enviou. */
    public void markRead(String me, String conversationId) {
        requireMember(me, conversationId);
        List<ChatMessage> unread = mongo.find(Query.query(Criteria.where("conversationId").is(conversationId)
                .and("recipientId").is(me).and("readAt").is(null)), ChatMessage.class);
        if (unread.isEmpty()) return;
        Instant now = Instant.now();
        Query q = Query.query(Criteria.where("id").in(unread.stream().map(ChatMessage::getId).toList()));
        mongo.updateMulti(q, new Update().set("readAt", now), ChatMessage.class);
        mongo.updateMulti(Query.query(Criteria.where("id").in(unread.stream().map(ChatMessage::getId).toList()).and("deliveredAt").is(null)),
                new Update().set("deliveredAt", now), ChatMessage.class);
        notifyStatus(unread, "read", now);
        hub.send(me, Map.of("type", "read_sync", "conversation_id", conversationId)); // zera o contador nos outros aparelhos
    }

    /** "Digitando…" — repassa ao outro participante, sem gravar nada. */
    public void typing(String me, String conversationId) {
        Conversation conv = requireMember(me, conversationId);
        hub.send(conv.peerOf(me), Map.of("type", "typing", "conversation_id", conversationId, "user_id", me));
    }

    // ------------------------------------------------------------ auxiliares
    private void notifyStatus(List<ChatMessage> msgs, String status, Instant at) {
        Map<String, Map<String, List<String>>> bySenderAndConv = new LinkedHashMap<>();
        for (ChatMessage m : msgs) {
            bySenderAndConv.computeIfAbsent(m.getSenderId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(m.getConversationId(), k -> new ArrayList<>()).add(m.getId());
        }
        bySenderAndConv.forEach((sender, convs) -> convs.forEach((conv, ids) ->
                hub.send(sender, Map.of("type", "status", "conversation_id", conv, "message_ids", ids,
                        "status", status, "at", at.toString()))));
    }

    private Conversation requireMember(String me, String conversationId) {
        Conversation c = conversationId == null ? null : mongo.findById(conversationId, Conversation.class);
        if (c == null || !c.hasMember(me)) throw error(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        return c;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
