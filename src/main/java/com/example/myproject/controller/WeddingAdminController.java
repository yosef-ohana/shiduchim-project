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
            return ResponseEntity.badRequest().build();
        }
        if (request.getStartTime() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Wedding w = weddingService.createWeddingByAdmin(
                    request.getName(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getAdminUserId(),
                    request.getOwnerUserId(),              // ← ערך תקין כעת
                    request.getBackgroundImageUrl(),
                    request.getBackgroundVideoUrl()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(w);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 2. עדכון חתונה ע"י אדמין (עדכון רגיל)
    // ============================================================

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
    // 3. מחיקת חתונה ע"י אדמין
    // ============================================================

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
    // 4. עדכון רקע
    // ============================================================

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

    @GetMapping("/{weddingId}/background/status")
    public ResponseEntity<AdminFullUpdateWeddingRequest.BackgroundStatusResponse> getWeddingBackgroundStatus(@PathVariable Long weddingId) {
        try {
            Wedding wedding = weddingService.getWeddingById(weddingId);

            AdminFullUpdateWeddingRequest.BackgroundStatusResponse resp =
                    new AdminFullUpdateWeddingRequest.BackgroundStatusResponse();
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

    @DeleteMapping("/{weddingId}/background")
    public ResponseEntity<Void> resetWeddingBackground(@PathVariable Long weddingId) {
        try {
            weddingService.updateWeddingBackground(weddingId, "", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 5. סטטיסטיקות
    // ============================================================

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

    @GetMapping("/live")
    public ResponseEntity<List<Wedding>> getLiveWeddings() {
        List<Wedding> list = weddingService.getLiveWeddings();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Wedding>> getUpcomingWeddings() {
        List<Wedding> list = weddingService.getUpcomingWeddings();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/finished")
    public ResponseEntity<List<Wedding>> getFinishedWeddings() {
        List<Wedding> list = weddingService.getFinishedWeddings();
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 7. משתתפים – נוכחיים / היסטוריים
    // ============================================================

    @GetMapping("/{weddingId}/participants/current")
    public ResponseEntity<List<User>> getCurrentParticipants(@PathVariable Long weddingId) {
        List<User> list = weddingService.getCurrentParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{weddingId}/participants/history")
    public ResponseEntity<List<User>> getHistoricalParticipants(@PathVariable Long weddingId) {
        List<User> list = weddingService.getHistoricalParticipants(weddingId);
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 8. סגירת חתונות – ידני / המוני
    // ============================================================

    @PostMapping("/{weddingId}/close")
    public ResponseEntity<Void> closeWeddingManually(@PathVariable Long weddingId) {
        try {
            weddingService.closeWeddingManually(weddingId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/close-expired")
    public ResponseEntity<Void> closeExpiredWeddings() {
        weddingService.closeExpiredWeddings();
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 9. Broadcast + הודעת "האירוע הסתיים"
    // ============================================================

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
    // 10. בדיקות מצב חתונה
    // ============================================================

    @GetMapping("/{weddingId}/live")
    public ResponseEntity<Boolean> isWeddingLive(@PathVariable Long weddingId) {
        boolean live = weddingService.isWeddingLive(weddingId);
        return ResponseEntity.ok(live);
    }

    @GetMapping("/{weddingId}/finished-flag")
    public ResponseEntity<Boolean> isWeddingFinished(@PathVariable Long weddingId) {
        boolean finished = weddingService.isWeddingFinished(weddingId);
        return ResponseEntity.ok(finished);
    }

    @GetMapping("/{weddingId}/active-flag")
    public ResponseEntity<Boolean> isWeddingMarkedActive(@PathVariable Long weddingId) {
        boolean active = weddingService.isWeddingMarkedActive(weddingId);
        return ResponseEntity.ok(active);
    }

    // ============================================================
    // 11. עדכון מלא + Hard Delete
    // ============================================================

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

    @DeleteMapping("/{weddingId}/hard")
    public ResponseEntity<Void> hardDeleteWedding(@PathVariable Long weddingId) {
        weddingService.deleteWedding(weddingId);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // DTOs
    // ============================================================

    public static class AdminCreateWeddingRequest {
        private Long adminUserId;
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String backgroundImageUrl;
        private String backgroundVideoUrl;

        private Long ownerUserId; // ← נוסף (נדרש ל-service)

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

        public Long getOwnerUserId() { return ownerUserId; }      // ← חדש
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; } // ← חדש
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