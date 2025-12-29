package com.example.myproject.repository;

import com.example.myproject.model.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    // ============================================================
    // 🔵 Dashboard / חיפוש UI
    // ============================================================

    List<SystemSettings> findByKeyNameStartingWith(String prefix);

    List<SystemSettings> findByKeyNameContainingIgnoreCase(String text);

    List<SystemSettings> findByDescriptionContainingIgnoreCase(String text);

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