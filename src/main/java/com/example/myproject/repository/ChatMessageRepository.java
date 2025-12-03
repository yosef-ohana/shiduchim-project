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

    // כל ההודעות בין שני משתמשים (ללא תלות בכיוון)
    List<ChatMessage> findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(Long senderId, Long recipientId);
    List<ChatMessage> findByRecipient_IdAndSender_IdOrderByCreatedAtAsc(Long recipientId, Long senderId);

    // שליפה דו-כיוונית
    default List<ChatMessage> findConversation(Long userA, Long userB) {
        List<ChatMessage> a = findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(userA, userB);
        List<ChatMessage> b = findByRecipient_IdAndSender_IdOrderByCreatedAtAsc(userA, userB);
        a.addAll(b);
        a.sort((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));
        return a;
    }


    // ============================================================
    // 🔵 2. שליפות לפי Match (צ'אט מלא אחרי אישור / Match)
    // ============================================================

    // כל ההודעות שקשורות למץ' מסוים
    List<ChatMessage> findByMatch_IdOrderByCreatedAtAsc(Long matchId);

    // הודעות חדשות לצורך unreadCounter והתרעות
    List<ChatMessage> findByMatch_IdAndReadFalse(Long matchId);

    // הודעות שנוצרו אחרי זמן מסוים (לסנכרון WebSocket)
    List<ChatMessage> findByMatch_IdAndCreatedAtAfter(Long matchId, LocalDateTime time);


    // ============================================================
    // 🔵 3. הודעה ראשונית (Opening Message — סעיף 1,10,11)
    // ============================================================

    // שליפת הודעה ראשונית יחידה בין שני משתמשים
    List<ChatMessage> findBySender_IdAndRecipient_IdAndOpeningMessageTrue(Long senderId, Long recipientId);

    // האם קיימת כבר הודעה ראשונית בין הצדדים?
    boolean existsBySender_IdAndRecipient_IdAndOpeningMessageTrue(Long senderId, Long recipientId);

    // הודעות ראשוניות ממתינות לאישור
    List<ChatMessage> findByRecipient_IdAndOpeningMessageTrueAndReadFalse(Long recipientId);


    // ============================================================
    // 🔵 4. תמיכה בקונטקסט חתונה (Wedding Context — סעיף 13)
    // ============================================================

    // כל הודעות החתונה (לדוגמה: LIVE chat overlays בעתיד)
    List<ChatMessage> findByWedding_Id(Long weddingId);

    // הודעות בחתונה בזמן חי (לסטטיסטיקות)
    List<ChatMessage> findByWedding_IdAndCreatedAtBetween(
            Long weddingId,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 5. הודעות אחרונות — רשימת צ'אטים (סעיף 5)
    // ============================================================

    // כל ההודעות שמשתמש מעורב בהן כ־Sender
    List<ChatMessage> findBySender_Id(Long senderId);

    // כל ההודעות שמשתמש מעורב בהן כ־Recipient
    List<ChatMessage> findByRecipient_Id(Long recipientId);

    // הודעה אחרונה בין שני משתמשים (למיון הרשימה)
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
    // 🔵 6. Unread Messages — (סעיף 3)
    // ============================================================

    // כל ההודעות שלא נקראו אצל משתמש מסוים
    List<ChatMessage> findByRecipient_IdAndReadFalse(Long recipientId);

    // שיחות שיש בהן הודעות שלא נקראו (לצורך הצגת bubble)
    List<ChatMessage> findByRecipient_IdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

    // ספירת הודעות לא נקראו
    long countByRecipient_IdAndReadFalse(Long recipientId);


    // ============================================================
    // 🔵 7. Mark As Read — תמיכה מלאה (סעיף 4)
    // ============================================================

    // שליפת כל ההודעות של שיחה מסוימת שטרם נקראו
    List<ChatMessage> findBySender_IdAndRecipient_IdAndReadFalse(Long senderId, Long recipientId);

    // סימון הודעות נקראו יתבצע ב-Service, לא כאן.


    // ============================================================
    // 🔵 8. שליפות לפי Status — System Messages / flagged / deleted
    // ============================================================

    // הודעות מערכת
    List<ChatMessage> findBySystemMessageTrue();

    // הודעות מדווחות
    List<ChatMessage> findByFlaggedTrue();

    // הודעות מחוקות לוגית
    List<ChatMessage> findByDeletedTrue();

    // כל ההודעות שנמחקו לפני זמן מסוים (ל-cleanup)
    List<ChatMessage> findByDeletedTrueAndDeletedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 9. סינון לפי סוג הודעה (Text / Image / File) — סעיף 7
    // ============================================================

    List<ChatMessage> findByMessageType(ChatMessageType type);

    List<ChatMessage> findByMessageTypeAndSender_Id(ChatMessageType type, Long senderId);


    // ============================================================
    // 🔵 10. לשליפת הודעות לפי conversationId (תמיכה עתידית ב-threading)
    // ============================================================

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<ChatMessage> findByConversationIdAndDeletedFalseOrderByCreatedAtAsc(Long conversationId);


    // ============================================================
    // 🔵 11. תמיכה מלאה ב-WebSocket / אינקרמנטים / סנכרון (סעיף 6)
    // ============================================================

    // כל ההודעות שנוצרו אחרי timestamp, לכל משתמש
    List<ChatMessage> findByRecipient_IdAndCreatedAtAfter(Long userId, LocalDateTime time);

    // סנכרון typing… ו־delivered דרך קצב הודעות
    List<ChatMessage> findBySender_IdAndCreatedAtAfter(Long userId, LocalDateTime time);


    // ============================================================
    // 🔵 12. שליפות לפי טווחי תאריכים — סטטיסטיקות
    // ============================================================

    List<ChatMessage> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 13. תמיכה בהתראות NotificationService
    // ============================================================

    // הודעות חדשות לצורך שליחת Push
    List<ChatMessage> findByRecipient_IdAndDeliveredFalse(Long recipientId);

    // הודעות שנוצרו במערכת (SYSTEM) לא נשלחו / לא נמסרו
    List<ChatMessage> findBySystemMessageTrueAndDeliveredFalse();
}