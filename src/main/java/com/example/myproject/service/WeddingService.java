package com.example.myproject.service;

import com.example.myproject.model.SystemLog;
import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.model.WeddingBackground;
import com.example.myproject.model.WeddingParticipant;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.model.enums.WeddingParticipantRole;
import com.example.myproject.repository.SystemLogRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingBackgroundRepository;
import com.example.myproject.repository.WeddingParticipantRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * WeddingService (2025 MASTER - Fully synced with WeddingParticipant)
 *
 * ✅ Principles:
 * 1) WeddingParticipant (wedding_participants) is the authoritative source for roles (OWNER/CO_OWNER/PARTICIPANT).
 * 2) Wedding.ownerUserId + Wedding.coOwners are kept for legacy/UI compatibility, but never override WP.
 * 3) No role downgrade: joining as participant will NEVER downgrade an existing OWNER/CO_OWNER row.
 * 4) All participant lifecycle actions go through WeddingParticipantService (lock-aware).
 */
@Service
@Transactional
public class WeddingService {

    private static final int UNIQUE_RETRY = 20;

    private static final Set<WeddingParticipantRole> OWNER_ROLES =
            Set.of(WeddingParticipantRole.OWNER, WeddingParticipantRole.CO_OWNER);

    private final WeddingRepository weddingRepository;
    private final UserRepository userRepository;
    private final WeddingBackgroundRepository weddingBackgroundRepository;
    private final SystemLogRepository systemLogRepository;

    private final WeddingParticipantService weddingParticipantService;
    private final WeddingParticipantRepository weddingParticipantRepository;

    public WeddingService(WeddingRepository weddingRepository,
                          UserRepository userRepository,
                          WeddingBackgroundRepository weddingBackgroundRepository,
                          SystemLogRepository systemLogRepository,
                          WeddingParticipantService weddingParticipantService,
                          WeddingParticipantRepository weddingParticipantRepository) {
        this.weddingRepository = weddingRepository;
        this.userRepository = userRepository;
        this.weddingBackgroundRepository = weddingBackgroundRepository;
        this.systemLogRepository = systemLogRepository;
        this.weddingParticipantService = weddingParticipantService;
        this.weddingParticipantRepository = weddingParticipantRepository;
    }

    // =====================================================
    // ✅ 0) CREATE
    // =====================================================

    public Wedding createWedding(Long actorUserId, Wedding draft) {
        User actor = getUserOrThrow(actorUserId);
        requireAdminOrEventManager(actor);

        if (draft == null) throw new IllegalArgumentException("Wedding draft is null");
        if (isBlank(draft.getName())) throw new IllegalArgumentException("Wedding name is required");
        if (draft.getWeddingDate() == null) throw new IllegalArgumentException("Wedding weddingDate is required");

        if (draft.getWeddingEndTime() == null) {
            LocalDate nextDay = draft.getWeddingDate().toLocalDate().plusDays(1);
            draft.setWeddingEndTime(LocalDateTime.of(nextDay, LocalTime.of(1, 0)));
        }
        ensureChronology(draft.getWeddingDate(), draft.getWeddingEndTime());

        if (isBlank(draft.getAccessCode())) draft.setAccessCode(generateUniqueAccessCode());
        else ensureAccessCodeUniqueOrThrow(draft.getAccessCode(), null);

        if (isBlank(draft.getWeddingToken())) draft.setWeddingToken(generateUniqueToken());
        else ensureTokenUniqueOrThrow(draft.getWeddingToken(), null);

        if (draft.getBackgroundMode() == null) draft.setBackgroundMode(BackgroundMode.DEFAULT);

        if (draft.getOwnerUserId() == null) {
            draft.setOwnerUserId(actorUserId);
        } else {
            getUserOrThrow(draft.getOwnerUserId());
        }

        draft.setUpdatedByUserId(actorUserId);
        safeInvoke(draft, "setCreatedByUserId", Long.class, actorUserId);
        safeInvoke(draft, "setCreatedAt", LocalDateTime.class, LocalDateTime.now());
        safeInvoke(draft, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(draft);

        ensureRoleAtLeast(actorUserId, saved.getId(), saved.getOwnerUserId(), WeddingParticipantRole.OWNER);
        syncCoOwnersToWp_NoDowngrade(actorUserId, saved);

        audit("WEDDING_CREATED", "INFO", true, actorUserId,
                "Wedding created: id=" + saved.getId() + ", name=" + saved.getName(),
                "Wedding", saved.getId(), null);

        return saved;
    }

    // =====================================================
    // ✅ 1) UPDATE (PATCH) - SAFE PATCH (NO FLAGS)
    // =====================================================

    public Wedding updateWedding(Long actorUserId, Long weddingId, Wedding patch) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        if (patch == null) throw new IllegalArgumentException("Patch is null");
        ensureNotCancelled(wedding);

        if (!isBlank(patch.getName())) wedding.setName(patch.getName());

        if (patch.getWeddingDate() != null) wedding.setWeddingDate(patch.getWeddingDate());
        if (patch.getWeddingEndTime() != null) wedding.setWeddingEndTime(patch.getWeddingEndTime());
        if (wedding.getWeddingDate() != null && wedding.getWeddingEndTime() != null) {
            ensureChronology(wedding.getWeddingDate(), wedding.getWeddingEndTime());
        }

        if (!isBlank(patch.getHallName())) wedding.setHallName(patch.getHallName());
        if (!isBlank(patch.getHallAddress())) wedding.setHallAddress(patch.getHallAddress());
        if (!isBlank(patch.getCity())) wedding.setCity(patch.getCity());
        if (!isBlank(patch.getNotes())) wedding.setNotes(patch.getNotes());

        if (patch.getOwnerUserId() != null && !Objects.equals(patch.getOwnerUserId(), wedding.getOwnerUserId())) {
            requireAdminOrOwner(actor, wedding);

            Long newOwnerId = patch.getOwnerUserId();
            getUserOrThrow(newOwnerId);

            Long prevOwnerId = wedding.getOwnerUserId();
            wedding.setOwnerUserId(newOwnerId);
            wedding.setOwner(null);

            ensureRoleAtLeast(actorUserId, weddingId, newOwnerId, WeddingParticipantRole.OWNER);

            if (prevOwnerId != null && !prevOwnerId.equals(newOwnerId)) {
                ensureRoleAtLeast(actorUserId, weddingId, prevOwnerId, WeddingParticipantRole.CO_OWNER);
                tryAddCoOwnerToWeddingEntity(wedding, prevOwnerId);
            }

            removeFromCoOwnersList(wedding, newOwnerId);
        }

        if (!isBlank(patch.getAccessCode()) && !patch.getAccessCode().equals(wedding.getAccessCode())) {
            requireAdminOrOwner(actor, wedding);
            ensureAccessCodeUniqueOrThrow(patch.getAccessCode(), weddingId);
            wedding.setAccessCode(patch.getAccessCode());
        }
        if (!isBlank(patch.getWeddingToken()) && !patch.getWeddingToken().equals(wedding.getWeddingToken())) {
            requireAdminOrOwner(actor, wedding);
            ensureTokenUniqueOrThrow(patch.getWeddingToken(), weddingId);
            wedding.setWeddingToken(patch.getWeddingToken());
        }

        if (patch.getBackgroundMode() != null) wedding.setBackgroundMode(patch.getBackgroundMode());

        safeInvokeIfPresent(wedding, "setBackgroundImageUrl", String.class, safeGetString(patch, "getBackgroundImageUrl"));
        safeInvokeIfPresent(wedding, "setBackgroundVideoUrl", String.class, safeGetString(patch, "getBackgroundVideoUrl"));

        if (patch.getActiveBackgroundId() != null) {
            validateBackgroundBelongsToWeddingOrThrow(patch.getActiveBackgroundId(), weddingId);
            wedding.setActiveBackgroundId(patch.getActiveBackgroundId());
        }

        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        ensureRoleAtLeast(actorUserId, weddingId, saved.getOwnerUserId(), WeddingParticipantRole.OWNER);
        syncCoOwnersToWp_NoDowngrade(actorUserId, saved);

        audit("WEDDING_UPDATED", "INFO", true, actorUserId,
                "Wedding updated: id=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    // =====================================================
    // ✅ 2) LIFECYCLE
    // =====================================================

    public Wedding activateWedding(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setActive(true);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_ACTIVATED", "NOTICE", true, actorUserId,
                "Wedding activated: id=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding deactivateWedding(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setActive(false);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_DEACTIVATED", "NOTICE", true, actorUserId,
                "Wedding deactivated: id=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding manualCloseWeddingPool(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setManuallyClosed(true);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_MANUALLY_CLOSED", "NOTICE", true, actorUserId,
                "Wedding pool manually closed: id=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding manualOpenWeddingPool(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setManuallyClosed(false);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_MANUALLY_OPENED", "NOTICE", true, actorUserId,
                "Wedding pool manually opened: id=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding cancelWedding(Long actorUserId, Long weddingId, String reason) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);

        if (wedding.isCancelled()) return wedding;

        wedding.setCancelled(true);
        wedding.setCancelReason(isBlank(reason) ? "Cancelled" : reason);
        wedding.setActive(false);
        wedding.setManuallyClosed(true);
        wedding.setUpdatedByUserId(actorUserId);

        LocalDateTime now = LocalDateTime.now();
        safeInvoke(wedding, "setCancelledAt", LocalDateTime.class, now);
        safeInvoke(wedding, "setCanceledAt", LocalDateTime.class, now);
        safeInvoke(wedding, "setCancelledByUserId", Long.class, actorUserId);
        safeInvoke(wedding, "setCanceledByUserId", Long.class, actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, now);

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_CANCELLED", "WARNING", true, actorUserId,
                "Wedding cancelled: id=" + weddingId + ", reason=" + saved.getCancelReason(),
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding reopenWedding(Long actorUserId, Long weddingId, boolean alsoOpenPool) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireAdminOrOwner(actor, wedding);

        if (!wedding.isCancelled()) return wedding;

        wedding.setCancelled(false);
        safeInvoke(wedding, "setCancelReason", String.class, null);
        safeInvoke(wedding, "setCancelledAt", LocalDateTime.class, null);
        safeInvoke(wedding, "setCanceledAt", LocalDateTime.class, null);

        wedding.setActive(true);
        if (alsoOpenPool) wedding.setManuallyClosed(false);

        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_REOPENED", "NOTICE", true, actorUserId,
                "Wedding reopened: id=" + weddingId + ", alsoOpenPool=" + alsoOpenPool,
                "Wedding", weddingId, null);

        return saved;
    }

    // =====================================================
    // ✅ 2.5) FLAGS (DEDICATED SAFE METHODS)
    // =====================================================

    public Wedding setPublicFlag(Long actorUserId, Long weddingId, boolean isPublic) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setPublic(isPublic);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_PUBLIC_FLAG_UPDATED", "NOTICE", true, actorUserId,
                "isPublic=" + isPublic + " for weddingId=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding setAllowCandidatePoolFlag(Long actorUserId, Long weddingId, boolean allowed) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setAllowCandidatePool(allowed);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_CANDIDATE_POOL_FLAG_UPDATED", "NOTICE", true, actorUserId,
                "allowCandidatePool=" + allowed + " for weddingId=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding setAllowGlobalApprovalsByOwner(Long actorUserId, Long weddingId, boolean allowed) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setAllowGlobalApprovalsByOwner(allowed);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_GLOBAL_APPROVALS_BY_OWNER_UPDATED", "NOTICE", true, actorUserId,
                "allowGlobalApprovalsByOwner=" + allowed + " for weddingId=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    // =====================================================
    // ✅ 3) TOKENS / CODES + JOIN FLOWS (NO ROLE DOWNGRADE)
    // =====================================================

    public Wedding rotateAccessCode(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireAdminOrOwner(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setAccessCode(generateUniqueAccessCode());
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_ACCESS_CODE_ROTATED", "NOTICE", true, actorUserId,
                "AccessCode rotated: weddingId=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding rotateWeddingToken(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireAdminOrOwner(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setWeddingToken(generateUniqueToken());
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_TOKEN_ROTATED", "NOTICE", true, actorUserId,
                "WeddingToken rotated: weddingId=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding joinByAccessCode(Long actorUserId, String accessCode) {
        if (isBlank(accessCode)) throw new IllegalArgumentException("accessCode is blank");
        Wedding wedding = weddingRepository.findByAccessCode(accessCode)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found for accessCode"));
        return joinWedding(actorUserId, wedding.getId());
    }

    public Wedding joinByToken(Long actorUserId, String weddingToken) {
        if (isBlank(weddingToken)) throw new IllegalArgumentException("weddingToken is blank");
        Wedding wedding = weddingRepository.findByWeddingToken(weddingToken)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found for token"));
        return joinWedding(actorUserId, wedding.getId());
    }

    public Wedding joinWedding(Long actorUserId, Long weddingId) {
        getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        ensureNotCancelled(wedding);
        ensurePoolOpen(wedding);

        WeddingParticipantRole desired = resolveDesiredRoleForUser(wedding, actorUserId);

        ensureRoleAtLeast(actorUserId, weddingId, actorUserId, desired);

        audit("WEDDING_JOINED", "INFO", true, actorUserId,
                "User joined wedding: weddingId=" + weddingId + ", role=" + desired,
                "Wedding", weddingId, null);

        return wedding;
    }

    // =====================================================
    // ✅ 4) OWNERSHIP (OWNER + CO-OWNERS) — WP authoritative
    // =====================================================

    public Wedding setOwner(Long actorUserId, Long weddingId, Long newOwnerUserId) {
        if (newOwnerUserId == null) throw new IllegalArgumentException("newOwnerUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireAdminOrOwner(actor, wedding);
        ensureNotCancelled(wedding);

        getUserOrThrow(newOwnerUserId);

        Long prevOwnerId = wedding.getOwnerUserId();

        wedding.setOwnerUserId(newOwnerUserId);
        wedding.setOwner(null);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        ensureRoleAtLeast(actorUserId, weddingId, newOwnerUserId, WeddingParticipantRole.OWNER);

        if (prevOwnerId != null && !prevOwnerId.equals(newOwnerUserId)) {
            ensureRoleAtLeast(actorUserId, weddingId, prevOwnerId, WeddingParticipantRole.CO_OWNER);
            tryAddCoOwnerToWeddingEntity(wedding, prevOwnerId);
        }

        removeFromCoOwnersList(wedding, newOwnerUserId);

        Wedding saved = weddingRepository.save(wedding);
        syncCoOwnersToWp_NoDowngrade(actorUserId, saved);

        audit("WEDDING_OWNER_CHANGED", "NOTICE", true, actorUserId,
                "Owner changed: weddingId=" + weddingId + ", newOwnerUserId=" + newOwnerUserId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding addCoOwner(Long actorUserId, Long weddingId, Long coOwnerUserId) {
        if (coOwnerUserId == null) throw new IllegalArgumentException("coOwnerUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        if (Objects.equals(wedding.getOwnerUserId(), coOwnerUserId)) return wedding;

        getUserOrThrow(coOwnerUserId);

        ensureRoleAtLeast(actorUserId, weddingId, coOwnerUserId, WeddingParticipantRole.CO_OWNER);
        tryAddCoOwnerToWeddingEntity(wedding, coOwnerUserId);

        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_COOWNER_ADDED", "INFO", true, actorUserId,
                "CoOwner added: weddingId=" + weddingId + ", coOwnerUserId=" + coOwnerUserId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding removeCoOwner(Long actorUserId, Long weddingId, Long coOwnerUserId) {
        if (coOwnerUserId == null) throw new IllegalArgumentException("coOwnerUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        if (Objects.equals(wedding.getOwnerUserId(), coOwnerUserId)) {
            throw new IllegalStateException("Cannot remove owner from coOwners (use setOwner)");
        }

        forceSetRole(actorUserId, weddingId, coOwnerUserId, WeddingParticipantRole.PARTICIPANT);
        removeFromCoOwnersList(wedding, coOwnerUserId);

        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_COOWNER_REMOVED", "INFO", true, actorUserId,
                "CoOwner removed: weddingId=" + weddingId + ", coOwnerUserId=" + coOwnerUserId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding replaceCoOwners(Long actorUserId, Long weddingId, List<Long> coOwnerUserIds) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        if (coOwnerUserIds == null) coOwnerUserIds = Collections.emptyList();

        Long ownerId = wedding.getOwnerUserId();
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : coOwnerUserIds) {
            if (id == null) continue;
            if (ownerId != null && ownerId.equals(id)) continue;
            unique.add(id);
        }

        List<User> newList = new ArrayList<>();
        for (Long id : unique) {
            newList.add(getUserOrThrow(id));
        }
        wedding.setCoOwners(newList);

        for (Long id : unique) {
            ensureRoleAtLeast(actorUserId, weddingId, id, WeddingParticipantRole.CO_OWNER);
        }

        tryDemoteMissingCoOwners(actorUserId, weddingId, unique, ownerId);

        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_COOWNERS_REPLACED", "INFO", true, actorUserId,
                "CoOwners replaced: weddingId=" + weddingId + ", count=" + unique.size(),
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding clearCoOwners(Long actorUserId, Long weddingId) {
        return replaceCoOwners(actorUserId, weddingId, Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<User> listCoOwners(Long weddingId) {
        Wedding wedding = getWeddingOrThrow(weddingId);
        return wedding.getCoOwners() == null ? Collections.emptyList() : wedding.getCoOwners();
    }

    // =====================================================
    // ✅ 5) BACKGROUND (MODE + ACTIVE BACKGROUND ID)
    // =====================================================

    public Wedding updateBackgroundMode(Long actorUserId, Long weddingId, BackgroundMode mode) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        if (mode != null) wedding.setBackgroundMode(mode);

        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_BACKGROUND_MODE_UPDATED", "INFO", true, actorUserId,
                "Background mode updated: weddingId=" + weddingId + ", mode=" + wedding.getBackgroundMode(),
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding setActiveBackgroundId(Long actorUserId, Long weddingId, Long backgroundId) {
        if (backgroundId == null) throw new IllegalArgumentException("backgroundId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        validateBackgroundBelongsToWeddingOrThrow(backgroundId, weddingId);

        wedding.setActiveBackgroundId(backgroundId);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_ACTIVE_BACKGROUND_SET", "NOTICE", true, actorUserId,
                "ActiveBackground set: weddingId=" + weddingId + ", backgroundId=" + backgroundId,
                "Wedding", weddingId, null);

        return saved;
    }

    public Wedding clearActiveBackgroundId(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        wedding.setActiveBackgroundId(null);
        wedding.setBackgroundMode(BackgroundMode.DEFAULT);
        wedding.setUpdatedByUserId(actorUserId);
        safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());

        Wedding saved = weddingRepository.save(wedding);

        audit("WEDDING_ACTIVE_BACKGROUND_CLEARED", "NOTICE", true, actorUserId,
                "ActiveBackground cleared: weddingId=" + weddingId,
                "Wedding", weddingId, null);

        return saved;
    }

    // =====================================================
    // ✅ 6) PARTICIPANTS (WP authoritative) + NO DOWNGRADE
    // =====================================================

    public Wedding addParticipant(Long actorUserId, Long weddingId, Long participantUserId) {
        if (participantUserId == null) throw new IllegalArgumentException("participantUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdminOrSelf(actor, wedding, participantUserId);

        ensureNotCancelled(wedding);
        ensurePoolOpen(wedding);

        WeddingParticipantRole desired = resolveDesiredRoleForUser(wedding, participantUserId);
        ensureRoleAtLeast(actorUserId, weddingId, participantUserId, desired);

        audit("WEDDING_PARTICIPANT_ADDED", "INFO", true, actorUserId,
                "Participant joined: weddingId=" + weddingId + ", userId=" + participantUserId + ", role=" + desired,
                "Wedding", weddingId, null);

        return wedding;
    }

    public Wedding removeParticipant(Long actorUserId, Long weddingId, Long participantUserId) {
        if (participantUserId == null) throw new IllegalArgumentException("participantUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdminOrSelf(actor, wedding, participantUserId);
        ensureNotCancelled(wedding);

        if (Objects.equals(actorUserId, participantUserId)) {
            // self-leave
            weddingParticipantService.leaveWedding(actorUserId, weddingId);
        } else {
            // ✅ synced with official WP service name: remove(...)
            weddingParticipantService.remove(actorUserId, weddingId, participantUserId);
        }

        audit("WEDDING_PARTICIPANT_REMOVED", "INFO", true, actorUserId,
                "Participant left/removed: weddingId=" + weddingId + ", userId=" + participantUserId,
                "Wedding", weddingId, null);

        return wedding;
    }

    public Wedding blockParticipant(Long actorUserId, Long weddingId, Long targetUserId, String reason) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        weddingParticipantService.removeAndBlock(actorUserId, weddingId, targetUserId, reason);

        audit("WEDDING_PARTICIPANT_BLOCKED", "WARNING", true, actorUserId,
                "Participant blocked: weddingId=" + weddingId + ", userId=" + targetUserId,
                "Wedding", weddingId, null);

        return wedding;
    }

    public Wedding unblockParticipant(Long actorUserId, Long weddingId, Long targetUserId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        weddingParticipantService.unblock(actorUserId, weddingId, targetUserId);

        audit("WEDDING_PARTICIPANT_UNBLOCKED", "NOTICE", true, actorUserId,
                "Participant unblocked: weddingId=" + weddingId + ", userId=" + targetUserId,
                "Wedding", weddingId, null);

        return wedding;
    }

    public void heartbeatParticipant(Long actorUserId, Long weddingId) {
        getUserOrThrow(actorUserId);
        getWeddingOrThrow(weddingId);

        weddingParticipantService.heartbeat(actorUserId, weddingId);

        audit("WEDDING_PARTICIPANT_HEARTBEAT", "INFO", true, actorUserId,
                "Heartbeat: weddingId=" + weddingId,
                "Wedding", weddingId, null);
    }

    /**
     * Wrapper חשוב: שינוי role רשמי (Owner/Admin).
     */
    public void setParticipantRole(Long actorUserId, Long weddingId, Long targetUserId, WeddingParticipantRole role) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is null");
        if (role == null) throw new IllegalArgumentException("role is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrAdmin(actor, wedding);
        ensureNotCancelled(wedding);

        if (Objects.equals(wedding.getOwnerUserId(), targetUserId) && role != WeddingParticipantRole.OWNER) {
            throw new IllegalStateException("Cannot set owner role to " + role);
        }

        forceSetRole(actorUserId, weddingId, targetUserId, role);

        audit("WEDDING_PARTICIPANT_ROLE_SET", "NOTICE", true, actorUserId,
                "Role set: weddingId=" + weddingId + ", userId=" + targetUserId + ", role=" + role,
                "Wedding", weddingId, null);
    }

    // =====================================================
    // ✅ 7) READ / FIND + JOIN LINK
    // =====================================================

    @Transactional(readOnly = true)
    public Wedding getWedding(Long weddingId) {
        return getWeddingOrThrow(weddingId);
    }

    @Transactional(readOnly = true)
    public Optional<Wedding> findByAccessCode(String accessCode) {
        if (isBlank(accessCode)) return Optional.empty();
        return weddingRepository.findByAccessCode(accessCode);
    }

    @Transactional(readOnly = true)
    public Optional<Wedding> findByWeddingToken(String weddingToken) {
        if (isBlank(weddingToken)) return Optional.empty();
        return weddingRepository.findByWeddingToken(weddingToken);
    }

    @Transactional(readOnly = true)
    public String buildJoinLink(String baseUrl, Long weddingId) {
        Wedding w = getWeddingOrThrow(weddingId);
        if (isBlank(baseUrl)) baseUrl = "";
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        String accessCode = w.getAccessCode();
        if (!isBlank(accessCode)) return baseUrl + "/wedding/join/" + accessCode;

        String token = w.getWeddingToken();
        if (!isBlank(token)) return baseUrl + "/wedding/join-token/" + token;

        return baseUrl + "/wedding/" + w.getId();
    }

    // =====================================================
    // ✅ 8) WRAPPERS FOR ALL REPO QUERIES + STATS
    // =====================================================

    @Transactional(readOnly = true) public List<Wedding> listAll() { return weddingRepository.findAll(); }
    @Transactional(readOnly = true) public List<Wedding> listActiveTrue() { return weddingRepository.findByActiveTrue(); }
    @Transactional(readOnly = true) public List<Wedding> listActiveFalse() { return weddingRepository.findByActiveFalse(); }
    @Transactional(readOnly = true) public List<Wedding> listByCreatedByUserId(Long userId) { return weddingRepository.findByCreatedByUserId(userId); }
    @Transactional(readOnly = true) public List<Wedding> listByOwnerUserId(Long ownerUserId) { return weddingRepository.findByOwnerUserId(ownerUserId); }
    @Transactional(readOnly = true) public List<Wedding> listByOwnerUserIdOrderByWeddingDateAsc(Long ownerUserId) { return weddingRepository.findByOwnerUserIdOrderByWeddingDateAsc(ownerUserId); }

    @Transactional(readOnly = true) public List<Wedding> listPlanned(LocalDateTime now) { return weddingRepository.findByWeddingDateAfter(now); }
    @Transactional(readOnly = true) public List<Wedding> listEnded(LocalDateTime now) { return weddingRepository.findByWeddingEndTimeBefore(now); }
    @Transactional(readOnly = true) public List<Wedding> listLive(LocalDateTime now) { return weddingRepository.findByWeddingDateBeforeAndWeddingEndTimeAfter(now, now); }

    @Transactional(readOnly = true) public List<Wedding> listByCity(String city) { return weddingRepository.findByCity(city); }
    @Transactional(readOnly = true) public List<Wedding> listByHallName(String hallName) { return weddingRepository.findByHallName(hallName); }
    @Transactional(readOnly = true) public List<Wedding> listByHallAddressContains(String address) { return weddingRepository.findByHallAddressContainingIgnoreCase(address); }
    @Transactional(readOnly = true) public List<Wedding> listByWeddingDateBetween(LocalDateTime start, LocalDateTime end) { return weddingRepository.findByWeddingDateBetween(start, end); }
    @Transactional(readOnly = true) public List<Wedding> listByWeddingEndTimeBetween(LocalDateTime start, LocalDateTime end) { return weddingRepository.findByWeddingEndTimeBetween(start, end); }

    @Transactional(readOnly = true) public List<Wedding> listAllowGlobalApprovalsByOwnerTrue() { return weddingRepository.findByAllowGlobalApprovalsByOwnerTrue(); }
    @Transactional(readOnly = true) public List<Wedding> listOwnerActiveTrue(Long ownerUserId) { return weddingRepository.findByOwnerUserIdAndActiveTrue(ownerUserId); }

    @Transactional(readOnly = true) public List<Wedding> listManuallyClosedTrue() { return weddingRepository.findByManuallyClosedTrue(); }
    @Transactional(readOnly = true) public List<Wedding> listManuallyClosedFalseAndActiveTrue() { return weddingRepository.findByManuallyClosedFalseAndActiveTrue(); }
    @Transactional(readOnly = true) public List<Wedding> listManuallyClosedFalseAndEnded(LocalDateTime now) { return weddingRepository.findByManuallyClosedFalseAndWeddingEndTimeBefore(now); }

    @Transactional(readOnly = true) public List<Wedding> listActivePlanned(LocalDateTime now) { return weddingRepository.findByWeddingDateAfterAndActiveTrue(now); }
    @Transactional(readOnly = true) public List<Wedding> listActiveLive(LocalDateTime now) { return weddingRepository.findByWeddingDateBeforeAndWeddingEndTimeAfterAndActiveTrue(now, now); }

    @Transactional(readOnly = true) public List<Wedding> listByBackgroundMode(BackgroundMode mode) { return weddingRepository.findByBackgroundMode(mode); }

    @Transactional(readOnly = true) public List<Wedding> listActiveTrueAndManuallyClosedFalse() { return weddingRepository.findByActiveTrueAndManuallyClosedFalse(); }
    @Transactional(readOnly = true) public List<Wedding> listActiveTrueAndLive(LocalDateTime now) { return weddingRepository.findByActiveTrueAndWeddingDateBeforeAndWeddingEndTimeAfter(now, now); }
    @Transactional(readOnly = true) public List<Wedding> listEndedButActiveTrue(LocalDateTime now) { return weddingRepository.findByWeddingEndTimeBeforeAndActiveTrue(now); }
    @Transactional(readOnly = true) public List<Wedding> listEndingSoon(LocalDateTime start, LocalDateTime end) { return weddingRepository.findByWeddingEndTimeBetweenOrderByWeddingEndTimeAsc(start, end); }

    @Transactional(readOnly = true) public List<Wedding> listActiveTrueAndPlanned(LocalDateTime now) { return weddingRepository.findByActiveTrueAndWeddingDateAfter(now); }
    @Transactional(readOnly = true) public List<Wedding> listActiveTrueAndNotEnded(LocalDateTime now) { return weddingRepository.findByActiveTrueAndWeddingEndTimeAfter(now); }
    @Transactional(readOnly = true) public List<Wedding> listPublicTrue() { return weddingRepository.findByIsPublicTrue(); }
    @Transactional(readOnly = true) public List<Wedding> listPublicFalse() { return weddingRepository.findByIsPublicFalse(); }
    @Transactional(readOnly = true) public List<Wedding> listAllowCandidatePoolTrue() { return weddingRepository.findByAllowCandidatePoolTrue(); }

    @Transactional(readOnly = true)
    public List<Wedding> listOwnerPlanned(Long ownerUserId, LocalDateTime now) {
        return weddingRepository.findByOwnerUserIdAndWeddingDateAfter(ownerUserId, now);
    }

    @Transactional(readOnly = true)
    public List<Wedding> listOwnerEnded(Long ownerUserId, LocalDateTime now) {
        return weddingRepository.findByOwnerUserIdAndWeddingEndTimeBefore(ownerUserId, now);
    }

    @Transactional(readOnly = true)
    public List<Wedding> listOwnerLive(Long ownerUserId, LocalDateTime now) {
        return weddingRepository.findByOwnerUserIdAndWeddingDateBeforeAndWeddingEndTimeAfter(ownerUserId, now, now);
    }

    // =====================================================
    // ✅ 8.5) UI SAFE LISTS (OPEN POOL FILTERING)
    // =====================================================

    @Transactional(readOnly = true)
    public List<Wedding> listPublicOpenWeddings() {
        return weddingRepository.findByIsPublicTrue()
                .stream()
                .filter(this::isPoolOpen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Wedding> listAllOpenPoolWeddings() {
        return weddingRepository.findByActiveTrue()
                .stream()
                .filter(this::isPoolOpen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Wedding> listOwnerOpenWeddings(Long ownerUserId) {
        if (ownerUserId == null) return Collections.emptyList();
        return weddingRepository.findByOwnerUserId(ownerUserId)
                .stream()
                .filter(this::isPoolOpen)
                .toList();
    }

    // =====================================================
    // ✅ 9) STATS
    // =====================================================

    @Transactional(readOnly = true) public long countByCity(String city) { return weddingRepository.countByCity(city); }
    @Transactional(readOnly = true) public long countActiveTrue() { return weddingRepository.countByActiveTrue(); }
    @Transactional(readOnly = true) public long countActiveFalse() { return weddingRepository.countByActiveFalse(); }
    @Transactional(readOnly = true) public long countManuallyClosedTrue() { return weddingRepository.countByManuallyClosedTrue(); }
    @Transactional(readOnly = true) public long countWeddingDateBefore(LocalDateTime now) { return weddingRepository.countByWeddingDateBefore(now); }
    @Transactional(readOnly = true) public long countWeddingEndTimeBefore(LocalDateTime now) { return weddingRepository.countByWeddingEndTimeBefore(now); }
    @Transactional(readOnly = true) public long countWeddingDateAfter(LocalDateTime now) { return weddingRepository.countByWeddingDateAfter(now); }
    @Transactional(readOnly = true) public long countByBackgroundMode(BackgroundMode mode) { return weddingRepository.countByBackgroundMode(mode); }

    // =====================================================
    // ✅ 10) STATUS HELPERS + POOL OPEN RULE
    // =====================================================

    @Transactional(readOnly = true)
    public String getTimeStatus(Long weddingId, LocalDateTime now) {
        Wedding w = getWeddingOrThrow(weddingId);
        if (now == null) now = LocalDateTime.now();

        if (w.getWeddingDate() == null || w.getWeddingEndTime() == null) return "UNKNOWN";
        if (now.isBefore(w.getWeddingDate())) return "PLANNED";
        if (!now.isAfter(w.getWeddingEndTime())) return "LIVE";
        return "ENDED";
    }

    @Transactional(readOnly = true)
    public String computeWeddingStatus(Long weddingId, LocalDateTime now) {
        Wedding w = getWeddingOrThrow(weddingId);
        return computeWeddingStatus(w, now);
    }

    private String computeWeddingStatus(Wedding w, LocalDateTime now) {
        if (w == null) return "UNKNOWN";
        if (now == null) now = LocalDateTime.now();

        Boolean deleted = tryGetBoolean(w, "isDeleted");
        if (deleted != null && deleted) return "DELETED";
        if (w.isCancelled()) return "CANCELLED";

        if (w.getWeddingDate() == null || w.getWeddingEndTime() == null) return "UNKNOWN";
        if (now.isBefore(w.getWeddingDate())) return "PLANNED";
        if (!now.isAfter(w.getWeddingEndTime())) return "LIVE";
        return "ENDED";
    }

    private boolean isPoolOpen(Wedding wedding) {
        if (wedding == null) return false;
        return wedding.isActive() && !wedding.isManuallyClosed() && !wedding.isCancelled();
    }

    // =====================================================
    // ✅ 11) VIEWS (Public / Private) — includes WP participantsCount
    // =====================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getWeddingView(Long actorUserId, Long weddingId) {
        User actor = getUserOrThrow(actorUserId);
        Wedding w = getWeddingOrThrow(weddingId);
        boolean includeSecrets = canSeeSecrets(actor, w);
        return buildWeddingView(w, includeSecrets);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWeddingPublicView(Long weddingId) {
        Wedding w = getWeddingOrThrow(weddingId);
        return buildWeddingView(w, false);
    }

    private Map<String, Object> buildWeddingView(Wedding w, boolean includeSecrets) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", w.getId());
        dto.put("name", w.getName());
        dto.put("weddingDate", w.getWeddingDate());
        dto.put("weddingEndTime", w.getWeddingEndTime());
        dto.put("status", computeWeddingStatus(w, LocalDateTime.now()));

        dto.put("active", w.isActive());
        dto.put("manuallyClosed", w.isManuallyClosed());
        dto.put("cancelled", w.isCancelled());

        dto.put("city", w.getCity());
        dto.put("hallName", w.getHallName());
        dto.put("hallAddress", w.getHallAddress());
        dto.put("notes", w.getNotes());

        dto.put("ownerUserId", w.getOwnerUserId());
        dto.put("coOwnerUserIds", extractUserIds(w.getCoOwners()));

        Long wid = w.getId();
        dto.put("participantsCount", (wid == null) ? 0 : countParticipantsSafe(wid));

        dto.put("backgroundMode", w.getBackgroundMode());
        dto.put("activeBackgroundId", w.getActiveBackgroundId());

        dto.put("backgroundImageUrl", safeGetString(w, "getBackgroundImageUrl"));
        dto.put("backgroundVideoUrl", safeGetString(w, "getBackgroundVideoUrl"));

        dto.put("isPublic", w.isPublic());
        dto.put("allowCandidatePool", w.isAllowCandidatePool());
        dto.put("allowGlobalApprovalsByOwner", w.isAllowGlobalApprovalsByOwner());

        if (includeSecrets) {
            dto.put("accessCode", w.getAccessCode());
            dto.put("weddingToken", w.getWeddingToken());
        }
        return dto;
    }

    private List<Long> extractUserIds(List<User> users) {
        if (users == null) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (User u : users) if (u != null && u.getId() != null) ids.add(u.getId());
        return ids;
    }

    private boolean canSeeSecrets(User actor, Wedding wedding) {
        if (actor == null || wedding == null) return false;
        if (tryUserFlag(actor, "isAdmin")) return true;

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId != null && ownerId.equals(actor.getId())) return true;

        List<User> coOwners = wedding.getCoOwners();
        if (coOwners != null && coOwners.stream().anyMatch(u -> u != null && Objects.equals(u.getId(), actor.getId()))) return true;

        Long wid = wedding.getId();
        return wid != null && weddingParticipantRepository.existsByWedding_IdAndUser_IdAndBlockedFalseAndRoleInWeddingIn(
                wid, actor.getId(), OWNER_ROLES
        );
    }

    // =====================================================
    // ✅ 12) Background moderation: mark unsuitable + fallback DEFAULT
    // =====================================================

    public Wedding markBackgroundUnsuitable(Long actorUserId, Long weddingId, Long backgroundId, String reason) {
        User actor = getUserOrThrow(actorUserId);
        requireAdmin(actor);

        Wedding wedding = getWeddingOrThrow(weddingId);

        WeddingBackground wb = weddingBackgroundRepository.findById(backgroundId)
                .orElseThrow(() -> new IllegalArgumentException("WeddingBackground not found: " + backgroundId));

        Long bgWeddingId = extractWeddingIdFromBackground(wb);
        if (bgWeddingId == null || !bgWeddingId.equals(weddingId)) {
            throw new IllegalArgumentException("Background does not belong to weddingId=" + weddingId);
        }

        safeInvoke(wb, "setUnsuitable", boolean.class, true);
        safeInvoke(wb, "setIsUnsuitable", boolean.class, true);
        safeInvoke(wb, "setUnsuitableByUserId", Long.class, actorUserId);
        safeInvoke(wb, "setUnsuitableReason", String.class, isBlank(reason) ? "Unsuitable" : reason);

        weddingBackgroundRepository.save(wb);

        if (Objects.equals(wedding.getActiveBackgroundId(), backgroundId)) {
            wedding.setActiveBackgroundId(null);
            wedding.setBackgroundMode(BackgroundMode.DEFAULT);
            wedding.setUpdatedByUserId(actorUserId);
            safeInvoke(wedding, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());
            wedding = weddingRepository.save(wedding);
        }

        audit("WEDDING_BACKGROUND_MARKED_UNSUITABLE", "WARNING", true, actorUserId,
                "Background marked unsuitable: weddingId=" + weddingId + ", backgroundId=" + backgroundId,
                "Wedding", weddingId, null);

        return wedding;
    }

    // =====================================================
    // ✅ INTERNAL: ROLE LOGIC (NO DOWNGRADE) + COOWNER SYNC
    // =====================================================

    private WeddingParticipantRole resolveDesiredRoleForUser(Wedding wedding, Long userId) {
        if (wedding == null || userId == null) return WeddingParticipantRole.PARTICIPANT;

        if (Objects.equals(wedding.getOwnerUserId(), userId)) return WeddingParticipantRole.OWNER;

        List<User> coOwners = wedding.getCoOwners();
        if (coOwners != null && coOwners.stream().anyMatch(u -> u != null && Objects.equals(u.getId(), userId))) {
            return WeddingParticipantRole.CO_OWNER;
        }

        return WeddingParticipantRole.PARTICIPANT;
    }

    private void ensureRoleAtLeast(Long actorUserId, Long weddingId, Long targetUserId, WeddingParticipantRole desired) {
        if (actorUserId == null || weddingId == null || targetUserId == null || desired == null) return;

        try {
            weddingParticipantService.joinWedding(actorUserId, weddingId, targetUserId, desired);
        } catch (Exception ignore) {}

        WeddingParticipantRole current = tryGetCurrentRole(weddingId, targetUserId);
        if (current == null) {
            try {
                weddingParticipantService.setRole(actorUserId, weddingId, targetUserId, desired);
            } catch (Exception ignore) {}
            return;
        }

        if (roleRank(current) >= roleRank(desired)) return;

        try {
            weddingParticipantService.setRole(actorUserId, weddingId, targetUserId, desired);
        } catch (Exception ignore) {}
    }

    private void forceSetRole(Long actorUserId, Long weddingId, Long targetUserId, WeddingParticipantRole role) {
        if (actorUserId == null || weddingId == null || targetUserId == null || role == null) return;
        try { weddingParticipantService.joinWedding(actorUserId, weddingId, targetUserId, role); } catch (Exception ignore) {}
        try { weddingParticipantService.setRole(actorUserId, weddingId, targetUserId, role); } catch (Exception ignore) {}
    }

    private int roleRank(WeddingParticipantRole role) {
        if (role == null) return 0;
        return switch (role) {
            case OWNER -> 3;
            case CO_OWNER -> 2;
            case PARTICIPANT -> 1;
        };
    }

    private WeddingParticipantRole tryGetCurrentRole(Long weddingId, Long userId) {
        try {
            Optional<WeddingParticipant> opt = weddingParticipantRepository.findByWedding_IdAndUser_Id(weddingId, userId);
            if (opt.isEmpty()) return null;

            WeddingParticipant wp = opt.get();
            Object v = safeInvokeGetter(wp, "getRoleInWedding");
            if (v instanceof WeddingParticipantRole r1) return r1;

            v = safeInvokeGetter(wp, "getRole");
            if (v instanceof WeddingParticipantRole r2) return r2;

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void syncCoOwnersToWp_NoDowngrade(Long actorUserId, Wedding wedding) {
        if (wedding == null || wedding.getId() == null) return;

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId != null) ensureRoleAtLeast(actorUserId, wedding.getId(), ownerId, WeddingParticipantRole.OWNER);

        List<User> coOwners = wedding.getCoOwners();
        if (coOwners == null || coOwners.isEmpty()) return;

        for (User u : coOwners) {
            if (u == null || u.getId() == null) continue;
            if (ownerId != null && ownerId.equals(u.getId())) continue;
            ensureRoleAtLeast(actorUserId, wedding.getId(), u.getId(), WeddingParticipantRole.CO_OWNER);
        }
    }

    private void tryDemoteMissingCoOwners(Long actorUserId, Long weddingId, Set<Long> keepCoOwners, Long ownerId) {
        try {
            Method m = weddingParticipantService.getClass().getMethod("listByWeddingAndRoles", Long.class, List.class);
            @SuppressWarnings("unchecked")
            List<WeddingParticipant> rows = (List<WeddingParticipant>) m.invoke(
                    weddingParticipantService, weddingId, List.of(WeddingParticipantRole.CO_OWNER)
            );
            if (rows == null) return;

            for (WeddingParticipant wp : rows) {
                Long uid = extractUserIdFromWp(wp);
                if (uid == null) continue;
                if (ownerId != null && ownerId.equals(uid)) continue;
                if (!keepCoOwners.contains(uid)) {
                    forceSetRole(actorUserId, weddingId, uid, WeddingParticipantRole.PARTICIPANT);
                }
            }
        } catch (Exception ignore) {}
    }

    private Long extractUserIdFromWp(WeddingParticipant wp) {
        if (wp == null) return null;
        try {
            Object u = safeInvokeGetter(wp, "getUser");
            if (u == null) return null;
            Object id = safeInvokeGetter(u, "getId");
            return (id instanceof Long) ? (Long) id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void removeFromCoOwnersList(Wedding wedding, Long userId) {
        if (wedding == null || userId == null) return;
        List<User> coOwners = wedding.getCoOwners();
        if (coOwners == null) return;
        coOwners.removeIf(u -> u != null && Objects.equals(u.getId(), userId));
        wedding.setCoOwners(coOwners);
    }

    private void tryAddCoOwnerToWeddingEntity(Wedding wedding, Long coOwnerUserId) {
        if (wedding == null || coOwnerUserId == null) return;
        try {
            User coOwner = getUserOrThrow(coOwnerUserId);
            List<User> coOwners = wedding.getCoOwners();
            if (coOwners == null) coOwners = new ArrayList<>();
            boolean exists = coOwners.stream().anyMatch(u -> u != null && Objects.equals(u.getId(), coOwnerUserId));
            if (!exists) coOwners.add(coOwner);
            wedding.setCoOwners(coOwners);
        } catch (Exception ignore) {}
    }

    // =====================================================
    // ✅ INTERNAL: VALIDATION / BACKGROUND CHECK
    // =====================================================

    private void ensureChronology(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return;
        if (!end.isAfter(start)) throw new IllegalArgumentException("weddingEndTime must be AFTER weddingDate");
    }

    private void ensureNotCancelled(Wedding wedding) {
        if (wedding.isCancelled()) throw new IllegalStateException("Wedding is cancelled");
    }

    private void ensurePoolOpen(Wedding wedding) {
        if (!wedding.isActive()) throw new IllegalStateException("Wedding is inactive");
        if (wedding.isManuallyClosed()) throw new IllegalStateException("Wedding pool is manually closed");
        if (wedding.isCancelled()) throw new IllegalStateException("Wedding is cancelled");
    }

    private void ensureAccessCodeUniqueOrThrow(String accessCode, Long currentWeddingId) {
        if (isBlank(accessCode)) throw new IllegalArgumentException("accessCode is blank");
        Optional<Wedding> existing = weddingRepository.findByAccessCode(accessCode);
        if (existing.isPresent() && (currentWeddingId == null || !existing.get().getId().equals(currentWeddingId))) {
            throw new IllegalArgumentException("accessCode already exists");
        }
    }

    private void ensureTokenUniqueOrThrow(String token, Long currentWeddingId) {
        if (isBlank(token)) throw new IllegalArgumentException("weddingToken is blank");
        Optional<Wedding> existing = weddingRepository.findByWeddingToken(token);
        if (existing.isPresent() && (currentWeddingId == null || !existing.get().getId().equals(currentWeddingId))) {
            throw new IllegalArgumentException("weddingToken already exists");
        }
    }

    private String generateUniqueAccessCode() {
        for (int i = 0; i < UNIQUE_RETRY; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT).substring(0, 8);
            if (!weddingRepository.existsByAccessCode(candidate)) return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private String generateUniqueToken() {
        for (int i = 0; i < UNIQUE_RETRY; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            if (!weddingRepository.existsByWeddingToken(candidate)) return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private void validateBackgroundBelongsToWeddingOrThrow(Long backgroundId, Long weddingId) {
        WeddingBackground wb = weddingBackgroundRepository.findById(backgroundId)
                .orElseThrow(() -> new IllegalArgumentException("WeddingBackground not found: " + backgroundId));

        Long bgWeddingId = extractWeddingIdFromBackground(wb);
        if (bgWeddingId == null || !bgWeddingId.equals(weddingId)) {
            throw new IllegalArgumentException("Background does not belong to weddingId=" + weddingId);
        }

        if (!isBackgroundUsable(wb)) {
            throw new IllegalStateException("Background is not usable (inactive/deleted/unsuitable)");
        }
    }

    private Long extractWeddingIdFromBackground(WeddingBackground wb) {
        try {
            Method getWedding = wb.getClass().getMethod("getWedding");
            Object weddingObj = getWedding.invoke(wb);
            if (weddingObj != null) {
                Method getId = weddingObj.getClass().getMethod("getId");
                Object idObj = getId.invoke(weddingObj);
                if (idObj instanceof Long) return (Long) idObj;
            }
        } catch (Exception ignore) {}

        try {
            Method getWeddingId = wb.getClass().getMethod("getWeddingId");
            Object idObj = getWeddingId.invoke(wb);
            if (idObj instanceof Long) return (Long) idObj;
        } catch (Exception ignore) {}

        return null;
    }

    private boolean isBackgroundUsable(WeddingBackground wb) {
        try {
            Method m = wb.getClass().getMethod("isUsable");
            Object v = m.invoke(wb);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Exception ignore) {}

        Boolean deleted = tryGetBoolean(wb, "isDeleted");
        if (deleted != null && deleted) return false;

        Boolean unsuitable = tryGetBoolean(wb, "isUnsuitable");
        if (unsuitable != null && unsuitable) return false;

        Boolean active = tryGetBoolean(wb, "isActive");
        if (active != null && !active) return false;

        return true;
    }

    private Boolean tryGetBoolean(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object v = m.invoke(obj);
            return (v instanceof Boolean) ? (Boolean) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    // =====================================================
    // ✅ INTERNAL: REPO GETTERS
    // =====================================================

    private Wedding getWeddingOrThrow(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found: " + weddingId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    // =====================================================
    // ✅ SECURITY HELPERS (WITH WP FALLBACK)
    // =====================================================

    private void requireAdmin(User actor) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (!tryUserFlag(actor, "isAdmin")) throw new SecurityException("Not allowed (admin required)");
    }

    private void requireAdminOrEventManager(User actor) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        boolean isAdmin = tryUserFlag(actor, "isAdmin");
        boolean isEventManager = tryUserFlag(actor, "isEventManager");
        if (!isAdmin && !isEventManager) throw new SecurityException("Not allowed (admin/eventManager required)");
    }

    private void requireAdminOrOwner(User actor, Wedding wedding) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (tryUserFlag(actor, "isAdmin")) return;

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId != null && ownerId.equals(actor.getId())) return;

        Long wid = wedding.getId();
        if (wid != null && weddingParticipantRepository.existsByWedding_IdAndUser_IdAndBlockedFalseAndRoleInWeddingIn(
                wid, actor.getId(), Set.of(WeddingParticipantRole.OWNER)
        )) return;

        throw new SecurityException("Not allowed (owner/admin required)");
    }

    private void requireOwnerOrAdmin(User actor, Wedding wedding) {
        requireAdminOrOwner(actor, wedding);
    }

    private void requireOwnerOrCoOwnerOrAdmin(User actor, Wedding wedding) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (tryUserFlag(actor, "isAdmin")) return;

        Long actorId = actor.getId();
        if (actorId == null) throw new SecurityException("Actor id is null");

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId != null && ownerId.equals(actorId)) return;

        List<User> coOwners = wedding.getCoOwners();
        if (coOwners != null && coOwners.stream().anyMatch(u -> u != null && Objects.equals(u.getId(), actorId))) return;

        Long weddingId = wedding.getId();
        if (weddingId != null) {
            boolean hasRoleInWp = weddingParticipantRepository.existsByWedding_IdAndUser_IdAndBlockedFalseAndRoleInWeddingIn(
                    weddingId, actorId, OWNER_ROLES
            );
            if (hasRoleInWp) return;
        }

        if (tryUserFlag(actor, "isEventManager")) return;

        throw new SecurityException("Not allowed (owner/coOwner/admin required)");
    }

    private void requireOwnerOrCoOwnerOrAdminOrSelf(User actor, Wedding wedding, Long targetUserId) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (targetUserId != null && Objects.equals(targetUserId, actor.getId())) return;
        requireOwnerOrCoOwnerOrAdmin(actor, wedding);
    }

    private boolean tryUserFlag(User actor, String methodName) {
        try {
            Method m = actor.getClass().getMethod(methodName);
            Object v = m.invoke(actor);
            return (v instanceof Boolean) && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    // =====================================================
    // ✅ UTIL: REFLECTION SAFE
    // =====================================================

    private Object safeInvokeGetter(Object target, String getterName) {
        if (target == null || getterName == null) return null;
        try {
            Method m = target.getClass().getMethod(getterName);
            return m.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String safeGetString(Object obj, String getterName) {
        Object v = safeInvokeGetter(obj, getterName);
        return (v instanceof String) ? (String) v : null;
    }

    private void safeInvokeIfPresent(Object target, String methodName, Class<?> paramType, Object arg) {
        if (target == null || methodName == null || paramType == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Exception ignore) {}
    }

    private void safeInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        if (target == null || methodName == null || paramType == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Exception ignore) {}
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // =====================================================
    // ✅ SAFE participants count (no hard dependency on WP service method names)
    // =====================================================

    private int countParticipantsSafe(Long weddingId) {
        try {
            Method m = weddingParticipantService.getClass().getMethod("countActive", Long.class);
            Object v = m.invoke(weddingParticipantService, weddingId);
            if (v instanceof Number n) {
                long c = n.longValue();
                return (c > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) c;
            }
        } catch (Exception ignore) {}

        try {
            Method m = weddingParticipantService.getClass().getMethod("count", Long.class);
            Object v = m.invoke(weddingParticipantService, weddingId);
            if (v instanceof Number n) {
                long c = n.longValue();
                return (c > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) c;
            }
        } catch (Exception ignore) {}

        return 0;
    }

    // =====================================================
    // ✅ AUDIT (SystemLog) - SAFE enum resolution
    // =====================================================

    private void audit(String actionName,
                       String severityName,
                       boolean success,
                       Long actorUserId,
                       String details,
                       String relatedEntityType,
                       Long relatedEntityId,
                       String contextJson) {
        try {
            if (systemLogRepository == null) return;

            SystemActionType actionType = safeEnum(SystemActionType.class, actionName);
            SystemSeverityLevel severity = safeEnum(SystemSeverityLevel.class, severityName);

            SystemModule module = safeEnum(SystemModule.class, "WEDDING_SERVICE");
            if (module == null) module = safeEnum(SystemModule.class, "WEDDING");

            SystemLog log = new SystemLog(
                    actionType != null ? actionType : SystemActionType.values()[0],
                    module != null ? module : SystemModule.values()[0],
                    severity != null ? severity : SystemSeverityLevel.values()[0],
                    success,
                    actorUserId,
                    details + " | rawAction=" + actionName + " rawSeverity=" + severityName
            );

            safeInvoke(log, "setRelatedEntityType", String.class, relatedEntityType);
            safeInvoke(log, "setRelatedEntityId", Long.class, relatedEntityId);
            safeInvoke(log, "setContextJson", String.class, contextJson);
            safeInvoke(log, "setAutomated", boolean.class, false);

            systemLogRepository.save(log);
        } catch (Exception ignore) {
            // never fail business operation because of audit
        }
    }

    private <E extends Enum<E>> E safeEnum(Class<E> enumClass, String name) {
        if (enumClass == null || name == null) return null;
        try {
            return Enum.valueOf(enumClass, name);
        } catch (Exception e) {
            return null;
        }
    }
}
