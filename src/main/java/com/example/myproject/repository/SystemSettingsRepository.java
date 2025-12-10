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
    // 🔵 1. שליפה לפי keyName — בסיסי וקריטי
    // ============================================================

    Optional<SystemSettings> findByKeyName(String keyName);

    boolean existsByKeyName(String keyName);

    void deleteByKeyName(String keyName);


    // ============================================================
    // 🔵 2. תמיכה ב-SCOPE (system / wedding / user)
    // ============================================================

    List<SystemSettings> findByScope(String scope);

    List<SystemSettings> findByScopeAndKeyName(String scope, String keyName);

    List<SystemSettings> findByScopeAndKeyNameStartingWith(String scope, String prefix);

    List<SystemSettings> findByScopeAndKeyNameIn(String scope, List<String> keys);

    List<SystemSettings> findByScopeAndKeyNameContainingIgnoreCase(String scope, String text);

    // 🆕 שליפות לפי Scope + טווח זמן (לדשבורד/לוגים)
    List<SystemSettings> findByScopeAndUpdatedAtAfter(String scope, LocalDateTime time);

    List<SystemSettings> findByScopeAndUpdatedAtBefore(String scope, LocalDateTime time);


    // ============================================================
    // 🔵 3. תמיכה ב-Rule Engine (SystemRules §1–41)
    // ============================================================

    List<SystemSettings> findByRuleId(Integer ruleId);

    List<SystemSettings> findByRuleGroup(String ruleGroup);

    List<SystemSettings> findByRuleGroupAndKeyNameStartingWith(String ruleGroup, String prefix);

    // 🆕 חיבור בין RuleEngine ל-SCOPE
    List<SystemSettings> findByRuleGroupAndScope(String ruleGroup, String scope);

    List<SystemSettings> findByRuleIdAndScope(Integer ruleId, String scope);


    // ============================================================
    // 🔵 4. שליפות לפי תבנית — Dashboard / UI
    // ============================================================

    List<SystemSettings> findByKeyNameStartingWith(String prefix);

    List<SystemSettings> findByKeyNameEndingWith(String suffix);

    List<SystemSettings> findByKeyNameContainingIgnoreCase(String text);


    // ============================================================
    // 🔵 5. לפי description — חיפוש לממשק ניהול
    // ============================================================

    List<SystemSettings> findByDescriptionContainingIgnoreCase(String text);

    // 🆕 לפי description + Scope (חיפוש עדין יותר במסכי הגדרות)
    List<SystemSettings> findByScopeAndDescriptionContainingIgnoreCase(String scope, String text);


    // ============================================================
    // 🔵 6. תחזוקה / ניקוי לפי זמן + Environment
    // ============================================================

    List<SystemSettings> findByUpdatedAtBefore(LocalDateTime time);

    List<SystemSettings> findByUpdatedAtAfter(LocalDateTime time);

    List<SystemSettings> findByCreatedAtBefore(LocalDateTime time);

    List<SystemSettings> findByEnvironmentAndCreatedAtBetween(
            String environment,
            LocalDateTime start,
            LocalDateTime end
    );

    // 🆕 שליפות ישירות לפי Environment (לפרופילי dev / prod / test)
    List<SystemSettings> findByEnvironment(String environment);

    List<SystemSettings> findByEnvironmentAndUpdatedAtAfter(
            String environment,
            LocalDateTime time
    );


    // ============================================================
    // 🔵 7. תמיכה ב-Auto Refresh / Dynamic Config
    // ============================================================

    List<SystemSettings> findByKeyNameStartingWithAndUpdatedAtAfter(
            String prefix,
            LocalDateTime time
    );

    // 🆕 Auto-Refresh לפי Scope + Prefix (למשל: "security." / "notifications.")
    List<SystemSettings> findByScopeAndKeyNameStartingWithAndUpdatedAtAfter(
            String scope,
            String prefix,
            LocalDateTime time
    );


    // ============================================================
    // 🔵 8. ברירת מחדל — הגדרה גלובלית אחרונה
    // ============================================================

    Optional<SystemSettings> findTopByOrderByCreatedAtDesc();

    // 🆕 ברירת מחדל לפי Scope / Environment
    Optional<SystemSettings> findTopByScopeOrderByCreatedAtDesc(String scope);

    Optional<SystemSettings> findTopByEnvironmentOrderByCreatedAtDesc(String environment);
}