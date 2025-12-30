package com.example.myproject.service;

import com.example.myproject.model.User;
import com.example.myproject.model.UserAction;
import com.example.myproject.model.enums.UserActionCategory;
import com.example.myproject.model.enums.UserActionType;
import com.example.myproject.model.enums.WeddingMode;
import com.example.myproject.repository.UserActionRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.System.SystemSettingsService;
import com.example.myproject.service.User.UserStateEvaluatorService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserInteractionService {

    // =====================================================
    // ✅ Settings keys (SystemSettings)
    // =====================================================

    private static final String K_UNDO_DISLIKE_MINUTES = "userAction.dislike.undoMinutes"; // default 10
    private static final String K_SUPERLIKE_DAILY_CAP  = "userAction.superLike.dailyCap";  // default 5

    private static final String K_FREEZE_DEFAULT_DAYS  = "userAction.freeze.defaultDays";  // default 14
    private static final String K_FREEZE_MAX_DAYS      = "userAction.freeze.maxDays";      // default 30

    private static final String K_MAX_SCAN_ACTIVE_ACTIONS = "userAction.scan.maxActive";  // default 2000

    // =====================================================
    // ✅ Dependencies
    // =====================================================

    private final UserRepository userRepo;
    private final UserActionRepository actionRepo;

    private final UserActionService userActionService;          // recorder/audit/rules
    private final UserStateEvaluatorService userStateEvaluator; // state gate
    private final SystemSettingsService settings;

    public UserInteractionService(UserRepository userRepo,
                                  UserActionRepository actionRepo,
                                  UserActionService userActionService,
                                  UserStateEvaluatorService userStateEvaluator,
                                  SystemSettingsService settings) {
        this.userRepo = userRepo;
        this.actionRepo = actionRepo;
        this.userActionService = userActionService;
        this.userStateEvaluator = userStateEvaluator;
        this.settings = settings;
    }

    // =====================================================
    // ✅ Context + Result DTOs
    // =====================================================

    public static class InteractionContext {
        public WeddingMode mode;              // default GLOBAL
        public Long weddingId;
        public Long originWeddingId;
        public Long matchId;
        public Long actionGroupId;

        public String source = "user";        // user/admin/system/ai
        public String ipAddress;
        public String deviceInfo;

        public boolean liveWeddingRules = false;        // “חתונה חיה”
        public boolean enforceUserStateGate = true;     // gate ב-global
    }

    public static class InteractionResult {
        private final UserAction savedAction;
        private final boolean mutualPositiveNow;
        private final String message;

        public InteractionResult(UserAction savedAction, boolean mutualPositiveNow, String message) {
            this.savedAction = savedAction;
            this.mutualPositiveNow = mutualPositiveNow;
            this.message = message;
        }

        public UserAction getSavedAction() { return savedAction; }
        public boolean isMutualPositiveNow() { return mutualPositiveNow; }
        public String getMessage() { return message; }
    }

    // =====================================================
    // ✅ DOMAIN APIs
    // =====================================================

    public InteractionResult like(Long actorId, Long targetId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);

        // preload actives once (performance)
        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        ActiveIndex targetIdx = loadActorActiveIndex(targetId);

        enforceNotBlockedBetween(actorIdx, targetIdx, actorId, targetId);
        enforceCanPerformPositiveAction(actor, target, ctx, false);

        // Like cancels dislike/freeze
        deactivateIfExists(actorIdx, actorId, targetId, UserActionType.DISLIKE, UserActionType.FREEZE);

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.LIKE;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "LIKE", null);

        // optional gate enforcement inside recorder (keeps future consistency)
        cmd.requireCanLike = (!isLiveWedding(ctx));

        UserAction saved = userActionService.record(cmd);

        // ✅ CRITICAL FIX: update index with the newly created action (otherwise mutual is usually false)
        actorIdx.put(saved);

        boolean mutual = isMutualLike(actorIdx, targetIdx, actorId, targetId);
        return new InteractionResult(saved, mutual, mutual ? "Mutual positive detected" : "Like recorded");
    }

    public InteractionResult dislike(Long actorId, Long targetId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(actorId);
        getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        ActiveIndex targetIdx = loadActorActiveIndex(targetId);

        enforceNotBlockedBetween(actorIdx, targetIdx, actorId, targetId);
        enforceCanPerformNeutralAction(actorId, ctx);

        // Dislike cancels like/freeze
        deactivateIfExists(actorIdx, actorId, targetId, UserActionType.LIKE, UserActionType.FREEZE);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("undoMinutes", String.valueOf(getIntSetting(K_UNDO_DISLIKE_MINUTES, 10)));

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.DISLIKE;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "DISLIKE", extra);

        cmd.requireCanLike = (!isLiveWedding(ctx));

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Dislike recorded");
    }

    public boolean undoDislike(Long actorId, Long targetId) {
        enforceBasicGuards(actorId, targetId);

        int undoMinutes = getIntSetting(K_UNDO_DISLIKE_MINUTES, 10);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, undoMinutes));

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        UserAction dislike = actorIdx.get(UserActionType.DISLIKE, targetId);

        if (dislike == null) return false;
        if (dislike.getCreatedAt() == null || dislike.getCreatedAt().isBefore(cutoff)) return false;

        dislike.setActive(false);
        dislike.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(dislike);
        actorIdx.markInactive(UserActionType.DISLIKE, targetId);
        return true;
    }

    public InteractionResult freeze(Long actorId, Long targetId, Integer daysNullable, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(actorId);
        getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        ActiveIndex targetIdx = loadActorActiveIndex(targetId);

        enforceNotBlockedBetween(actorIdx, targetIdx, actorId, targetId);
        enforceCanPerformNeutralAction(actorId, ctx);

        // Freeze cancels like/dislike
        deactivateIfExists(actorIdx, actorId, targetId, UserActionType.LIKE, UserActionType.DISLIKE);

        int defDays = getIntSetting(K_FREEZE_DEFAULT_DAYS, 14);
        int maxDays = getIntSetting(K_FREEZE_MAX_DAYS, 30);

        int days = (daysNullable == null ? defDays : daysNullable);
        days = Math.max(1, Math.min(days, maxDays));

        LocalDateTime until = LocalDateTime.now().plusDays(days);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("freezeDays", String.valueOf(days));
        extra.put("freezeUntil", until.toString());

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.FREEZE;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "FREEZE", extra);

        cmd.requireCanLike = (!isLiveWedding(ctx));

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Freeze recorded");
    }

    public boolean unfreeze(Long actorId, Long targetId, String reason) {
        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        UserAction fr = actorIdx.get(UserActionType.FREEZE, targetId);
        if (fr == null) return false;

        fr.setActive(false);
        if (reason != null && !reason.isBlank()) fr.setReason(reason.trim());
        fr.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(fr);
        actorIdx.markInactive(UserActionType.FREEZE, targetId);

        // audit UNFREEZE action record (optional but useful)
        try {
            InteractionContext ctx = normalizeCtx(null);
            UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
            cmd.actionType = UserActionType.UNFREEZE;
            cmd.category = mapCategory(cmd.actionType);
            cmd.reason = (reason != null && !reason.isBlank()) ? reason.trim() : null;
            cmd.metadata = buildMetadata(ctx, "UNFREEZE", null);
            cmd.enforceRateLimit = false;
            cmd.enforceCooldownSameType = false;
            cmd.preventDuplicates = false;
            userActionService.record(cmd);
        } catch (Exception ignore) {}

        return true;
    }

    /**
     * SUPER_LIKE לא קיים אצלך ב-enum:
     * נשמר כ-LIKE + metadata superLike=true
     */
    public InteractionResult superLike(Long actorId, Long targetId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        ActiveIndex targetIdx = loadActorActiveIndex(targetId);

        enforceNotBlockedBetween(actorIdx, targetIdx, actorId, targetId);
        enforceCanPerformPositiveAction(actor, target, ctx, true);
        enforceSuperLikeDailyCap(actorId);

        // SuperLike cancels dislike/freeze/like (replace)
        deactivateIfExists(actorIdx, actorId, targetId, UserActionType.DISLIKE, UserActionType.FREEZE, UserActionType.LIKE);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("superLike", "true");
        extra.put("dailyCap", String.valueOf(getIntSetting(K_SUPERLIKE_DAILY_CAP, 5)));

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.LIKE;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "SUPERLIKE", extra);

        cmd.requireCanLike = (!isLiveWedding(ctx));

        UserAction saved = userActionService.record(cmd);

        // ✅ CRITICAL FIX: update index so mutual is computed correctly
        actorIdx.put(saved);

        boolean mutual = isMutualLike(actorIdx, targetIdx, actorId, targetId);
        return new InteractionResult(saved, mutual, mutual ? "Mutual positive detected (via superlike)" : "SuperLike recorded");
    }

    public InteractionResult block(Long actorId, Long targetId, InteractionContext ctx, String reason) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(actorId);
        getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        ActiveIndex targetIdx = loadActorActiveIndex(targetId);

        // cancel relationship actions both directions
        deactivateRelationshipBothDirections(actorId, targetId, actorIdx, targetIdx);

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.BLOCK;
        cmd.category = mapCategory(cmd.actionType);
        cmd.reason = (reason != null && !reason.isBlank()) ? reason.trim() : null;
        cmd.metadata = buildMetadata(ctx, "BLOCK", null);

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Blocked");
    }

    public boolean unblock(Long actorId, Long targetId, String reason) {
        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        UserAction bl = actorIdx.get(UserActionType.BLOCK, targetId);
        if (bl == null) return false;

        bl.setActive(false);
        if (reason != null && !reason.isBlank()) bl.setReason(reason.trim());
        bl.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(bl);
        actorIdx.markInactive(UserActionType.BLOCK, targetId);

        // audit UNBLOCK
        try {
            InteractionContext ctx = normalizeCtx(null);
            UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
            cmd.actionType = UserActionType.UNBLOCK;
            cmd.category = mapCategory(cmd.actionType);
            cmd.reason = (reason != null && !reason.isBlank()) ? reason.trim() : null;
            cmd.metadata = buildMetadata(ctx, "UNBLOCK", null);
            cmd.enforceRateLimit = false;
            cmd.enforceCooldownSameType = false;
            cmd.preventDuplicates = false;
            userActionService.record(cmd);
        } catch (Exception ignore) {}

        return true;
    }

    /**
     * VIEW בלבד (אין “מי צפה”), רק count
     */
    public long viewProfile(Long viewerId, Long profileUserId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        if (Objects.equals(viewerId, profileUserId)) {
            return getProfileViewsCount(profileUserId);
        }

        getUserOrThrow(viewerId);
        getUserOrThrow(profileUserId);

        ActiveIndex viewerIdx = loadActorActiveIndex(viewerId);
        ActiveIndex profIdx = loadActorActiveIndex(profileUserId);
        enforceNotBlockedBetween(viewerIdx, profIdx, viewerId, profileUserId);

        UserActionService.CreateActionCommand cmd = baseCmd(viewerId, profileUserId, ctx);
        cmd.actionType = UserActionType.VIEW;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "PROFILE_VIEW", null);

        cmd.enforceCooldownSameType = false;

        userActionService.record(cmd);
        return getProfileViewsCount(profileUserId);
    }

    @Transactional(readOnly = true)
    public long getProfileViewsCount(Long profileUserId) {
        if (profileUserId == null) return 0;
        return actionRepo.countByTarget_IdAndActionType(profileUserId, UserActionType.VIEW);
    }

    /**
     * IMPORTANT:
     * לפי אפיון 2025 - דיווח אמיתי חייב ליצור UserReport (מודול UserReport).
     * כאן נשאיר action "signal" בלבד לצורכי audit/trace.
     */
    public InteractionResult reportUser(Long reporterId, Long targetId, InteractionContext ctx, String reportType, String details) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(reporterId);
        getUserOrThrow(targetId);

        enforceBasicGuards(reporterId, targetId);

        ActiveIndex repIdx = loadActorActiveIndex(reporterId);
        ActiveIndex tarIdx = loadActorActiveIndex(targetId);
        enforceNotBlockedBetween(repIdx, tarIdx, reporterId, targetId);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("report", "true");
        if (reportType != null && !reportType.isBlank()) extra.put("reportType", reportType.trim());
        if (details != null && !details.isBlank()) extra.put("details", safeShort(details.trim(), 800));

        UserActionService.CreateActionCommand cmd = baseCmd(reporterId, targetId, ctx);
        cmd.actionType = UserActionType.UNKNOWN;
        cmd.category = UserActionCategory.SYSTEM;
        cmd.metadata = buildMetadata(ctx, "REPORT", extra);

        cmd.enforceRateLimit = false;
        cmd.enforceCooldownSameType = false;

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Report signal recorded (UserReport must be created separately)");
    }

    // =====================================================
    // ✅ Cancel APIs
    // =====================================================

    public boolean cancelAction(Long actorId, Long targetId, UserActionType type, String reason) {
        enforceBasicGuards(actorId, targetId);
        if (type == null) throw new IllegalArgumentException("type is null");

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        UserAction act = actorIdx.get(type, targetId);
        if (act == null) return false;

        act.setActive(false);
        if (reason != null && !reason.isBlank()) act.setReason(reason.trim());
        act.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(act);
        actorIdx.markInactive(type, targetId);
        return true;
    }

    public boolean cancelLike(Long actorId, Long targetId, String reason) {
        return cancelAction(actorId, targetId, UserActionType.LIKE, reason);
    }

    public boolean cancelSuperLike(Long actorId, Long targetId, String reason) {
        enforceBasicGuards(actorId, targetId);

        ActiveIndex actorIdx = loadActorActiveIndex(actorId);
        UserAction like = actorIdx.get(UserActionType.LIKE, targetId);
        if (like == null) return false;
        if (!isSuperLike(like)) return false;

        like.setActive(false);
        if (reason != null && !reason.isBlank()) like.setReason(reason.trim());
        like.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(like);
        actorIdx.markInactive(UserActionType.LIKE, targetId);
        return true;
    }

    public boolean cancelFreeze(Long actorId, Long targetId, String reason) {
        return cancelAction(actorId, targetId, UserActionType.FREEZE, reason);
    }

    public boolean cancelDislike(Long actorId, Long targetId, String reason) {
        return cancelAction(actorId, targetId, UserActionType.DISLIKE, reason);
    }

    // =====================================================
    // ✅ Lists (optimized)
    // =====================================================

    @Transactional(readOnly = true)
    public List<Long> getLikesGiven(Long actorId, int limit) {
        int lim = clamp(limit, 1, 500);
        ActiveIndex idx = loadActorActiveIndex(actorId);

        return idx.targetsOfType(UserActionType.LIKE).stream()
                .filter(tid -> {
                    UserAction ua = idx.get(UserActionType.LIKE, tid);
                    return ua != null && ua.isActive() && !isSuperLike(ua);
                })
                .limit(lim)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getSuperLikesGiven(Long actorId, int limit) {
        int lim = clamp(limit, 1, 500);
        ActiveIndex idx = loadActorActiveIndex(actorId);

        return idx.targetsOfType(UserActionType.LIKE).stream()
                .filter(tid -> {
                    UserAction ua = idx.get(UserActionType.LIKE, tid);
                    return ua != null && ua.isActive() && isSuperLike(ua);
                })
                .limit(lim)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getDislikesGiven(Long actorId, int limit) {
        int lim = clamp(limit, 1, 500);
        ActiveIndex idx = loadActorActiveIndex(actorId);
        return idx.targetsOfType(UserActionType.DISLIKE).stream().limit(lim).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getFreezesGiven(Long actorId, int limit) {
        try { cleanupExpiredFreezesForActor(actorId); } catch (Exception ignore) {}
        int lim = clamp(limit, 1, 500);
        ActiveIndex idx = loadActorActiveIndex(actorId);
        return idx.targetsOfType(UserActionType.FREEZE).stream().limit(lim).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getLikesReceived(Long userId, int limit) {
        int lim = clamp(limit, 1, 500);
        return getActorsByActiveTypeOnTarget(userId, UserActionType.LIKE, lim);
    }

    /**
     * Mutual = likesGiven ∩ likesReceived (fast, no loops calling DB)
     */
    @Transactional(readOnly = true)
    public List<Long> getMutualPositiveTargets(Long actorId, int limit) {
        int lim = clamp(limit, 1, 500);

        ActiveIndex idx = loadActorActiveIndex(actorId);
        Set<Long> given = new LinkedHashSet<>(idx.targetsOfType(UserActionType.LIKE));
        if (given.isEmpty()) return Collections.emptyList();

        Set<Long> receivedActors = new LinkedHashSet<>(getActorsByActiveTypeOnTarget(actorId, UserActionType.LIKE, 500));

        List<Long> out = new ArrayList<>();
        for (Long t : given) {
            if (t == null) continue;
            if (receivedActors.contains(t)) {
                out.add(t);
                if (out.size() >= lim) break;
            }
        }
        return out;
    }

    // =====================================================
    // ✅ Cleanup expired freezes
    // =====================================================

    public int cleanupExpiredFreezesForActor(Long actorId) {
        if (actorId == null) return 0;

        ActiveIndex idx = loadActorActiveIndex(actorId);
        List<UserAction> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Long tid : idx.targetsOfType(UserActionType.FREEZE)) {
            UserAction ua = idx.get(UserActionType.FREEZE, tid);
            if (ua == null || !ua.isActive() || ua.getMetadata() == null) continue;

            LocalDateTime until = tryParseFreezeUntil(ua.getMetadata());
            if (until != null && until.isBefore(now)) {
                ua.setActive(false);
                ua.setUpdatedAt(now);
                toSave.add(ua);
                idx.markInactive(UserActionType.FREEZE, tid);
            }
        }

        if (!toSave.isEmpty()) actionRepo.saveAll(toSave);
        return toSave.size();
    }

    private LocalDateTime tryParseFreezeUntil(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        Map<String, String> m = parseMetadata(metadata);
        String v = m.get("freezeUntil");
        if (v == null || v.isBlank()) return null;
        try { return LocalDateTime.parse(v.trim()); } catch (Exception ignore) { return null; }
    }

    // =====================================================
    // 🔧 Guards + helpers
    // =====================================================

    private InteractionContext normalizeCtx(InteractionContext ctx) {
        if (ctx == null) ctx = new InteractionContext();
        if (ctx.source == null || ctx.source.isBlank()) ctx.source = "user";
        if (ctx.mode == null) ctx.mode = WeddingMode.GLOBAL;
        return ctx;
    }

    /**
     * ✅ FIX: לא להסתמך על enum value ספציפי (WEDDING / LIVE_WEDDING וכו')
     * כדי למנוע אי-סנכרון עם WeddingMode אצלך.
     */
    private boolean isLiveWedding(InteractionContext ctx) {
        if (ctx == null) return false;
        if (ctx.liveWeddingRules) return true;
        if (ctx.mode == null) return false;
        String n = ctx.mode.name();
        // תומך גם בשמות קיימים/עתידיים בלי לשבור קומפילציה
        return "LIVE_WEDDING".equalsIgnoreCase(n) || "WEDDING".equalsIgnoreCase(n) || n.toUpperCase().contains("WEDDING");
    }

    private User getUserOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    private void enforceBasicGuards(Long actorId, Long targetId) {
        if (actorId == null || targetId == null) throw new IllegalArgumentException("actorId/targetId is null");
        if (Objects.equals(actorId, targetId)) throw new IllegalArgumentException("Actor cannot target himself");
    }

    private void enforceNotBlockedBetween(ActiveIndex aIdx, ActiveIndex bIdx, Long a, Long b) {
        if (aIdx.get(UserActionType.BLOCK, b) != null || bIdx.get(UserActionType.BLOCK, a) != null) {
            throw new IllegalStateException("Blocked between users");
        }
    }

    private void enforceCanPerformPositiveAction(User actor, User target, InteractionContext ctx, boolean superLike) {
        boolean liveWedding = isLiveWedding(ctx);

        if (liveWedding) {
            UserStateEvaluatorService.UserStateSummary a = userStateEvaluator.evaluateUserState(actor);
            UserStateEvaluatorService.UserStateSummary t = userStateEvaluator.evaluateUserState(target);

            if (!hasPrimaryPhoto(a) || !hasPrimaryPhoto(t)) {
                throw new IllegalStateException("In wedding mode, both users must have a primary photo");
            }
            if (superLike && ctx.weddingId == null) {
                throw new IllegalStateException("SuperLike in wedding mode requires weddingId");
            }
            return;
        }

        if (ctx != null && !ctx.enforceUserStateGate) return;

        UserStateEvaluatorService.UserStateSummary st = userStateEvaluator.evaluateUserState(actor);
        if (!st.isCanLike()) {
            throw new IllegalStateException("User cannot like: " + String.join(" | ", safeReasons(st)));
        }
        if (!hasPrimaryPhoto(st)) {
            throw new IllegalStateException("Primary photo is required to like");
        }
    }

    private void enforceCanPerformNeutralAction(Long actorId, InteractionContext ctx) {
        if (ctx != null && !ctx.enforceUserStateGate) return;

        User actor = getUserOrThrow(actorId);
        UserStateEvaluatorService.UserStateSummary st = userStateEvaluator.evaluateUserState(actor);

        boolean liveWedding = isLiveWedding(ctx);
        if (liveWedding) {
            if (!hasPrimaryPhoto(st)) throw new IllegalStateException("Primary photo is required");
            return;
        }

        if (!st.isCanLike()) {
            throw new IllegalStateException("Action blocked: " + String.join(" | ", safeReasons(st)));
        }
    }

    private boolean hasPrimaryPhoto(UserStateEvaluatorService.UserStateSummary st) {
        return st != null && st.isHasPrimaryPhoto();
    }

    private List<String> safeReasons(UserStateEvaluatorService.UserStateSummary st) {
        if (st == null || st.getReasonsBlocked() == null) return List.of();
        return st.getReasonsBlocked().stream().filter(Objects::nonNull).limit(10).toList();
    }

    // =====================================================
    // ✅ ActiveIndex (single DB hit, fast lookup)
    // =====================================================

    private static class ActiveIndex {
        private final Map<UserActionType, Map<Long, UserAction>> byTypeTarget = new EnumMap<>(UserActionType.class);

        UserAction get(UserActionType type, Long targetId) {
            if (type == null || targetId == null) return null;
            Map<Long, UserAction> m = byTypeTarget.get(type);
            if (m == null) return null;
            UserAction ua = m.get(targetId);
            return (ua != null && ua.isActive()) ? ua : null;
        }

        void put(UserAction ua) {
            if (ua == null || !ua.isActive()) return;
            if (ua.getActionType() == null) return;
            if (ua.getTarget() == null || ua.getTarget().getId() == null) return;

            byTypeTarget.computeIfAbsent(ua.getActionType(), k -> new LinkedHashMap<>())
                    .putIfAbsent(ua.getTarget().getId(), ua);
        }

        void markInactive(UserActionType type, Long targetId) {
            Map<Long, UserAction> m = byTypeTarget.get(type);
            if (m != null) m.remove(targetId);
        }

        List<Long> targetsOfType(UserActionType type) {
            Map<Long, UserAction> m = byTypeTarget.get(type);
            if (m == null) return Collections.emptyList();
            return new ArrayList<>(m.keySet());
        }
    }

    private ActiveIndex loadActorActiveIndex(Long actorId) {
        if (actorId == null) return new ActiveIndex();

        int maxScan = getIntSetting(K_MAX_SCAN_ACTIVE_ACTIONS, 2000);
        Pageable p = PageRequest.of(0, clamp(maxScan, 50, 5000));

        List<UserAction> rows = actionRepo.findByActor_IdAndActiveTrueOrderByCreatedAtDesc(actorId, p);
        ActiveIndex idx = new ActiveIndex();

        if (rows == null) return idx;
        for (UserAction ua : rows) {
            if (ua == null || !ua.isActive()) continue;
            idx.put(ua);
        }
        return idx;
    }

    private boolean isMutualLike(ActiveIndex actorIdx, ActiveIndex targetIdx, Long actorId, Long targetId) {
        UserAction ab = actorIdx.get(UserActionType.LIKE, targetId);
        if (ab == null) return false;

        UserAction ba = targetIdx.get(UserActionType.LIKE, actorId);
        return ba != null;
    }

    private void deactivateIfExists(ActiveIndex idx, Long actorId, Long targetId, UserActionType... types) {
        if (idx == null || types == null || types.length == 0) return;

        LocalDateTime now = LocalDateTime.now();
        List<UserAction> toSave = new ArrayList<>();

        for (UserActionType t : types) {
            UserAction act = idx.get(t, targetId);
            if (act != null) {
                act.setActive(false);
                act.setUpdatedAt(now);
                toSave.add(act);
                idx.markInactive(t, targetId);
            }
        }

        if (!toSave.isEmpty()) actionRepo.saveAll(toSave);
    }

    private void deactivateRelationshipBothDirections(Long a, Long b, ActiveIndex aIdx, ActiveIndex bIdx) {
        UserActionType[] types = new UserActionType[] {
                UserActionType.LIKE,
                UserActionType.DISLIKE,
                UserActionType.FREEZE,
                UserActionType.BLOCK
        };

        deactivateIfExists(aIdx, a, b, types);
        deactivateIfExists(bIdx, b, a, types);
    }

    // =====================================================
    // ✅ Received lists helper
    // =====================================================

    @Transactional(readOnly = true)
    private List<Long> getActorsByActiveTypeOnTarget(Long targetId, UserActionType type, int limit) {
        if (targetId == null || type == null) return Collections.emptyList();

        int lim = clamp(limit, 1, 500);
        Pageable p = PageRequest.of(0, lim);

        List<UserAction> rows = actionRepo.findByTarget_IdAndActionTypeOrderByCreatedAtDesc(targetId, type, p);
        if (rows == null) return Collections.emptyList();

        return rows.stream()
                .filter(Objects::nonNull)
                .filter(UserAction::isActive)
                .map(UserAction::getActor)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(lim)
                .collect(Collectors.toList());
    }

    // =====================================================
    // ✅ SuperLike daily cap
    // =====================================================

    private void enforceSuperLikeDailyCap(Long actorId) {
        int cap = getIntSetting(K_SUPERLIKE_DAILY_CAP, 5);
        if (cap <= 0) throw new IllegalStateException("SuperLike disabled by settings");

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        ActiveIndex idx = loadActorActiveIndex(actorId);

        long used = idx.targetsOfType(UserActionType.LIKE).stream()
                .map(tid -> idx.get(UserActionType.LIKE, tid))
                .filter(Objects::nonNull)
                .filter(UserAction::isActive)
                .filter(ua -> ua.getCreatedAt() != null && !ua.getCreatedAt().isBefore(startOfDay))
                .filter(this::isSuperLike)
                .count();

        if (used >= cap) {
            throw new IllegalStateException("SuperLike daily cap reached: " + used + "/" + cap);
        }
    }

    private boolean isSuperLike(UserAction ua) {
        if (ua == null) return false;
        if (ua.getMetadata() == null) return false;
        Map<String, String> m = parseMetadata(ua.getMetadata());
        String v = m.get("superLike");
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    // =====================================================
    // ✅ CreateActionCommand builder (no reflection)
    // =====================================================

    private UserActionService.CreateActionCommand baseCmd(Long actorId, Long targetId, InteractionContext ctx) {
        UserActionService.CreateActionCommand cmd = new UserActionService.CreateActionCommand();
        cmd.actorUserId = actorId;
        cmd.targetUserId = targetId;

        if (ctx != null) {
            cmd.weddingId = ctx.weddingId;
            cmd.originWeddingId = ctx.originWeddingId;
            cmd.matchId = ctx.matchId;
            cmd.actionGroupId = ctx.actionGroupId;

            cmd.source = ctx.source;
            cmd.ipAddress = ctx.ipAddress;
            cmd.deviceInfo = ctx.deviceInfo;

            cmd.autoGenerated = (ctx.source != null && !"user".equalsIgnoreCase(ctx.source));
        } else {
            cmd.source = "user";
            cmd.autoGenerated = false;
        }

        cmd.active = true;
        return cmd;
    }

    // =====================================================
    // ✅ Metadata helpers (same behavior)
    // =====================================================

    private String buildMetadata(InteractionContext ctx, String event, Map<String, String> extra) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("event", event);
        if (ctx != null) {
            if (ctx.mode != null) m.put("mode", String.valueOf(ctx.mode));
            if (ctx.weddingId != null) m.put("weddingId", String.valueOf(ctx.weddingId));
            if (ctx.originWeddingId != null) m.put("originWeddingId", String.valueOf(ctx.originWeddingId));
            if (ctx.matchId != null) m.put("matchId", String.valueOf(ctx.matchId));
            if (ctx.actionGroupId != null) m.put("actionGroupId", String.valueOf(ctx.actionGroupId));
            if (ctx.source != null) m.put("source", ctx.source);
        }
        if (extra != null) m.putAll(extra);
        return toJsonLike(m);
    }

    private String toJsonLike(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getKey() == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append("\"").append(escape(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, String> parseMetadata(String metadata) {
        Map<String, String> out = new LinkedHashMap<>();
        if (metadata == null) return out;

        String s = metadata.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1).trim();
            if (s.isEmpty()) return out;

            String[] parts = s.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String p : parts) {
                String[] kv = p.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                if (kv.length != 2) continue;
                String k = stripQuotes(kv[0].trim());
                String v = stripQuotes(kv[1].trim());
                if (!k.isBlank()) out.put(k, v);
            }
            return out;
        }

        String[] parts = s.split(";");
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            out.put(kv[0].trim(), kv[1].trim());
        }
        return out;
    }

    private String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =====================================================
    // ✅ Mapping / utils
    // =====================================================

    private UserActionCategory mapCategory(UserActionType t) {
        if (t == null) return UserActionCategory.UNKNOWN;
        return switch (t) {
            case LIKE, LIKE_BACK, DISLIKE, FREEZE, UNFREEZE, VIEW, BLOCK, UNBLOCK -> UserActionCategory.SOCIAL;
            case SEND_MESSAGE, REQUEST_CHAT, APPROVE_CHAT, DECLINE_CHAT, FIRST_MESSAGE_SENT -> UserActionCategory.CHAT;
            case ENTER_WEDDING, EXIT_WEDDING, VIEW_WEDDING_MEMBER -> UserActionCategory.EVENT;
            case REQUEST_GLOBAL_ACCESS, APPROVE_GLOBAL_ACCESS, DECLINE_GLOBAL_ACCESS, ENTER_GLOBAL_POOL, EXIT_GLOBAL_POOL -> UserActionCategory.GLOBAL_POOL;
            case PROFILE_UPDATED, PROFILE_BASIC_COMPLETED, PROFILE_FULL_COMPLETED, PROFILE_PHOTO_ADDED, PROFILE_PHOTO_DELETED -> UserActionCategory.PROFILE;
            default -> UserActionCategory.UNKNOWN;
        };
    }

    private int getIntSetting(String key, int def) {
        try {
            if (key == null || key.isBlank()) return def;
            return settings.getEffectiveInt(
                    settings.resolveEnv(),
                    SystemSettingsService.Scope.SYSTEM,
                    null,
                    key.trim(),
                    def
            );
        } catch (Exception ignore) {
            return def;
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String safeShort(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max));
    }
}