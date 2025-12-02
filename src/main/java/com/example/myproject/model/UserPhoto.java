package com.example.myproject.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_photos",
        indexes = {
                @Index(name = "idx_photo_user", columnList = "user_id"),
                @Index(name = "idx_photo_primary", columnList = "is_primary"),
                @Index(name = "idx_photo_main", columnList = "is_main"),
                @Index(name = "idx_photo_deleted", columnList = "deleted"),
                @Index(name = "idx_photo_position", columnList = "position_index")
        }
)
public class UserPhoto {

    // ======================================================
    // 🔵 מזהה תמונה
    // ======================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ======================================================
    // 🔵 למי התמונה שייכת
    // ======================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // ======================================================
    // 🔵 נתוני קובץ / כתובת
    // ======================================================
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    public String getUrl() { return imageUrl; }
    public void setUrl(String url) { this.imageUrl = url; }

    // ======================================================
    // 🔵 תמונה ראשית (Primary)
    // ======================================================
    @Column(name = "is_primary", nullable = false)
    private boolean primaryPhoto = false;

    // ======================================================
    // 🔵 תמונה מרכזית (Main) — תמיכה עתידית
    // ======================================================
    @Column(name = "is_main", nullable = false)
    private boolean main = false;

    // ======================================================
    // 🔵 מחיקה לוגית
    // ======================================================
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ======================================================
    // 🔵 סדר תמונות
    // ======================================================
    @Column(name = "position_index")
    private Integer positionIndex;

    // ======================================================
    // 🔵 Metadata
    // ======================================================
    @Column(name = "metadata_json", length = 3000)
    private String metadataJson;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_by_admin", nullable = false)
    private boolean uploadedByAdmin = false;

    @Column(name = "locked_after_wedding", nullable = false)
    private boolean lockedAfterWedding = false;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (deleted && deletedAt == null) deletedAt = LocalDateTime.now();
    }

    // ======================================================
    // 🔵 Constructors
    // ======================================================
    public UserPhoto() {}

    public UserPhoto(User user,
                     String url,
                     boolean primaryPhoto,
                     Integer positionIndex) {

        this.user = user;
        this.imageUrl = url;
        this.primaryPhoto = primaryPhoto;
        this.positionIndex = positionIndex;
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
    }

    // ======================================================
    // 🔵 Getters & Setters
    // ======================================================
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isPrimaryPhoto() { return primaryPhoto; }
    public void setPrimaryPhoto(boolean primaryPhoto) { this.primaryPhoto = primaryPhoto; }

    public boolean isMain() { return main; }
    public void setMain(boolean main) { this.main = main; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        if (deleted && deletedAt == null) deletedAt = LocalDateTime.now();
    }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public Integer getPositionIndex() { return positionIndex; }
    public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public boolean isUploadedByAdmin() { return uploadedByAdmin; }
    public void setUploadedByAdmin(boolean uploadedByAdmin) { this.uploadedByAdmin = uploadedByAdmin; }

    public boolean isLockedAfterWedding() { return lockedAfterWedding; }
    public void setLockedAfterWedding(boolean lockedAfterWedding) { this.lockedAfterWedding = lockedAfterWedding; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}