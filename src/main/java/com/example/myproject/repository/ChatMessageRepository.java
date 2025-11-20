package com.example.myproject.repository;

import com.example.myproject.model.ChatMessage;      // ישות הודעה
import com.example.myproject.model.Match;            // התאמה
import com.example.myproject.model.User;             // משתמש
import com.example.myproject.model.Wedding;          // חתונה
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // ============================================================
    // 🔵 1. בסיס – לפי שולח / מקבל
    // ============================================================

    List<ChatMessage> findBySender(User sender);                           // כל מה ששלח משתמש
    List<ChatMessage> findByRecipient(User recipient);                     // כל מה שקיבל משתמש

    List<ChatMessage> findBySenderIdAndRecipientIdOrSenderIdAndRecipientId(
            Long senderId, Long recipientId,
            Long recipientId2, Long senderId2
    );                                                                     // שיחה דו-כיוונית מלאה A↔B


    // ============================================================
    // 🔵 2. הודעות לא נקראו
    // ============================================================

    List<ChatMessage> findByRecipientIdAndReadFalse(Long recipientId);     // כל הלא נקראו
    long countByRecipientIdAndReadFalse(Long recipientId);                 // כמות לא נקראו

    List<ChatMessage> findByMatchIdAndRecipientIdAndReadFalse(
            Long matchId, Long recipientId
    );                                                                     // לא נקראו בצ'אט התאמה

    long countByMatchIdAndRecipientIdAndReadFalse(
            Long matchId, Long recipientId
    );                                                                     // ספירה בצ'אט התאמה


    // ============================================================
    // 🔵 3. לפי Match
    // ============================================================

    List<ChatMessage> findByMatch(Match match);                            // לפי אובייקט Match
    List<ChatMessage> findByMatchId(Long matchId);                         // לפי מזהה Match

    List<ChatMessage> findByMatchIdOrderByCreatedAtAsc(Long matchId);      // צ'אט מסודר כרונולוגית


    // ============================================================
    // 🔵 4. לפי חתונה (Wedding Chat)
    // ============================================================

    List<ChatMessage> findByWedding(Wedding wedding);                      // כל צ'אט החתונה
    List<ChatMessage> findByWeddingIdAndSenderId(
            Long weddingId, Long senderId
    );                                                                     // הודעות ששלח משתמש באירוע

    List<ChatMessage> findByWeddingIdOrderByCreatedAtAsc(Long weddingId);  // צ'אט אירוע מלא


    // ============================================================
    // 🔵 5. לפי זמנים (Stats / Cleanup)
    // ============================================================

    List<ChatMessage> findByCreatedAtAfter(LocalDateTime dt);             // אחרי זמן
    List<ChatMessage> findByCreatedAtBefore(LocalDateTime dt);            // לפני זמן


    // ============================================================
    // 🔵 6. תיבת הודעות / Recent Messages
    // ============================================================

    List<ChatMessage> findTop20BySenderIdAndRecipientIdOrderByCreatedAtDesc(
            Long senderId, Long recipientId
    );                                                                     // 20 האחרונות A→B

    ChatMessage findTop1BySenderIdAndRecipientIdOrderByCreatedAtDesc(
            Long senderId, Long recipientId
    );                                                                     // ההודעה האחרונה A→B

    List<ChatMessage> findTop50BySenderIdOrRecipientIdOrderByCreatedAtDesc(
            Long senderId, Long recipientId
    );                                                                     // inbox — 50 הודעות אחרונות


    // ============================================================
    // 🔵 7. חיפוש טקסט בצ'אט
    // ============================================================

    List<ChatMessage> findByContentContainingIgnoreCase(String keyword);   // חיפוש טקסט חופשי


    // ============================================================
    // 🔵 8. Opening Messages
    // ============================================================

    boolean existsBySenderIdAndRecipientIdAndOpeningMessageTrue(
            Long senderId, Long recipientId
    );                                                                     // האם שלח opening קודם?

    List<ChatMessage> findByRecipientIdAndOpeningMessageTrueAndMatchIsNullAndDeletedFalseOrderByCreatedAtDesc(
            Long recipientId
    );                                                                     // הודעות פתיחה שממתינות


    // ============================================================
    // 🔵 9. תמיכה בקבצים (Attachment)
    // ============================================================

    List<ChatMessage> findByAttachmentUrlIsNotNull();                      // הודעות עם קבצים

    List<ChatMessage> findByAttachmentType(String type);                   // image / video / file


    // ============================================================
    // 🔵 10. מחיקה לוגית
    // ============================================================

    List<ChatMessage> findByDeletedTrue();                                 // הודעות שנמחקו
    List<ChatMessage> findByDeletedFalse();                                // הודעות פעילות בלבד

    List<ChatMessage> findByRecipientIdAndDeletedFalse(Long recipientId);  // הודעות שלא נמחקו אצלי


    // ============================================================
    // 🔵 11. System Messages
    // ============================================================

    List<ChatMessage> findBySystemMessageTrue();                           // הודעות מערכת
    List<ChatMessage> findBySystemMessageFalse();                          // הודעות רגילות


    // ============================================================
    // 🔵 12. Delivered (ל־WebSocket)
    // ============================================================

    List<ChatMessage> findByDeliveredFalseAndRecipientId(Long recipientId); // הודעות שלא נמסרו עדיין


    // ============================================================
    // 🔵 13. Flagged (דיווחים / חשוד)
    // ============================================================

    List<ChatMessage> findByFlaggedTrue();                                  // הודעות שסומנו ע"י מודרטור/AI


    // ============================================================
    // 🔵 14. לפי מזהה שיחה (Conversation ID)
    // ============================================================

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long cid);    // grouping of chat threads
}