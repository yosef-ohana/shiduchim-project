package com.example.myproject.controller.useraction.user;

import com.example.myproject.model.UserAction;
import com.example.myproject.service.UserActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 🎯 UserActionUserController
 *
 * שכבת API למשתמש הרגיל:
 * - לייק / דיסלייק / הקפאה / ביטול הקפאה / צפייה בפרופיל
 * - רשימות אישיות (LIKE / DISLIKE / FREEZE / VIEW)
 * - היסטוריית פעולות שלי (Actor)
 * - פעולות שנעשו עליי (Target)
 *
 * כל המתודות פה משתמשות ישירות ב-UserActionService כפי שהגדרת.
 */
@RestController
@RequestMapping("/api/users/{userId}/actions")
public class UserActionUserController {

    private final UserActionService userActionService;

    public UserActionUserController(UserActionService userActionService) {
        this.userActionService = userActionService;
    }

    // =====================================================
    // 1️⃣ פעולות ליבה: LIKE / DISLIKE / FREEZE / UNFREEZE / VIEW
    // =====================================================

    /**
     * 👍 לייק למשתמש אחר.
     * - יוצר UserAction מסוג LIKE
     * - listName = "LIKE"
     * - מכבה DISLIKE ו-FREEZE קודמים לאותו יעד
     *
     * POST /api/users/{userId}/actions/like/{targetUserId}?weddingId=123
     */
    @PostMapping("/like/{targetUserId}")
    public ResponseEntity<?> likeUser(@PathVariable("userId") Long actorId,
                                      @PathVariable Long targetUserId,
                                      @RequestParam(value = "weddingId", required = false) Long weddingId) {
        try {
            UserAction action = userActionService.likeUser(actorId, targetUserId, weddingId);
            return ResponseEntity.ok(action);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    /**
     * 👎 דיסלייק למשתמש אחר.
     * - יוצר UserAction מסוג DISLIKE
     * - listName = "DISLIKE"
     * - מכבה LIKE קודמים
     * - שולח התראה דרך NotificationService (USER_DISLIKED)
     *
     * POST /api/users/{userId}/actions/dislike/{targetUserId}?weddingId=123
     */
    @PostMapping("/dislike/{targetUserId}")
    public ResponseEntity<?> dislikeUser(@PathVariable("userId") Long actorId,
                                         @PathVariable Long targetUserId,
                                         @RequestParam(value = "weddingId", required = false) Long weddingId) {
        try {
            UserAction action = userActionService.dislikeUser(actorId, targetUserId, weddingId);
            return ResponseEntity.ok(action);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    /**
     * 🧊 הקפאת משתמש (FREEZE).
     * - מכניס את היעד לרשימת "FREEZE"
     * - מכבה FREEZE קודמים
     * - שולח התראה USER_FROZEN
     *
     * POST /api/users/{userId}/actions/freeze/{targetUserId}?weddingId=123
     */
    @PostMapping("/freeze/{targetUserId}")
    public ResponseEntity<?> freezeUser(@PathVariable("userId") Long actorId,
                                        @PathVariable Long targetUserId,
                                        @RequestParam(value = "weddingId", required = false) Long weddingId) {
        try {
            UserAction action = userActionService.freezeUser(actorId, targetUserId, weddingId);
            return ResponseEntity.ok(action);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    /**
     * 🔄 ביטול הקפאה (UNFREEZE).
     * - מכבה כל פעולות FREEZE פעילות
     * - יוצר פעולה היסטורית UNFREEZE
     * - שולח התראה USER_UNFROZEN
     *
     * POST /api/users/{userId}/actions/unfreeze/{targetUserId}
     */
    @PostMapping("/unfreeze/{targetUserId}")
    public ResponseEntity<?> unfreezeUser(@PathVariable("userId") Long actorId,
                                          @PathVariable Long targetUserId) {
        try {
            UserAction action = userActionService.unfreezeUser(actorId, targetUserId);
            return ResponseEntity.ok(action);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    /**
     * 👁️ צפייה בפרופיל (VIEW).
     * - listName = "VIEW"
     * - משמש לסטטיסטיקות צפיות בפרופיל
     *
     * POST /api/users/{userId}/actions/view/{targetUserId}?weddingId=123
     */
    @PostMapping("/view/{targetUserId}")
    public ResponseEntity<?> viewProfile(@PathVariable("userId") Long actorId,
                                         @PathVariable Long targetUserId,
                                         @RequestParam(value = "weddingId", required = false) Long weddingId) {
        try {
            UserAction action = userActionService.viewProfile(actorId, targetUserId, weddingId);
            return ResponseEntity.ok(action);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    // =====================================================
    // 2️⃣ רשימות אישיות – LIKE / DISLIKE / FREEZE / VIEW
    // =====================================================

    /**
     * כל הרשומות הפעילות של המשתמש ברשימה מסוימת (LIKE / DISLIKE / FREEZE / VIEW / MAYBE…)
     *
     * GET /api/users/{userId}/actions/lists/{listName}
     */
    @GetMapping("/lists/{listName}")
    public ResponseEntity<List<UserAction>> getListForUser(@PathVariable("userId") Long actorId,
                                                           @PathVariable String listName) {
        List<UserAction> list = userActionService.getListForUser(actorId, listName);
        return ResponseEntity.ok(list);
    }

    /**
     * כל הלייקים הפעילים שלי.
     *
     * GET /api/users/{userId}/actions/my/likes
     */
    @GetMapping("/my/likes")
    public ResponseEntity<List<UserAction>> getMyLikes(@PathVariable("userId") Long actorId) {
        return ResponseEntity.ok(userActionService.getMyLikes(actorId));
    }

    /**
     * כל הדיסלייקים הפעילים שלי.
     *
     * GET /api/users/{userId}/actions/my/dislikes
     */
    @GetMapping("/my/dislikes")
    public ResponseEntity<List<UserAction>> getMyDislikes(@PathVariable("userId") Long actorId) {
        return ResponseEntity.ok(userActionService.getMyDislikes(actorId));
    }

    /**
     * כל ה-"מקפיא" הפעילים שלי.
     *
     * GET /api/users/{userId}/actions/my/freezes
     */
    @GetMapping("/my/freezes")
    public ResponseEntity<List<UserAction>> getMyFreezes(@PathVariable("userId") Long actorId) {
        return ResponseEntity.ok(userActionService.getMyFreezes(actorId));
    }

    /**
     * כל צפיות הפרופיל שאני ביצעתי (VIEW) כ-Actor.
     *
     * GET /api/users/{userId}/actions/my/views
     */
    @GetMapping("/my/views")
    public ResponseEntity<List<UserAction>> getMyProfileViews(@PathVariable("userId") Long actorId) {
        return ResponseEntity.ok(userActionService.getMyProfileViews(actorId));
    }

    // =====================================================
    // 3️⃣ היסטוריה שלי (Actor) + פעולות שנעשו עליי (Target)
    // =====================================================

    /**
     * כל הפעולות שאני עשיתי (Actor).
     *
     * GET /api/users/{userId}/actions/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<UserAction>> getMyActionsHistory(@PathVariable("userId") Long actorId) {
        return ResponseEntity.ok(userActionService.getActionsByActor(actorId));
    }

    /**
     * כל הפעולות הפעילות שאני עשיתי (Actor, active=true).
     *
     * GET /api/users/{userId}/actions/history/active
     */
    @GetMapping("/history/active")
    public ResponseEntity<List<UserAction>> getMyActiveActions(@PathVariable("userId") Long actorId) {
        return ResponseEntity.ok(userActionService.getActiveActionsByActor(actorId));
    }

    /**
     * כל הפעולות שנעשו עליי (אני Target).
     *
     * GET /api/users/{userId}/actions/on-me
     */
    @GetMapping("/on-me")
    public ResponseEntity<List<UserAction>> getActionsOnMe(@PathVariable("userId") Long targetId) {
        return ResponseEntity.ok(userActionService.getActionsByTarget(targetId));
    }

    /**
     * כל הפעולות הפעילות שנעשו עליי (Target, active=true).
     *
     * GET /api/users/{userId}/actions/on-me/active
     */
    @GetMapping("/on-me/active")
    public ResponseEntity<List<UserAction>> getActiveActionsOnMe(@PathVariable("userId") Long targetId) {
        return ResponseEntity.ok(userActionService.getActiveActionsByTarget(targetId));
    }
}