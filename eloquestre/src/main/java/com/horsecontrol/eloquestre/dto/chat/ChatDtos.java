package com.horsecontrol.eloquestre.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.horsecontrol.eloquestre.models.chat.ChatMessage;

/** DTOs do chat no formato do app (src/lib/types.ts), em snake_case. */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record MessageDto(
            String id,
            String conversationId,
            String senderId,
            String body,
            String createdAt,
            String deliveredAt,
            String readAt,
            /** "sent" | "delivered" | "read" */
            String status,
            @JsonInclude(JsonInclude.Include.NON_NULL) String clientId) {

        public static MessageDto of(ChatMessage m) {
            return new MessageDto(m.getId(), m.getConversationId(), m.getSenderId(), m.getBody(),
                    iso(m.getCreatedAt()), iso(m.getDeliveredAt()), iso(m.getReadAt()), m.status(), m.getClientId());
        }
    }

    public record ConversationDto(
            String conversationId,
            String peerId,
            String peerFirstName,
            String peerLastName,
            String peerUsername,
            String peerAvatarUrl,
            String lastMessageAt,
            String lastMessagePreview,
            String lastMessageSenderId,
            String lastMessageStatus,
            long unreadCount) {
    }

    public record OpenConversationRequest(String peerId) {
    }

    public record SendMessageRequest(String body, String clientId) {
    }

    static String iso(java.time.Instant i) {
        return i != null ? i.toString() : null;
    }
}
