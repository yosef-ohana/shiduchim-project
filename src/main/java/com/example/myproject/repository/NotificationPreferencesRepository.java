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

    // הגדרות ההתראות למשתמש מסוים
    Optional<NotificationPreferences> findByUserId(Long userId);

    // בדיקה האם יש הגדרות למשתמש
    boolean existsByUserId(Long userId);

    // מחיקת הגדרות כאשר מוחקים משתמש / מאפסים אותו
    void deleteByUserId(Long userId);


    // ============================================================
    // 🔵 2. טעינת הגדרות בקבוצות (Batch)
    //     שימושי ב-NotificationService כשנטען הרבה משתמשים בבת אחת
    // ============================================================

    List<NotificationPreferences> findByUserIdIn(List<Long> userIds);


    // ============================================================
    // 🔵 3. muteAll / muteUntil — חוקי SystemRules (הגבלת התראות)
    // ============================================================

    // כל מי שמושתק לגמרי כרגע (muteAll = true)
    List<NotificationPreferences> findByMuteAllTrue();

    // כל מי שיש לו muteUntil אחרי זמן מסוים (עדיין מושתק זמנית)
    List<NotificationPreferences> findByMuteUntilAfter(LocalDateTime now);

    // שילוב — כל מי שמושתק כרגע (או muteAll או muteUntil פעיל)
    List<NotificationPreferences> findByMuteAllTrueOrMuteUntilAfter(LocalDateTime now);


    // ============================================================
    // 🔵 4. העדפות קריטיות — Match / SuperLike
    //     (התראות שחייבות להישלח למרות הגבלות אחרות)
    // ============================================================

    // משתמשים שביקשו תמיד לראות התראות Match
    List<NotificationPreferences> findByAlwaysShowMatchTrue();

    // משתמשים שביקשו תמיד לראות התראות SuperLike
    List<NotificationPreferences> findByAlwaysShowSuperLikeTrue();


    // ============================================================
    // 🔵 5. תחזוקה / ניקוי — לפי updatedAt
    // ============================================================

    // הגדרות ישנות – לצורך אנליזה/ניקוי/מיגרציה
    List<NotificationPreferences> findByUpdatedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 6. Quiet Hours — שעות שקט
    // ============================================================

    // כל מי שהפעיל "שעות שקט" (החישוב אם כרגע שקט נעשה ב-Service / SystemRules)
    List<NotificationPreferences> findByQuietHoursEnabledTrue();


    // ============================================================
    // 🔵 7. ערוצי התראה (Channels) — Push / Email / In-App
    // ============================================================

    // משתמשים המאפשרים Push Notifications
    List<NotificationPreferences> findByEnablePushTrue();

    // משתמשים המאפשרים Email Notifications
    List<NotificationPreferences> findByEnableEmailTrue();

    // משתמשים המאפשרים In-App בלבד (או כחלק מערוצים נוספים)
    List<NotificationPreferences> findByEnableInAppTrue();


    // ============================================================
    // 🔵 8. Anti-Spam / Throttle — הגבלת עומס התראות
    // ============================================================

    // משתמשים שנמצאים כרגע במצב "throttled" (קיבלו יותר מדי התראות)
    List<NotificationPreferences> findByThrottledTrue();

    // משתמשים שה-throttle שלהם עדיין פעיל בזמן נתון
    List<NotificationPreferences> findByThrottleUntilAfter(LocalDateTime now);
}