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

    // חתונות שבבעלות משתמש מסוים (Owner)
    List<Wedding> findByOwnerUserId(Long ownerUserId);


    // ============================================================
    // 🔵 2. סטטוס חתונה לפי זמנים (PLANNED / LIVE / ENDED)
    // ============================================================

    // חתונות שטרם התחילו
    List<Wedding> findByWeddingDateAfter(LocalDateTime now);

    // חתונות שכבר הסתיימו
    List<Wedding> findByWeddingEndTimeBefore(LocalDateTime now);

    // חתונות LIVE
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

    List<Wedding> findByWeddingDateBetween(LocalDateTime start, LocalDateTime end);

    List<Wedding> findByWeddingEndTimeBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 4. חתונות + בעלים / הרשאות
    // ============================================================

    // חתונות שבעל האירוע (owner) יכול לאשר גלובלי
    List<Wedding> findByAllowGlobalApprovalsByOwnerTrue();

    // חתונות שבהן המשתמש הוא הבעלים הפעיל
    List<Wedding> findByOwnerUserIdAndActiveTrue(Long ownerUserId);


    // ============================================================
    // 🔵 5. פעילים בחתונה (Heartbeat / מגבלות)
    // ============================================================

    List<Wedding> findByManuallyClosedTrue();

    List<Wedding> findByManuallyClosedFalseAndActiveTrue();

    List<Wedding> findByManuallyClosedFalseAndWeddingEndTimeBefore(LocalDateTime now);


    // ============================================================
    // 🔵 6. חתונות — חיתוכים לאדמין
    // ============================================================

    List<Wedding> findByWeddingDateAfterAndActiveTrue(LocalDateTime now);

    List<Wedding> findByWeddingDateBeforeAndWeddingEndTimeAfterAndActiveTrue(
            LocalDateTime now1,
            LocalDateTime now2
    );

    List<Wedding> findByOwnerUserIdOrderByWeddingDateAsc(Long ownerUserId);


    // ============================================================
    // 🔵 7. רקעים — Background / Themes
    // ============================================================

    List<Wedding> findByBackgroundMode(BackgroundMode mode);

    List<Wedding> findByBackgroundImageUrlIsNotNull();

    List<Wedding> findByBackgroundVideoUrlIsNotNull();


    // ============================================================
    // 🔵 8. חיתוכים מורכבים — SystemRules / Monitoring
    // ============================================================

    // חתונות פעילות שאינן סגורות (מאגר זמין)
    List<Wedding> findByActiveTrueAndManuallyClosedFalse();

    // חתונות חיות (לצורך LIVE MATCH notifications)
    List<Wedding> findByActiveTrueAndWeddingDateBeforeAndWeddingEndTimeAfter(
            LocalDateTime now1,
            LocalDateTime now2
    );

    // חתונות פתוחות גם אחרי הזמן (מאגר חתונה נשאר זמין)
    List<Wedding> findByWeddingEndTimeBeforeAndActiveTrue(LocalDateTime now);

    // חתונות שעומדות להסתיים בקרוב
    List<Wedding> findByWeddingEndTimeBetweenOrderByWeddingEndTimeAsc(
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 9. סטטיסטיקות — Dashboard Admin/Owner
    // ============================================================

    long countByCity(String city);

    long countByActiveTrue();

    long countByActiveFalse();

    long countByManuallyClosedTrue();

    long countByWeddingDateBefore(LocalDateTime now);

    long countByWeddingEndTimeBefore(LocalDateTime now);

    long countByWeddingDateAfter(LocalDateTime now);

    long countByBackgroundMode(BackgroundMode mode);


    // ============================================================
    // 🔵 10. שאילתות חסרות – נוספו עכשיו כדי לכסות את מלוא האפיון
    // ============================================================

    // ✔ חתונות פתוחות לפני תחילת האירוע (מאגר פתוח לפי האפיון החדש)
    List<Wedding> findByActiveTrueAndWeddingDateAfter(LocalDateTime now);

    // ✔ חתונות שמתנהלות כרגע (לא רק LIVE לפי זמן, אלא ACTIVE + window check)
    List<Wedding> findByActiveTrueAndWeddingEndTimeAfter(LocalDateTime now);

    // ✔ חתונות PRIVATE / PUBLIC (תמיכה מלאה באפיון הדור הבא)
    List<Wedding> findByIsPublicTrue();
    List<Wedding> findByIsPublicFalse();

    // ✔ חתונות לפי AllowCandidatePool (מאגר פתוח במיוחד)
    List<Wedding> findByAllowCandidatePoolTrue();

    // ✔ חתונות לפי Owner + PLANNED
    List<Wedding> findByOwnerUserIdAndWeddingDateAfter(Long ownerUserId, LocalDateTime now);

    // ✔ חתונות לפי Owner + ENDED
    List<Wedding> findByOwnerUserIdAndWeddingEndTimeBefore(Long ownerUserId, LocalDateTime now);

    // ✔ חתונות לפי Owner + LIVE status
    List<Wedding> findByOwnerUserIdAndWeddingDateBeforeAndWeddingEndTimeAfter(
            Long ownerUserId,
            LocalDateTime now1,
            LocalDateTime now2
    );

    Optional<Wedding> findByWeddingToken(String weddingToken);
    boolean existsByAccessCode(String accessCode);
    boolean existsByWeddingToken(String weddingToken);
}