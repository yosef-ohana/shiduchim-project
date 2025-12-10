package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.service.User.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    // ============================================================
    // 1. הפיכת משתמש לאדמין
    // ============================================================

    @PutMapping("/{userId}/set-admin")
    public ResponseEntity<?> setAdmin(@PathVariable Long userId) {

        User updated = userService.setAdminFlag(userId, true);

        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        return ResponseEntity.ok(updated);
    }

    // ============================================================
    // 2. ביטול אדמין
    // ============================================================

    @PutMapping("/{userId}/unset-admin")
    public ResponseEntity<?> unsetAdmin(@PathVariable Long userId) {

        User updated = userService.setAdminFlag(userId, false);

        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        return ResponseEntity.ok(updated);
    }

    // ============================================================
    // 3. יצירה ישירה של מנהל אירוע
    // ============================================================

    @PostMapping("/event-managers/create")
    public ResponseEntity<User> createEventManager(@RequestBody CreateEventManagerRequest req) {

        User created = userService.createEventManager(
                req.fullName,
                req.phone,
                req.email,
                req.gender
        );

        return ResponseEntity.ok(created);
    }

    // ============================================================
    // 4. שליפת משתמש
    // ============================================================

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable Long userId) {

        return userService.getUserById(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found")));
    }

    // ============================================================
    // 5. רשימת כל האדמינים
    // ============================================================

    @GetMapping("/list/admins")
    public ResponseEntity<List<User>> getAdminUsers() {
        return ResponseEntity.ok(userService.getAdminUsers());
    }

    // DTO לבקשת יצירת מנהל אירוע
    public static class CreateEventManagerRequest {
        public String fullName;
        public String phone;
        public String email;
        public String gender;
    }
}