package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
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
    private Integer profileViewDigestEveryHours = 168; // שבוע

    // השתקת כל ההתראות
    private boolean muteAll = false;
    private LocalDateTime muteUntil;

    // התראות קריטיות — Match, SuperLike
    private boolean alwaysShowMatch = true;
    private boolean alwaysShowSuperLike = true;

    // הגדרות נוספות (JSON)
    @Column(columnDefinition = "TEXT")
    private String customPreferencesJson;

    private LocalDateTime updatedAt;
}