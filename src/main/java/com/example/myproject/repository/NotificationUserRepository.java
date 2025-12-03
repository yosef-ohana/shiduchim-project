package com.example.myproject.repository;

import com.example.myproject.model.NotificationUser;
import com.example.myproject.model.Notification;
import com.example.myproject.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationUserRepository extends JpaRepository<NotificationUser, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות לפי משתמש
    // ============================================================

    List<NotificationUser> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndHiddenFalseOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 2. לא נקראו / לא נצפו / דגלים אישיים
    // ============================================================

    List<NotificationUser> findByUser_IdAndReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndReadFalse(Long userId);

    List<NotificationUser> findByUser_IdAndPopupSeenFalseOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndPinnedTrueOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndSnoozedTrueOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 3. פילטור לפי מחיקה / הסתרה
    // ============================================================

    List<NotificationUser> findByUser_IdAndDeletedTrue(Long userId);

    List<NotificationUser> findByUser_IdAndHiddenTrue(Long userId);


    // ============================================================
    // 🔵 4. שליפה לפי Notification / קשרים
    // ============================================================

    Optional<NotificationUser> findByUser_IdAndNotification_Id(Long userId, Long notificationId);

    List<NotificationUser> findByNotification_Id(Long notificationId);

    long countByNotification_Id(Long notificationId);


    // ============================================================
    // 🔵 5. טווח תאריכים (Notification Center – Filters)
    // ============================================================


    List<NotificationUser> findByUser_IdAndReadFalseAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 6. קיבוץ התראות (סעיף 13 – Aggregation)
    // ============================================================

    long countByUser_IdAndReadFalseAndCreatedAtAfter(Long userId, LocalDateTime since);

    long countByUser_IdAndDeletedFalseAndCreatedAtAfter(Long userId, LocalDateTime since);


    // ============================================================
    // 🔵 7. ערוצים — delivered / not delivered (WebSocket/PUSH)
    // ============================================================

    List<NotificationUser> findByUser_IdAndDeletedFalseAndReadFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);

    // התראות שממתינות לשליחה למשתמש (PUSH)
    List<NotificationUser> findByUser_IdAndPopupSeenFalse(Long userId);


    // ============================================================
    // 🔵 8. רק התראות שלא נמחקו / לא הוסתרו (ל־UnifiedUserCard)
    // ============================================================

    List<NotificationUser> findByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 9. פעולות Read / Seen / Pinned / Snoozed לפי זמן
    // ============================================================

    List<NotificationUser> findByUser_IdAndReadAtAfter(Long userId, LocalDateTime since);

    List<NotificationUser> findByUser_IdAndSnoozedTrueAndCreatedAtAfter(Long userId, LocalDateTime since);


    // ============================================================
    // 🔵 10. שאילתות לאדמין — Dashboard
    // ============================================================

    long countByUser_Id(Long userId);

    long countByDeletedTrue();

    long countByHiddenTrue();

    long countByPinnedTrue();

    long countBySnoozedTrue();

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 11. שאילתות עומק — Wedding/Global Mode (SystemRules)
    // ============================================================

    // התראות בזמן חתונה פעילה
    List<NotificationUser> findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime weddingStart,
            LocalDateTime weddingEnd
    );

    // התראות שהתקבלו אחרי החתונה (Lock Mode)
    List<NotificationUser> findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime weddingEnd
    );


    // ============================================================
    // 🔵 12. שליפות לאיחוי — איחוד התראות מרובות (SystemRules)
    // ============================================================

    List<NotificationUser> findByUser_IdAndPopupSeenFalseAndCreatedAtBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 13. ניקוי אוטומטי — AutoCleanup (SystemRules 9, 11)
    // ============================================================

    List<NotificationUser> findByCreatedAtBefore(LocalDateTime time);

    List<NotificationUser> findByDeletedTrueAndCreatedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 14. שאילתות לפי סטטוס “Locked” של משתמש בלי צילום
    // ============================================================

    // בזמן שהמשתמש נעול — נשתמש כדי לשלוף רק התראות קריטיות
    List<NotificationUser> findByUser_IdAndPinnedTrueOrReadFalseOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 15. שאילתות מתקדמות לעתיד — AI / מודרטור
    // ============================================================

    // אם התראה מסומנת כ-"AI-danger" ב־Notification.category
    List<NotificationUser> findByNotification_CategoryAndUser_IdOrderByCreatedAtDesc(
            String category,
            Long userId
    );

}