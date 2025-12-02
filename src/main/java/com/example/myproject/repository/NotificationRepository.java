package com.example.myproject.repository;                                // חבילת הריפו

import com.example.myproject.model.Notification;                         // ישות התראה
import com.example.myproject.model.enums.NotificationType;                     // Enum סוגי התראות
import com.example.myproject.model.User;                                 // ישות משתמש
import org.springframework.data.jpa.repository.JpaRepository;            // בסיס ל־CRUD
import org.springframework.stereotype.Repository;                        // מסמן כריפו

import java.time.LocalDateTime;                                          // טיפוסי זמן
import java.util.List;                                                   // רשימות

@Repository                                                              // ריפו לניהול התראות
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ==============================
    // 🔵 לפי משתמש
    // ==============================

    List<Notification> findByRecipient(User recipient);                  // כל ההתראות של משתמש
    List<Notification> findByRecipientAndReadFalse(User recipient);      // התראות לא נקראו (User)
    List<Notification> findByRecipientIdAndReadFalse(Long userId);       // התראות לא נקראו (id)
    long countByRecipientIdAndReadFalse(Long userId);                    // ספירת לא נקראו

    List<Notification> findTop50ByRecipientOrderByCreatedAtDesc(         // 50 האחרונות
                                                                         User recipient
    );

    // ==============================
    // 🔵 לפי סוג התראה
    // ==============================

    List<Notification> findByRecipientAndType(
            User recipient, NotificationType type
    );                                                                   // לפי סוג ספציפי

    List<Notification> findByRecipientIdAndTypeAndReadFalse(
            Long userId, NotificationType type
    );                                                                   // לא נקראו מסוג מסוים

    List<Notification> findByType(NotificationType type);                // כל ההתראות בסוג מסוים

    // ==============================
    // 🔵 לפי קטגוריה
    // ==============================

    List<Notification> findByCategory(String category);                  // כל ההתראות בקטגוריה
    List<Notification> findByRecipientIdAndCategory(
            Long userId, String category
    );                                                                   // לפי משתמש+קטגוריה

    List<Notification> findByRecipientIdAndCategoryAndReadFalse(
            Long userId, String category
    );                                                                   // לא נקראו בקטגוריה

    // ==============================
    // 🔵 לפי מקור ההתראה
    // ==============================

    List<Notification> findBySource(String source);                      // כל ההתראות שנוצרו ממקור מסוים
    List<Notification> findByRecipientIdAndSource(
            Long userId, String source
    );                                                                   // לפי משתמש+מקור

    // ==============================
    // 🔵 לפי רמת עדיפות (Priority)
    // ==============================

    List<Notification> findByPriorityLevel(int level);                   // כל ההתראות בעדיפות מסוימת
    List<Notification> findByRecipientIdAndPriorityLevel(
            Long userId, int level
    );                                                                   // למשתמש לפי עדיפות

    List<Notification> findByRecipientIdAndPriorityLevelAndReadFalse(
            Long userId, int level
    );                                                                   // דחופות שלא נקראו

    // ==============================
    // 🔵 לפי Popup (פופאפ)
    // ==============================

    List<Notification> findByRecipientIdAndPopupSeenFalse(Long userId);  // פופאפים שלא נצפו

    // ==============================
    // 🔵 קשרי Match/Wedding/ChatMessage
    // ==============================

    List<Notification> findByWeddingId(Long weddingId);                  // לפי חתונה
    List<Notification> findByRelatedUserId(Long relatedUserId);          // לפי משתמש נוסף
    List<Notification> findByMatchId(Long matchId);                      // לפי התאמה
    List<Notification> findByChatMessageId(Long chatMessageId);          // לפי הודעת צ'אט

    // ==============================
    // 🔵 זמנים (Cron / סטטיסטיקות)
    // ==============================

    List<Notification> findByCreatedAtAfter(LocalDateTime time);         // התראות חדשות
    List<Notification> findByCreatedAtBefore(LocalDateTime time);        // ישנות (למחיקה עתידית)

    // ==============================
    // 🔵 כל הלא־נקראו במערכת (ניהול)
    // ==============================

    List<Notification> findByReadFalse();                                // לניהול אדמין

    // ==============================
    // 🔵 תמיכה במחיקות לוגיות
    // ==============================

    List<Notification> findByDeletedTrue();                              // התראות שנמחקו לוגית
    List<Notification> findByDeletedFalse();                             // התראות פעילות בלבד
}