package com.example.myproject.repository;

import com.example.myproject.model.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {

    // ============================================================
    // 🔵 בסיסי וקריטי
    // ============================================================

    Optional<SystemSettings> findByKeyName(String keyName);

    boolean existsByKeyName(String keyName);

    void deleteByKeyName(String keyName);

    // ✅ אופטימיזציה למחיקות מרובות (תחזוקה/ניקוי)
    void deleteByKeyNameIn(Collection<String> keyNames);

    // ============================================================
    // 🔵 Dashboard / חיפוש UI
    // ============================================================

    List<SystemSettings> findByKeyNameStartingWith(String prefix);

    List<SystemSettings> findByKeyNameContainingIgnoreCase(String text);

    List<SystemSettings> findByDescriptionContainingIgnoreCase(String text);

    // ✅ חסר לסרביס: חיפוש גם בערך (Admin Search מלא)
    List<SystemSettings> findByValueContainingIgnoreCase(String text);

    // ============================================================
    // 🔵 Auto Refresh / Live updates (prefix + time)
    // ============================================================

    // ✅ חסר לסרביס: שליפות “שינוי מאז זמן” תחת prefix (Jobs/Refresh)
    List<SystemSettings> findByKeyNameStartingWithAndUpdatedAtAfter(String prefix, LocalDateTime time);

    // ============================================================
    // 🔵 תחזוקה לפי זמן (יש לנו updatedAt בלבד)
    // ============================================================

    List<SystemSettings> findByUpdatedAtBefore(LocalDateTime time);

    List<SystemSettings> findByUpdatedAtAfter(LocalDateTime time);

    // ============================================================
    // 🔵 ברירת מחדל — ההגדרה האחרונה
    // ============================================================

    Optional<SystemSettings> findTopByOrderByUpdatedAtDesc();
}