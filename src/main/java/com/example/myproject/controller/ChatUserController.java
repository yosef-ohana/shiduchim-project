package com.example.myproject.controller;

import com.example.myproject.model.ChatMessage;
import com.example.myproject.service.ChatMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * ChatUserController
 * ==================
 * קונטרולר צד-משתמש לניהול הודעות צ'אט:
 *  - שליחת הודעות בתוך Match קיים
 *  - שליחת Opening Message
 *  - קבלת הודעות / שיחות
 *  - הודעות לא נקראו
 *  - סימון כנקרא
 *  - מחיקה לוגית (Soft Delete)
 *  - ניהול Delivered (WebSocket)
 */
@RestController
@RequestMapping("/api/chat/user")
public class ChatUserController {

    private final ChatMessageService chatMessageService;

    public ChatUserController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    // ============================================================
    // 🔵 1. שליחת הודעה רגילה בתוך Match
    // ============================================================

    @PostMapping("/send")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody SendMessageRequest request) {
        try {
            ChatMessage message = chatMessageService.sendMessage(
                    request.getSenderId(),
                    request.getRecipientId(),
                    request.getMatchId(),
                    request.getWeddingId(),
                    request.getContent()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            // לדוגמה: "Chat is not allowed for this match."
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * שליחת הודעה עם deviceType (ios / android / web).
     */
    @PostMapping("/send-with-device")
    public ResponseEntity<ChatMessage> sendMessageWithDevice(@RequestBody SendMessageRequest request) {
        try {
            ChatMessage message = chatMessageService.sendMessage(
                    request.getSenderId(),
                    request.getRecipientId(),
                    request.getMatchId(),
                    request.getWeddingId(),
                    request.getContent(),
                    request.getDeviceType()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 🔵 2. Opening Message – שליחת הודעה ראשונית
    // ============================================================

    @PostMapping("/opening/send")
    public ResponseEntity<ChatMessage> sendOpeningMessage(@RequestBody OpeningMessageRequest request) {
        try {
            ChatMessage message = chatMessageService.sendOpeningMessage(
                    request.getSenderId(),
                    request.getRecipientId(),
                    request.getContent()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (IllegalArgumentException ex) {
            // לדוגמה: Sender not found / Recipient not found / content ריק
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            // לדוגמה: פרופיל לא מלא / כבר נשלח opening / כבר יש Match
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * כל הודעות ה-Opening שממתינות לאישור אצל משתמש.
     */
    @GetMapping("/opening/pending/{userId}")
    public ResponseEntity<List<ChatMessage>> getPendingOpeningMessages(@PathVariable Long userId) {
        List<ChatMessage> messages = chatMessageService.getPendingOpeningMessages(userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * אישור הודעת Opening → יצירת Match / שימוש ב-Match קיים + פתיחת צ'אט.
     */
    @PostMapping("/opening/{messageId}/approve")
    public ResponseEntity<Void> approveOpening(@PathVariable Long messageId,
                                               @RequestBody ApproveOpeningRequest request) {
        try {
            chatMessageService.approveOpeningMessage(messageId, request.getRecipientId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            // message not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            // לא opening / לא הנמען / בעיה לוגית אחרת
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * דחיית הודעת Opening → מחיקה לוגית + התראות.
     */
    @PostMapping("/opening/{messageId}/reject")
    public ResponseEntity<Void> rejectOpening(@PathVariable Long messageId,
                                              @RequestBody ApproveOpeningRequest request) {
        try {
            chatMessageService.rejectOpeningMessage(messageId, request.getRecipientId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 🔵 3. שיחה דו-כיוונית בין שני משתמשים (לא תלוי Match)
    // ============================================================

    /**
     * כל השיחה הדו-כיוונית בין שני משתמשים (A↔B), בלי קשר ל-Match ספציפי.
     */
    @GetMapping("/conversation")
    public ResponseEntity<List<ChatMessage>> getConversation(@RequestParam Long user1Id,
                                                             @RequestParam Long user2Id) {
        List<ChatMessage> messages = chatMessageService.getConversation(user1Id, user2Id);
        return ResponseEntity.ok(messages);
    }

    /**
     * 50 ההודעות האחרונות של משתמש (שלח/קיבל).
     */
    @GetMapping("/recent/{userId}")
    public ResponseEntity<List<ChatMessage>> getRecentMessages(@PathVariable Long userId) {
        List<ChatMessage> messages = chatMessageService.getRecentMessages(userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 20 ההודעות האחרונות ש-A שלח ל-B.
     */
    @GetMapping("/last20")
    public ResponseEntity<List<ChatMessage>> getLast20Sent(@RequestParam Long senderId,
                                                           @RequestParam Long recipientId) {
        List<ChatMessage> messages = chatMessageService.getLast20Sent(senderId, recipientId);
        return ResponseEntity.ok(messages);
    }

    /**
     * ההודעה האחרונה בין שני משתמשים (בכל כיוון).
     */
    @GetMapping("/last-between")
    public ResponseEntity<ChatMessage> getLastMessageBetween(@RequestParam Long user1Id,
                                                             @RequestParam Long user2Id) {
        Optional<ChatMessage> opt = chatMessageService.getLastMessageBetween(user1Id, user2Id);
        return opt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // ============================================================
    // 🔵 4. הודעות לא נקראו (Unread)
    // ============================================================

    /**
     * כל ההודעות הלא נקראות של משתמש.
     */
    @GetMapping("/unread/{userId}")
    public ResponseEntity<List<ChatMessage>> getUnreadMessages(@PathVariable Long userId) {
        List<ChatMessage> messages = chatMessageService.getUnreadMessages(userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * ספירת הודעות לא נקראות של משתמש.
     */
    @GetMapping("/unread/{userId}/count")
    public ResponseEntity<Long> countUnread(@PathVariable Long userId) {
        long count = chatMessageService.countUnread(userId);
        return ResponseEntity.ok(count);
    }

    // ============================================================
    // 🔵 5. סימון כנקרא
    // ============================================================

    /**
     * סימון הודעה בודדת כנקראה (רק אם userId הוא recipient).
     */
    @PostMapping("/read/{messageId}")
    public ResponseEntity<Void> markMessageAsRead(@PathVariable Long messageId,
                                                  @RequestBody MarkReadRequest request) {
        try {
            chatMessageService.markMessageAsRead(messageId, request.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * סימון כל השיחה בין userId לבין otherUserId כנקראה.
     */
    @PostMapping("/read/conversation")
    public ResponseEntity<Void> markConversationAsRead(@RequestBody MarkConversationReadRequest request) {
        chatMessageService.markConversationAsRead(request.getUserId(), request.getOtherUserId());
        return ResponseEntity.ok().build();
    }

    /**
     * סימון כל ההודעות הלא נקראות של משתמש כנקראו.
     */
    @PostMapping("/read/all/{userId}")
    public ResponseEntity<Void> markAllUnreadAsRead(@PathVariable Long userId) {
        chatMessageService.markAllUnreadAsRead(userId);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 🔵 6. מחיקה לוגית (Soft Delete)
    // ============================================================

    /**
     * מחיקה לוגית של הודעה.
     * רק השולח או המקבל רשאים.
     */
    @DeleteMapping("/soft/{messageId}")
    public ResponseEntity<Void> softDeleteMessage(@PathVariable Long messageId,
                                                  @RequestBody DeleteMessageRequest request) {
        try {
            chatMessageService.softDeleteMessage(messageId, request.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            // משתמש שאינו חלק מההודעה
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 🔵 7. Delivered (WebSocket) – בצד המשתמש
    // ============================================================

    /**
     * הודעות שטרם נמסרו למשתמש (delivered=false) – לדוגמה בזמן התחברות WebSocket.
     */
    @GetMapping("/undelivered/{userId}")
    public ResponseEntity<List<ChatMessage>> getUndeliveredMessages(@PathVariable Long userId) {
        List<ChatMessage> messages = chatMessageService.getUndeliveredMessagesForUser(userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * סימון כל ההודעות שלא נמסרו כ-delivered=true עבור משתמש (לאחר דחיפה ללקוח).
     */
    @PostMapping("/delivered/{userId}")
    public ResponseEntity<Void> markDeliveredForUser(@PathVariable Long userId) {
        chatMessageService.markMessagesAsDeliveredForUser(userId);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 🔵 8. הודעות מסוג Image / Video (מבוסס Match)
    // ============================================================

    @PostMapping("/send/image")
    public ResponseEntity<ChatMessage> sendImage(@RequestBody MediaMessageRequest request) {
        try {
            ChatMessage message = chatMessageService.sendImageMessage(
                    request.getSenderId(),
                    request.getRecipientId(),
                    request.getMatchId(),
                    request.getMediaUrl()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/send/video")
    public ResponseEntity<ChatMessage> sendVideo(@RequestBody MediaMessageRequest request) {
        try {
            ChatMessage message = chatMessageService.sendVideoMessage(
                    request.getSenderId(),
                    request.getRecipientId(),
                    request.getMatchId(),
                    request.getMediaUrl()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============================================================
    // 🔵 DTO פנימיים לבקשות
    // ============================================================

    public static class SendMessageRequest {
        private Long senderId;
        private Long recipientId;
        private Long matchId;
        private Long weddingId;
        private String content;
        private String deviceType;

        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }

        public Long getRecipientId() { return recipientId; }
        public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

        public Long getMatchId() { return matchId; }
        public void setMatchId(Long matchId) { this.matchId = matchId; }

        public Long getWeddingId() { return weddingId; }
        public void setWeddingId(Long weddingId) { this.weddingId = weddingId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    }

    public static class OpeningMessageRequest {
        private Long senderId;
        private Long recipientId;
        private String content;

        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }

        public Long getRecipientId() { return recipientId; }
        public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ApproveOpeningRequest {
        private Long recipientId;

        public Long getRecipientId() { return recipientId; }
        public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    }

    public static class MarkReadRequest {
        private Long userId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    public static class MarkConversationReadRequest {
        private Long userId;
        private Long otherUserId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getOtherUserId() { return otherUserId; }
        public void setOtherUserId(Long otherUserId) { this.otherUserId = otherUserId; }
    }

    public static class DeleteMessageRequest {
        private Long userId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    public static class MediaMessageRequest {
        private Long senderId;
        private Long recipientId;
        private Long matchId;
        private String mediaUrl;

        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }

        public Long getRecipientId() { return recipientId; }
        public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

        public Long getMatchId() { return matchId; }
        public void setMatchId(Long matchId) { this.matchId = matchId; }

        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    }
}