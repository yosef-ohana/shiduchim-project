package com.example.myproject.repository;

import com.example.myproject.model.Notification;
import com.example.myproject.model.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות לפי משתמש
    // ============================================================

    // כל ההתראות למשתמש מסוים (לפי createdAt)
    List<Notification> findByRelatedUserIdOrderByCreatedAtDesc(Long userId);

    // התראות לא נקראו
    List<Notification> findByRelatedUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    // כמה לא נקראו? (ל-Badge)
    long countByRelatedUserIdAndReadFalse(Long userId);


    // ============================================================
    // 🔵 2. שליפות לפי סוג
    // ============================================================

    // לפי סוג ספציפי (LIKE, MATCH, MESSAGE RECEIVED…)
    List<Notification> findByRelatedUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type
    );

    // מספר התראות מסוג מסוים
    long countByRelatedUserIdAndType(Long userId, NotificationType type);


    // ============================================================
    // 🔵 3. חתונות — סעיפים 7, 8, 18
    // ============================================================

    // התראות שקשורות לחתונה
    List<Notification> findByWeddingIdOrderByCreatedAtDesc(Long weddingId);

    // התראות חתונה שהתרחשו בזמן ספציפי (לפני/אחרי/במהלך LIVE)
    List<Notification> findByWeddingIdAndCreatedAtBetween(
            Long weddingId,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 4. התאמות ו־ChatMessage — סעיף 1, 5, 6, 18
    // ============================================================

    // התראות שקשורות למץ'
    List<Notification> findByMatchId(Long matchId);

    // עבור התראות הודעות
    List<Notification> findByChatMessageId(Long chatMessageId);


    // ============================================================
    // 🔵 5. קטגוריות + מקור מערכת (category / source)
    //     match / chat / wedding / system / profile / ai
    // ============================================================

    List<Notification> findByCategoryOrderByCreatedAtDesc(String category);


    List<Notification> findByRelatedUserIdAndSourceOrderByCreatedAtDesc(
            Long userId, String source
    );


    // ============================================================
    // 🔵 6. עדיפויות — Priority (סעיף 11, 14)
    // ============================================================

    List<Notification> findByRelatedUserIdAndPriorityLevelGreaterThanEqualOrderByCreatedAtDesc(
            Long userId, int minPriority
    );


    // ============================================================
    // 🔵 7. מחיקה / נקראו / ניקוי — סעיף 9, 11
    // ============================================================

    // התראות שנמחקו לוגית (אם נוסיף דגל deleted בהמשך)
    List<Notification> findByRelatedUserIdAndDeletedTrue(Long userId);

    // ניקוי אוטומטי — כל ההתראות הישנות לפני זמן
    List<Notification> findByCreatedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 8. מרכז ההתראות (סעיף 12)
    //     פילטר לפי סוג + חתונה + טווח תאריכים + עדיפות
    // ============================================================

    List<Notification> findByRelatedUserIdAndTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            NotificationType type,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Notification> findByRelatedUserIdAndWeddingIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            Long weddingId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Notification> findByRelatedUserIdAndPriorityLevelAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            int priorityLevel,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 9. קיבוץ התראות (סעיף 13 — איחוד)
    // ============================================================

    // “כמה אנשים עשו לך X” — לקיבוץ אירועים
    long countByRelatedUserIdAndTypeAndCreatedAtAfter(
            Long userId,
            NotificationType type,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 10. Rate Limiting — סעיף 16
    // ============================================================

    // בדיקה האם משתמש קיבל התראה מסוג ספציפי בזמן האחרון
    List<Notification> findByRelatedUserIdAndTypeAndCreatedAtAfter(
            Long userId,
            NotificationType type,
            LocalDateTime limit
    );


    // ============================================================
    // 🔵 11. מנגנון “משתמש נעול” (Post-Wedding Lock — סעיף 17)
    // ============================================================

    // כל ההתראות שממתינות למשתמש נעול
    List<Notification> findByRelatedUserIdAndCategoryOrderByCreatedAtDesc(
            Long userId,
            String category   // usually “system-lock”
    );

    // ההתראות שחסומות למשתמש נעול אבל נשמרות במערכת
    List<Notification> findByRelatedUserIdAndPriorityLevelLessThanEqualOrderByCreatedAtDesc(
            Long userId,
            int maxViewablePriority
    );


    // ============================================================
    // 🔵 12. התראות שלא נמסרו (ל־Push / WebSocket)
    // ============================================================

    List<Notification> findByRelatedUserIdAndDeliveredFalse(Long userId);

    List<Notification> findByRelatedUserIdAndDeliveredFalseOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 13. שליפות לפי מחבר (Admin / System / Wedding-Owner)
    // ============================================================

    List<Notification> findBySourceOrderByCreatedAtDesc(String source);

    List<Notification> findByRelatedUserIdAndSourceInOrderByCreatedAtDesc(
            Long userId,
            List<String> sources
    );


    // ============================================================
    // 🔵 14. שאילתות רוחב — לצורכי Dashboard Admin
    // ============================================================

    long countByType(NotificationType type);

    long countByCategory(String category);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByWeddingId(Long weddingId);

    long countByMatchId(Long matchId);

    long countBySource(String source);


    // ============================================================
    // 🔵 15. פונקציות עתידיות — AI / מודרטור
    // ============================================================

    // התראות מסומנות “AI” שנוצרו על ידי המערכת (תמונות חשודות וכו')
    List<Notification> findByCategoryAndSourceOrderByCreatedAtDesc(
            String category,
            String source   // "ai"
    );

    // התראות מסומנות כחשודות (לדוגמה תמונות לא ראויות)
    List<Notification> findByCategoryAndPriorityLevelGreaterThanEqualOrderByCreatedAtDesc(
            String category,
            int priority
    );
}