package com.example.myproject.service;

import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.repository.MatchRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WeddingRepository weddingRepository;
    private final MatchRepository matchRepository;

    @Autowired
    public UserService(UserRepository userRepository,
                       WeddingRepository weddingRepository,
                       MatchRepository matchRepository) {
        this.userRepository = userRepository;
        this.weddingRepository = weddingRepository;
        this.matchRepository = matchRepository;
    }

    // ✅ יצירת משתמש חדש
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    // ✅ שליפת כל המשתמשים
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // ✅ שליפת משתמש לפי מזהה
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    // ✅ שיוך משתמש לחתונה
    public String assignWedding(Long userId, Long weddingId) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Wedding> weddingOpt = weddingRepository.findById(weddingId);

        if (userOpt.isEmpty() || weddingOpt.isEmpty())
            return "User or Wedding not found.";

        User user = userOpt.get();
        Wedding wedding = weddingOpt.get();

        if (user.getWedding() != null && user.getWedding().getId().equals(weddingId))
            return "User is already part of this wedding.";

        user.setWedding(wedding);
        userRepository.save(user);
        return "User assigned to wedding successfully!";
    }

    // ✅ בקשת גישה גלובלית
    public String requestGlobalAccess(Long userId) {
        return userRepository.findById(userId).map(user -> {
            if (user.isGlobalAccessRequest())
                return "Global access already requested.";

            user.setGlobalAccessRequest(true);
            userRepository.save(user);
            return "Global access request submitted.";
        }).orElse("User not found.");
    }

    // ✅ אישור גישה גלובלית ע"י אדמין
    public String approveGlobalAccess(Long userId) {
        return userRepository.findById(userId).map(user -> {
            if (user.isGlobalAccessApproved())
                return "Global access already approved.";

            user.setGlobalAccessApproved(true);
            user.setGlobalAccessRequest(false);
            userRepository.save(user);
            return "Global access approved successfully.";
        }).orElse("User not found.");
    }

    // ✅ שליפת כל המשתמשים לפי חתונה
    public List<User> getUsersByWedding(Long weddingId) {
        return userRepository.findAll().stream()
                .filter(u -> u.getWedding() != null && u.getWedding().getId().equals(weddingId))
                .collect(Collectors.toList());
    }

    // ✅ שליפת כל ההתאמות של משתמש
    public List<Match> getMatchesForUser(Long userId) {
        return matchRepository.findAll().stream()
                .filter(m -> m.getUser1() != null && m.getUser2() != null)
                .filter(m -> m.getUser1().getId().equals(userId) || m.getUser2().getId().equals(userId))
                .distinct()
                .collect(Collectors.toList());
    }
}
