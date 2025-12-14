package com.example.myproject.service;

import com.example.myproject.model.SystemLog;
import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.model.WeddingBackground;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.model.enums.BackgroundType;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.model.enums.WeddingParticipantRole;
import com.example.myproject.repository.SystemLogRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingBackgroundRepository;
import com.example.myproject.repository.WeddingParticipantRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class WeddingBackgroundService {

    private final WeddingBackgroundRepository weddingBackgroundRepository;
    private final WeddingRepository weddingRepository;
    private final UserRepository userRepository;
    private final WeddingParticipantRepository weddingParticipantRepository;

    private static final Set<WeddingParticipantRole> OWNER_ROLES =
            Set.of(WeddingParticipantRole.OWNER, WeddingParticipantRole.CO_OWNER);

    // ============================================================
    // ✅ OPTIONAL HOOKS (לא שוברים קומפילציה אם אין עדיין Storage/SystemLog/Scheduler)
    // ============================================================

    /** אם יש לך אחסון (S3/Cloudinary/CDN/FileSystem) — תוכל לממש Bean שמוחק URL פיזית. */
    public interface BackgroundStorageService {
        void deleteByUrl(String url);
    }

    /** אם יש לך Audit — תוכל לממש Bean שיקבל אירועים. */
    public interface BackgroundAuditSink {
        void record(String action, Long actorUserId, Long weddingId, Long backgroundId, String details);
    }

    private BackgroundStorageService storageService; // optional
    private BackgroundAuditSink auditSink;           // optional

    // optional direct logging via SystemLogRepository (if exists)
    private SystemLogRepository systemLogRepository;

    @Autowired(required = false)
    public void setStorageService(BackgroundStorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired(required = false)
    public void setAuditSink(BackgroundAuditSink auditSink) {
        this.auditSink = auditSink;
    }

    @Autowired(required = false)
    public void setSystemLogRepository(SystemLogRepository systemLogRepository) {
        this.systemLogRepository = systemLogRepository;
    }

    private void audit(String action, Long actorUserId, Long weddingId, Long backgroundId, String details) {
        // 1) External sink (if provided)
        if (auditSink != null) {
            try { auditSink.record(action, actorUserId, weddingId, backgroundId, details); }
            catch (Exception ignore) {}
            return;
        }

        // 2) SystemLogRepository (if exists) - safe reflection (no dependency on exact field names)
        if (systemLogRepository == null) return;

        try {
            SystemLog log = new SystemLog();

            // enums (best-effort)
            SystemActionType actionType = resolveEnumOrFirst(SystemActionType.class, action);
            SystemModule module = resolveEnumOrFirst(SystemModule.class, "WEDDING");
            SystemSeverityLevel severity = resolveEnumOrFirst(SystemSeverityLevel.class, "INFO");

            safeInvoke(log, "setTimestamp", LocalDateTime.class, LocalDateTime.now());
            safeInvoke(log, "setUserId", Long.class, actorUserId);
            safeInvoke(log, "setActionType", SystemActionType.class, actionType);
            safeInvoke(log, "setModule", SystemModule.class, module);
            safeInvoke(log, "setSeverity", SystemSeverityLevel.class, severity);
            safeInvoke(log, "setSuccess", boolean.class, true);
            safeInvoke(log, "setDetails", String.class, details);

            safeInvoke(log, "setRelatedEntityType", String.class, "WeddingBackground");
            safeInvoke(log, "setRelatedEntityId", Long.class, backgroundId);

            String ctx = "{"
                    + "\"weddingId\":" + (weddingId == null ? "null" : weddingId) + ","
                    + "\"backgroundId\":" + (backgroundId == null ? "null" : backgroundId) + ","
                    + "\"action\":\"" + escapeJson(action) + "\""
                    + "}";
            safeInvoke(log, "setContextJson", String.class, ctx);

            systemLogRepository.save(log);
        } catch (Exception ignore) {}
    }

    private static <E extends Enum<E>> E resolveEnumOrFirst(Class<E> enumClass, String name) {
        try {
            if (name != null) return Enum.valueOf(enumClass, name);
        } catch (Exception ignore) {}
        E[] all = enumClass.getEnumConstants();
        return (all != null && all.length > 0) ? all[0] : null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public WeddingBackgroundService(
            WeddingBackgroundRepository weddingBackgroundRepository,
            WeddingRepository weddingRepository,
            UserRepository userRepository,
            WeddingParticipantRepository weddingParticipantRepository
    ) {
        this.weddingBackgroundRepository = weddingBackgroundRepository;
        this.weddingRepository = weddingRepository;
        this.userRepository = userRepository;
        this.weddingParticipantRepository = weddingParticipantRepository;
    }

    // ============================================================
    // ✅ UI Resolution DTO
    // ============================================================

    public static class BackgroundResolution {
        private final BackgroundMode mode;     // DEFAULT / IMAGE / VIDEO
        private final BackgroundType type;     // IMAGE / VIDEO (null if DEFAULT)
        private final String url;              // null if DEFAULT
        private final Long backgroundId;       // null if DEFAULT
        private final boolean fallbackUsed;
        private final String message;          // optional toast

        public BackgroundResolution(BackgroundMode mode, BackgroundType type, String url, Long backgroundId,
                                    boolean fallbackUsed, String message) {
            this.mode = mode;
            this.type = type;
            this.url = url;
            this.backgroundId = backgroundId;
            this.fallbackUsed = fallbackUsed;
            this.message = message;
        }

        public BackgroundMode getMode() { return mode; }
        public BackgroundType getType() { return type; }
        public String getUrl() { return url; }
        public Long getBackgroundId() { return backgroundId; }
        public boolean isFallbackUsed() { return fallbackUsed; }
        public String getMessage() { return message; }
    }

    private BackgroundResolution defaultRes(String msg) {
        return new BackgroundResolution(
                BackgroundMode.DEFAULT,
                null,
                null,
                null,
                true,
                (msg == null ? "טעינת הרקע נכשלה — מוצג רקע ברירת מחדל." : msg)
        );
    }

    private BackgroundMode modeFromType(BackgroundType type) {
        if (type == null) return BackgroundMode.DEFAULT;
        return (type == BackgroundType.VIDEO) ? BackgroundMode.VIDEO : BackgroundMode.IMAGE;
    }

    // ============================================================
    // 10) Validation scaffold (no hard limits currently)
    // ============================================================

    private void validateUploadInputs(BackgroundType type, String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("backgroundUrl is required");
        }
        // תשתית בלבד: בעתיד אפשר לבדוק MIME/size/extension.
        // כרגע: לא מגבילים בפועל.
        if (type == null) {
            // ok -> default IMAGE at entity
        }
    }

    // ============================================================
    // 1) Resolve Wedding Background (weddingId) — IMAGE/VIDEO/DEFAULT
    // ============================================================

    @Transactional(readOnly = true)
    public BackgroundResolution resolveWeddingBackground(Long weddingId) {
        if (weddingId == null) return defaultRes("אין weddingId — מוצג רקע ברירת מחדל.");

        Wedding wedding = getWeddingOrThrow(weddingId);

        if (wedding.isCancelled() || !wedding.isActive()) {
            return defaultRes("החתונה אינה פעילה — מוצג רקע ברירת מחדל.");
        }

        BackgroundMode weddingMode = wedding.getBackgroundMode();
        if (weddingMode == null || weddingMode == BackgroundMode.DEFAULT) {
            return new BackgroundResolution(BackgroundMode.DEFAULT, null, null, null, false, null);
        }

        BackgroundType desiredType = (weddingMode == BackgroundMode.VIDEO)
                ? BackgroundType.VIDEO
                : BackgroundType.IMAGE;

        // (א) קודם activeBackgroundId אם קיים ותקין
        Long activeId = wedding.getActiveBackgroundId();
        if (activeId != null) {
            WeddingBackground wb = weddingBackgroundRepository.findById(activeId).orElse(null);
            if (isUsableForWedding(wb, weddingId) && wb.getType() == desiredType) {
                return new BackgroundResolution(
                        modeFromType(wb.getType()),
                        wb.getType(),
                        wb.getBackgroundUrl(),
                        wb.getId(),
                        false,
                        null
                );
            }
        }

        // (ב) fallback usable list מסודר (default first)
        List<WeddingBackground> usable = weddingBackgroundRepository.findUsableBackgroundsForWedding(weddingId);
        WeddingBackground chosen = (usable == null) ? null : usable.stream()
                .filter(wb -> wb != null && wb.getType() == desiredType)
                .findFirst()
                .orElse(null);

        if (chosen != null) {
            return new BackgroundResolution(
                    modeFromType(chosen.getType()),
                    chosen.getType(),
                    chosen.getBackgroundUrl(),
                    chosen.getId(),
                    false,
                    null
            );
        }

        return defaultRes(null);
    }

    // ============================================================
    // 2) Resolve Global Background — always usable/default
    // ============================================================

    @Transactional(readOnly = true)
    public BackgroundResolution resolveGlobalBackground() {
        WeddingBackground def = weddingBackgroundRepository
                .findFirstByGlobalTrueAndActiveTrueAndDeletedFalseAndUnsuitableFalseAndDefaultBackgroundTrueOrderByCreatedAtDesc()
                .orElse(null);

        if (def != null && def.isUsable() && def.isGlobal()) {
            return new BackgroundResolution(
                    modeFromType(def.getType()),
                    def.getType(),
                    def.getBackgroundUrl(),
                    def.getId(),
                    false,
                    null
            );
        }

        List<WeddingBackground> usable = weddingBackgroundRepository.findUsableGlobalBackgrounds();
        WeddingBackground chosen = (usable == null || usable.isEmpty()) ? null : usable.get(0);

        if (chosen != null && chosen.isUsable() && chosen.isGlobal()) {
            return new BackgroundResolution(
                    modeFromType(chosen.getType()),
                    chosen.getType(),
                    chosen.getBackgroundUrl(),
                    chosen.getId(),
                    false,
                    null
            );
        }

        return defaultRes(null);
    }

    // ============================================================
    // 3+4) Resolve by system mode (WeddingMode ↔ GlobalMode)
    // ============================================================

    @Transactional(readOnly = true)
    public BackgroundResolution resolveForMode(boolean isWeddingMode, Long weddingIdIfAny) {
        return isWeddingMode ? resolveWeddingBackground(weddingIdIfAny) : resolveGlobalBackground();
    }

    // ============================================================
    // 9) Get background by id (with permissions)
    // ============================================================

    @Transactional(readOnly = true)
    public WeddingBackground getBackground(Long actorUserId, Long backgroundId) {
        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        if (wb.isGlobal()) {
            requireAdmin(actorUserId);
        } else {
            Long weddingId = requireWeddingId(wb);
            requireOwnerOrAdmin(actorUserId, weddingId);
        }
        return wb;
    }

    // ============================================================
    // 5) Upload Wedding background (Owner/Admin) + optional set active/default
    // ============================================================

    public WeddingBackground uploadWeddingBackground(Long actorUserId,
                                                     Long weddingId,
                                                     BackgroundType type,
                                                     String url,
                                                     String title,
                                                     String description,
                                                     String metadataJson,
                                                     Integer displayOrder,
                                                     boolean setAsDefault,
                                                     boolean setAsActive) {

        requireOwnerOrAdmin(actorUserId, weddingId);
        validateUploadInputs(type, url);

        Wedding wedding = getWeddingOrThrow(weddingId);

        WeddingBackground wb = new WeddingBackground(
                wedding,
                false,
                (type == null ? BackgroundType.IMAGE : type),
                url.trim(),
                false
        );
        wb.setTitle(title);
        wb.setDescription(description);
        wb.setMetadataJson(metadataJson);
        wb.setDisplayOrder(displayOrder);
        wb.setUploadedByUserId(actorUserId);

        wb.setActive(true);
        wb.setDeleted(false);
        wb.setUnsuitable(false);

        if (setAsDefault) {
            weddingBackgroundRepository.clearDefaultForWedding(weddingId);
            wb.setDefaultBackground(true);
        }

        wb = weddingBackgroundRepository.save(wb);

        if (setAsActive) {
            setActiveWeddingBackgroundInternal(actorUserId, wedding, wb);
        } else if (setAsDefault && wedding.getActiveBackgroundId() == null) {
            // אם אין active — ברירת מחדל הופכת גם ל-active כדי למנוע מצב “יש default אבל לא נבחר”
            setActiveWeddingBackgroundInternal(actorUserId, wedding, wb);
        }

        audit("WEDDING_BG_UPLOAD", actorUserId, weddingId, wb.getId(), "type=" + wb.getType());
        return wb;
    }

    // ============================================================
    // 6) Upload Global background (Admin) + optional default
    // ============================================================

    public WeddingBackground uploadGlobalBackground(Long actorUserId,
                                                    BackgroundType type,
                                                    String url,
                                                    String title,
                                                    String description,
                                                    String metadataJson,
                                                    Integer displayOrder,
                                                    boolean setAsDefault) {

        requireAdmin(actorUserId);
        validateUploadInputs(type, url);

        WeddingBackground wb = new WeddingBackground(
                null,
                true,
                (type == null ? BackgroundType.IMAGE : type),
                url.trim(),
                false
        );
        wb.setTitle(title);
        wb.setDescription(description);
        wb.setMetadataJson(metadataJson);
        wb.setDisplayOrder(displayOrder);
        wb.setUploadedByUserId(actorUserId);

        wb.setActive(true);
        wb.setDeleted(false);
        wb.setUnsuitable(false);

        if (setAsDefault) {
            weddingBackgroundRepository.clearDefaultForGlobal();
            wb.setDefaultBackground(true);
        }

        wb = weddingBackgroundRepository.save(wb);
        audit("GLOBAL_BG_UPLOAD", actorUserId, null, wb.getId(), "type=" + wb.getType());
        return wb;
    }

    // ============================================================
    // 9) Update metadata/details (Admin / Owner for wedding background)
    // ============================================================

    public WeddingBackground updateBackgroundDetails(Long actorUserId,
                                                     Long backgroundId,
                                                     String title,
                                                     String description,
                                                     String metadataJson,
                                                     Integer displayOrder) {

        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        if (wb.isGlobal()) {
            requireAdmin(actorUserId);
        } else {
            Long weddingId = requireWeddingId(wb);
            requireOwnerOrAdmin(actorUserId, weddingId);
        }

        wb.setTitle(title);
        wb.setDescription(description);
        wb.setMetadataJson(metadataJson);
        wb.setDisplayOrder(displayOrder);

        wb = weddingBackgroundRepository.save(wb);
        audit("BG_UPDATE_DETAILS", actorUserId, wb.isGlobal() ? null : requireWeddingId(wb), wb.getId(), "ok");
        return wb;
    }

    // ============================================================
    // 9) Activate/Deactivate (without delete) — for management screens
    // ============================================================

    public WeddingBackground setActiveFlag(Long actorUserId, Long backgroundId, boolean active) {
        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        if (wb.isGlobal()) {
            requireAdmin(actorUserId);
        } else {
            Long weddingId = requireWeddingId(wb);
            requireOwnerOrAdmin(actorUserId, weddingId);
        }

        if (active && (wb.isDeleted() || wb.isUnsuitable())) {
            throw new IllegalStateException("Cannot activate deleted/unsuitable background");
        }

        wb.setActive(active);

        if (!active) {
            wb.setDefaultBackground(false);
            wb = weddingBackgroundRepository.save(wb);

            if (!wb.isGlobal()) {
                Long weddingId = requireWeddingId(wb);
                resetWeddingIfActiveBackground(actorUserId, weddingId, wb.getId());
            }

            audit("BG_DEACTIVATE", actorUserId, wb.isGlobal()? null : requireWeddingId(wb), wb.getId(), "active=false");
            return wb;
        }

        wb = weddingBackgroundRepository.save(wb);
        audit("BG_ACTIVATE", actorUserId, wb.isGlobal()? null : requireWeddingId(wb), wb.getId(), "active=true");
        return wb;
    }

    // ============================================================
    // 18) Default handling — Wedding (Owner/Admin)
    // ============================================================

    public WeddingBackground setDefaultWeddingBackground(Long actorUserId, Long weddingId, Long backgroundId) {
        requireOwnerOrAdmin(actorUserId, weddingId);

        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        if (wb.isGlobal()) throw new IllegalArgumentException("Cannot set global as wedding default");
        if (!belongsToWedding(wb, weddingId)) throw new IllegalArgumentException("Background not in this wedding");
        if (!wb.isUsable()) throw new IllegalStateException("Background is not usable");

        // ברירת מחדל חייבת להיות גם active
        wb.setActive(true);

        weddingBackgroundRepository.clearDefaultForWedding(weddingId);
        wb.setDefaultBackground(true);

        wb = weddingBackgroundRepository.save(wb);
        audit("WEDDING_BG_SET_DEFAULT", actorUserId, weddingId, wb.getId(), "ok");
        return wb;
    }

    // ============================================================
    // 18) Default handling — Global (Admin)
    // ============================================================

    public WeddingBackground setDefaultGlobalBackground(Long actorUserId, Long backgroundId) {
        requireAdmin(actorUserId);

        WeddingBackground wb = getBackgroundOrThrow(backgroundId);
        if (!wb.isGlobal()) throw new IllegalArgumentException("Not a global background");
        if (!wb.isUsable()) throw new IllegalStateException("Background is not usable");

        wb.setActive(true);

        weddingBackgroundRepository.clearDefaultForGlobal();
        wb.setDefaultBackground(true);

        wb = weddingBackgroundRepository.save(wb);
        audit("GLOBAL_BG_SET_DEFAULT", actorUserId, null, wb.getId(), "ok");
        return wb;
    }

    // ============================================================
    // 9) Set Wedding active background (Owner/Admin) + updates Wedding.backgroundMode
    // ============================================================

    public void setActiveWeddingBackground(Long actorUserId, Long weddingId, Long backgroundId) {
        requireOwnerOrAdmin(actorUserId, weddingId);

        Wedding wedding = getWeddingOrThrow(weddingId);
        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        if (wb.isGlobal()) throw new IllegalArgumentException("Cannot set global as wedding active");
        if (!belongsToWedding(wb, weddingId)) throw new IllegalArgumentException("Background not in this wedding");
        if (!wb.isUsable()) throw new IllegalStateException("Background is not usable");

        wb.setActive(true);
        wb = weddingBackgroundRepository.save(wb);

        setActiveWeddingBackgroundInternal(actorUserId, wedding, wb);
        audit("WEDDING_BG_SET_ACTIVE", actorUserId, weddingId, wb.getId(), "mode=" + wedding.getBackgroundMode());
    }

    private void setActiveWeddingBackgroundInternal(Long actorUserId, Wedding wedding, WeddingBackground wb) {
        wedding.setActiveBackgroundId(wb.getId());
        wedding.setBackgroundMode(modeFromType(wb.getType())); // IMAGE/VIDEO
        safeInvoke(wedding, "setUpdatedByUserId", Long.class, actorUserId);
        weddingRepository.save(wedding);
    }

    // ============================================================
    // 3) Explicit wedding background mode set (Owner/Admin)
    // ============================================================

    public void setWeddingBackgroundMode(Long actorUserId, Long weddingId, BackgroundMode mode) {
        requireOwnerOrAdmin(actorUserId, weddingId);

        Wedding wedding = getWeddingOrThrow(weddingId);
        BackgroundMode m = (mode == null ? BackgroundMode.DEFAULT : mode);

        wedding.setBackgroundMode(m);

        if (m == BackgroundMode.DEFAULT) {
            wedding.setActiveBackgroundId(null);
        }

        safeInvoke(wedding, "setUpdatedByUserId", Long.class, actorUserId);
        weddingRepository.save(wedding);

        audit("WEDDING_BG_SET_MODE", actorUserId, weddingId, null, "mode=" + m);
    }

    // ============================================================
    // 7) Mark unsuitable (Admin only) + immediate removal + wedding fallback
    // ============================================================

    public WeddingBackground markUnsuitable(Long actorUserId, Long backgroundId, String reason) {
        requireAdmin(actorUserId);

        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        wb.setUnsuitable(true);
        wb.setUnsuitableAt(LocalDateTime.now());
        wb.setUnsuitableByUserId(actorUserId);
        wb.setUnsuitableReason(isBlank(reason) ? "Unsuitable background" : reason.trim());

        wb.setActive(false);
        wb.setDefaultBackground(false);

        wb = weddingBackgroundRepository.save(wb);

        if (!wb.isGlobal()) {
            Long weddingId = requireWeddingId(wb);
            resetWeddingIfActiveBackground(actorUserId, weddingId, wb.getId());
            audit("WEDDING_BG_MARK_UNSUITABLE", actorUserId, weddingId, wb.getId(), wb.getUnsuitableReason());
        } else {
            audit("GLOBAL_BG_MARK_UNSUITABLE", actorUserId, null, wb.getId(), wb.getUnsuitableReason());
        }

        return wb;
    }

    // ============================================================
    // 8) Soft delete + wedding fallback
    // ============================================================

    public WeddingBackground softDelete(Long actorUserId, Long backgroundId, String reason) {
        WeddingBackground wb = getBackgroundOrThrow(backgroundId);

        if (wb.isGlobal()) {
            requireAdmin(actorUserId);
        } else {
            Long weddingId = requireWeddingId(wb);
            requireOwnerOrAdmin(actorUserId, weddingId);
        }

        if (!wb.isDeleted()) {
            wb.setDeleted(true);
            wb.setDeletedAt(LocalDateTime.now());
        }
        wb.setDeletedByUserId(actorUserId);
        wb.setDeletedReason(isBlank(reason) ? "Deleted" : reason.trim());

        wb.setActive(false);
        wb.setDefaultBackground(false);

        wb = weddingBackgroundRepository.save(wb);

        if (!wb.isGlobal()) {
            Long weddingId = requireWeddingId(wb);
            resetWeddingIfActiveBackground(actorUserId, weddingId, wb.getId());
            audit("WEDDING_BG_SOFT_DELETE", actorUserId, weddingId, wb.getId(), wb.getDeletedReason());
        } else {
            audit("GLOBAL_BG_SOFT_DELETE", actorUserId, null, wb.getId(), wb.getDeletedReason());
        }

        return wb;
    }

    private void resetWeddingIfActiveBackground(Long actorUserId, Long weddingId, Long backgroundId) {
        Wedding w = getWeddingOrThrow(weddingId);
        if (Objects.equals(w.getActiveBackgroundId(), backgroundId)) {
            w.setActiveBackgroundId(null);
            w.setBackgroundMode(BackgroundMode.DEFAULT);
            safeInvoke(w, "setUpdatedByUserId", Long.class, actorUserId);
            weddingRepository.save(w);
        }
    }

    // ============================================================
    // 15) Cleanup Job — hard delete soft-deleted before cutoff (+ optional storage deletion)
    // ============================================================

    public int hardDeleteSoftDeletedBefore(LocalDateTime cutoff) {
        if (cutoff == null) throw new IllegalArgumentException("cutoff is null");

        List<WeddingBackground> toDelete = weddingBackgroundRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);
        if (toDelete == null || toDelete.isEmpty()) return 0;

        // optional physical delete
        if (storageService != null) {
            for (WeddingBackground wb : toDelete) {
                if (wb != null && wb.getBackgroundUrl() != null) {
                    try { storageService.deleteByUrl(wb.getBackgroundUrl()); }
                    catch (Exception ignore) {}
                }
            }
        }

        weddingBackgroundRepository.deleteAll(toDelete);

        audit("BG_HARD_DELETE_BATCH", null, null, null, "count=" + toDelete.size());
        return toDelete.size();
    }

    public int hardDeleteSoftDeletedOlderThanDays(int days) {
        if (days <= 0) throw new IllegalArgumentException("days must be > 0");
        return hardDeleteSoftDeletedBefore(LocalDateTime.now().minusDays(days));
    }

    // ============================================================
    // 9+16+17) Lists & filtering for management
    // ============================================================

    @Transactional(readOnly = true)
    public List<WeddingBackground> listWeddingBackgrounds(Long actorUserId, Long weddingId) {
        requireOwnerOrAdmin(actorUserId, weddingId);
        return weddingBackgroundRepository.findByWedding_IdAndDeletedFalseOrderByCreatedAtDesc(weddingId);
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listWeddingBackgroundsUsable(Long actorUserId, Long weddingId) {
        requireOwnerOrAdmin(actorUserId, weddingId);
        return weddingBackgroundRepository.findByWedding_IdAndActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc(weddingId);
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listWeddingBackgroundsByActive(Long actorUserId, Long weddingId, boolean active) {
        requireOwnerOrAdmin(actorUserId, weddingId);
        return weddingBackgroundRepository.findByWedding_IdAndActiveAndDeletedFalseOrderByCreatedAtDesc(weddingId, active);
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listWeddingBackgroundsByType(Long actorUserId, Long weddingId, BackgroundType type) {
        requireOwnerOrAdmin(actorUserId, weddingId);
        return weddingBackgroundRepository.findByWedding_IdAndTypeAndDeletedFalseOrderByCreatedAtDesc(weddingId, type);
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listGlobalBackgrounds(Long actorUserId) {
        requireAdmin(actorUserId);
        return weddingBackgroundRepository.findByGlobalTrueAndDeletedFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listGlobalUsable(Long actorUserId) {
        requireAdmin(actorUserId);
        return weddingBackgroundRepository.findByGlobalTrueAndActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listGlobalByType(Long actorUserId, BackgroundType type) {
        requireAdmin(actorUserId);
        return weddingBackgroundRepository.findByGlobalTrueAndTypeAndDeletedFalseOrderByCreatedAtDesc(type);
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listSystemUsable(Long actorUserId) {
        requireAdmin(actorUserId);
        return weddingBackgroundRepository.findByActiveTrueAndDeletedFalseAndUnsuitableFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listUnsuitable(Long actorUserId) {
        requireAdmin(actorUserId);
        return weddingBackgroundRepository.findByUnsuitableTrueAndDeletedFalseOrderByUnsuitableAtDesc();
    }

    @Transactional(readOnly = true)
    public List<WeddingBackground> listDeletedArchive(Long actorUserId) {
        requireAdmin(actorUserId);
        return weddingBackgroundRepository.findByDeletedTrueOrderByDeletedAtDesc();
    }

    // ✅ Admin “רואה הכל” (גלובלי + חתונה), כולל/בלי מחוקים
    @Transactional(readOnly = true)
    public List<WeddingBackground> listAllForAdmin(Long actorUserId, boolean includeDeleted) {
        requireAdmin(actorUserId);

        List<WeddingBackground> all = weddingBackgroundRepository.findAll();
        if (all == null) return List.of();

        return all.stream()
                .filter(Objects::nonNull)
                .filter(wb -> includeDeleted || !wb.isDeleted())
                .sorted(Comparator.comparing(WeddingBackground::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    // ============================================================
    // 13) Stats
    // ============================================================

    public static class BackgroundStats {
        public final long globalCount;
        public final long weddingCount;
        public final long usableCount;
        public final Long weddingId;
        public final Long weddingTotal;
        public final Long weddingUsable;

        public BackgroundStats(long globalCount, long weddingCount, long usableCount,
                               Long weddingId, Long weddingTotal, Long weddingUsable) {
            this.globalCount = globalCount;
            this.weddingCount = weddingCount;
            this.usableCount = usableCount;
            this.weddingId = weddingId;
            this.weddingTotal = weddingTotal;
            this.weddingUsable = weddingUsable;
        }
    }

    @Transactional(readOnly = true)
    public BackgroundStats getSystemStats(Long actorUserId) {
        requireAdmin(actorUserId);
        long g = weddingBackgroundRepository.countByGlobalTrueAndDeletedFalse();
        long w = weddingBackgroundRepository.countByGlobalFalseAndDeletedFalse();
        long u = weddingBackgroundRepository.countByActiveTrueAndDeletedFalseAndUnsuitableFalse();
        return new BackgroundStats(g, w, u, null, null, null);
    }

    @Transactional(readOnly = true)
    public BackgroundStats getWeddingStats(Long actorUserId, Long weddingId) {
        requireOwnerOrAdmin(actorUserId, weddingId);

        long g = weddingBackgroundRepository.countByGlobalTrueAndDeletedFalse();
        long w = weddingBackgroundRepository.countByGlobalFalseAndDeletedFalse();
        long u = weddingBackgroundRepository.countByActiveTrueAndDeletedFalseAndUnsuitableFalse();

        long total = weddingBackgroundRepository.countByWedding_IdAndDeletedFalse(weddingId);
        long usable = weddingBackgroundRepository.countByWedding_IdAndActiveTrueAndDeletedFalseAndUnsuitableFalse(weddingId);

        return new BackgroundStats(g, w, u, weddingId, total, usable);
    }

    // ============================================================
    // INTERNAL helpers
    // ============================================================

    private Wedding getWeddingOrThrow(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found: " + weddingId));
    }

    private WeddingBackground getBackgroundOrThrow(Long backgroundId) {
        if (backgroundId == null) throw new IllegalArgumentException("backgroundId is null");
        return weddingBackgroundRepository.findById(backgroundId)
                .orElseThrow(() -> new IllegalArgumentException("Background not found: " + backgroundId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private Long requireWeddingId(WeddingBackground wb) {
        if (wb.getWedding() == null || wb.getWedding().getId() == null) {
            throw new IllegalStateException("Wedding background without weddingId");
        }
        return wb.getWedding().getId();
    }

    private boolean belongsToWedding(WeddingBackground wb, Long weddingId) {
        return wb != null
                && !wb.isGlobal()
                && wb.getWedding() != null
                && Objects.equals(wb.getWedding().getId(), weddingId);
    }

    private boolean isUsableForWedding(WeddingBackground wb, Long weddingId) {
        return belongsToWedding(wb, weddingId) && wb.isUsable();
    }

    private boolean isAdmin(User u) {
        if (u == null) return false;
        try {
            Method m = u.getClass().getMethod("isAdmin");
            Object v = m.invoke(u);
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isEventManager(User u) {
        if (u == null) return false;
        try {
            Method m = u.getClass().getMethod("isEventManager");
            Object v = m.invoke(u);
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    private void requireAdmin(Long actorUserId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        User actor = getUserOrThrow(actorUserId);
        if (!isAdmin(actor)) throw new SecurityException("Not allowed (admin required)");
    }

    private void requireOwnerOrAdmin(Long actorUserId, Long weddingId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");

        User actor = getUserOrThrow(actorUserId);
        if (isAdmin(actor)) return;

        Wedding wedding = getWeddingOrThrow(weddingId);

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId != null && ownerId.equals(actorUserId)) return;

        if (isEventManager(actor)) return;

        boolean hasRole = weddingParticipantRepository.existsByWedding_IdAndUser_IdAndBlockedFalseAndRoleInWeddingIn(
                weddingId, actorUserId, OWNER_ROLES
        );
        if (hasRole) return;

        throw new SecurityException("Not allowed (owner/admin required)");
    }

    private void safeInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Exception ignore) {}
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}