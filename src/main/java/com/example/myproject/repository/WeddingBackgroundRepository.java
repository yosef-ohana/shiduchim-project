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

    List<WeddingBackground> findByWedding_IdAndDeletedFalseOrderByCreatedAtDesc(Long weddingId);

    List<WeddingBackground> findByWedding_IdAndActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc(Long weddingId);

    Optional<WeddingBackground> findFirstByWedding_IdAndActiveTrueAndDeletedFalseAndUnsuitableFalseAndDefaultBackgroundTrueOrderByCreatedAtDesc(Long weddingId);

    // ============================================================
    // 🔵 2. שליפות רקעים גלובליים
    // ============================================================

    List<WeddingBackground> findByGlobalTrueAndDeletedFalseOrderByCreatedAtDesc();

    List<WeddingBackground> findByGlobalTrueAndActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc();

    Optional<WeddingBackground> findFirstByGlobalTrueAndActiveTrueAndDeletedFalseAndUnsuitableFalseAndDefaultBackgroundTrueOrderByCreatedAtDesc();

    List<WeddingBackground> findByGlobalTrueAndTypeAndDeletedFalseOrderByCreatedAtDesc(BackgroundType type);

    // ============================================================
    // 🔵 3. רקעים לא מתאימים / מחוקים – עבור Admin
    // ============================================================

    List<WeddingBackground> findByUnsuitableTrueAndDeletedFalseOrderByUnsuitableAtDesc();

    List<WeddingBackground> findByDeletedTrueOrderByDeletedAtDesc();

    List<WeddingBackground> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoff);

    // ============================================================
    // 🔵 4. שליפות כלליות – לדשבורד / סטטיסטיקות
    // ============================================================

    long countByGlobalTrueAndDeletedFalse();

    long countByGlobalFalseAndDeletedFalse();

    long countByActiveTrueAndDeletedFalseAndUnsuitableFalse();

    long countByWedding_IdAndDeletedFalse(Long weddingId);

    long countByWedding_IdAndActiveTrueAndDeletedFalseAndUnsuitableFalse(Long weddingId);

    // ============================================================
    // 🔵 5. ניהול "ברירת מחדל" – רקע ראשי
    // ============================================================

    @Modifying
    @Query("UPDATE WeddingBackground wb " +
            "SET wb.defaultBackground = false " +
            "WHERE wb.wedding.id = :weddingId AND wb.deleted = false")
    void clearDefaultForWedding(@Param("weddingId") Long weddingId);

    @Modifying
    @Query("UPDATE WeddingBackground wb " +
            "SET wb.defaultBackground = false " +
            "WHERE wb.global = true AND wb.deleted = false")
    void clearDefaultForGlobal();

    // ============================================================
    // 🔵 6. שליפות "שימושיות" ישר לשכבת BackgroundService
    // ============================================================

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

    List<WeddingBackground> findByWedding_IdAndActiveAndDeletedFalseOrderByCreatedAtDesc(Long weddingId, boolean active);

    List<WeddingBackground> findByWedding_IdAndTypeAndDeletedFalseOrderByCreatedAtDesc(Long weddingId, BackgroundType type);

    List<WeddingBackground> findByActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc();
}