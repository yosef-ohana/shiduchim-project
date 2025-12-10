package com.example.myproject.repository;

import com.example.myproject.model.ChatMessage;
import com.example.myproject.model.enums.ChatMessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות — התכתבויות בין משתמשים
    // ============================================================

    List<ChatMessage> findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(Long senderId, Long recipientId);
    List<ChatMessage> findByRecipient_IdAndSender_IdOrderByCreatedAtAsc(Long recipientId, Long senderId);

    default List<ChatMessage> findConversation(Long userA, Long userB) {
        List<ChatMessage> a = findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(userA, userB);
        List<ChatMessage> b = findByRecipient_IdAndSender_IdOrderByCreatedAtAsc(userA, userB);
        a.addAll(b);
        a.sort((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));
        return a;
    }

    // ============================================================
    // 🔵 2. לפי Match (צ'אט הדדי)
    // ============================================================

    List<ChatMessage> findByMatch_IdOrderByCreatedAtAsc(Long matchId);
    List<ChatMessage> findByMatch_IdAndReadFalse(Long matchId);
    List<ChatMessage> findByMatch_IdAndCreatedAtAfter(Long matchId, LocalDateTime time);

    // === חדש: תמיכה מלאה ב-unread per match ===
    long countByMatch_IdAndRecipient_IdAndReadFalse(Long matchId, Long recipientId);


    // ============================================================
    // 🔵 3. הודעה ראשונית (Opening Message)
    // ============================================================

    List<ChatMessage> findBySender_IdAndRecipient_IdAndOpeningMessageTrue(Long senderId, Long recipientId);
    boolean existsBySender_IdAndRecipient_IdAndOpeningMessageTrue(Long senderId, Long recipientId);

    List<ChatMessage> findByRecipient_IdAndOpeningMessageTrueAndReadFalse(Long recipientId);

    // === חדש: OpeningMessage לפי Match ===
    List<ChatMessage> findByMatch_IdAndOpeningMessageTrue(Long matchId);
    boolean existsByMatch_IdAndOpeningMessageTrue(Long matchId);

    // === חדש: OpeningMessage לפי Wedding (LIVE) ===
    List<ChatMessage> findByWedding_IdAndOpeningMessageTrue(Long weddingId);
    List<ChatMessage> findByWedding_IdAndOpeningMessageTrueAndReadFalse(Long weddingId);


    // ============================================================
    // 🔵 4. Wedding Context
    // ============================================================

    List<ChatMessage> findByWedding_Id(Long weddingId);

    List<ChatMessage> findByWedding_IdAndCreatedAtBetween(
            Long weddingId,
            LocalDateTime start,
            LocalDateTime end
    );

    // ============================================================
    // 🔵 5. הודעות אחרונות — רשימת צ'אטים
    // ============================================================

    List<ChatMessage> findBySender_Id(Long senderId);
    List<ChatMessage> findByRecipient_Id(Long recipientId);

    ChatMessage findTopBySender_IdAndRecipient_IdOrderByCreatedAtDesc(Long senderId, Long recipientId);
    ChatMessage findTopByRecipient_IdAndSender_IdOrderByCreatedAtDesc(Long recipientId, Long senderId);

    default ChatMessage findLastMessageBetween(Long userA, Long userB) {
        ChatMessage a = findTopBySender_IdAndRecipient_IdOrderByCreatedAtDesc(userA, userB);
        ChatMessage b = findTopByRecipient_IdAndSender_IdOrderByCreatedAtDesc(userA, userB);
        if (a == null) return b;
        if (b == null) return a;
        return a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b;
    }

    // ============================================================
    // 🔵 6. Unread Messages
    // ============================================================

    List<ChatMessage> findByRecipient_IdAndReadFalse(Long recipientId);
    List<ChatMessage> findByRecipient_IdAndReadFalseOrderByCreatedAtDesc(Long recipientId);
    long countByRecipient_IdAndReadFalse(Long recipientId);

    List<ChatMessage> findBySender_IdAndRecipient_IdAndReadFalse(Long senderId, Long recipientId);


    // ============================================================
    // 🔵 7. System / flagged / deleted
    // ============================================================

    List<ChatMessage> findBySystemMessageTrue();
    List<ChatMessage> findByFlaggedTrue();
    List<ChatMessage> findByDeletedTrue();
    List<ChatMessage> findByDeletedTrueAndDeletedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 8. לפי סוג הודעה
    // ============================================================

    List<ChatMessage> findByMessageType(ChatMessageType type);
    List<ChatMessage> findByMessageTypeAndSender_Id(ChatMessageType type, Long senderId);


    // ============================================================
    // 🔵 9. לפי ConversationId (תמיכה עתידית)
    // ============================================================

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    List<ChatMessage> findByConversationIdAndDeletedFalseOrderByCreatedAtAsc(Long conversationId);


    // ============================================================
    // 🔵 10. WebSocket Sync
    // ============================================================

    List<ChatMessage> findByRecipient_IdAndCreatedAtAfter(Long userId, LocalDateTime time);
    List<ChatMessage> findBySender_IdAndCreatedAtAfter(Long userId, LocalDateTime time);


    // ============================================================
    // 🔵 11. טווחי תאריכים
    // ============================================================

    List<ChatMessage> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 12. התראות (NotificationService)
    // ============================================================

    List<ChatMessage> findByRecipient_IdAndDeliveredFalse(Long recipientId);
    List<ChatMessage> findBySystemMessageTrueAndDeliveredFalse();
}