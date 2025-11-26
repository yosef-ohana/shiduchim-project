package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/event-managers")
public class EventManagerAdminController {

    private final UserService userService;

    public EventManagerAdminController(UserService userService) {
        this.userService = userService;
    }

    // יצירת מנהל אירוע ע"י אדמין
    @PostMapping("/create")
    public ResponseEntity<User> createEventManager(@RequestBody CreateEventManagerRequest req) {

        User created = userService.createEventManager(
                req.fullName,
                req.phone,
                req.email,
                req.gender
        );

        return ResponseEntity.ok(created);
    }

    public static class CreateEventManagerRequest {
        public String fullName;
        public String phone;
        public String email;
        public String gender;
    }
}