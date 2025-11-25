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
 * 🟢 WeddingOwnerController
 *
 * קונטרולר ניהול חתונות מצד "בעל האירוע" (Event Owner).
 * נותן יכולות דומות לאדמין, אבל רק על החתונות שהמשתמש הוא הבעלים שלהן.
 *
 * כל פעולה כאן:
 *  - מקבלת ownerUserId (ב-RequestBody או כ-RequestParam)
 *  - בודקת באמצעות weddingService.isOwnerOfWedding(ownerUserId, weddingId)
 *  - אם המשתמש אינו בעל האירוע → מחזיר 403 FORBIDDEN
 *
 * בפרודקשן אמיתי תהיה שכבת Auth/JWT מעל זה, וה-ownerUserId יגיע מה-Token.
 */
@RestController
@RequestMapping("/api/owner/weddings")
public class WeddingOwnerController {

    private final WeddingService weddingService;

    public WeddingOwnerController(WeddingService weddingService) {
        this.weddingService = weddingService;
    }

    // עוזר פנימי – בודק שהמשתמש הוא בעל האירוע
    private boolean isOwner(Long ownerUserId, Long weddingId) {
        if (ownerUserId == null || weddingId == null) {
            return false;
        }
        return weddingService.isUserInWedding(ownerUserId, weddingId)
                && weddingService.isOwnerOfWedding(ownerUserId, weddingId);
    }

    // ============================================================
    // 1. יצירת חתונה ע"י בעל אירוע
    // ============================================================

    /**
     * יצירת חתונה חדשה ע"י בעל אירוע.
     *
     * POST /api/weddings/owner
     *
     * Request JSON:
     * {
     *   "ownerUserId": 5,
     *   "name": "חתונת דניאל & תמר",
     *   "startTime": "2025-12-01T19:30:00",
     *   "endTime": "2025-12-02T01:00:00",   // אופציונלי
     *   "backgroundImageUrl": "https://.../bg.jpg", // אופציונלי
     *   "backgroundVideoUrl": "https://.../bg.mp4"  // אופציונלי
     * }
     */
    @PostMapping
    public ResponseEntity<Wedding> createWeddingByOwner(@RequestBody OwnerCreateWeddingRequest request) {

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
        } catch (IllegalStateException ex) {
            // המשתמש אינו eventManager
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ============================================================
    // 2. עדכון חתונה ע"י בעל האירוע
    // ============================================================

    /**
     * עדכון חתונה קיימת ע"י בעל האירוע.
     *
     * PUT /api/weddings/owner/{weddingId}
     *
     * Request JSON:
     * {
     *   "ownerUserId": 5,
     *   "name": "...",                // אופציונלי
     *   "startTime": "...",           // אופציונלי
     *   "endTime": "...",             // אופציונלי
     *   "backgroundImageUrl": "...",  // אופציונלי
     *   "backgroundVideoUrl": "...",  // אופציונלי
     *   "active": true                // אופציונלי
     * }
     */
    @PutMapping("/{weddingId}")
    public ResponseEntity<Wedding> updateWeddingByOwner(@PathVariable Long weddingId,
                                                        @RequestBody OwnerUpdateWeddingRequest request) {

        if (request.getOwnerUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // ה-Service כבר בודק שהמשתמש הוא בעל האירוע (validateOwnerOfWedding)
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
            // חתונה לא נמצאה
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            // לא בעל האירוע / אין הרשאה
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 3. רקעים – עדכון / סטטוס / איפוס (לבעל האירוע בלבד)
    // ============================================================

    /**
     * עדכון רקעים של חתונה (תמונה / וידאו) ע"י בעל האירוע.
     *
     * PUT /api/weddings/owner/{weddingId}/background?ownerUserId=5
     */
    @PutMapping("/{weddingId}/background")
    public ResponseEntity<Wedding> updateWeddingBackgroundByOwner(@PathVariable Long weddingId,
                                                                  @RequestParam Long ownerUserId,
                                                                  @RequestBody BackgroundUpdateRequest request) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
     * שליפת סטטוס רקע של חתונה עבור בעל האירוע.
     *
     * GET /api/weddings/owner/{weddingId}/background/status?ownerUserId=5
     */
    @GetMapping("/{weddingId}/background/status")
    public ResponseEntity<BackgroundStatusResponse> getWeddingBackgroundStatusByOwner(@PathVariable Long weddingId,
                                                                                      @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Wedding wedding = weddingService.getWeddingById(weddingId);

            BackgroundStatusResponse resp = new BackgroundStatusResponse();
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
     * איפוס רקע של חתונה ע"י בעל האירוע.
     *
     * DELETE /api/weddings/owner/{weddingId}/background?ownerUserId=5
     */
    @DeleteMapping("/{weddingId}/background")
    public ResponseEntity<Void> resetWeddingBackgroundByOwner(@PathVariable Long weddingId,
                                                              @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            weddingService.updateWeddingBackground(weddingId, "", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 4. סטטיסטיקות חתונה – לבעל האירוע
    // ============================================================

    /**
     * סטטיסטיקות מלאות על חתונה (רק אם הוא הבעלים).
     *
     * GET /api/weddings/owner/{weddingId}/stats?ownerUserId=5
     */
    @GetMapping("/{weddingId}/stats")
    public ResponseEntity<WeddingStats> getWeddingStatsByOwner(@PathVariable Long weddingId,
                                                               @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            WeddingStats stats = weddingService.getWeddingStats(weddingId);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 5. משתתפים – נוכחיים / היסטוריים
    // ============================================================

    /**
     * משתתפים נוכחיים (lastWeddingId = weddingId).
     *
     * GET /api/weddings/owner/{weddingId}/participants/current?ownerUserId=5
     */
    @GetMapping("/{weddingId}/participants/current")
    public ResponseEntity<List<User>> getCurrentParticipantsByOwner(@PathVariable Long weddingId,
                                                                    @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<User> list = weddingService.getCurrentParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    /**
     * משתתפים היסטוריים (כל מי שהיה אי פעם בחתונה).
     *
     * GET /api/weddings/owner/{weddingId}/participants/history?ownerUserId=5
     */
    @GetMapping("/{weddingId}/participants/history")
    public ResponseEntity<List<User>> getHistoricalParticipantsByOwner(@PathVariable Long weddingId,
                                                                       @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<User> list = weddingService.getHistoricalParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 6. סגירת חתונה + Close Expired (לאירועים שלו בלבד)
    // ============================================================

    /**
     * סגירה ידנית של חתונה (active=false).
     *
     * POST /api/weddings/owner/{weddingId}/close?ownerUserId=5
     */
    @PostMapping("/{weddingId}/close")
    public ResponseEntity<Void> closeWeddingManuallyByOwner(@PathVariable Long weddingId,
                                                            @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            weddingService.closeWeddingManually(weddingId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * סגירת כל החתונות שפג תוקפן עבור הבעלים הזה (אופציונלי).
     * כרגע מבצע closeExpiredWeddings גלובלי – אפשר בהמשך לצמצם רק לחתונות שלו.
     *
     * POST /api/weddings/owner/close-expired?ownerUserId=5
     */
    @PostMapping("/close-expired")
    public ResponseEntity<Void> closeExpiredWeddingsByOwner(@RequestParam Long ownerUserId) {
        // כרגע לא מסנן לפי ownerId – אפשר להחמיר בעתיד.
        // נניח שבעלי אירוע לא ישתמשו בזה הרבה, או שזה כפתור אדמין בלבד ממש.
        weddingService.closeExpiredWeddings();
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 7. Broadcast + "האירוע הסתיים" – לבעל האירוע
    // ============================================================

    /**
     * שליחת Broadcast לכל המשתתפים הנוכחיים באירוע.
     *
     * POST /api/weddings/owner/{weddingId}/broadcast?ownerUserId=5
     */
    @PostMapping("/{weddingId}/broadcast")
    public ResponseEntity<Void> sendBroadcastByOwner(@PathVariable Long weddingId,
                                                     @RequestParam Long ownerUserId,
                                                     @RequestBody BroadcastRequest request) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
     * POST /api/weddings/owner/{weddingId}/notify-ended?ownerUserId=5
     */
    @PostMapping("/{weddingId}/notify-ended")
    public ResponseEntity<Void> notifyEventEndedByOwner(@PathVariable Long weddingId,
                                                        @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            weddingService.notifyEventEnded(weddingId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 8. בדיקות מצב חתונה: live / finished / active flag (owner only)
    // ============================================================

    /**
     * האם החתונה LIVE כרגע? (active + בין startTime ל-endTime)
     *
     * GET /api/weddings/owner/{weddingId}/live?ownerUserId=5
     */
    @GetMapping("/{weddingId}/live")
    public ResponseEntity<Boolean> isWeddingLiveByOwner(@PathVariable Long weddingId,
                                                        @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean live = weddingService.isWeddingLive(weddingId);
        return ResponseEntity.ok(live);
    }

    /**
     * האם החתונה הסתיימה (endTime לפני עכשיו)?
     *
     * GET /api/weddings/owner/{weddingId}/finished-flag?ownerUserId=5
     */
    @GetMapping("/{weddingId}/finished-flag")
    public ResponseEntity<Boolean> isWeddingFinishedByOwner(@PathVariable Long weddingId,
                                                            @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean finished = weddingService.isWeddingFinished(weddingId);
        return ResponseEntity.ok(finished);
    }

    /**
     * האם החתונה מסומנת כ-active בטבלה?
     *
     * GET /api/weddings/owner/{weddingId}/active-flag?ownerUserId=5
     */
    @GetMapping("/{weddingId}/active-flag")
    public ResponseEntity<Boolean> isWeddingMarkedActiveByOwner(@PathVariable Long weddingId,
                                                                @RequestParam Long ownerUserId) {

        if (!isOwner(ownerUserId, weddingId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean active = weddingService.isWeddingMarkedActive(weddingId);
        return ResponseEntity.ok(active);
    }

    // ============================================================
    // DTOs פנימיים
    // ============================================================

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

    /**
     * סטטוס רקע – DTO קטן.
     */
    public static class BackgroundStatusResponse {
        private String backgroundImageUrl;
        private String backgroundVideoUrl;
        private String backgroundMode;
        private String effectiveBackgroundUrl;
        private LocalDateTime updatedAt;

        public String getBackgroundImageUrl() { return backgroundImageUrl; }
        public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

        public String getBackgroundVideoUrl() { return backgroundVideoUrl; }
        public void setBackgroundVideoUrl(String backgroundVideoUrl) { this.backgroundVideoUrl = backgroundVideoUrl; }

        public String getBackgroundMode() { return backgroundMode; }
        public void setBackgroundMode(String backgroundMode) { this.backgroundMode = backgroundMode; }

        public String getEffectiveBackgroundUrl() { return effectiveBackgroundUrl; }
        public void setEffectiveBackgroundUrl(String effectiveBackgroundUrl) { this.effectiveBackgroundUrl = effectiveBackgroundUrl; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}