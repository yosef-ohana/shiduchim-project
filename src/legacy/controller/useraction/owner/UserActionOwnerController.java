package com.example.myproject.controller.useraction.owner;

import com.example.myproject.model.UserAction;
import com.example.myproject.model.enums.UserActionType;
import com.example.myproject.model.Wedding;
import com.example.myproject.repository.WeddingRepository;
import com.example.myproject.service.UserActionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 🔵 UserActionOwnerController
 *
 * קונטרולר לבעלי אירוע (Event Owners) לצפייה וסטטיסטיקות על פעולות משתמשים
 * בתוך חתונה מסוימת:
 *  - כל הפעולות שבוצעו בחתונה
 *  - פילוח לפי סוג פעולה: LIKE / DISLIKE / FREEZE / VIEW
 *  - סינון לפי טווח זמן (from/to) – אופציונלי
 *  - סטטיסטיקות מסכמות על הפעולות בחתונה
 *  - פעולות של משתמש מסוים בתוך חתונה
 *
 * ⚠️ ולידציית בעלות:
 *   לפני כל שליפה – בדיקה ש-wedding.ownerUserId == ownerUserId.
 *   אם לא:
 *     - Wedding לא קיימת → 404
 *     - Wedding קיימת אבל לא שייכת ל-ownerUserId → 403
 *
 * ⚙️ Service:
 *   - UserActionService.getActionsByWedding(weddingId)
 *   - UserActionService.getActionsByActor(actorId)      *ממנו נמסנן לפי weddingId*
 *
 * Base path:
 *   /api/owner/user-actions
 */
@RestController
@RequestMapping("/api/owner/user-actions")
public class UserActionOwnerController {

    private final UserActionService userActionService;
    private final WeddingRepository weddingRepository;

    public UserActionOwnerController(UserActionService userActionService,
                                     WeddingRepository weddingRepository) {
        this.userActionService = userActionService;
        this.weddingRepository = weddingRepository;
    }

    // ============================================================
    // 🧩 1. כל הפעולות בחתונה (עם אפשרות סינון זמן)
    // ============================================================

    /**
     * כל הפעולות בחתונה מסוימת, עבור בעל האירוע.
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}
     *    ?from=2025-12-01T00:00:00
     *    &to=2025-12-02T00:00:00
     *
     * from/to – אופציונלי, בפורמט ISO-8601.
     *
     * Response:
     * 200 OK  → רשימת UserAction (פעילים בלבד, active=true)
     * 403 FORBIDDEN → המשתמש אינו בעל האירוע
     * 404 NOT FOUND → החתונה לא נמצאה
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}")
    public ResponseEntity<List<UserAction>> getAllActionsForWedding(@PathVariable Long weddingId,
                                                                    @PathVariable Long ownerUserId,
                                                                    @RequestParam(required = false) LocalDateTime from,
                                                                    @RequestParam(required = false) LocalDateTime to) {
        try {
            getWeddingForOwnerOrThrow(weddingId, ownerUserId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<UserAction> all = userActionService.getActionsByWedding(weddingId);

        List<UserAction> filtered = filterByTimeAndActive(all, from, to);

        return ResponseEntity.ok(filtered);
    }

    // ============================================================
    // 🧩 2. פילוח לפי סוג פעולה – LIKE / DISLIKE / FREEZE / VIEW
    // ============================================================

    /**
     * כל ה-LIKE בחתונה זו.
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}/likes
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}/likes")
    public ResponseEntity<List<UserAction>> getLikesInWedding(@PathVariable Long weddingId,
                                                              @PathVariable Long ownerUserId,
                                                              @RequestParam(required = false) LocalDateTime from,
                                                              @RequestParam(required = false) LocalDateTime to) {
        return getActionsByTypeInWedding(weddingId, ownerUserId, UserActionType.LIKE, from, to);
    }

    /**
     * כל ה-DISLIKE בחתונה זו.
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}/dislikes
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}/dislikes")
    public ResponseEntity<List<UserAction>> getDislikesInWedding(@PathVariable Long weddingId,
                                                                 @PathVariable Long ownerUserId,
                                                                 @RequestParam(required = false) LocalDateTime from,
                                                                 @RequestParam(required = false) LocalDateTime to) {
        return getActionsByTypeInWedding(weddingId, ownerUserId, UserActionType.DISLIKE, from, to);
    }

    /**
     * כל ה-FREEZE בחתונה זו.
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}/freezes
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}/freezes")
    public ResponseEntity<List<UserAction>> getFreezesInWedding(@PathVariable Long weddingId,
                                                                @PathVariable Long ownerUserId,
                                                                @RequestParam(required = false) LocalDateTime from,
                                                                @RequestParam(required = false) LocalDateTime to) {
        return getActionsByTypeInWedding(weddingId, ownerUserId, UserActionType.FREEZE, from, to);
    }

    /**
     * כל ה-VIEW (צפיות בפרופילים) בחתונה זו.
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}/views
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}/views")
    public ResponseEntity<List<UserAction>> getViewsInWedding(@PathVariable Long weddingId,
                                                              @PathVariable Long ownerUserId,
                                                              @RequestParam(required = false) LocalDateTime from,
                                                              @RequestParam(required = false) LocalDateTime to) {
        return getActionsByTypeInWedding(weddingId, ownerUserId, UserActionType.VIEW, from, to);
    }

    /**
     * עזר פנימי – אותה לוגיקה לכל סוג (LIKE/DISLIKE/FREEZE/VIEW).
     */
    private ResponseEntity<List<UserAction>> getActionsByTypeInWedding(Long weddingId,
                                                                       Long ownerUserId,
                                                                       UserActionType type,
                                                                       LocalDateTime from,
                                                                       LocalDateTime to) {
        try {
            getWeddingForOwnerOrThrow(weddingId, ownerUserId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<UserAction> all = userActionService.getActionsByWedding(weddingId);

        List<UserAction> filtered = filterByTimeAndActive(all, from, to).stream()
                .filter(a -> a.getActionType() == type)
                .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    // ============================================================
    // 🧩 3. סטטיסטיקות חתונה עבור בעל האירוע
    // ============================================================

    /**
     * סטטיסטיקות על פעולות משתמשים בחתונה:
     * - סה"כ פעולות
     * - כמות לייקים / דיסלייקים / הקפאות / צפיות
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}/stats
     *   ?from=...
     *   &to=...
     *
     * Response JSON (WeddingUserActionStatsResponse):
     * {
     *   "weddingId": 10,
     *   "ownerUserId": 123,
     *   "from": "2025-12-01T00:00:00",
     *   "to": "2025-12-02T00:00:00",
     *   "totalActions": 120,
     *   "likesCount": 40,
     *   "dislikesCount": 10,
     *   "freezesCount": 5,
     *   "viewsCount": 65,
     *   "generatedAt": "2025-12-01T22:15:30"
     * }
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}/stats")
    public ResponseEntity<WeddingUserActionStatsResponse> getWeddingActionStats(@PathVariable Long weddingId,
                                                                                @PathVariable Long ownerUserId,
                                                                                @RequestParam(required = false) LocalDateTime from,
                                                                                @RequestParam(required = false) LocalDateTime to) {
        try {
            getWeddingForOwnerOrThrow(weddingId, ownerUserId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<UserAction> all = userActionService.getActionsByWedding(weddingId);
        List<UserAction> filtered = filterByTimeAndActive(all, from, to);

        long likes = filtered.stream().filter(a -> a.getActionType() == UserActionType.LIKE).count();
        long dislikes = filtered.stream().filter(a -> a.getActionType() == UserActionType.DISLIKE).count();
        long freezes = filtered.stream().filter(a -> a.getActionType() == UserActionType.FREEZE).count();
        long views = filtered.stream().filter(a -> a.getActionType() == UserActionType.VIEW).count();

        WeddingUserActionStatsResponse resp = new WeddingUserActionStatsResponse();
        resp.setWeddingId(weddingId);
        resp.setOwnerUserId(ownerUserId);
        resp.setFrom(from);
        resp.setTo(to);
        resp.setTotalActions(filtered.size());
        resp.setLikesCount(likes);
        resp.setDislikesCount(dislikes);
        resp.setFreezesCount(freezes);
        resp.setViewsCount(views);
        resp.setGeneratedAt(LocalDateTime.now());

        return ResponseEntity.ok(resp);
    }

    // ============================================================
    // 🧩 4. פעולות של משתמש מסוים בתוך חתונה
    // ============================================================

    /**
     * כל הפעולות שמשתמש מסוים ביצע בחתונה הזו.
     *
     * GET /api/owner/user-actions/wedding/{weddingId}/owner/{ownerUserId}/actor/{actorId}
     *   ?from=...
     *   &to=...
     */
    @GetMapping("/wedding/{weddingId}/owner/{ownerUserId}/actor/{actorId}")
    public ResponseEntity<List<UserAction>> getActionsForActorInWedding(@PathVariable Long weddingId,
                                                                        @PathVariable Long ownerUserId,
                                                                        @PathVariable Long actorId,
                                                                        @RequestParam(required = false) LocalDateTime from,
                                                                        @RequestParam(required = false) LocalDateTime to) {
        try {
            getWeddingForOwnerOrThrow(weddingId, ownerUserId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // כל הפעולות של המשתמש → סינון לפי weddingId
        List<UserAction> allForActor = userActionService.getActionsByActor(actorId);

        List<UserAction> filtered = allForActor.stream()
                .filter(a -> a.getWeddingId() != null && a.getWeddingId().equals(weddingId))
                .collect(Collectors.toList());

        filtered = filterByTimeAndActive(filtered, from, to);

        return ResponseEntity.ok(filtered);
    }

    // ============================================================
    // 🧩 5. פונקציות עזר – ולידציית בעלות + סינון זמן
    // ============================================================

    /**
     * בדיקת בעלות על חתונה:
     *  - אם החתונה לא קיימת → IllegalArgumentException
     *  - אם אינה שייכת לבעל האירוע → IllegalStateException
     */
    private Wedding getWeddingForOwnerOrThrow(Long weddingId, Long ownerUserId) {
        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        if (!Objects.equals(w.getOwnerUserId(), ownerUserId)) {
            throw new IllegalStateException("User is not owner of this wedding");
        }
        return w;
    }

    /**
     * סינון לפי:
     *  - active=true
     *  - from/to (אם נשלח)
     */
    private List<UserAction> filterByTimeAndActive(List<UserAction> actions,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
        return actions.stream()
                .filter(UserAction::isActive)
                .filter(a -> from == null || (a.getCreatedAt() != null && !a.getCreatedAt().isBefore(from)))
                .filter(a -> to == null || (a.getCreatedAt() != null && !a.getCreatedAt().isAfter(to)))
                .sorted((a1, a2) -> {
                    LocalDateTime t1 = a1.getCreatedAt();
                    LocalDateTime t2 = a2.getCreatedAt();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1); // מהחדש לישן
                })
                .collect(Collectors.toList());
    }

    // ============================================================
    // 🧩 6. DTO לסטטיסטיקות פעולות בחתונה
    // ============================================================

    public static class WeddingUserActionStatsResponse {
        private Long weddingId;
        private Long ownerUserId;
        private LocalDateTime from;
        private LocalDateTime to;
        private int totalActions;
        private long likesCount;
        private long dislikesCount;
        private long freezesCount;
        private long viewsCount;
        private LocalDateTime generatedAt;

        public Long getWeddingId() { return weddingId; }
        public void setWeddingId(Long weddingId) { this.weddingId = weddingId; }

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

        public LocalDateTime getFrom() { return from; }
        public void setFrom(LocalDateTime from) { this.from = from; }

        public LocalDateTime getTo() { return to; }
        public void setTo(LocalDateTime to) { this.to = to; }

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