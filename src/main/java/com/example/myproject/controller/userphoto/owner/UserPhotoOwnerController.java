package com.example.myproject.controller.userphoto.owner;

import com.example.myproject.model.UserPhoto;
import com.example.myproject.service.UserPhotoService;
import com.example.myproject.service.WeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 🔵 UserPhotoOwnerController
 *
 * קונטרולר מלא לבעל האירוע (Event Owner).
 * כולל:
 * - תמונות פעילות
 * - תמונה ראשית
 * - כל התמונות (כולל מחוקות)
 * - בדיקות "יש תמונה"
 * - בדיקות "יש תמונה ראשית"
 *
 * ⚠️ בעל האירוע אינו יכול לערוך / למחוק / להעלות.
 */
@RestController
@RequestMapping("/api/owner/photos")
public class UserPhotoOwnerController {

    private final UserPhotoService userPhotoService;
    private final WeddingService weddingService;

    public UserPhotoOwnerController(UserPhotoService userPhotoService,
                                    WeddingService weddingService) {
        this.userPhotoService = userPhotoService;
        this.weddingService = weddingService;
    }

    // ============================================================
    // ולידציה מרכזית — לפי מסמך אפיון 2025
    // ============================================================

    private void validateOwner(Long weddingId, Long ownerId) {
        if (!weddingService.isOwnerOfWedding(ownerId, weddingId)) {
            throw new IllegalStateException("User is not owner of this wedding");
        }
    }

    private void validateParticipant(Long weddingId, Long userId) {
        if (!weddingService.isUserInWedding(userId, weddingId)) {
            throw new IllegalStateException("User is not participant of this wedding");
        }
    }

    private void validateOwnerAndParticipant(Long weddingId,
                                             Long ownerId,
                                             Long userId) {
        validateOwner(weddingId, ownerId);
        validateParticipant(weddingId, userId);
    }

    // ============================================================
    // 1. תמונות פעילות של משתמש בחתונה
    // ============================================================

    /**
     * GET /api/owner/photos/{weddingId}/owner/{ownerId}/user/{userId}/active
     */
    @GetMapping("/{weddingId}/owner/{ownerId}/user/{userId}/active")
    public ResponseEntity<List<UserPhoto>> getActivePhotos(
            @PathVariable Long weddingId,
            @PathVariable Long ownerId,
            @PathVariable Long userId
    ) {
        validateOwnerAndParticipant(weddingId, ownerId, userId);

        List<UserPhoto> photos = userPhotoService.getActivePhotosForUser(userId);
        return ResponseEntity.ok(photos);
    }

    // ============================================================
    // 2. תמונה ראשית של משתמש
    // ============================================================

    /**
     * GET /api/owner/photos/{weddingId}/owner/{ownerId}/user/{userId}/primary
     */
    @GetMapping("/{weddingId}/owner/{ownerId}/user/{userId}/primary")
    public ResponseEntity<UserPhoto> getPrimaryPhoto(
            @PathVariable Long weddingId,
            @PathVariable Long ownerId,
            @PathVariable Long userId
    ) {
        validateOwnerAndParticipant(weddingId, ownerId, userId);

        UserPhoto photo = userPhotoService.getPrimaryPhotoForUser(userId);
        if (photo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(photo);
    }

    // ============================================================
    // 3. כל התמונות (כולל מחוקות)
    // ============================================================

    /**
     * GET /api/owner/photos/{weddingId}/owner/{ownerId}/user/{userId}/all
     */
    @GetMapping("/{weddingId}/owner/{ownerId}/user/{userId}/all")
    public ResponseEntity<List<UserPhoto>> getAllPhotos(
            @PathVariable Long weddingId,
            @PathVariable Long ownerId,
            @PathVariable Long userId
    ) {
        validateOwnerAndParticipant(weddingId, ownerId, userId);

        List<UserPhoto> photos = userPhotoService.getAllPhotosForUser(userId);
        return ResponseEntity.ok(photos);
    }

    // ============================================================
    // 4. האם למשתמש יש לפחות תמונה פעילה?
    // ============================================================

    /**
     * GET /api/owner/photos/{weddingId}/owner/{ownerId}/user/{userId}/has-photo
     */
    @GetMapping("/{weddingId}/owner/{ownerId}/user/{userId}/has-photo")
    public ResponseEntity<Boolean> hasAnyActivePhoto(
            @PathVariable Long weddingId,
            @PathVariable Long ownerId,
            @PathVariable Long userId
    ) {
        validateOwnerAndParticipant(weddingId, ownerId, userId);

        boolean hasPhoto = userPhotoService.userHasAtLeastOneActivePhoto(userId);
        return ResponseEntity.ok(hasPhoto);
    }

    // ============================================================
    // 5. האם למשתמש יש תמונה ראשית?
    // ============================================================

    /**
     * GET /api/owner/photos/{weddingId}/owner/{ownerId}/user/{userId}/has-primary
     */
    @GetMapping("/{weddingId}/owner/{ownerId}/user/{userId}/has-primary")
    public ResponseEntity<Boolean> hasPrimaryPhoto(
            @PathVariable Long weddingId,
            @PathVariable Long ownerId,
            @PathVariable Long userId
    ) {
        validateOwnerAndParticipant(weddingId, ownerId, userId);

        UserPhoto primary = userPhotoService.getPrimaryPhotoForUser(userId);
        return ResponseEntity.ok(primary != null);
    }
}