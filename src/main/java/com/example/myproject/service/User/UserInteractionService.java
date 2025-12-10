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
 * UserInteractionService
 *
 * מנהל את כל פעולות המשתמשים זה מול זה:
 * - LIKE
 * - DISLIKE
 * - FREEZE
 * - UNFREEZE (באמצעות מחיקת פעולה / פעולה הפוכה)
 *
 * שלב זה מטפל רק ב-UserAction.
 * לוגיקת Match / Chat / Notifications מתבצעת בשכבות ייעודיות (MatchService, ChatMessageService, NotificationService).
 */
@Service
@Transactional
public class UserInteractionService {

    private final UserRepository userRepository;
    private final UserActionRepository userActionRepository;

    public UserInteractionService(UserRepository userRepository,
                                  UserActionRepository userActionRepository) {
        this.userRepository = userRepository;
        this.userActionRepository = userActionRepository;
    }

    // =====================================================
    // 🔵 LIKE
    // =====================================================

    public UserAction likeUser(Long actorId, Long targetId, String note) {
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("User cannot like himself");
        }

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        UserAction action = new UserAction();
        action.setActor(actor);
        action.setTarget(target);
        action.setActionType(UserActionType.LIKE);
        action.setReason(note);
        action.setCreatedAt(LocalDateTime.now());

        return userActionRepository.save(action);
    }

    // =====================================================
    // 🔵 DISLIKE
    // =====================================================

    public UserAction dislikeUser(Long actorId, Long targetId, String note) {
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("User cannot dislike himself");
        }

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        UserAction action = new UserAction();
        action.setActor(actor);
        action.setTarget(target);
        action.setActionType(UserActionType.DISLIKE);
        action.setReason(note);
        action.setCreatedAt(LocalDateTime.now());

        return userActionRepository.save(action);
    }

    // =====================================================
    // 🔵 FREEZE (הקפאת משתמש ברשימה)
    // =====================================================

    public UserAction freezeUser(Long actorId, Long targetId, String note) {
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("User cannot freeze himself");
        }

        User actor = getUserOrThrow(actorId);
        User target = getUserOrThrow(targetId);

        UserAction action = new UserAction();
        action.setActor(actor);
        action.setTarget(target);
        action.setActionType(UserActionType.FREEZE);
        action.setReason(note);
        action.setCreatedAt(LocalDateTime.now());

        return userActionRepository.save(action);
    }

    // =====================================================
    // 🔵 ביטול FREEZE / ביטול פעולה
    // =====================================================

    public void unfreezeUser(Long actorId, Long targetId) {
        userActionRepository.deleteByActorIdAndTargetIdAndActionType(
                actorId, targetId, UserActionType.FREEZE
        );
    }

    public void removeAllInteractions(Long actorId, Long targetId) {
        userActionRepository.deleteByActorIdAndTargetId(actorId, targetId);
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}