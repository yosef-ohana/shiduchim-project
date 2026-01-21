package com.example.myproject.controller;

import com.example.myproject.model.Match;
import com.example.myproject.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matches/admin")
@CrossOrigin(origins = "*")
public class MatchAdminController {

    private final MatchService matchService;

    public MatchAdminController(MatchService matchService) {
        this.matchService = matchService;
    }

    // ============================================================
    // DTOs פנימיים למחזרים ולבקשות
    // ============================================================

    public static class AdminCreateMatchRequest {
        private Long userId1;
        private Long userId2;
        private Long meetingWeddingId;
        private Long originWeddingId;
        private Double matchScore;
        private String source;

        public Long getUserId1() { return userId1; }
        public void setUserId1(Long userId1) { this.userId1 = userId1; }

        public Long getUserId2() { return userId2; }
        public void setUserId2(Long userId2) { this.userId2 = userId2; }

        public Long getMeetingWeddingId() { return meetingWeddingId; }
        public void setMeetingWeddingId(Long meetingWeddingId) { this.meetingWeddingId = meetingWeddingId; }

        public Long getOriginWeddingId() { return originWeddingId; }
        public void setOriginWeddingId(Long originWeddingId) { this.originWeddingId = originWeddingId; }

        public Double getMatchScore() { return matchScore; }
        public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class UpdateMatchSourceRequest {
        private String source;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class UpdateMatchScoreRequest {
        private Double score;

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }

    public static class UpdateOriginWeddingRequest {
        private Long originWeddingId;

        public Long getOriginWeddingId() { return originWeddingId; }
        public void setOriginWeddingId(Long originWeddingId) { this.originWeddingId = originWeddingId; }
    }

    public static class ExistsMatchRequest {
        private Long userId1;
        private Long userId2;

        public Long getUserId1() { return userId1; }
        public void setUserId1(Long userId1) { this.userId1 = userId1; }

        public Long getUserId2() { return userId2; }
        public void setUserId2(Long userId2) { this.userId2 = userId2; }
    }

    public static class MatchAdminResponse {
        private Long id;
        private Long user1Id;
        private Long user2Id;

        private Long meetingWeddingId;
        private Long originWeddingId;

        private Double matchScore;
        private String matchSource;

        private boolean user1Approved;
        private boolean user2Approved;
        private boolean mutualApproved;

        private boolean active;
        private boolean blocked;
        private boolean frozen;
        private boolean chatOpened;

        private Integer unreadCount;
        private String freezeReason;

        private LocalDateTime lastMessageAt;
        private boolean readByUser1;
        private boolean readByUser2;
        private boolean firstMessageSent;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Getters & Setters

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

        public String getMatchSource() { return matchSource; }
        public void setMatchSource(String matchSource) { this.matchSource = matchSource; }

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

        public LocalDateTime getLastMessageAt() { return lastMessageAt; }
        public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

        public boolean isReadByUser1() { return readByUser1; }
        public void setReadByUser1(boolean readByUser1) { this.readByUser1 = readByUser1; }

        public boolean isReadByUser2() { return readByUser2; }
        public void setReadByUser2(boolean readByUser2) { this.readByUser2 = readByUser2; }

        public boolean isFirstMessageSent() { return firstMessageSent; }
        public void setFirstMessageSent(boolean firstMessageSent) { this.firstMessageSent = firstMessageSent; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    private MatchAdminResponse toAdminResponse(Match m) {
        MatchAdminResponse dto = new MatchAdminResponse();

        dto.setId(m.getId());
        dto.setUser1Id(m.getUser1() != null ? m.getUser1().getId() : null);
        dto.setUser2Id(m.getUser2() != null ? m.getUser2().getId() : null);

        dto.setMeetingWeddingId(m.getMeetingWeddingId());
        dto.setOriginWeddingId(m.getOriginWeddingId());

        dto.setMatchScore(m.getMatchScore());
        dto.setMatchSource(m.getMatchSource());

        dto.setUser1Approved(m.isUser1Approved());
        dto.setUser2Approved(m.isUser2Approved());
        dto.setMutualApproved(m.isMutualApproved());

        dto.setActive(m.isActive());
        dto.setBlocked(m.isBlocked());
        dto.setFrozen(m.isFrozen());
        dto.setChatOpened(m.isChatOpened());

        dto.setUnreadCount(m.getUnreadCount());
        dto.setFreezeReason(m.getFreezeReason());

        dto.setLastMessageAt(m.getLastMessageAt());
        dto.setReadByUser1(m.isReadByUser1());
        dto.setReadByUser2(m.isReadByUser2());
        dto.setFirstMessageSent(m.isFirstMessageSent());

        dto.setCreatedAt(m.getCreatedAt());
        dto.setUpdatedAt(m.getUpdatedAt());

        return dto;
    }

    // ============================================================
    // 1. שליפה בסיסית / גלובלית
    // ============================================================

    /** שליפת Match לפי ID (לשימוש דשבורד אדמין). */
    @GetMapping("/{matchId}")
    public ResponseEntity<MatchAdminResponse> getMatchById(@PathVariable Long matchId) {
        Optional<Match> opt = matchService.getMatchById(matchId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(toAdminResponse(opt.get()));
    }

    /** כל המַצים הפעילים במערכת. */
    @GetMapping("/active")
    public ResponseEntity<List<MatchAdminResponse>> getAllActiveMatches() {
        List<MatchAdminResponse> list = matchService.getAllActiveMatches()
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** כל המַצים הלא־פעילים (נסגרו / בוטלו). */
    @GetMapping("/inactive")
    public ResponseEntity<List<MatchAdminResponse>> getAllInactiveMatches() {
        List<MatchAdminResponse> list = matchService.getAllInactiveMatches()
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** כל המַצים החסומים במערכת. */
    @GetMapping("/blocked")
    public ResponseEntity<List<MatchAdminResponse>> getBlockedMatches() {
        List<MatchAdminResponse> list = matchService.getBlockedMatches()
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** כל המַצים המוקפאים במערכת. */
    @GetMapping("/frozen")
    public ResponseEntity<List<MatchAdminResponse>> getFrozenMatches() {
        List<MatchAdminResponse> list = matchService.getFrozenMatches()
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** כל המַצים עם צ'אט פתוח. */
    @GetMapping("/open-chat")
    public ResponseEntity<List<MatchAdminResponse>> getMatchesWithOpenChat() {
        List<MatchAdminResponse> list = matchService.getMatchesWithOpenChat()
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 2. לפי מקור התאמה (matchSource)
    // ============================================================

    /** כל המַצים לפי מקור התאמה (wedding / global / admin / ai ...). */
    @GetMapping("/source/{source}")
    public ResponseEntity<List<MatchAdminResponse>> getMatchesBySource(@PathVariable String source) {
        List<MatchAdminResponse> list = matchService.getMatchesBySource(source)
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** מַצים פעילים לפי מקור. */
    @GetMapping("/source/{source}/active")
    public ResponseEntity<List<MatchAdminResponse>> getActiveMatchesBySource(@PathVariable String source) {
        List<MatchAdminResponse> list = matchService.getActiveMatchesBySource(source)
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** מַצים הדדיים + פעילים לפי מקור. */
    @GetMapping("/source/{source}/mutual")
    public ResponseEntity<List<MatchAdminResponse>> getMutualMatchesBySource(@PathVariable String source) {
        List<MatchAdminResponse> list = matchService.getMutualMatchesBySource(source)
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 3. לפי ניקוד (matchScore)
    // ============================================================

    /** כל המַצים עם score >= minScore. */
    @GetMapping("/score/min/{minScore}")
    public ResponseEntity<List<MatchAdminResponse>> getMatchesWithMinScore(@PathVariable double minScore) {
        List<MatchAdminResponse> list = matchService.getMatchesWithMinScore(minScore)
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** כל המַצים עם score מדויק. */
    @GetMapping("/score/exact/{score}")
    public ResponseEntity<List<MatchAdminResponse>> getMatchesByExactScore(@PathVariable double score) {
        List<MatchAdminResponse> list = matchService.getMatchesByExactScore(score)
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 4. פעולות אדמין חזקות (יצירה / מחיקה / עדכונים)
    // ============================================================

    /**
     * יצירת Match ע"י אדמין (override מלא).
     * משתמש ב־adminForceCreateMatch כדי למנוע כפילויות.
     */
    @PostMapping("/create")
    public ResponseEntity<MatchAdminResponse> adminForceCreateMatch(@RequestBody AdminCreateMatchRequest req) {
        if (req.getUserId1() == null || req.getUserId2() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match m = matchService.adminForceCreateMatch(
                    req.getUserId1(),
                    req.getUserId2(),
                    req.getMeetingWeddingId(),
                    req.getOriginWeddingId(),
                    req.getMatchScore(),
                    req.getSource()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(toAdminResponse(m));
        } catch (IllegalArgumentException ex) {
            // למשל "User not found" או "Match must be between two different users"
            return ResponseEntity.badRequest().build();
        }
    }

    /** מחיקה מלאה (Hard Delete) של Match. */
    @DeleteMapping("/{matchId}")
    public ResponseEntity<Void> deleteMatchPermanently(@PathVariable Long matchId) {
        try {
            matchService.deleteMatchPermanently(matchId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** עדכון מקור ההתאמה (matchSource) ע"י אדמין. */
    @PatchMapping("/{matchId}/source")
    public ResponseEntity<MatchAdminResponse> updateMatchSource(@PathVariable Long matchId,
                                                                @RequestBody UpdateMatchSourceRequest req) {

        if (req.getSource() == null || req.getSource().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match m = matchService.updateMatchSource(matchId, req.getSource());
            return ResponseEntity.ok(toAdminResponse(m));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** עדכון ציון התאמה (matchScore) ידני ע"י אדמין. */
    @PatchMapping("/{matchId}/score")
    public ResponseEntity<MatchAdminResponse> updateMatchScore(@PathVariable Long matchId,
                                                               @RequestBody UpdateMatchScoreRequest req) {

        if (req.getScore() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match m = matchService.updateMatchScore(matchId, req.getScore());
            return ResponseEntity.ok(toAdminResponse(m));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** עדכון originWeddingId למַץ קיים. */
    @PatchMapping("/{matchId}/origin-wedding")
    public ResponseEntity<MatchAdminResponse> updateOriginWedding(@PathVariable Long matchId,
                                                                  @RequestBody UpdateOriginWeddingRequest req) {

        if (req.getOriginWeddingId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match m = matchService.updateOriginWedding(matchId, req.getOriginWeddingId());
            return ResponseEntity.ok(toAdminResponse(m));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 5. סגירה לוגית ו־Bulk Operations
    // ============================================================

    /** סגירה לוגית של Match (active=false). */
    @PostMapping("/{matchId}/close")
    public ResponseEntity<Void> closeMatch(@PathVariable Long matchId) {
        // closeMatch לא זורק אם לא קיים – הוא פשוט ifPresent
        matchService.closeMatch(matchId);
        return ResponseEntity.ok().build();
    }

    /** סגירת כל המַצים שבהם המשתמש משתתף (למשל מחיקת חשבון). */
    @PostMapping("/close/user/{userId}")
    public ResponseEntity<Void> closeAllMatchesForUser(@PathVariable Long userId) {
        matchService.closeAllMatchesForUser(userId);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 6. בדיקות קיום / איתור בין שני משתמשים
    // ============================================================

    /** בדיקה האם קיים Match בין שני משתמשים (באחד הכיוונים). */
    @PostMapping("/exists")
    public ResponseEntity<Boolean> existsMatchBetween(@RequestBody ExistsMatchRequest req) {
        if (req.getUserId1() == null || req.getUserId2() == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean exists = matchService.existsMatchBetween(req.getUserId1(), req.getUserId2());
        return ResponseEntity.ok(exists);
    }

    /** מציאת Match פעיל בין שני משתמשים (אם קיים). */
    @PostMapping("/find-active-between")
    public ResponseEntity<MatchAdminResponse> findActiveMatch(@RequestBody ExistsMatchRequest req) {
        if (req.getUserId1() == null || req.getUserId2() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Match> opt = matchService.findActiveMatch(req.getUserId1(), req.getUserId2());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(toAdminResponse(opt.get()));
    }

    // ============================================================
    // 7. originWedding – "איפה נפגשו לראשונה"
    // ============================================================

    /** כל המַצים שה-originWeddingId שלהם = weddingId. */
    @GetMapping("/origin-wedding/{weddingId}")
    public ResponseEntity<List<MatchAdminResponse>> getMatchesOriginatedInWedding(@PathVariable Long weddingId) {
        List<MatchAdminResponse> list = matchService.getMatchesOriginatedInWedding(weddingId)
                .stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}