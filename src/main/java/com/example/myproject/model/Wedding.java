package com.example.myproject.model;

import com.example.myproject.model.enums.BackgroundMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "weddings")
public class Wedding {

    // =====================================================
    // 🔵 מזהה וסטטוס כללי
    // =====================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;                      // שם החתונה (למשל "חתונת X & Y")

    @Column(nullable = false)
    private boolean active = true;            // סטטוס ניהולי (לא רק זמן)

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    // 🎫 קוד כניסה / ברקוד (חובה)
    @Column(name = "access_code", length = 50, unique = true)
    private String accessCode;

    // 🛑 האם האירוע נסגר ידנית? (מומלץ)
    @Column(name = "manually_closed", nullable = false)
    private boolean manuallyClosed = false;

    // =====================================================
    // 🔵 זמן ומיקום
    // =====================================================

    @Column(name = "wedding_date", nullable = false)
    private LocalDateTime weddingDate;        // תחילת האירוע (start)

    @Column(name = "wedding_end_time", nullable = false)
    private LocalDateTime weddingEndTime;     // סוף האירוע (ברירת מחדל 01:00 בלילה הבא)

    @Column(length = 150)
    private String hallName;                  // שם האולם / גן אירועים

    @Column(length = 255)
    private String hallAddress;               // כתובת מלאה (רחוב, מספר וכו')

    @Column(length = 100)
    private String city;                      // עיר האירוע

    // =====================================================
    // 🔵 בעלות והרשאות
    // =====================================================

    /**
     * קישור ישיר ל־User שהוא בעל האירוע (אופציונלי לטעינה).
     * ownerUserId מחזיק את ה-ID גם אם לא טוענים את האובייקט.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonIgnore
    private User owner;

    @Column(name = "owner_user_id")
    private Long ownerUserId;                 // מזהה בעל האירוע (גם בלי owner טעון)

    @Column(nullable = false)
    private boolean allowGlobalApprovalsByOwner = false;
    // האם לבעל האירוע מותר לאשר מאגר גלובלי

    /**
     * מי יצר את החתונה במערכת (בד"כ Admin).
     * לא שדה חובה – Metadata בלבד.
     */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    // =====================================================
    // 🔵 קישור למשתתפים
    // =====================================================

    /**
     * רשימת המשתתפים בחתונה.
     * בפועל ממופה לטבלת קשר wedding_participants (wedding_id, user_id).
     * משמשת גם ל-history וגם לסינון ב-Wedding Mode.
     */
    @ManyToMany
    @JoinTable(
            name = "wedding_participants",
            joinColumns = @JoinColumn(name = "wedding_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnore
    private List<User> participants;

    // =====================================================
    // 🔵 רקעים (תואם WeddingBackground / BackgroundService)
    // =====================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "background_mode", nullable = false, length = 20)
    private BackgroundMode backgroundMode = BackgroundMode.DEFAULT;   // IMAGE / VIDEO / DEFAULT

    @Column(name = "background_image_url")
    private String backgroundImageUrl;

    @Column(name = "background_video_url")
    private String backgroundVideoUrl;

    // הערות כלליות על החתונה (מופיע במסמך 2 – notes/location)
    @Column(length = 2000)
    private String notes;

    // =====================================================
    // 🔵 Hooks של JPA – טיפול תאריכים, endTime וסנכרון ownerUserId
    // =====================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // ברירת מחדל ל-weddingEndTime אם לא הוגדר:
        // יום למחרת בשעה 01:00 (כפי שמוגדר במסמכים).
        if (weddingDate != null && weddingEndTime == null) {
            LocalDate nextDay = weddingDate.toLocalDate().plusDays(1);
            this.weddingEndTime = LocalDateTime.of(nextDay, LocalTime.of(1, 0));
        }

        // סנכרון ownerUserId עם owner (אם מוגדר owner)
        if (owner != null && ownerUserId == null) {
            ownerUserId = owner.getId();
        }

        // הגנה – אם משום מה backgroundMode לא הוגדר
        if (backgroundMode == null) {
            backgroundMode = BackgroundMode.DEFAULT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();

        // סנכרון ownerUserId גם בעדכון
        if (owner != null && ownerUserId == null) {
            ownerUserId = owner.getId();
        }

        if (backgroundMode == null) {
            backgroundMode = BackgroundMode.DEFAULT;
        }
    }

    // =====================================================
    // 🔵 בנאים
    // =====================================================

    public Wedding() {
        // בנאי ריק נדרש ע"י JPA
    }

    public Wedding(String name,
                   LocalDateTime weddingDate,
                   LocalDateTime weddingEndTime,
                   Long ownerUserId,
                   String hallName,
                   String city) {

        this.name = name;
        this.weddingDate = weddingDate;
        this.weddingEndTime = weddingEndTime;
        this.ownerUserId = ownerUserId;
        this.hallName = hallName;
        this.city = city;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.backgroundMode = BackgroundMode.DEFAULT;
    }

    // =====================================================
    // 🔵 Getters & Setters
    // =====================================================

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
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

    public LocalDateTime getWeddingDate() {
        return weddingDate;
    }
    public void setWeddingDate(LocalDateTime weddingDate) {
        this.weddingDate = weddingDate;
    }

    public LocalDateTime getWeddingEndTime() {
        return weddingEndTime;
    }
    public void setWeddingEndTime(LocalDateTime weddingEndTime) {
        this.weddingEndTime = weddingEndTime;
    }

    public String getHallName() {
        return hallName;
    }
    public void setHallName(String hallName) {
        this.hallName = hallName;
    }

    public String getHallAddress() {
        return hallAddress;
    }
    public void setHallAddress(String hallAddress) {
        this.hallAddress = hallAddress;
    }

    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }

    public User getOwner() {
        return owner;
    }
    public void setOwner(User owner) {
        this.owner = owner;
        this.ownerUserId = (owner != null ? owner.getId() : null);
    }

    public Long getOwnerUserId() {
        if (ownerUserId == null && owner != null) {
            return owner.getId();
        }
        return ownerUserId;
    }
    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public boolean isAllowGlobalApprovalsByOwner() {
        return allowGlobalApprovalsByOwner;
    }
    public void setAllowGlobalApprovalsByOwner(boolean allowGlobalApprovalsByOwner) {
        this.allowGlobalApprovalsByOwner = allowGlobalApprovalsByOwner;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }
    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public List<User> getParticipants() {
        return participants;
    }
    public void setParticipants(List<User> participants) {
        this.participants = participants;
    }

    public BackgroundMode getBackgroundMode() {
        return backgroundMode;
    }
    public void setBackgroundMode(BackgroundMode backgroundMode) {
        this.backgroundMode = backgroundMode;
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

    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public boolean isManuallyClosed() {
        return manuallyClosed;
    }

    public void setManuallyClosed(boolean manuallyClosed) {
        this.manuallyClosed = manuallyClosed;
    }

    // =====================================================
    // 🔵 מתודות עזר
    // =====================================================

    /**
     * מחזיר את ה-URL האפקטיבי לרקע (תמונה / וידאו) לפי backgroundMode.
     * אם שניהם null – ה-Frontend יציג רקע ברירת מחדל.
     */
    @Transient
    public String getEffectiveBackgroundUrl() {
        if (backgroundMode == BackgroundMode.VIDEO && backgroundVideoUrl != null) {
            return backgroundVideoUrl;
        }
        if (backgroundMode == BackgroundMode.IMAGE && backgroundImageUrl != null) {
            return backgroundImageUrl;
        }
        return null; // DEFAULT → רקע ברירת מחדל בצד לקוח
    }

    /**
     * בדיקה אם החתונה כבר הסתיימה בזמן נתון (לשימוש ב־WeddingService/SystemRules).
     */
    @Transient
    public boolean isFinished(LocalDateTime now) {
        if (weddingEndTime == null) return false;
        return now.isAfter(weddingEndTime);
    }

    /**
     * בדיקה אם החתונה "חיה" כרגע (בין התחלה לסיום).
     */
    @Transient
    public boolean isLive(LocalDateTime now) {
        if (weddingDate == null || weddingEndTime == null) return false;
        return !now.isBefore(weddingDate) && !now.isAfter(weddingEndTime);
    }
}