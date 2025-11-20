package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;

@Entity                                 // מסמן שזו ישות JPA שממופה לטבלה בבסיס הנתונים
@Table(name = "weddings")               // שם הטבלה: weddings
public class Wedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // מפתח ראשי אוטומטי (מספר רץ)
    private Long id;                    // מזהה חתונה ייחודי

    // שם החתונה (למשל: "חתונת דניאל & תמר")
    @Column(nullable = false, length = 120) // חובה, עם אורך מקסימלי 120 תווים
    private String name;

    // מתי האירוע התחיל בפועל (כולל תאריך ושעה)
    @Column(nullable = false)          // חובה – אי אפשר חתונה בלי זמן התחלה
    private LocalDateTime startTime;

    // זמן סיום האירוע:
    // אם לא הוגדר במפורש – נגדיר 01:00 ביום שאחרי startTime
    @Column(nullable = false)          // גם חובה – נוודא ב-@PrePersist
    private LocalDateTime endTime;

    // מי הבעלים / מנהל האירוע (User id של owner)
    // (מאפשר לתת לו גישה לסטטיסטיקות ולניהול)
    private Long ownerUserId;

    // רקע תמונה אישי לחתונה (כתובת URL של תמונה)
    // אם null → נשתמש ברקע ברירת המחדל של האתר
    private String backgroundImageUrl;

    @Column(name = "created_by_user_id")
    private Long createdByUserId; // מי יצר את החתונה

    // רקע וידאו אישי לחתונה (כתובת URL של וידאו)
    // אם null → נשתמש בתמונה / ברקע כללי
    private String backgroundVideoUrl;

    // איזה סוג רקע להשתמש לחתונה (IMAGE / VIDEO / DEFAULT)
    @Column(nullable = false)
    private String backgroundMode = "DEFAULT";

    // האם החתונה עדיין פעילה:
    // true  → האירוע פעיל / בעיצומו / לא נסגר עדיין
    // false → נסגרה (או כי הזמן עבר, או שסגרו ידנית)
    @Column(nullable = false)
    private boolean active = true;

    // תאריך ושעת יצירה של החתונה במערכת
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // תאריך ושעת עדכון אחרון
    private LocalDateTime updatedAt;
    // -------------------------------------------------
    // Hooks של JPA – טיפול אוטומטי בתאריכים ו־endTime
    // -------------------------------------------------

    @PrePersist
    protected void onCreate() {
        // אם משום מה לא הוגדר createdAt – נגדיר עכשיו
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // אם יש זמן התחלה אבל אין זמן סיום → נגדיר 01:00 ביום הבא
        if (startTime != null && endTime == null) {
            // היום הבא לתאריך של startTime
            LocalDate nextDay = startTime.toLocalDate().plusDays(1);
            // קובע סוף אירוע ל-01:00
            this.endTime = LocalDateTime.of(nextDay, LocalTime.of(1, 0));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // כל עדכון לישות יעדכן את updatedAt אוטומטית
        this.updatedAt = LocalDateTime.now();
    }

    // ------------ בנאים ------------

    public Wedding() {
        // בנאי ריק נדרש ע"י JPA
    }

    // בנאי נוח ליצירת חתונה חדשה בקוד
    public Wedding(String name,
                   LocalDateTime startTime,
                   LocalDateTime endTime,
                   Long ownerUserId) {

        this.name = name;                  // שם האירוע
        this.startTime = startTime;        // התחלה
        this.endTime = endTime;            // אפשר להעביר null – ואז @PrePersist יקבע ברירת מחדל
        this.ownerUserId = ownerUserId;    // מזהה בעל האירוע
        this.createdAt = LocalDateTime.now();
        this.active = true;                // ברירת מחדל – אירוע חדש הוא פעיל
    }

    // ------------ Getters & Setters ------------

    public Long getId() {
        return id;                         // מחזיר את מזהה החתונה
    }

    public String getName() {
        return name;                       // שם האירוע
    }
    public void setName(String name) {
        this.name = name;                  // עדכון שם האירוע
    }

    public LocalDateTime getStartTime() {
        return startTime;                  // מחזיר זמן התחלה
    }
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;        // מעדכן זמן התחלה
    }

    public LocalDateTime getEndTime() {
        return endTime;                    // מחזיר זמן סיום
    }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;            // מעדכן זמן סיום
    }

    public Long getOwnerUserId() {
        return ownerUserId;                // מחזיר את מזהה בעל האירוע
    }
    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;    // מעדכן בעל אירוע
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;         // מחזיר URL של תמונת רקע
    }
    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl; // מעדכן תמונת רקע
    }

    public String getBackgroundVideoUrl() {
        return backgroundVideoUrl;         // מחזיר URL של וידאו רקע
    }
    public void setBackgroundVideoUrl(String backgroundVideoUrl) {
        this.backgroundVideoUrl = backgroundVideoUrl; // מעדכן וידאו רקע
    }

    public boolean isActive() {
        return active;                     // האם החתונה פעילה
    }
    public void setActive(boolean active) {
        this.active = active;              // עדכון סטטוס פעיל/לא
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;                  // מחזיר זמן יצירה
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;        // מאפשר לעדכן ידנית אם צריך
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;                  // מחזיר זמן עדכון אחרון
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;        // מאפשר לעדכן ידנית (לא חובה להשתמש)
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
}