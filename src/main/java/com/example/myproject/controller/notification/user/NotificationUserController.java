package com.example.myproject.controller.notification.user;

import com.example.myproject.model.Notification;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 🔵 NotificationUserController
 * קונטרולר שמטפל בכל מה שקשור להתראות עבור משתמש רגיל.
 * משתמש יכול:
 * - לקבל התראות
 * - לספור לא נקראו
 * - לסמן נקרא
 * - לסמן פופאפ כנקרא
 * - לקבל לפי קטגוריה / סוג / עדיפות
 * - לבצע soft delete רק על ההתראות שלו
 */
@RestController
@RequestMapping("/api/user/notifications")
public class NotificationUserController {

    private final NotificationService notificationService;

    public NotificationUserController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // =====================================================
    // 1️⃣ שליפות עיקריות למשתמש
    // =====================================================

    /** 50 ההתראות האחרונות */
    @GetMapping("/{userId}/latest")
    public ResponseEntity<List<Notification>> getLatestNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getLatestNotificationsForUser(userId));
    }

    /** כל ההתראות (מהחדש לישן) */
    @GetMapping("/{userId}/all")
    public ResponseEntity<List<Notification>> getAllNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getAllNotificationsForUser(userId));
    }

    /** כל ההתראות שלא נקראו */
    @GetMapping("/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationsForUser(userId));
    }

    /** ספירת לא נקראו */
    @GetMapping("/{userId}/unread/count")
    public ResponseEntity<Long> countUnread(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.countUnreadNotificationsForUser(userId));
    }

    // =====================================================
    // 2️⃣ סינון לפי קטגוריה / סוג / עדיפות
    // =====================================================

    /** קטגוריה: match/chat/system/profile/wedding */
    @GetMapping("/{userId}/category/{category}")
    public ResponseEntity<List<Notification>> getByCategory(
            @PathVariable Long userId,
            @PathVariable String category) {

        return ResponseEntity.ok(notificationService.getNotificationsByCategory(userId, category));
    }

    /** סוג NotificationType */
    @GetMapping("/{userId}/type/{type}")
    public ResponseEntity<List<Notification>> getByType(
            @PathVariable Long userId,
            @PathVariable NotificationType type) {

        return ResponseEntity.ok(notificationService.getNotificationsByType(userId, type));
    }

    /** לפי עדיפות (1,2,3) */
    @GetMapping("/{userId}/priority/{level}")
    public ResponseEntity<List<Notification>> getByPriority(
            @PathVariable Long userId,
            @PathVariable int level) {

        return ResponseEntity.ok(notificationService.getNotificationsByPriorityLevel(userId, level));
    }

    // =====================================================
    // 3️⃣ פופאפים
    // =====================================================

    /** פופאפים שלא נצפו */
    @GetMapping("/{userId}/popups/unseen")
    public ResponseEntity<List<Notification>> getUnseenPopups(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUnseenPopupsForUser(userId));
    }

    /** סימון פופאפ בודד כ"נצפה" */
    @PostMapping("/{userId}/popup/{notificationId}/seen")
    public ResponseEntity<Void> markPopupSeen(
            @PathVariable Long userId,
            @PathVariable Long notificationId) {

        notificationService.markNotificationPopupSeen(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /** סימון כל הפופאפים כנצפו */
    @PostMapping("/{userId}/popups/seen/all")
    public ResponseEntity<Void> markAllPopupsSeen(@PathVariable Long userId) {
        notificationService.markAllPopupsSeenForUser(userId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // 4️⃣ סימון כנקרא
    // =====================================================

    /** סימון התראה אחת כנקראה */
    @PostMapping("/{userId}/read/{notificationId}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long userId,
            @PathVariable Long notificationId) {

        notificationService.markNotificationAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /** סימון כל ההתראות כנקראו */
    @PostMapping("/{userId}/read/all")
    public ResponseEntity<Void> markAllRead(@PathVariable Long userId) {
        notificationService.markAllNotificationsAsReadForUser(userId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // 5️⃣ מחיקה (User = מחיקה לוגית בלבד)
    // =====================================================

    /** מחיקה לוגית של התראה (רק שלו) */
    @DeleteMapping("/{userId}/delete/{notificationId}")
    public ResponseEntity<Void> softDelete(
            @PathVariable Long userId,
            @PathVariable Long notificationId) {

        notificationService.softDeleteNotification(notificationId, userId);
        return ResponseEntity.ok().build();
    }

}