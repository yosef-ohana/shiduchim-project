package com.example.myproject.service.notification;

import com.example.myproject.model.Notification;
import com.example.myproject.model.NotificationPreferences;
import com.example.myproject.model.NotificationUser;
import com.example.myproject.model.User;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.repository.NotificationPreferencesRepository;
import com.example.myproject.repository.NotificationRepository;
import com.example.myproject.repository.NotificationUserRepository;
import com.example.myproject.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationUserRepository notificationUserRepository;
    private final NotificationPreferencesRepository notificationPreferencesRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationUserRepository notificationUserRepository,
                               NotificationPreferencesRepository notificationPreferencesRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationUserRepository = notificationUserRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // DTOs
    // =========================================================

    public static class NotificationCenterItemDto {
        public Long notificationUserId;
        public Long notificationId;

        public NotificationType type;
        public String title;
        public String message;
        public String metadata;

        public String category;
        public String source;
        public int priorityLevel;

        public Long relatedUserId;   // actor
        public Long weddingId;
        public Long matchId;
        public Long chatMessageId;

        public LocalDateTime createdAt;

        public boolean read;
        public LocalDateTime readAt;
        public boolean popupSeen;
        public boolean deleted;
        public boolean pinned;
        public boolean snoozed;
        public boolean hidden;

        // note: לא מוסיפים שדות חדשים ל-DTO כדי לא לשבור Controllers/Frontend
        public static NotificationCenterItemDto from(NotificationUser nu) {
            Notification n = (nu != null ? nu.getNotification() : null);

            NotificationCenterItemDto dto = new NotificationCenterItemDto();
            dto.notificationUserId = (nu != null ? nu.getId() : null);
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

            dto.createdAt = (nu != null ? nu.getCreatedAt() : null);

            dto.read = (nu != null && nu.isRead());
            dto.readAt = (nu != null ? nu.getReadAt() : null);
            dto.popupSeen = (nu != null && nu.isPopupSeen());
            dto.deleted = (nu != null && nu.isDeleted());
            dto.pinned = (nu != null && nu.isPinned());
            dto.snoozed = (nu != null && nu.isSnoozed());
            dto.hidden = (nu != null && nu.isHidden());

            return dto;
        }
    }

    public static class CreateNotificationRequest {
        public Long recipientUserId;

        public NotificationType type;
        public String title;
        public String message;

        public Long actorUserId; // Notification.relatedUserId
        public Long weddingId;
        public Long matchId;
        public Long chatMessageId;

        public String metadataJson;
        public String category;
        public String source;
        public int priorityLevel = 1;

        /** אם false -> לא יופיע בתור popups (אבל עדיין יופיע במרכז התראות) */
        public boolean popupEligible = true;

        /** אם true -> נאלץ יצירה גם אם יש מגבלות (רק לשימוש פנימי במערכת) */
        public boolean force = false;
    }

    // =========================================================
    // Loaders + Ownership Guard (אבטחה לוגית)
    // =========================================================

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private NotificationUser requireNotificationUser(Long notificationUserId) {
        return notificationUserRepository.findById(notificationUserId)
                .orElseThrow(() -> new EntityNotFoundException("NotificationUser not found: " + notificationUserId));
    }

    /** חובה: לא מאפשרים פעולה על NotificationUser בלי לוודא שייך למשתמש המבצע. */
    private NotificationUser requireNotificationUserForUser(Long actorUserId, Long notificationUserId) {
        if (actorUserId == null) {
            throw new IllegalArgumentException("actorUserId is required for this action");
        }
        NotificationUser nu = requireNotificationUser(notificationUserId);
        if (nu.getUser() == null || nu.getUser().getId() == null || !nu.getUser().getId().equals(actorUserId)) {
            throw new EntityNotFoundException("NotificationUser not found: " + notificationUserId);
        }
        return nu;
    }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    // =========================================================
    // Preferences (CRUD בסיסי, מסונכרן לריפו שלך)
    // =========================================================

    @Transactional(readOnly = true)
    public Optional<NotificationPreferences> getPreferences(Long userId) {
        return notificationPreferencesRepository.findByUser_Id(userId);
    }

    public NotificationPreferences getOrCreatePreferences(Long userId) {
        return notificationPreferencesRepository.findByUser_Id(userId).orElseGet(() -> {
            User u = requireUser(userId);
            NotificationPreferences p = new NotificationPreferences();
            p.setUser(u);

            // לא חובה (יש PrePersist), אבל משאיר עקבי
            if (p.getCreatedAt() == null) p.setCreatedAt(LocalDateTime.now());
            p.setUpdatedAt(LocalDateTime.now());

            return notificationPreferencesRepository.save(p);
        });
    }

    public NotificationPreferences setMuteAll(Long userId, boolean muteAll) {
        NotificationPreferences p = getOrCreatePreferences(userId);
        p.setMuteAll(muteAll);
        p.setUpdatedAt(LocalDateTime.now());
        return notificationPreferencesRepository.save(p);
    }

    public NotificationPreferences setMuteUntil(Long userId, LocalDateTime muteUntil) {
        NotificationPreferences p = getOrCreatePreferences(userId);
        p.setMuteUntil(muteUntil);
        p.setUpdatedAt(LocalDateTime.now());
        return notificationPreferencesRepository.save(p);
    }

    public NotificationPreferences setCustomPreferencesJson(Long userId, String json) {
        NotificationPreferences p = getOrCreatePreferences(userId);
        p.setCustomPreferencesJson(json);
        p.setUpdatedAt(LocalDateTime.now());
        return notificationPreferencesRepository.save(p);
    }

    // =========================================================
    // Rules helpers
    // =========================================================

    private NotificationPreferences loadPrefsOrNull(Long userId) {
        if (userId == null) return null;
        return notificationPreferencesRepository.findByUser_Id(userId).orElse(null);
    }

    private boolean isMuted(NotificationPreferences p) {
        if (p == null) return false;

        // muteAll הוא boolean primitive בישות -> משתמשים isMuteAll()
        if (p.isMuteAll()) return true;

        LocalDateTime until = p.getMuteUntil();
        return until != null && until.isAfter(LocalDateTime.now());
    }

    /**
     * קריטי בפרויקט: התראות שממש לא אמורות "להיעלם"
     * (הרחבנו מעט מעבר לשלישייה המקורית כדי שלא יהיה חור לוגי).
     */
    private boolean isAlwaysBypassMute(NotificationType type) {
        if (type == null) return false;

        return type == NotificationType.MATCH_MUTUAL
                || type == NotificationType.MATCH_CONFIRMED
                || type == NotificationType.LIKE_BACK_RECEIVED
                || type == NotificationType.FIRST_MESSAGE_RECEIVED
                || type == NotificationType.FIRST_MESSAGE_ACCEPTED;
    }

    private boolean passesMuteRules(NotificationPreferences prefs, NotificationType type) {
        if (!isMuted(prefs)) return true;
        return isAlwaysBypassMute(type);
    }

    private boolean isInAppEnabled(NotificationPreferences prefs) {
        if (prefs == null) return true; // default system behavior
        Boolean v = prefs.getEnableInApp();
        return v == null || v; // default true לפי הישות
    }

    private boolean isInQuietHours(NotificationPreferences prefs) {
        if (prefs == null) return false;
        Boolean enabled = prefs.getQuietHoursEnabled();
        if (enabled == null || !enabled) return false;

        LocalTime start = parseTimeSafe(prefs.getQuietHoursStart(), "22:00");
        LocalTime end = parseTimeSafe(prefs.getQuietHoursEnd(), "07:00");
        LocalTime now = LocalTime.now();

        // טווח שחוצה חצות (22:00-07:00)
        if (start.equals(end)) return true; // "כל היום"
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    private LocalTime parseTimeSafe(String value, String fallback) {
        try {
            String v = (value == null || value.isBlank()) ? fallback : value.trim();
            return LocalTime.parse(v);
        } catch (Exception e) {
            return LocalTime.parse(fallback);
        }
    }

    private boolean isThrottledNow(NotificationPreferences prefs) {
        if (prefs == null) return false;
        Boolean throttled = prefs.getThrottled();
        if (throttled == null || !throttled) return false;

        LocalDateTime until = prefs.getThrottleUntil();
        return until != null && until.isAfter(LocalDateTime.now());
    }

    private boolean isLikeType(NotificationType type) {
        return type == NotificationType.LIKE_RECEIVED
                || type == NotificationType.LIKE_BACK_RECEIVED;
    }

    private boolean isViewType(NotificationType type) {
        return type == NotificationType.PROFILE_VIEWS_SUMMARY;
    }

    private boolean isInitialMessageType(NotificationType type) {
        return type == NotificationType.FIRST_MESSAGE_RECEIVED
                || type == NotificationType.FIRST_MESSAGE_SENT
                || type == NotificationType.FIRST_MESSAGE_ACCEPTED
                || type == NotificationType.FIRST_MESSAGE_REJECTED;
    }

    /**
     * Rate limits לפי Preferences (אם קיימים), אחרת fallback לברירות מחדל.
     * קריטיים תמיד עוברים.
     */
    public boolean passesRateLimit(Long recipientUserId, NotificationType type) {
        if (recipientUserId == null || type == null) return true;

        if (isAlwaysBypassMute(type)) return true;

        NotificationPreferences prefs = loadPrefsOrNull(recipientUserId);

        int limit;
        int minutes;

        if (isLikeType(type)) {
            limit = (prefs != null && prefs.getLikeNotificationsLimit() != null) ? prefs.getLikeNotificationsLimit() : 1;
            minutes = (prefs != null && prefs.getLikeNotificationsMinutes() != null) ? prefs.getLikeNotificationsMinutes() : 10;
        } else if (isViewType(type)) {
            limit = (prefs != null && prefs.getViewNotificationsLimit() != null) ? prefs.getViewNotificationsLimit() : 1;
            minutes = (prefs != null && prefs.getViewNotificationsMinutes() != null) ? prefs.getViewNotificationsMinutes() : 60;
        } else if (isInitialMessageType(type)) {
            limit = (prefs != null && prefs.getInitialMessageLimit() != null) ? prefs.getInitialMessageLimit() : 1;
            minutes = (prefs != null && prefs.getInitialMessageMinutes() != null) ? prefs.getInitialMessageMinutes() : 5;
        } else {
            // סוגים אחרים: אין מגבלה כאן
            return true;
        }

        if (limit <= 0 || minutes <= 0) return true;

        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);

        // יש לנו כבר שאילתת Top50 -> מספיק טוב לרוב, בלי לשנות Repos
        List<NotificationUser> last = notificationUserRepository
                .findTop50ByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(recipientUserId);

        long count = 0;
        for (NotificationUser nu : last) {
            if (nu.getCreatedAt() == null || nu.getCreatedAt().isBefore(since)) continue;
            Notification n = nu.getNotification();
            if (n == null || n.getType() == null) continue;

            if (type.equals(n.getType())) {
                count++;
                if (count >= limit) return false;
            }
        }
        return true;
    }

    /**
     * Rate caps כלליים (Per Hour/Day) מה-Preferences.
     * משתמשים ב-count קיים בריפו (deleted=false) כדי לא לגעת בשכבות אחרות.
     */
    private boolean passesGlobalCaps(Long recipientUserId, NotificationPreferences prefs, NotificationType type) {
        if (recipientUserId == null || type == null) return true;
        if (isAlwaysBypassMute(type)) return true;
        if (prefs == null) return true;

        Integer perHour = prefs.getMaxNotificationsPerHour();
        if (perHour != null && perHour > 0) {
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            long c = notificationUserRepository.countByUser_IdAndDeletedFalseAndCreatedAtAfter(recipientUserId, since);
            if (c >= perHour) return false;
        }

        Integer perDay = prefs.getMaxNotificationsPerDay();
        if (perDay != null && perDay > 0) {
            LocalDateTime since = LocalDateTime.now().minusDays(1);
            long c = notificationUserRepository.countByUser_IdAndDeletedFalseAndCreatedAtAfter(recipientUserId, since);
            if (c >= perDay) return false;
        }

        return true;
    }

    // =========================================================
    // CREATE
    // =========================================================

    public NotificationCenterItemDto create(CreateNotificationRequest req) {
        if (req == null) throw new IllegalArgumentException("request is null");
        if (req.recipientUserId == null) throw new IllegalArgumentException("recipientUserId is required");
        if (req.type == null) throw new IllegalArgumentException("type is required");

        // 1) prefs פעם אחת
        NotificationPreferences prefs = loadPrefsOrNull(req.recipientUserId);

        // 2) אם המשתמש ביטל In-App -> לא יוצרים רשומה במרכז התראות (אלא אם קריטי/force)
        if (!req.force && !isInAppEnabled(prefs) && !isAlwaysBypassMute(req.type)) {
            return null;
        }

        // 3) throttled (מצב חסימה זמני) -> רק קריטיים/force עוברים
        if (!req.force && isThrottledNow(prefs) && !isAlwaysBypassMute(req.type)) {
            return null;
        }

        // 4) mute rules (קריטיים עוקפים)
        if (!req.force && !passesMuteRules(prefs, req.type)) {
            return null;
        }

        // 5) rate limit לפי סוג
        if (!req.force && !passesRateLimit(req.recipientUserId, req.type)) {
            return null;
        }

        // 6) caps כלליים (per hour/day)
        if (!req.force && !passesGlobalCaps(req.recipientUserId, prefs, req.type)) {
            return null;
        }

        // 7) quiet hours: ברירת מחדל לא חוסמים "מרכז התראות", רק חוסמים POPUP
        boolean quiet = isInQuietHours(prefs);
        boolean popupEligible = req.popupEligible;
        if (!req.force && quiet && !isAlwaysBypassMute(req.type)) {
            popupEligible = false;
        }

        User recipient = requireUser(req.recipientUserId);

        Notification n = new Notification();
        n.setType(req.type);
        n.setTitle(trimTo(req.title, 200));
        n.setMessage(trimTo(req.message, 2000));
        n.setMetadata(req.metadataJson);

        n.setCategory(trimTo(req.category, 50));
        n.setSource(trimTo(req.source, 50));
        n.setPriorityLevel(Math.max(1, req.priorityLevel));

        n.setRelatedUserId(req.actorUserId);
        n.setWeddingId(req.weddingId);
        n.setMatchId(req.matchId);
        n.setChatMessageId(req.chatMessageId);

        if (n.getCreatedAt() == null) n.setCreatedAt(LocalDateTime.now());

        n = notificationRepository.save(n);

        NotificationUser nu = new NotificationUser(n, recipient);

        // popup queue (ממשיכים להשתמש בשדה הקיים popupSeen)
        if (!popupEligible) {
            nu.setPopupSeen(true);
        }

        // delivered: בישות יש default/PrePersist, אבל נשארים null-safe
        if (nu.getDelivered() == null) nu.setDelivered(false);

        if (nu.getCreatedAt() == null) nu.setCreatedAt(LocalDateTime.now());

        nu = notificationUserRepository.save(nu);

        return NotificationCenterItemDto.from(nu);
    }

    public NotificationCenterItemDto createWithThrottle(CreateNotificationRequest req) {
        // תאימות לקוד ישן: create כבר כולל throttle/caps
        return create(req);
    }

    public List<NotificationCenterItemDto> createForUsers(List<CreateNotificationRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        List<NotificationCenterItemDto> out = new ArrayList<>(requests.size());
        for (CreateNotificationRequest r : requests) {
            NotificationCenterItemDto dto = create(r);
            if (dto != null) out.add(dto);
        }
        return out;
    }

    // =========================================================
    // CENTER (lists + badge)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationCenterItemDto> getLatestForUser(Long userId) {
        return notificationUserRepository
                .findTop50ByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(userId)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationUser::isPinned).reversed()
                        .thenComparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(NotificationCenterItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationCenterItemDto> getLatestUnreadForUser(Long userId) {
        return notificationUserRepository
                .findTop50ByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationUser::isPinned).reversed()
                        .thenComparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(NotificationCenterItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnreadVisible(Long userId) {
        return notificationUserRepository.countByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationCenterItemDto> getImportantForUser(Long userId, int minPriority) {
        return notificationUserRepository.findImportantNotificationsForUser(userId, minPriority)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationUser::isPinned).reversed()
                        .thenComparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(NotificationCenterItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationCenterItemDto> getLockedModeVisibleNotifications(Long userId) {
        return notificationUserRepository.findLockedModeVisibleNotifications(userId)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationUser::isPinned).reversed()
                        .thenComparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(NotificationCenterItemDto::from)
                .toList();
    }

    // =========================================================
    // POPUPS
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationCenterItemDto> getPendingPopupsForUser(Long userId) {
        return notificationUserRepository.findByUser_IdAndPopupSeenFalse(userId)
                .stream()
                .filter(nu -> !nu.isDeleted() && !nu.isHidden())
                .sorted(Comparator.comparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(NotificationCenterItemDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationCenterItemDto> getGlobalPendingPopups() {
        return notificationUserRepository.findByPopupSeenFalseAndDeletedFalseAndHiddenFalse()
                .stream()
                .sorted(Comparator.comparing(NotificationUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(NotificationCenterItemDto::from)
                .toList();
    }

    public NotificationCenterItemDto markPopupSeen(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setPopupSeen(true);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public List<NotificationCenterItemDto> markPopupSeenBulk(Long actorUserId, List<Long> notificationUserIds) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
        if (notificationUserIds == null || notificationUserIds.isEmpty()) return List.of();

        List<NotificationUser> items = notificationUserRepository.findByIdIn(notificationUserIds);
        List<NotificationUser> owned = new ArrayList<>();

        for (NotificationUser nu : items) {
            if (nu.getUser() != null && actorUserId.equals(nu.getUser().getId())) {
                nu.setPopupSeen(true);
                owned.add(nu);
            }
        }

        notificationUserRepository.saveAll(owned);
        return owned.stream().map(NotificationCenterItemDto::from).toList();
    }

    // =========================================================
    // READ/UNREAD — עם בעלות
    // =========================================================

    public NotificationCenterItemDto markRead(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setRead(true);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public NotificationCenterItemDto markUnread(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setRead(false);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public List<NotificationCenterItemDto> markReadBulk(Long actorUserId, List<Long> notificationUserIds) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
        if (notificationUserIds == null || notificationUserIds.isEmpty()) return List.of();

        List<NotificationUser> items = notificationUserRepository.findByIdIn(notificationUserIds);
        List<NotificationUser> owned = new ArrayList<>();

        for (NotificationUser nu : items) {
            if (nu.getUser() != null && actorUserId.equals(nu.getUser().getId())) {
                nu.setRead(true);
                owned.add(nu);
            }
        }

        notificationUserRepository.saveAll(owned);
        return owned.stream().map(NotificationCenterItemDto::from).toList();
    }

    public List<NotificationCenterItemDto> markUnreadBulk(Long actorUserId, List<Long> notificationUserIds) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
        if (notificationUserIds == null || notificationUserIds.isEmpty()) return List.of();

        List<NotificationUser> items = notificationUserRepository.findByIdIn(notificationUserIds);
        List<NotificationUser> owned = new ArrayList<>();

        for (NotificationUser nu : items) {
            if (nu.getUser() != null && actorUserId.equals(nu.getUser().getId())) {
                nu.setRead(false);
                owned.add(nu);
            }
        }

        notificationUserRepository.saveAll(owned);
        return owned.stream().map(NotificationCenterItemDto::from).toList();
    }

    // =========================================================
    // ACTIONS — pin/hide/snooze/delete — עם בעלות
    // =========================================================

    public NotificationCenterItemDto setPinned(Long actorUserId, Long notificationUserId, boolean pinned) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setPinned(pinned);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public NotificationCenterItemDto setHidden(Long actorUserId, Long notificationUserId, boolean hidden) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setHidden(hidden);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public NotificationCenterItemDto setSnoozed(Long actorUserId, Long notificationUserId, boolean snoozed) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setSnoozed(snoozed);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public NotificationCenterItemDto softDelete(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setDeleted(true);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    public List<NotificationCenterItemDto> softDeleteBulk(Long actorUserId, List<Long> notificationUserIds) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");
        if (notificationUserIds == null || notificationUserIds.isEmpty()) return List.of();

        List<NotificationUser> items = notificationUserRepository.findByIdIn(notificationUserIds);
        List<NotificationUser> owned = new ArrayList<>();

        for (NotificationUser nu : items) {
            if (nu.getUser() != null && actorUserId.equals(nu.getUser().getId())) {
                nu.setDeleted(true);
                owned.add(nu);
            }
        }

        notificationUserRepository.saveAll(owned);
        return owned.stream().map(NotificationCenterItemDto::from).toList();
    }

    // =========================================================
    // Bulk: mark all read (visible)
    // =========================================================

    public int markAllReadVisible(Long actorUserId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is required");

        List<NotificationUser> items = notificationUserRepository
                .findTop50ByUser_IdAndDeletedFalseAndHiddenFalseAndReadFalseOrderByCreatedAtDesc(actorUserId);

        if (items.isEmpty()) return 0;

        for (NotificationUser nu : items) nu.setRead(true);
        notificationUserRepository.saveAll(items);
        return items.size();
    }

    // =========================================================
    // Delivered queue (Push/WebSocket workers) — תואם לשדות החדשים
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationUser> getUndeliveredForUser(Long userId) {
        return notificationUserRepository.findByUser_IdAndDeliveredFalseAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationUser> getGlobalUndeliveredQueue() {
        return notificationUserRepository.findByDeliveredFalseAndDeletedFalseAndHiddenFalse();
    }

    public NotificationCenterItemDto markDelivered(Long actorUserId, Long notificationUserId) {
        NotificationUser nu = requireNotificationUserForUser(actorUserId, notificationUserId);
        nu.setDelivered(true);
        nu = notificationUserRepository.save(nu);
        return NotificationCenterItemDto.from(nu);
    }

    // =========================================================
    // ADMIN / ENTITY QUERIES (Dashboard + linkage)
    // =========================================================

    @Transactional(readOnly = true)
    public long countByType(NotificationType type) {
        return notificationRepository.countByType(type);
    }

    @Transactional(readOnly = true)
    public long countByCategory(String category) {
        return notificationRepository.countByCategory(category);
    }

    @Transactional(readOnly = true)
    public long countBySource(String source) {
        return notificationRepository.countBySource(source);
    }

    @Transactional(readOnly = true)
    public long countBetween(LocalDateTime start, LocalDateTime end) {
        return notificationRepository.countByCreatedAtBetween(start, end);
    }

    @Transactional(readOnly = true)
    public long countForWedding(Long weddingId) {
        return notificationRepository.countByWeddingId(weddingId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getWeddingNotifications(Long weddingId) {
        return notificationRepository.findByWeddingIdOrderByCreatedAtDesc(weddingId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getWeddingNotificationsBetween(Long weddingId, LocalDateTime start, LocalDateTime end) {
        return notificationRepository.findByWeddingIdAndCreatedAtBetweenOrderByCreatedAtDesc(weddingId, start, end);
    }

    @Transactional(readOnly = true)
    public List<Notification> getMatchNotifications(Long matchId) {
        return notificationRepository.findByMatchIdOrderByCreatedAtDesc(matchId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getChatMessageNotifications(Long chatMessageId) {
        return notificationRepository.findByChatMessageIdOrderByCreatedAtDesc(chatMessageId);
    }

    // =========================================================
    // AGGREGATION (summary)
    // =========================================================

    @Transactional(readOnly = true)
    public Map<NotificationType, Long> aggregateCountsLastHours(Long userId, int hoursBack) {
        if (userId == null) throw new IllegalArgumentException("userId is null");
        if (hoursBack <= 0) hoursBack = 24;

        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);

        List<NotificationUser> last = notificationUserRepository
                .findTop50ByUser_IdAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(userId);

        Map<NotificationType, Long> map = new EnumMap<>(NotificationType.class);
        for (NotificationUser nu : last) {
            if (nu.getCreatedAt() == null || nu.getCreatedAt().isBefore(since)) continue;
            Notification n = nu.getNotification();
            if (n == null || n.getType() == null) continue;
            map.put(n.getType(), map.getOrDefault(n.getType(), 0L) + 1L);
        }
        return map;
    }

    // =========================================================
    // CLEANUP — נכון: קודם NotificationUser ואז Notification
    // =========================================================

    public int hardDeleteNotificationUsersOlderThan(LocalDateTime time) {
        if (time == null) throw new IllegalArgumentException("time is null");
        List<NotificationUser> old = notificationUserRepository.findByCreatedAtBefore(time);
        if (old.isEmpty()) return 0;
        notificationUserRepository.deleteAll(old);
        return old.size();
    }

    public int hardDeleteNotificationsOlderThan(LocalDateTime time) {
        if (time == null) throw new IllegalArgumentException("time is null");

        // 1) קודם למחוק NotificationUser ישנים
        hardDeleteNotificationUsersOlderThan(time);

        // 2) ואז למחוק Notifications ישנים
        List<Notification> old = notificationRepository.findByCreatedAtBefore(time);
        if (old.isEmpty()) return 0;

        notificationRepository.deleteAll(old);
        return old.size();
    }
}