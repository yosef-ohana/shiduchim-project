package com.example.myproject.service;

import com.example.myproject.model.User;
import com.example.myproject.model.UserPhoto;
import com.example.myproject.repository.UserPhotoRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * שירות תמונות משתמש:
 * - העלאה והוספת תמונות לפרופיל
 * - ניהול תמונה ראשית (primaryPhoto)
 * - מחיקה לוגית / מחיקה מלאה
 * - סידור גלריה מחדש
 * - בדיקה אם למשתמש יש לפחות תמונה אחת פעילה
 */
@Service                      // מגדיר את המחלקה כ-Service של Spring
@Transactional                // כל המתודות רצות בטרנזקציה מול ה-DB
public class UserPhotoService {

    private final UserPhotoRepository userPhotoRepository; // ריפו לטבלת התמונות
    private final UserRepository userRepository;           // ריפו למשתמשים

    // מקסימום תמונות פעילות למשתמש (כיום 6 – אפשר לשנות בעתיד במקום אחד מרכזי)
    private static final int MAX_PHOTOS_PER_USER = 6;

    // בנאי DI – Spring מזריק את הריפוז
    public UserPhotoService(UserPhotoRepository userPhotoRepository,
                            UserRepository userRepository) {
        this.userPhotoRepository = userPhotoRepository;
        this.userRepository = userRepository;
    }

    // ----------------------------------------------------
    // 1. יצירה / העלאה של תמונה
    // ----------------------------------------------------

    /**
     * העלאת תמונה חדשה למשתמש.
     * - בודק userId ו־imageUrl.
     * - בודק שלא עברנו את מגבלת התמונות.
     * - אם זו התמונה הראשונה → נהיית ראשית.
     * - אם makePrimary=true → מציב כראשית ומוריד primary מכל האחרות.
     * - אם positionIndex=null → קובע אינדקס אוטומטי לפי סוף הגלריה.
     */
    public UserPhoto addPhoto(Long userId,
                              String imageUrl,
                              boolean makePrimary,
                              Integer positionIndex) {

        if (userId == null || imageUrl == null || imageUrl.isBlank()) { // ולידציה בסיסית
            throw new IllegalArgumentException("userId and imageUrl are required");
        }

        // טעינת המשתמש מה-DB
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // כמה תמונות פעילות יש כבר למשתמש
        long activeCount = userPhotoRepository.countByUserAndDeletedFalse(user);
        if (activeCount >= MAX_PHOTOS_PER_USER) {
            throw new IllegalStateException(
                    "User already has maximum number of photos (" + MAX_PHOTOS_PER_USER + ")"
            );
        }

        // אם לא קיבלנו positionIndex – נמצא את הבא בתור בקצה הגלריה
        if (positionIndex == null) {
            List<UserPhoto> existing = userPhotoRepository
                    .findByUserAndDeletedFalseOrderByPositionIndexAsc(user);

            int nextIndex;
            if (existing.isEmpty()) {
                nextIndex = 1;               // תמונה ראשונה – אינדקס 1
            } else {
                UserPhoto last = existing.get(existing.size() - 1);
                Integer lastIndex = last.getPositionIndex();
                nextIndex = (lastIndex != null) ? lastIndex + 1 : existing.size() + 1;
            }
            positionIndex = nextIndex;
        }

        // יצירת אובייקט תמונה חדש
        UserPhoto photo = new UserPhoto();
        photo.setUser(user);                        // למי שייכת התמונה
        photo.setImageUrl(imageUrl);               // URL (למשל Cloudinary/S3)
        photo.setPositionIndex(positionIndex);     // מיקום בגלריה
        photo.setCreatedAt(LocalDateTime.now());   // זמן יצירה (אם יש setter בישות)
        photo.setDeleted(false);                   // ברירת מחדל – לא מחוקה

        // האם כבר קיימת תמונה ראשית למשתמש?
        boolean hasPrimary =
                userPhotoRepository.existsByUserAndPrimaryPhotoTrueAndDeletedFalse(user);

        // אם אין ראשית עדיין, או שביקשנו במפורש makePrimary
        if (!hasPrimary || makePrimary) {
            photo.setPrimaryPhoto(true);           // מסמנים ראשית בתמונה החדשה
            clearPrimaryFlagFromOtherPhotos(user); // ומורידים primary מהשאר
        } else {
            photo.setPrimaryPhoto(false);          // תמונה רגילה
        }

        return userPhotoRepository.save(photo);    // שמירה והחזרה
    }

    // ----------------------------------------------------
    // 2. החלפת / הגדרת תמונה ראשית
    // ----------------------------------------------------

    /**
     * קביעת תמונה מסוימת כראשית.
     * - מוודא שהתמונה שייכת למשתמש ולא מחוקה.
     * - מבטל primary מכל שאר התמונות הפעילות.
     */
    public UserPhoto setPrimaryPhoto(Long userId, Long photoId) {
        if (userId == null || photoId == null) {
            throw new IllegalArgumentException("userId and photoId are required");
        }

        // טעינת המשתמש
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // טעינת התמונה
        UserPhoto photo = userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));

        // בדיקה שהתמונה באמת שייכת למשתמש הזה
        if (!photo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Photo does not belong to the given user");
        }

        // לא ניתן להגדיר תמונה מחוקה כראשית
        if (photo.isDeleted()) {
            throw new IllegalStateException("Cannot set deleted photo as primary");
        }

        // ביטול primary מכל התמונות האחרות של המשתמש
        clearPrimaryFlagFromOtherPhotos(user);

        // סימון התמונה הנוכחית כראשית
        photo.setPrimaryPhoto(true);
        return userPhotoRepository.save(photo);
    }

    /**
     * עזר פנימי – מבטל primary מכל התמונות הפעילות של המשתמש.
     */
    private void clearPrimaryFlagFromOtherPhotos(User user) {
        List<UserPhoto> active = userPhotoRepository.findByUserAndDeletedFalse(user);

        for (UserPhoto p : active) {
            if (p.isPrimaryPhoto()) {      // אם התמונה מסומנת כ-primary
                p.setPrimaryPhoto(false);  // נסיר את הדגל
            }
        }

        userPhotoRepository.saveAll(active); // שמירה מרוכזת
    }

    // ----------------------------------------------------
    // 3. מחיקה לוגית / מחיקה מלאה
    // ----------------------------------------------------

    /**
     * מחיקה לוגית (soft delete) של תמונה:
     * - מסמן deleted=true ו-primaryPhoto=false.
     * - אם זו הייתה התמונה הראשית → מחפש תמונה פעילה אחרת ומגדיר אותה כראשית.
     */
    public void softDeletePhoto(Long userId, Long photoId) {
        if (userId == null || photoId == null) {
            throw new IllegalArgumentException("userId and photoId are required");
        }

        // טעינת המשתמש
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // טעינת התמונה
        UserPhoto photo = userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));

        // לוודא שהתמונה שייכת למשתמש
        if (!photo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Photo does not belong to the given user");
        }

        boolean wasPrimary = photo.isPrimaryPhoto(); // נזכור אם הייתה ראשית

        photo.setDeleted(true);          // מסמן כמחוקה
        photo.setPrimaryPhoto(false);    // כבר לא ראשית
        userPhotoRepository.save(photo); // שמירה

        // אם מחקנו את התמונה הראשית → ננסה לבחור ראשית חדשה
        if (wasPrimary) {
            UserPhoto replacement =
                    userPhotoRepository.findFirstByUserAndDeletedFalseOrderByCreatedAtAsc(user);

            if (replacement != null) {
                replacement.setPrimaryPhoto(true);
                userPhotoRepository.save(replacement);
            }
        }
    }

    /**
     * מחיקה פיזית של כל התמונות של משתמש (למשל בעת מחיקת חשבון סופית).
     * בדרך כלל ייקרא מתוך UserService.deleteUserHard().
     */
    public void hardDeleteAllPhotosForUser(Long userId) {
        if (userId == null) {
            return; // אין מה למחוק
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        userPhotoRepository.deleteByUser(user); // מחיקה פיזית מה-DB
    }

    // ----------------------------------------------------
    // 4. גלריה – שליפה וסידור מחדש
    // ----------------------------------------------------

    /**
     * מחזיר את כל התמונות הפעילות של המשתמש לפי סדר positionIndex.
     * שימושי למסך גלריה בצד ה-Client.
     */
    public List<UserPhoto> getActivePhotosForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.findByUserAndDeletedFalseOrderByPositionIndexAsc(user);
    }

    /**
     * מחזיר את כל התמונות (כולל מחוקות) – בעיקר למסכי ניהול.
     */
    public List<UserPhoto> getAllPhotosForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.findByUser(user);
    }

    /**
     * מחזיר את התמונה הראשית של המשתמש, אם קיימת.
     */
    public UserPhoto getPrimaryPhotoForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.findByUserAndPrimaryPhotoTrueAndDeletedFalse(user);
    }

    /**
     * סידור מחדש של גלריית התמונות:
     * - מקבל רשימת מזהי תמונות פעילים בסדר הרצוי.
     * - מוודא שכל id שייך למשתמש ושהתמונה לא מחוקה.
     * - מגדיר positionIndex 1..n לפי סדר הרשימה.
     */
    public void reorderUserPhotos(Long userId, List<Long> photoIdsActiveInOrder) {
        if (userId == null || photoIdsActiveInOrder == null) {
            throw new IllegalArgumentException("userId and photoIdsActiveInOrder are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // כל התמונות הפעילות הנוכחיות
        List<UserPhoto> activePhotos =
                userPhotoRepository.findByUserAndDeletedFalseOrderByPositionIndexAsc(user);

        // מיפוי id → אובייקט
        Map<Long, UserPhoto> activeById = activePhotos.stream()
                .collect(Collectors.toMap(UserPhoto::getId, p -> p));

        // לוודא שכל id ברשימה באמת שייך למשתמש ופעיל
        for (Long pid : photoIdsActiveInOrder) {
            if (!activeById.containsKey(pid)) {
                throw new IllegalArgumentException(
                        "Photo id " + pid + " is not an active photo of this user"
                );
            }
        }

        // כאן החלטנו שהרשימה חייבת לכלול את כל התמונות הפעילות
        if (photoIdsActiveInOrder.size() != activePhotos.size()) {
            throw new IllegalArgumentException(
                    "Order list size does not match number of active photos"
            );
        }

        // עדכון positionIndex לפי הסדר החדש
        int index = 1;
        for (Long pid : photoIdsActiveInOrder) {
            UserPhoto p = activeById.get(pid);
            p.setPositionIndex(index++);
        }

        userPhotoRepository.saveAll(activePhotos); // שמירה מרוכזת
    }

    // ----------------------------------------------------
    // 5. עזר – בדיקה האם למשתמש יש לפחות תמונה פעילה אחת
    // ----------------------------------------------------

    /**
     * בודק האם למשתמש יש לפחות תמונה פעילה אחת.
     * שימושי בבדיקת "פרופיל תקין" – חובה תמונה אחת.
     */
    public boolean userHasAtLeastOneActivePhoto(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        long count = userPhotoRepository.countByUserAndDeletedFalse(user);
        return count > 0;
    }
}