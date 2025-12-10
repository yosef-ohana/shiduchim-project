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

    Optional<LoginAttempt> findTopByEmailOrPhoneOrderByAttemptTimeDesc(String emailOrPhone);


    // ============================================================
    // 🔵 2. שליפות לפי טווח זמן / אבטחה
    // ============================================================

    List<LoginAttempt> findByEmailOrPhoneAndSuccessFalseAndAttemptTimeAfter(
            String emailOrPhone,
            LocalDateTime since
    );

    List<LoginAttempt> findByAttemptTimeBetween(
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 3. חסימות זמניות (3 כישלונות)
    // ============================================================

    List<LoginAttempt> findByTemporaryBlockedTrue();

    List<LoginAttempt> findByEmailOrPhoneAndTemporaryBlockedTrue(String emailOrPhone);

    List<LoginAttempt> findByBlockedUntilAfter(LocalDateTime now);


    // ============================================================
    // 🔵 4. OTP – ניסיונות שמחייבים אימות נוסף
    // ============================================================

    List<LoginAttempt> findByEmailOrPhoneAndRequiresOtpTrue(String emailOrPhone);

    long countByEmailOrPhoneAndRequiresOtpTrue(String emailOrPhone);


    // ============================================================
    // 🔵 5. ניטור מתקפות (IP Monitoring)
    // ============================================================

    List<LoginAttempt> findByIpAddressOrderByAttemptTimeDesc(String ip);

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

    long countBySuccessFalse();

    long countBySuccessTrue();

    long countByTemporaryBlockedTrue();

    long countByAttemptTimeBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 7. התראות אבטחה (SystemRules §22)
    // ============================================================

    long countByEmailOrPhoneAndAttemptTimeAfter(
            String emailOrPhone,
            LocalDateTime since
    );

    long countByEmailOrPhoneAndSuccessFalse(String emailOrPhone);


    // ============================================================
    // 🔵 8. Clean-Up אוטומטי (לוגים ישנים)
    // ============================================================

    List<LoginAttempt> findByExpiresAtBefore(LocalDateTime now);

    List<LoginAttempt> findByAttemptTimeBefore(LocalDateTime threshold);


    // ============================================================
    // 🔵 9. שליפות מיוחדות לשירות האבטחה
    // ============================================================

    Optional<LoginAttempt> findTopByEmailOrPhoneAndTemporaryBlockedFalseOrderByAttemptTimeDesc(
            String emailOrPhone
    );

    Optional<LoginAttempt> findTopByEmailOrPhoneAndSuccessFalseOrderByAttemptTimeDesc(
            String emailOrPhone
    );

    Optional<LoginAttempt> findTopByEmailOrPhoneAndSuccessTrueOrderByAttemptTimeDesc(
            String emailOrPhone
    );


    // ============================================================
    // 🔵 10. תוספות חדשות – איתור מתקפות חכמות
    // ============================================================

    // 🆕 ניסיון לפי אימייל + IP (לזהות השתלטות חיצונית)
    List<LoginAttempt> findByEmailOrPhoneAndIpAddressOrderByAttemptTimeDesc(
            String emailOrPhone,
            String ipAddress
    );

    // 🆕 כל הנסיונות לפי deviceId (מכשיר מסוים)
    List<LoginAttempt> findByDeviceIdOrderByAttemptTimeDesc(String deviceId);

    // 🆕 כמות ניסיונות כושלים ממכשיר מסוים בזמן קצר
    long countByDeviceIdAndSuccessFalseAndAttemptTimeAfter(
            String deviceId,
            LocalDateTime since
    );

    // 🆕 ניסיון אחרון ממכשיר מסוים
    Optional<LoginAttempt> findTopByDeviceIdOrderByAttemptTimeDesc(String deviceId);

    // 🆕 כמה מכשירים שונים ניסו להתחבר לאותו חשבון
    long countDistinctByEmailOrPhoneAndDeviceIdIsNotNull(String emailOrPhone);


    // ============================================================
    // 🔵 11. אנליזה מתקדמת — Risk Engine (תשתית)
    // ============================================================

    // 🆕 כמות ניסיונות במכשיר *וב־IP* כקרוס־קורלציה (BRUTE + BOT)
    long countByIpAddressAndDeviceIdAndSuccessFalseAndAttemptTimeAfter(
            String ipAddress,
            String deviceId,
            LocalDateTime since
    );

    // 🆕 כמות ניסיונות עם userAgent חדש (מכשיר חדש / לקוח חשוד)
    long countByEmailOrPhoneAndUserAgentAndAttemptTimeAfter(
            String emailOrPhone,
            String userAgent,
            LocalDateTime since
    );
}