package com.example.myproject.repository;

import com.example.myproject.model.Notification;
import com.example.myproject.model.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type);
    List<Notification> findByCategoryOrderByCreatedAtDesc(String category);
    List<Notification> findBySourceOrderByCreatedAtDesc(String source);
    List<Notification> findBySourceInOrderByCreatedAtDesc(List<String> sources);

    List<Notification> findByCategoryAndSourceOrderByCreatedAtDesc(String category, String source);
    List<Notification> findByCategoryAndPriorityLevelGreaterThanEqualOrderByCreatedAtDesc(String category, int minPriority);

    List<Notification> findByRelatedUserIdOrderByCreatedAtDesc(Long relatedUserId);
    List<Notification> findByRelatedUserIdAndTypeOrderByCreatedAtDesc(Long relatedUserId, NotificationType type);

    List<Notification> findByRelatedUserIdAndSourceOrderByCreatedAtDesc(Long relatedUserId, String source);
    List<Notification> findByRelatedUserIdAndPriorityLevelGreaterThanEqualOrderByCreatedAtDesc(Long relatedUserId, int minPriority);

    List<Notification> findByWeddingIdOrderByCreatedAtDesc(Long weddingId);
    List<Notification> findByWeddingIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long weddingId, LocalDateTime start, LocalDateTime end);

    List<Notification> findByMatchIdOrderByCreatedAtDesc(Long matchId);
    List<Notification> findByChatMessageIdOrderByCreatedAtDesc(Long chatMessageId);

    List<Notification> findByWeddingIdAndTypeOrderByCreatedAtDesc(Long weddingId, NotificationType type);

    List<Notification> findByCreatedAtBefore(LocalDateTime time);
    List<Notification> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<Notification> findByPriorityLevelGreaterThanEqualOrderByCreatedAtDesc(int minPriority);
    List<Notification> findByTypeAndCreatedAtBetweenOrderByCreatedAtDesc(NotificationType type, LocalDateTime start, LocalDateTime end);
    List<Notification> findByWeddingIdAndTypeAndCreatedAtBetweenOrderByCreatedAtDesc(Long weddingId, NotificationType type, LocalDateTime start, LocalDateTime end);

    long countByType(NotificationType type);
    long countByCategory(String category);
    long countBySource(String source);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByWeddingId(Long weddingId);
    long countByMatchId(Long matchId);

    long countByWeddingIdAndType(Long weddingId, NotificationType type);

    // ✅ תוספות “נחמדות” למסכים (לא חובה, אבל שימושי)
    List<Notification> findByTypeInOrderByCreatedAtDesc(List<NotificationType> types);
    List<Notification> findByCategoryInOrderByCreatedAtDesc(List<String> categories);
}