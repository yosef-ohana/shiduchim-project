package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.UserAction;
import com.example.myproject.model.enums.UserActionType;
import com.example.myproject.repository.UserActionRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UserSecurityService
 *
 * אחראי על:
 * - חסימת משתמשים (Block)
 * - ביטול חסימה (Unblock)
 * - בדיקה האם מותר למשתמש A לראות / לפתוח צ'אט עם משתמש B
 * - כיבוד דגלי allowProfileViewByOppositeGender / allowProfileViewBySameGender
 */
@Service
@Transactional
public class UserSecurityService {

    private final UserRepository userRepository;
    private final UserActionRepository userActionRepository;

    public UserSecurityService(UserRepository userRepository,
                               UserActionRepository userActionRepository) {
        this.userRepository = userRepository;
        this.userActionRepository = userActionRepository;
    }

    // =====================================================
    // 🔵 חסימה / ביטול חסימה
    // =====================================================

    public void blockUser(Long actorId, Long targetId, String reason) {
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("User cannot block himself");
        }

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        if (isBlocked(actorId, targetId)) {
            return; // כבר חסום – לא עושים כלום
        }

        UserAction action = new UserAction();
        action.setActor(actor);
        action.setTarget(target);
        action.setActionType(UserActionType.BLOCK);
        action.setReason(reason);
        action.setCreatedAt(LocalDateTime.now());

        userActionRepository.save(action);
    }

    public void unblockUser(Long actorId, Long targetId) {
        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        userActionRepository.deleteByActorIdAndTargetIdAndActionType(
                actor.getId(), target.getId(), UserActionType.BLOCK
        );
    }

    // =====================================================
    // 🔵 בדיקות חסימה
    // =====================================================

    /**
     * האם actor חסם את target.
     */
    public boolean isBlocked(Long actorId, Long targetId) {
        return userActionRepository.existsByActorIdAndTargetIdAndActionType(
                actorId, targetId, UserActionType.BLOCK
        );
    }

    /**
     * האם יש חסימה דו-כיוונית: אחד מהצדדים חסם את השני.
     */
    public boolean isMutuallyBlocked(Long userId1, Long userId2) {
        return isBlocked(userId1, userId2) || isBlocked(userId2, userId1);
    }

    // =====================================================
    // 🔵 האם מותר לצפות בפרופיל
    // =====================================================

    public boolean canViewProfile(Long viewerId, Long targetId) {
        if (viewerId.equals(targetId)) {
            return true;
        }

        User viewer = getUserOrThrow(viewerId);
        User target = getUserOrThrow(targetId);

        if (isMutuallyBlocked(viewerId, targetId)) {
            return false;
        }

        // מגבלות מגדר
        boolean sameGender = viewer.getGender() != null
                && viewer.getGender().equalsIgnoreCase(target.getGender());

        if (sameGender && !target.isAllowProfileViewBySameGender()) {
            return false;
        }

        if (!sameGender && !target.isAllowProfileViewByOppositeGender()) {
            return false;
        }

        // אפשר להוסיף כאן חוקים נוספים (למשל: צניעות, גיל מינימלי וכו')
        return true;
    }

    // =====================================================
    // 🔵 האם מותר לפתוח צ'אט
    // =====================================================

    public boolean canOpenChat(Long viewerId, Long targetId) {
        if (!canViewProfile(viewerId, targetId)) {
            return false;
        }

        User viewer = getUserOrThrow(viewerId);
        User target = getUserOrThrow(targetId);

        if (viewer.isDeletionRequested() || target.isDeletionRequested()) {
            return false;
        }

        // ניתן להוסיף כאן חוקים מתקדמים: רק אחרי לייק הדדי, רק אחרי אישור מנהל וכו'.
        return true;
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}