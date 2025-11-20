package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ======================================================
    // 🔵 שני המשתמשים בהתאמה (User1 / User2)
    // ======================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    // ======================================================
    // 🔵 מאפיינים כלליים של ההתאמה
    // ======================================================

    @Column(name = "meeting_wedding_id")
    private Long meetingWeddingId;    // החתונה שבה נוצר המץ'

    @Column(name = "origin_wedding_id")
    private Long originWeddingId;     // החתונה הראשונה שבה נפגשו

    @Column(name = "match_score")
    private Double matchScore;        // ציון התאמה

    @Column(nullable = false)
    private boolean user1Approved = false;

    @Column(nullable = false)
    private boolean user2Approved = false;

    @Column(nullable = false)
    private boolean mutualApproved = false;

    @Column(nullable = false)
    private boolean active = true;    // האם המץ פעיל

    @Column(nullable = false)
    private boolean blocked = false;  // האם אחד חסם את השני

    @Column(nullable = false)
    private boolean frozen = false;   // האם קפוא (freeze)

    @Column(nullable = false)
    private boolean chatOpened = false;  // האם צ'אט פתוח

    // ======================================================
    // 🔵 שדות חדשים לפי אפיון 2025
    // ======================================================

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;          // כמה הודעות לא נקראו (למי ששייך)

    @Column(length = 500)
    private String freezeReason;              // סיבת הקפאה

    @Column(name = "match_source")
    private String matchSource;               // מקור ההתאמה: wedding/global/admin/ai...

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;      // זמן הודעה אחרונה – חשוב לצ'אט ולדירוג

    @Column(name = "read_by_user1", nullable = false)
    private boolean readByUser1 = true;       // האם user1 קרא את כל ההודעות

    @Column(name = "read_by_user2", nullable = false)
    private boolean readByUser2 = true;       // האם user2 קרא את כל ההודעות

    @Column(name = "first_message_sent", nullable = false)
    private boolean firstMessageSent = false; // האם נשלחה הודעת Opening

    // ======================================================
    // 🔵 הודעות צ'אט שקשורות למץ'
    // ======================================================

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages;

    // ======================================================
    // 🔵 תאריכים
    // ======================================================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ======================================================
    // 🔵 JPA Hooks
    // ======================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ======================================================
    // 🔵 בנאים
    // ======================================================

    public Match() {
    }

    public Match(User user1,
                 User user2,
                 Long meetingWeddingId,
                 Long originWeddingId,
                 Double matchScore,
                 String matchSource) {

        this.user1 = user1;
        this.user2 = user2;
        this.meetingWeddingId = meetingWeddingId;
        this.originWeddingId = originWeddingId;
        this.matchScore = matchScore;
        this.matchSource = matchSource;

        this.active = true;
        this.blocked = false;
        this.frozen = false;
        this.chatOpened = false;
        this.mutualApproved = false;

        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ======================================================
    // 🔵 Getters & Setters
    // ======================================================

    public Long getId() { return id; }

    public User getUser1() { return user1; }
    public void setUser1(User user1) { this.user1 = user1; }

    public User getUser2() { return user2; }
    public void setUser2(User user2) { this.user2 = user2; }

    public Long getMeetingWeddingId() { return meetingWeddingId; }
    public void setMeetingWeddingId(Long meetingWeddingId) { this.meetingWeddingId = meetingWeddingId; }

    public Long getOriginWeddingId() { return originWeddingId; }
    public void setOriginWeddingId(Long originWeddingId) { this.originWeddingId = originWeddingId; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public boolean isUser1Approved() { return user1Approved; }
    public void setUser1Approved(boolean user1Approved) { this.user1Approved = user1Approved; }

    public boolean isUser2Approved() { return user2Approved; }
    public void setUser2Approved(boolean user2Approved) { this.user2Approved = user2Approved; }

    public boolean isMutualApproved() { return mutualApproved; }
    public void setMutualApproved(boolean mutualApproved) { this.mutualApproved = mutualApproved; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }

    public boolean isChatOpened() { return chatOpened; }
    public void setChatOpened(boolean chatOpened) { this.chatOpened = chatOpened; }

    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }

    public String getFreezeReason() { return freezeReason; }
    public void setFreezeReason(String freezeReason) { this.freezeReason = freezeReason; }

    public String getMatchSource() { return matchSource; }
    public void setMatchSource(String matchSource) { this.matchSource = matchSource; }

    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public boolean isReadByUser1() { return readByUser1; }
    public void setReadByUser1(boolean readByUser1) { this.readByUser1 = readByUser1; }

    public boolean isReadByUser2() { return readByUser2; }
    public void setReadByUser2(boolean readByUser2) { this.readByUser2 = readByUser2; }

    public boolean isFirstMessageSent() { return firstMessageSent; }
    public void setFirstMessageSent(boolean firstMessageSent) { this.firstMessageSent = firstMessageSent; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ======================================================
    // 🔵 Helpers לוגיים
    // ======================================================

    /** האם המשתמש הוא חלק מההתאמה */
    public boolean involvesUser(Long userId) {
        return (user1 != null && user1.getId().equals(userId))
                || (user2 != null && user2.getId().equals(userId));
    }

    /** האם המץ כולל שני משתמשים מסוימים — לא משנה סדר */
    public boolean involvesBoth(Long u1, Long u2) {
        if (user1 == null || user2 == null) return false;
        Long id1 = user1.getId();
        Long id2 = user2.getId();
        return (id1.equals(u1) && id2.equals(u2)) || (id1.equals(u2) && id2.equals(u1));
    }

    // נוחות
    @Transient public boolean isApprovedByUser1() { return user1Approved; }
    @Transient public boolean isApprovedByUser2() { return user2Approved; }

    public void setApprovedByUser1(boolean approved) { this.user1Approved = approved; }
    public void setApprovedByUser2(boolean approved) { this.user2Approved = approved; }

}