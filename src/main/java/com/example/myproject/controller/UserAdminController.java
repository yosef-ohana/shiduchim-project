package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class UserAdminController {

    private final UserRepository userRepository;

    public UserAdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ============================================================
    // 1. הפיכת משתמש לאדמין
    // ============================================================

    /**
     * ✔️ הפיכת משתמש לאדמין
     * PUT /api/admin/users/{userId}/set-admin
     */
    @PutMapping("/{userId}/set-admin")
    public ResponseEntity<?> setAdmin(@PathVariable Long userId) {

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        if (Boolean.TRUE.equals(user.isAdmin())) {
            return ResponseEntity.ok(user); // כבר אדמין, מחזירים כרגיל
        }

        user.setAdmin(true);
        userRepository.save(user);

        return ResponseEntity.ok(user);
    }

    // ============================================================
    // 2. ביטול אדמין
    // ============================================================

    /**
     * ✔️ ביטול הרשאת אדמין
     * PUT /api/admin/users/{userId}/unset-admin
     */
    @PutMapping("/{userId}/unset-admin")
    public ResponseEntity<?> unsetAdmin(@PathVariable Long userId) {

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        if (!Boolean.TRUE.equals(user.isAdmin())) {
            return ResponseEntity.ok(user); // כבר לא אדמין
        }

        user.setAdmin(false);
        userRepository.save(user);

        return ResponseEntity.ok(user);
    }

    // ============================================================
    // 3. שליפת משתמש ע"י אדמין
    // ============================================================

    /**
     * ✔️ שליפת משתמש (לצרכי ניהול)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable Long userId) {

        return userRepository.findById(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found")));
    }

    // ============================================================
    // 4. שליפת כל האדמינים
    // ============================================================

    /**
     * ✔️ מחזיר את כל האדמינים — כלי חשוב לדשבורד הניהול
     */
    @GetMapping("/list/admins")
    public ResponseEntity<?> getAdminUsers() {
        return ResponseEntity.ok(userRepository.findByAdminTrue());
    }
}