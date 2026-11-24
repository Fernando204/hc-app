package com.horsecontrol.eloquestre.models.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Conversa 1:1. Única por par de usuários (pairKey = ids ordenados). */
@Document(collection = "conversations")
@CompoundIndex(name = "members_last_idx", def = "{ 'members': 1, 'lastMessageAt': -1 }")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private String id;

    /** "idA:idB" com os ids em ordem: garante uma conversa por par. */
    @Indexed(unique = true)
    private String pairKey;

    /** Os dois participantes (ordenados). */
    private List<String> members;

    private Instant lastMessageAt;
    private String lastMessageId;
    private String lastMessagePreview;
    private String lastMessageSenderId;

    @CreatedDate
    private Instant createdAt;

    public static String pairKeyOf(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public String peerOf(String userId) {
        return members.get(0).equals(userId) ? members.get(1) : members.get(0);
    }

    public boolean hasMember(String userId) {
        return members != null && members.contains(userId);
    }
}
