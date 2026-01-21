package com.example.myproject.controller;

import com.example.myproject.model.ChatMessage;
import com.example.myproject.service.ChatMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ChatMatchController
 * ===================
 * קונטרולר מבוסס Match/Wedding:
 *  - כל ההודעות של Match
 *  - הודעות לפי חתונה
 *  - הודעות לא נקראו במאץ'
 *  - System Messages
 *  - Conversation ID
 */
@RestController
@RequestMapping("/api/chat/match")
public class ChatMatchController {

    private final ChatMessageService chatMessageService;

    public ChatMatchController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    // ============================================================
    // 🔵 1. הודעות לפי Match
    // ============================================================

    /**
     * כל ההודעות של Match מסוים (צ'אט התאמה).
     */
    @GetMapping("/{matchId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessagesByMatch(@PathVariable Long matchId) {
        try {
            List<ChatMessage> messages = chatMessageService.getMessagesByMatch(matchId);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * כל ההודעות הלא נקראות במאץ' עבור משתמש מסוים.
     */
    @GetMapping("/{matchId}/unread/{userId}")
    public ResponseEntity<List<ChatMessage>> getUnreadInMatch(@PathVariable Long matchId,
                                                              @PathVariable Long userId) {
        try {
            List<ChatMessage> messages = chatMessageService.getUnreadInMatch(matchId, userId);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 🔵 2. הודעות לפי חתונה (Wedding Chat / Event Context)
    // ============================================================

    /**
     * כל ההודעות הקשורות לחתונה מסוימת.
     */
    @GetMapping("/wedding/{weddingId}")
    public ResponseEntity<List<ChatMessage>> getMessagesByWedding(@PathVariable Long weddingId) {
        try {
            List<ChatMessage> messages = chatMessageService.getMessagesByWedding(weddingId);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 🔵 3. System Messages במאץ'
    // ============================================================

    /**
     * יצירת הודעת מערכת בתוך Match.
     * לדוגמה: "ההתאמה הופסקה", "המשתמש עדכן פרטים" וכו'.
     * (לרוב יופעל ע"י לוגיקה פנימית או אדמין).
     */
    @PostMapping("/{matchId}/system-message")
    public ResponseEntity<ChatMessage> createSystemMessage(@PathVariable Long matchId,
                                                           @RequestBody SystemMessageRequest request) {
        try {
            ChatMessage msg = chatMessageService.createSystemMessage(matchId, request.getContent());
            return ResponseEntity.status(HttpStatus.CREATED).body(msg);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 🔵 4. Conversation ID (חוט שיחה לוגי לכל Match)
    // ============================================================

    /**
     * יצירת/הבטחת Conversation ID לכל הודעות ה-Match.
     * אם כבר קיים – יחזיר את ה-ID הקיים.
     */
    @PostMapping("/{matchId}/conversation-id/ensure")
    public ResponseEntity<Long> ensureConversationId(@PathVariable Long matchId) {
        Long convId = chatMessageService.ensureConversationId(matchId);
        if (convId == null) {
            // אין הודעות → אין שיחה
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(convId);
    }

    // ============================================================
    // 🔵 DTO
    // ============================================================

    public static class SystemMessageRequest {
        private String content;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}