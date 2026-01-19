package com.example.myproject.service.notification;

import com.example.myproject.model.Notification;
import com.example.myproject.model.NotificationUser;
import com.example.myproject.model.User;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.repository.NotificationRepository;
import com.example.myproject.repository.NotificationUserRepository;
import com.example.myproject.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;


import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationUserService {

    private final NotificationUserRepository notificationUserRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

    public NotificationUserService(NotificationUserRepository notificationUserRepository,
                                   NotificationRepository notificationRepository,
                                   UserRepository userRepository) {
        this.notificationUserRepository = notificationUserRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // =====================================================
    // ✅ Weekly Digest (MASTER decision: INNER DTO only)
    // =====================================================
    public static class WeeklyDigestDto {
        private final LocalDateTime since;
        private final LocalDateTime until;
        private final long total;
        private final Map<NotificationType, Long> byType;

        public WeeklyDigestDto(LocalDateTime since,
                               LocalDateTime until,
                               long total,
                               Map<NotificationType, Long> byType) {
            this.since = since;
            this.until = until;
            this.total = total;
            this.byType = byType;
        }

        public LocalDateTime getSince() { return since; }
        public LocalDateTime getUntil() { return until; }
        public long getTotal() { return total; }
        public Map<NotificationType, Long> getByType() { return byType; }
    }


    // =========================================================
    // DTO — מסך Notification Center/Badge/Important Only
    // =========================================================
    public static class NotificationUserItemDto {
        public Long notificationUserId;
        public Long notificationId;

        public NotificationType type;
        public String title;
        public String message;
        public String metadata;

        public String category;
        public String source;
        public int priorityLevel;

        public Long relatedUserId;
        public Long weddingId;
        public Long matchId;
        public Long chatMessageId;

        public boolean read;
        public LocalDateTime readAt;

        public boolean popupSeen;

        public boolean deleted;
        public boolean pinned;
        public boolean snoozed;
        public boolean hidden;

        public Boolean delivered;
        public LocalDateTime deliveredAt;

        public LocalDateTime createdAt;

        public static NotificationUserItemDto from(NotificationUser nu) {
            Notification n = nu.getNotification();

            NotificationUserItemDto dto = new NotificationUserItemDto();
            dto.notificationUserId = nu.getId();
            dto.notificationId = (n != null ? n.getId() : null);

            dto.type = (n != null ? n.getType() : null);
            dto.title = (n != null ? n.getTitle() : null);
            dto.message = (n != null ? n.getMessage() : null);
            dto.metadata = (n != null ? n.getMetadata() : null);

            dto.category = (n != null ? n.getCategory() : null);
            dto.source = (n != null ? n.getSource() : null);
            dto.priorityLevel = (n != null ? n.getPriorityLevel() : 1);

            dto.relatedUserId = (n != null ? n.getRelatedUserId() : null);
            dto.weddingId = (n != null ? n.getWeddingId() : null);
            dto.matchId = (n != null ? n.getMatchId() : null);
            dto.chatMessageId = (n != null ? n.getChatMessageId() : null);

            dto.read = nu.isRead();
            dto.readAt = nu.getReadAt();
            dto.popupSeen = nu.isPopupSeen();

            dto.deleted = nu.isDeleted();
            dto.pinned = nu.isPinned();
            dto.snoozed = nu.isSnoozed();
            dto.hidden = nu.isHidden();

            dto.delivered = nu.getDelivered();
            dto.deliveredAt = nu.getDeliveredAt();

            dto.createdAt = nu.getCreatedAt();
            return dto;
        }
    }

    // =========================================================
    // Guards / Loaders
    // =========================================================
    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private Notification requireNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));
    }

    private NotificationUser requireNotificationUser(Long notificationUserId) {
        return notificationUserRepository.findById(notificationUserId)
                .orElseThrow(() -> new EntityNotFoundException("NotificationUser not found: " + notificationUserId));
    }

    /**
     * אבטחה לוגית: אסור לאפשר פעולה על NU שלא שייך למשתמש.
     * בכוונה EntityNotFound כדי לא לחשוף IDs.
     */
    private NotificationUser requireOwned(Long actorUserId, Long notificationUserId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
        NotificationUser nu = requireNotificationUser(notificationUserId);

        if (nu.getUser() == null || nu.getUser().getId() == null || !actorUserId.equals(nu.getUser().getId())) {
            throw new EntityNotFoundException("NotificationUser not found: " + notificationUserId);
        }
        return nu;
    }

    // =========================================================
    // Sorting helper: pinned DESC, createdAt DESC
    // =========================================================
    private Comparator<NotificationUser> pinnedFirstNewestFirst() {
        return Comparator
                .comparing(NotificationUser::isPinned).reversed()
                .thenComparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private void initDefaults(NotificationUser nu) {
        // סטטוסים התחלתיים לפי החוקים (ברירת מחדל)
        nu.setRead(false);
        nu.setReadAt(null);
        nu.setHidden(false);
        nu.setDeleted(false);
        nu.setPopupSeen(false);
        nu.setPinned(false);
        nu.setSnoozed(false);

        if (nu.getDelivered() == null) nu.setDelivered(false);
        if (Boolean.FALSE.equals(nu.getDelivered())) nu.setDeliveredAt(null);
    }

    // =========================================================
    // 1) יצירה וניהול NotificationUser (fan-out)
    // =========================================================

    /**
     * Upsert: אם כבר קיים (userId, notificationId) — מחזיר את הקיים.
     */
    public NotificationUser createForUserIfMissing(Long notificationId, Long userId) {
        if (notificationId == null) throw new IllegalArgumentException("notificationId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        requireUser(userId);
        requireNotification(notificationId);

        return notificationUserRepository.findByUser_IdAndNotification_Id(userId, notificationId)
                .orElseGet(() -> {
                    Notification n = requireNotification(notificationId);
                    User u = requireUser(userId);

                    NotificationUser nu = new NotificationUser(n, u);
                    initDefaults(nu);
                    return notificationUserRepository.save(nu);
                });
    }

    /**
     * Fan-out לקבוצת משתמשים: מייצר NU לכל userId שלא קיים (בבת אחת, בלי N+1 שאילתות).
     */
    public List<NotificationUser> createForUsersIfMissing(Long notificationId, List<Long> userIds) {
        if (notificationId == null) throw new IllegalArgumentException("notificationId is required");
        if (userIds == null || userIds.isEmpty()) return List.of();

        // מנקים כפילויות
        List<Long> uniqueUserIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueUserIds.isEmpty()) return List.of();

        Notification n = requireNotification(notificationId);

        // טוענים משתמשים קיימים
        Map<Long, User> users = userRepository.findAllById(uniqueUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // מביאים קיימים בבת אחת כדי לא לעשות isPresent() לכל אחד
        Set<Long> existingUserIds = new HashSet<>(
                em.createQuery("""
                        select nu.user.id
                        from NotificationUser nu
                        where nu.notification.id = :nid
                          and nu.user.id in :uids
                        """, Long.class)
                        .setParameter("nid", notificationId)
                        .setParameter("uids", uniqueUserIds)
                        .getResultList()
        );

        List<NotificationUser> toSave = new ArrayList<>();

        for (Long userId : uniqueUserIds) {
            User u = users.get(userId);
            if (u == null) continue; // דילוג – לא שוברים fan-out על משתמש לא קיים

            if (existingUserIds.contains(userId)) continue;

            NotificationUser nu = new NotificationUser(n, u);
            initDefaults(nu);
            toSave.add(nu);
        }

        if (toSave.isEmpty()) return List.of();
        return notificationUserRepository.saveAll(toSave);
    }

    // =========================================================
    // 2) מרכז התראות — Queries בסיס
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getAllForUser(Long userId) {
        return notificationUserRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getActiveCenterForUser(Long userId) {
        return notificationUserRepository.findByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(userId)
                .stream()
                .sorted(pinnedFirstNewestFirst())
                .map(NotificationUserItemDto::from)
                .toList();
    }

    // =========================================================
    // ✅ Paging wrappers (Notification Center)
    // =========================================================

    @Transactional(readOnly = true)
    public Page<NotificationUserItemDto> getAllForUser(Long userId, Pageable pageable) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (pageable == null) pageable = PageRequest.of(0, 50);

        // ✅ מרכז התראות אמיתי (deleted=false, hidden=false + pinned-first)
        List<NotificationUserItemDto> all = getActiveCenterForUser(userId);

        int total = (all == null) ? 0 : all.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        List<NotificationUserItemDto> content =
                (total == 0 || start >= total) ? List.of() : all.subList(start, end);

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public Page<NotificationUserItemDto> getSinceForUser(Long userId, LocalDateTime since, Pageable pageable) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (since == null) since = LocalDateTime.now().minusDays(7);
        if (pageable == null) pageable = PageRequest.of(0, 50);

        // ב-ZIP הקיים יש List לפי createdAtAfter בלי deleted/hidden,
        // אז מסננים פה כדי לשמור SSOT התנהגותי (מרכז התראות = לא מחוקים/לא מוסתרים).
        List<NotificationUser> rows =
                notificationUserRepository.findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since);

        List<NotificationUserItemDto> all = (rows == null ? List.<NotificationUserItemDto>of() :
                rows.stream()
                        .filter(nu -> nu != null && !nu.isDeleted() && !nu.isHidden())
                        .sorted(pinnedFirstNewestFirst())
                        .map(NotificationUserItemDto::from)
                        .toList()
        );

        int total = all.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        List<NotificationUserItemDto> content =
                (total == 0 || start >= total) ? List.of() : all.subList(start, end);

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public WeeklyDigestDto buildWeeklyDigest(Long userId, LocalDateTime since) {
        if (userId == null) {
            return new WeeklyDigestDto(
                    null,
                    LocalDateTime.now(),
                    0,
                    new EnumMap<>(NotificationType.class)
            );
        }
        if (since == null) since = LocalDateTime.now().minusDays(7);

        // משתמשים במתודה שקיימת ב-ZIP, ומסננים deleted/hidden בשירות
        List<NotificationUser> rows =
                notificationUserRepository.findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since);

        Map<NotificationType, Long> map = new EnumMap<>(NotificationType.class);
        long total = 0;

        if (rows != null) {
            for (NotificationUser nu : rows) {
                if (nu == null || nu.isDeleted() || nu.isHidden()) continue;
                if (nu.getNotification() == null) continue;

                NotificationType t = nu.getNotification().getType();
                if (t == null) continue;

                map.put(t, map.getOrDefault(t, 0L) + 1L);
                total++;
            }
        }

        return new WeeklyDigestDto(since, LocalDateTime.now(), total, map);
    }


    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getUnreadForUser(Long userId) {
        return notificationUserRepository.findByUser_IdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long countUnreadTotal(Long userId) {
        return notificationUserRepository.countByUser_IdAndReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public long countUnreadVisible(Long userId) {
        return notificationUserRepository.countByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getTop50Active(Long userId) {
        return notificationUserRepository.findTop50ByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(userId)
                .stream()
                .sorted(pinnedFirstNewestFirst())
                .map(NotificationUserItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getTop50UnreadActive(Long userId) {
        return notificationUserRepository.findTop50ByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .sorted(pinnedFirstNewestFirst())
                .map(NotificationUserItemDto::from)
                .toList();
    }

    // =========================================================
    // 3) Locked Mode / Important Only
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getLockedModeVisible(Long userId) {
        return notificationUserRepository.findLockedModeVisibleNotifications(userId)
                .stream()
                .sorted(pinnedFirstNewestFirst())
                .map(NotificationUserItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getHighPriorityForUser(Long userId, int minPriority) {
        return notificationUserRepository
                .findByUser_IdAndDeletedFalseAndHiddenFalseAndNotification_PriorityLevelGreaterThanEqualOrderByCreatedAtDesc(userId, minPriority)
                .stream()
                .sorted(pinnedFirstNewestFirst())
                .map(NotificationUserItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getImportantOnlyForUser(Long userId, int minPriority) {
        return notificationUserRepository.findImportantNotificationsForUser(userId, minPriority)
                .stream()
                .sorted(pinnedFirstNewestFirst())
                .map(NotificationUserItemDto::from)
                .toList();
    }

    // =========================================================
    // 4) פעולות על NotificationUser — SINGLE
    // =========================================================

    public NotificationUser markRead(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setRead(true);
        if (nu.getReadAt() == null) nu.setReadAt(LocalDateTime.now());
        return notificationUserRepository.save(nu);
    }

    public NotificationUser markUnread(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setRead(false);
        nu.setReadAt(null);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser markPopupSeen(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setPopupSeen(true);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser pin(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setPinned(true);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser unpin(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setPinned(false);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser snooze(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setSnoozed(true);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser unsnooze(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setSnoozed(false);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser hide(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setHidden(true);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser unhide(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setHidden(false);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser softDelete(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setDeleted(true);
        return notificationUserRepository.save(nu);
    }

    public NotificationUser restore(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireOwned(actorUserId, notificationUserId);
        nu.setDeleted(false);
        return notificationUserRepository.save(nu);
    }

    // =========================================================
    // 5) Batch operations — לפי findByIdIn
    // =========================================================

    @FunctionalInterface
    private interface NuMutator {
        void mutate(NotificationUser nu);
    }

    private List<NotificationUser> applyBatchOwned(Long actorUserId,
                                                   List<Long> notificationUserIds,
                                                   NuMutator mutator) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
        if (notificationUserIds == null || notificationUserIds.isEmpty()) return List.of();

        List<Long> ids = notificationUserIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();

        List<NotificationUser> nus = notificationUserRepository.findByIdIn(ids);

        List<NotificationUser> owned = nus.stream()
                .filter(nu -> nu.getUser() != null && actorUserId.equals(nu.getUser().getId()))
                .collect(Collectors.toList());

        for (NotificationUser nu : owned) {
            mutator.mutate(nu);
        }

        if (owned.isEmpty()) return List.of();
        return notificationUserRepository.saveAll(owned);
    }

    public List<NotificationUser> markReadBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> {
            nu.setRead(true);
            if (nu.getReadAt() == null) nu.setReadAt(LocalDateTime.now());
        });
    }

    public List<NotificationUser> markPopupSeenBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setPopupSeen(true));
    }

    public List<NotificationUser> pinBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setPinned(true));
    }

    public List<NotificationUser> unpinBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setPinned(false));
    }

    public List<NotificationUser> snoozeBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setSnoozed(true));
    }

    public List<NotificationUser> unsnoozeBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setSnoozed(false));
    }

    public List<NotificationUser> hideBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setHidden(true));
    }

    public List<NotificationUser> unhideBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setHidden(false));
    }

    public List<NotificationUser> softDeleteBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setDeleted(true));
    }

    public List<NotificationUser> restoreBatch(Long actorUserId, List<Long> notificationUserIds) {
        return applyBatchOwned(actorUserId, notificationUserIds, nu -> nu.setDeleted(false));
    }

    // =========================================================
    // 6) Filters לפי זמן (Notification Center)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getUnreadBetween(Long userId, LocalDateTime start, LocalDateTime end) {
        return notificationUserRepository
                .findByUser_IdAndReadFalseAndCreatedAtBetweenOrderByCreatedAtDesc(userId, start, end)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long countUnreadSince(Long userId, LocalDateTime since) {
        return notificationUserRepository.countByUser_IdAndReadFalseAndCreatedAtAfter(userId, since);
    }

    @Transactional(readOnly = true)
    public long countAnyNotDeletedSince(Long userId, LocalDateTime since) {
        return notificationUserRepository.countByUser_IdAndDeletedFalseAndCreatedAtAfter(userId, since);
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> findReadAfter(Long userId, LocalDateTime since) {
        return notificationUserRepository.findByUser_IdAndReadAtAfter(userId, since);
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> findSnoozedAfter(Long userId, LocalDateTime since) {
        return notificationUserRepository.findByUser_IdAndSnoozedTrueAndCreatedAtAfter(userId, since);
    }

    // =========================================================
    // 7) חתונה / אחרי חתונה (WeddingMode / GlobalMode)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getForWeddingWindow(Long userId, LocalDateTime weddingStart, LocalDateTime weddingEnd) {
        return notificationUserRepository
                .findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, weddingStart, weddingEnd)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getAfterWeddingEnd(Long userId, LocalDateTime weddingEnd) {
        return notificationUserRepository
                .findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(userId, weddingEnd)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    // =========================================================
    // 8) Aggregation helpers (קיבוץ)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getPopupNotSeenBetween(Long userId, LocalDateTime start, LocalDateTime end) {
        return notificationUserRepository
                .findByUser_IdAndPopupSeenFalseAndCreatedAtBetween(userId, start, end)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    // =========================================================
    // 9) Category / Advanced
    // =========================================================
    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getByCategoryForUser(Long userId, String category) {
        return notificationUserRepository
                .findByNotification_CategoryAndUser_IdOrderByCreatedAtDesc(category, userId)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    // =========================================================
    // 10) קשר Notification ↔ Users (סטטיסטיקה/בדיקות)
    // =========================================================

    @Transactional(readOnly = true)
    public NotificationUserItemDto getCopyForUserAndNotification(Long userId, Long notificationId) {
        return notificationUserRepository.findByUser_IdAndNotification_Id(userId, notificationId)
                .map(NotificationUserItemDto::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> getAllCopiesForNotification(Long notificationId) {
        return notificationUserRepository.findByNotification_Id(notificationId);
    }

    @Transactional(readOnly = true)
    public long countCopiesForNotification(Long notificationId) {
        return notificationUserRepository.countByNotification_Id(notificationId);
    }

    // =========================================================
    // 11) POPUP queues + Delivery queues (Worker/WebSocket/Push)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUserItemDto> getPopupQueueForUser(Long userId) {
        return notificationUserRepository.findByUser_IdAndPopupSeenFalse(userId)
                .stream().map(NotificationUserItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> getPopupQueueGlobal() {
        return notificationUserRepository.findByPopupSeenFalseAndDeletedFalseAndHiddenFalse();
    }

    /**
     * Pending delivery (NULL-safe) — בלי תלות במתודות ריפו מיוחדות.
     * מחזיר: לא delivered או delivered=null, ולא מחוק/מוסתר.
     */
    @Transactional(readOnly = true)
    public List<NotificationUser> getPendingDeliveryGlobal(int limit) {
        if (limit <= 0) limit = 200;

        return em.createQuery("""
                select nu
                from NotificationUser nu
                where (nu.delivered is null or nu.delivered = false)
                  and nu.deleted = false
                  and nu.hidden = false
                order by nu.createdAt asc
                """, NotificationUser.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> getPendingDeliveryForUser(Long userId, int limit) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (limit <= 0) limit = 200;

        return em.createQuery("""
                select nu
                from NotificationUser nu
                where nu.user.id = :uid
                  and (nu.delivered is null or nu.delivered = false)
                  and nu.deleted = false
                  and nu.hidden = false
                order by nu.createdAt asc
                """, NotificationUser.class)
                .setParameter("uid", userId)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<NotificationUser> markDeliveredBatch(List<Long> notificationUserIds) {
        if (notificationUserIds == null || notificationUserIds.isEmpty()) return List.of();

        List<Long> ids = notificationUserIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();

        List<NotificationUser> nus = notificationUserRepository.findByIdIn(ids);
        LocalDateTime now = LocalDateTime.now();

        for (NotificationUser nu : nus) {
            nu.setDelivered(true);
            if (nu.getDeliveredAt() == null) nu.setDeliveredAt(now);
        }

        return notificationUserRepository.saveAll(nus);
    }

    // =========================================================
    // 12) AutoCleanup (תחזוקה)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUser> findOlderThan(LocalDateTime time) {
        return notificationUserRepository.findByCreatedAtBefore(time);
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> findDeletedOlderThan(LocalDateTime time) {
        return notificationUserRepository.findByDeletedTrueAndCreatedAtBefore(time);
    }

    public int hardDeleteDeletedOlderThan(LocalDateTime time) {
        List<NotificationUser> toDelete = findDeletedOlderThan(time);
        if (toDelete.isEmpty()) return 0;
        notificationUserRepository.deleteAll(toDelete);
        return toDelete.size();
    }

    // =========================================================
    // 13) Admin / Dashboard stats
    // =========================================================

    @Transactional(readOnly = true)
    public long countAllForUser(Long userId) {
        return notificationUserRepository.countByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public long countDeletedGlobal() {
        return notificationUserRepository.countByDeletedTrue();
    }

    @Transactional(readOnly = true)
    public long countHiddenGlobal() {
        return notificationUserRepository.countByHiddenTrue();
    }

    @Transactional(readOnly = true)
    public long countPinnedGlobal() {
        return notificationUserRepository.countByPinnedTrue();
    }

    @Transactional(readOnly = true)
    public long countSnoozedGlobal() {
        return notificationUserRepository.countBySnoozedTrue();
    }

    @Transactional(readOnly = true)
    public long countCreatedBetween(LocalDateTime start, LocalDateTime end) {
        return notificationUserRepository.countByCreatedAtBetween(start, end);
    }
}