package com.example.myproject.controller.userphoto.upload;

import com.example.myproject.model.UserPhoto;
import com.example.myproject.service.ImageStorageService;
import com.example.myproject.service.UserPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * UserPhotoUploadController
 *
 * קונטרולר ייעודי להעלאת קבצי תמונה (multipart) מהמכשיר → ל-Cloudinary → לטבלת USER_PHOTOS.
 *
 * Base URL:
 *   /api/users/{userId}/photos/upload
 */
@RestController
@RequestMapping("/api/users")
public class UserPhotoUploadController {

    private final ImageStorageService imageStorageService;
    private final UserPhotoService userPhotoService;

    public UserPhotoUploadController(ImageStorageService imageStorageService,
                                     UserPhotoService userPhotoService) {
        this.imageStorageService = imageStorageService;
        this.userPhotoService = userPhotoService;
    }

    /**
     * העלאת תמונה חדשה למשתמש.
     *
     * POST /api/users/{userId}/photos/upload
     *
     * form-data:
     *   file       = קובץ (type = File)
     *   isPrimary  = true/false (type = Text, אופציונלי, ברירת מחדל false)
     */
    @PostMapping("/{userId}/photos/upload")
    public ResponseEntity<UserPhoto> uploadUserPhoto(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "isPrimary", defaultValue = "false") boolean isPrimary
    ) {
        try {
            // 1. העלאה ל-Cloudinary → מחזיר URL
            String imageUrl = imageStorageService.uploadUserPhoto(file, userId);

            // 2. שמירה ב-DB דרך השירות הקיים שלך
            UserPhoto created = userPhotoService.addPhoto(userId, imageUrl, isPrimary, null);

            // 3. החזרת הישות המלאה ללקוח
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            // userId לא תקין, קובץ ריק, או User not found
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException ex) {
            // למשל עברנו את MAX_PHOTOS_PER_USER
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (RuntimeException ex) {
            // בעיית IO / Cloudinary
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}