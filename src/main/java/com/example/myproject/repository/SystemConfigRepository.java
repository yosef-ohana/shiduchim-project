package com.example.myproject.repository;

import com.example.myproject.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    // ============================================================
    // 🔵 1. קונפיג עדכני לפי environment
    // ============================================================

    // הקונפיג האחרון לסביבה מסוימת (prod / dev / staging ...)
    Optional<SystemConfig> findTopByEnvironmentOrderByCreatedAtDesc(String environment);

    // כל הגרסאות של קונפיג לסביבה מסוימת (לפי זמן)
    List<SystemConfig> findByEnvironmentOrderByCreatedAtDesc(String environment);

    // קונפיג גלובלי (environment = null) – ברירת מחדל לכל המערכת
    Optional<SystemConfig> findTopByEnvironmentIsNullOrderByCreatedAtDesc();

    List<SystemConfig> findByEnvironmentIsNullOrderByCreatedAtDesc();


    // ============================================================
    // 🔵 2. בדיקות קיום וספירה
    // ============================================================

    boolean existsByEnvironment(String environment);

    long countByEnvironment(String environment);


    // ============================================================
    // 🔵 3. טעינה לקבוצת סביבות (Warmup / Dashboard)
    // ============================================================

    List<SystemConfig> findByEnvironmentIn(List<String> environments);


    // ============================================================
    // 🔵 4. תחזוקה / ניקוי לפי תאריכים
    // ============================================================

    // קונפיג ישן לפני תאריך מסוים – לניקוי לוגים/ארכיון
    List<SystemConfig> findByCreatedAtBefore(LocalDateTime time);

    // שינויי קונפיג מהזמן האחרון – לניטור/דאשבורד
    List<SystemConfig> findByUpdatedAtAfter(LocalDateTime time);

    // היסטוריית קונפיג לסביבה בטווח תאריכים
    List<SystemConfig> findByEnvironmentAndCreatedAtBetween(
            String environment,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 5. קונפיג אחרון בכל מערכת (לא משנה סביבה)
    // ============================================================

    Optional<SystemConfig> findTopByOrderByCreatedAtDesc();
}