package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity                                     // טבלת chat_messages
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                        // מזהה הודעה

    // ============================================================
    // 🔵 קשרי הודעה (Sender / Recipient / Match / Wedding)
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;                   // שולח ההודעה

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;                // מקבל ההודעה

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wedding_id")
    private Wedding wedding;               // חתונה רלוונטית (אם קיים)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;                   // צ'אט התאמה (אם קיים)

    // ============================================================
    // 🔵 תוכן / סוג / קבצים
    // ============================================================

    @Column(nullable = false, length = 2000)
    private String content;                // תוכן טקסט

    @Column(name = "message_type", nullable = false)
    private String messageType = "text";   // text / image / video / file / system

    @Column(name = "direction", length = 20)
    private String direction;   // incoming / outgoing / system

    @Column(name = "attachment_url")
    private String attachmentUrl;          // קישור לקובץ (S3/Cloudinary)

    @Column(name = "attachment_type")
    private String attachmentType;         // image / video / file

    @Column(name = "is_system_message", nullable = false)
    private boolean systemMessage = false; // הודעת מערכת

    // ============================================================
    // 🔵 Opening Message + Conversation Grouping
    // ============================================================

    @Column(name = "is_opening_message", nullable = false)
    private boolean openingMessage = false; // הודעה ראשונית (לפני Match)

    @Column(name = "conversation_id")
    private Long conversationId;            // מזהה שיחה לוגי

    // ============================================================
    // 🔵 סטטוסי קריאה/מסירה/מחיקה
    // ============================================================

    @Column(name = "is_read", nullable = false)
    private boolean read = false;          // האם נקראה

    @Column(name = "read_at")
    private LocalDateTime readAt;          // זמן קריאה

    @Column(name = "delivered", nullable = false)
    private boolean delivered = false;      // האם נמסרה (ל-WebSocket)

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;        // מחיקה לוגית

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;        // זמן מחיקה

    @Column(name = "flagged", nullable = false)
    private boolean flagged = false;        // דיווח/חשוד (Moderator / AI)

    // ============================================================
    // 🔵 מידע למערכת (Device Metadata)
    // ============================================================

    @Column(name = "device_type")
    private String deviceType;              // ios / android / web

    // ============================================================
    // 🔵 זמנים
    // ============================================================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(); // זמן יצירה

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;        // זמן עדכון

// ============================================================
    // 🔵 Hooks של JPA
    // ============================================================

    @PrePersist
    protected void onCreate() {             // לפני יצירה
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {             // לפני עדכון
        if (read && readAt == null) readAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ============================================================
    // 🔵 בנאים
    // ============================================================

    public ChatMessage() {}

    public ChatMessage(User sender,
                       User recipient,
                       Wedding wedding,
                       Match match,
                       String content,
                       boolean openingMessage,
                       boolean systemMessage) {

        this.sender = sender;
        this.recipient = recipient;
        this.wedding = wedding;
        this.match = match;
        this.content = content;
        this.openingMessage = openingMessage;
        this.systemMessage = systemMessage;
        this.createdAt = LocalDateTime.now();
    }

    // ============================================================
    // 🔵 Getters & Setters
    // ============================================================

    public Long getId() { return id; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public Wedding getWedding() { return wedding; }
    public void setWedding(Wedding wedding) { this.wedding = wedding; }

    public Match getMatch() { return match; }
    public void setMatch(Match match) { this.match = match; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }

    public boolean isSystemMessage() { return systemMessage; }
    public void setSystemMessage(boolean systemMessage) { this.systemMessage = systemMessage; }

    public boolean isOpeningMessage() { return openingMessage; }
    public void setOpeningMessage(boolean openingMessage) { this.openingMessage = openingMessage; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) {
        this.read = read;
        if (read && this.readAt == null) this.readAt = LocalDateTime.now();
    }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted && this.deletedAt == null) this.deletedAt = LocalDateTime.now();
    }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}