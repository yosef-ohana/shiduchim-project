package com.example.myproject.controller;

import com.example.myproject.model.Notification;
import com.example.myproject.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 🔵 NotificationOwnerController
 *
 * קונטרולר ייעודי לבעלי אירועים (Wedding Owners):
 * - צפייה בהתראות שקשורות לחתונות שלהם
 * - פילוח לפי קטגוריה wedding
 * - צפייה בהתראות לפי חתונה / Match
 * - ניהול "נקרא" / פופאפ / מחיקה לוגית
 *
 * כל המתודות כאן נשענות ישירות על NotificationService
 * ויודעות לעבוד עם קטגוריה "wedding" לפי מסמך אפיון 2025.
 */
@RestController
@RequestMapping("/api/v1/notifications/owner")
public class NotificationOwnerController {

    private final NotificationService notificationService;

    public NotificationOwnerController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // =====================================================================
    // 1️⃣ פיד ראשי לבעל האירוע – התראות אחרונות / כולן / לא נקראו
    // =====================================================================

    /**
     * 50 ההתראות האחרונות של בעל אירוע (כולל כל הקטגוריות).
     * צד הלקוח יפילטר לפי category אם צריך (למשל רק wedding).
     */
    @GetMapping("/user/{ownerId}/latest")
    public ResponseEntity<List<Notification>> getLatestForOwner(@PathVariable Long ownerId) {
        try {
            List<Notification> list = notificationService.getLatestNotificationsForUser(ownerId);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * כל ההתראות של בעל האירוע (מהחדש לישן).
     */
    @GetMapping("/user/{ownerId}/all")
    public ResponseEntity<List<Notification>> getAllForOwner(@PathVariable Long ownerId) {
        try {
            List<Notification> list = notificationService.getAllNotificationsForUser(ownerId);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * כל ההתראות שלא נקראו אצל בעל האירוע.
     */
    @GetMapping("/user/{ownerId}/unread")
    public ResponseEntity<List<Notification>> getUnreadForOwner(@PathVariable Long ownerId) {
        try {
            List<Notification> list = notificationService.getUnreadNotificationsForUser(ownerId);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // =====================================================================
    // 2️⃣ התראות בקטגוריית "wedding" בלבד – מסך ניהול אירועים
    // =====================================================================

    /**
     * כל ההתראות של בעל האירוע בקטגוריית "wedding".
     * מתאים למסך "התראות לפי אירועים שלי".
     */
    @GetMapping("/user/{ownerId}/wedding")
    public ResponseEntity<List<Notification>> getWeddingCategoryForOwner(@PathVariable Long ownerId) {
        try {
            List<Notification> list = notificationService.getNotificationsByCategory(ownerId, "wedding");
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * כל ההתראות של בעל האירוע בקטגוריה כלשהי (למשל: match / chat / system / profile / wedding).
     */
    @GetMapping("/user/{ownerId}/category/{category}")
    public ResponseEntity<List<Notification>> getByCategoryForOwner(
            @PathVariable Long ownerId,
            @PathVariable String category
    ) {
        try {
            List<Notification> list = notificationService.getNotificationsByCategory(ownerId, category);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // =====================================================================
    // 3️⃣ התראות לפי חתונה / Match – דשבורד לבעל אירוע
    // =====================================================================

    /**
     * כל ההתראות של חתונה מסוימת.
     * משמש בעל אירוע לדשבורד: מי נכנס, מי השלים פרופיל, התאמות חדשות, בקשות גלובליות וכו'.
     */
    @GetMapping("/wedding/{weddingId}")
    public ResponseEntity<List<Notification>> getNotificationsForWedding(@PathVariable Long weddingId) {
        List<Notification> list = notificationService.getNotificationsForWedding(weddingId);
        return ResponseEntity.ok(list);
    }

    /**
     * כל ההתראות של Match מסוים שנוצר באירוע שלו.
     * אפשרי לשימוש במסך "פרטי התאמה" לבעל האירוע (לסטטיסטיקות / ניטור).
     */
    @GetMapping("/match/{matchId}")
    public ResponseEntity<List<Notification>> getNotificationsForMatch(@PathVariable Long matchId) {
        List<Notification> list = notificationService.getNotificationsForMatch(matchId);
        return ResponseEntity.ok(list);
    }

    // =====================================================================
    // 4️⃣ פופאפים – Bell / Toast בצד לקוח לבעל אירוע
    // =====================================================================

    /**
     * כל ההתראות שלא נצפו כפופאפ אצל בעל האירוע (popupSeen=false).
     * מתאים לטעינה בעת פתיחת Web / App כדי להציג Toasts.
     */
    @GetMapping("/user/{ownerId}/popups/unseen")
    public ResponseEntity<List<Notification>> getUnseenPopups(@PathVariable Long ownerId) {
        List<Notification> list = notificationService.getUnseenPopupsForUser(ownerId);
        return ResponseEntity.ok(list);
    }

    /**
     * סימון התראת פופאפ בודדת כ"נצפתה" ע"י בעל האירוע.
     */
    @PostMapping("/{notificationId}/popup-seen")
    public ResponseEntity<Void> markPopupSeen(
            @PathVariable Long notificationId,
            @RequestParam("ownerId") Long ownerId
    ) {
        notificationService.markNotificationPopupSeen(notificationId, ownerId);
        return ResponseEntity.ok().build();
    }

    /**
     * סימון כל הפופאפים של בעל אירוע כ"נצפו".
     */
    @PostMapping("/user/{ownerId}/popups/seen-all")
    public ResponseEntity<Void> markAllPopupsSeen(@PathVariable Long ownerId) {
        notificationService.markAllPopupsSeenForUser(ownerId);
        return ResponseEntity.ok().build();
    }

    // =====================================================================
    // 5️⃣ סימון כנקרא / מחיקה לוגית – ניהול התראות לבעל אירוע
    // =====================================================================

    /**
     * סימון התראה בודדת של בעל האירוע כ"נקראה".
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markNotificationAsRead(
            @PathVariable Long notificationId,
            @RequestParam("ownerId") Long ownerId
    ) {
        notificationService.markNotificationAsRead(notificationId, ownerId);
        return ResponseEntity.ok().build();
    }

    /**
     * סימון כל ההתראות של בעל האירוע כ"נקראו".
     */
    @PostMapping("/user/{ownerId}/read-all")
    public ResponseEntity<Void> markAllNotificationsAsRead(@PathVariable Long ownerId) {
        notificationService.markAllNotificationsAsReadForUser(ownerId);
        return ResponseEntity.ok().build();
    }

    /**
     * מחיקה לוגית של התראה (לא מוחקים פיזית – רק is_deleted=true).
     * רק הנמען (בעל האירוע) רשאי למחוק.
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> softDeleteNotification(
            @PathVariable Long notificationId,
            @RequestParam("ownerId") Long ownerId
    ) {
        notificationService.softDeleteNotification(notificationId, ownerId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // 6️⃣ ספירת לא נקראו – לבאג' באייקון 🔔
    // =====================================================================

    /**
     * ספירת התראות לא נקראו של בעל אירוע (לכל הקטגוריות).
     * בצד לקוח אפשר לבחור להציג רק wedding או הכל.
     */
    @GetMapping("/user/{ownerId}/unread/count")
    public ResponseEntity<Long> countUnreadForOwner(@PathVariable Long ownerId) {
        long count = notificationService.countUnreadNotificationsForUser(ownerId);
        return ResponseEntity.ok(count);
    }
}