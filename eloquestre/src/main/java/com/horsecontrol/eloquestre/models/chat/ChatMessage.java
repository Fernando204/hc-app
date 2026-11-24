package com.horsecontrol.eloquestre.models.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mensagem de uma conversa. Status:
 *   enviada  = gravada (deliveredAt e readAt nulos)
 *   recebida = chegou ao aparelho do destinatário (deliveredAt)
 *   lida     = o destinatário abriu a conversa (readAt)
 */
@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "conv_created_idx", def = "{ 'conversationId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "recipient_pending_idx", def = "{ 'recipientId': 1, 'deliveredAt': 1 }"),
        @CompoundIndex(name = "recipient_unread_idx", def = "{ 'recipientId': 1, 'conversationId': 1, 'readAt': 1 }"),
        @CompoundIndex(name = "sender_client_idx", def = "{ 'senderId': 1, 'clientId': 1 }", sparse = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    private String id;

    private String conversationId;
    private String senderId;
    private String recipientId;
    private String body;

    /** Id gerado no aparelho de quem enviou: evita duplicar ao reenviar e liga a resposta à mensagem otimista. */
    private String clientId;

    private Instant createdAt;
    private Instant deliveredAt;
    private Instant readAt;

    public String status() {
        if (readAt != null) return "read";
        if (deliveredAt != null) return "delivered";
        return "sent";
    }
}
