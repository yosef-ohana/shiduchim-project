package com.example.myproject.model;

import com.example.myproject.model.enums.ChatMessageDirection;
import com.example.myproject.model.enums.ChatMessageType;
import com.example.myproject.model.enums.DeviceType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_sender", columnList = "sender_id"),
                @Index(name = "idx_chat_recipient", columnList = "recipient_id"),
                @Index(name = "idx_chat_match", columnList = "match_id"),
                @Index(name = "idx_chat_wedding", columnList = "wedding_id"),
                @Index(name = "idx_chat_conversation", columnList = "conversation_id"),
                @Index(name = "idx_chat_created", columnList = "created_at"),
                @Index(name = "idx_chat_read_flag", columnList = "is_read")
        }
)
public class ChatMessage {

    // ============================================================
    // 🔵 מזהה
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                        // מזהה הודעה

    // ============================================================
    // 🔵 קשרי הודעה (Sender / Recipient / Match / Wedding)
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;                    // שולח ההודעה

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;                 // מקבל ההודעה (יכול להיות null בהודעת מערכת)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wedding_id")
    private Wedding wedding;                // חתונה רלוונטית (אם קיים)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;                    // צ'אט התאמה (אם קיים)

    // ============================================================
    // 🔵 תוכן / סוג / קבצים
    // ============================================================

    @Column(nullable = false, length = 2000)
    private String content;                 // תוכן טקסט (אופציונלי ריק אם זו הודעה רק עם קובץ)

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType = ChatMessageType.TEXT;   // TEXT/IMAGE/VIDEO/FILE/SYSTEM

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20)
    private ChatMessageDirection direction = ChatMessageDirection.OUTGOING;

    @Column(name = "attachment_url")
    private String attachmentUrl;           // קישור לקובץ (S3/Cloudinary)

    @Column(name = "attachment_type")
    private String attachmentType;          // image / video / pdf / doc וכו' (MIME לוגי חופשי)

    /**
     * האם זו הודעת מערכת (בנוסף לסוג SYSTEM – redundancy מודעת).
     * נוח לפילטרים מהירים בלי לבדוק Enum.
     */
    @Column(name = "is_system_message", nullable = false)
    private boolean systemMessage = false;

    // ============================================================
    // 🔵 Opening Message + Conversation Grouping
    // ============================================================

    /**
     * הודעה ראשונית לפני פתיחת Match מלא / או ה־"שלום" הראשון
     * לפי החוק "הודעה ראשונה אחת בלבד".
     */
    @Column(name = "is_opening_message", nullable = false)
    private boolean openingMessage = false;

    /**
     * מזהה שיחה לוגי – מאפשר לקבץ מספר הודעות ל-Thread אחד,
     * גם אם בעתיד יתפצלו שיחות או יחולקו למספר Match-ים.
     */
    @Column(name = "conversation_id")
    private Long conversationId;

    // ============================================================
    // 🔵 סטטוסי קריאה/מסירה/מחיקה
    // ============================================================

    @Column(name = "is_read", nullable = false)
    private boolean read = false;           // האם נקראה ע"י הנמען

    @Column(name = "read_at")
    private LocalDateTime readAt;           // זמן קריאה

    @Column(name = "delivered", nullable = false)
    private boolean delivered = false;       // האם נמסרה (WebSocket / Push)

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;         // מחיקה לוגית

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;         // זמן מחיקה

    @Column(name = "flagged", nullable = false)
    private boolean flagged = false;         // דווח/חשוד (Moderator / AI)

    // ============================================================
    // 🔵 מידע למערכת (Device Metadata)
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType = DeviceType.UNKNOWN;  // ios / android / web / unknown

    // ============================================================
    // 🔵 זמנים
    // ============================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;        // זמן יצירה

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;        // זמן עדכון

    // ============================================================
    // 🔵 Hooks של JPA
    // ============================================================

    @PrePersist
    protected void onCreate() {             // לפני יצירה
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        // הגנה: אם ההודעה מסומנת כנקראה כבר ביצירה – נעדכן readAt
        if (read && readAt == null) {
            readAt = createdAt;
        }
        // הגנה: אם מחוקה כבר ביצירה – נגדיר זמן מחיקה
        if (deleted && deletedAt == null) {
            deletedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {             // לפני עדכון
        this.updatedAt = LocalDateTime.now();

        if (read && this.readAt == null) {
            this.readAt = this.updatedAt;
        }
        if (deleted && this.deletedAt == null) {
            this.deletedAt = this.updatedAt;
        }
    }

    // ============================================================
    // 🔵 בנאים
    // ============================================================

    public ChatMessage() {
        // JPA
    }

    public ChatMessage(User sender,
                       User recipient,
                       Wedding wedding,
                       Match match,
                       String content,
                       ChatMessageType messageType,
                       boolean openingMessage,
                       boolean systemMessage) {

        this.sender = sender;
        this.recipient = recipient;
        this.wedding = wedding;
        this.match = match;
        this.content = content;
        this.messageType = (messageType != null ? messageType : ChatMessageType.TEXT);
        this.openingMessage = openingMessage;
        this.systemMessage = systemMessage;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
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

    public ChatMessageType getMessageType() { return messageType; }
    public void setMessageType(ChatMessageType messageType) {
        this.messageType = (messageType != null ? messageType : ChatMessageType.TEXT);
    }

    public ChatMessageDirection getDirection() {
        return direction;
    }
    public void setDirection(ChatMessageDirection direction) {
        this.direction = direction;
    }

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
        if (read && this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted && this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = (deviceType != null ? deviceType : DeviceType.UNKNOWN);
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ============================================================
    // 🔵 Helpers לוגיים
    // ============================================================

    /** האם ההודעה קשורה למשתמש מסוים (כשולח או כמקבל). */
    @Transient
    public boolean involvesUser(Long userId) {
        if (userId == null) return false;
        return (sender != null && userId.equals(sender.getId()))
                || (recipient != null && userId.equals(recipient.getId()));
    }

    /** סימון נוח של קריאה. */
    public void markAsRead() {
        setRead(true);
    }

    /** מחיקה לוגית נוחה. */
    public void softDelete() {
        setDeleted(true);
    }
}