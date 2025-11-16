package com.example.myproject.controller;

import com.example.myproject.model.Match;
import com.example.myproject.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    // שליפת כל ההתאמות
    @GetMapping("/all")
    public ResponseEntity<List<Match>> getAllMatches() {
        return ResponseEntity.ok(matchService.getAllMatches());
    }

    // שליפת התאמות למשתמש מסוים
    @GetMapping("/{userId}")
    public ResponseEntity<List<Match>> getMatchesForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(matchService.findMatchesForUser(userId));
    }

    // אישור התאמה ע"י משתמש מסוים
    @PostMapping("/{matchId}/approve/{userId}")
    public ResponseEntity<String> approveMatch(
            @PathVariable Long matchId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(matchService.approveMatch(matchId, userId));
    }
}
