package com.example.myproject.model;

import com.example.myproject.model.enums.MatchSourceType;
import com.example.myproject.model.enums.MatchStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "matches",
        indexes = {
                @Index(name = "idx_match_user1", columnList = "user1_id"),
                @Index(name = "idx_match_user2", columnList = "user2_id"),
                @Index(name = "idx_match_users_pair", columnList = "user1_id,user2_id"),
                @Index(name = "idx_match_meeting_wedding", columnList = "meeting_wedding_id"),
                @Index(name = "idx_match_origin_wedding", columnList = "origin_wedding_id"),
                @Index(name = "idx_match_mutual", columnList = "mutual_approved")
        }
)
public class Match {

    // ======================================================
    // 🔵 מזהה
    // ======================================================

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

    // 👀 מתי המשתמש ראה את ההתאמה לראשונה?
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    // ======================================================
    // 🔵 חתונות / מקור מפגש
    // ======================================================

    /**
     * החתונה שבה "כרגע" רואים את ההתאמה / נוצרה ההתאמה האחרונה.
     * (למשל אם נפגשו שוב בחתונה אחרת – זה יעדכן את השדה הזה.)
     */
    @Column(name = "meeting_wedding_id")
    private Long meetingWeddingId;

    /**
     * החתונה הראשונה שבה נפגשו – לא משתנה.
     * בשימוש לצורך הצגת "השתתפתם יחד בחתונה X" ולסטטיסטיקות.
     */
    @Column(name = "origin_wedding_id")
    private Long originWeddingId;

    // ======================================================
    // 🔵 ניקוד ומקור התאמה
    // ======================================================

    @Column(name = "match_score")
    private Double matchScore;  // ציון התאמה (0–100 או כל סולם שנחליט)

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 30)
    private MatchStatus status = MatchStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_source", length = 30)
    private MatchSourceType source = MatchSourceType.UNKNOWN;

    // ======================================================
    // 🔵 אישורים / הדדיות
    // ======================================================

    @Column(name = "user1_approved", nullable = false)
    private boolean user1Approved = false;

    @Column(name = "user2_approved", nullable = false)
    private boolean user2Approved = false;

    @Column(name = "mutual_approved", nullable = false)
    private boolean mutualApproved = false;

    // ======================================================
    // 🔵 חסימה / הקפאה / ארכוב
    // ======================================================

    @Column(name = "blocked_by_user1", nullable = false)
    private boolean blockedByUser1 = false;

    @Column(name = "blocked_by_user2", nullable = false)
    private boolean blockedByUser2 = false;

    @Column(name = "frozen_by_user1", nullable = false)
    private boolean frozenByUser1 = false;

    @Column(name = "frozen_by_user2", nullable = false)
    private boolean frozenByUser2 = false;

    @Column(name = "freeze_reason", length = 500)
    private String freezeReason;              // טקסט חופשי – סיבת הקפאה

    @Column(name = "archived", nullable = false)
    private boolean archived = false;         // האם עבר לארכיון (היסטוריה)

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    // ======================================================
    // 🔵 צ'אט / הודעות / קריאה
    // ======================================================

    /**
     * האם הצ'אט נפתח בפועל (אחרי הודעה ראשונית + אישור).
     */
    @Column(name = "chat_opened", nullable = false)
    private boolean chatOpened = false;

    /**
     * כמה הודעות לא נקראו (מצטבר לצורך badge והתרעות).
     * ברמת המערכת – נוכל לעדכן בהתאם למי המשתמש שצופה.
     */
    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;

    /**
     * האם User1 קרא את כל ההודעות הקיימות (עבור תצוגת "נקי").
     */
    @Column(name = "read_by_user1", nullable = false)
    private boolean readByUser1 = true;

    /**
     * האם User2 קרא את כל ההודעות הקיימות.
     */
    @Column(name = "read_by_user2", nullable = false)
    private boolean readByUser2 = true;

    /**
     * האם נשלחה הודעה ראשונית (Opening Message) – חשוב לחוקים
     * של "אפשר הודעה ראשונה אחת בלבד", ניהול התראות וכו'.
     */
    @Column(name = "first_message_sent", nullable = false)
    private boolean firstMessageSent = false;

    /**
     * זמן ההודעה האחרונה בצ'אט – חשוב לסידור רשימת ההתאמות, סטטיסטיקות,
     * והצגת "נראה לאחרונה".
     */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    // ======================================================
    // 🔵 הודעות צ'אט שקשורות למץ'
    // ======================================================

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages;

    // ======================================================
    // 🔵 תאריכים / מחיקה לוגית
    // ======================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ======================================================
    // 🔵 JPA Hooks
    // ======================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        recalcMutualApproved();
        recalcStatusFromFlags();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        recalcMutualApproved();
        recalcStatusFromFlags();
    }

    // ======================================================
    // 🔵 בנאים
    // ======================================================

    public Match() {
        // JPA
    }

    public Match(User user1,
                 User user2,
                 Long meetingWeddingId,
                 Long originWeddingId,
                 Double matchScore,
                 MatchSourceType source) {

        this.user1 = user1;
        this.user2 = user2;
        this.meetingWeddingId = meetingWeddingId;
        this.originWeddingId = originWeddingId;
        this.matchScore = matchScore;
        this.source = (source != null ? source : MatchSourceType.UNKNOWN);

        this.status = MatchStatus.PENDING;
        this.chatOpened = false;
        this.unreadCount = 0;
        this.readByUser1 = true;
        this.readByUser2 = true;

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // ======================================================
    // 🔵 לוגיקת עזר – התאמה מול חוקי המערכת
    // ======================================================

    /** חישוב הדדיות לפי שני האישים. */
    private void recalcMutualApproved() {
        this.mutualApproved = this.user1Approved && this.user2Approved && !this.deleted;
    }

    /**
     * קביעת ה־status לפי דגלי חסימה/הקפאה/ארכוב.
     * (לשימוש פנימי לפני שמגיעים ל-UserStateEvaluator.)
     */
    private void recalcStatusFromFlags() {
        if (deleted || archived) {
            this.status = MatchStatus.ARCHIVED;
        } else if (blockedByUser1 || blockedByUser2) {
            this.status = MatchStatus.BLOCKED;
        } else if (frozenByUser1 || frozenByUser2) {
            this.status = MatchStatus.FROZEN;
        } else if (mutualApproved) {
            this.status = MatchStatus.ACTIVE;
        } else {
            this.status = MatchStatus.PENDING;
        }
    }

    // ======================================================
    // 🔵 Getters & Setters
    // ======================================================

    public Long getId() {
        return id;
    }

    public User getUser1() {
        return user1;
    }
    public void setUser1(User user1) {
        this.user1 = user1;
    }

    public User getUser2() {
        return user2;
    }
    public void setUser2(User user2) {
        this.user2 = user2;
    }

    public Long getMeetingWeddingId() {
        return meetingWeddingId;
    }
    public void setMeetingWeddingId(Long meetingWeddingId) {
        this.meetingWeddingId = meetingWeddingId;
    }

    public Long getOriginWeddingId() {
        return originWeddingId;
    }
    public void setOriginWeddingId(Long originWeddingId) {
        this.originWeddingId = originWeddingId;
    }

    public Double getMatchScore() {
        return matchScore;
    }
    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public MatchStatus getStatus() {
        return status;
    }
    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public MatchSourceType getSource() {
        return source;
    }
    public void setSource(MatchSourceType source) {
        this.source = source;
    }

    public boolean isUser1Approved() {
        return user1Approved;
    }
    public void setUser1Approved(boolean user1Approved) {
        this.user1Approved = user1Approved;
        recalcMutualApproved();
        recalcStatusFromFlags();
    }

    public boolean isUser2Approved() {
        return user2Approved;
    }
    public void setUser2Approved(boolean user2Approved) {
        this.user2Approved = user2Approved;
        recalcMutualApproved();
        recalcStatusFromFlags();
    }

    public boolean isMutualApproved() {
        return mutualApproved;
    }
    public void setMutualApproved(boolean mutualApproved) {
        this.mutualApproved = mutualApproved;
        recalcStatusFromFlags();
    }

    public boolean isBlockedByUser1() {
        return blockedByUser1;
    }
    public void setBlockedByUser1(boolean blockedByUser1) {
        this.blockedByUser1 = blockedByUser1;
        recalcStatusFromFlags();
    }

    public boolean isBlockedByUser2() {
        return blockedByUser2;
    }
    public void setBlockedByUser2(boolean blockedByUser2) {
        this.blockedByUser2 = blockedByUser2;
        recalcStatusFromFlags();
    }

    public boolean isFrozenByUser1() {
        return frozenByUser1;
    }
    public void setFrozenByUser1(boolean frozenByUser1) {
        this.frozenByUser1 = frozenByUser1;
        recalcStatusFromFlags();
    }

    public boolean isFrozenByUser2() {
        return frozenByUser2;
    }
    public void setFrozenByUser2(boolean frozenByUser2) {
        this.frozenByUser2 = frozenByUser2;
        recalcStatusFromFlags();
    }

    public String getFreezeReason() {
        return freezeReason;
    }
    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public boolean isArchived() {
        return archived;
    }
    public void setArchived(boolean archived) {
        this.archived = archived;
        if (archived && archivedAt == null) {
            this.archivedAt = LocalDateTime.now();
        }
        recalcStatusFromFlags();
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }
    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isChatOpened() {
        return chatOpened;
    }
    public void setChatOpened(boolean chatOpened) {
        this.chatOpened = chatOpened;
    }

    public Integer getUnreadCount() {
        return unreadCount;
    }
    public void setUnreadCount(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }

    public boolean isReadByUser1() {
        return readByUser1;
    }
    public void setReadByUser1(boolean readByUser1) {
        this.readByUser1 = readByUser1;
    }

    public boolean isReadByUser2() {
        return readByUser2;
    }
    public void setReadByUser2(boolean readByUser2) {
        this.readByUser2 = readByUser2;
    }

    public boolean isFirstMessageSent() {
        return firstMessageSent;
    }
    public void setFirstMessageSent(boolean firstMessageSent) {
        this.firstMessageSent = firstMessageSent;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }
    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }
    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }


    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted && deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
        recalcStatusFromFlags();
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    // ======================================================
    // 🔵 Helpers לוגיים
    // ======================================================

    /** האם המשתמש הוא חלק מההתאמה. */
    public boolean involvesUser(Long userId) {
        return (user1 != null && user1.getId().equals(userId))
                || (user2 != null && user2.getId().equals(userId));
    }

    /** האם המץ כולל שני משתמשים מסוימים — לא משנה סדר. */
    public boolean involvesBoth(Long u1, Long u2) {
        if (user1 == null || user2 == null) return false;
        Long id1 = user1.getId();
        Long id2 = user2.getId();
        return (id1.equals(u1) && id2.equals(u2)) || (id1.equals(u2) && id2.equals(u1));
    }

    @Transient
    public boolean isApprovedByUser1() {
        return user1Approved;
    }

    @Transient
    public boolean isApprovedByUser2() {
        return user2Approved;
    }
}