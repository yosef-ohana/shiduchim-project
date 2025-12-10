package com.example.myproject.repository;

import com.example.myproject.model.NotificationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    // 🔵 6. קיבוץ התראות (Aggregation)
    // ============================================================

    long countByUser_IdAndReadFalseAndCreatedAtAfter(Long userId, LocalDateTime since);

    long countByUser_IdAndDeletedFalseAndCreatedAtAfter(Long userId, LocalDateTime since);


    // ============================================================
    // 🔵 7. ערוצים — delivered / not delivered (WebSocket/PUSH)
    // ============================================================

    // כל מה שכרגע “חי” למרכז ההתראות (לא מחוק, לא מוסתר, לא נקרא)
    List<NotificationUser> findByUser_IdAndDeletedFalseAndReadFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);

    // התראות שממתינות להצגת popup למשתמש
    List<NotificationUser> findByUser_IdAndPopupSeenFalse(Long userId);


    // ============================================================
    // 🔵 8. רק התראות שלא נמחקו / לא הוסתרו (ל־Notification Center)
    // ============================================================

    List<NotificationUser> findByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 9. פעולות Read / Snoozed לפי זמן
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
    // 🔵 11. חתונה / אחרי חתונה (Wedding/Global Mode)
    // ============================================================

    // התראות בזמן חתונה פעילה (לפי טווח זמן)
    List<NotificationUser> findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime weddingStart,
            LocalDateTime weddingEnd
    );

    // התראות שהתקבלו אחרי החתונה (מצב Lock עד השלמת פרופיל)
    List<NotificationUser> findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime weddingEnd
    );


    // ============================================================
    // 🔵 12. שליפות לאיחוי — Aggregation בחלון זמן
    // ============================================================

    List<NotificationUser> findByUser_IdAndPopupSeenFalseAndCreatedAtBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 13. ניקוי אוטומטי — AutoCleanup
    // ============================================================

    List<NotificationUser> findByCreatedAtBefore(LocalDateTime time);

    List<NotificationUser> findByDeletedTrueAndCreatedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 14. מצב Locked — משתמש בלי תמונה / נעול לפעולות מאגר
    // ============================================================

    @Query("""
           SELECT nu
           FROM NotificationUser nu
           WHERE nu.user.id = :userId
             AND nu.deleted = false
             AND nu.hidden = false
             AND (nu.pinned = true OR nu.read = false)
           ORDER BY nu.createdAt DESC
           """)
    List<NotificationUser> findLockedModeVisibleNotifications(@Param("userId") Long userId);


    // ============================================================
    // 🔵 15. שאילתות מתקדמות — קטגוריה / AI / סיווג
    // ============================================================

    List<NotificationUser> findByNotification_CategoryAndUser_IdOrderByCreatedAtDesc(
            String category,
            Long userId
    );


    // ============================================================
    // 🔵 16. התראות חשובות / High Priority + Important Only
    // ============================================================

    List<NotificationUser> findByUser_IdAndDeletedFalseAndHiddenFalseAndNotification_PriorityLevelGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            int minPriority
    );

    @Query("""
           SELECT nu
           FROM NotificationUser nu
           WHERE nu.user.id = :userId
             AND nu.deleted = false
             AND nu.hidden = false
             AND (nu.pinned = true OR nu.notification.priorityLevel >= :minPriority)
           ORDER BY nu.createdAt DESC
           """)
    List<NotificationUser> findImportantNotificationsForUser(
            @Param("userId") Long userId,
            @Param("minPriority") int minPriority
    );


    // ============================================================
    // 🔵 17. עזר ל־Service — Batch / Unread Visible / Paging ראשוני
    // ============================================================

    // שליפה ב־Batch לפי רשימת IDs (לעדכון סטטוס מרוכז: read/hidden/deleted)
    List<NotificationUser> findByIdIn(List<Long> ids);

    // כמה התראות “חיות” ולא־נקראו (לא מחוק, לא מוסתר, לא נקרא)
    long countByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalse(Long userId);

    // אוסף מצומצם של ההתראות האחרונות לצורך טעינה ראשונית יעילה (ללא paging מלא בצד DB)
    List<NotificationUser> findTop50ByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);

    // אוסף מצומצם של ההתראות הלא־נקראות האחרונות (למצב Popup ראשוני)
    List<NotificationUser> findTop50ByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalseOrderByCreatedAtDesc(Long userId);


    // ============================================================
    // 🔵 18. תור POPUP גלובלי — Worker/WebSocket
    // ============================================================

    // כל ההתראות שעדיין לא הוצגו כ־popup ושעדיין בתוקף (לא מחוק, לא מוסתר)
    List<NotificationUser> findByPopupSeenFalseAndDeletedFalseAndHiddenFalse();
}