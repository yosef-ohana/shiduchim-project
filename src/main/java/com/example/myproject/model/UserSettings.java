package com.example.myproject.model;

import com.example.myproject.model.enums.DefaultMode;
import jakarta.persistence.*;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // מצב התחלה של המשתמש (גלובלי/חתונה)
    @Enumerated(EnumType.STRING)
    private DefaultMode defaultMode = DefaultMode.GLOBAL;

    // צפייה במשתמשים מאותו המין (תמיכה עתידית)
    private boolean canViewSameGender = false;

    // שדות שיוצגו בכרטיס המקוצר (JSON נתמך)
    @Column(columnDefinition = "TEXT")
    private String shortCardFieldsJson;

    // Anti-Spam אישי
    private Integer likeCooldownSeconds = 2;         // לייק → לייק
    private Integer messageCooldownSeconds = 2;      // הודעה → הודעה

    private boolean autoAntiSpam = true;

    // הגדרות UI עתידיות
    @Column(columnDefinition = "TEXT")
    private String uiPreferencesJson;

    // הגדרות כלליות נוספות (מאוד גמיש להמשך)
    @Column(columnDefinition = "TEXT")
    private String extraSettingsJson;
}