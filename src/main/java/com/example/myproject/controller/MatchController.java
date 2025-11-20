package com.example.myproject.controller;

import com.example.myproject.model.Match;
import com.example.myproject.service.MatchService;
import com.example.myproject.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final NotificationService notificationService;

    public MatchController(MatchService matchService,
                           NotificationService notificationService) {
        this.matchService = matchService;
        this.notificationService = notificationService;
    }

    // ----------------------------------------------------
    // 1. יצירת Match חדש (או קבלת קיים אם כבר יש)
    // ----------------------------------------------------
    @PostMapping
    public ResponseEntity<Match> createOrGetMatch(@RequestBody CreateMatchRequest request) {
        Match match = matchService.createOrGetMatch(
                request.getUserId1(),
                request.getUserId2(),
                request.getMeetingWeddingId(),
                request.getMatchScore()
        );
        return ResponseEntity.ok(match);
    }

    // ----------------------------------------------------
    // אישור Match על־ידי משתמש
    // ----------------------------------------------------
    @PostMapping("/{matchId}/approve")
    public ResponseEntity<Match> approveMatch(
            @PathVariable Long matchId,
            @RequestParam Long userId
    ) {
        // הסרביס מעדכן את ה־Match (מאשר עבור המשתמש הזה, ומחשב mutualApproved)
        Match m = matchService.approveMatch(matchId, userId);

        Long user1Id = m.getUser1().getId();
        Long user2Id = m.getUser2().getId();
        Long otherUserId = user1Id.equals(userId) ? user2Id : user1Id;

        if (!m.isMutualApproved()) {
            // יש כרגע אישור חד־צדדי → התראה לצד השני:
            // recipient = otherUserId, otherUserId = userId
            notificationService.notifyMatchApproved(matchId, otherUserId, userId);
        } else {
            // נהייתה התאמה הדדית → שולחים לשני הצדדים
            notificationService.notifyMatchMutual(matchId, user1Id, user2Id);
        }

        return ResponseEntity.ok(m);
    }

    // ----------------------------------------------------
    // 2. שליפה / סגירה
    // ----------------------------------------------------

    /** שליפת Match לפי מזהה. */
    @GetMapping("/{matchId}")
    public ResponseEntity<Match> getMatchById(@PathVariable Long matchId) {
        return matchService.getMatchById(matchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** סגירת Match אחד (active=false). */
    @PostMapping("/{matchId}/close")
    public ResponseEntity<Void> closeMatch(@PathVariable Long matchId) {
        matchService.closeMatch(matchId);
        return ResponseEntity.ok().build();
    }

    /** סגירת כל ה-Matches שבהם משתמש מסוים משתתף. */
    @PostMapping("/close/user/{userId}")
    public ResponseEntity<Void> closeAllMatchesForUser(@PathVariable Long userId) {
        matchService.closeAllMatchesForUser(userId);
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------
    // 3. שליפות לפי משתמש
    // ----------------------------------------------------

    /** כל ההתאמות *הפעילות* של משתמש (בכל החתונות). */
    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<Match>> getActiveMatchesForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(matchService.getActiveMatchesForUser(userId));
    }

    /** כל ההתאמות של משתמש (פעילות ולא פעילות). */
    @GetMapping("/user/{userId}/all")
    public ResponseEntity<List<Match>> getAllMatchesForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(matchService.getAllMatchesForUser(userId));
    }

    /**
     * כל ההתאמות *הפעילות* של משתמש בחתונה מסוימת.
     * טוב למסך "ההתאמות שלי באירוע X".
     */
    @GetMapping("/user/{userId}/wedding/{weddingId}/active")
    public ResponseEntity<List<Match>> getActiveMatchesForUserInWedding(
            @PathVariable Long userId,
            @PathVariable Long weddingId
    ) {
        return ResponseEntity.ok(
                matchService.getActiveMatchesForUserInWedding(userId, weddingId)
        );
    }

    /**
     * כל ההתאמות *ההדדיות* של משתמש (mutualApproved=true).
     * מיישם את הדרישה: GET /api/matches/user/{userId}/mutual
     */
    @GetMapping("/user/{userId}/mutual")
    public ResponseEntity<List<Match>> getMutualMatchesForUser(@PathVariable Long userId) {
        List<Match> allForUser = matchService.getAllMatchesForUser(userId);

        List<Match> mutual = allForUser.stream()
                .filter(Match::isActive)
                .filter(Match::isMutualApproved)
                .collect(Collectors.toList());

        return ResponseEntity.ok(mutual);
    }

    // ----------------------------------------------------
    // 4. שליפות לפי חתונה
    // ----------------------------------------------------

    /** כל ה-Matches שנוצרו מחתונה מסוימת (לסטטיסטיקות / ניהול). */
    @GetMapping("/wedding/{weddingId}")
    public ResponseEntity<List<Match>> getMatchesByWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(matchService.getMatchesByWedding(weddingId));
    }

    // ----------------------------------------------------
    // 5. מנוע יצירת התאמות עבור חתונה
    // ----------------------------------------------------

    /**
     * יצירת התאמות לכל המשתתפים בחתונה מסוימת.
     * אפשר להעביר minScore בפרמטר query, ברירת מחדל 60.
     *
     * דוגמה קריאה מצד לקוח:
     * POST /api/matches/generate/wedding/5?minScore=55
     */
    @PostMapping("/generate/wedding/{weddingId}")
    public ResponseEntity<List<Match>> generateMatchesForWedding(
            @PathVariable Long weddingId,
            @RequestParam(name = "minScore", defaultValue = "60") double minScore
    ) {
        List<Match> created = matchService.generateMatchesForWedding(weddingId, minScore);
        return ResponseEntity.ok(created);
    }

    // ----------------------------------------------------
    // DTO פנימי לבקשות יצירת Match
    // ----------------------------------------------------

    public static class CreateMatchRequest {
        private Long userId1;
        private Long userId2;
        private Long meetingWeddingId;
        private Double matchScore;

        public Long getUserId1() { return userId1; }
        public void setUserId1(Long userId1) { this.userId1 = userId1; }

        public Long getUserId2() { return userId2; }
        public void setUserId2(Long userId2) { this.userId2 = userId2; }

        public Long getMeetingWeddingId() { return meetingWeddingId; }
        public void setMeetingWeddingId(Long meetingWeddingId) { this.meetingWeddingId = meetingWeddingId; }

        public Double getMatchScore() { return matchScore; }
        public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }
    }
}