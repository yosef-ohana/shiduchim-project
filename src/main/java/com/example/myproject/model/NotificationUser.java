package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_users",
        indexes = {
                @Index(name = "idx_nu_user", columnList = "user_id"),
                @Index(name = "idx_nu_notification", columnList = "notification_id"),
                @Index(name = "idx_nu_read", columnList = "is_read")
        })
public class NotificationUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================
    // 🔵 קשרים
    // =========================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // =========================================
    // 🔵 סטטוסים אישיים
    // =========================================

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "popup_seen", nullable = false)
    private boolean popupSeen = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "is_snoozed", nullable = false)
    private boolean snoozed = false;

    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    // =========================================
    // 🔵 זמנים
    // =========================================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (read && readAt == null)
            readAt = LocalDateTime.now();
    }

    // =========================================
    // 🔵 בנאים
    // =========================================

    public NotificationUser() {}

    public NotificationUser(Notification notification, User user) {
        this.notification = notification;
        this.user = user;
        this.createdAt = LocalDateTime.now();
    }

    // =========================================
    // 🔵 Getters & Setters
    // =========================================

    public Long getId() { return id; }

    public Notification getNotification() { return notification; }
    public void setNotification(Notification notification) { this.notification = notification; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) {
        this.read = read;
        if (read && readAt == null)
            readAt = LocalDateTime.now();
    }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isPopupSeen() { return popupSeen; }
    public void setPopupSeen(boolean popupSeen) { this.popupSeen = popupSeen; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public boolean isSnoozed() { return snoozed; }
    public void setSnoozed(boolean snoozed) { this.snoozed = snoozed; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}