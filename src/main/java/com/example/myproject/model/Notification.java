package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity                                 // מייצג טבלת notifications במסד הנתונים
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // מזהה התראה ייחודי

    // ==============================
    // 🔵 קשר למשתמש
    // ==============================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;             // המשתמש שמקבל את ההתראה

    // ==============================
    // 🔵 סוג ההתראה וקטגוריה
    // ==============================

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;      // סוג ההתראה (LIKE_RECEIVED, MATCH_MUTUAL, MESSAGE_RECEIVED וכו')

    @Column(name = "category", length = 50)
    private String category;            // קטגוריה כללית: match / chat / system / profile / wedding

    @Column(name = "source", length = 50)
    private String source;              // מקור ההתראה: system / admin / AI / wedding-owner

    // ==============================
    // 🔵 תוכן ההתראה
    // ==============================

    @Column(name = "title", length = 200)
    private String title;               // כותרת קצרה של ההתראה

    @Column(name = "message", length = 2000)
    private String message;             // טקסט מלא שמוצג למשתמש

    @Column(name = "metadata", length = 3000)
    private String metadata;            // מידע נוסף בפורמט JSON / טקסט חופשי (לשימוש בצד לקוח)

    // ==============================
    // 🔵 קישורים לישויות אחרות
    // ==============================

    @Column(name = "related_user_id")
    private Long relatedUserId;         // משתמש שקשור להתראה (מי עשה לייק / מי שלח הודעה וכו')

    @Column(name = "wedding_id")
    private Long weddingId;             // חתונה רלוונטית (אם יש)

    @Column(name = "match_id")
    private Long matchId;               // התאמה רלוונטית (אם יש)

    @Column(name = "chat_message_id")
    private Long chatMessageId;         // מזהה הודעת צ'אט רלוונטית (אם ההתראה על הודעה)

    // ==============================
    // 🔵 סטטוס ההתראה
    // ==============================

    @Column(name = "is_read", nullable = false)
    private boolean read = false;       // האם המשתמש כבר "קרא" את ההתראה (נכנס למסך ההתראות)

    @Column(name = "popup_seen", nullable = false)
    private boolean popupSeen = false;  // האם רק ראה פופאפ (Notification Bell / Toast) בלי להיכנס למסך

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;    // מחיקה לוגית – לא להציג למשתמש אבל נשאר ב־DB

    @Column(name = "priority_level", nullable = false)
    private int priorityLevel = 1;      // רמת עדיפות: 1=רגיל, 2=חשוב, 3=דחוף

    // ==============================
    // 🔵 זמנים
    // ==============================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(); // מתי נוצרה ההתראה

    @Column(name = "read_at")
    private LocalDateTime readAt;       // מתי נקראה (אם נקראה)

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;    // מתי עודכנה לאחרונה (למשל שינוי סטטוס)

    // ==============================
    // 🔵 Hooks – יצירה/עדכון
    // ==============================

    @PrePersist
    protected void onCreate() {         // רץ לפני INSERT
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {         // רץ לפני UPDATE
        this.updatedAt = LocalDateTime.now();
        // אם נקבע ש"התראה נקראה" ואין readAt – נמלא אותו
        if (read && readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    // ==============================
    // 🔵 בנאים
    // ==============================

    public Notification() {
        // בנאי ריק ל-JPA
    }

    public Notification(User recipient,
                        NotificationType type,
                        String title,
                        String message,
                        Long relatedUserId,
                        Long weddingId,
                        Long matchId,
                        Long chatMessageId,
                        String metadata,
                        String category,
                        String source,
                        int priorityLevel) {

        this.recipient = recipient;           // למי שייכת ההתראה
        this.type = type;                     // סוג ההתראה
        this.title = title;                   // כותרת
        this.message = message;               // הודעה טקסטואלית
        this.relatedUserId = relatedUserId;   // משתמש נוסף שקשור להתראה
        this.weddingId = weddingId;           // חתונה רלוונטית
        this.matchId = matchId;               // התאמה רלוונטית
        this.chatMessageId = chatMessageId;   // הודעת צ'אט רלוונטית
        this.metadata = metadata;             // מידע נוסף (JSON)

        this.category = category;             // קטגוריה לוגית
        this.source = source;                 // מקור ההתראה
        this.priorityLevel = priorityLevel;   // עדיפות

        this.createdAt = LocalDateTime.now(); // זמן יצירה
        this.read = false;                    // ברירת מחדל – לא נקרא
    }

    // ==============================
    // 🔵 Getters & Setters
    // ==============================

    public Long getId() { return id; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Long getRelatedUserId() { return relatedUserId; }
    public void setRelatedUserId(Long relatedUserId) { this.relatedUserId = relatedUserId; }

    public Long getWeddingId() { return weddingId; }
    public void setWeddingId(Long weddingId) { this.weddingId = weddingId; }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Long getChatMessageId() { return chatMessageId; }
    public void setChatMessageId(Long chatMessageId) { this.chatMessageId = chatMessageId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) {
        this.read = read;                     // עדכון דגל "נקרא"
        if (read && this.readAt == null) {    // אם עכשיו סומן כנקרא ואין readAt – נשמור זמן
            this.readAt = LocalDateTime.now();
        }
    }

    public boolean isPopupSeen() { return popupSeen; }
    public void setPopupSeen(boolean popupSeen) { this.popupSeen = popupSeen; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public int getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(int priorityLevel) { this.priorityLevel = priorityLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}