package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ✅ יצירת משתמש חדש
    @PostMapping("/create")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User created = userService.saveUser(user);
        return ResponseEntity.ok(created);
    }

    // ✅ שליפת כל המשתמשים
    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ✅ שליפת משתמש לפי מזהה
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Optional<User> userOpt = userService.getUserById(id);
        return userOpt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // ✅ שיוך משתמש לחתונה קיימת
    @PutMapping("/{id}/assign-wedding")
    public ResponseEntity<String> assignWeddingToUser(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long weddingId = body.get("weddingId");
        String message = userService.assignWedding(id, weddingId);
        return ResponseEntity.ok(message);
    }

    // ✅ בקשת גישה גלובלית (משתמש שולח בקשה)
    @PostMapping("/{id}/request-global")
    public ResponseEntity<String> requestGlobalAccess(@PathVariable Long id) {
        String msg = userService.requestGlobalAccess(id);
        return ResponseEntity.ok(msg);
    }

    // ✅ אישור גישה גלובלית (ע"י אדמין)
    @PostMapping("/{id}/approve-global")
    public ResponseEntity<String> approveGlobalAccess(@PathVariable Long id) {
        String msg = userService.approveGlobalAccess(id);
        return ResponseEntity.ok(msg);
    }

    // ✅ שליפת כל המשתמשים לפי חתונה
    @GetMapping("/by-wedding/{weddingId}")
    public ResponseEntity<List<User>> getUsersByWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(userService.getUsersByWedding(weddingId));
    }

    // ✅ שליפת כל ההתאמות של משתמש
    @GetMapping("/{id}/matches")
    public ResponseEntity<?> getUserMatches(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getMatchesForUser(id));
    }
}
