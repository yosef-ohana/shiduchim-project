package com.example.myproject.controller.userphoto.user;

import com.example.myproject.model.UserPhoto;
import com.example.myproject.service.UserPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 🔵 UserPhotoUserController
 *
 * קונטרולר לצד "משתמש רגיל":
 * - העלאת תמונה (URL שכבר עלה ל-Cloudinary/S3 וכו')
 * - קביעת תמונה ראשית
 * - מחיקה לוגית של תמונה
 * - סידור מחדש של הגלריה (Drag & Drop בצד לקוח)
 * - שליפת גלריית תמונות פעילה
 * - שליפת כל התמונות (כולל מחוקות) למשתמש עצמו
 * - שליפת תמונה ראשית בלבד
 * - בדיקה אם יש למשתמש לפחות תמונה פעילה אחת
 *
 * URL Base:
 *   /api/user/photos
 *
 * ⚠️ הערה:
 * האימות שהמשתמש באמת רשאי לפעול בשם userId מסוים
 * ייעשה בשכבת האבטחה (JWT / Session) ולא כאן.
 */
@RestController
@RequestMapping("/api/user/photos")
public class UserPhotoUserController {

    private final UserPhotoService userPhotoService;

    public UserPhotoUserController(UserPhotoService userPhotoService) {
        this.userPhotoService = userPhotoService;
    }

    // ============================================================
    // 1. העלאת תמונה חדשה למשתמש
    // ============================================================

    /**
     * העלאת/רישום תמונה חדשה למשתמש.
     *
     * POST /api/user/photos/{userId}
     *
     * Request JSON:
     * {
     *   "imageUrl": "https://cloudinary.com/....",
     *   "makePrimary": true,         // אופציונלי (ברירת מחדל: false)
     *   "positionIndex": 1           // אופציונלי (אם null → ישובץ בסוף הגלריה)
     * }
     *
     * Service:
     * - UserPhotoService.addPhoto(userId, imageUrl, makePrimary, positionIndex)
     *
     * קודי תשובה:
     * - 201 CREATED – כשהוספה בוצעה בהצלחה
     * - 400 BAD_REQUEST – userId/imageUrl ריקים או משתמש לא קיים
     * - 409 CONFLICT – עברנו את מגבלת התמונות למשתמש
     */
    @PostMapping("/{userId}")
    public ResponseEntity<UserPhoto> uploadPhoto(@PathVariable Long userId,
                                                 @RequestBody UploadPhotoRequest request) {
        if (request == null || request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean makePrimary =
                request.getMakePrimary() != null && request.getMakePrimary();

        try {
            UserPhoto created = userPhotoService.addPhoto(
                    userId,
                    request.getImageUrl(),
                    makePrimary,
                    request.getPositionIndex()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            // userId לא תקין / משתמש לא נמצא וכו'
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // למשל: עברנו את MAX_PHOTOS_PER_USER
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // ============================================================
    // 2. קביעת תמונה ראשית
    // ============================================================

    /**
     * קביעת תמונה מסוימת כראשית עבור המשתמש.
     *
     * POST /api/user/photos/{userId}/{photoId}/primary
     *
     * Service:
     * - UserPhotoService.setPrimaryPhoto(userId, photoId)
     *
     * קודי תשובה:
     * - 200 OK – הצלחה
     * - 400 BAD_REQUEST – פרמטרים חסרים / התמונה לא שייכת למשתמש
     * - 404 NOT_FOUND – משתמש/תמונה לא נמצאו
     * - 409 CONFLICT – ניסיון לקבוע תמונה מחוקה כראשית
     */
    @PostMapping("/{userId}/{photoId}/primary")
    public ResponseEntity<UserPhoto> setPrimaryPhoto(@PathVariable Long userId,
                                                     @PathVariable Long photoId) {
        try {
            UserPhoto updated = userPhotoService.setPrimaryPhoto(userId, photoId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("User not found") || msg.contains("Photo not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // לדוגמה: "Cannot set deleted photo as primary"
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // ============================================================
    // 3. מחיקה לוגית של תמונה
    // ============================================================

    /**
     * מחיקה לוגית של תמונת משתמש.
     *
     * DELETE /api/user/photos/{userId}/{photoId}
     *
     * Service:
     * - UserPhotoService.softDeletePhoto(userId, photoId)
     *
     * הערות:
     * - אם זו התמונה הראשית → השירות ינסה לבחור תמונה פעילה אחרת כראשית.
     *
     * קודי תשובה:
     * - 200 OK – נמחק לוגית בהצלחה
     * - 400 BAD_REQUEST – פרמטרים חסרים / התמונה לא שייכת למשתמש
     * - 404 NOT_FOUND – משתמש/תמונה לא נמצאו
     */
    @DeleteMapping("/{userId}/{photoId}")
    public ResponseEntity<Void> softDeletePhoto(@PathVariable Long userId,
                                                @PathVariable Long photoId) {
        try {
            userPhotoService.softDeletePhoto(userId, photoId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("User not found") || msg.contains("Photo not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 4. שליפת גלריית התמונות הפעילות של המשתמש
    // ============================================================

    /**
     * כל התמונות הפעילות (deleted=false) של המשתמש, לפי positionIndex.
     *
     * GET /api/user/photos/{userId}
     *
     * Service:
     * - UserPhotoService.getActivePhotosForUser(userId)
     *
     * קודי תשובה:
     * - 200 OK – תמיד, אם userId תקין
     * - 404 NOT_FOUND – אם המשתמש לא קיים
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<UserPhoto>> getActivePhotos(@PathVariable Long userId) {
        try {
            List<UserPhoto> photos = userPhotoService.getActivePhotosForUser(userId);
            return ResponseEntity.ok(photos);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 5. שליפת כל התמונות (כולל מחוקות) עבור המשתמש עצמו
    // ============================================================

    /**
     * כל התמונות של המשתמש, כולל מחוקות לוגית.
     * שימושי למסכי "היסטוריית תמונות" / ניהול עצמי.
     *
     * GET /api/user/photos/{userId}/all
     *
     * Service:
     * - UserPhotoService.getAllPhotosForUser(userId)
     */
    @GetMapping("/{userId}/all")
    public ResponseEntity<List<UserPhoto>> getAllPhotos(@PathVariable Long userId) {
        try {
            List<UserPhoto> photos = userPhotoService.getAllPhotosForUser(userId);
            return ResponseEntity.ok(photos);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // 6. שליפת תמונה ראשית בלבד
    // ============================================================

    /**
     * החזרת התמונה הראשית של המשתמש.
     *
     * GET /api/user/photos/{userId}/primary
     *
     * Service:
     * - UserPhotoService.getPrimaryPhotoForUser(userId)
     *
     * קודי תשובה:
     * - 200 OK + JSON של UserPhoto – אם קיימת תמונה ראשית
     * - 204 NO_CONTENT – אם אין תמונה ראשית פעילה
     * - 404 NOT_FOUND – אם המשתמש לא קיים
     */
    @GetMapping("/{userId}/primary")
    public ResponseEntity<UserPhoto> getPrimaryPhoto(@PathVariable Long userId) {
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

    // ============================================================
    // 7. סידור מחדש של גלריית התמונות
    // ============================================================

    /**
     * סידור מחדש של גלריית התמונות הפעילות.
     *
     * POST /api/user/photos/{userId}/reorder
     *
     * Request JSON:
     * {
     *   "photoIds": [5, 3, 10, 7]
     * }
     *
     * דרישות:
     * - הרשימה חייבת להכיל *את כל* התמונות הפעילות וללא כפילויות.
     * - אם חסרה תמונה או יש id שלא שייך למשתמש → IllegalArgumentException.
     *
     * Service:
     * - UserPhotoService.reorderUserPhotos(userId, photoIds)
     *
     * קודי תשובה:
     * - 200 OK – סודר בהצלחה
     * - 400 BAD_REQUEST – רשימה לא תואמת/ערכים שגויים
     * - 404 NOT_FOUND – משתמש לא קיים
     */
    @PostMapping("/{userId}/reorder")
    public ResponseEntity<Void> reorderPhotos(@PathVariable Long userId,
                                              @RequestBody ReorderPhotosRequest request) {
        if (request == null || request.getPhotoIds() == null || request.getPhotoIds().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            userPhotoService.reorderUserPhotos(userId, request.getPhotoIds());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("User not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // שאר הודעות השגיאה – BAD_REQUEST (למשל רשימה לא תואמת)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ============================================================
    // 8. בדיקה: האם למשתמש יש לפחות תמונה פעילה אחת
    // ============================================================

    /**
     * בדיקה אם למשתמש יש לפחות תמונה פעילה אחת.
     * שימושי במסכי "האם הפרופיל שלך כבר תקין?".
     *
     * GET /api/user/photos/{userId}/has-active
     *
     * Response:
     * {
     *   "userId": 123,
     *   "hasActivePhoto": true
     * }
     *
     * Service:
     * - UserPhotoService.userHasAtLeastOneActivePhoto(userId)
     */
    @GetMapping("/{userId}/has-active")
    public ResponseEntity<HasActivePhotoResponse> hasActivePhoto(@PathVariable Long userId) {
        try {
            boolean has = userPhotoService.userHasAtLeastOneActivePhoto(userId);
            HasActivePhotoResponse resp = new HasActivePhotoResponse();
            resp.setUserId(userId);
            resp.setHasActivePhoto(has);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            // User not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ============================================================
    // DTOs פנימיים לבקשות ותשובות JSON
    // ============================================================

    /**
     * DTO – בקשת העלאת תמונה (אחרי שהקובץ עצמו נשמר ב-Cloudinary/S3).
     */
    public static class UploadPhotoRequest {
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
     * DTO – בקשה לסידור מחדש של גלריה.
     */
    public static class ReorderPhotosRequest {
        private List<Long> photoIds;

        public List<Long> getPhotoIds() { return photoIds; }
        public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
    }

    /**
     * DTO – תשובה לבדיקה האם יש תמונה פעילה.
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