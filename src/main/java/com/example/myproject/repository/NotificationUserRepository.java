package com.example.myproject.repository;

import com.example.myproject.model.NotificationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationUserRepository extends JpaRepository<NotificationUser, Long> {

    List<NotificationUser> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findByUser_IdAndDeletedFalseOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findByUser_IdAndHiddenFalseOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndReadFalseOrderByCreatedAtDesc(Long userId);
    long countByUser_IdAndReadFalse(Long userId);

    List<NotificationUser> findByUser_IdAndPopupSeenFalseOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findByUser_IdAndPinnedTrueOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findByUser_IdAndSnoozedTrueOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndDeletedTrue(Long userId);
    List<NotificationUser> findByUser_IdAndHiddenTrue(Long userId);

    Optional<NotificationUser> findByUser_IdAndNotification_Id(Long userId, Long notificationId);
    List<NotificationUser> findByNotification_Id(Long notificationId);
    long countByNotification_Id(Long notificationId);

    List<NotificationUser> findByUser_IdAndReadFalseAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end
    );

    long countByUser_IdAndReadFalseAndCreatedAtAfter(Long userId, LocalDateTime since);
    long countByUser_IdAndDeletedFalseAndCreatedAtAfter(Long userId, LocalDateTime since);

    List<NotificationUser> findByUser_IdAndDeletedFalseAndReadFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findByUser_IdAndPopupSeenFalse(Long userId);

    List<NotificationUser> findByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByUser_IdAndReadAtAfter(Long userId, LocalDateTime since);
    List<NotificationUser> findByUser_IdAndSnoozedTrueAndCreatedAtAfter(Long userId, LocalDateTime since);

    long countByUser_Id(Long userId);
    long countByDeletedTrue();
    long countByHiddenTrue();
    long countByPinnedTrue();
    long countBySnoozedTrue();
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<NotificationUser> findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, LocalDateTime weddingStart, LocalDateTime weddingEnd
    );
    List<NotificationUser> findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, LocalDateTime weddingEnd
    );

    List<NotificationUser> findByUser_IdAndPopupSeenFalseAndCreatedAtBetween(
            Long userId, LocalDateTime start, LocalDateTime end
    );

    List<NotificationUser> findByCreatedAtBefore(LocalDateTime time);
    List<NotificationUser> findByDeletedTrueAndCreatedAtBefore(LocalDateTime time);

    @Query("""
           SELECT nu
           FROM NotificationUser nu
           WHERE nu.user.id = :userId
             AND nu.deleted = false
             AND nu.hidden = false
             AND (nu.pinned = true OR nu.read = false)
           ORDER BY nu.createdAt DESC
           """)
    List<NotificationUser> findLockedModeVisibleNotifications(@Param("userId") Long userId);

    List<NotificationUser> findByNotification_CategoryAndUser_IdOrderByCreatedAtDesc(String category, Long userId);

    List<NotificationUser> findByUser_IdAndDeletedFalseAndHiddenFalseAndNotification_PriorityLevelGreaterThanEqualOrderByCreatedAtDesc(
            Long userId, int minPriority
    );

    @Query("""
           SELECT nu
           FROM NotificationUser nu
           WHERE nu.user.id = :userId
             AND nu.deleted = false
             AND nu.hidden = false
             AND (nu.pinned = true OR nu.notification.priorityLevel >= :minPriority)
           ORDER BY nu.createdAt DESC
           """)
    List<NotificationUser> findImportantNotificationsForUser(@Param("userId") Long userId, @Param("minPriority") int minPriority);

    List<NotificationUser> findByIdIn(List<Long> ids);
    long countByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalse(Long userId);

    List<NotificationUser> findTop50ByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findTop50ByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalseOrderByCreatedAtDesc(Long userId);

    List<NotificationUser> findByPopupSeenFalseAndDeletedFalseAndHiddenFalse();

    // ============================================================
    // ✅ delivered: שים לב ל-NULLים מרשומות ישנות
    // ============================================================

    // זה מחזיר רק delivered=false (לא כולל NULL)
    List<NotificationUser> findByUser_IdAndDeliveredFalseAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(Long userId);
    List<NotificationUser> findByDeliveredFalseAndDeletedFalseAndHiddenFalse();
    long countByUser_IdAndDeliveredFalse(Long userId);

    // ✅ Worker אמיתי: NULL נחשב כ-false
    @Query("""
           SELECT nu
           FROM NotificationUser nu
           WHERE nu.deleted = false
             AND nu.hidden = false
             AND (nu.delivered IS NULL OR nu.delivered = false)
           ORDER BY nu.createdAt ASC
           """)
    List<NotificationUser> findPendingDeliveryGlobal();

    @Query("""
           SELECT nu
           FROM NotificationUser nu
           WHERE nu.user.id = :userId
             AND nu.deleted = false
             AND nu.hidden = false
             AND (nu.delivered IS NULL OR nu.delivered = false)
           ORDER BY nu.createdAt ASC
           """)
    List<NotificationUser> findPendingDeliveryForUser(@Param("userId") Long userId);
}