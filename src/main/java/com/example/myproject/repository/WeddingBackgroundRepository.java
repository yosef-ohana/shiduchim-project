package com.example.myproject.repository;

import com.example.myproject.model.WeddingBackground;
import com.example.myproject.model.enums.BackgroundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeddingBackgroundRepository extends JpaRepository<WeddingBackground, Long> {

    // ============================================================
    // 🔵 1. שליפות רקעים פעילים / "שימושיים" – לפי חתונה
    // ============================================================

    // כל הרקעים (לא מחוקים) של חתונה מסוימת לפי זמן יצירה
    List<WeddingBackground> findByWeddingIdAndDeletedFalseOrderByCreatedAtDesc(Long weddingId);

    // רק רקעים פעילים ו"שימושיים" (לא unsuitable ולא deleted)
    List<WeddingBackground> findByWeddingIdAndActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc(
            Long weddingId
    );

    // רקע ברירת מחדל של חתונה (אם קיים)
    Optional<WeddingBackground> findFirstByWeddingIdAndActiveTrueAndDeletedFalseAndUnsuitableFalseAndDefaultBackgroundTrueOrderByCreatedAtDesc(
            Long weddingId
    );


    // ============================================================
    // 🔵 2. שליפות רקעים גלובליים
    // ============================================================

    // כל הרקעים הגלובליים (לא מחוקים)
    List<WeddingBackground> findByGlobalTrueAndDeletedFalseOrderByCreatedAtDesc();

    // רק הרקעים הגלובליים הפעילים והשימושיים
    List<WeddingBackground> findByGlobalTrueAndActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc();

    // רקע גלובלי ברירת מחדל (הראשי למערכת)
    Optional<WeddingBackground> findFirstByGlobalTrueAndActiveTrueAndDeletedFalseAndUnsuitableFalseAndDefaultBackgroundTrueOrderByCreatedAtDesc();

    // לפי סוג (IMAGE / VIDEO) – שימושי אם תרצה להפריד בין סוגי רקעים
    List<WeddingBackground> findByGlobalTrueAndTypeAndDeletedFalseOrderByCreatedAtDesc(BackgroundType type);


    // ============================================================
    // 🔵 3. רקעים לא מתאימים / מחוקים – עבור Admin
    // ============================================================

    // כל הרקעים שסומנו "לא מתאים"
    List<WeddingBackground> findByUnsuitableTrueAndDeletedFalseOrderByUnsuitableAtDesc();

    // כל הרקעים שנמחקו (soft delete)
    List<WeddingBackground> findByDeletedTrueOrderByDeletedAtDesc();

    // לריצת CRON – מחיקה פיזית אחרי X ימים
    List<WeddingBackground> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoff);


    // ============================================================
    // 🔵 4. שליפות כלליות – לדשבורד / סטטיסטיקות
    // ============================================================

    long countByGlobalTrueAndDeletedFalse();                 // כמה רקעים גלובליים קיימים
    long countByGlobalFalseAndDeletedFalse();                // כמה רקעי חתונה קיימים

    long countByActiveTrueAndDeletedFalseAndUnsuitableFalse(); // כמה רקעים "שימושיים" במערכת

    long countByWeddingIdAndDeletedFalse(Long weddingId);
    long countByWeddingIdAndActiveTrueAndDeletedFalseAndUnsuitableFalse(Long weddingId);


    // ============================================================
    // 🔵 5. ניהול "ברירת מחדל" – רקע ראשי
    // ============================================================
    // חשוב ל-BackgroundService כשמחליפים רקע חתונה / גלובלי

    // אפס את כל ברירות המחדל של חתונה ספציפית (לפני שמגדירים חדשה)
    @Modifying
    @Query("UPDATE WeddingBackground wb " +
            "SET wb.defaultBackground = false " +
            "WHERE wb.wedding.id = :weddingId AND wb.deleted = false")
    void clearDefaultForWedding(@Param("weddingId") Long weddingId);

    // אפס את כל הרקעים הגלובליים כברירת מחדל
    @Modifying
    @Query("UPDATE WeddingBackground wb " +
            "SET wb.defaultBackground = false " +
            "WHERE wb.global = true AND wb.deleted = false")
    void clearDefaultForGlobal();


    // ============================================================
    // 🔵 6. שליפות "שימושיות" ישר לשכבת BackgroundService
    // ============================================================

    // רקע שימושי (active + !unsuitable + !deleted) לחתונה, סדר לפי עדיפות:
    // קודם default, ואם אין → לפי createdAt יורד
    @Query("""
           SELECT wb
           FROM WeddingBackground wb
           WHERE wb.wedding.id = :weddingId
             AND wb.active = true
             AND wb.deleted = false
             AND wb.unsuitable = false
           ORDER BY wb.defaultBackground DESC, wb.createdAt DESC
           """)
    List<WeddingBackground> findUsableBackgroundsForWedding(@Param("weddingId") Long weddingId);

    // רקע שימושי גלובלי, לפי עדיפות (default → newest)
    @Query("""
           SELECT wb
           FROM WeddingBackground wb
           WHERE wb.global = true
             AND wb.active = true
             AND wb.deleted = false
             AND wb.unsuitable = false
           ORDER BY wb.defaultBackground DESC, wb.createdAt DESC
           """)
    List<WeddingBackground> findUsableGlobalBackgrounds();


    // ============================================================
    // 🔵 7. פילטרים משולבים – עבור מסכי ניהול מתקדמים
    // ============================================================

    // כל הרקעים של חתונה, לפי סטטוס "פעיל"
    List<WeddingBackground> findByWeddingIdAndActiveAndDeletedFalseOrderByCreatedAtDesc(
            Long weddingId,
            boolean active
    );

    // רקעים של חתונה לפי TYPE (תמונה/וידאו)
    List<WeddingBackground> findByWeddingIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(
            Long weddingId,
            BackgroundType type
    );

    // כל הרקעים הפעילים (גם גלובליים וגם חתונות)
    List<WeddingBackground> findByActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc();
}