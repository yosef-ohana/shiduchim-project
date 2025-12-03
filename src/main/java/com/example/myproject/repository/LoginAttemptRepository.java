package com.example.myproject.repository;

import com.example.myproject.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות לפי אימייל/טלפון
    // ============================================================

    List<LoginAttempt> findByEmailOrPhoneOrderByAttemptTimeDesc(String emailOrPhone);

    // אחרון (לבדוק חסימה, OTP)
    Optional<LoginAttempt> findTopByEmailOrPhoneOrderByAttemptTimeDesc(String emailOrPhone);


    // ============================================================
    // 🔵 2. שליפות לפי טווח זמן / אבטחה (Anti-Spam + Brute Force)
    // ============================================================

    // נסיונות כושלים בזמן האחרון (שליטה על 3 כישלונות)
    List<LoginAttempt> findByEmailOrPhoneAndSuccessFalseAndAttemptTimeAfter(
            String emailOrPhone,
            LocalDateTime since
    );

    // כל הנסיונות בזמן מסוים (לדוחות)
    List<LoginAttempt> findByAttemptTimeBetween(
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 3. חסימות זמניות (3 כישלונות)
    // ============================================================

    // מי חסום עכשיו
    List<LoginAttempt> findByTemporaryBlockedTrue();

    // שליפת ניסיון שנחסם זמנית עם blockedUntil
    List<LoginAttempt> findByEmailOrPhoneAndTemporaryBlockedTrue(String emailOrPhone);

    // מי שעדיין חסום בזמן הנוכחי
    List<LoginAttempt> findByBlockedUntilAfter(LocalDateTime now);


    // ============================================================
    // 🔵 4. OTP – ניסיונות שמחייבים אימות נוסף
    // ============================================================

    List<LoginAttempt> findByEmailOrPhoneAndRequiresOtpTrue(String emailOrPhone);

    long countByEmailOrPhoneAndRequiresOtpTrue(String emailOrPhone);


    // ============================================================
    // 🔵 5. ניטור מתקפות (IP Monitoring)
    // ============================================================

    // כל הנסיונות מ־IP מסוים
    List<LoginAttempt> findByIpAddressOrderByAttemptTimeDesc(String ip);

    // ניסיונות כושלים מ־IP בזמן מוגבל (BRUTE FORCE)
    List<LoginAttempt> findByIpAddressAndSuccessFalseAndAttemptTimeAfter(
            String ip,
            LocalDateTime since
    );

    long countByIpAddressAndSuccessFalseAndAttemptTimeAfter(
            String ip,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 6. סטטיסטיקות – Dashboard Admin
    // ============================================================

    long countBySuccessFalse();   // כמה כישלונות מערכת-wide
    long countBySuccessTrue();    // כמה הצלחות

    long countByTemporaryBlockedTrue();  // כמה משתמשים בחסימה זמנית

    long countByAttemptTimeBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 7. התראות אבטחה (SystemRules §22)
    // ============================================================

    // מי ניסה להתחבר X פעמים לאחרונה
    long countByEmailOrPhoneAndAttemptTimeAfter(
            String emailOrPhone,
            LocalDateTime since
    );

    // מי נכשל 3 פעמים ברצף (משמש בבדיקה)
    long countByEmailOrPhoneAndSuccessFalse(String emailOrPhone);


    // ============================================================
    // 🔵 8. Clean-Up אוטומטי (לוגים ישנים)
    // ============================================================

    // רשומות שפג תוקפן
    List<LoginAttempt> findByExpiresAtBefore(LocalDateTime now);

    // רשומות ישנות לפי attemptTime
    List<LoginAttempt> findByAttemptTimeBefore(LocalDateTime threshold);


    // ============================================================
    // 🔵 9. שליפות מיוחדות לשירות האבטחה
    // ============================================================

    // נסיון אחרון (ללא OTP, רק חסימה)
    Optional<LoginAttempt> findTopByEmailOrPhoneAndTemporaryBlockedFalseOrderByAttemptTimeDesc(
            String emailOrPhone
    );

    // נסיון אחרון שהיה כישלון
    Optional<LoginAttempt> findTopByEmailOrPhoneAndSuccessFalseOrderByAttemptTimeDesc(
            String emailOrPhone
    );

    // נסיון אחרון שהצליח
    Optional<LoginAttempt> findTopByEmailOrPhoneAndSuccessTrueOrderByAttemptTimeDesc(
            String emailOrPhone
    );
}