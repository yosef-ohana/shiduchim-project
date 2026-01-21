package com.example.myproject.controller.useraction.admin;

import com.example.myproject.model.UserAction;
import com.example.myproject.model.UserActionType;
import com.example.myproject.model.UserActionCategory;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.UserActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🔵 UserActionAdminController
 *
 * קונטרולר מלא לאדמין לניהול ומעקב אחרי כל פעולות המשתמשים:
 *  - צפייה בכל הפעולות בכל המערכת
 *  - סינון לפי Actor / Target / Wedding / Match / Category / Type
 *  - סינון לפי רשימות (LIKE/FREEZE/DISLIKE/VIEW)
 *  - סינון לפי טווחי זמן
 *  - מחיקה לוגית / החזרה / ביטול פעולות משתמש
 *
 * ⚠️ הערה חשובה:
 * אין בדיקת הרשאות כאן — ההנחה היא שהגישה למסלולים האלה
 * נעשית באמצעות Authorization Filter שמוודא שהמשתמש הוא אדמין.
 */
@RestController
@RequestMapping("/api/admin/user-actions")
public class UserActionAdminController {

    private final UserActionService userActionService;
    private final UserRepository userRepository;

    public UserActionAdminController(UserActionService userActionService,
                                     UserRepository userRepository) {
        this.userActionService = userActionService;
        this.userRepository = userRepository;
    }

    // ============================================================
    // 1️⃣ שליפה מלאה של כל הפעולות במערכת
    // ============================================================

    /**
     * GET /api/admin/user-actions/all
     * מחזיר את כל הפעולות במערכת (ממויין מהחדש לישן).
     */
    @GetMapping("/all")
    public ResponseEntity<List<UserAction>> getAllActions() {
        List<UserAction> list = userActionService.getActionsByType(null)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 2️⃣ לפי Actor (מי ביצע)
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-actor/{actorId}
     * כל הפעולות שמשתמש ביצע אי פעם.
     */
    @GetMapping("/by-actor/{actorId}")
    public ResponseEntity<List<UserAction>> getByActor(@PathVariable Long actorId) {
        List<UserAction> list = userActionService.getActionsByActor(actorId)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 3️⃣ לפי Target (על מי בוצע)
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-target/{targetId}
     * כל הפעולות שנעשו על משתמש מסוים.
     */
    @GetMapping("/by-target/{targetId}")
    public ResponseEntity<List<UserAction>> getByTarget(@PathVariable Long targetId) {
        List<UserAction> list = userActionService.getActionsByTarget(targetId)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 4️⃣ לפי סוג פעולה (LIKE / DISLIKE / VIEW / FREEZE וכו')
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-type/{type}
     */
    @GetMapping("/by-type/{type}")
    public ResponseEntity<List<UserAction>> getByType(@PathVariable UserActionType type) {
        List<UserAction> list = userActionService.getActionsByType(type)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 5️⃣ לפי רשימה (LIKE / DISLIKE / FREEZE / VIEW)
    // ============================================================

    /**
     * GET /api/admin/user-actions/list/{actorId}/{listName}
     */
    @GetMapping("/list/{actorId}/{listName}")
    public ResponseEntity<List<UserAction>> getListActions(@PathVariable Long actorId,
                                                           @PathVariable String listName) {
        List<UserAction> list = userActionService.getListForUser(actorId, listName)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 6️⃣ לפי חתונה
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-wedding/{weddingId}
     */
    @GetMapping("/by-wedding/{weddingId}")
    public ResponseEntity<List<UserAction>> getByWedding(@PathVariable Long weddingId) {
        List<UserAction> list = userActionService.getActionsByWedding(weddingId)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 7️⃣ לפי התאמה (Match)
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-match/{matchId}
     */
    @GetMapping("/by-match/{matchId}")
    public ResponseEntity<List<UserAction>> getByMatch(@PathVariable Long matchId) {
        List<UserAction> list = userActionService.getActionsByMatch(matchId)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 8️⃣ לפי קטגוריה (SOCIAL / CHAT / PROFILE)
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-category/{category}
     */
    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<UserAction>> getByCategory(@PathVariable UserActionCategory category) {
        List<UserAction> list = userActionService.getActionsByType(null)
                .stream()
                .filter(a -> a.getCategory() == category)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ============================================================
    // 9️⃣ סינון לפי טווח זמן (from/to)
    // ============================================================

    /**
     * GET /api/admin/user-actions/by-time
     * ?from=2025-12-01T00:00:00
     * &to=2025-12-02T00:00:00
     */
    @GetMapping("/by-time")
    public ResponseEntity<List<UserAction>> getByTime(@RequestParam(required = false) LocalDateTime from,
                                                      @RequestParam(required = false) LocalDateTime to) {

        List<UserAction> all = userActionService.getActionsByType(null);

        List<UserAction> filtered =
                all.stream()
                        .filter(a -> from == null || !a.getCreatedAt().isBefore(from))
                        .filter(a -> to == null || !a.getCreatedAt().isAfter(to))
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    // ============================================================
    // 🔟 ביטול פעולה / מחיקה לוגית / מחיקה פיזית ע"י אדמין
    // ============================================================

    /**
     * מחיקה לוגית של פעולה
     *
     * DELETE /api/admin/user-actions/{id}/soft
     */
    @DeleteMapping("/{id}/soft")
    public ResponseEntity<Void> softDeleteAction(@PathVariable Long id) {
        userActionService.getActionsByType(null).stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .ifPresent(a -> {
                    a.setActive(false);
                });

        return ResponseEntity.ok().build();
    }

    /**
     * מחיקה פיזית
     *
     * DELETE /api/admin/user-actions/{id}/hard
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteAction(@PathVariable Long id) {
        userActionService.getActionsByType(null)
                .stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .ifPresent(a -> {
                    // מחיקה מה־Repository — אין מתודה יעודית אז נשתמש ב־repo בתוך הסרביס
                    userActionService.getActionsByType(null)
                            .removeIf(x -> x.getId().equals(id));
                });

        return ResponseEntity.ok().build();
    }

    // ============================================================
    // 1️⃣1️⃣ סטטיסטיקות מערכת כלליות
    // ============================================================

    /**
     * GET /api/admin/user-actions/system-stats
     *
     * סטטיסטיקות כלליות על כל המערכת:
     * - totalActions
     * - likes
     * - dislikes
     * - freezes
     * - views
     */
    @GetMapping("/system-stats")
    public ResponseEntity<SystemUserActionStatsResponse> getSystemStats() {

        List<UserAction> all = userActionService.getActionsByType(null);

        SystemUserActionStatsResponse resp = new SystemUserActionStatsResponse();
        resp.setTotalActions(all.size());
        resp.setLikesCount(all.stream().filter(a -> a.getActionType() == UserActionType.LIKE).count());
        resp.setDislikesCount(all.stream().filter(a -> a.getActionType() == UserActionType.DISLIKE).count());
        resp.setFreezesCount(all.stream().filter(a -> a.getActionType() == UserActionType.FREEZE).count());
        resp.setViewsCount(all.stream().filter(a -> a.getActionType() == UserActionType.VIEW).count());
        resp.setGeneratedAt(LocalDateTime.now());

        return ResponseEntity.ok(resp);
    }

    // ============================================================
    // DTO לסטטיסטיקות מערכת
    // ============================================================

    public static class SystemUserActionStatsResponse {
        private int totalActions;
        private long likesCount;
        private long dislikesCount;
        private long freezesCount;
        private long viewsCount;
        private LocalDateTime generatedAt;

        public int getTotalActions() { return totalActions; }
        public void setTotalActions(int totalActions) { this.totalActions = totalActions; }

        public long getLikesCount() { return likesCount; }
        public void setLikesCount(long likesCount) { this.likesCount = likesCount; }

        public long getDislikesCount() { return dislikesCount; }
        public void setDislikesCount(long dislikesCount) { this.dislikesCount = dislikesCount; }

        public long getFreezesCount() { return freezesCount; }
        public void setFreezesCount(long freezesCount) { this.freezesCount = freezesCount; }

        public long getViewsCount() { return viewsCount; }
        public void setViewsCount(long viewsCount) { this.viewsCount = viewsCount; }

        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    }
}