package com.example.myproject.model;

import com.example.myproject.model.enums.DefaultMode;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    private DefaultMode defaultMode = DefaultMode.GLOBAL;

    private boolean canViewSameGender = false;

    @Column(columnDefinition = "TEXT")
    private String shortCardFieldsJson;

    private Integer likeCooldownSeconds = 2;
    private Integer messageCooldownSeconds = 2;
    private boolean autoAntiSpam = true;

    @Column(columnDefinition = "TEXT")
    private String uiPreferencesJson;

    @Column(columnDefinition = "TEXT")
    private String extraSettingsJson;

    // ============================================================
    // ✅ הרחבות 2025 (Additive בלבד) — SAFE FOR ddl-auto=update
    // ============================================================

    // ✅ שים לב: Boolean + nullable DB כדי לא להפיל schema update על נתונים קיימים
    @Column
    private Boolean lockedAfterWedding = false;

    @Column
    private LocalDateTime lockedUntil;

    // ✅ nullable DB כדי לא להפיל update. ימולא אוטומטית ל-records חדשים/מתעדכנים
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.lockedAfterWedding == null) this.lockedAfterWedding = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.lockedAfterWedding == null) this.lockedAfterWedding = false;
    }

    // ============================================================
    // Getters / Setters (רק עבור השדות החדשים, אם תרצה מינימלי)
    // ============================================================

    public Boolean getLockedAfterWedding() { return lockedAfterWedding; }
    public void setLockedAfterWedding(Boolean lockedAfterWedding) { this.lockedAfterWedding = lockedAfterWedding; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}