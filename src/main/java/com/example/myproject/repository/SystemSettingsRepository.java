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
    // 🔵 1. שליפה לפי keyName — הכי חשוב במערכת
    // ============================================================

    Optional<SystemSettings> findByKeyName(String keyName);

    boolean existsByKeyName(String keyName);

    void deleteByKeyName(String keyName);


    // ============================================================
    // 🔵 2. שליפות לפי תבנית — Admin Dashboard
    // ============================================================

    // כל המפתחות שמתחילים בקידומת (notification.*, wedding.*, system.*, etc.)
    List<SystemSettings> findByKeyNameStartingWith(String prefix);

    // כל ההגדרות שמסתיימות בסיומת מסוימת
    List<SystemSettings> findByKeyNameEndingWith(String suffix);

    // חיפוש מפתח שמכיל מילה מסוימת (לוגיקת חיפוש בדשבורד)
    List<SystemSettings> findByKeyNameContainingIgnoreCase(String text);


    // ============================================================
    // 🔵 3. שליפות לפי תיאור (description) — קיים במסמכים
    // ============================================================

    List<SystemSettings> findByDescriptionContainingIgnoreCase(String text);


    // ============================================================
    // 🔵 4. ניקיון ותחזוקה
    // ============================================================

    // שליפת מפתחות שהשתנו לפני X זמן — לניקוי/בדיקה
    List<SystemSettings> findByUpdatedAtBefore(LocalDateTime time);

    // שליפת מפתחות שהשתנו אחרי זמן מסוים — למעקב ניהול
    List<SystemSettings> findByUpdatedAtAfter(LocalDateTime time);


    // ============================================================
    // 🔵 5. שימושי מערכת מתקדמים (תשתית ל-AI & Auto-Config)
    // ============================================================

    // שליפת מפתחות לפי רשימת מפתחות (bulk multi-key)
    List<SystemSettings> findByKeyNameIn(List<String> keys);

    // כמה הגדרות קיימות לפי prefix (כמות config לדשבורד)
    long countByKeyNameStartingWith(String prefix);

}