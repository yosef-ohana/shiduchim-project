package com.example.myproject.repository;

import com.example.myproject.model.Wedding;
import com.example.myproject.model.enums.BackgroundMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeddingRepository extends JpaRepository<Wedding, Long> {

    // ============================================================
    // 🔵 1. יצירה / שליפה בסיסית
    // ============================================================

    Optional<Wedding> findById(Long id);

    // שליפה לפי קוד כניסה (ברקוד / קישור)
    Optional<Wedding> findByAccessCode(String accessCode);

    // כל החתונות הפעילות
    List<Wedding> findByActiveTrue();

    // כל החתונות הלא-פעילות
    List<Wedding> findByActiveFalse();

    // חתונות שנוצרו ע"י מנהל/בעל אירוע מסוים
    List<Wedding> findByCreatedByUserId(Long userId);

    // חתונות שבבעלות משתמש מסוים

    // ============================================================
    // 🔵 2. סטטוס חתונה לפי זמנים (PLANNED / LIVE / ENDED)
    // ============================================================

    // חתונות שטרם התחילו
    List<Wedding> findByWeddingDateAfter(LocalDateTime now);

    // חתונות שכבר הסתיימו
    List<Wedding> findByWeddingEndTimeBefore(LocalDateTime now);

    // חתונות שחיות כרגע (LIVE)
    List<Wedding> findByWeddingDateBeforeAndWeddingEndTimeAfter(
            LocalDateTime now1,
            LocalDateTime now2
    );


    // ============================================================
    // 🔵 3. חתונות לפי עיר/מיקום/תאריכים
    // ============================================================

    List<Wedding> findByCity(String city);

    List<Wedding> findByHallName(String hallName);

    List<Wedding> findByHallAddressContainingIgnoreCase(String address);

    // חתונות בטווח תאריכים (לסטטיסטיקות/פאנל ניהול)
    List<Wedding> findByWeddingDateBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    List<Wedding> findByWeddingEndTimeBetween(
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 4. חתונות + בעלים / הרשאות
    // ============================================================

    // חתונות שבעל האירוע (owner) יכול לאשר גלובלי
    List<Wedding> findByAllowGlobalApprovalsByOwnerTrue();

    // חתונות שבהן משתמש מסוים הוא הבעלים הפעיל
    List<Wedding> findByOwnerUserIdAndActiveTrue(Long ownerUserId);

    // חתונות שהמשתמש הזה מנהל (owner או co-owner בעתיד)
    List<Wedding> findByOwnerUserId(Long ownerUserId);


    // ============================================================
    // 🔵 5. פעילים בחתונה (Heartbeat / מגבלות)
    // ============================================================

    // חתונות שנסגרו ידנית
    List<Wedding> findByManuallyClosedTrue();

    // חתונות שפתוחות לקהל
    List<Wedding> findByManuallyClosedFalseAndActiveTrue();

    // חתונות שאינן סגורות ידנית אך הסתיימו לפי זמן
    List<Wedding> findByManuallyClosedFalseAndWeddingEndTimeBefore(LocalDateTime now);


    // ============================================================
    // 🔵 6. פילטרים לאדמין — כל סוגי החתונות
    // ============================================================

    // כל החתונות שמתוכננות קדימה
    List<Wedding> findByWeddingDateAfterAndActiveTrue(LocalDateTime now);

    // חתונות חיות של אדמין
    List<Wedding> findByWeddingDateBeforeAndWeddingEndTimeAfterAndActiveTrue(
            LocalDateTime now1,
            LocalDateTime now2
    );

    // חתונות עבר של אדמין

    // כל החתונות (כולל לא-אקטיביות) לפי בעלים
    List<Wedding> findByOwnerUserIdOrderByWeddingDateAsc(Long ownerUserId);


    // ============================================================
    // 🔵 7. רקעים — Background / Theme Management
    // ============================================================

    // חתונות עם רקע מסוג מסוים (IMAGE / VIDEO / DEFAULT)
    List<Wedding> findByBackgroundMode(BackgroundMode mode);

    // חתונות שיש להן רקע תמונה
    List<Wedding> findByBackgroundImageUrlIsNotNull();

    // חתונות שיש להן רקע וידאו
    List<Wedding> findByBackgroundVideoUrlIsNotNull();


    // ============================================================
    // 🔵 8. חיתוכים מורכבים לחוקי המערכת (41 חוקים)
    // ============================================================

    // חתונות פעילות שבהן מותר לצפות
    List<Wedding> findByActiveTrueAndManuallyClosedFalse();

    // חתונות חיות (לשימוש בהתראות Match בזמן אמת)
    List<Wedding> findByActiveTrueAndWeddingDateBeforeAndWeddingEndTimeAfter(
            LocalDateTime now1,
            LocalDateTime now2
    );

    // חתונות שעדיין פתוחות לפעילות גם אחרי הסיום (המאגר נשאר זמין)
    List<Wedding> findByWeddingEndTimeBeforeAndActiveTrue(LocalDateTime now);

    // חתונות שעומדות להסתיים בקרוב (לצורך התראות/היגיון מערכת)
    List<Wedding> findByWeddingEndTimeBetweenOrderByWeddingEndTimeAsc(
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 9. שאילתות סטטיסטיקה — Dashboard Admin / Owner
    // ============================================================

    long countByCity(String city);

    long countByActiveTrue();

    long countByActiveFalse();

    long countByManuallyClosedTrue();

    long countByWeddingDateBefore(LocalDateTime now);

    long countByWeddingEndTimeBefore(LocalDateTime now);

    long countByWeddingDateAfter(LocalDateTime now);

    // לפי רקע
    long countByBackgroundMode(BackgroundMode mode);
}