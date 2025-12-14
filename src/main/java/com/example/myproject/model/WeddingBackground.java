package com.example.myproject.model;

import com.example.myproject.model.enums.BackgroundType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "wedding_backgrounds",
        indexes = {
                @Index(name = "idx_wb_wedding", columnList = "wedding_id"),
                @Index(name = "idx_wb_is_global", columnList = "is_global"),
                @Index(name = "idx_wb_active", columnList = "active"),
                @Index(name = "idx_wb_default", columnList = "is_default"),
                @Index(name = "idx_wb_deleted", columnList = "deleted"),
                @Index(name = "idx_wb_unsuitable", columnList = "unsuitable"),
                @Index(name = "idx_wb_type", columnList = "background_type")
        }
)
public class WeddingBackground {

    // ======================================================
    // 🔵 מזהה
    // ======================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ======================================================
    // 🔵 קשר לחתונה / רקע גלובלי
    // ======================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wedding_id")
    @JsonIgnore
    private Wedding wedding;

    @Column(name = "is_global", nullable = false)
    private boolean global = false;

    // ======================================================
    // 🔵 נתוני הרקע
    // ======================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "background_type", nullable = false, length = 20)
    private BackgroundType type = BackgroundType.IMAGE;

    @Column(name = "background_url", nullable = false, length = 500)
    private String backgroundUrl;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;

    // ======================================================
    // 🔵 סטטוס הרקע
    // ======================================================

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean defaultBackground = false;

    @Column(name = "unsuitable", nullable = false)
    private boolean unsuitable = false;

    @Column(name = "unsuitable_at")
    private LocalDateTime unsuitableAt;

    // ======================================================
    // 🔵 Audit / Ownership + Reasons
    // ======================================================

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "unsuitable_by_user_id")
    private Long unsuitableByUserId;

    @Column(name = "unsuitable_reason", length = 500)
    private String unsuitableReason;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

    @Column(name = "display_order")
    private Integer displayOrder;

    // ======================================================
    // 🔵 מחיקה לוגית (Soft Delete)
    // ======================================================

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ======================================================
    // 🔵 זמנים
    // ======================================================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ======================================================
    // 🔵 Hooks
    // ======================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;

        if (deleted && deletedAt == null) deletedAt = createdAt;
        if (unsuitable && unsuitableAt == null) unsuitableAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        if (deleted && deletedAt == null) deletedAt = updatedAt;
        if (unsuitable && unsuitableAt == null) unsuitableAt = updatedAt;
    }

    // ======================================================
    // 🔵 Constructors
    // ======================================================

    public WeddingBackground() {}

    public WeddingBackground(
            Wedding wedding,
            boolean global,
            BackgroundType type,
            String backgroundUrl,
            boolean defaultBackground
    ) {
        this.wedding = wedding;
        this.global = global;
        this.type = (type != null ? type : BackgroundType.IMAGE);
        this.backgroundUrl = backgroundUrl;
        this.defaultBackground = defaultBackground;
        this.active = true;
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // ======================================================
    // 🔵 Getters & Setters
    // ======================================================

    public Long getId() {
        return id;
    }

    public Wedding getWedding() {
        return wedding;
    }
    public void setWedding(Wedding wedding) {
        this.wedding = wedding;
    }

    public boolean isGlobal() {
        return global;
    }
    public void setGlobal(boolean global) {
        this.global = global;
    }

    public BackgroundType getType() {
        return type;
    }
    public void setType(BackgroundType type) {
        this.type = (type != null ? type : BackgroundType.IMAGE);
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }
    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDefaultBackground() {
        return defaultBackground;
    }
    public void setDefaultBackground(boolean defaultBackground) {
        this.defaultBackground = defaultBackground;
    }

    public boolean isUnsuitable() {
        return unsuitable;
    }
    public void setUnsuitable(boolean unsuitable) {
        this.unsuitable = unsuitable;
        if (unsuitable && this.unsuitableAt == null) {
            this.unsuitableAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getUnsuitableAt() {
        return unsuitableAt;
    }
    public void setUnsuitableAt(LocalDateTime unsuitableAt) {
        this.unsuitableAt = unsuitableAt;
    }

    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted && this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
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

    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }
    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public Long getUnsuitableByUserId() {
        return unsuitableByUserId;
    }
    public void setUnsuitableByUserId(Long unsuitableByUserId) {
        this.unsuitableByUserId = unsuitableByUserId;
    }

    public String getUnsuitableReason() {
        return unsuitableReason;
    }
    public void setUnsuitableReason(String unsuitableReason) {
        this.unsuitableReason = unsuitableReason;
    }

    public Long getDeletedByUserId() {
        return deletedByUserId;
    }
    public void setDeletedByUserId(Long deletedByUserId) {
        this.deletedByUserId = deletedByUserId;
    }

    public String getDeletedReason() {
        return deletedReason;
    }
    public void setDeletedReason(String deletedReason) {
        this.deletedReason = deletedReason;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    // ======================================================
    // 🔵 Helpers
    // ======================================================

    @Transient
    public boolean isUsable() {
        return active && !deleted && !unsuitable;
    }
}