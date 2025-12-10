package com.example.myproject.repository;

import com.example.myproject.model.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, Long> {

    // ============================================================
    // 🔵 1. שליפה / קיום לפי משתמש
    // ============================================================

    Optional<NotificationPreferences> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);


    // ============================================================
    // 🔵 2. טעינת הגדרות בקבוצות (Batch)
    // ============================================================

    List<NotificationPreferences> findByUserIdIn(List<Long> userIds);


    // ============================================================
    // 🔵 3. muteAll / muteUntil — חוקי SystemRules
    // ============================================================

    List<NotificationPreferences> findByMuteAllTrue();

    List<NotificationPreferences> findByMuteUntilAfter(LocalDateTime now);

    List<NotificationPreferences> findByMuteAllTrueOrMuteUntilAfter(LocalDateTime now);


    // ============================================================
    // 🔵 4. העדפות קריטיות — Match / SuperLike
    // ============================================================

    List<NotificationPreferences> findByAlwaysShowMatchTrue();

    List<NotificationPreferences> findByAlwaysShowSuperLikeTrue();


    // ============================================================
    // 🔵 5. תחזוקה / ניקוי לפי updatedAt
    // ============================================================

    List<NotificationPreferences> findByUpdatedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 6. Quiet Hours — שעות שקט
    // ============================================================

    List<NotificationPreferences> findByQuietHoursEnabledTrue();


    // ============================================================
    // 🔵 7. ערוצי התראה — Push / Email / In-App
    // ============================================================

    List<NotificationPreferences> findByEnablePushTrue();

    List<NotificationPreferences> findByEnableEmailTrue();

    List<NotificationPreferences> findByEnableInAppTrue();


    // ============================================================
    // 🔵 8. Anti-Spam / Throttle — הגבלת עומס התראות
    // ============================================================

    List<NotificationPreferences> findByThrottledTrue();

    List<NotificationPreferences> findByThrottleUntilAfter(LocalDateTime now);


    // ============================================================
    // 🔵 9. סטטיסטיקות — Dashboard Admin
    // ============================================================

    long countByMuteAllTrue();

    long countByMuteAllTrueOrMuteUntilAfter(LocalDateTime now);

    long countByQuietHoursEnabledTrue();

    long countByEnablePushTrue();

    long countByEnableEmailTrue();

    long countByEnableInAppTrue();


    // ============================================================
    // 🔵 10. פילוחים למערכות שליחה חכמות (Future AI Routing)
    // ============================================================

    // משתמשים שאינם רוצים Push → fallback ל־Email/InApp
    List<NotificationPreferences> findByEnablePushFalse();

    // משתמשים שאינם רוצים Email → fallback ל־Push/InApp
    List<NotificationPreferences> findByEnableEmailFalse();

    // משתמשים שרוצים רק In-App (ללא Push/Email)
    List<NotificationPreferences> findByEnableInAppTrueAndEnablePushFalseAndEnableEmailFalse();


    // ============================================================
    // 🔵 11. משתמשים שמוגבלים בקצב התראות (NotificationRate)
    // ============================================================

    // מי שיש לו limit של מספר התראות לתקופה (תשתית ל־SystemRules §13)
    List<NotificationPreferences> findByMaxNotificationsPerHourIsNotNull();

    List<NotificationPreferences> findByMaxNotificationsPerDayIsNotNull();
}