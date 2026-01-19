package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.UserSettings;
import com.example.myproject.model.enums.DefaultMode;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.UserSettingsRepository;
import com.example.myproject.service.System.SystemLogService;
import com.example.myproject.service.System.SystemSettingsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UserSettingsService (MASTER 2025 — FINAL + OPTIMAL + COMPAT)
 *
 * ✅ Covers:
 * - Full UserSettingsRepository capabilities (1–8) wrappers
 * - Correct “Currently Locked” semantics: lockedUntil NULL means still locked
 * - Efficient autoUnlockExpiredLocks using repo: lockedUntilBefore(now)
 * - No DB writes inside readOnly methods (no implicit getOrCreate there)
 * - Consistency with UserStateEvaluator:
 *   - canViewSameGender => User.allowProfileViewBySameGender
 *   - lockAfterWedding  => User.profileLockedAfterWedding/profileLockedAt
 * - Optional SystemSettings overrides + optional audit logging
 */
@Service
@Transactional
public class UserSettingsService {

    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;

    private final SystemSettingsService systemSettingsService; // optional
    private final SystemLogService systemLogService;           // optional

    public UserSettingsService(UserSettingsRepository settingsRepo,
                               UserRepository userRepo,
                               SystemSettingsService systemSettingsService,
                               SystemLogService systemLogService) {
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
        this.systemSettingsService = systemSettingsService;
        this.systemLogService = systemLogService;
    }

    // ============================================================
    // Core: find / exists / getOrCreate
    // ============================================================

    @Transactional(readOnly = true)
    public Optional<UserSettings> findByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        return settingsRepo.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public boolean existsForUser(Long userId) {
        if (userId == null) return false;
        return settingsRepo.existsByUser_Id(userId);
    }

    /**
     * getOrCreate: כותב (עשוי ליצור + sync user), ולכן אינו readOnly.
     */
    public UserSettings getOrCreate(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        Optional<UserSettings> existing = settingsRepo.findByUser_Id(userId);
        if (existing.isPresent()) {
            softSyncUserFromSettings(existing.get());
            return existing.get();
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserSettings created = new UserSettings();

        set(created, "user", "setUser", User.class, user);
        set(created, "defaultMode", "setDefaultMode", DefaultMode.class, DefaultMode.GLOBAL);
        set(created, "canViewSameGender", "setCanViewSameGender", boolean.class, false);

        setIfNull(created, "likeCooldownSeconds", "getLikeCooldownSeconds", "setLikeCooldownSeconds",
                Integer.class, getSystemIntOrDefault("defaults.likeCooldownSeconds", 2));
        setIfNull(created, "messageCooldownSeconds", "getMessageCooldownSeconds", "setMessageCooldownSeconds",
                Integer.class, getSystemIntOrDefault("defaults.messageCooldownSeconds", 2));
        setIfNull(created, "autoAntiSpam", "isAutoAntiSpam", "setAutoAntiSpam",
                boolean.class, getSystemBoolOrDefault("defaults.autoAntiSpam", true));

        setIfNull(created, "lockedAfterWedding", "getLockedAfterWedding", "setLockedAfterWedding",
                Boolean.class, false);

        try {
            UserSettings saved = settingsRepo.save(created);
            softSyncUserFromSettings(saved);
            audit(userId, "UserSettings created (getOrCreate)");
            return saved;
        } catch (DataIntegrityViolationException race) {
            return settingsRepo.findByUser_Id(userId).orElseThrow(() -> race);
        }
    }

    // ============================================================
    // Delete (SAFE + FORCE)
    // ============================================================

    /**
     * SAFE delete: לא מוחק אם יש LOCK פעיל (כדי לא לשבור Lock rules/queries).
     * לשחרור מלא + מחיקה השתמש ב-forceDeleteForUser.
     */
    public void deleteForUser(Long userId) {
        if (userId == null) return;

        boolean lockedNow = isCurrentlyLocked(userId);
        if (lockedNow) {
            throw new IllegalStateException(
                    "Cannot delete UserSettings while user is currently locked. " +
                            "Use forceDeleteForUser(userId, reason) or resetToDefaultsIncludingLock."
            );
        }

        settingsRepo.deleteByUser_Id(userId);

        // keep evaluator consistent: default false
        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            trySetBoolean(user, "allowProfileViewBySameGender", "setAllowProfileViewBySameGender", false);
            userRepo.save(user);
        }

        audit(userId, "UserSettings deleted for user (SAFE)");
    }

    /**
     * FORCE delete: מאפס גם Lock על ה-User + מוחק settings.
     * Admin/System בלבד.
     */
    public void forceDeleteForUser(Long userId, String reason) {
        if (userId == null) return;

        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            user.setProfileLockedAfterWedding(false);
            trySetBoolean(user, "allowProfileViewBySameGender", "setAllowProfileViewBySameGender", false);
            userRepo.save(user);
        }

        settingsRepo.deleteByUser_Id(userId);
        audit(userId, "UserSettings deleted for user (FORCE). reason=" + safe(reason));
    }

    // ============================================================
    // Update APIs (User side)
    // ============================================================

    public UserSettings updateDefaultMode(Long userId, DefaultMode mode) {
        if (userId == null) throw new IllegalArgumentException("userId is null");
        if (mode == null) mode = DefaultMode.GLOBAL;

        UserSettings s = getOrCreate(userId);
        set(s, "defaultMode", "setDefaultMode", DefaultMode.class, mode);

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings.defaultMode updated to " + mode);
        return saved;
    }

    public UserSettings updateCanViewSameGender(Long userId, boolean canViewSameGender) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        UserSettings s = getOrCreate(userId);
        set(s, "canViewSameGender", "setCanViewSameGender", boolean.class, canViewSameGender);

        User user = loadUserFromSettingsOrRepo(s, userId);
        if (user != null) {
            trySetBoolean(user, "allowProfileViewBySameGender", "setAllowProfileViewBySameGender", canViewSameGender);
            userRepo.save(user);
        }

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings.canViewSameGender updated to " + canViewSameGender);
        return saved;
    }

    public UserSettings updateShortCardFieldsJson(Long userId, String shortCardFieldsJson) {
        UserSettings s = getOrCreate(userId);
        set(s, "shortCardFieldsJson", "setShortCardFieldsJson", String.class, normalizeJsonOrNull(shortCardFieldsJson));
        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings.shortCardFieldsJson updated");
        return saved;
    }

    public UserSettings updateUiPreferencesJson(Long userId, String uiPreferencesJson) {
        UserSettings s = getOrCreate(userId);
        set(s, "uiPreferencesJson", "setUiPreferencesJson", String.class, normalizeJsonOrNull(uiPreferencesJson));
        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings.uiPreferencesJson updated");
        return saved;
    }

    public UserSettings updateExtraSettingsJson(Long userId, String extraSettingsJson) {
        UserSettings s = getOrCreate(userId);
        set(s, "extraSettingsJson", "setExtraSettingsJson", String.class, normalizeJsonOrNull(extraSettingsJson));
        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings.extraSettingsJson updated");
        return saved;
    }

    public UserSettings updateAntiSpam(Long userId,
                                       Integer likeCooldownSeconds,
                                       Integer messageCooldownSeconds,
                                       Boolean autoAntiSpam) {
        UserSettings s = getOrCreate(userId);

        if (likeCooldownSeconds != null) {
            set(s, "likeCooldownSeconds", "setLikeCooldownSeconds", Integer.class, clamp(likeCooldownSeconds, 0, 120));
        }
        if (messageCooldownSeconds != null) {
            set(s, "messageCooldownSeconds", "setMessageCooldownSeconds", Integer.class, clamp(messageCooldownSeconds, 0, 120));
        }
        if (autoAntiSpam != null) {
            set(s, "autoAntiSpam", "setAutoAntiSpam", boolean.class, autoAntiSpam);
        }

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings anti-spam updated");
        return saved;
    }

    /**
     * Reset “Preferences” בלבד (לא נוגע ב-lock).
     */
    public UserSettings resetToDefaults(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        UserSettings s = getOrCreate(userId);

        set(s, "defaultMode", "setDefaultMode", DefaultMode.class, DefaultMode.GLOBAL);
        set(s, "canViewSameGender", "setCanViewSameGender", boolean.class, false);
        set(s, "shortCardFieldsJson", "setShortCardFieldsJson", String.class, null);
        set(s, "uiPreferencesJson", "setUiPreferencesJson", String.class, null);
        set(s, "extraSettingsJson", "setExtraSettingsJson", String.class, null);

        set(s, "likeCooldownSeconds", "setLikeCooldownSeconds", Integer.class, getSystemIntOrDefault("defaults.likeCooldownSeconds", 2));
        set(s, "messageCooldownSeconds", "setMessageCooldownSeconds", Integer.class, getSystemIntOrDefault("defaults.messageCooldownSeconds", 2));
        set(s, "autoAntiSpam", "setAutoAntiSpam", boolean.class, getSystemBoolOrDefault("defaults.autoAntiSpam", true));

        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            trySetBoolean(user, "allowProfileViewBySameGender", "setAllowProfileViewBySameGender", false);
            userRepo.save(user);
        }

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings resetToDefaults (preferences only)");
        return saved;
    }

    /**
     * Reset כולל lock (Admin/System בלבד) — save יחיד.
     */
    public UserSettings resetToDefaultsIncludingLock(Long userId, String reason) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        UserSettings s = getOrCreate(userId);

        // preferences
        set(s, "defaultMode", "setDefaultMode", DefaultMode.class, DefaultMode.GLOBAL);
        set(s, "canViewSameGender", "setCanViewSameGender", boolean.class, false);
        set(s, "shortCardFieldsJson", "setShortCardFieldsJson", String.class, null);
        set(s, "uiPreferencesJson", "setUiPreferencesJson", String.class, null);
        set(s, "extraSettingsJson", "setExtraSettingsJson", String.class, null);

        set(s, "likeCooldownSeconds", "setLikeCooldownSeconds", Integer.class, getSystemIntOrDefault("defaults.likeCooldownSeconds", 2));
        set(s, "messageCooldownSeconds", "setMessageCooldownSeconds", Integer.class, getSystemIntOrDefault("defaults.messageCooldownSeconds", 2));
        set(s, "autoAntiSpam", "setAutoAntiSpam", boolean.class, getSystemBoolOrDefault("defaults.autoAntiSpam", true));

        // lock
        set(s, "lockedAfterWedding", "setLockedAfterWedding", Boolean.class, false);
        set(s, "lockedUntil", "setLockedUntil", LocalDateTime.class, null);

        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            user.setProfileLockedAfterWedding(false);
            trySetBoolean(user, "allowProfileViewBySameGender", "setAllowProfileViewBySameGender", false);
            userRepo.save(user);
        }

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "UserSettings resetToDefaultsIncludingLock. reason=" + safe(reason));
        return saved;
    }

    // ============================================================
    // Lock After Wedding
    // ============================================================

    public UserSettings lockAfterWedding(Long userId, LocalDateTime lockedUntil, String reason) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        UserSettings s = getOrCreate(userId);

        set(s, "lockedAfterWedding", "setLockedAfterWedding", Boolean.class, true);
        set(s, "lockedUntil", "setLockedUntil", LocalDateTime.class, lockedUntil);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setProfileLockedAfterWedding(true);
        if (user.getProfileLockedAt() == null) user.setProfileLockedAt(LocalDateTime.now());
        userRepo.save(user);

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "LockAfterWedding applied. until=" + lockedUntil + " reason=" + safe(reason));
        return saved;
    }

    public UserSettings unlockAfterWedding(Long userId, String reason) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        UserSettings s = getOrCreate(userId);

        set(s, "lockedAfterWedding", "setLockedAfterWedding", Boolean.class, false);
        set(s, "lockedUntil", "setLockedUntil", LocalDateTime.class, null);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setProfileLockedAfterWedding(false);
        userRepo.save(user);

        UserSettings saved = settingsRepo.save(s);
        audit(userId, "LockAfterWedding removed. reason=" + safe(reason));
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean isCurrentlyLocked(Long userId) {
        if (userId == null) return false;

        Optional<UserSettings> opt = settingsRepo.findByUser_Id(userId);
        if (opt.isEmpty()) return false;

        UserSettings s = opt.get();
        Boolean locked = get(s, "lockedAfterWedding", "getLockedAfterWedding", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) return false;

        LocalDateTime until = get(s, "lockedUntil", "getLockedUntil", LocalDateTime.class);
        return (until == null) || until.isAfter(LocalDateTime.now());
    }

    public int autoUnlockExpiredLocks(int maxBatch) {
        if (maxBatch <= 0) maxBatch = 500;

        LocalDateTime now = LocalDateTime.now();
        List<UserSettings> expired = settingsRepo.findByLockedAfterWeddingTrueAndLockedUntilIsNotNullAndLockedUntilBefore(now);
        if (expired.isEmpty()) return 0;

        int changed = 0;

        List<UserSettings> toSaveSettings = new ArrayList<>();
        List<User> toSaveUsers = new ArrayList<>();

        for (UserSettings s : expired) {
            if (changed >= maxBatch) break;

            Long userId = extractUserId(s);
            if (userId == null) continue;

            set(s, "lockedAfterWedding", "setLockedAfterWedding", Boolean.class, false);
            set(s, "lockedUntil", "setLockedUntil", LocalDateTime.class, null);
            toSaveSettings.add(s);

            User user = userRepo.findById(userId).orElse(null);
            if (user != null) {
                user.setProfileLockedAfterWedding(false);
                toSaveUsers.add(user);
            }

            audit(userId, "autoUnlockExpiredLocks");
            changed++;
        }

        if (!toSaveUsers.isEmpty()) userRepo.saveAll(toSaveUsers);
        if (!toSaveSettings.isEmpty()) settingsRepo.saveAll(toSaveSettings);

        return changed;
    }

    // ============================================================
    // Effective values (read-only)
    // ============================================================

    @Transactional(readOnly = true)
    public int getEffectiveLikeCooldownSeconds(Long userId) {
        Integer def = getSystemIntOrDefault("defaults.likeCooldownSeconds", 2);
        if (userId == null) return clamp(def, 0, 120);

        Optional<UserSettings> opt = settingsRepo.findByUser_Id(userId);
        if (opt.isEmpty()) return clamp(def, 0, 120);

        Integer v = get(opt.get(), "likeCooldownSeconds", "getLikeCooldownSeconds", Integer.class);
        if (v == null) v = def;
        return clamp(v, 0, 120);
    }

    @Transactional(readOnly = true)
    public int getEffectiveMessageCooldownSeconds(Long userId) {
        Integer def = getSystemIntOrDefault("defaults.messageCooldownSeconds", 2);
        if (userId == null) return clamp(def, 0, 120);

        Optional<UserSettings> opt = settingsRepo.findByUser_Id(userId);
        if (opt.isEmpty()) return clamp(def, 0, 120);

        Integer v = get(opt.get(), "messageCooldownSeconds", "getMessageCooldownSeconds", Integer.class);
        if (v == null) v = def;
        return clamp(v, 0, 120);
    }

    @Transactional(readOnly = true)
    public boolean isAutoAntiSpamEnabled(Long userId) {
        boolean def = getSystemBoolOrDefault("defaults.autoAntiSpam", true);
        if (userId == null) return def;

        Optional<UserSettings> opt = settingsRepo.findByUser_Id(userId);
        if (opt.isEmpty()) return def;

        Boolean b = get(opt.get(), "autoAntiSpam", "isAutoAntiSpam", Boolean.class);
        return (b == null) ? def : b;
    }

    @Transactional(readOnly = true)
    public DefaultMode getDefaultMode(Long userId) {
        if (userId == null) return DefaultMode.GLOBAL;

        Optional<UserSettings> opt = settingsRepo.findByUser_Id(userId);
        if (opt.isEmpty()) return DefaultMode.GLOBAL;

        DefaultMode dm = get(opt.get(), "defaultMode", "getDefaultMode", DefaultMode.class);
        return (dm == null) ? DefaultMode.GLOBAL : dm;
    }

    @Transactional(readOnly = true)
    public boolean canViewSameGender(Long userId) {
        if (userId == null) return false;

        Optional<UserSettings> opt = settingsRepo.findByUser_Id(userId);
        if (opt.isPresent()) {
            Boolean b = get(opt.get(), "canViewSameGender", "isCanViewSameGender", Boolean.class);
            if (b != null) return b;
        }

        User u = userRepo.findById(userId).orElse(null);
        return u != null && u.isAllowProfileViewBySameGender();
    }

    // ============================================================
    // Admin / Stats wrappers — FULL COVERAGE
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserSettings> listByDefaultMode(DefaultMode mode) {
        if (mode == null) return List.of();
        return settingsRepo.findByDefaultMode(mode);
    }

    @Transactional(readOnly = true)
    public long countByDefaultMode(DefaultMode mode) {
        if (mode == null) return 0;
        return settingsRepo.countByDefaultMode(mode);
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listByDefaultModeNot(DefaultMode mode) {
        if (mode == null) return List.of();
        return settingsRepo.findByDefaultModeNot(mode);
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listCanViewSameGenderEnabled() {
        return settingsRepo.findByCanViewSameGenderTrue();
    }

    @Transactional(readOnly = true)
    public long countCanViewSameGenderEnabled() {
        return settingsRepo.countByCanViewSameGenderTrue();
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listAutoAntiSpamEnabled() {
        return settingsRepo.findByAutoAntiSpamTrue();
    }

    @Transactional(readOnly = true)
    public long countAutoAntiSpamEnabled() {
        return settingsRepo.countByAutoAntiSpamTrue();
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listByLikeCooldownSecondsLessThanEqual(Integer seconds) {
        if (seconds == null) return List.of();
        return settingsRepo.findByLikeCooldownSecondsLessThanEqual(seconds);
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listByMessageCooldownSecondsLessThanEqual(Integer seconds) {
        if (seconds == null) return List.of();
        return settingsRepo.findByMessageCooldownSecondsLessThanEqual(seconds);
    }

    @Transactional(readOnly = true)
    public long countShortCardFieldsJsonNotNull() {
        return settingsRepo.countByShortCardFieldsJsonIsNotNull();
    }

    @Transactional(readOnly = true)
    public long countUiPreferencesJsonNotNull() {
        return settingsRepo.countByUiPreferencesJsonIsNotNull();
    }

    @Transactional(readOnly = true)
    public long countExtraSettingsJsonNotNull() {
        return settingsRepo.countByExtraSettingsJsonIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listLockedAfterWedding() {
        return settingsRepo.findByLockedAfterWeddingTrue();
    }

    /**
     * ✅ Wrapper חסר שהיה כדאי: Legacy list (לא כולל lockedUntil NULL)
     */
    @Transactional(readOnly = true)
    public List<UserSettings> listLockedAfterWeddingLegacyLockedUntilAfter(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return settingsRepo.findByLockedAfterWeddingTrueAndLockedUntilAfter(now);
    }

    @Transactional(readOnly = true)
    public long countLockedAfterWedding() {
        return settingsRepo.countByLockedAfterWeddingTrue();
    }

    @Transactional(readOnly = true)
    public long countCurrentlyLocked(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return settingsRepo.countCurrentlyLockedIncludingNull(now);
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listCurrentlyLocked(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return settingsRepo.findCurrentlyLockedIncludingNull(now);
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listUpdatedAfter(LocalDateTime time) {
        if (time == null) return List.of();
        return settingsRepo.findByUpdatedAtAfter(time);
    }

    @Transactional(readOnly = true)
    public List<UserSettings> listCreatedBefore(LocalDateTime time) {
        if (time == null) return List.of();
        return settingsRepo.findByCreatedAtBefore(time);
    }

    @Transactional(readOnly = true)
    public long countAutoAntiSpamTrueAndLikeCooldownSecondsLessThanEqual(Integer seconds) {
        if (seconds == null) return 0;
        return settingsRepo.countByAutoAntiSpamTrueAndLikeCooldownSecondsLessThanEqual(seconds);
    }

    @Transactional(readOnly = true)
    public long countAutoAntiSpamTrueAndMessageCooldownSecondsLessThanEqual(Integer seconds) {
        if (seconds == null) return 0;
        return settingsRepo.countByAutoAntiSpamTrueAndMessageCooldownSecondsLessThanEqual(seconds);
    }

    @Transactional(readOnly = true)
    public long countCurrentlyLockedLegacyLockedUntilAfter(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        return settingsRepo.countByLockedAfterWeddingTrueAndLockedUntilAfter(now);
    }

    // ============================================================
    // Internal: soft sync UserSettings -> User (evaluator consistency)
    // ============================================================

    private void softSyncUserFromSettings(UserSettings s) {
        if (s == null) return;

        Long userId = extractUserId(s);
        if (userId == null) return;

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        Boolean canViewSame = get(s, "canViewSameGender", "isCanViewSameGender", Boolean.class);
        if (canViewSame != null && user.isAllowProfileViewBySameGender() != canViewSame) {
            trySetBoolean(user, "allowProfileViewBySameGender", "setAllowProfileViewBySameGender", canViewSame);
        }

        Boolean lockedAfterWedding = get(s, "lockedAfterWedding", "getLockedAfterWedding", Boolean.class);
        if (lockedAfterWedding != null) {
            boolean shouldLock = Boolean.TRUE.equals(lockedAfterWedding) && isLockStillActive(s);
            if (user.isProfileLockedAfterWedding() != shouldLock) {
                user.setProfileLockedAfterWedding(shouldLock);
                if (shouldLock && user.getProfileLockedAt() == null) user.setProfileLockedAt(LocalDateTime.now());
            }
        }

        userRepo.save(user);
    }

    private boolean isLockStillActive(UserSettings s) {
        Boolean locked = get(s, "lockedAfterWedding", "getLockedAfterWedding", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) return false;
        LocalDateTime until = get(s, "lockedUntil", "getLockedUntil", LocalDateTime.class);
        return (until == null) || until.isAfter(LocalDateTime.now());
    }

    private Long extractUserId(UserSettings s) {
        try {
            Object userObj = invokeGetter(s, "getUser");
            if (userObj instanceof User u) return u.getId();
        } catch (Exception ignore) { }
        Object userObj = getField(s, "user");
        if (userObj instanceof User u) return u.getId();
        return null;
    }

    private User loadUserFromSettingsOrRepo(UserSettings s, Long userId) {
        try {
            Object userObj = invokeGetter(s, "getUser");
            if (userObj instanceof User u) return u;
        } catch (Exception ignore) { }
        return userRepo.findById(userId).orElse(null);
    }

    // ============================================================
    // Logging (optional)
    // ============================================================

    private void audit(Long userId, String details) {
        if (systemLogService == null) return;
        try {
            systemLogService.log(
                    SystemActionType.USER_ACTION_RECORDED,
                    SystemModule.USER_SERVICE,
                    SystemSeverityLevel.INFO,
                    true,
                    userId,
                    details
            );
        } catch (Exception ignore) { }
    }

    // ============================================================
    // SystemSettings overrides (optional)
    // ============================================================

    private int getSystemIntOrDefault(String suffix, int def) {
        if (systemSettingsService == null) return def;
        try {
            return systemSettingsService.getEffectiveInt(
                    systemSettingsService.resolveEnv(),
                    SystemSettingsService.Scope.SYSTEM,
                    null,
                    "user." + suffix,
                    def
            );
        } catch (Exception e) {
            return def;
        }
    }

    private boolean getSystemBoolOrDefault(String suffix, boolean def) {
        if (systemSettingsService == null) return def;
        try {
            return systemSettingsService.getEffectiveBoolean(
                    systemSettingsService.resolveEnv(),
                    SystemSettingsService.Scope.SYSTEM,
                    null,
                    "user." + suffix,
                    def
            );
        } catch (Exception e) {
            return def;
        }
    }

    // ============================================================
    // Reflection helpers (COMPAT)
    // ============================================================

    private static String normalizeJsonOrNull(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private <T> T get(Object target, String fieldName, String getterName, Class<T> type) {
        if (target == null) return null;

        Object viaGetter = invokeGetter(target, getterName);
        if (viaGetter != null && type.isInstance(viaGetter)) return type.cast(viaGetter);

        Object viaField = getField(target, fieldName);
        if (viaField != null && type.isInstance(viaField)) return type.cast(viaField);

        return null;
    }

    private void set(Object target, String fieldName, String setterName, Class<?> paramType, Object value) {
        if (target == null) return;

        boolean ok = invokeSetter(target, setterName, paramType, value);
        if (ok) return;

        setField(target, fieldName, value);
    }

    private void setIfNull(Object target,
                           String fieldName,
                           String getterName,
                           String setterName,
                           Class<?> paramType,
                           Object valueIfNull) {
        Object cur = invokeGetter(target, getterName);
        if (cur != null) return;
        Object viaField = getField(target, fieldName);
        if (viaField != null) return;
        set(target, fieldName, setterName, paramType, valueIfNull);
    }

    private static Object invokeGetter(Object target, String getterName) {
        if (target == null || getterName == null) return null;
        try {
            Method m = target.getClass().getMethod(getterName);
            return m.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean invokeSetter(Object target, String setterName, Class<?> paramType, Object value) {
        if (target == null || setterName == null || paramType == null) return false;
        try {
            Method m;
            try {
                m = target.getClass().getMethod(setterName, paramType);
            } catch (NoSuchMethodException e) {
                if (paramType == boolean.class) m = target.getClass().getMethod(setterName, Boolean.class);
                else if (paramType == int.class) m = target.getClass().getMethod(setterName, Integer.class);
                else if (paramType == long.class) m = target.getClass().getMethod(setterName, Long.class);
                else return false;
            }
            m.invoke(target, value);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static Object getField(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) return;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignore) { }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static void trySetBoolean(Object target, String fieldName, String setterName, boolean value) {
        if (target == null) return;
        boolean ok = invokeSetter(target, setterName, boolean.class, value);
        if (ok) return;
        setField(target, fieldName, value);
    }

    // =====================================================
    // ✅ Paging wrappers (MASTER-ONE)
    // =====================================================

    @Transactional(readOnly = true)
    public Page<UserSettings> listByDefaultMode(DefaultMode mode, Pageable pageable) {
        if (mode == null) throw new IllegalArgumentException("mode is null");
        return settingsRepo.findByDefaultMode(mode, pageable);
    }

    @Transactional(readOnly = true)
    public Page<UserSettings> listLockedAfterWedding(Pageable pageable) {
        return settingsRepo.findByLockedAfterWeddingTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<UserSettings> listLockedUntilNotNull(Pageable pageable) {
        return settingsRepo.findByLockedUntilIsNotNull(pageable);
    }

}