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
 * - סנכרון photosCount + hasPrimaryPhoto בישות User
 * - לפי האפיון החדש: תמונה ראשית היא תנאי לכרטיס משתמש פעיל
 */
@Service
@Transactional
public class UserPhotoService {

    private final UserPhotoRepository userPhotoRepository;
    private final UserRepository userRepository;

    // מקסימום תמונות פעילות למשתמש (כיום 6 – אפשר לשנות בעתיד)
    private static final int MAX_PHOTOS_PER_USER = 6;

    public UserPhotoService(UserPhotoRepository userPhotoRepository,
                            UserRepository userRepository) {
        this.userPhotoRepository = userPhotoRepository;
        this.userRepository = userRepository;
    }

    // ----------------------------------------------------
    // 1. יצירה / העלאה של תמונה
    // ----------------------------------------------------


    @Transactional
    public UserPhoto addPhoto(Long userId,
                              String imageUrl,
                              boolean makePrimary,
                              Integer positionIndex) {

        if (userId == null || imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("userId and imageUrl are required");
        }

        // טעינת המשתמש
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // ספירת תמונות פעילות
        long activeCount = userPhotoRepository.countByUserAndDeletedFalse(user);
        if (activeCount >= MAX_PHOTOS_PER_USER) {
            throw new IllegalStateException(
                    "User already has maximum number of photos (" + MAX_PHOTOS_PER_USER + ")"
            );
        }

        // חישוב positionIndex אם לא נשלח
        if (positionIndex == null) {
            List<UserPhoto> existing = userPhotoRepository
                    .findByUserAndDeletedFalseOrderByPositionIndexAsc(user);

            int nextIndex;
            if (existing.isEmpty()) {
                nextIndex = 1;
            } else {
                UserPhoto last = existing.get(existing.size() - 1);
                Integer lastIndex = last.getPositionIndex();
                nextIndex = (lastIndex != null) ? lastIndex + 1 : existing.size() + 1;
            }
            positionIndex = nextIndex;
        }

        // יצירת תמונה חדשה
        UserPhoto photo = new UserPhoto();
        photo.setUser(user);
        photo.setImageUrl(imageUrl);
        photo.setPositionIndex(positionIndex);
        photo.setCreatedAt(LocalDateTime.now());
        photo.setDeleted(false);

        // האם יש כבר primary?
        boolean hasPrimary =
                userPhotoRepository.existsByUserAndPrimaryPhotoTrueAndDeletedFalse(user);

        // קביעת primary
        if (!hasPrimary || makePrimary) {
            photo.setPrimaryPhoto(true);
            clearPrimaryFlagFromOtherPhotos(user);
        } else {
            photo.setPrimaryPhoto(false);
        }

        // שמירה
        UserPhoto saved = userPhotoRepository.save(photo);

        // עדכון שדות משתמש
        syncUserPhotoFlagsAfterAdd(user, saved);

        // ------------------------------
        // 🔵 אפיון 2025 – חזרה אוטומטית לגלובלי
        // ------------------------------
        if (user.isFullProfileCompleted() && user.isHasPrimaryPhoto()) {
            if (user.isGlobalAccessApproved()) {
                user.setInGlobalPool(true);
            }
        }

        userRepository.save(user);

        return saved;
    }
    /**
     * עדכון photosCount + hasPrimaryPhoto אחרי הוספת תמונה.
     */
    private void syncUserPhotoFlagsAfterAdd(User user, UserPhoto newPhoto) {
        // ספירה מחדש של תמונות פעילות
        long activeCount = userPhotoRepository.countByUserAndDeletedFalse(user);

        user.setPhotosCount((int) activeCount);

        if (newPhoto.isPrimaryPhoto()) {
            user.setHasPrimaryPhoto(true);
        } else {
            // אם אין אף primary פעילה – נבדוק מחדש
            boolean hasPrimary =
                    userPhotoRepository.existsByUserAndPrimaryPhotoTrueAndDeletedFalse(user);
            user.setHasPrimaryPhoto(hasPrimary);
        }

        userRepository.save(user);
    }

    // ----------------------------------------------------
    // 2. החלפת / הגדרת תמונה ראשית
    // ----------------------------------------------------

    public UserPhoto setPrimaryPhoto(Long userId, Long photoId) {
        if (userId == null || photoId == null) {
            throw new IllegalArgumentException("userId and photoId are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserPhoto photo = userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));

        if (!photo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Photo does not belong to the given user");
        }

        if (photo.isDeleted()) {
            throw new IllegalStateException("Cannot set deleted photo as primary");
        }

        clearPrimaryFlagFromOtherPhotos(user);

        photo.setPrimaryPhoto(true);
        UserPhoto saved = userPhotoRepository.save(photo);

        // אם יש primary אחת לפחות → מסמנים hasPrimaryPhoto=true
        user.setHasPrimaryPhoto(true);
        userRepository.save(user);

        return saved;
    }

    private void clearPrimaryFlagFromOtherPhotos(User user) {
        List<UserPhoto> active = userPhotoRepository.findByUserAndDeletedFalse(user);

        for (UserPhoto p : active) {
            if (p.isPrimaryPhoto()) {
                p.setPrimaryPhoto(false);
            }
        }

        userPhotoRepository.saveAll(active);
    }

    // ----------------------------------------------------
    // 3. מחיקה לוגית / מחיקה מלאה
    // ----------------------------------------------------


    @Transactional
    public void softDeletePhoto(Long userId, Long photoId) {

        if (userId == null || photoId == null) {
            throw new IllegalArgumentException("userId and photoId are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserPhoto photo = userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));

        if (!photo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Photo does not belong to the given user");
        }

        boolean wasPrimary = photo.isPrimaryPhoto();

        // -------------------------------
        // 🔵 מחיקה לוגית של התמונה
        // -------------------------------
        photo.setDeleted(true);
        photo.setPrimaryPhoto(false);
        userPhotoRepository.save(photo);

        // -------------------------------
        // 🔵 אם זו הייתה תמונה ראשית – מציאת תחליף
        // -------------------------------
        if (wasPrimary) {
            UserPhoto replacement =
                    userPhotoRepository.findFirstByUserAndDeletedFalseOrderByCreatedAtAsc(user);

            if (replacement != null) {
                replacement.setPrimaryPhoto(true);
                userPhotoRepository.save(replacement);
            }
        }

        // -------------------------------
        // 🔵 עדכון photosCount + hasPrimaryPhoto
        // -------------------------------
        syncUserPhotoFlagsAfterDelete(user);

        // --------------------------------------------------------
        // 🔵 קריטי מאוד: ריענון אובייקט המשתמש מה־DB
        // --------------------------------------------------------
        // הסיבה: רשימת התמונות user.getPhotos() בזיכרון לא עודכנה,
        // ולכן ללא ריענון – המערכת עדיין חושבת שהתמונה קיימת ו-primary.
        user = userRepository.findById(userId).get();

        // --------------------------------------------------------
        // 🔵 שמירת הנתונים לאחר ריענון מלא
        // --------------------------------------------------------
        userRepository.save(user);
    }

    /**
     * עדכון photosCount + hasPrimaryPhoto אחרי מחיקה לוגית / מחיקה מלאה.
     */
    private void syncUserPhotoFlagsAfterDelete(User user) {
        long activeCount = userPhotoRepository.countByUserAndDeletedFalse(user);
        user.setPhotosCount((int) activeCount);

        boolean hasPrimary =
                userPhotoRepository.existsByUserAndPrimaryPhotoTrueAndDeletedFalse(user);
        user.setHasPrimaryPhoto(hasPrimary);

        userRepository.save(user);
    }

    /**
     * מחיקה פיזית של כל התמונות של משתמש (למשל בעת מחיקת חשבון סופית).
     */
    public void hardDeleteAllPhotosForUser(Long userId) {
        if (userId == null) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        userPhotoRepository.deleteByUser(user);

        // איפוס מוחלט בשדות המשתמש
        user.setPhotosCount(0);
        user.setHasPrimaryPhoto(false);
        userRepository.save(user);
    }

    // ----------------------------------------------------
    // 4. גלריה – שליפה וסידור מחדש
    // ----------------------------------------------------

    public List<UserPhoto> getActivePhotosForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.findByUserAndDeletedFalseOrderByPositionIndexAsc(user);
    }

    public List<UserPhoto> getAllPhotosForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.findByUser(user);
    }

    public UserPhoto getPrimaryPhotoForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.findByUserAndPrimaryPhotoTrueAndDeletedFalse(user);
    }

    public void reorderUserPhotos(Long userId, List<Long> photoIdsActiveInOrder) {
        if (userId == null || photoIdsActiveInOrder == null) {
            throw new IllegalArgumentException("userId and photoIdsActiveInOrder are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<UserPhoto> activePhotos =
                userPhotoRepository.findByUserAndDeletedFalseOrderByPositionIndexAsc(user);

        Map<Long, UserPhoto> activeById = activePhotos.stream()
                .collect(Collectors.toMap(UserPhoto::getId, p -> p));

        for (Long pid : photoIdsActiveInOrder) {
            if (!activeById.containsKey(pid)) {
                throw new IllegalArgumentException(
                        "Photo id " + pid + " is not an active photo of this user"
                );
            }
        }

        if (photoIdsActiveInOrder.size() != activePhotos.size()) {
            throw new IllegalArgumentException(
                    "Order list size does not match number of active photos"
            );
        }

        int index = 1;
        for (Long pid : photoIdsActiveInOrder) {
            UserPhoto p = activeById.get(pid);
            p.setPositionIndex(index++);
        }

        userPhotoRepository.saveAll(activePhotos);
        // אין שינוי בשדות משתמש – רק סדר גלריה.
    }

    // ----------------------------------------------------
    // 5. פונקציות עזר – למשתמש
    // ----------------------------------------------------

    /**
     * האם למשתמש יש לפחות תמונה פעילה אחת.
     */
    public boolean userHasAtLeastOneActivePhoto(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        long count = userPhotoRepository.countByUserAndDeletedFalse(user);
        return count > 0;
    }

    /**
     * האם למשתמש יש תמונה ראשית אמיתית (active + primary=true).
     */
    public boolean userHasPrimaryPhoto(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userPhotoRepository.existsByUserAndPrimaryPhotoTrueAndDeletedFalse(user);
    }
}