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

import java.lang.reflect.Field;
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
        public WeddingMode mode;              // NONE / WEDDING / GLOBAL / PAST_WEDDING
        public Long weddingId;
        public Long originWeddingId;
        public Long matchId;
        public Long actionGroupId;

        public String source = "user";        // user/admin/system/ai
        public String ipAddress;
        public String deviceInfo;

        public boolean liveWeddingRules = false;
        public boolean enforceUserStateGate = true;
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
        enforceNotBlockedBetween(actorId, targetId);
        enforceCanPerformPositiveAction(actor, target, ctx, false);

        deactivateConflicts(actorId, targetId, UserActionType.DISLIKE, UserActionType.FREEZE);

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.LIKE;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "LIKE", null);

        UserAction saved = userActionService.record(cmd);
        boolean mutual = isMutualPositive(actorId, targetId);

        return new InteractionResult(saved, mutual, mutual ? "Mutual positive detected" : "Like recorded");
    }

    public InteractionResult dislike(Long actorId, Long targetId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(actorId);
        getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);
        enforceNotBlockedBetween(actorId, targetId);
        enforceCanPerformNeutralAction(actorId, ctx);

        deactivateConflicts(actorId, targetId, UserActionType.LIKE, UserActionType.FREEZE);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("undoMinutes", String.valueOf(getIntSetting(K_UNDO_DISLIKE_MINUTES, 10)));

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.DISLIKE;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "DISLIKE", extra);

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Dislike recorded");
    }

    public boolean undoDislike(Long actorId, Long targetId) {
        enforceBasicGuards(actorId, targetId);

        int undoMinutes = getIntSetting(K_UNDO_DISLIKE_MINUTES, 10);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, undoMinutes));

        UserAction dislike = findActiveActionBetween(actorId, targetId, UserActionType.DISLIKE);
        if (dislike == null) return false;

        if (dislike.getCreatedAt() == null || dislike.getCreatedAt().isBefore(cutoff)) {
            return false;
        }

        dislike.setActive(false);
        dislike.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(dislike);
        return true;
    }

    public InteractionResult freeze(Long actorId, Long targetId, Integer daysNullable, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(actorId);
        getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);
        enforceNotBlockedBetween(actorId, targetId);
        enforceCanPerformNeutralAction(actorId, ctx);

        deactivateConflicts(actorId, targetId, UserActionType.LIKE, UserActionType.DISLIKE);

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

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Freeze recorded");
    }

    public boolean unfreeze(Long actorId, Long targetId, String reason) {
        enforceBasicGuards(actorId, targetId);

        UserAction fr = findActiveActionBetween(actorId, targetId, UserActionType.FREEZE);
        if (fr == null) return false;

        fr.setActive(false);
        if (reason != null && !reason.isBlank()) fr.setReason(reason.trim());
        fr.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(fr);

        // audit UNFREEZE
        try {
            InteractionContext ctx = normalizeCtx(null);
            UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
            cmd.actionType = UserActionType.UNFREEZE;
            cmd.category = mapCategory(cmd.actionType);
            cmd.reason = (reason != null && !reason.isBlank()) ? reason.trim() : null;
            cmd.metadata = buildMetadata(ctx, "UNFREEZE", null);
            userActionService.record(cmd);
        } catch (Exception ignore) {}

        return true;
    }

    /**
     * אין SUPER_LIKE ב-enum שלך:
     * LIKE רגיל + metadata superLike=true
     */
    public InteractionResult superLike(Long actorId, Long targetId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);
        enforceNotBlockedBetween(actorId, targetId);
        enforceCanPerformPositiveAction(actor, target, ctx, true);

        enforceSuperLikeDailyCap(actorId);

        deactivateConflicts(actorId, targetId, UserActionType.DISLIKE, UserActionType.FREEZE, UserActionType.LIKE);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("superLike", "true");
        extra.put("dailyCap", String.valueOf(getIntSetting(K_SUPERLIKE_DAILY_CAP, 5)));

        UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
        cmd.actionType = UserActionType.LIKE;     // ✅ aligned
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "SUPERLIKE", extra);

        UserAction saved = userActionService.record(cmd);

        boolean mutual = isMutualPositive(actorId, targetId);
        return new InteractionResult(saved, mutual, mutual ? "Mutual positive detected (via superlike)" : "SuperLike recorded");
    }

    public InteractionResult block(Long actorId, Long targetId, InteractionContext ctx, String reason) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(actorId);
        getUserOrThrow(targetId);

        enforceBasicGuards(actorId, targetId);

        deactivateAllRelationshipActionsBothDirections(actorId, targetId);

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

        UserAction bl = findActiveActionBetween(actorId, targetId, UserActionType.BLOCK);
        if (bl == null) return false;

        bl.setActive(false);
        if (reason != null && !reason.isBlank()) bl.setReason(reason.trim());
        bl.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(bl);

        // audit UNBLOCK
        try {
            InteractionContext ctx = normalizeCtx(null);
            UserActionService.CreateActionCommand cmd = baseCmd(actorId, targetId, ctx);
            cmd.actionType = UserActionType.UNBLOCK;
            cmd.category = mapCategory(cmd.actionType);
            cmd.reason = (reason != null && !reason.isBlank()) ? reason.trim() : null;
            cmd.metadata = buildMetadata(ctx, "UNBLOCK", null);
            userActionService.record(cmd);
        } catch (Exception ignore) {}

        return true;
    }

    /**
     * אין PROFILE_VIEW, משתמשים ב-VIEW
     */
    public long viewProfile(Long viewerId, Long profileUserId, InteractionContext ctx) {
        ctx = normalizeCtx(ctx);

        if (Objects.equals(viewerId, profileUserId)) {
            return getProfileViewsCount(profileUserId);
        }

        getUserOrThrow(viewerId);
        getUserOrThrow(profileUserId);

        enforceNotBlockedBetween(viewerId, profileUserId);

        UserActionService.CreateActionCommand cmd = baseCmd(viewerId, profileUserId, ctx);
        cmd.actionType = UserActionType.VIEW;
        cmd.category = mapCategory(cmd.actionType);
        cmd.metadata = buildMetadata(ctx, "PROFILE_VIEW", null);

        userActionService.record(cmd);

        return getProfileViewsCount(profileUserId);
    }

    @Transactional(readOnly = true)
    public long getProfileViewsCount(Long profileUserId) {
        if (profileUserId == null) return 0;
        return actionRepo.countByTarget_IdAndActionType(profileUserId, UserActionType.VIEW);
    }

    /**
     * אין REPORT, רושמים UNKNOWN + metadata
     */
    public InteractionResult reportUser(Long reporterId, Long targetId, InteractionContext ctx, String reportType, String details) {
        ctx = normalizeCtx(ctx);

        getUserOrThrow(reporterId);
        getUserOrThrow(targetId);

        enforceBasicGuards(reporterId, targetId);
        enforceNotBlockedBetween(reporterId, targetId);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("report", "true");
        if (reportType != null && !reportType.isBlank()) extra.put("reportType", reportType.trim());
        if (details != null && !details.isBlank()) extra.put("details", safeShort(details.trim(), 800));

        UserActionService.CreateActionCommand cmd = baseCmd(reporterId, targetId, ctx);
        cmd.actionType = UserActionType.UNKNOWN;
        cmd.category = UserActionCategory.SYSTEM;
        cmd.metadata = buildMetadata(ctx, "REPORT", extra);

        UserAction saved = userActionService.record(cmd);
        return new InteractionResult(saved, false, "Report recorded");
    }

    // =====================================================
    // ✅ Cancel APIs
    // =====================================================

    public boolean cancelAction(Long actorId, Long targetId, UserActionType type, String reason) {
        enforceBasicGuards(actorId, targetId);
        if (type == null) throw new IllegalArgumentException("type is null");

        UserAction act = findActiveActionBetween(actorId, targetId, type);
        if (act == null) return false;

        act.setActive(false);
        if (reason != null && !reason.isBlank()) act.setReason(reason.trim());
        act.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(act);
        return true;
    }

    public boolean cancelLike(Long actorId, Long targetId, String reason) {
        return cancelAction(actorId, targetId, UserActionType.LIKE, reason);
    }

    public boolean cancelSuperLike(Long actorId, Long targetId, String reason) {
        enforceBasicGuards(actorId, targetId);

        UserAction like = findActiveActionBetween(actorId, targetId, UserActionType.LIKE);
        if (like == null) return false;
        if (!isSuperLike(like)) return false;

        like.setActive(false);
        if (reason != null && !reason.isBlank()) like.setReason(reason.trim());
        like.setUpdatedAt(LocalDateTime.now());
        actionRepo.save(like);
        return true;
    }

    public boolean cancelFreeze(Long actorId, Long targetId, String reason) {
        return cancelAction(actorId, targetId, UserActionType.FREEZE, reason);
    }

    public boolean cancelDislike(Long actorId, Long targetId, String reason) {
        return cancelAction(actorId, targetId, UserActionType.DISLIKE, reason);
    }

    // =====================================================
    // ✅ Lists
    // =====================================================

    @Transactional(readOnly = true)
    public List<Long> getLikesGiven(Long actorId, int limit) {
        return getTargetsByActiveType(actorId, UserActionType.LIKE, limit).stream()
                .filter(t -> !isSuperLikeTarget(actorId, t))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getDislikesGiven(Long actorId, int limit) {
        return getTargetsByActiveType(actorId, UserActionType.DISLIKE, limit);
    }

    @Transactional(readOnly = true)
    public List<Long> getFreezesGiven(Long actorId, int limit) {
        try { cleanupExpiredFreezesForActor(actorId); } catch (Exception ignore) {}
        return getTargetsByActiveType(actorId, UserActionType.FREEZE, limit);
    }

    @Transactional(readOnly = true)
    public List<Long> getSuperLikesGiven(Long actorId, int limit) {
        if (actorId == null) return Collections.emptyList();
        int lim = clamp(limit, 1, 500);

        Pageable p = PageRequest.of(0, lim);
        List<UserAction> rows = actionRepo.findByActor_IdAndActiveTrueOrderByCreatedAtDesc(actorId, p);
        if (rows == null) return Collections.emptyList();

        return rows.stream()
                .filter(Objects::nonNull)
                .filter(UserAction::isActive)
                .filter(ua -> ua.getActionType() == UserActionType.LIKE)
                .filter(this::isSuperLike)
                .map(UserAction::getTarget)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(lim)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getLikesReceived(Long userId, int limit) {
        int lim = clamp(limit, 1, 500);
        return getActorsByActiveTypeOnTarget(userId, UserActionType.LIKE, lim);
    }

    @Transactional(readOnly = true)
    public List<Long> getMutualPositiveTargets(Long actorId, int limit) {
        int lim = clamp(limit, 1, 500);
        List<Long> positives = getTargetsByActiveType(actorId, UserActionType.LIKE, lim);

        List<Long> res = new ArrayList<>();
        for (Long t : positives) {
            if (t == null) continue;
            if (isMutualPositive(actorId, t)) res.add(t);
            if (res.size() >= lim) break;
        }
        return res;
    }

    // =====================================================
    // ✅ Cleanup expired freezes
    // =====================================================

    public int cleanupExpiredFreezesForActor(Long actorId) {
        if (actorId == null) return 0;

        int maxScan = getIntSetting(K_MAX_SCAN_ACTIVE_ACTIONS, 2000);
        Pageable p = PageRequest.of(0, clamp(maxScan, 50, 5000));

        List<UserAction> actives = actionRepo.findByActor_IdAndActiveTrueOrderByCreatedAtDesc(actorId, p);
        if (actives == null || actives.isEmpty()) return 0;

        int changed = 0;
        LocalDateTime now = LocalDateTime.now();

        for (UserAction ua : actives) {
            if (ua == null || !ua.isActive()) continue;
            if (ua.getActionType() != UserActionType.FREEZE) continue;

            LocalDateTime until = tryParseFreezeUntil(ua.getMetadata());
            if (until != null && until.isBefore(now)) {
                ua.setActive(false);
                ua.setUpdatedAt(now);
                actionRepo.save(ua);
                changed++;
            }
        }
        return changed;
    }

    private LocalDateTime tryParseFreezeUntil(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        Map<String, String> m = parseMetadata(metadata);
        String v = m.get("freezeUntil");
        if (v == null || v.isBlank()) return null;
        try { return LocalDateTime.parse(v.trim()); } catch (Exception ignore) { return null; }
    }

    // =====================================================
    // 🔧 Internal guards + helpers
    // =====================================================

    private InteractionContext normalizeCtx(InteractionContext ctx) {
        if (ctx == null) ctx = new InteractionContext();
        if (ctx.source == null || ctx.source.isBlank()) ctx.source = "user";
        if (ctx.mode == null) ctx.mode = WeddingMode.GLOBAL; // ✅ aligned default
        return ctx;
    }

    private User getUserOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    private void enforceBasicGuards(Long actorId, Long targetId) {
        if (actorId == null || targetId == null) throw new IllegalArgumentException("actorId/targetId is null");
        if (Objects.equals(actorId, targetId)) throw new IllegalArgumentException("Actor cannot target himself");
    }

    private void enforceNotBlockedBetween(Long a, Long b) {
        if (isBlockedActive(a, b) || isBlockedActive(b, a)) {
            throw new IllegalStateException("Blocked between users");
        }
    }

    private boolean isBlockedActive(Long actorId, Long targetId) {
        return findActiveActionBetween(actorId, targetId, UserActionType.BLOCK) != null;
    }

    private void enforceCanPerformPositiveAction(User actor, User target, InteractionContext ctx, boolean superLike) {
        boolean liveWedding = ctx.liveWeddingRules || (ctx.mode == WeddingMode.WEDDING);

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

        if (!ctx.enforceUserStateGate) return;

        UserStateEvaluatorService.UserStateSummary st = userStateEvaluator.evaluateUserState(actor);
        if (!st.isCanLike()) {
            throw new IllegalStateException("User cannot like: " + String.join(" | ", safeReasons(st)));
        }
        if (!hasPrimaryPhoto(st)) {
            throw new IllegalStateException("Primary photo is required to like");
        }
    }

    private void enforceCanPerformNeutralAction(Long actorId, InteractionContext ctx) {
        if (!ctx.enforceUserStateGate) return;

        User actor = getUserOrThrow(actorId);
        UserStateEvaluatorService.UserStateSummary st = userStateEvaluator.evaluateUserState(actor);

        boolean liveWedding = ctx.liveWeddingRules || (ctx.mode == WeddingMode.WEDDING);
        if (liveWedding) {
            if (!hasPrimaryPhoto(st)) {
                throw new IllegalStateException("Primary photo is required");
            }
            return;
        }

        if (!st.isCanLike()) { // לפי ה-evaluator שלך: canLike = hasPhoto && !deletion
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
    // ✅ Action repository helpers
    // =====================================================

    private UserAction findActiveActionBetween(Long actorId, Long targetId, UserActionType type) {
        if (actorId == null || targetId == null || type == null) return null;

        int maxScan = getIntSetting(K_MAX_SCAN_ACTIVE_ACTIONS, 2000);
        Pageable p = PageRequest.of(0, clamp(maxScan, 50, 5000));

        // סריקה יעילה יחסית: פעולות פעילות של actor, מסונן בזיכרון
        List<UserAction> rows = actionRepo.findByActor_IdAndActiveTrueOrderByCreatedAtDesc(actorId, p);
        if (rows == null) return null;

        for (UserAction ua : rows) {
            if (ua == null || !ua.isActive()) continue;
            if (ua.getActionType() != type) continue;
            if (ua.getTarget() == null || ua.getTarget().getId() == null) continue;
            if (Objects.equals(ua.getTarget().getId(), targetId)) return ua;
        }
        return null;
    }

    private boolean isMutualPositive(Long a, Long b) {
        UserAction ab = findActiveActionBetween(a, b, UserActionType.LIKE);
        if (ab == null) return false;
        UserAction ba = findActiveActionBetween(b, a, UserActionType.LIKE);
        return ba != null;
    }

    private void deactivateConflicts(Long actorId, Long targetId, UserActionType... types) {
        if (types == null || types.length == 0) return;
        LocalDateTime now = LocalDateTime.now();

        for (UserActionType t : types) {
            UserAction act = findActiveActionBetween(actorId, targetId, t);
            if (act != null) {
                act.setActive(false);
                act.setUpdatedAt(now);
                actionRepo.save(act);
            }
        }
    }

    private void deactivateAllRelationshipActionsBothDirections(Long a, Long b) {
        // מבטל LIKE/DISLIKE/FREEZE/BLOCK בשני כיוונים
        UserActionType[] types = new UserActionType[] {
                UserActionType.LIKE,
                UserActionType.DISLIKE,
                UserActionType.FREEZE,
                UserActionType.BLOCK
        };
        for (UserActionType t : types) {
            UserAction ab = findActiveActionBetween(a, b, t);
            if (ab != null) {
                ab.setActive(false);
                ab.setUpdatedAt(LocalDateTime.now());
                actionRepo.save(ab);
            }
            UserAction ba = findActiveActionBetween(b, a, t);
            if (ba != null) {
                ba.setActive(false);
                ba.setUpdatedAt(LocalDateTime.now());
                actionRepo.save(ba);
            }
        }
    }

    @Transactional(readOnly = true)
    private List<Long> getTargetsByActiveType(Long actorId, UserActionType type, int limit) {
        if (actorId == null || type == null) return Collections.emptyList();

        int lim = clamp(limit, 1, 500);
        Pageable p = PageRequest.of(0, Math.max(lim, 50));

        List<UserAction> rows = actionRepo.findByActor_IdAndActiveTrueOrderByCreatedAtDesc(actorId, p);
        if (rows == null) return Collections.emptyList();

        return rows.stream()
                .filter(Objects::nonNull)
                .filter(UserAction::isActive)
                .filter(ua -> ua.getActionType() == type)
                .map(UserAction::getTarget)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(lim)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    private List<Long> getActorsByActiveTypeOnTarget(Long targetId, UserActionType type, int limit) {
        if (targetId == null || type == null) return Collections.emptyList();

        int lim = clamp(limit, 1, 500);
        Pageable p = PageRequest.of(0, lim);

        // ✅ מתודה שכן קיימת בריפו ששלחת
        List<UserAction> rows =
                actionRepo.findByTarget_IdAndActionTypeOrderByCreatedAtDesc(targetId, type, p);

        if (rows == null) return Collections.emptyList();

        return rows.stream()
                .filter(Objects::nonNull)
                .filter(UserAction::isActive)          // active=true (במקום activeTrue בריפו)
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
        int maxScan = getIntSetting(K_MAX_SCAN_ACTIVE_ACTIONS, 2000);

        Pageable p = PageRequest.of(0, clamp(maxScan, 50, 5000));
        List<UserAction> rows = actionRepo.findByActor_IdAndActiveTrueOrderByCreatedAtDesc(actorId, p);
        if (rows == null) return;

        long used = rows.stream()
                .filter(Objects::nonNull)
                .filter(UserAction::isActive)
                .filter(ua -> ua.getCreatedAt() != null && !ua.getCreatedAt().isBefore(startOfDay))
                .filter(ua -> ua.getActionType() == UserActionType.LIKE)
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

    private boolean isSuperLikeTarget(Long actorId, Long targetId) {
        UserAction like = findActiveActionBetween(actorId, targetId, UserActionType.LIKE);
        return like != null && isSuperLike(like);
    }

    // =====================================================
    // ✅ CreateActionCommand builder (reflection-safe)
    // =====================================================

    private UserActionService.CreateActionCommand baseCmd(Long actorId, Long targetId, InteractionContext ctx) {
        UserActionService.CreateActionCommand cmd = new UserActionService.CreateActionCommand();

        // actor / target (תואם לכל naming style)
        setIfExists(cmd, "actorId", actorId);
        setIfExists(cmd, "actorUserId", actorId);

        setIfExists(cmd, "targetId", targetId);
        setIfExists(cmd, "targetUserId", targetId);

        // context ids
        setIfExists(cmd, "weddingId", ctx.weddingId);
        setIfExists(cmd, "originWeddingId", ctx.originWeddingId);
        setIfExists(cmd, "matchId", ctx.matchId);
        setIfExists(cmd, "actionGroupId", ctx.actionGroupId);

        // telemetry
        setIfExists(cmd, "source", ctx.source);
        setIfExists(cmd, "ipAddress", ctx.ipAddress);
        setIfExists(cmd, "deviceInfo", ctx.deviceInfo);

        // common
        setIfExists(cmd, "active", true);

        return cmd;
    }

    private void setIfExists(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception ignore) { }
    }

    // =====================================================
    // ✅ Metadata helpers
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
        // לא תלוי ב-ObjectMapper, אבל עדיין "כמו JSON"
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
        // naive JSON-ish parse: "k":"v"
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

        // fallback: k=v;k=v
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

            // משתמשים ב-API הרשמי של SystemSettingsService
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