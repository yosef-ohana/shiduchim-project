package com.example.myproject.controller;

import com.example.myproject.model.Match;
import com.example.myproject.service.MatchService;
import com.example.myproject.service.WeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 🔵 MatchWeddingController
 * לוגיקת Match בתוך חתונה (Wedding Context)
 *
 * כולל:
 *  • generateMatchesForWedding – יצירת התאמות בין כל המשתתפים
 *  • getMatchesByWedding – כל המצים בחתונה
 *  • getActiveMatchesInWedding – פעילים בלבד
 *  • getMatchesForUserInWedding – התאמות של משתמש בחתונה
 *  • getMutualMatchesForUserInWedding – הדדיים בתוך החתונה
 *  • getMatchesOriginatedInWedding – כל התאמות שהמקור הראשוני שלהן בחתונה זו
 *
 * בסיס כתובת: /api/matches/wedding
 */
@RestController
@RequestMapping("/api/matches/wedding")
public class MatchWeddingController {

    private final MatchService matchService;
    private final WeddingService weddingService;

    public MatchWeddingController(MatchService matchService,
                                  WeddingService weddingService) {
        this.matchService = matchService;
        this.weddingService = weddingService;
    }

    // ============================================================
    // 1. יצירת התאמות לכל המשתתפים בחתונה (Scoring Engine)
    // ============================================================

    /**
     * יצירת התאמות לפי אפיון 2025:
     * - מביא את כל המשתתפים לפי lastWeddingId
     * - מחשב ציון התאמה בין כל זוג
     * - יוצר Match רק אם score >= minScore
     *
     * JSON:
     * {
     *   "minScore": 40.0
     * }
     */
    @PostMapping("/{weddingId}/generate")
    public ResponseEntity<List<MatchDto>> generateMatches(
            @PathVariable Long weddingId,
            @RequestBody(required = false) GenerateRequest req) {

        double minScore = (req != null && req.getMinScore() != null)
                ? req.getMinScore()
                : 0.0;

        try {
            List<Match> created = matchService.generateMatchesForWedding(weddingId, minScore);
            return ResponseEntity.ok(toList(created));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 2. כל המַצים שקרו בחתונה (meetingWeddingId = weddingId)
    // ============================================================

    @GetMapping("/{weddingId}")
    public ResponseEntity<List<MatchDto>> getMatchesByWedding(@PathVariable Long weddingId) {
        List<Match> matches = matchService.getMatchesByWedding(weddingId);
        return ResponseEntity.ok(toList(matches));
    }

    // ============================================================
    // 3. כל המַצים הפעילים בחתונה
    // ============================================================

    @GetMapping("/{weddingId}/active")
    public ResponseEntity<List<MatchDto>> getActiveMatchesByWedding(@PathVariable Long weddingId) {

        List<Match> matches = matchService.getMatchesByWedding(weddingId)
                .stream()
                .filter(Match::isActive)
                .collect(Collectors.toList());

        return ResponseEntity.ok(toList(matches));
    }

    // ============================================================
    // 4. כל ההתאמות של משתמש בחתונה
    // ============================================================

    @GetMapping("/{weddingId}/user/{userId}")
    public ResponseEntity<List<MatchDto>> getMatchesForUserInWedding(@PathVariable Long weddingId,
                                                                     @PathVariable Long userId) {

        List<Match> matches = matchService.getActiveMatchesForUserInWedding(userId, weddingId);
        return ResponseEntity.ok(toList(matches));
    }

    // ============================================================
    // 5. כל ההתאמות ההדדיות של משתמש בחתונה
    // ============================================================

    @GetMapping("/{weddingId}/user/{userId}/mutual")
    public ResponseEntity<List<MatchDto>> getMutualMatchesForUserInWedding(
            @PathVariable Long weddingId,
            @PathVariable Long userId) {

        List<Match> matches = matchService.getMutualMatchesForUserInWedding(userId, weddingId);
        return ResponseEntity.ok(toList(matches));
    }

    // ============================================================
    // 6. כל ההתאמות שהמקור הראשוני שלהן (originWeddingId) = weddingId
    // ============================================================

    @GetMapping("/{weddingId}/origin")
    public ResponseEntity<List<MatchDto>> getOriginMatches(@PathVariable Long weddingId) {

        List<Match> matches = matchService.getMatchesOriginatedInWedding(weddingId);
        return ResponseEntity.ok(toList(matches));
    }

    // ============================================================
    // DTO + Mapper פנימיים
    // ============================================================

    private List<MatchDto> toList(List<Match> list) {
        return list.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private MatchDto toDto(Match m) {
        MatchDto dto = new MatchDto();
        dto.setId(m.getId());
        dto.setUser1Id(m.getUser1() != null ? m.getUser1().getId() : null);
        dto.setUser2Id(m.getUser2() != null ? m.getUser2().getId() : null);

        dto.setMeetingWeddingId(m.getMeetingWeddingId());
        dto.setOriginWeddingId(m.getOriginWeddingId());
        dto.setMatchScore(m.getMatchScore());

        dto.setUser1Approved(m.isUser1Approved());
        dto.setUser2Approved(m.isUser2Approved());
        dto.setMutualApproved(m.isMutualApproved());

        dto.setActive(m.isActive());
        dto.setBlocked(m.isBlocked());
        dto.setFrozen(m.isFrozen());
        dto.setChatOpened(m.isChatOpened());

        dto.setUnreadCount(m.getUnreadCount());
        dto.setFreezeReason(m.getFreezeReason());
        dto.setMatchSource(m.getMatchSource());
        dto.setLastMessageAt(m.getLastMessageAt());

        dto.setReadByUser1(m.isReadByUser1());
        dto.setReadByUser2(m.isReadByUser2());
        dto.setFirstMessageSent(m.isFirstMessageSent());

        dto.setCreatedAt(m.getCreatedAt());
        dto.setUpdatedAt(m.getUpdatedAt());
        return dto;
    }

    // ===== DTO Request + Response =====

    public static class GenerateRequest {
        private Double minScore;

        public Double getMinScore() { return minScore; }
        public void setMinScore(Double minScore) { this.minScore = minScore; }
    }

    public static class MatchDto {
        private Long id;
        private Long user1Id;
        private Long user2Id;
        private Long meetingWeddingId;
        private Long originWeddingId;
        private Double matchScore;

        private boolean user1Approved;
        private boolean user2Approved;
        private boolean mutualApproved;

        private boolean active;
        private boolean blocked;
        private boolean frozen;
        private boolean chatOpened;

        private Integer unreadCount;
        private String freezeReason;
        private String matchSource;
        private java.time.LocalDateTime lastMessageAt;

        private boolean readByUser1;
        private boolean readByUser2;
        private boolean firstMessageSent;

        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;

        // getters + setters...
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getUser1Id() { return user1Id; }
        public void setUser1Id(Long user1Id) { this.user1Id = user1Id; }

        public Long getUser2Id() { return user2Id; }
        public void setUser2Id(Long user2Id) { this.user2Id = user2Id; }

        public Long getMeetingWeddingId() { return meetingWeddingId; }
        public void setMeetingWeddingId(Long meetingWeddingId) { this.meetingWeddingId = meetingWeddingId; }

        public Long getOriginWeddingId() { return originWeddingId; }
        public void setOriginWeddingId(Long originWeddingId) { this.originWeddingId = originWeddingId; }

        public Double getMatchScore() { return matchScore; }
        public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

        public boolean isUser1Approved() { return user1Approved; }
        public void setUser1Approved(boolean user1Approved) { this.user1Approved = user1Approved; }

        public boolean isUser2Approved() { return user2Approved; }
        public void setUser2Approved(boolean user2Approved) { this.user2Approved = user2Approved; }

        public boolean isMutualApproved() { return mutualApproved; }
        public void setMutualApproved(boolean mutualApproved) { this.mutualApproved = mutualApproved; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }

        public boolean isFrozen() { return frozen; }
        public void setFrozen(boolean frozen) { this.frozen = frozen; }

        public boolean isChatOpened() { return chatOpened; }
        public void setChatOpened(boolean chatOpened) { this.chatOpened = chatOpened; }

        public Integer getUnreadCount() { return unreadCount; }
        public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }

        public String getFreezeReason() { return freezeReason; }
        public void setFreezeReason(String freezeReason) { this.freezeReason = freezeReason; }

        public String getMatchSource() { return matchSource; }
        public void setMatchSource(String matchSource) { this.matchSource = matchSource; }

        public java.time.LocalDateTime getLastMessageAt() { return lastMessageAt; }
        public void setLastMessageAt(java.time.LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

        public boolean isReadByUser1() { return readByUser1; }
        public void setReadByUser1(boolean readByUser1) { this.readByUser1 = readByUser1; }

        public boolean isReadByUser2() { return readByUser2; }
        public void setReadByUser2(boolean readByUser2) { this.readByUser2 = readByUser2; }

        public boolean isFirstMessageSent() { return firstMessageSent; }
        public void setFirstMessageSent(boolean firstMessageSent) { this.firstMessageSent = firstMessageSent; }

        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}