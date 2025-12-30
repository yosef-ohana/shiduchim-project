package com.example.myproject.repository;

import com.example.myproject.model.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות לפי אימייל/טלפון
    // ============================================================

    /**
     * ⚠️ קריטי: EmailOrPhone בשם השדה גורם ל-Spring לפרש "OR".
     * ✅ לכן מכריחים JPQL מפורש על השדה emailOrPhone.
     */
    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "order by la.attemptTime desc")
    List<LoginAttempt> findByEmailOrPhoneOrderByAttemptTimeDesc(@Param("emailOrPhone") String emailOrPhone);

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "order by la.attemptTime desc")
    Optional<LoginAttempt> findTopByEmailOrPhoneOrderByAttemptTimeDesc(@Param("emailOrPhone") String emailOrPhone);


    // ============================================================
    // 🔵 2. שליפות לפי טווח זמן / אבטחה
    // ============================================================

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.success = false " +
            "and la.attemptTime > :since")
    List<LoginAttempt> findByEmailOrPhoneAndSuccessFalseAndAttemptTimeAfter(
            @Param("emailOrPhone") String emailOrPhone,
            @Param("since") LocalDateTime since
    );

    List<LoginAttempt> findByAttemptTimeBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 3. חסימות זמניות (3 כישלונות)
    // ============================================================

    List<LoginAttempt> findByTemporaryBlockedTrue();

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.temporaryBlocked = true")
    List<LoginAttempt> findByEmailOrPhoneAndTemporaryBlockedTrue(@Param("emailOrPhone") String emailOrPhone);

    List<LoginAttempt> findByBlockedUntilAfter(LocalDateTime now);


    // ============================================================
    // 🔵 4. OTP – ניסיונות שמחייבים אימות נוסף
    // ============================================================

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.requiresOtp = true")
    List<LoginAttempt> findByEmailOrPhoneAndRequiresOtpTrue(@Param("emailOrPhone") String emailOrPhone);

    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.requiresOtp = true")
    long countByEmailOrPhoneAndRequiresOtpTrue(@Param("emailOrPhone") String emailOrPhone);


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

    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.attemptTime > :since")
    long countByEmailOrPhoneAndAttemptTimeAfter(
            @Param("emailOrPhone") String emailOrPhone,
            @Param("since") LocalDateTime since
    );

    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.success = false")
    long countByEmailOrPhoneAndSuccessFalse(@Param("emailOrPhone") String emailOrPhone);


    // ============================================================
    // 🔵 8. Clean-Up אוטומטי (לוגים ישנים)
    // ============================================================

    List<LoginAttempt> findByExpiresAtBefore(LocalDateTime now);

    List<LoginAttempt> findByAttemptTimeBefore(LocalDateTime threshold);

    /**
     * 🧱 תשתית קדימה: מחיקה פיזית של ניסיונות ישנים (ל-Jobs).
     * (לא שובר כלום כי זו תוספת בלבד)
     */
    @Transactional
    @Modifying
    @Query("delete from LoginAttempt la where la.attemptTime < :threshold")
    int deleteAllByAttemptTimeBefore(@Param("threshold") LocalDateTime threshold);


    // ============================================================
    // 🔵 9. שליפות מיוחדות לשירות האבטחה
    // ============================================================

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.temporaryBlocked = false " +
            "order by la.attemptTime desc")
    Optional<LoginAttempt> findTopByEmailOrPhoneAndTemporaryBlockedFalseOrderByAttemptTimeDesc(
            @Param("emailOrPhone") String emailOrPhone
    );

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.success = false " +
            "order by la.attemptTime desc")
    Optional<LoginAttempt> findTopByEmailOrPhoneAndSuccessFalseOrderByAttemptTimeDesc(
            @Param("emailOrPhone") String emailOrPhone
    );

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.success = true " +
            "order by la.attemptTime desc")
    Optional<LoginAttempt> findTopByEmailOrPhoneAndSuccessTrueOrderByAttemptTimeDesc(
            @Param("emailOrPhone") String emailOrPhone
    );


    // ============================================================
    // 🔵 10. תוספות חדשות – איתור מתקפות חכמות
    // ============================================================

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.ipAddress = :ipAddress " +
            "order by la.attemptTime desc")
    List<LoginAttempt> findByEmailOrPhoneAndIpAddressOrderByAttemptTimeDesc(
            @Param("emailOrPhone") String emailOrPhone,
            @Param("ipAddress") String ipAddress
    );

    List<LoginAttempt> findByDeviceIdOrderByAttemptTimeDesc(String deviceId);

    long countByDeviceIdAndSuccessFalseAndAttemptTimeAfter(
            String deviceId,
            LocalDateTime since
    );

    Optional<LoginAttempt> findTopByDeviceIdOrderByAttemptTimeDesc(String deviceId);

    @Query("select count(distinct la.deviceId) from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.deviceId is not null")
    long countDistinctByEmailOrPhoneAndDeviceIdIsNotNull(@Param("emailOrPhone") String emailOrPhone);


    // ============================================================
    // 🔵 11. אנליזה מתקדמת — Risk Engine (תשתית)
    // ============================================================

    long countByIpAddressAndDeviceIdAndSuccessFalseAndAttemptTimeAfter(
            String ipAddress,
            String deviceId,
            LocalDateTime since
    );

    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.userAgent = :userAgent " +
            "and la.attemptTime > :since")
    long countByEmailOrPhoneAndUserAgentAndAttemptTimeAfter(
            @Param("emailOrPhone") String emailOrPhone,
            @Param("userAgent") String userAgent,
            @Param("since") LocalDateTime since
    );


    // ============================================================
    // 🧱 תשתית קדימה (לא שובר תלויים): Paging/Monitoring
    // ============================================================

    /**
     * Paging של ניסיונות לפי חשבון (ל-UI/אדמין/חקירה).
     */
    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "order by la.attemptTime desc")
    Page<LoginAttempt> findByEmailOrPhoneOrderByAttemptTimeDesc(
            @Param("emailOrPhone") String emailOrPhone,
            Pageable pageable
    );

    /**
     * כמות כישלונות אחרונים עבור חשבון + IP (עוזר ל-Rules/Anomaly).
     */
    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :emailOrPhone " +
            "and la.ipAddress = :ipAddress " +
            "and la.success = false " +
            "and la.attemptTime > :since")
    long countFailedByEmailOrPhoneAndIpSince(
            @Param("emailOrPhone") String emailOrPhone,
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );
}