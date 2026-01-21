package com.example.myproject.controller.userphoto.admin;

import com.example.myproject.model.User;
import com.example.myproject.model.UserPhoto;
import com.example.myproject.repository.UserPhotoRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.UserPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * 🔵 UserPhotoAdminController (API v1)
 *
 * קונטרולר אדמין מלא לניהול תמונות משתמשים:
 * - צפייה בכל התמונות (פעילות / מחוקות / לפי משתמש)
 * - הוספת תמונה למשתמש
 * - קביעת תמונה ראשית
 * - מחיקה לוגית
 * - מחיקה פיזית של כל התמונות למשתמש
 * - שיחזור תמונה (undelete)
 * - סידור גלריה מחדש (reorder)
 * - בדיקת קיום תמונה פעילה למשתמש
 * - סטטיסטיקות בסיסיות (תמונות שנמחקו, תמונות חדשות אחרי זמן מסוים)
 *
 * Base Path: /api/admin/photos
 */
@RestController
@RequestMapping("/api/admin/photos")
public class UserPhotoAdminController {

    private final UserPhotoService userPhotoService;
    private final UserPhotoRepository userPhotoRepository;
    private final UserRepository userRepository;

    public UserPhotoAdminController(UserPhotoService userPhotoService,
                                    UserPhotoRepository userPhotoRepository,
                                    UserRepository userRepository) {
        this.userPhotoService = userPhotoService;
        this.userPhotoRepository = userPhotoRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // 1️⃣ שליפות ברמת מערכת / משתמש
    // =========================================================

    /**
     * כל התמונות הפעילות (לא מחוקות) של משתמש מסוים לפי userId – מסודרות לפי positionIndex.
     *
     * GET /api/admin/photos/user/{userId}/active
     *
     * Service:
     * - UserPhotoService.getActivePhotosForUser(userId)
     */
    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<UserPhoto>> getActivePhotosForUser(@PathVariable Long userId) {
        try {
            List<UserPhoto> photos = userPhotoService.getActivePhotosForUser(userId);
            return ResponseEntity.ok(photos);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * כל התמונות (כולל מחוקות) של משתמש מסוים.
     *
     * GET /api/admin/photos/user/{userId}/all
     *
     * Service:
     * - UserPhotoService.getAllPhotosForUser(userId)
     */
    @GetMapping("/user/{userId}/all")
    public ResponseEntity<List<UserPhoto>> getAllPhotosForUser(@PathVariable Long userId) {
        try {
            List<UserPhoto> photos = userPhotoService.getAllPhotosForUser(userId);
            return ResponseEntity.ok(photos);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * התמונה הראשית של משתמש, אם קיימת.
     *
     * GET /api/admin/photos/user/{userId}/primary
     *
     * Service:
     * - UserPhotoService.getPrimaryPhotoForUser(userId)
     */
    @GetMapping("/user/{userId}/primary")
    public ResponseEntity<UserPhoto> getPrimaryPhotoForUser(@PathVariable Long userId) {
        try {
            UserPhoto primary = userPhotoService.getPrimaryPhotoForUser(userId);
            if (primary == null) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }
            return ResponseEntity.ok(primary);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * כל התמונות שמסומנות כ-deleted=true (מחיקה לוגית) בכל המערכת.
     *
     * GET /api/admin/photos/deleted
     *
     * Repository:
     * - UserPhotoRepository.findByDeletedTrue()
     */
    @GetMapping("/deleted")
    public ResponseEntity<List<UserPhoto>> getAllDeletedPhotos() {
        List<UserPhoto> deleted = userPhotoRepository.findByDeletedTrue();
        return ResponseEntity.ok(deleted);
    }

    /**
     * כל התמונות הפעילות שנוצרו אחרי זמן מסוים.
     *
     * GET /api/admin/photos/recent?since=2025-11-01T00:00:00
     *
     * Repository:
     * - UserPhotoRepository.findByCreatedAtAfterAndDeletedFalse(time)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<UserPhoto>> getRecentActivePhotos(@RequestParam("since") String sinceIso) {
        try {
            LocalDateTime since = LocalDateTime.parse(sinceIso);
            List<UserPhoto> list =
                    userPhotoRepository.findByCreatedAtAfterAndDeletedFalse(since);
            return ResponseEntity.ok(list);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * שליפת תמונה בודדת לפי photoId (כולל תמונה מחוקה).
     *
     * GET /api/admin/photos/photo/{photoId}
     */
    @GetMapping("/photo/{photoId}")
    public ResponseEntity<UserPhoto> getPhotoById(@PathVariable Long photoId) {
        Optional<UserPhoto> opt = userPhotoRepository.findById(photoId);
        return opt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * בדיקה האם למשתמש יש לפחות תמונה פעילה אחת (לוגיקת "פרופיל תקין").
     *
     * GET /api/admin/photos/user/{userId}/has-active
     *
     * Service:
     * - UserPhotoService.userHasAtLeastOneActivePhoto(userId)
     */
    @GetMapping("/user/{userId}/has-active")
    public ResponseEntity<HasActivePhotoResponse> userHasActivePhoto(@PathVariable Long userId) {
        try {
            boolean hasActive = userPhotoService.userHasAtLeastOneActivePhoto(userId);
            HasActivePhotoResponse resp = new HasActivePhotoResponse();
            resp.setUserId(userId);
            resp.setHasActivePhoto(hasActive);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // =========================================================
    // 2️⃣ יצירה / הוספת תמונה ע"י אדמין
    // =========================================================

    /**
     * הוספת תמונה למשתמש ע"י אדמין.
     *
     * POST /api/admin/photos/user/{userId}
     *
     * Request JSON:
     * {
     *   "imageUrl": "https://.../image.jpg",
     *   "makePrimary": true,          // אופציונלי (ברירת מחדל false)
     *   "positionIndex": 1            // אופציונלי – אם null יקבע אוטומטית
     * }
     *
     * Service:
     * - UserPhotoService.addPhoto(userId, imageUrl, makePrimary, positionIndex)
     */
    @PostMapping("/user/{userId}")
    public ResponseEntity<UserPhoto> addPhotoForUser(@PathVariable Long userId,
                                                     @RequestBody AdminAddPhotoRequest request) {
        if (request == null || request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean makePrimary = request.getMakePrimary() != null && request.getMakePrimary();

        try {
            UserPhoto created = userPhotoService.addPhoto(
                    userId,
                    request.getImageUrl(),
                    makePrimary,
                    request.getPositionIndex()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            // User not found / invalid args
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // עברנו את מגבלת התמונות למשתמש
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // =========================================================
    // 3️⃣ קביעת תמונה ראשית ע"י אדמין
    // =========================================================

    /**
     * קביעת תמונה כראשית עבור משתמש (אדמין יכול לעקוף הכול).
     *
     * POST /api/admin/photos/user/{userId}/primary/{photoId}
     *
     * Service:
     * - UserPhotoService.setPrimaryPhoto(userId, photoId)
     */
    @PostMapping("/user/{userId}/primary/{photoId}")
    public ResponseEntity<UserPhoto> setPrimaryPhoto(@PathVariable Long userId,
                                                     @PathVariable Long photoId) {
        try {
            UserPhoto updated = userPhotoService.setPrimaryPhoto(userId, photoId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            // User / photo not found or mismatch
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // ניסו להגדיר תמונה מחוקה כראשית
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // =========================================================
    // 4️⃣ מחיקה לוגית / מחיקה פיזית / שיחזור
    // =========================================================

    /**
     * מחיקה לוגית של תמונה (soft delete) – ע"י אדמין.
     * - מסמן deleted=true ו-primaryPhoto=false.
     * - אם זו הייתה הראשית – השירות ינסה לבחור אחרת.
     *
     * DELETE /api/admin/photos/user/{userId}/photo/{photoId}
     *
     * Service:
     * - UserPhotoService.softDeletePhoto(userId, photoId)
     */
    @DeleteMapping("/user/{userId}/photo/{photoId}")
    public ResponseEntity<Void> softDeletePhoto(@PathVariable Long userId,
                                                @PathVariable Long photoId) {
        try {
            userPhotoService.softDeletePhoto(userId, photoId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            // User / photo not found / mismatch
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * מחיקה פיזית של כל התמונות של משתמש (איפוס גלריה מלא).
     * ⚠️ משמש רק ב־Admin / מחיקת משתמש קשיחה.
     *
     * DELETE /api/admin/photos/user/{userId}/hard
     *
     * Service:
     * - UserPhotoService.hardDeleteAllPhotosForUser(userId)
     */
    @DeleteMapping("/user/{userId}/hard")
    public ResponseEntity<Void> hardDeleteAllPhotosForUser(@PathVariable Long userId) {
        try {
            userPhotoService.hardDeleteAllPhotosForUser(userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * שיחזור תמונה שנמחקה (undelete).
     *
     * POST /api/admin/photos/photo/{photoId}/undelete
     *
     * לוגיקה:
     * - מאתר את התמונה לפי id.
     * - אם לא קיימת → 404.
     * - מסמן deleted=false.
     * - כדי לא לשבור לוגיקת primary, לא מחזירים את הדגל primaryPhoto אוטומטית,
     *   אלא משאירים אותו כפי שהוא (אם היה true) אבל ללא הבטחה שהוא "ראשי אמיתי".
     *   מומלץ לאדמין לקרוא אח"כ ל־setPrimaryPhoto אם רוצים אותה כראשית.
     */
    @PostMapping("/photo/{photoId}/undelete")
    public ResponseEntity<UserPhoto> undeletePhoto(@PathVariable Long photoId) {
        Optional<UserPhoto> opt = userPhotoRepository.findById(photoId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        UserPhoto photo = opt.get();
        // מחזירים את התמונה להיות פעילה
        photo.setDeleted(false);
        photo.setUpdatedAt(LocalDateTime.now());

        UserPhoto saved = userPhotoRepository.save(photo);
        return ResponseEntity.ok(saved);
    }

    // =========================================================
    // 5️⃣ סידור גלריה מחדש – Reorder
    // =========================================================

    /**
     * סידור מחדש של כל התמונות הפעילות של משתמש.
     * - הרשימה חייבת לכלול את *כל* התמונות הפעילות.
     * - סדר ה־IDs מגדיר את positionIndex 1..n.
     *
     * POST /api/admin/photos/user/{userId}/reorder
     *
     * Request JSON:
     * {
     *   "photoIds": [10, 5, 7, 9]
     * }
     *
     * Service:
     * - UserPhotoService.reorderUserPhotos(userId, photoIds)
     */
    @PostMapping("/user/{userId}/reorder")
    public ResponseEntity<Void> reorderUserPhotos(@PathVariable Long userId,
                                                  @RequestBody ReorderPhotosRequest request) {
        if (request == null || request.getPhotoIds() == null || request.getPhotoIds().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            userPhotoService.reorderUserPhotos(userId, request.getPhotoIds());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            // User not found / רשימה לא תואמת
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // =========================================================
    // 6️⃣ DTOs פנימיים לבקשות / תשובות
    // =========================================================

    /**
     * DTO – בקשת אדמין להוספת תמונה למשתמש.
     */
    public static class AdminAddPhotoRequest {
        private String imageUrl;
        private Boolean makePrimary;
        private Integer positionIndex;

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public Boolean getMakePrimary() { return makePrimary; }
        public void setMakePrimary(Boolean makePrimary) { this.makePrimary = makePrimary; }

        public Integer getPositionIndex() { return positionIndex; }
        public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }
    }

    /**
     * DTO – בקשה לסידור מחדש של גלריית התמונות.
     */
    public static class ReorderPhotosRequest {
        private List<Long> photoIds;

        public List<Long> getPhotoIds() { return photoIds; }
        public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
    }

    /**
     * DTO – תשובה לשאלה האם למשתמש יש לפחות תמונה פעילה אחת.
     */
    public static class HasActivePhotoResponse {
        private Long userId;
        private boolean hasActivePhoto;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public boolean isHasActivePhoto() { return hasActivePhoto; }
        public void setHasActivePhoto(boolean hasActivePhoto) { this.hasActivePhoto = hasActivePhoto; }
    }
}