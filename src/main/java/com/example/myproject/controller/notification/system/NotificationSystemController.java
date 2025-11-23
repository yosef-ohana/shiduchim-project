package com.example.myproject.controller.notification.system;

import com.example.myproject.model.Notification;
import com.example.myproject.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🔵 NotificationSystemController
 * קונטרולר מערכת — מנוהל רק ע"י המערכת (Cron / Scheduler / AI / Rules Engine)
 *
 * אחריות:
 *  - ניקוי התראות ישנות
 *  - שליפת התראות מערכתיות לצרכי סטטיסטיקה פנימית
 *  - פעולות שאינן שייכות למנהל או למשתמש
 *  - ללא הרשאות ידניות
 */
@RestController
@RequestMapping("/api/system/notifications")
public class NotificationSystemController {

    private final NotificationService notificationService;

    public NotificationSystemController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ============================================================================
    // 🔵 1. שליפות מערכתיות לצרכי Cron / סטטיסטיקה / דירוג התראות
    // ============================================================================

    /**
     * שליפה של התראות שלא נקראו כלל במערכת.
     * מיועד לדוחות מערכתיים או לכלים פנימיים.
     */
    @GetMapping("/unread/all")
    public ResponseEntity<List<Notification>> getAllUnreadNotifications() {
        List<Notification> list = notificationService.getAllUnreadNotificationsForAdmin();
        return ResponseEntity.ok(list);
    }

    /**
     * התראות שנמחקו לוגית — לצרכי Debug או ממשק פיקוח.
     */
    @GetMapping("/deleted")
    public ResponseEntity<List<Notification>> getDeletedNotifications() {
        List<Notification> list = notificationService.getDeletedNotificationsForAdmin();
        return ResponseEntity.ok(list);
    }

    /**
     * התראות לפי חתונה (אירוע) — לשימוש מערכת בלבד.
     */
    @GetMapping("/wedding/{weddingId}")
    public ResponseEntity<List<Notification>> getNotificationsForWedding(
            @PathVariable Long weddingId) {

        List<Notification> list = notificationService.getNotificationsForWedding(weddingId);
        return ResponseEntity.ok(list);
    }

    /**
     * התראות לפי Match — לצרכים מערכתיים.
     */
    @GetMapping("/match/{matchId}")
    public ResponseEntity<List<Notification>> getNotificationsForMatch(
            @PathVariable Long matchId) {

        List<Notification> list = notificationService.getNotificationsForMatch(matchId);
        return ResponseEntity.ok(list);
    }

    // ============================================================================
    // 🔵 2. פעולות ניקוי מערכתיות (System / Cron)
    // ============================================================================

    /**
     * מחיקה פיזית של התראות לפני תאריך מסוים.
     * מיועד למשימות Cron בלבד.
     */
    @DeleteMapping("/cleanup/hard")
    public ResponseEntity<String> hardDeleteNotificationsBefore(
            @RequestParam("before") String beforeIso) {

        try {
            LocalDateTime threshold = LocalDateTime.parse(beforeIso);
            notificationService.deleteNotificationsBefore(threshold);
            return ResponseEntity.ok("✔ Notifications deleted permanently before: " + beforeIso);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ Invalid date format: " + e.getMessage());
        }
    }

    /**
     * מחיקה לוגית של התראות ישנות (Soft Delete).
     * נשארות במערכת לסטטיסטיקות.
     */
    @PutMapping("/cleanup/soft")
    public ResponseEntity<String> softDeleteOldNotifications(
            @RequestParam("before") String beforeIso) {

        try {
            LocalDateTime threshold = LocalDateTime.parse(beforeIso);
            notificationService.softDeleteOldNotifications(threshold);
            return ResponseEntity.ok("✔ Soft-deleted old notifications before: " + beforeIso);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ Invalid date format: " + e.getMessage());
        }
    }

    /**
     * ניקוי פופאפים ישנים שאין בהם צורך.
     */
    @PutMapping("/cleanup/popups")
    public ResponseEntity<String> cleanOldPopups(
            @RequestParam("before") String beforeIso) {

        try {
            LocalDateTime threshold = LocalDateTime.parse(beforeIso);
            notificationService.cleanOldPopups(threshold);
            return ResponseEntity.ok("✔ Old popups cleaned before: " + beforeIso);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ Invalid date: " + e.getMessage());
        }
    }

    /**
     * ניקוי הודעות מערכת (SYSTEM_ANNOUNCEMENT) ישנות.
     */
    @PutMapping("/cleanup/system-announcements")
    public ResponseEntity<String> cleanOldSystemAnnouncements(
            @RequestParam("before") String beforeIso) {

        try {
            LocalDateTime threshold = LocalDateTime.parse(beforeIso);
            notificationService.cleanOldSystemAnnouncements(threshold);
            return ResponseEntity.ok("✔ Old system announcements cleaned before: " + beforeIso);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ Invalid date: " + e.getMessage());
        }
    }

    // ============================================================================
    // 🔵 3. מחיקות מערכתיות בודדות (לא קשורות למשתמש)
    // ============================================================================

    /**
     * מחיקה פיזית של התראה בודדת — SYSTEM ONLY.
     */
    @DeleteMapping("/{notificationId}/hard-delete")
    public ResponseEntity<String> hardDeleteNotification(
            @PathVariable Long notificationId) {

        notificationService.hardDeleteNotification(notificationId);
        return ResponseEntity.ok("✔ Notification permanently deleted (system): " + notificationId);
    }

    // ============================================================================
    // 🔵 4. פעולות Cron מוכנות לפריסה
    // ============================================================================

    /**
     * Cron Hook — ניקוי התראות ישנות (ברירת מחדל: 30 יום).
     * ניתן להפעיל ידנית מהרסט.
     */
    @PutMapping("/cron/cleanup-default")
    public ResponseEntity<String> runDefaultCleanup() {

        LocalDateTime threshold = LocalDateTime.now().minusDays(30);

        notificationService.softDeleteOldNotifications(threshold);
        notificationService.cleanOldPopups(threshold);
        notificationService.cleanOldSystemAnnouncements(threshold);

        return ResponseEntity.ok("✔ Default 30-day cleanup executed.");
    }

    /**
     * Cron Hook — ניקוי התראות לפני X ימים.
     */
    @PutMapping("/cron/cleanup-days")
    public ResponseEntity<String> runCleanupByDays(
            @RequestParam("days") int days) {

        if (days <= 0 || days > 365)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ days must be between 1 and 365.");

        LocalDateTime threshold = LocalDateTime.now().minusDays(days);

        notificationService.softDeleteOldNotifications(threshold);
        notificationService.cleanOldPopups(threshold);
        notificationService.cleanOldSystemAnnouncements(threshold);

        return ResponseEntity.ok("✔ Cleanup executed for notifications older than " + days + " days.");
    }
}