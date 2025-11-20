package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.service.WeddingService;
import com.example.myproject.service.WeddingService.WeddingStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/weddings")
public class WeddingController {

    private final WeddingService weddingService;

    public WeddingController(WeddingService weddingService) {
        this.weddingService = weddingService;
    }

    // ----------------------------------------------------------
    // 1. יצירת חתונה חדשה
    // ----------------------------------------------------------
    @PostMapping
    public ResponseEntity<Wedding> createWedding(@RequestBody CreateWeddingRequest request) {
        Wedding created = weddingService.createWedding(
                request.getName(),
                request.getStartTime(),
                request.getEndTime(),
                request.getCreatorUserId(),
                request.getBackgroundImage(),
                request.getBackgroundVideo()
        );
        return ResponseEntity.ok(created);
    }

    // ----------------------------------------------------------
    // 2. עדכון חתונה (Partial)
    // ----------------------------------------------------------
    @PutMapping("/{weddingId}")
    public ResponseEntity<Wedding> updateWedding(
            @PathVariable Long weddingId,
            @RequestBody UpdateWeddingRequest request
    ) {
        return weddingService.updateWedding(
                        weddingId,
                        request.getName(),
                        request.getStartTime(),
                        request.getEndTime(),
                        request.getBackgroundImage(),
                        request.getBackgroundVideo(),
                        request.getActive()
                )
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{weddingId}/background")
    public ResponseEntity<Wedding> updateWeddingBackground(
            @PathVariable Long weddingId,
            @RequestBody UpdateBackgroundRequest request
    ) {
        return weddingService.updateWeddingBackground(
                        weddingId,
                        request.getBackgroundImage(),
                        request.getBackgroundVideo()
                )
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ----------------------------------------------------------
    // 3. סגירת חתונה
    // ----------------------------------------------------------
    @PostMapping("/{weddingId}/close")
    public ResponseEntity<Void> closeWedding(@PathVariable Long weddingId) {
        weddingService.closeWeddingManually(weddingId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/close-expired")
    public ResponseEntity<Void> closeExpiredWeddings() {
        weddingService.closeExpiredWeddings();
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------------
    // 4. שליפות
    // ----------------------------------------------------------
    @GetMapping("/{weddingId}")
    public ResponseEntity<Wedding> getWedding(@PathVariable Long weddingId) {
        return weddingService.getWedding(weddingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{weddingId}/active")
    public ResponseEntity<Boolean> isWeddingActive(@PathVariable Long weddingId) {
        boolean active = weddingService.isWeddingActive(weddingId);
        return ResponseEntity.ok(active);
    }

    @GetMapping("/active")
    public ResponseEntity<List<Wedding>> getActiveWeddings() {
        return ResponseEntity.ok(weddingService.getAllActiveWeddings());
    }

    @GetMapping("/inactive")
    public ResponseEntity<List<Wedding>> getInactiveWeddings() {
        return ResponseEntity.ok(weddingService.getInactiveWeddings());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Wedding>> getUpcomingWeddings() {
        return ResponseEntity.ok(weddingService.getUpcomingWeddings());
    }

    @GetMapping("/live-now")
    public ResponseEntity<List<Wedding>> getLiveWeddingsNow() {
        return ResponseEntity.ok(weddingService.getLiveWeddingsNow());
    }

    @GetMapping("/by-creator/{userId}")
    public ResponseEntity<List<Wedding>> getWeddingsByCreator(@PathVariable Long userId) {
        return ResponseEntity.ok(weddingService.getWeddingsCreatedBy(userId));
    }

    @GetMapping("/between")
    public ResponseEntity<List<Wedding>> getWeddingsBetween(
            @RequestParam("from") LocalDateTime from,
            @RequestParam("to") LocalDateTime to
    ) {
        return ResponseEntity.ok(weddingService.getWeddingsBetween(from, to));
    }

    // ----------------------------------------------------------
    // 5. משתתפים וסטטיסטיקות
    // ----------------------------------------------------------
    @PostMapping("/{weddingId}/join")
    public ResponseEntity<Void> joinWedding(
            @PathVariable Long weddingId,
            @RequestParam("userId") Long userId
    ) {
        weddingService.joinWedding(userId, weddingId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{weddingId}/participants")
    public ResponseEntity<List<User>> getParticipants(@PathVariable Long weddingId) {
        return ResponseEntity.ok(weddingService.getParticipants(weddingId));
    }

    @GetMapping("/{weddingId}/stats")
    public ResponseEntity<WeddingStats> getWeddingStats(@PathVariable Long weddingId) {
        return ResponseEntity.ok(weddingService.getWeddingStats(weddingId));
    }

    // ----------------------------------------------------------
    // 6. שידור הודעה לכל משתתפי החתונה
    // ----------------------------------------------------------
    @PostMapping("/{weddingId}/broadcast")
    public ResponseEntity<Void> sendBroadcast(
            @PathVariable Long weddingId,
            @RequestBody BroadcastRequest request
    ) {
        weddingService.sendBroadcast(weddingId, request.getTitle(), request.getMessage());
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------------
    // DTO פנימיים לבקשות
    // ----------------------------------------------------------

    public static class CreateWeddingRequest {
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long creatorUserId;
        private String backgroundImage;
        private String backgroundVideo;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public Long getCreatorUserId() { return creatorUserId; }
        public void setCreatorUserId(Long creatorUserId) { this.creatorUserId = creatorUserId; }

        public String getBackgroundImage() { return backgroundImage; }
        public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

        public String getBackgroundVideo() { return backgroundVideo; }
        public void setBackgroundVideo(String backgroundVideo) { this.backgroundVideo = backgroundVideo; }
    }

    public static class UpdateWeddingRequest {
        private String name;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String backgroundImage;
        private String backgroundVideo;
        private Boolean active;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public String getBackgroundImage() { return backgroundImage; }
        public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

        public String getBackgroundVideo() { return backgroundVideo; }
        public void setBackgroundVideo(String backgroundVideo) { this.backgroundVideo = backgroundVideo; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    public static class UpdateBackgroundRequest {
        private String backgroundImage;
        private String backgroundVideo;

        public String getBackgroundImage() { return backgroundImage; }
        public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

        public String getBackgroundVideo() { return backgroundVideo; }
        public void setBackgroundVideo(String backgroundVideo) { this.backgroundVideo = backgroundVideo; }
    }

    public static class BroadcastRequest {
        private String title;
        private String message;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}