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
                @Index(name = "idx_wb_deleted", columnList = "deleted")
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

    /**
     * אם זה רקע של חתונה ספציפית – wedding לא null.
     * אם זה רקע גלובלי (Global Background) – wedding == null && isGlobal == true.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wedding_id")
    @JsonIgnore
    private Wedding wedding;

    @Column(name = "is_global", nullable = false)
    private boolean global = false;   // true = רקע גלובלי לכל המערכת

    // ======================================================
    // 🔵 נתוני הרקע
    // ======================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "background_type", nullable = false, length = 20)
    private BackgroundType type = BackgroundType.IMAGE;   // IMAGE / VIDEO

    @Column(name = "background_url", nullable = false, length = 500)
    private String backgroundUrl;      // URL של הקובץ (S3 / Cloudinary / Static)

    /**
     * טקסט קצר לתיאור הרקע (לדוגמה: "רקע אולם", "רקע כללי לחתונה").
     */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * תיאור מורחב / הערות (עבור ממשק ניהול).
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * Metadata גמיש בפורמט JSON — רזולוציה, יחס רוחב/גובה, מקור וכו’.
     */
    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;

    // ======================================================
    // 🔵 סטטוס הרקע
    // ======================================================

    @Column(name = "active", nullable = false)
    private boolean active = true;        // האם הרקע פעיל לשימוש כרגע

    @Column(name = "is_default", nullable = false)
    private boolean defaultBackground = false;  // האם זה הרקע הראשי של אותה חתונה / גלובלי

    @Column(name = "unsuitable", nullable = false)
    private boolean unsuitable = false;   // אדמין סימן כ"לא מתאים" → לא יוצג

    @Column(name = "unsuitable_at")
    private LocalDateTime unsuitableAt;   // מתי סומן כלא מתאים

    // ======================================================
    // 🔵 מחיקה לוגית (Soft Delete) + מחיקה פיזית עתידית
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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (deleted && deletedAt == null) {
            deletedAt = createdAt;
        }
        if (unsuitable && unsuitableAt == null) {
            unsuitableAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        if (deleted && deletedAt == null) {
            deletedAt = updatedAt;
        }
        if (unsuitable && unsuitableAt == null) {
            unsuitableAt = updatedAt;
        }
    }

    // ======================================================
    // 🔵 Constructors
    // ======================================================

    public WeddingBackground() {
        // JPA
    }

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

    // ======================================================
    // 🔵 Helpers
    // ======================================================

    /**
     * האם הרקע הזה זמין להצגה למשתמשים:
     * חייב להיות: active == true, deleted == false, unsuitable == false.
     */
    @Transient
    public boolean isUsable() {
        return active && !deleted && !unsuitable;
    }
}