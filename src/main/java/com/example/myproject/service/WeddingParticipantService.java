package com.example.myproject.service;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.model.WeddingParticipant;
import com.example.myproject.model.enums.WeddingParticipantRole;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingParticipantRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class WeddingParticipantService {

    private final WeddingParticipantRepository weddingParticipantRepository;
    private final WeddingRepository weddingRepository;
    private final UserRepository userRepository;

    private static final Set<WeddingParticipantRole> OWNER_ROLES =
            Set.of(WeddingParticipantRole.OWNER, WeddingParticipantRole.CO_OWNER);

    public WeddingParticipantService(WeddingParticipantRepository weddingParticipantRepository,
                                     WeddingRepository weddingRepository,
                                     UserRepository userRepository) {
        this.weddingParticipantRepository = weddingParticipantRepository;
        this.weddingRepository = weddingRepository;
        this.userRepository = userRepository;
    }

    // =====================================================
    // ✅ JOIN (barcode/link/manual) - SAFE (locked)
    // =====================================================

    public WeddingParticipant joinWedding(Long actorUserId, Long weddingId, Long targetUserId, WeddingParticipantRole roleIfNew) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdminOrSelf(actor, wedding, weddingId, targetUserId);
        ensurePoolOpen(wedding);

        User target = getUserOrThrow(targetUserId);

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, targetUserId).orElse(null);

        if (wp == null) {
            wp = new WeddingParticipant(wedding, target);
            wp.setRoleInWedding(roleIfNew == null ? WeddingParticipantRole.PARTICIPANT : roleIfNew);
            wp.setJoinedAt(LocalDateTime.now());
            wp.setLeftAt(null);
            wp.setBlocked(false);
            wp.setUpdatedByUserId(actorUserId);

            try {
                wp = weddingParticipantRepository.save(wp);
            } catch (DataIntegrityViolationException dup) {
                wp = weddingParticipantRepository.findForUpdate(weddingId, targetUserId)
                        .orElseThrow(() -> dup);
            }
        } else {
            if (wp.isBlocked()) {
                throw new IllegalStateException("User is blocked from this wedding");
            }
            if (wp.getLeftAt() != null) {
                wp.setLeftAt(null); // re-join
                wp.setUpdatedByUserId(actorUserId);
                wp = weddingParticipantRepository.save(wp);
            }
        }

        // Sync user context (best-effort)
        trySetUserContextJoin(target, weddingId);
        userRepository.save(target);

        return wp;
    }

    // =====================================================
    // ✅ LEAVE (self)
    // =====================================================

    public WeddingParticipant leaveWedding(Long actorUserId, Long weddingId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        Long userId = actor.getId();
        if (userId == null) throw new IllegalArgumentException("actor id null");

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Not a participant in this wedding"));

        if (wp.getRoleInWedding() == WeddingParticipantRole.OWNER) {
            throw new IllegalStateException("OWNER cannot leave wedding via leaveWedding()");
        }

        if (wp.getLeftAt() == null) {
            wp.setLeftAt(LocalDateTime.now());
            wp.setUpdatedByUserId(actorUserId);
            wp = weddingParticipantRepository.save(wp);
        }

        trySetUserContextLeave(actor, weddingId);
        userRepository.save(actor);

        return wp;
    }

    // =====================================================
    // ✅ REMOVE (Owner/Admin)  +  REMOVE+BLOCK  +  UNBLOCK
    // =====================================================

    /**
     * ✅ זה השם ש-WeddingService מצפה לו.
     */
    public WeddingParticipant remove(Long actorUserId, Long weddingId, Long targetUserId) {
        return removeParticipant(actorUserId, weddingId, targetUserId);
    }

    /**
     * ✅ זה השם ש-WeddingService מצפה לו.
     */
    public WeddingParticipant removeAndBlock(Long actorUserId, Long weddingId, Long targetUserId, String reason) {
        return removeAndBlockParticipant(actorUserId, weddingId, targetUserId, reason);
    }

    /**
     * ✅ זה השם ש-WeddingService מצפה לו.
     */
    public WeddingParticipant unblock(Long actorUserId, Long weddingId, Long targetUserId) {
        return unblockParticipant(actorUserId, weddingId, targetUserId);
    }

    // ---- Backward compatible names (למי שכבר קרא לזה ככה בקוד) ----

    public WeddingParticipant removeParticipant(Long actorUserId, Long weddingId, Long targetUserId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding, weddingId);

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Not a participant in this wedding"));

        if (wp.getRoleInWedding() == WeddingParticipantRole.OWNER) {
            throw new IllegalStateException("Cannot remove OWNER from wedding");
        }

        if (wp.getRoleInWedding() == WeddingParticipantRole.CO_OWNER) {
            requireAdminOrOwner(actor, wedding);
        }

        if (wp.getLeftAt() == null) wp.setLeftAt(LocalDateTime.now());
        wp.setUpdatedByUserId(actorUserId);
        wp = weddingParticipantRepository.save(wp);

        User target = getUserOrThrow(targetUserId);
        trySetUserContextLeave(target, weddingId);
        userRepository.save(target);

        return wp;
    }

    public WeddingParticipant removeAndBlockParticipant(Long actorUserId, Long weddingId, Long targetUserId, String reason) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding, weddingId);

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, targetUserId).orElse(null);

        if (wp == null) {
            User target = getUserOrThrow(targetUserId);
            wp = new WeddingParticipant(wedding, target);
            wp.setJoinedAt(LocalDateTime.now());
            wp.setRoleInWedding(WeddingParticipantRole.PARTICIPANT);
        }

        if (wp.getRoleInWedding() == WeddingParticipantRole.OWNER) {
            throw new IllegalStateException("Cannot block OWNER from wedding");
        }

        if (wp.getRoleInWedding() == WeddingParticipantRole.CO_OWNER) {
            requireAdminOrOwner(actor, wedding);
        }

        LocalDateTime now = LocalDateTime.now();
        wp.setLeftAt(now);
        wp.setBlocked(true);
        wp.setBlockedAt(now);
        wp.setBlockedByUserId(actorUserId);
        wp.setBlockReason(isBlank(reason) ? "Blocked from wedding" : reason.trim());
        wp.setUpdatedByUserId(actorUserId);

        wp = weddingParticipantRepository.save(wp);

        User target = getUserOrThrow(targetUserId);
        trySetUserContextLeave(target, weddingId);
        userRepository.save(target);

        return wp;
    }

    public WeddingParticipant unblockParticipant(Long actorUserId, Long weddingId, Long targetUserId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        requireOwnerOrCoOwnerOrAdmin(actor, wedding, weddingId);

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("No participation record found"));

        if (wp.getRoleInWedding() == WeddingParticipantRole.CO_OWNER) {
            requireAdminOrOwner(actor, wedding);
        }

        wp.setBlocked(false);
        wp.setBlockedAt(null);
        wp.setBlockedByUserId(null);
        wp.setBlockReason(null);
        wp.setUpdatedByUserId(actorUserId);

        return weddingParticipantRepository.save(wp);
    }

    // =====================================================
    // ✅ ROLE (used by WeddingService ensureRoleAtLeast/forceSetRole)
    // =====================================================

    public WeddingParticipant setRole(Long actorUserId, Long weddingId, Long targetUserId, WeddingParticipantRole role) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is null");
        if (role == null) throw new IllegalArgumentException("role is null");

        User actor = getUserOrThrow(actorUserId);
        Wedding wedding = getWeddingOrThrow(weddingId);

        // OWNER assign/change: admin only
        if (role == WeddingParticipantRole.OWNER) {
            if (!tryUserFlag(actor, "isAdmin")) {
                throw new SecurityException("Not allowed (admin required to assign OWNER role)");
            }
        } else {
            requireAdminOrOwner(actor, wedding);
        }

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, targetUserId).orElse(null);

        // If missing row: create WITHOUT enforcing pool open (role mgmt can be done even if pool closed)
        if (wp == null) {
            User target = getUserOrThrow(targetUserId);
            wp = new WeddingParticipant(wedding, target);
            wp.setJoinedAt(LocalDateTime.now());
            wp.setLeftAt(null);
            wp.setBlocked(false);
        }

        // protect current OWNER from non-admin edits
        if (wp.getRoleInWedding() == WeddingParticipantRole.OWNER && !tryUserFlag(actor, "isAdmin")) {
            throw new SecurityException("Not allowed (admin required to change OWNER role)");
        }

        wp.setRoleInWedding(role);
        wp.setUpdatedByUserId(actorUserId);

        return weddingParticipantRepository.save(wp);
    }

    // =====================================================
    // ✅ READS + helpers WeddingService expects
    // =====================================================

    @Transactional(readOnly = true)
    public List<WeddingParticipant> listActiveParticipants(Long weddingId) {
        if (weddingId == null) return Collections.emptyList();
        return weddingParticipantRepository.findByWedding_IdAndLeftAtIsNullAndBlockedFalseOrderByJoinedAtAsc(weddingId);
    }

    @Transactional(readOnly = true)
    public long countActive(Long weddingId) {
        if (weddingId == null) return 0;
        return weddingParticipantRepository.countByWedding_IdAndLeftAtIsNullAndBlockedFalse(weddingId);
    }

    /**
     * ✅ WeddingService משתמש בזה (אצלך היה reflection).
     * עשינו מימוש שמבוסס על history + סינון בזיכרון כדי לא להיות תלויים במתודות ריפו נוספות.
     */
    @Transactional(readOnly = true)
    public List<WeddingParticipant> listByWeddingAndRoles(Long weddingId, List<WeddingParticipantRole> roles) {
        if (weddingId == null) return Collections.emptyList();
        if (roles == null || roles.isEmpty()) return Collections.emptyList();

        List<WeddingParticipant> all = weddingParticipantRepository.findByWedding_IdOrderByJoinedAtDesc(weddingId);
        if (all == null || all.isEmpty()) return Collections.emptyList();

        Set<WeddingParticipantRole> set = new HashSet<>(roles);
        return all.stream()
                .filter(Objects::nonNull)
                .filter(wp -> wp.getRoleInWedding() != null && set.contains(wp.getRoleInWedding()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WeddingParticipant> listWeddingHistory(Long weddingId) {
        if (weddingId == null) return Collections.emptyList();
        return weddingParticipantRepository.findByWedding_IdOrderByJoinedAtDesc(weddingId);
    }

    @Transactional(readOnly = true)
    public List<WeddingParticipant> listUserHistory(Long userId) {
        if (userId == null) return Collections.emptyList();
        return weddingParticipantRepository.findByUser_IdOrderByJoinedAtDesc(userId);
    }

    // =====================================================
    // ✅ Heartbeat
    // =====================================================

    public void heartbeat(Long actorUserId, Long weddingId) {
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId is null");
        if (weddingId == null) throw new IllegalArgumentException("weddingId is null");

        WeddingParticipant wp = weddingParticipantRepository.findForUpdate(weddingId, actorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Not a participant in this wedding"));

        if (!wp.isActiveInWedding()) {
            throw new IllegalStateException("User is not active in this wedding (left/blocked)");
        }

        wp.setLastHeartbeatAt(LocalDateTime.now());
        wp.setUpdatedByUserId(actorUserId);
        weddingParticipantRepository.save(wp);
    }

    // =====================================================
    // INTERNAL helpers (security + context)
    // =====================================================

    private void ensurePoolOpen(Wedding wedding) {
        if (wedding.isCancelled()) throw new IllegalStateException("Wedding is cancelled");
        if (!wedding.isActive()) throw new IllegalStateException("Wedding is inactive");
        if (wedding.isManuallyClosed()) throw new IllegalStateException("Wedding pool is manually closed");
    }

    private Wedding getWeddingOrThrow(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found: " + weddingId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private void requireAdminOrOwner(User actor, Wedding wedding) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (tryUserFlag(actor, "isAdmin")) return;

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId == null || !ownerId.equals(actor.getId())) {
            throw new SecurityException("Not allowed (owner/admin required)");
        }
    }

    private void requireOwnerOrCoOwnerOrAdmin(User actor, Wedding wedding, Long weddingId) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (tryUserFlag(actor, "isAdmin")) return;

        Long actorId = actor.getId();
        if (actorId == null) throw new SecurityException("Actor id is null");

        Long ownerId = wedding.getOwnerUserId();
        if (ownerId != null && ownerId.equals(actorId)) return;

        List<User> coOwners = wedding.getCoOwners();
        if (coOwners != null && coOwners.stream().anyMatch(u -> u != null && Objects.equals(u.getId(), actorId))) return;

        boolean hasRoleInWp = weddingParticipantRepository.existsByWedding_IdAndUser_IdAndBlockedFalseAndRoleInWeddingIn(
                weddingId, actorId, OWNER_ROLES
        );
        if (hasRoleInWp) return;

        if (tryUserFlag(actor, "isEventManager")) return;

        throw new SecurityException("Not allowed (owner/coOwner/admin required)");
    }

    private void requireOwnerOrCoOwnerOrAdminOrSelf(User actor, Wedding wedding, Long weddingId, Long targetUserId) {
        if (actor == null) throw new IllegalArgumentException("Actor is null");
        if (targetUserId != null && targetUserId.equals(actor.getId())) return;
        requireOwnerOrCoOwnerOrAdmin(actor, wedding, weddingId);
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

    private void trySetUserContextJoin(User user, Long weddingId) {
        safeInvoke(user, "setActiveWeddingId", Long.class, weddingId);
        safeInvoke(user, "setWeddingEnterAt", LocalDateTime.class, LocalDateTime.now());
        safeInvoke(user, "setLastWeddingId", Long.class, weddingId);
    }

    private void trySetUserContextLeave(User user, Long weddingId) {
        safeInvoke(user, "setWeddingExitAt", LocalDateTime.class, LocalDateTime.now());
        safeInvoke(user, "setLastWeddingId", Long.class, weddingId);
        safeInvoke(user, "setActiveWeddingId", Long.class, null);
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