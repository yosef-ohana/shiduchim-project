// =====================================================
// ✅ LoginAttemptRepository (MASTER 2025 - FINAL OPTIMAL)
// Fixes:
// - Adds Capability #20 query: countFailuresByIpBetween
// - Keeps @Query-based methods to avoid "EmailOrPhone" parsing issues
// =====================================================
package com.example.myproject.repository;

import com.example.myproject.model.LoginAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    // =========================================================
    // Latest attempt by identifier
    // =========================================================
    @Query("select la from LoginAttempt la where la.emailOrPhone = :id order by la.attemptTime desc")
    List<LoginAttempt> findLatestAttemptList(@Param("id") String id);

    default Optional<LoginAttempt> findLatestAttempt(String id) {
        List<LoginAttempt> list = findLatestAttemptList(id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // Latest SUCCESS attempt by identifier (for IP-change detection)
    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.success = true " +
            "order by la.attemptTime desc")
    List<LoginAttempt> findLatestSuccessAttemptList(@Param("id") String id);

    default Optional<LoginAttempt> findLatestSuccessAttempt(String id) {
        List<LoginAttempt> list = findLatestSuccessAttemptList(id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // Latest attempt by deviceId (for device+ip anomaly)
    @Query("select la from LoginAttempt la where la.deviceId = :deviceId order by la.attemptTime desc")
    List<LoginAttempt> findLatestAttemptByDeviceList(@Param("deviceId") String deviceId);

    default Optional<LoginAttempt> findLatestAttemptByDevice(String deviceId) {
        List<LoginAttempt> list = findLatestAttemptByDeviceList(deviceId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // =========================================================
    // Failures counters
    // =========================================================

    // Failures by identifier since
    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.success = false and la.attemptTime >= :since")
    long countFailuresSince(@Param("id") String id, @Param("since") LocalDateTime since);

    // Failures by IP since
    @Query("select count(la) from LoginAttempt la " +
            "where la.ipAddress = :ip and la.success = false and la.attemptTime >= :since")
    long countFailuresByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since);

    // OTP failures (best-effort): requiresOtp=true + success=false
    @Query("select count(la) from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.requiresOtp = true and la.success = false and la.attemptTime >= :since")
    long countOtpFailuresSince(@Param("id") String id, @Param("since") LocalDateTime since);

    @Query("select count(la) from LoginAttempt la " +
            "where la.ipAddress = :ip and la.requiresOtp = true and la.success = false and la.attemptTime >= :since")
    long countOtpFailuresByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since);

    // =========================================================
    // Device intelligence
    // =========================================================
    @Query("select count(distinct la.deviceId) from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.deviceId is not null and la.attemptTime >= :since")
    long countDistinctDevicesByIdentifierSince(@Param("id") String id, @Param("since") LocalDateTime since);

    @Query("select count(distinct la.emailOrPhone) from LoginAttempt la " +
            "where la.deviceId = :deviceId and la.emailOrPhone is not null and la.attemptTime >= :since")
    long countDistinctIdentifiersByDeviceSince(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    @Query("select count(la) > 0 from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.deviceId = :deviceId")
    boolean existsKnownDevice(@Param("id") String id, @Param("deviceId") String deviceId);

    @Query("select count(la) > 0 from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.userAgent = :ua")
    boolean existsKnownUserAgent(@Param("id") String id, @Param("ua") String userAgent);

    @Query("select count(la) > 0 from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.ipAddress = :ip")
    boolean existsKnownIp(@Param("id") String id, @Param("ip") String ip);

    // Distinct IPs per identifier in window (for takeover suspicion)
    @Query("select count(distinct la.ipAddress) from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.ipAddress is not null and la.attemptTime >= :since")
    long countDistinctIpsByIdentifierSince(@Param("id") String id, @Param("since") LocalDateTime since);

    // =========================================================
    // Admin queries
    // =========================================================
    @Query("select la from LoginAttempt la " +
            "where la.ipAddress = :ip and la.attemptTime between :from and :to order by la.attemptTime desc")
    List<LoginAttempt> findByIpBetween(@Param("ip") String ip,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("select la from LoginAttempt la " +
            "where la.emailOrPhone = :id and la.attemptTime between :from and :to order by la.attemptTime desc")
    List<LoginAttempt> findByIdentifierBetween(@Param("id") String id,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    long countByAttemptTimeBetweenAndSuccess(LocalDateTime from, LocalDateTime to, boolean success);

    // ✅ Capability #20: failures for specific IP in range (dashboard)
    @Query("select count(la) from LoginAttempt la " +
            "where la.ipAddress = :ip and la.success = false and la.attemptTime between :from and :to")
    long countFailuresByIpBetween(@Param("ip") String ip,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    // Top attackers by IP
    @Query("select la.ipAddress, count(la) from LoginAttempt la " +
            "where la.ipAddress is not null and la.attemptTime between :from and :to and la.success = false " +
            "group by la.ipAddress order by count(la) desc")
    List<Object[]> topIpsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Top attackers by deviceId
    @Query("select la.deviceId, count(la) from LoginAttempt la " +
            "where la.deviceId is not null and la.attemptTime between :from and :to and la.success = false " +
            "group by la.deviceId order by count(la) desc")
    List<Object[]> topDevicesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // =========================================================
    // Cleanup
    // =========================================================
    @Modifying
    @Query("delete from LoginAttempt la where la.expiresAt is not null and la.expiresAt < :now")
    long deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from LoginAttempt la where la.attemptTime < :cutoff")
    long deleteByAttemptTimeBefore(@Param("cutoff") LocalDateTime cutoff);
}