package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.service.WeddingService;
import com.example.myproject.service.WeddingService.WeddingStats;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🔵 WeddingAdminController
 *
 * קונטרולר ניהול חתונות (Admin Dashboard).
 * מטפל בכל מה שקשור ל:
 *  - יצירת חתונה
 *  - עדכון חתונה
 *  - מחיקה (Soft / Hard לפי Service)
 *  - סטטיסטיקות חתונה
 *  - רקעים (Background)
 *  - רשימות חתונות Live / עתידיות / סגורות
 *  - רשימות משתתפים
 *  - סגירת חתונות ידנית / אוטומטית
 *  - Broadcast / הודעות סיום אירוע
 *
 * ⚠️ בדיקות הרשאות אדמין/בעל־אירוע נעשות בשכבת ה-Service (במקומות שיש validateAdmin וכו'),
 *     ובפרודקשן אמיתית יתווסף גם שכבת Auth/JWT מעל הקונטרולרים.
 */
@RestController
@RequestMapping("/api/admin/weddings")
public class WeddingAdminController {

    private final WeddingService weddingService;

    public WeddingAdminController(WeddingService weddingService) {
        this.weddingService = weddingService;
    }

    // ============================================================
    // 1. יצירת חתונה ע"י אדמין
    // ============================================================

    /**
     * יצירת חתונה חדשה ע"י אדמין.
     *
     * POST /api/weddings/admin
     *
     * Request JSON:
     * {
     *   "adminUserId": 1,
     *   "name": "חתונת דניאל & תמר",
     *   "startTime": "2025-12-01T19:30:00",
     *   "endTime": "2025-12-02T01:00:00",   // אופציונלי, אפשר null
     *   "backgroundImageUrl": "https://.../bg.jpg", // אופציונלי
     *   "backgroundVideoUrl": "https://.../bg.mp4"  // אופציונלי
     * }
     */
    @PostMapping
    public ResponseEntity<Wedding> createWeddingByAdmin(@RequestBody AdminCreateWeddingRequest request) {
        if (request.getAdminUserId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (request.getStartTime() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Wedding created = weddingService.createWeddingByAdmin(
                request.getName(),
                request.getStartTime(),
                request.getEndTime(),
                request.getAdminUserId(),
                request.getBackgroundImageUrl(),
                request.getBackgroundVideoUrl()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ============================================================
    // 2. עדכון חתונה ע"י אדמין (עדכון רגיל)
    // ============================================================

    /**
     * עדכון חתונה קיימת (שם / זמנים / רקע / Active).
     *
     * PUT /api/weddings/admin/{weddingId}
     *
     * Request JSON:
     * {
     *   "name": "...",                // אופציונלי
     *   "startTime": "2025-12-01T19:30:00", // אופציונלי
     *   "endTime": "2025-12-02T01:00:00",   // אופציונלי
     *   "backgroundImageUrl": "...",  // אופציונלי (ריק = מחיקה)
     *   "backgroundVideoUrl": "...",  // אופציונלי (ריק = מחיקה)
     *   "active": true                // אופציונלי
     * }
     */
    @PutMapping("/{weddingId}")
    public ResponseEntity<Wedding> updateWeddingByAdmin(@PathVariable Long weddingId,
                                                        @RequestBody AdminUpdateWeddingRequest request) {
        try {
            Wedding updated = weddingService.updateWeddingByAdmin(
                    weddingId,
                    request.getName(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getBackgroundImageUrl(),
                    request.getBackgroundVideoUrl(),
                    request.getActive()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 3. מחיקת חתונה ע"י אדמין (שימוש ב-deleteWeddingByAdmin)
    // ============================================================

    /**
     * מחיקה ע"י אדמין (משתמשת ב־WeddingService.deleteWeddingByAdmin).
     *
     * DELETE /api/weddings/admin/{weddingId}
     *
     * Request JSON:
     * {
     *   "adminUserId": 1
     * }
     */
    @DeleteMapping("/{weddingId}")
    public ResponseEntity<Void> deleteWeddingByAdmin(@PathVariable Long weddingId,
                                                     @RequestBody AdminDeleteWeddingRequest request) {
        try {
            if (request.getAdminUserId() == null) {
                return ResponseEntity.badRequest().build();
            }
            weddingService.deleteWeddingByAdmin(request.getAdminUserId(), weddingId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 4. עדכון רקעים (תמונה / וידאו) בלבד
    // ============================================================

    /**
     * עדכון רקעים של חתונה (תמונה / וידאו).
     *
     * PUT /api/weddings/admin/{weddingId}/background
     *
     * Request JSON:
     * {
     *   "backgroundImageUrl": "https://.../bg.jpg",  // אופציונלי, "" = מחיקה
     *   "backgroundVideoUrl": "https://.../bg.mp4"   // אופציונלי, "" = מחיקה
     * }
     */
    @PutMapping("/{weddingId}/background")
    public ResponseEntity<Wedding> updateWeddingBackground(@PathVariable Long weddingId,
                                                           @RequestBody BackgroundUpdateRequest request) {
        try {
            Wedding updated = weddingService.updateWeddingBackground(
                    weddingId,
                    request.getBackgroundImageUrl(),
                    request.getBackgroundVideoUrl()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * שליפת סטטוס רקע של חתונה לאדמין.
     *
     * GET /api/weddings/admin/{weddingId}/background/status
     */
    @GetMapping("/{weddingId}/background/status")
    public ResponseEntity<AdminFullUpdateWeddingRequest.BackgroundStatusResponse> getWeddingBackgroundStatus(@PathVariable Long weddingId) {
        try {
            Wedding wedding = weddingService.getWeddingById(weddingId);

            AdminFullUpdateWeddingRequest.BackgroundStatusResponse resp = new AdminFullUpdateWeddingRequest.BackgroundStatusResponse();
            resp.setBackgroundImageUrl(wedding.getBackgroundImageUrl());
            resp.setBackgroundVideoUrl(wedding.getBackgroundVideoUrl());
            resp.setBackgroundMode(wedding.getBackgroundMode());
            resp.setEffectiveBackgroundUrl(wedding.getEffectiveBackgroundUrl());
            resp.setUpdatedAt(wedding.getUpdatedAt());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * איפוס רקע של חתונה:
     * מוחק גם תמונת רקע וגם וידאו, ומשאיר את החתונה במצב DEFAULT.
     *
     * DELETE /api/weddings/admin/{weddingId}/background
     */
    @DeleteMapping("/{weddingId}/background")
    public ResponseEntity<Void> resetWeddingBackground(@PathVariable Long weddingId) {
        try {
            // "" → יתפרש כ-"מחק" ב-Service (שם זה הופך ל-null ואז ל-DEFAULT)
            weddingService.updateWeddingBackground(weddingId, "", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 5. סטטיסטיקות חתונה
    // ============================================================

    /**
     * סטטיסטיקות מלאות על חתונה.
     *
     * GET /api/weddings/admin/{weddingId}/stats
     *
     * Response JSON (WeddingStats):
     * {
     *   "weddingId": 10,
     *   "weddingName": "חתונת X",
     *   "active": true,
     *   "startTime": "...",
     *   "endTime": "...",
     *   "currentParticipants": 40,
     *   "historicalParticipants": 70,
     *   "matchesCount": 25,
     *   "mutualMatchesCount": 12
     * }
     */
    @GetMapping("/{weddingId}/stats")
    public ResponseEntity<WeddingStats> getWeddingStats(@PathVariable Long weddingId) {
        try {
            WeddingStats stats = weddingService.getWeddingStats(weddingId);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 6. רשימות חתונות – LIVE / עתידיות / הסתיימו
    // ============================================================

    /**
     * כל החתונות שמתקיימות כרגע (LIVE).
     *
     * GET /api/weddings/admin/live
     */
    @GetMapping("/live")
    public ResponseEntity<List<Wedding>> getLiveWeddings() {
        List<Wedding> list = weddingService.getLiveWeddings();
        return ResponseEntity.ok(list);
    }

    /**
     * חתונות עתידיות (שטרם התחילו).
     *
     * GET /api/weddings/admin/upcoming
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<Wedding>> getUpcomingWeddings() {
        List<Wedding> list = weddingService.getUpcomingWeddings();
        return ResponseEntity.ok(list);
    }

    /**
     * חתונות שכבר הסתיימו.
     *
     * GET /api/weddings/admin/finished
     */
    @GetMapping("/finished")
    public ResponseEntity<List<Wedding>> getFinishedWeddings() {
        List<Wedding> list = weddingService.getFinishedWeddings();
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 7. משתתפים – נוכחיים / היסטוריים
    // ============================================================

    /**
     * משתמשים שהחתונה האחרונה שלהם היא weddingId (נוכחיים באירוע).
     *
     * GET /api/weddings/admin/{weddingId}/participants/current
     */
    @GetMapping("/{weddingId}/participants/current")
    public ResponseEntity<List<User>> getCurrentParticipants(@PathVariable Long weddingId) {
        List<User> list = weddingService.getCurrentParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    /**
     * משתמשים שהיו אי פעם בחתונה זו (היסטוריה מלאה).
     *
     * GET /api/weddings/admin/{weddingId}/participants/history
     */
    @GetMapping("/{weddingId}/participants/history")
    public ResponseEntity<List<User>> getHistoricalParticipants(@PathVariable Long weddingId) {
        List<User> list = weddingService.getHistoricalParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 8. סגירת חתונות – ידני / המוני
    // ============================================================

    /**
     * סגירה ידנית של חתונה (active=false, endTime עכשיו אם לא מוגדר).
     *
     * POST /api/weddings/admin/{weddingId}/close
     */
    @PostMapping("/{weddingId}/close")
    public ResponseEntity<Void> closeWeddingManually(@PathVariable Long weddingId) {
        try {
            weddingService.closeWeddingManually(weddingId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * סגירת כל החתונות שפג תוקפן (endTime עבר).
     * מיועד ל־Job מתוזמן, אבל אפשר גם מכפתור בדשבורד.
     *
     * POST /api/weddings/admin/close-expired
     */
    @PostMapping("/close-expired")
    public ResponseEntity<Void> closeExpiredWeddings() {
        weddingService.closeExpiredWeddings();
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 9. Broadcast + הודעת "האירוע הסתיים"
    // ============================================================

    /**
     * שליחת Broadcast לכל המשתתפים הנוכחיים באירוע.
     *
     * POST /api/weddings/admin/{weddingId}/broadcast
     *
     * Request JSON:
     * {
     *   "title": "הכלה נכנסת",
     *   "message": "כולם לעמוד בצדדים..."
     * }
     */
    @PostMapping("/{weddingId}/broadcast")
    public ResponseEntity<Void> sendBroadcast(@PathVariable Long weddingId,
                                              @RequestBody BroadcastRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        weddingService.sendBroadcast(weddingId, request.getTitle(), request.getMessage());
        return ResponseEntity.ok().build();
    }

    /**
     * שליחת התראות "האירוע הסתיים" לכל המשתתפים.
     *
     * POST /api/weddings/admin/{weddingId}/notify-ended
     */
    @PostMapping("/{weddingId}/notify-ended")
    public ResponseEntity<Void> notifyEventEnded(@PathVariable Long weddingId) {
        try {
            weddingService.notifyEventEnded(weddingId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 10. בדיקות מצב חתונה: live / finished / active flag
    // ============================================================

    /**
     * האם החתונה LIVE כרגע? (active + עכשיו בין startTime ל-endTime)
     *
     * GET /api/weddings/admin/{weddingId}/live
     */
    @GetMapping("/{weddingId}/live")
    public ResponseEntity<Boolean> isWeddingLive(@PathVariable Long weddingId) {
        boolean live = weddingService.isWeddingLive(weddingId);
        return ResponseEntity.ok(live);
    }

    /**
     * האם החתונה הסתיימה (endTime לפני עכשיו)?
     *
     * GET /api/weddings/admin/{weddingId}/finished-flag
     */
    @GetMapping("/{weddingId}/finished-flag")
    public ResponseEntity<Boolean> isWeddingFinished(@PathVariable Long weddingId) {
        boolean finished = weddingService.isWeddingFinished(weddingId);
        return ResponseEntity.ok(finished);
    }

    /**
     * האם החתונה מסומנת כ-active בטבלה (בלי קשר לזמן)?
     *
     * GET /api/weddings/admin/{weddingId}/active-flag
     */
    @GetMapping("/{weddingId}/active-flag")
    public ResponseEntity<Boolean> isWeddingMarkedActive(@PathVariable Long weddingId) {
        boolean active = weddingService.isWeddingMarkedActive(weddingId);
        return ResponseEntity.ok(active);
    }

    // ============================================================
    // 11. עדכון מלא (Admin Panel מתקדם) + מחיקה פיזית
    // ============================================================

    /**
     * עדכון מלא של חתונה (כולל ownerUserId).
     *
     * PUT /api/weddings/admin/{weddingId}/admin-update
     *
     * Request JSON:
     * {
     *   "name": "...",
     *   "startTime": "...",
     *   "endTime": "...",
     *   "ownerUserId": 123,
     *   "backgroundImageUrl": "...",
     *   "backgroundVideoUrl": "...",
     *   "active": true
     * }
     */
    @PutMapping("/{weddingId}/admin-update")
    public ResponseEntity<Wedding> adminUpdateWedding(@PathVariable Long weddingId,
                                                      @RequestBody AdminFullUpdateWeddingRequest request) {
        try {
            Wedding updated = weddingService.adminUpdateWedding(
                    weddingId,
                    request.getName(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getOwnerUserId(),
                    request.getBackgroundImageUrl(),
                    request.getBackgroundVideoUrl(),
                    request.getActive()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * מחיקה פיזית (Hard Delete) של חתונה.
     * ⚠️ לשימוש זהיר בלבד – מוחק מה־DB לגמרי.
     *
     * DELETE /api/weddings/admin/{weddingId}/hard
     */
    @DeleteMapping("/{weddingId}/hard")
    public ResponseEntity<Void> hardDeleteWedding(@PathVariable Long weddingId) {
        weddingService.deleteWedding(weddingId);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // DTOs פנימיים לקונטרולר – כדי לשמור על JSON מסודר
    // ============================================================

    public static class AdminCreateWeddingRequest {
        private Long adminUserId;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String backgroundImageUrl;
        private String backgroundVideoUrl;

        public Long getAdminUserId() { return adminUserId; }
        public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }

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

    public static class AdminUpdateWeddingRequest {
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String backgroundImageUrl;
        private String backgroundVideoUrl;
        private Boolean active;

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

    public static class AdminDeleteWeddingRequest {
        private Long adminUserId;

        public Long getAdminUserId() { return adminUserId; }
        public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }
    }

    public static class BackgroundUpdateRequest {
        private String backgroundImageUrl;
        private String backgroundVideoUrl;

        public String getBackgroundImageUrl() { return backgroundImageUrl; }
        public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

        public String getBackgroundVideoUrl() { return backgroundVideoUrl; }
        public void setBackgroundVideoUrl(String backgroundVideoUrl) { this.backgroundVideoUrl = backgroundVideoUrl; }
    }

    public static class BroadcastRequest {
        private String title;
        private String message;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class AdminFullUpdateWeddingRequest {
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long ownerUserId;
        private String backgroundImageUrl;
        private String backgroundVideoUrl;
        private Boolean active;

        /**
         * DTO – סטטוס רקע של חתונה (לתצוגת אדמין).
         */
        public static class BackgroundStatusResponse {
            private String backgroundImageUrl;
            private String backgroundVideoUrl;
            private String backgroundMode;
            private String effectiveBackgroundUrl;
            private LocalDateTime updatedAt;

            public String getBackgroundImageUrl() {
                return backgroundImageUrl;
            }
            public void setBackgroundImageUrl(String backgroundImageUrl) {
                this.backgroundImageUrl = backgroundImageUrl;
            }

            public String getBackgroundVideoUrl() {
                return backgroundVideoUrl;
            }
            public void setBackgroundVideoUrl(String backgroundVideoUrl) {
                this.backgroundVideoUrl = backgroundVideoUrl;
            }

            public String getBackgroundMode() {
                return backgroundMode;
            }
            public void setBackgroundMode(String backgroundMode) {
                this.backgroundMode = backgroundMode;
            }

            public String getEffectiveBackgroundUrl() {
                return effectiveBackgroundUrl;
            }
            public void setEffectiveBackgroundUrl(String effectiveBackgroundUrl) {
                this.effectiveBackgroundUrl = effectiveBackgroundUrl;
            }

            public LocalDateTime getUpdatedAt() {
                return updatedAt;
            }
            public void setUpdatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
            }
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

        public String getBackgroundImageUrl() { return backgroundImageUrl; }
        public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

        public String getBackgroundVideoUrl() { return backgroundVideoUrl; }
        public void setBackgroundVideoUrl(String backgroundVideoUrl) { this.backgroundVideoUrl = backgroundVideoUrl; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
}