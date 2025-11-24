package com.example.myproject.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_photos")
public class UserPhoto {

    // ==========================
    // 🔵 מזהה תמונה
    // ==========================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================
    // 🔵 למי התמונה שייכת
    // ==========================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // ==========================
    // 🔵 נתוני הקובץ (URL)
    // ==========================
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;     // ← השדה נשאר כפי שהוא!

    // ==========================
    // 🔵 תמונה ראשית / רגילה
    // ==========================
    @Column(name = "is_primary", nullable = false)
    private boolean primaryPhoto = false;

    // ==========================
    // 🔵 מחיקה לוגית
    // ==========================
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    // ==========================
    // 🔵 סדר תמונות
    // ==========================
    @Column(name = "position_index")
    private Integer positionIndex;

    // ==========================
    // 🔵 תאריכים
    // ==========================
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==========================
    // 🔵 בנאים
    // ==========================
    public UserPhoto() {}

    public UserPhoto(User user,
                     String imageUrl,
                     boolean primaryPhoto,
                     boolean deleted,
                     Integer positionIndex) {

        this.user = user;
        this.imageUrl = imageUrl;
        this.primaryPhoto = primaryPhoto;
        this.deleted = deleted;
        this.positionIndex = positionIndex;
        this.createdAt = LocalDateTime.now();
    }

    // ==========================
    // 🔵 Getters & Setters
    // ==========================

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    // שמרתי את המתודות המקוריות:
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // ⬅⬅⬅ הפתרון: הוספת getUrl() תואם למסמך האפיון ולשאר הקוד:
    public String getUrl() { return imageUrl; }
    public void setUrl(String url) { this.imageUrl = url; }

    public boolean isPrimaryPhoto() { return primaryPhoto; }
    public void setPrimaryPhoto(boolean primaryPhoto) { this.primaryPhoto = primaryPhoto; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public Integer getPositionIndex() { return positionIndex; }
    public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}