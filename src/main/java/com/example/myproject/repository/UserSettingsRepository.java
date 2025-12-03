package com.example.myproject.repository;

import com.example.myproject.model.UserSettings;
import com.example.myproject.model.enums.DefaultMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות לפי משתמש
    // ============================================================

    // שליפת הגדרות לפי userId (הכי שימושי לסרוויסים)
    Optional<UserSettings> findByUser_Id(Long userId);

    // האם כבר קיימות הגדרות למשתמש
    boolean existsByUser_Id(Long userId);

    // מחיקת הגדרות למשתמש (לשימוש אדמין / Reset)
    void deleteByUser_Id(Long userId);


    // ============================================================
    // 🔵 2. מצב פתיחה (DefaultMode: GLOBAL / WEDDING)
    // ============================================================

    // כל מי שפותח כברירת מחדל על מצב מסוים
    List<UserSettings> findByDefaultMode(DefaultMode defaultMode);

    // כמה משתמשים במצב פתיחה מסוים (לדוחות)
    long countByDefaultMode(DefaultMode defaultMode);


    // ============================================================
    // 🔵 3. לוגיקת מגדר — צפייה באותו המין
    // ============================================================

    // כל מי שאיפשר צפייה באותו המין (תשתית עתידית)
    List<UserSettings> findByCanViewSameGenderTrue();

    long countByCanViewSameGenderTrue();


    // ============================================================
    // 🔵 4. Anti-Spam אישי (Like / Message Cooldown)
    // ============================================================

    // כל מי שמשתמש ב־Auto Anti-Spam
    List<UserSettings> findByAutoAntiSpamTrue();

    long countByAutoAntiSpamTrue();

    // משתמשים עם like-cooldown קטן/שווה לערך מסוים
    List<UserSettings> findByLikeCooldownSecondsLessThanEqual(Integer seconds);

    // משתמשים עם message-cooldown קטן/שווה לערך מסוים
    List<UserSettings> findByMessageCooldownSecondsLessThanEqual(Integer seconds);


    // ============================================================
    // 🔵 5. שימוש רוחבי ל-Dashboard / ניתוח הגדרות
    // ============================================================

    // כל המשתמשים עם הגדרות מוגדרות (פשוט שליפה כללית, כבר קיימת ב-JpaRepository findAll)

    // כמה משתמשים הגדירו בכלל כרטיס מקוצר מותאם אישית
    long countByShortCardFieldsJsonIsNotNull();

    // כמה משתמשים הגדירו העדפות UI
    long countByUiPreferencesJsonIsNotNull();

    // כמה משתמשים הגדירו extraSettingsJson
    long countByExtraSettingsJsonIsNotNull();
}