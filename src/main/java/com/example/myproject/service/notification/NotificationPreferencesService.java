package com.example.myproject.service.notification;

import com.example.myproject.model.NotificationPreferences;
import com.example.myproject.model.User;
import com.example.myproject.repository.NotificationPreferencesRepository;
import com.example.myproject.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;

    public NotificationPreferencesService(NotificationPreferencesRepository preferencesRepository,
                                          UserRepository userRepository) {
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // Core loaders
    // =========================================================

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public Optional<NotificationPreferences> getPreferencesOptional(Long userId) {
        if (userId == null) return Optional.empty();
        return preferencesRepository.findByUser_Id(userId);
    }

    public NotificationPreferences getOrCreate(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");

        return preferencesRepository.findByUser_Id(userId).orElseGet(() -> {
            User u = requireUser(userId);
            NotificationPreferences p = new NotificationPreferences();
            p.setUser(u);
            // חשוב: hooks בישות ימלאו ברירות מחדל (channels/quietHours/throttled וכו')
            return preferencesRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public boolean exists(Long userId) {
        if (userId == null) return false;
        return preferencesRepository.existsByUser_Id(userId);
    }

    public void deleteByUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        preferencesRepository.deleteByUser_Id(userId);
    }

    /**
     * Reset “רך”: מאפס הגדרות אבל משאיר רשומה קיימת (נוח בלי לשבור FK/תלויות UI)
     */
    public NotificationPreferences resetToDefaults(Long userId) {
        NotificationPreferences p = getOrCreate(userId);

        // מגבלות בסיסיות (כמו שהיה אצלך)
        p.setLikeNotificationsLimit(1);
        p.setLikeNotificationsMinutes(10);
        p.setViewNotificationsLimit(1);
        p.setViewNotificationsMinutes(60);
        p.setInitialMessageLimit(1);
        p.setInitialMessageMinutes(5);
        p.setProfileViewDigestEveryViews(30);
        p.setProfileViewDigestEveryHours(168);

        // mute
        p.setMuteAll(false);
        p.setMuteUntil(null);

        // קריטיים
        p.setAlwaysShowMatch(true);
        p.setAlwaysShowSuperLike(true);

        // custom json
        p.setCustomPreferencesJson(null);

        // channels (לא לשנות שמות) — defaults לפי הישות: inApp=true, push/email=false
        p.setEnableInApp(true);
        p.setEnablePush(false);
        p.setEnableEmail(false);

        // quiet hours
        p.setQuietHoursEnabled(false);
        p.setQuietHoursStart("22:00");
        p.setQuietHoursEnd("07:00");

        // throttle/rate caps
        p.setThrottled(false);
        p.setThrottleUntil(null);
        p.setMaxNotificationsPerHour(null);
        p.setMaxNotificationsPerDay(null);

        return preferencesRepository.save(p);
    }

    // =========================================================
    // Batch loading (Fan-out)
    // =========================================================

    @Transactional(readOnly = true)
    public Map<Long, NotificationPreferences> loadBatchMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();

        List<NotificationPreferences> prefs = preferencesRepository.findByUser_IdIn(distinct);
        Map<Long, NotificationPreferences> map = new HashMap<>();

        for (NotificationPreferences p : prefs) {
            if (p.getUser() != null && p.getUser().getId() != null) {
                map.put(p.getUser().getId(), p);
            }
        }

        // מי שחסר — לא יוצרים פה אוטומטית (כדי לא לייצר כתיבה סמויה),
        // fallback נקודתי דרך getOrCreate().
        return map;
    }

    // =========================================================
    // Simple update helpers (שימושי ל-Controller)
    // =========================================================

    public NotificationPreferences updateCustomJson(Long userId, String customJson) {
        NotificationPreferences p = getOrCreate(userId);
        p.setCustomPreferencesJson(customJson);
        return preferencesRepository.save(p);
    }

    public NotificationPreferences setAlwaysShowMatch(Long userId, boolean value) {
        NotificationPreferences p = getOrCreate(userId);
        p.setAlwaysShowMatch(value);
        return preferencesRepository.save(p);
    }

    public NotificationPreferences setAlwaysShowSuperLike(Long userId, boolean value) {
        NotificationPreferences p = getOrCreate(userId);
        p.setAlwaysShowSuperLike(value);
        return preferencesRepository.save(p);
    }

    // =========================================================
    // Mute rules
    // =========================================================

    @Transactional(readOnly = true)
    public boolean isMuted(Long userId, LocalDateTime now) {
        NotificationPreferences p = getOrCreate(userId);
        return isMuted(p, now);
    }

    public boolean isMuted(NotificationPreferences p, LocalDateTime now) {
        if (p == null) return false;
        if (now == null) now = LocalDateTime.now();

        if (p.isMuteAll()) return true;
        LocalDateTime until = p.getMuteUntil();
        return until != null && until.isAfter(now);
    }

    public NotificationPreferences muteAll(Long userId, boolean mute) {
        NotificationPreferences p = getOrCreate(userId);
        p.setMuteAll(mute);
        if (!mute) p.setMuteUntil(null);
        return preferencesRepository.save(p);
    }

    public NotificationPreferences muteUntil(Long userId, LocalDateTime until) {
        NotificationPreferences p = getOrCreate(userId);
        p.setMuteAll(false);
        p.setMuteUntil(until);
        return preferencesRepository.save(p);
    }

    public NotificationPreferences unmute(Long userId) {
        NotificationPreferences p = getOrCreate(userId);
        p.setMuteAll(false);
        p.setMuteUntil(null);
        return preferencesRepository.save(p);
    }

    // =========================================================
    // Quiet hours (string "HH:mm" + overnight support)
    // =========================================================

    @Transactional(readOnly = true)
    public boolean isQuietHoursEnabled(Long userId) {
        NotificationPreferences p = getOrCreate(userId);
        return Boolean.TRUE.equals(p.getQuietHoursEnabled());
    }

    @Transactional(readOnly = true)
    public boolean isWithinQuietHours(Long userId, LocalDateTime now) {
        NotificationPreferences p = getOrCreate(userId);
        return isWithinQuietHours(p, now);
    }

    public boolean isWithinQuietHours(NotificationPreferences p, LocalDateTime now) {
        if (p == null) return false;
        if (!Boolean.TRUE.equals(p.getQuietHoursEnabled())) return false;
        if (now == null) now = LocalDateTime.now();

        LocalTime start = parseTimeOrDefault(p.getQuietHoursStart(), LocalTime.of(22, 0));
        LocalTime end = parseTimeOrDefault(p.getQuietHoursEnd(), LocalTime.of(7, 0));
        LocalTime t = now.toLocalTime();

        // אם start < end => חלון באותו יום (לדוגמה 13:00-16:00)
        if (start.isBefore(end)) {
            return !t.isBefore(start) && t.isBefore(end);
        }

        // overnight (לדוגמה 22:00-07:00)
        return !t.isBefore(start) || t.isBefore(end);
    }

    private LocalTime parseTimeOrDefault(String hhmm, LocalTime def) {
        if (hhmm == null || hhmm.isBlank()) return def;
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (DateTimeParseException e) {
            return def;
        }
    }

    public NotificationPreferences setQuietHours(Long userId, boolean enabled, String startHHmm, String endHHmm) {
        NotificationPreferences p = getOrCreate(userId);
        p.setQuietHoursEnabled(enabled);
        if (startHHmm != null) p.setQuietHoursStart(startHHmm);
        if (endHHmm != null) p.setQuietHoursEnd(endHHmm);
        return preferencesRepository.save(p);
    }

    // =========================================================
    // Channels (Push/Email/InApp)
    // =========================================================

    @Transactional(readOnly = true)
    public boolean enableInApp(Long userId) {
        return Boolean.TRUE.equals(getOrCreate(userId).getEnableInApp());
    }

    @Transactional(readOnly = true)
    public boolean enablePush(Long userId) {
        return Boolean.TRUE.equals(getOrCreate(userId).getEnablePush());
    }

    @Transactional(readOnly = true)
    public boolean enableEmail(Long userId) {
        return Boolean.TRUE.equals(getOrCreate(userId).getEnableEmail());
    }

    public NotificationPreferences setChannels(Long userId, Boolean inApp, Boolean push, Boolean email) {
        NotificationPreferences p = getOrCreate(userId);
        if (inApp != null) p.setEnableInApp(inApp);
        if (push != null) p.setEnablePush(push);
        if (email != null) p.setEnableEmail(email);
        return preferencesRepository.save(p);
    }

    public enum DeliveryChannel {
        IN_APP, PUSH, EMAIL
    }

    /**
     * מחזיר ערוצים “מותר” לפי העדפות + quietHours (ברירת מחדל: בשעות שקט - InApp בלבד).
     * שים לב: Mute/Throttle מחליטים אם לשלוח בכלל (ב־NotificationService/Engine),
     * פה רק Routing לערוצים.
     */
    @Transactional(readOnly = true)
    public EnumSet<DeliveryChannel> resolveAllowedChannels(Long userId, LocalDateTime now) {
        NotificationPreferences p = getOrCreate(userId);
        return resolveAllowedChannels(p, now);
    }

    public EnumSet<DeliveryChannel> resolveAllowedChannels(NotificationPreferences p, LocalDateTime now) {
        EnumSet<DeliveryChannel> out = EnumSet.noneOf(DeliveryChannel.class);
        if (p == null) return out;

        boolean inApp = Boolean.TRUE.equals(p.getEnableInApp());
        boolean push = Boolean.TRUE.equals(p.getEnablePush());
        boolean email = Boolean.TRUE.equals(p.getEnableEmail());

        if (inApp) out.add(DeliveryChannel.IN_APP);

        // quiet hours policy: אם enabled ובתוך טווח => InApp-only
        if (isWithinQuietHours(p, now)) {
            return out;
        }

        if (push) out.add(DeliveryChannel.PUSH);
        if (email) out.add(DeliveryChannel.EMAIL);

        return out;
    }

    // =========================================================
    // Throttle (Anti-Spam)
    // =========================================================

    @Transactional(readOnly = true)
    public boolean isThrottled(Long userId, LocalDateTime now) {
        NotificationPreferences p = getOrCreate(userId);
        return isThrottled(p, now);
    }

    public boolean isThrottled(NotificationPreferences p, LocalDateTime now) {
        if (p == null) return false;
        if (now == null) now = LocalDateTime.now();

        if (Boolean.TRUE.equals(p.getThrottled())) {
            LocalDateTime until = p.getThrottleUntil();
            if (until == null) return true; // throttled בלי זמן -> נחשב פעיל
            return until.isAfter(now);
        }
        return false;
    }

    public NotificationPreferences throttleUser(Long userId, LocalDateTime until) {
        NotificationPreferences p = getOrCreate(userId);
        p.setThrottled(true);
        p.setThrottleUntil(until);
        return preferencesRepository.save(p);
    }

    public NotificationPreferences releaseThrottle(Long userId) {
        NotificationPreferences p = getOrCreate(userId);
        p.setThrottled(false);
        p.setThrottleUntil(null);
        return preferencesRepository.save(p);
    }

    /**
     * עוזר: אם throttleUntil עבר – משחרר אוטומטית.
     */
    public boolean releaseThrottleIfExpired(Long userId, LocalDateTime now) {
        NotificationPreferences p = getOrCreate(userId);
        if (!Boolean.TRUE.equals(p.getThrottled())) return false;

        if (now == null) now = LocalDateTime.now();
        LocalDateTime until = p.getThrottleUntil();
        if (until != null && !until.isAfter(now)) {
            p.setThrottled(false);
            p.setThrottleUntil(null);
            preferencesRepository.save(p);
            return true;
        }
        return false;
    }

    // =========================================================
    // Rate caps (per hour / per day)
    // האכיפה בפועל נעשית ע"י NotificationService/Engine עם counts אמיתיים.
    // =========================================================

    @Transactional(readOnly = true)
    public Integer getMaxPerHour(Long userId) {
        return getOrCreate(userId).getMaxNotificationsPerHour();
    }

    @Transactional(readOnly = true)
    public Integer getMaxPerDay(Long userId) {
        return getOrCreate(userId).getMaxNotificationsPerDay();
    }

    public NotificationPreferences setRateCaps(Long userId, Integer perHour, Integer perDay) {
        NotificationPreferences p = getOrCreate(userId);
        p.setMaxNotificationsPerHour(perHour);
        p.setMaxNotificationsPerDay(perDay);
        return preferencesRepository.save(p);
    }

    public boolean isRateCapExceeded(NotificationPreferences p, int sentLastHour, int sentToday) {
        if (p == null) return false;

        Integer maxH = p.getMaxNotificationsPerHour();
        Integer maxD = p.getMaxNotificationsPerDay();

        if (maxH != null && maxH > 0 && sentLastHour >= maxH) return true;
        if (maxD != null && maxD > 0 && sentToday >= maxD) return true;

        return false;
    }

    // =========================================================
    // Repository-backed queries (כמו שקיים בריפו)
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findMutedAll() {
        return preferencesRepository.findByMuteAllTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findMutedUntilAfter(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return preferencesRepository.findByMuteUntilAfter(now);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findMutedNow(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return preferencesRepository.findByMuteAllTrueOrMuteUntilAfter(now);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findAlwaysShowMatch() {
        return preferencesRepository.findByAlwaysShowMatchTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findAlwaysShowSuperLike() {
        return preferencesRepository.findByAlwaysShowSuperLikeTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findQuietHoursEnabled() {
        return preferencesRepository.findByQuietHoursEnabledTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findEnablePushTrue() {
        return preferencesRepository.findByEnablePushTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findEnableEmailTrue() {
        return preferencesRepository.findByEnableEmailTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findEnableInAppTrue() {
        return preferencesRepository.findByEnableInAppTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findThrottledTrue() {
        return preferencesRepository.findByThrottledTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findThrottleUntilAfter(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return preferencesRepository.findByThrottleUntilAfter(now);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findRateCapPerHourDefined() {
        return preferencesRepository.findByMaxNotificationsPerHourIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findRateCapPerDayDefined() {
        return preferencesRepository.findByMaxNotificationsPerDayIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findWithCustomJson() {
        return preferencesRepository.findByCustomPreferencesJsonIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findUpdatedBefore(LocalDateTime time) {
        if (time == null) throw new IllegalArgumentException("time is required");
        return preferencesRepository.findByUpdatedAtBefore(time);
    }

    // =========================================================
    // Admin stats (כמו בריפו)
    // =========================================================

    @Transactional(readOnly = true)
    public long countMuteAllTrue() {
        return preferencesRepository.countByMuteAllTrue();
    }

    @Transactional(readOnly = true)
    public long countMutedNow(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return preferencesRepository.countByMuteAllTrueOrMuteUntilAfter(now);
    }

    @Transactional(readOnly = true)
    public long countEnablePushTrue() {
        return preferencesRepository.countByEnablePushTrue();
    }

    @Transactional(readOnly = true)
    public long countEnableEmailTrue() {
        return preferencesRepository.countByEnableEmailTrue();
    }

    @Transactional(readOnly = true)
    public long countEnableInAppTrue() {
        return preferencesRepository.countByEnableInAppTrue();
    }

    @Transactional(readOnly = true)
    public long countQuietHoursEnabledTrue() {
        return preferencesRepository.countByQuietHoursEnabledTrue();
    }

    @Transactional(readOnly = true)
    public long countThrottledTrue() {
        return preferencesRepository.countByThrottledTrue();
    }

    // =========================================================
    // Segmentation helpers שלא קיימים כרגע בריפו — fallback דרך findAll()
    // מומלץ לדשבורד/אנליטיקה, לא למסלול קריטי.
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findEnablePushFalse() {
        return preferencesRepository.findAll()
                .stream()
                .filter(p -> !Boolean.TRUE.equals(p.getEnablePush()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findEnableEmailFalse() {
        return preferencesRepository.findAll()
                .stream()
                .filter(p -> !Boolean.TRUE.equals(p.getEnableEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferences> findOnlyInAppUsers() {
        return preferencesRepository.findAll()
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnableInApp()))
                .filter(p -> !Boolean.TRUE.equals(p.getEnablePush()))
                .filter(p -> !Boolean.TRUE.equals(p.getEnableEmail()))
                .toList();
    }

    // =========================================================
    // Helper summary (נוח לדשבורד)
    // =========================================================

    @Transactional(readOnly = true)
    public Map<String, Long> buildAdminSummary(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();

        Map<String, Long> m = new LinkedHashMap<>();
        m.put("muteAllTrue", countMuteAllTrue());
        m.put("mutedNow", countMutedNow(now));
        m.put("enableInAppTrue", countEnableInAppTrue());
        m.put("enablePushTrue", countEnablePushTrue());
        m.put("enableEmailTrue", countEnableEmailTrue());
        m.put("quietHoursEnabledTrue", countQuietHoursEnabledTrue());
        m.put("throttledTrue", countThrottledTrue());
        return m;
    }
}