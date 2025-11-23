package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.repository.WeddingRepository;
import com.example.myproject.service.WeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 🔵 WeddingOwnerController
 *
 * קונטרולר לבעלי אירוע (Event Owners):
 * - יצירת חתונה
 * - עדכון חתונה
 * - רשימת החתונות של בעל אירוע
 * - סטטיסטיקות חתונה
 * - רשימת משתתפים (נוכחיים / היסטוריים)
 * - סגירת חתונה
 * - שליחת Broadcast לכל המשתתפים
 * - שליחת התראת "אירוע הסתיים"
 * - בדיקת סטטוס (LIVE / Finished / Active Flag)
 *
 * ⚠️ הערות:
 * - ולידציית "האם המשתמש הוא בעל האירוע של החתונה הזאת"
 *   נעשית ברמת הקונטרולר ע"י בדיקה מול Wedding.ownerUserId.
 * - ולידציית "האם המשתמש מסומן כבעל אירוע" נעשית בפונקציה
 *   createWeddingByOwner בתוך WeddingService (validateEventOwner).
 */
@RestController
@RequestMapping("/api/owner/weddings")
public class WeddingOwnerController {

    private final WeddingService weddingService;
    private final WeddingRepository weddingRepository;

    public WeddingOwnerController(WeddingService weddingService,
                                  WeddingRepository weddingRepository) {
        this.weddingService = weddingService;
        this.weddingRepository = weddingRepository;
    }

    // ============================================================
    // 1. יצירת חתונה ע"י בעל אירוע
    // ============================================================

    /**
     * יצירת חתונה חדשה ע"י בעל אירוע.
     *
     * POST /api/owner/weddings
     *
     * Request JSON:
     * {
     *   "ownerUserId": 123,
     *   "name": "חתונת יוסי & דניאלה",
     *   "startTime": "2025-12-01T19:30:00",
     *   "endTime": "2025-12-02T01:00:00",       // אופציונלי, null → 01:00 ביום הבא
     *   "backgroundImageUrl": "https://...jpg", // אופציונלי
     *   "backgroundVideoUrl": "https://...mp4"  // אופציונלי
     * }
     *
     * Service:
     * - WeddingService.createWeddingByOwner(...)
     */
    @PostMapping
    public ResponseEntity<Wedding> createWeddingByOwner(@RequestBody OwnerCreateWeddingRequest request) {
        if (request.getOwnerUserId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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
            // User not found / invalid params
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // לא בעל אירוע (validateEventOwner)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 2. עדכון חתונה ע"י בעל אירוע
    // ============================================================

    /**
     * עדכון חתונה קיימת ע"י בעל האירוע.
     *
     * PUT /api/owner/weddings/{weddingId}
     *
     * Request JSON:
     * {
     *   "ownerUserId": 123,                  // חובה – מי מנסה לעדכן
     *   "name": "שם חדש",                   // אופציונלי
     *   "startTime": "2025-12-01T19:30:00", // אופציונלי
     *   "endTime": "2025-12-02T01:00:00",   // אופציונלי
     *   "backgroundImageUrl": "https://...", // אופציונלי, "" = מחיקה
     *   "backgroundVideoUrl": "https://...", // אופציונלי, "" = מחיקה
     *   "active": true                      // אופציונלי
     * }
     *
     * Service:
     * - WeddingService.updateWeddingByOwner(...)
     */
    @PutMapping("/{weddingId}")
    public ResponseEntity<Wedding> updateWeddingByOwner(@PathVariable Long weddingId,
                                                        @RequestBody OwnerUpdateWeddingRequest request) {
        if (request.getOwnerUserId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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
            // המשתמש אינו בעל האירוע (validateOwnerOfWedding)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 3. רשימת חתונות לפי בעל אירוע
    //    (שימוש ישיר ב-WeddingRepository לפי דרישת האפיון)
    // ============================================================

    /**
     * רשימת כל החתונות של בעל אירוע מסוים.
     *
     * GET /api/owner/weddings/by-owner/{ownerUserId}
     *
     * Repository:
     * - WeddingRepository.findByOwnerUserId(ownerUserId)
     */
    @GetMapping("/by-owner/{ownerUserId}")
    public ResponseEntity<List<Wedding>> getWeddingsByOwner(@PathVariable Long ownerUserId) {
        List<Wedding> list = weddingRepository.findByOwnerUserId(ownerUserId);
        return ResponseEntity.ok(list);
    }

    /**
     * רשימת כל החתונות הפעילות של בעל אירוע מסוים.
     *
     * GET /api/owner/weddings/by-owner/{ownerUserId}/active
     *
     * Repository:
     * - WeddingRepository.findByOwnerUserIdAndActiveTrue(ownerUserId)
     */
    @GetMapping("/by-owner/{ownerUserId}/active")
    public ResponseEntity<List<Wedding>> getActiveWeddingsByOwner(@PathVariable Long ownerUserId) {
        List<Wedding> list = weddingRepository.findByOwnerUserIdAndActiveTrue(ownerUserId);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 4. סטטיסטיקות חתונה (WeddingStats) לבעל האירוע
    // ============================================================

    /**
     * סטטיסטיקות חתונה לבעל האירוע.
     *
     * GET /api/owner/weddings/{weddingId}/owner/{ownerUserId}/stats
     *
     * Service:
     * - WeddingService.getWeddingStats(weddingId)
     *
     * לפני השליפה:
     * - בדיקת בעלות: wedding.ownerUserId == ownerUserId
     */
    @GetMapping("/{weddingId}/owner/{ownerUserId}/stats")
    public ResponseEntity<WeddingService.WeddingStats> getWeddingStatsForOwner(@PathVariable Long weddingId,
                                                                               @PathVariable Long ownerUserId) {
        Wedding wedding = getWeddingForOwnerOrThrow(weddingId, ownerUserId);
        if (wedding == null) {
            // כבר טופל ב-getWeddingForOwnerOrThrow (עם Exception)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        WeddingService.WeddingStats stats = weddingService.getWeddingStats(weddingId);
        return ResponseEntity.ok(stats);
    }

    // ============================================================
    // 5. משתתפים (Current / Historical) – לבעל האירוע
    // ============================================================

    /**
     * משתתפים נוכחיים (החתונה האחרונה שלהם היא weddingId).
     *
     * GET /api/owner/weddings/{weddingId}/owner/{ownerUserId}/participants/current
     *
     * Service:
     * - WeddingService.getCurrentParticipants(weddingId)
     */
    @GetMapping("/{weddingId}/owner/{ownerUserId}/participants/current")
    public ResponseEntity<List<User>> getCurrentParticipantsForOwner(@PathVariable Long weddingId,
                                                                     @PathVariable Long ownerUserId) {
        getWeddingForOwnerOrThrow(weddingId, ownerUserId); // תיזרק שגיאה אם לא שייך
        List<User> list = weddingService.getCurrentParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    /**
     * משתתפים היסטוריים (כל מי שאי פעם היה בחתונה זו).
     *
     * GET /api/owner/weddings/{weddingId}/owner/{ownerUserId}/participants/history
     *
     * Service:
     * - WeddingService.getHistoricalParticipants(weddingId)
     */
    @GetMapping("/{weddingId}/owner/{ownerUserId}/participants/history")
    public ResponseEntity<List<User>> getHistoricalParticipantsForOwner(@PathVariable Long weddingId,
                                                                        @PathVariable Long ownerUserId) {
        getWeddingForOwnerOrThrow(weddingId, ownerUserId);
        List<User> list = weddingService.getHistoricalParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 6. סגירה ידנית של חתונה ע"י בעל האירוע
    // ============================================================

    /**
     * סגירה ידנית של חתונה (active=false).
     * - אם endTime == null → נקבע ל־LocalDateTime.now().
     *
     * POST /api/owner/weddings/{weddingId}/owner/{ownerUserId}/close
     *
     * Service:
     * - WeddingService.closeWeddingManually(weddingId)
     */
    @PostMapping("/{weddingId}/owner/{ownerUserId}/close")
    public ResponseEntity<Void> closeWeddingManuallyByOwner(@PathVariable Long weddingId,
                                                            @PathVariable Long ownerUserId) {
        getWeddingForOwnerOrThrow(weddingId, ownerUserId);

        weddingService.closeWeddingManually(weddingId);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 7. Broadcast הודעה לכל משתתפי האירוע – ע"י בעל האירוע
    // ============================================================

    /**
     * שליחת הודעת Broadcast לכל המשתתפים באירוע.
     *
     * POST /api/owner/weddings/{weddingId}/owner/{ownerUserId}/broadcast
     *
     * Request JSON:
     * {
     *   "title": "הכלה נכנסת",
     *   "message": "כולם מתבקשים להתכנס באולם המרכזי."
     * }
     *
     * Service:
     * - WeddingService.sendBroadcast(weddingId, title, message)
     */
    @PostMapping("/{weddingId}/owner/{ownerUserId}/broadcast")
    public ResponseEntity<Void> sendBroadcast(@PathVariable Long weddingId,
                                              @PathVariable Long ownerUserId,
                                              @RequestBody BroadcastRequest request) {
        getWeddingForOwnerOrThrow(weddingId, ownerUserId);

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        weddingService.sendBroadcast(weddingId, request.getTitle(), request.getMessage());
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 8. התראת "האירוע הסתיים" – ע"י בעל האירוע
    // ============================================================

    /**
     * שליחת התראת "האירוע הסתיים" לכל המשתתפים.
     *
     * POST /api/owner/weddings/{weddingId}/owner/{ownerUserId}/notify-ended
     *
     * Service:
     * - WeddingService.notifyEventEnded(weddingId)
     */
    @PostMapping("/{weddingId}/owner/{ownerUserId}/notify-ended")
    public ResponseEntity<Void> notifyEventEnded(@PathVariable Long weddingId,
                                                 @PathVariable Long ownerUserId) {
        getWeddingForOwnerOrThrow(weddingId, ownerUserId);

        weddingService.notifyEventEnded(weddingId);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 9. סטטוס חתונה (LIVE / Finished / Active Flag) – לבעל האירוע
    // ============================================================

    /**
     * סטטוס מלא של החתונה מנקודת מבט בעל האירוע:
     * - isLive        → עכשיו בזמן האירוע (startTime <= now <= endTime) וגם active=true
     * - isFinished    → endTime < now
     * - isMarkedActive→ הערך בטבלה (active) בלי קשר לזמן
     *
     * GET /api/owner/weddings/{weddingId}/owner/{ownerUserId}/status
     *
     * Service:
     * - WeddingService.isWeddingLive(weddingId)
     * - WeddingService.isWeddingFinished(weddingId)
     * - WeddingService.isWeddingMarkedActive(weddingId)
     */
    @GetMapping("/{weddingId}/owner/{ownerUserId}/status")
    public ResponseEntity<OwnerWeddingStatusResponse> getOwnerWeddingStatus(@PathVariable Long weddingId,
                                                                            @PathVariable Long ownerUserId) {
        getWeddingForOwnerOrThrow(weddingId, ownerUserId);

        boolean live = weddingService.isWeddingLive(weddingId);
        boolean finished = weddingService.isWeddingFinished(weddingId);
        boolean markedActive = weddingService.isWeddingMarkedActive(weddingId);

        OwnerWeddingStatusResponse resp = new OwnerWeddingStatusResponse();
        resp.setWeddingId(weddingId);
        resp.setOwnerUserId(ownerUserId);
        resp.setLive(live);
        resp.setFinished(finished);
        resp.setMarkedActive(markedActive);
        resp.setCheckedAt(LocalDateTime.now());

        return ResponseEntity.ok(resp);
    }

    // ============================================================
    // 10. פונקציית עזר – בדיקת בעלות על חתונה
    // ============================================================

    /**
     * מחזיר את ה-Wedding אם הוא קיים ושייך ל-ownerUserId.
     * אחרת זורק IllegalArgumentException / IllegalStateException.
     *
     * IllegalArgumentException → 404 (לא נמצאה חתונה)
     * IllegalStateException    → 403 (לא שייך לבעל האירוע הזה)
     */
    private Wedding getWeddingForOwnerOrThrow(Long weddingId, Long ownerUserId) {
        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        if (!Objects.equals(w.getOwnerUserId(), ownerUserId)) {
            throw new IllegalStateException("User is not owner of this wedding");
        }
        return w;
    }

    // ============================================================
    // DTOs פנימיים לבקשות ותשובות JSON
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
     * DTO – בקשת Broadcast.
     */
    public static class BroadcastRequest {
        private String title;
        private String message;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * DTO – תשובת סטטוס חתונה לבעל האירוע.
     */
    public static class OwnerWeddingStatusResponse {
        private Long weddingId;
        private Long ownerUserId;
        private boolean live;
        private boolean finished;
        private boolean markedActive;
        private LocalDateTime checkedAt;

        public Long getWeddingId() { return weddingId; }
        public void setWeddingId(Long weddingId) { this.weddingId = weddingId; }

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

        public boolean isLive() { return live; }
        public void setLive(boolean live) { this.live = live; }

        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }

        public boolean isMarkedActive() { return markedActive; }
        public void setMarkedActive(boolean markedActive) { this.markedActive = markedActive; }

        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    }
}