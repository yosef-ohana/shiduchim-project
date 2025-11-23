package com.example.myproject.controller;

import com.example.myproject.model.Match;
import com.example.myproject.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 🔵 MatchPublicController
 * כל הפעולות הציבוריות / למשתמש רגיל על מַצים:
 * - יצירת Match (גלובלי / חתונה)
 * - שליפת Match לפי ID
 * - רשימות למשתמש: אקטיביים, כללי, הדדיים, לייקים, ממתינים, חסומים, קפואים
 * - אישור / ביטול אישור
 * - ביטול התאמה (unmatch)
 * - חסימה / ביטול חסימה
 * - הקפאה / ביטול הקפאה
 * - בדיקת קיום Match בין שני משתמשים
 * - שליפת Match פעיל בין שני משתמשים
 *
 * כתובת בסיס: /api/matches
 */
@RestController
@RequestMapping("/api/matches")
public class MatchPublicController {

    private final MatchService matchService;

    public MatchPublicController(MatchService matchService) {
        this.matchService = matchService;
    }

    // ============================================================
    // 1. יצירת Match (createOrGetMatch)
    // ============================================================

    /**
     * יצירת Match חדש או החזרת Match קיים בין שני משתמשים.
     * מתאים ליצירת התאמה גלובלית או מתוך חתונה (meetingWeddingId).
     *
     * דוגמה ל־JSON:
     * {
     *   "userId1": 10,
     *   "userId2": 25,
     *   "meetingWeddingId": 5,
     *   "originWeddingId": 5,
     *   "matchScore": 80.5,
     *   "matchSource": "wedding"
     * }
     */
    @PostMapping
    public ResponseEntity<MatchDto> createOrGetMatch(@RequestBody CreateMatchRequest request) {
        if (request.getUserId1() == null || request.getUserId2() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match match = matchService.createOrGetMatch(
                    request.getUserId1(),
                    request.getUserId2(),
                    request.getMeetingWeddingId(),
                    request.getOriginWeddingId(),
                    request.getMatchScore(),
                    request.getMatchSource()
            );
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 2. שליפת Match לפי ID
    // ============================================================

    /**
     * שליפת Match בודד לפי מזהה.
     */
    @GetMapping("/{matchId}")
    public ResponseEntity<MatchDto> getMatchById(@PathVariable Long matchId) {
        Optional<Match> matchOpt = matchService.getMatchById(matchId);
        return matchOpt
                .map(match -> ResponseEntity.ok(toDto(match)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ============================================================
    // 3. רשימות למשתמש – אקטיבי / כללי
    // ============================================================

    /**
     * כל המַצים האקטיביים (active=true) של משתמש.
     */
    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<MatchDto>> getActiveMatchesForUser(@PathVariable Long userId) {
        List<Match> matches = matchService.getActiveMatchesForUser(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    /**
     * כל המַצים (אקטיביים + לא אקטיביים) של משתמש.
     */
    @GetMapping("/user/{userId}/all")
    public ResponseEntity<List<MatchDto>> getAllMatchesForUser(@PathVariable Long userId) {
        List<Match> matches = matchService.getAllMatchesForUser(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    // ============================================================
    // 4. התאמות הדדיות למשתמש
    // ============================================================

    /**
     * רשימת כל המַצים ההדדיים והפעילים של המשתמש.
     * (mutualApproved=true && active=true)
     * מסך "התאמות הדדיות".
     */
    @GetMapping("/user/{userId}/mutual")
    public ResponseEntity<List<MatchDto>> getMutualMatchesForUser(@PathVariable Long userId) {
        List<Match> matches = matchService.getMutualMatchesListForUser(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    // ============================================================
    // 5. רשימות "אני עשיתי לייק" / "מחכים לי" / חסומים / קפואים
    // ============================================================

    /**
     * מַצים שבהם המשתמש כבר אישר, אבל זה עדיין לא הדדי.
     * מסך "התאמות ששלחתי / אני מחכה להם".
     */
    @GetMapping("/user/{userId}/liked")
    public ResponseEntity<List<MatchDto>> getMatchesILiked(@PathVariable Long userId) {
        List<Match> matches = matchService.getMatchesILiked(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    /**
     * מַצים שבהם הצד השני כבר אישר, ואני עדיין לא אישרתי.
     * מסך "ממתינים לאישור שלי".
     */
    @GetMapping("/user/{userId}/waiting")
    public ResponseEntity<List<MatchDto>> getMatchesWaitingForMyApproval(@PathVariable Long userId) {
        List<Match> matches = matchService.getMatchesWaitingForMyApproval(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    /**
     * רשימת מַצים חסומים (blocked=true) של המשתמש.
     */
    @GetMapping("/user/{userId}/blocked")
    public ResponseEntity<List<MatchDto>> getBlockedMatchesForUser(@PathVariable Long userId) {
        List<Match> matches = matchService.getBlockedMatchesForUser(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    /**
     * רשימת מַצים קפואים (frozen=true) של המשתמש.
     */
    @GetMapping("/user/{userId}/frozen")
    public ResponseEntity<List<MatchDto>> getFrozenMatchesForUser(@PathVariable Long userId) {
        List<Match> matches = matchService.getFrozenMatchesForUser(userId);
        return ResponseEntity.ok(toDtoList(matches));
    }

    // ============================================================
    // 6. אישור / ביטול אישור Match (Mutual Flow)
    // ============================================================

    /**
     * אישור Match ע"י אחד הצדדים.
     * JSON:
     * {
     *   "userId": 10
     * }
     */
    @PostMapping("/{matchId}/approve")
    public ResponseEntity<MatchDto> approveMatch(@PathVariable Long matchId,
                                                 @RequestBody ApproveMatchRequest request) {
        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match match = matchService.approveMatch(matchId, request.getUserId());
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            // "Match not found" / "User does not belong to this match"
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * ביטול אישור Match ע"י אחד הצדדים.
     * JSON:
     * {
     *   "userId": 10
     * }
     */
    @PostMapping("/{matchId}/unapprove")
    public ResponseEntity<MatchDto> unapproveMatch(@PathVariable Long matchId,
                                                   @RequestBody ApproveMatchRequest request) {
        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match match = matchService.unapproveMatch(matchId, request.getUserId());
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 7. ביטול התאמה (Unmatch)
    // ============================================================

    /**
     * ביטול התאמה:
     * - איפוס אישורים
     * - mutualApproved=false
     * - chatOpened=false
     * - active=false
     *
     * JSON:
     * {
     *   "userId": 10
     * }
     */
    @PostMapping("/{matchId}/unmatch")
    public ResponseEntity<MatchDto> unmatch(@PathVariable Long matchId,
                                            @RequestBody ApproveMatchRequest request) {
        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Match match = matchService.unmatch(matchId, request.getUserId());
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 8. חסימה / ביטול חסימה
    // ============================================================

    /**
     * חסימת Match:
     * - blocked=true, active=false, mutualApproved=false, chatOpened=false
     * כרגע ללא בדיקת בעלות (יוסף יוסיף בעתיד Security / JWT).
     */
    @PostMapping("/{matchId}/block")
    public ResponseEntity<MatchDto> blockMatch(@PathVariable Long matchId) {
        try {
            Match match = matchService.blockMatch(matchId);
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * ביטול חסימה.
     */
    @PostMapping("/{matchId}/unblock")
    public ResponseEntity<MatchDto> unblockMatch(@PathVariable Long matchId) {
        try {
            Match match = matchService.unblockMatch(matchId);
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 9. הקפאה / ביטול הקפאה
    // ============================================================

    /**
     * הקפאת Match – עם סיבת הקפאה אופציונלית.
     * JSON:
     * {
     *   "reason": "החלטת מערכת / מנהל"
     * }
     */
    @PostMapping("/{matchId}/freeze")
    public ResponseEntity<MatchDto> freezeMatch(@PathVariable Long matchId,
                                                @RequestBody FreezeMatchRequest request) {
        try {
            Match match = matchService.freezeMatch(matchId, request.getReason());
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * ביטול הקפאה.
     */
    @PostMapping("/{matchId}/unfreeze")
    public ResponseEntity<MatchDto> unfreezeMatch(@PathVariable Long matchId) {
        try {
            Match match = matchService.unfreezeMatch(matchId);
            return ResponseEntity.ok(toDto(match));
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Match not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 10. בדיקת קיום Match בין שני משתמשים
    // ============================================================

    /**
     * האם קיים Match בין שני משתמשים (באחד הכיוונים).
     * GET /api/matches/exists-between?userId1=10&userId2=25
     */
    @GetMapping("/exists-between")
    public ResponseEntity<ExistsResponse> existsMatchBetween(@RequestParam Long userId1,
                                                             @RequestParam Long userId2) {
        boolean exists = matchService.existsMatchBetween(userId1, userId2);
        ExistsResponse response = new ExistsResponse(exists);
        return ResponseEntity.ok(response);
    }

    /**
     * שליפת Match פעיל בין שני משתמשים (אם קיים).
     * GET /api/matches/active-between?userId1=10&userId2=25
     */
    @GetMapping("/active-between")
    public ResponseEntity<MatchDto> getActiveMatchBetween(@RequestParam Long userId1,
                                                          @RequestParam Long userId2) {
        Optional<Match> matchOpt = matchService.findActiveMatch(userId1, userId2);
        return matchOpt
                .map(match -> ResponseEntity.ok(toDto(match)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // ============================================================
    // 11. Mapperים פנימיים ל־DTO
    // ============================================================

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

    private List<MatchDto> toDtoList(List<Match> matches) {
        return matches.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 12. DTO פנימיים
    // ============================================================

    /**
     * DTO ליצירת / שליפת Match.
     */
    public static class CreateMatchRequest {
        private Long userId1;
        private Long userId2;
        private Long meetingWeddingId;
        private Long originWeddingId;
        private Double matchScore;
        private String matchSource;

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

        public String getMatchSource() { return matchSource; }
        public void setMatchSource(String matchSource) { this.matchSource = matchSource; }
    }

    /**
     * DTO לפעולות אישור / ביטול / Unmatch.
     */
    public static class ApproveMatchRequest {
        private Long userId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    /**
     * DTO להקפאה – סיבת הקפאה.
     */
    public static class FreezeMatchRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * DTO לפלט של "קיים / לא קיים".
     */
    public static class ExistsResponse {
        private boolean exists;

        public ExistsResponse() { }
        public ExistsResponse(boolean exists) { this.exists = exists; }

        public boolean isExists() { return exists; }
        public void setExists(boolean exists) { this.exists = exists; }
    }

    /**
     * DTO ראשי ל־Match – מה שנחזיר ל־Frontend.
     */
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