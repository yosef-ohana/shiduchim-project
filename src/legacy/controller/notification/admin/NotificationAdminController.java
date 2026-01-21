package com.example.myproject.controller.notification.admin;

import com.example.myproject.model.Notification;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.model.User;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationAdminController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationAdminController(NotificationService notificationService,
                                       UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // ============================================================
    // 🔐 בדיקת אדמין בסיסית
    // ============================================================

    private void assertAdmin(Long adminId) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "adminId is required");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin user not found"));

        // בהנחה שב-User יש isAdmin()
        if (!admin.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges required");
        }
    }

    // ============================================================
    // 📌 DTO ליצירת התראה ע"י אדמין
    // ============================================================

    public static class AdminNotificationRequest {
        public Long recipientId;
        public String type;          // NotificationType כטקסט, למשל "SYSTEM_ANNOUNCEMENT"
        public String title;
        public String message;

        public Long relatedUserId;
        public Long weddingId;
        public Long matchId;
        public Long chatMessageId;

        public String metadata;
        public String category;      // match / chat / system / profile / wedding
        public String source;        // system / admin / ai / wedding-owner

        public Integer priorityLevel; // 1 / 2 / 3
    }

    // ============================================================
    // 1️⃣ יצירת התראה ידנית ע"י אדמין
    // ============================================================

    @PostMapping
    public ResponseEntity<Notification> createNotificationAsAdmin(
            @RequestParam("adminId") Long adminId,
            @RequestBody AdminNotificationRequest request
    ) {
        assertAdmin(adminId);

        if (request.recipientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientId is required");
        }
        if (request.type == null || request.type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }

        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(request.type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid notification type: " + request.type
            );
        }

        int priority = (request.priorityLevel != null) ? request.priorityLevel : 1;

        Notification created = notificationService.createNotification(
                request.recipientId,
                notificationType,
                request.title,
                request.message,
                request.relatedUserId,
                request.weddingId,
                request.matchId,
                request.chatMessageId,
                request.metadata,
                request.category,
                request.source != null ? request.source : "admin",
                priority
        );

        // במקרה שהמשתמש לא מקבל התראות (allowInAppNotifications=false וכו')
        if (created == null) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ============================================================
    // 2️⃣ שליפות למשתמש ספציפי (Full Feed / Unread / Category / Type / Priority)
    // ============================================================

    /** כל ההתראות של משתמש (כולל נקראות/לא נקראות, לא מחוקות) */
    @GetMapping("/user/{userId}/all")
    public ResponseEntity<List<Notification>> getAllNotificationsForUserAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("userId") Long userId
    ) {
        assertAdmin(adminId);
        List<Notification> list = notificationService.getAllNotificationsForUser(userId);
        return ResponseEntity.ok(list);
    }

    /** כל ההתראות שלא נקראו למשתמש */
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadNotificationsForUserAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("userId") Long userId
    ) {
        assertAdmin(adminId);
        List<Notification> list = notificationService.getUnreadNotificationsForUser(userId);
        return ResponseEntity.ok(list);
    }

    /** התראות לפי קטגוריה למשתמש (match/chat/system/profile/wedding) */
    @GetMapping("/user/{userId}/category/{category}")
    public ResponseEntity<List<Notification>> getNotificationsByCategoryForUserAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("userId") Long userId,
            @PathVariable("category") String category
    ) {
        assertAdmin(adminId);
        List<Notification> list =
                notificationService.getNotificationsByCategory(userId, category);
        return ResponseEntity.ok(list);
    }

    /** התראות לפי סוג NotificationType למשתמש */
    @GetMapping("/user/{userId}/type/{type}")
    public ResponseEntity<List<Notification>> getNotificationsByTypeForUserAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("userId") Long userId,
            @PathVariable("type") String type
    ) {
        assertAdmin(adminId);

        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid notification type: " + type
            );
        }

        List<Notification> list =
                notificationService.getNotificationsByType(userId, notificationType);
        return ResponseEntity.ok(list);
    }

    /** התראות לפי רמת עדיפות (1/2/3) למשתמש */
    @GetMapping("/user/{userId}/priority/{priorityLevel}")
    public ResponseEntity<List<Notification>> getNotificationsByPriorityForUserAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("userId") Long userId,
            @PathVariable("priorityLevel") int priorityLevel
    ) {
        assertAdmin(adminId);
        List<Notification> list =
                notificationService.getNotificationsByPriorityLevel(userId, priorityLevel);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 3️⃣ שליפות גלובליות לאדמין (Unread / Deleted / לפי חתונה / Match)
    // ============================================================

    /** כל ההתראות שלא נקראו במערכת (לפי NotificationService.getAllUnreadNotificationsForAdmin) */
    @GetMapping("/unread/all")
    public ResponseEntity<List<Notification>> getAllUnreadNotificationsForAdmin(
            @RequestParam("adminId") Long adminId
    ) {
        assertAdmin(adminId);
        List<Notification> list = notificationService.getAllUnreadNotificationsForAdmin();
        return ResponseEntity.ok(list);
    }

    /** כל ההתראות שנמחקו לוגית במערכת */
    @GetMapping("/deleted")
    public ResponseEntity<List<Notification>> getDeletedNotificationsForAdmin(
            @RequestParam("adminId") Long adminId
    ) {
        assertAdmin(adminId);
        List<Notification> list = notificationService.getDeletedNotificationsForAdmin();
        return ResponseEntity.ok(list);
    }

    /** כל ההתראות של חתונה מסוימת */
    @GetMapping("/wedding/{weddingId}")
    public ResponseEntity<List<Notification>> getNotificationsForWeddingAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("weddingId") Long weddingId
    ) {
        assertAdmin(adminId);
        List<Notification> list = notificationService.getNotificationsForWedding(weddingId);
        return ResponseEntity.ok(list);
    }

    /** כל ההתראות של Match מסוים */
    @GetMapping("/match/{matchId}")
    public ResponseEntity<List<Notification>> getNotificationsForMatchAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("matchId") Long matchId
    ) {
        assertAdmin(adminId);
        List<Notification> list = notificationService.getNotificationsForMatch(matchId);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 4️⃣ מחיקות ע"י אדמין (Hard Delete + Cleanup Jobs)
    // ============================================================

    /** מחיקה פיזית של התראה אחת (Hard Delete) */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> hardDeleteNotificationAsAdmin(
            @RequestParam("adminId") Long adminId,
            @PathVariable("notificationId") Long notificationId
    ) {
        assertAdmin(adminId);
        notificationService.hardDeleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * מחיקת התראות פיזית לפני כמות ימים מסוימת.
     * לדוגמה: daysBack=90 → מוחק התראות שקדמו ל-90 יום אחורה.
     */
    @PostMapping("/cleanup/delete-before")
    public ResponseEntity<Void> hardDeleteNotificationsBeforeDate(
            @RequestParam("adminId") Long adminId,
            @RequestParam("daysBack") int daysBack
    ) {
        assertAdmin(adminId);

        if (daysBack <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysBack must be > 0");
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(daysBack);
        notificationService.deleteNotificationsBefore(threshold);

        return ResponseEntity.ok().build();
    }

    /**
     * מחיקה לוגית (soft delete) של התראות ישנות לפני X ימים.
     */
    @PostMapping("/cleanup/soft-delete-before")
    public ResponseEntity<Void> softDeleteOldNotifications(
            @RequestParam("adminId") Long adminId,
            @RequestParam("daysBack") int daysBack
    ) {
        assertAdmin(adminId);

        if (daysBack <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysBack must be > 0");
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(daysBack);
        notificationService.softDeleteOldNotifications(threshold);

        return ResponseEntity.ok().build();
    }

    /**
     * ניקוי פופאפים ישנים:
     * - רק התראות popupSeen=true
     * - רק לפני תאריך סף
     * - מסומן כ-deleted=true
     */
    @PostMapping("/cleanup/old-popups")
    public ResponseEntity<Void> cleanOldPopups(
            @RequestParam("adminId") Long adminId,
            @RequestParam("daysBack") int daysBack
    ) {
        assertAdmin(adminId);

        if (daysBack <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysBack must be > 0");
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(daysBack);
        notificationService.cleanOldPopups(threshold);

        return ResponseEntity.ok().build();
    }

    /**
     * ניקוי הודעות מערכת עתיקות (SYSTEM_ANNOUNCEMENT) לפני תאריך סף.
     */
    @PostMapping("/cleanup/old-system-announcements")
    public ResponseEntity<Void> cleanOldSystemAnnouncements(
            @RequestParam("adminId") Long adminId,
            @RequestParam("daysBack") int daysBack
    ) {
        assertAdmin(adminId);

        if (daysBack <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysBack must be > 0");
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(daysBack);
        notificationService.cleanOldSystemAnnouncements(threshold);

        return ResponseEntity.ok().build();
    }

}