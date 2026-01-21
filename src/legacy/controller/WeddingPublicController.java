package com.example.myproject.controller;

import com.example.myproject.model.Wedding;
import com.example.myproject.service.WeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🔵 WeddingPublicController
 *
 * קונטרולר צד "פאבליק" / משתמש:
 * - הצטרפות לחתונה (Join Wedding)
 * - יצירת/עדכון חתונה ע"י בעל אירוע (Event Owner)
 * - בדיקת מצב צפייה: Wedding Mode בלבד / מותר גם מאגר גלובלי
 * - מעבר למאגר גלובלי אחרי אירוע (allowGlobalPoolAfterEvent)
 * - חתונות LIVE / עתידיות / הסתיימו – מנקודת מבט המשתמש
 *
 * ⚠️ הרשאות:
 * - ולידציית "בעל אירוע" / "אדמין" נעשית בשכבת ה-Service (validateEventOwner / validateOwnerOfWedding / validateAdmin).
 * - בשלב מאוחר יותר תתווסף שכבת Auth/JWT, ואז לא נצטרך להעביר userId ב־Body.
 */
@RestController
@RequestMapping("/api/public/weddings")
public class WeddingPublicController {

    private final WeddingService weddingService;

    public WeddingPublicController(WeddingService weddingService) {
        this.weddingService = weddingService;
    }

    // ============================================================
    // 1. יצירת / עדכון חתונה ע"י בעל אירוע (Event Owner)
    // ============================================================

    /**
     * יצירת חתונה חדשה ע"י בעל אירוע.
     *
     * POST /api/weddings/owner
     *
     * Request JSON:
     * {
     *   "ownerUserId": 123,
     *   "name": "חתונת יוסי & דניאלה",
     *   "startTime": "2025-12-01T19:30:00",
     *   "endTime": "2025-12-02T01:00:00",      // אופציונלי (null → 01:00 ביום הבא)
     *   "backgroundImageUrl": "https://...jpg", // אופציונלי
     *   "backgroundVideoUrl": "https://...mp4"  // אופציונלי
     * }
     *
     * לוגיקה:
     * - WeddingService.validateEventOwner(ownerUserId)
     * - יצירת חתונה עם רקע, זמנים, Active = true.
     */
    @PostMapping("/owner")
    public ResponseEntity<Wedding> createWeddingByOwner(@RequestBody OwnerCreateWeddingRequest request) {
        // בדיקות בסיסיות
        if (request.getOwnerUserId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (request.getStartTime() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            Wedding created = weddingService.createWeddingByOwner(
                    request.getName(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getOwnerUserId(),
                    request.getBackgroundImageUrl(),
                    request.getBackgroundVideoUrl()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            // User not found / bad data
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // לא בעל אירוע (validateEventOwner)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * עדכון חתונה קיימת ע"י בעל האירוע.
     *
     * PUT /api/weddings/owner/{weddingId}
     *
     * Request JSON:
     * {
     *   "ownerUserId": 123,                // מי מנסה לעדכן
     *   "name": "שם חדש",                 // אופציונלי
     *   "startTime": "2025-12-01T19:30:00",// אופציונלי
     *   "endTime": "2025-12-02T01:00:00",  // אופציונלי
     *   "backgroundImageUrl": "https://...", // אופציונלי, "" = מחיקה
     *   "backgroundVideoUrl": "https://...", // אופציונלי, "" = מחיקה
     *   "active": true                     // אופציונלי
     * }
     */
    @PutMapping("/owner/{weddingId}")
    public ResponseEntity<Wedding> updateWeddingByOwner(@PathVariable Long weddingId,
                                                        @RequestBody OwnerUpdateWeddingRequest request) {
        if (request.getOwnerUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Wedding updated = weddingService.updateWeddingByOwner(
                    weddingId,
                    request.getOwnerUserId(),
                    request.getName(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getBackgroundImageUrl(),
                    request.getBackgroundVideoUrl(),
                    request.getActive()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            // חתונה / משתמש לא נמצאו
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            // המשתמש אינו בעל האירוע
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 2. הצטרפות חתן/כלה/אורח לחתונה (Join Wedding)
    // ============================================================

    /**
     * הצטרפות לחתונה (סריקת ברקוד → userId + weddingId).
     *
     * POST /api/weddings/{weddingId}/join
     *
     * Request JSON:
     * {
     *   "userId": 456
     * }
     *
     * לוגיקה:
     * - WeddingService.joinWedding(userId, weddingId)
     *   - אם זו הפעם הראשונה → firstWeddingId
     *   - תמיד מעדכן lastWeddingId
     *   - מעדכן weddingsHistory (אם לא קיים ברשימה)
     *   - מעדכן activeBackgroundWeddingId = weddingId
     */
    @PostMapping("/{weddingId}/join")
    public ResponseEntity<Void> joinWedding(@PathVariable Long weddingId,
                                            @RequestBody JoinWeddingRequest request) {
        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            weddingService.joinWedding(request.getUserId(), weddingId);
            return ResponseEntity.ok().build();
        }
        catch (IllegalStateException ex) {
            // אירוע אינו פעיל
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        catch (RuntimeException ex) {
            // "User not found" / "Wedding not found"
            if (ex.getMessage() != null && ex.getMessage().contains("User not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (ex.getMessage() != null && ex.getMessage().contains("Wedding not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 3. בדיקות גישה לתצוגה – Wedding Mode / Global Pool
    // ============================================================

    /**
     * האם המשתמש נמצא במצב "Wedding Mode בלבד"?
     * - יש לו activeBackgroundWeddingId
     * - והחתונה עדיין LIVE.
     *
     * GET /api/weddings/visibility/{userId}/wedding-only
     *
     * Response:
     *  true  → לראות רק משתמשים מהחתונה
     *  false → מותר לו לראות גם מאגר כללי (או שאין חתונה פעילה)
     */
    @GetMapping("/visibility/{userId}/wedding-only")
    public ResponseEntity<Boolean> canViewWeddingOnly(@PathVariable Long userId) {
        try {
            boolean result = weddingService.canViewWeddingOnly(userId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * האם המשתמש יכול לראות גם מאגר גלובלי?
     *
     * לוגיקה ב-Service:
     * - אם לא היה אף פעם בחתונה → true
     * - אם היה בחתונה → רק אם היא הסתיימה (endTime עבר).
     *
     * GET /api/weddings/visibility/{userId}/global
     */
    @GetMapping("/visibility/{userId}/global")
    public ResponseEntity<Boolean> canViewGlobal(@PathVariable Long userId) {
        try {
            boolean result = weddingService.canViewGlobal(userId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * מעבר למאגר גלובלי אחרי אירוע:
     * - מנקה את activeBackgroundWeddingId למשתמש
     * - בודק שהחתונה האחרונה שלו הסתיימה (אחרת זורק שגיאה).
     *
     * POST /api/weddings/visibility/allow-global-after-event
     *
     * Request JSON:
     * {
     *   "userId": 456
     * }
     */
    @PostMapping("/visibility/allow-global-after-event")
    public ResponseEntity<Void> allowGlobalPoolAfterEvent(@RequestBody AllowGlobalAfterEventRequest request) {
        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            weddingService.allowGlobalPoolAfterEvent(request.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            // "האירוע עדיין פעיל – אי אפשר לעבור למאגר הכללי."
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 4. חתונות LIVE / עתידיות / הסתיימו – צד פאבליק
    // ============================================================

    /**
     * חתונות LIVE כרגע – "מסך אירועים חיים".
     *
     * GET /api/weddings/live
     *
     * (פונקציה זו כבר קיימת בקונטרולר אדמין, כאן זה endpoint נוסף לצרכים פומביים / מובייל)
     */
    @GetMapping("/live")
    public ResponseEntity<List<Wedding>> getLiveWeddingsPublic() {
        List<Wedding> list = weddingService.getLiveWeddings();
        return ResponseEntity.ok(list);
    }

    /**
     * חתונות עתידיות – למשל למסך "אירועים קרובים".
     *
     * GET /api/weddings/upcoming
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<Wedding>> getUpcomingWeddingsPublic() {
        List<Wedding> list = weddingService.getUpcomingWeddings();
        return ResponseEntity.ok(list);
    }

    /**
     * חתונות שכבר הסתיימו – יכול לשמש להיסטוריה/ארכיון.
     *
     * GET /api/weddings/finished
     */
    @GetMapping("/finished")
    public ResponseEntity<List<Wedding>> getFinishedWeddingsPublic() {
        List<Wedding> list = weddingService.getFinishedWeddings();
        return ResponseEntity.ok(list);
    }

    /**
     * בדיקה האם חתונה מסוימת LIVE כרגע.
     *
     * GET /api/weddings/{weddingId}/live-status
     *
     * Response JSON:
     *   true  → האירוע כרגע חי
     *   false → לא
     */
    @GetMapping("/{weddingId}/live-status")
    public ResponseEntity<Boolean> isWeddingLivePublic(@PathVariable Long weddingId) {
        boolean live = weddingService.isWeddingLive(weddingId);
        return ResponseEntity.ok(live);
    }

    /**
     * בדיקה האם חתונה הסתיימה (endTime < now).
     *
     * GET /api/weddings/{weddingId}/finished-status
     */
    @GetMapping("/{weddingId}/finished-status")
    public ResponseEntity<Boolean> isWeddingFinishedPublic(@PathVariable Long weddingId) {
        boolean finished = weddingService.isWeddingFinished(weddingId);
        return ResponseEntity.ok(finished);
    }

    // ============================================================
    // DTOs פנימיים לבקשות JSON
    // ============================================================

    /**
     * DTO – יצירת חתונה ע"י בעל אירוע.
     */
    public static class OwnerCreateWeddingRequest {
        private Long ownerUserId;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String backgroundImageUrl;
        private String backgroundVideoUrl;

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public String getBackgroundImageUrl() { return backgroundImageUrl; }
        public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

        public String getBackgroundVideoUrl() { return backgroundVideoUrl; }
        public void setBackgroundVideoUrl(String backgroundVideoUrl) { this.backgroundVideoUrl = backgroundVideoUrl; }
    }

    /**
     * DTO – עדכון חתונה ע"י בעל אירוע.
     */
    public static class OwnerUpdateWeddingRequest {
        private Long ownerUserId;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String backgroundImageUrl;
        private String backgroundVideoUrl;
        private Boolean active;

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public String getBackgroundImageUrl() { return backgroundImageUrl; }
        public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

        public String getBackgroundVideoUrl() { return backgroundVideoUrl; }
        public void setBackgroundVideoUrl(String backgroundVideoUrl) { this.backgroundVideoUrl = backgroundVideoUrl; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    /**
     * DTO – הצטרפות לחתונה (Join).
     */
    public static class JoinWeddingRequest {
        private Long userId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    /**
     * DTO – מעבר למצב גלובלי אחרי אירוע.
     */
    public static class AllowGlobalAfterEventRequest {
        private Long userId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
}