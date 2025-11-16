package com.example.myproject.controller;

import com.example.myproject.model.Wedding;
import com.example.myproject.service.WeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/weddings")
public class WeddingController {

    private final WeddingService weddingService;

    @Autowired
    public WeddingController(WeddingService weddingService) {
        this.weddingService = weddingService;
    }

    // ✅ יצירת חתונה חדשה
    @PostMapping("/create")
    public ResponseEntity<?> createWedding(@RequestBody Wedding wedding) {
        try {
            Wedding created = weddingService.createWedding(wedding);
            return ResponseEntity.ok(created);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ שליפת כל החתונות
    @GetMapping("/all")
    public ResponseEntity<List<Wedding>> getAllWeddings() {
        return ResponseEntity.ok(weddingService.getAllWeddings());
    }

    // ✅ שליפת חתונה לפי מזהה
    @GetMapping("/{id}")
    public ResponseEntity<?> getWeddingById(@PathVariable Long id) {
        Optional<Wedding> weddingOpt = weddingService.getWeddingById(id);
        return weddingOpt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ✅ הוספת משתמש לחתונה (קשר ישיר בין User ↔ Wedding)
    @PostMapping("/{weddingId}/add-user/{userId}")
    public ResponseEntity<String> addUserToWedding(@PathVariable Long weddingId, @PathVariable Long userId) {
        String result = weddingService.addUserToWedding(userId, weddingId);
        return ResponseEntity.ok(result);
    }
}
