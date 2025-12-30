package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.UserPhoto;
import com.example.myproject.repository.UserPhotoRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class UserPhotoService {

    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;

    public UserPhotoService(UserRepository userRepository,
                            UserPhotoRepository userPhotoRepository) {
        this.userRepository = userRepository;
        this.userPhotoRepository = userPhotoRepository;
    }

    // =====================================================
    // 🔵 העלאת תמונה חדשה
    // abilities 19, 26, 28
    // =====================================================

    public UserPhoto addPhoto(Long userId,
                              String imageUrl,
                              String fileType,
                              Long fileSizeBytes,
                              boolean setAsPrimary) {

        User user = getUserOrThrow(userId);

        int nextIndex = computeNextPositionIndex(userId);

        UserPhoto photo = new UserPhoto(user, imageUrl, false, nextIndex);
        photo.setFileType(fileType);
        photo.setFileSizeBytes(fileSizeBytes);
        photo.setUploadedByAdmin(false);
        photo.setLockedAfterWedding(false);
        photo.setCreatedAt(LocalDateTime.now());

        UserPhoto saved = userPhotoRepository.save(photo);

        incrementPhotosCount(user);

        if (setAsPrimary || !user.isHasPrimaryPhoto()) {
            setPrimaryPhotoInternal(user, saved);
        }

        userRepository.save(user);
        return saved;
    }

    private int computeNextPositionIndex(Long userId) {
        List<UserPhoto> photos =
                userPhotoRepository.findByUser_IdAndDeletedFalseOrderByPositionIndexAscIdAsc(userId);        if (photos.isEmpty()) return 0;
        return photos.stream()
                .map(UserPhoto::getPositionIndex)
                .filter(i -> i != null)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private void incrementPhotosCount(User user) {
        int count = (user.getPhotosCount() != null ? user.getPhotosCount() : 0);
        user.setPhotosCount(count + 1);
    }

    private void decrementPhotosCount(User user) {
        int count = (user.getPhotosCount() != null ? user.getPhotosCount() : 0);
        user.setPhotosCount(Math.max(0, count - 1));
    }

    // =====================================================
    // 🔵 שינוי תמונה ראשית
    // =====================================================

    public void setPrimaryPhoto(Long userId, Long photoId) {
        User user = getUserOrThrow(userId);
        UserPhoto photo = getPhotoOrThrow(photoId);

        if (!photo.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }

        setPrimaryPhotoInternal(user, photo);
        userRepository.save(user);
    }

    private void setPrimaryPhotoInternal(User user, UserPhoto newPrimary) {
        List<UserPhoto> photos =
                userPhotoRepository.findByUser_IdAndDeletedFalseOrderByPositionIndexAscIdAsc(user.getId());
        for (UserPhoto p : photos) {
            if (p.getId().equals(newPrimary.getId())) {
                p.setPrimaryPhoto(true);
            } else {
                p.setPrimaryPhoto(false);
            }
        }
        userPhotoRepository.saveAll(photos);

        user.setHasPrimaryPhoto(true);
    }

    // =====================================================
    // 🔵 מחיקת תמונה (לוגית)
    // =====================================================

    public void deletePhoto(Long userId, Long photoId) {
        User user = getUserOrThrow(userId);
        UserPhoto photo = getPhotoOrThrow(photoId);

        if (!photo.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }

        boolean wasPrimary = photo.isPrimaryPhoto();

        photo.setDeleted(true);
        photo.setPrimaryPhoto(false);
        userPhotoRepository.save(photo);

        decrementPhotosCount(user);

        if (wasPrimary) {
            // נמצא תמונה אחרת שתהיה ראשית (אם קיימת)
            List<UserPhoto> remaining =
                    userPhotoRepository.findByUser_IdAndDeletedFalseOrderByPositionIndexAscIdAsc(userId);            if (!remaining.isEmpty()) {
                remaining.get(0).setPrimaryPhoto(true);
                userPhotoRepository.save(remaining.get(0));
                user.setHasPrimaryPhoto(true);
            } else {
                user.setHasPrimaryPhoto(false);
            }
        }

        userRepository.save(user);
    }

    // =====================================================
    // 🔵 נעילת תמונות אחרי חתונה (חוקי freeze)
    // =====================================================

    public void lockPhotosAfterWedding(Long userId) {
        List<UserPhoto> photos =
                userPhotoRepository.findByUser_IdAndDeletedFalseOrderByPositionIndexAscIdAsc(userId);        for (UserPhoto p : photos) {
            p.setLockedAfterWedding(true);
        }
        userPhotoRepository.saveAll(photos);
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private UserPhoto getPhotoOrThrow(Long photoId) {
        return userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));
    }
}