package com.example.myproject.repository;

import com.example.myproject.model.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, Long> {

    Optional<NotificationPreferences> findByUser_Id(Long userId);
    boolean existsByUser_Id(Long userId);
    void deleteByUser_Id(Long userId);
    List<NotificationPreferences> findByUser_IdIn(List<Long> userIds);

    List<NotificationPreferences> findByMuteAllTrue();
    List<NotificationPreferences> findByMuteUntilAfter(LocalDateTime now);
    List<NotificationPreferences> findByMuteAllTrueOrMuteUntilAfter(LocalDateTime now);

    List<NotificationPreferences> findByAlwaysShowMatchTrue();
    List<NotificationPreferences> findByAlwaysShowSuperLikeTrue();

    List<NotificationPreferences> findByUpdatedAtBefore(LocalDateTime time);

    List<NotificationPreferences> findByCustomPreferencesJsonIsNotNull();

    long countByMuteAllTrue();
    long countByMuteAllTrueOrMuteUntilAfter(LocalDateTime now);

    // ✅ ערוצים / quiet hours / throttle / rate caps
    List<NotificationPreferences> findByEnablePushTrue();
    List<NotificationPreferences> findByEnableEmailTrue();
    List<NotificationPreferences> findByEnableInAppTrue();

    List<NotificationPreferences> findByQuietHoursEnabledTrue();

    List<NotificationPreferences> findByThrottledTrue();
    List<NotificationPreferences> findByThrottleUntilAfter(LocalDateTime now);

    List<NotificationPreferences> findByMaxNotificationsPerHourIsNotNull();
    List<NotificationPreferences> findByMaxNotificationsPerDayIsNotNull();

    long countByEnablePushTrue();
    long countByEnableEmailTrue();
    long countByEnableInAppTrue();
    long countByQuietHoursEnabledTrue();
    long countByThrottledTrue();
}