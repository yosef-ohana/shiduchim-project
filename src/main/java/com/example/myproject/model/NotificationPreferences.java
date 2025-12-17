package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "notification_preferences",
        indexes = {
                @Index(name = "idx_np_user", columnList = "user_id"),
                @Index(name = "idx_np_mute_all", columnList = "muteAll"),
                @Index(name = "idx_np_mute_until", columnList = "muteUntil"),
                @Index(name = "idx_np_updated_at", columnList = "updatedAt") // ✅ תואם לריפו שלך
        }
)
public class NotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // הגבלת התראות לפי SystemRules
    private Integer likeNotificationsLimit = 1;
    private Integer likeNotificationsMinutes = 10;

    private Integer viewNotificationsLimit = 1;
    private Integer viewNotificationsMinutes = 60;

    private Integer initialMessageLimit = 1;
    private Integer initialMessageMinutes = 5;

    // Digest של צפיות
    private Integer profileViewDigestEveryViews = 30;
    private Integer profileViewDigestEveryHours = 168;

    // השתקת כל ההתראות
    private boolean muteAll = false;
    private LocalDateTime muteUntil;

    // התראות קריטיות
    private boolean alwaysShowMatch = true;
    private boolean alwaysShowSuperLike = true;

    @Column(columnDefinition = "TEXT")
    private String customPreferencesJson;

    // ==============================
    // ✅ הוספות (לא לשנות שמות קיימים)
    // ==============================

    private Boolean enablePush;   // default: false
    private Boolean enableEmail;  // default: false
    private Boolean enableInApp;  // default: true

    private Boolean quietHoursEnabled; // default: false
    private String quietHoursStart;    // "22:00"
    private String quietHoursEnd;      // "07:00"

    private Boolean throttled;         // default: false
    private LocalDateTime throttleUntil;

    private Integer maxNotificationsPerHour;
    private Integer maxNotificationsPerDay;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (enableInApp == null) enableInApp = true;
        if (enablePush == null) enablePush = false;
        if (enableEmail == null) enableEmail = false;

        if (quietHoursEnabled == null) quietHoursEnabled = false;
        if (quietHoursStart == null) quietHoursStart = "22:00";
        if (quietHoursEnd == null) quietHoursEnd = "07:00";

        if (throttled == null) throttled = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters/Setters (כמו אצלך — נשאר)
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getLikeNotificationsLimit() { return likeNotificationsLimit; }
    public void setLikeNotificationsLimit(Integer likeNotificationsLimit) { this.likeNotificationsLimit = likeNotificationsLimit; }

    public Integer getLikeNotificationsMinutes() { return likeNotificationsMinutes; }
    public void setLikeNotificationsMinutes(Integer likeNotificationsMinutes) { this.likeNotificationsMinutes = likeNotificationsMinutes; }

    public Integer getViewNotificationsLimit() { return viewNotificationsLimit; }
    public void setViewNotificationsLimit(Integer viewNotificationsLimit) { this.viewNotificationsLimit = viewNotificationsLimit; }

    public Integer getViewNotificationsMinutes() { return viewNotificationsMinutes; }
    public void setViewNotificationsMinutes(Integer viewNotificationsMinutes) { this.viewNotificationsMinutes = viewNotificationsMinutes; }

    public Integer getInitialMessageLimit() { return initialMessageLimit; }
    public void setInitialMessageLimit(Integer initialMessageLimit) { this.initialMessageLimit = initialMessageLimit; }

    public Integer getInitialMessageMinutes() { return initialMessageMinutes; }
    public void setInitialMessageMinutes(Integer initialMessageMinutes) { this.initialMessageMinutes = initialMessageMinutes; }

    public Integer getProfileViewDigestEveryViews() { return profileViewDigestEveryViews; }
    public void setProfileViewDigestEveryViews(Integer profileViewDigestEveryViews) { this.profileViewDigestEveryViews = profileViewDigestEveryViews; }

    public Integer getProfileViewDigestEveryHours() { return profileViewDigestEveryHours; }
    public void setProfileViewDigestEveryHours(Integer profileViewDigestEveryHours) { this.profileViewDigestEveryHours = profileViewDigestEveryHours; }

    public boolean isMuteAll() { return muteAll; }
    public void setMuteAll(boolean muteAll) { this.muteAll = muteAll; }

    public LocalDateTime getMuteUntil() { return muteUntil; }
    public void setMuteUntil(LocalDateTime muteUntil) { this.muteUntil = muteUntil; }

    public boolean isAlwaysShowMatch() { return alwaysShowMatch; }
    public void setAlwaysShowMatch(boolean alwaysShowMatch) { this.alwaysShowMatch = alwaysShowMatch; }

    public boolean isAlwaysShowSuperLike() { return alwaysShowSuperLike; }
    public void setAlwaysShowSuperLike(boolean alwaysShowSuperLike) { this.alwaysShowSuperLike = alwaysShowSuperLike; }

    public String getCustomPreferencesJson() { return customPreferencesJson; }
    public void setCustomPreferencesJson(String customPreferencesJson) { this.customPreferencesJson = customPreferencesJson; }

    public Boolean getEnablePush() { return enablePush; }
    public void setEnablePush(Boolean enablePush) { this.enablePush = enablePush; }

    public Boolean getEnableEmail() { return enableEmail; }
    public void setEnableEmail(Boolean enableEmail) { this.enableEmail = enableEmail; }

    public Boolean getEnableInApp() { return enableInApp; }
    public void setEnableInApp(Boolean enableInApp) { this.enableInApp = enableInApp; }

    public Boolean getQuietHoursEnabled() { return quietHoursEnabled; }
    public void setQuietHoursEnabled(Boolean quietHoursEnabled) { this.quietHoursEnabled = quietHoursEnabled; }

    public String getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }

    public String getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }

    public Boolean getThrottled() { return throttled; }
    public void setThrottled(Boolean throttled) { this.throttled = throttled; }

    public LocalDateTime getThrottleUntil() { return throttleUntil; }
    public void setThrottleUntil(LocalDateTime throttleUntil) { this.throttleUntil = throttleUntil; }

    public Integer getMaxNotificationsPerHour() { return maxNotificationsPerHour; }
    public void setMaxNotificationsPerHour(Integer maxNotificationsPerHour) { this.maxNotificationsPerHour = maxNotificationsPerHour; }

    public Integer getMaxNotificationsPerDay() { return maxNotificationsPerDay; }
    public void setMaxNotificationsPerDay(Integer maxNotificationsPerDay) { this.maxNotificationsPerDay = maxNotificationsPerDay; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}