package com.example.myproject.service;

import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import com.example.myproject.repository.MatchRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    public MatchService(MatchRepository matchRepository, UserRepository userRepository) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
    }

    // שליפת כל ההתאמות במערכת
    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    // חישוב התאמות למשתמש לפי קריטריונים
    public List<Match> findMatchesForUser(Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<User> users = userRepository.findAll();
        List<Match> matches = new ArrayList<>();

        for (User other : users) {
            if (other.getId().equals(currentUser.getId())) continue;
            if (currentUser.getGender() == null || other.getGender() == null) continue;
            if (currentUser.getGender().equalsIgnoreCase(other.getGender())) continue;

            double score = calculateMatchScore(currentUser, other);

            Optional<Match> existing = matchRepository
                    .findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                            currentUser.getId(), other.getId(),
                            other.getId(), currentUser.getId());

            if (existing.isEmpty()) {
                Match match = new Match(currentUser, other, score);
                matchRepository.save(match);
                matches.add(match);
            }
        }

        return matches;
    }

    // חישוב ציון התאמה
    private double calculateMatchScore(User u1, User u2) {
        double score = 0;

        if (u1.getAge() != null && u2.getAge() != null) {
            int diff = Math.abs(u1.getAge() - u2.getAge());
            if (diff <= 3) score += 30;
            else if (diff <= 7) score += 20;
            else if (diff <= 10) score += 10;
        }

        if (u1.getCity() != null && u2.getCity() != null &&
                u1.getCity().equalsIgnoreCase(u2.getCity())) {
            score += 25;
        }

        if (u1.getReligiousLevel() != null && u2.getReligiousLevel() != null &&
                u1.getReligiousLevel().equals(u2.getReligiousLevel())) {
            score += 30;
        }

        if (u1.getWedding() != null && u2.getWedding() != null &&
                u1.getWedding().equals(u2.getWedding())) {
            score += 15;
        }

        return score;
    }

    // אישור התאמה ע"י משתמש מסוים
    public String approveMatch(Long matchId, Long userId) {
        Optional<Match> matchOpt = matchRepository.findById(matchId);
        if (matchOpt.isEmpty()) return "Match not found.";

        Match match = matchOpt.get();

        if (match.isMutualApproved()) {
            return "Match already mutually approved!";
        }

        if (match.getUser1().getId().equals(userId)) {
            match.setUser1Approved(true);
        } else if (match.getUser2().getId().equals(userId)) {
            match.setUser2Approved(true);
        } else {
            return "User not part of this match.";
        }

        if (match.isUser1Approved() && match.isUser2Approved()) {
            match.setMutualApproved(true);
        }

        matchRepository.save(match);

        return match.isMutualApproved()
                ? "Both users approved! Match is now mutual 💍"
                : "Approval saved. Waiting for the other user.";
    }
}
