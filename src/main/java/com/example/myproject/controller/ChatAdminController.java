package com.example.myproject.controller;

import com.example.myproject.model.ChatMessage;
import com.example.myproject.repository.ChatMessageRepository;
import com.example.myproject.service.ChatMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ChatAdminController
 * ===================
 * קונטרולר אדמין / תחזוקה:
 *  - חיפוש הודעות (full text)
 *  - הודעות ישנות / מחיקה ישנה
 *  - מחיקת הודעה מלאה (Hard Delete)
 *  - הודעות מדווחות (flagged)
 *  - System Messages / Attachments / Deleted
 */
@RestController
@RequestMapping("/api/admin/chat")
public class ChatAdminController {

    private final ChatMessageService chatMessageService;
    private final ChatMessageRepository chatMessageRepository;

    public ChatAdminController(ChatMessageService chatMessageService,
                               ChatMessageRepository chatMessageRepository) {
        this.chatMessageService = chatMessageService;
        this.chatMessageRepository = chatMessageRepository;
    }

    // ============================================================
    // 🔵 1. חיפוש הודעות (Text Search)
    // ============================================================

    /**
     * חיפוש הודעות לפי מחרוזת (case-insensitive).
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChatMessage>> searchMessages(@RequestParam String keyword) {
        List<ChatMessage> messages = chatMessageService.searchMessages(keyword);
        return ResponseEntity.ok(messages);
    }

    // ============================================================
    // 🔵 2. הודעות ישנות / מחיקה ישנה
    // ============================================================

    /**
     * שליפת הודעות שנוצרו לפני X ימים.
     */
    @GetMapping("/older-than/{days}")
    public ResponseEntity<List<ChatMessage>> getMessagesOlderThan(@PathVariable int days) {
        if (days <= 0) {
            return ResponseEntity.badRequest().build();
        }
        List<ChatMessage> messages = chatMessageService.getMessagesOlderThan(days);
        return ResponseEntity.ok(messages);
    }

    /**
     * מחיקה מלאה של הודעות שנוצרו לפני X ימים.
     * מחזיר כמה הודעות נמחקו.
     */
    @DeleteMapping("/older-than/{days}")
    public ResponseEntity<Integer> deleteMessagesOlderThan(@PathVariable int days) {
        if (days <= 0) {
            return ResponseEntity.badRequest().build();
        }
        int deletedCount = chatMessageService.deleteMessagesOlderThan(days);
        return ResponseEntity.ok(deletedCount);
    }

    // ============================================================
    // 🔵 3. מחיקה מלאה (Hard Delete) של הודעה
    // ============================================================

    /**
     * מחיקה מלאה של הודעה ספציפית מה-DB.
     */
    @DeleteMapping("/message/{messageId}")
    public ResponseEntity<Void> adminDeleteMessage(@PathVariable Long messageId) {
        try {
            chatMessageService.adminDeleteMessage(messageId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 🔵 4. הודעות מדווחות / חשודות (Flagged)
    // ============================================================

    /**
     * כל ההודעות שסומנו כ-flagged (חשודות / מדווחות).
     * משתמש ישירות ב-ChatMessageRepository.
     */
    @GetMapping("/flagged")
    public ResponseEntity<List<ChatMessage>> getFlaggedMessages() {
        List<ChatMessage> messages = chatMessageRepository.findByFlaggedTrue();
        return ResponseEntity.ok(messages);
    }

    // ============================================================
    // 🔵 5. System Messages / Deleted / Attachments
    // ============================================================

    /**
     * כל הודעות המערכת (systemMessage=true).
     */
    @GetMapping("/system")
    public ResponseEntity<List<ChatMessage>> getSystemMessages() {
        List<ChatMessage> messages = chatMessageRepository.findBySystemMessageTrue();
        return ResponseEntity.ok(messages);
    }

    /**
     * כל ההודעות שנמחקו לוגית (deleted=true).
     */
    @GetMapping("/deleted")
    public ResponseEntity<List<ChatMessage>> getDeletedMessages() {
        List<ChatMessage> messages = chatMessageRepository.findByDeletedTrue();
        return ResponseEntity.ok(messages);
    }

    /**
     * כל ההודעות הפעילות בלבד (deleted=false).
     */
    @GetMapping("/active")
    public ResponseEntity<List<ChatMessage>> getActiveMessages() {
        List<ChatMessage> messages = chatMessageRepository.findByDeletedFalse();
        return ResponseEntity.ok(messages);
    }

    /**
     * כל ההודעות עם קבצים (attachmentUrl != null).
     */
    @GetMapping("/attachments")
    public ResponseEntity<List<ChatMessage>> getMessagesWithAttachments() {
        List<ChatMessage> messages = chatMessageRepository.findByAttachmentUrlIsNotNull();
        return ResponseEntity.ok(messages);
    }

    /**
     * כל ההודעות עם סוג קובץ מסוים (image / video / file).
     */
    @GetMapping("/attachments/{type}")
    public ResponseEntity<List<ChatMessage>> getMessagesByAttachmentType(@PathVariable String type) {
        List<ChatMessage> messages = chatMessageRepository.findByAttachmentType(type);
        return ResponseEntity.ok(messages);
    }
}