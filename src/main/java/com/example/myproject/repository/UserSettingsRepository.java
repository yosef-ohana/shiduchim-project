package com.example.myproject.repository;

import com.example.myproject.model.UserSettings;
import com.example.myproject.model.enums.DefaultMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות לפי משתמש
    // ============================================================

    Optional<UserSettings> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);


    // ============================================================
    // 🔵 2. מצב פתיחה (DefaultMode: GLOBAL / WEDDING)
    // ============================================================

    List<UserSettings> findByDefaultMode(DefaultMode defaultMode);

    long countByDefaultMode(DefaultMode defaultMode);

    // משתמשים שההגדרה שלהם שונה מהברירת־מחדל (לניתוח SystemRules)
    List<UserSettings> findByDefaultModeNot(DefaultMode defaultMode);


    // ============================================================
    // 🔵 3. לוגיקת מגדר — צפייה באותו המין
    // ============================================================

    List<UserSettings> findByCanViewSameGenderTrue();

    long countByCanViewSameGenderTrue();


    // ============================================================
    // 🔵 4. Anti-Spam אישי (Like / Message Cooldown)
    // ============================================================

    List<UserSettings> findByAutoAntiSpamTrue();

    long countByAutoAntiSpamTrue();

    List<UserSettings> findByLikeCooldownSecondsLessThanEqual(Integer seconds);

    List<UserSettings> findByMessageCooldownSecondsLessThanEqual(Integer seconds);


    // ============================================================
    // 🔵 5. שימוש רוחבי ל-Dashboard / ניתוח הגדרות
    // ============================================================

    long countByShortCardFieldsJsonIsNotNull();

    long countByUiPreferencesJsonIsNotNull();

    long countByExtraSettingsJsonIsNotNull();


    // ============================================================
    // 🔵 6. ⚠ Lock Mode After Wedding — תמיכה מלאה בחוקי מערכת
    //     (Rules: 14, 19, 23, 27 — משתמש נעול עד שיסיים פרופיל מלא)
    // ============================================================

    // מי מוגדר כנעול אחרי חתונה
    List<UserSettings> findByLockedAfterWeddingTrue();

    // מי עדיין נעול (lockedUntil > now)
    List<UserSettings> findByLockedAfterWeddingTrueAndLockedUntilAfter(LocalDateTime now);

    // כמה משתמשים במצב Lock (ללא קשר ל־lockedUntil)
    long countByLockedAfterWeddingTrue();


    // ============================================================
    // 🔵 7. תחזוקה / ניקוי לפי זמן
    // ============================================================

    List<UserSettings> findByUpdatedAtAfter(LocalDateTime time);

    List<UserSettings> findByCreatedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 8. סטטיסטיקות מתקדמות — Anti-Spam & Lock
    // ============================================================

    // כמה משתמשים עם Anti-Spam אוטומטי ובקירור לייק קטן/שווה לערך מסוים
    long countByAutoAntiSpamTrueAndLikeCooldownSecondsLessThanEqual(Integer seconds);

    // כמה משתמשים עם Anti-Spam אוטומטי ובקירור הודעות קטן/שווה לערך מסוים
    long countByAutoAntiSpamTrueAndMessageCooldownSecondsLessThanEqual(Integer seconds);

    // כמה משתמשים עדיין נעולים כרגע (lockedAfterWedding + lockedUntil > now)
    long countByLockedAfterWeddingTrueAndLockedUntilAfter(LocalDateTime now);
}