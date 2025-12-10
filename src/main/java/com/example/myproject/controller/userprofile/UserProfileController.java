package com.example.myproject.controller.userprofile;

import com.example.myproject.dto.UserProfileResponse;
import com.example.myproject.service.User.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 🔵 שליפת פרופיל משתמש מלא (לפי אפיון 2025)
     *
     * GET /api/users/{userId}/profile
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getFullProfile(@PathVariable Long userId) {
        try {
            UserProfileResponse resp = userService.getFullUserProfile(userId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}