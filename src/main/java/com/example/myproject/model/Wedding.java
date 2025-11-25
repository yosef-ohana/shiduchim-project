package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;

@Entity
@Table(name = "weddings")
public class Wedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id") // לא nullable כדי לא לשבור נתונים קיימים
    private User owner;

    @Column(nullable = false)
    private LocalDateTime endTime;

    // מזהה בעל האירוע (נשמר גם כשלא טוענים את האובייקט owner מה־DB)
    private Long ownerUserId;

    private String backgroundImageUrl;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    private String backgroundVideoUrl;

    @Column(nullable = false)
    private String backgroundMode = "DEFAULT";

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    // -------------------------------------------------
    // Hooks של JPA – טיפול אוטומטי בתאריכים ו־endTime + סנכרון ownerUserId
    // -------------------------------------------------

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // ברירת מחדל ל-endTime אם לא הוגדר
        if (startTime != null && endTime == null) {
            LocalDate nextDay = startTime.toLocalDate().plusDays(1);
            this.endTime = LocalDateTime.of(nextDay, LocalTime.of(1, 0));
        }

        // סנכרון ownerUserId מה-owner אם צריך
        if (owner != null && ownerUserId == null) {
            ownerUserId = owner.getId();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();

        // שוב, לוודא סנכרון
        if (owner != null && ownerUserId == null) {
            ownerUserId = owner.getId();
        }
    }

    // ------------ בנאים ------------

    public Wedding() {
        // בנאי ריק נדרש ע"י JPA
    }

    public Wedding(String name,
                   LocalDateTime startTime,
                   LocalDateTime endTime,
                   Long ownerUserId) {

        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ownerUserId = ownerUserId;
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    // ------------ Getters & Setters ------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getOwnerUserId() {
        // אם יש owner טעון ואין ownerUserId – נשתמש בו
        if (ownerUserId == null && owner != null) {
            return owner.getId();
        }
        return ownerUserId;
    }
    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }
    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public String getBackgroundVideoUrl() {
        return backgroundVideoUrl;
    }
    public void setBackgroundVideoUrl(String backgroundVideoUrl) {
        this.backgroundVideoUrl = backgroundVideoUrl;
    }

    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
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

    public Long getCreatedByUserId() {
        return createdByUserId;
    }
    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getBackgroundMode() {
        return backgroundMode;
    }
    public void setBackgroundMode(String backgroundMode) {
        this.backgroundMode = backgroundMode;
    }

    @Transient
    public String getEffectiveBackgroundUrl() {
        if ("VIDEO".equalsIgnoreCase(backgroundMode) && backgroundVideoUrl != null) {
            return backgroundVideoUrl;
        }
        if ("IMAGE".equalsIgnoreCase(backgroundMode) && backgroundImageUrl != null) {
            return backgroundImageUrl;
        }
        // null → המערכת תציג רקע ברירת מחדל
        return null;
    }

    public User getOwner() {
        return owner;
    }
    public void setOwner(User owner) {
        this.owner = owner;
        // כאן הקסם העיקרי: סנכרון אוטומטי
        this.ownerUserId = (owner != null ? owner.getId() : null);
    }
}