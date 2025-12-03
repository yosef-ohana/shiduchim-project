package com.example.myproject.repository;

import com.example.myproject.model.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {

    // ============================================================
    // 🔵 1. תמונות לפי משתמש
    // ============================================================

    List<UserPhoto> findByUser_Id(Long userId);

    List<UserPhoto> findByUser_IdOrderByPositionIndexAsc(Long userId);

    List<UserPhoto> findByUser_IdAndDeletedFalseOrderByPositionIndexAsc(Long userId);

    List<UserPhoto> findByUser_IdAndDeletedTrueOrderByDeletedAtDesc(Long userId);


    // ============================================================
    // 🔵 2. תמונה ראשית / תמונת Main
    // ============================================================

    Optional<UserPhoto> findByUser_IdAndPrimaryPhotoTrueAndDeletedFalse(Long userId);

    List<UserPhoto> findByUser_IdAndPrimaryPhotoTrue(Long userId);

    List<UserPhoto> findByUser_IdAndMainTrueAndDeletedFalse(Long userId);


    // ============================================================
    // 🔵 3. ספירות וסטטוסים
    // ============================================================

    long countByUser_Id(Long userId);

    long countByUser_IdAndDeletedFalse(Long userId);

    long countByUser_IdAndDeletedTrue(Long userId);

    boolean existsByUser_IdAndPrimaryPhotoTrue(Long userId);

    boolean existsByUser_IdAndDeletedFalse(Long userId);


    // ============================================================
    // 🔵 4. סדר / מיקום / גלריה
    // ============================================================

    Optional<UserPhoto> findByUser_IdAndPositionIndex(Long userId, Integer positionIndex);

    List<UserPhoto> findByUser_IdAndDeletedFalseOrderByCreatedAtAsc(Long userId);


    // ============================================================
    // 🔵 5. תמונות מחוקות לוגית / שחזור
    // ============================================================

    List<UserPhoto> findByDeletedTrueOrderByDeletedAtDesc();

    List<UserPhoto> findByDeletedTrueAndUser_Id(Long userId);


    // ============================================================
    // 🔵 6. תמונות לא ראויות
    // ============================================================

    List<UserPhoto> findByMetadataJsonContainingIgnoreCase(String flagText);

    // תמונות שסומנו כ"לא ראויה" בעזרת Flag (metadata, או fileType מסוים)
    List<UserPhoto> findByUser_IdAndMetadataJsonContainingIgnoreCase(Long userId, String flagText);


    // ============================================================
    // 🔵 7. העלאה ע"י אדמין / חסימת העלאות
    // ============================================================

    List<UserPhoto> findByUploadedByAdminTrueAndUser_Id(Long userId);

    long countByUploadedByAdminTrue();

    List<UserPhoto> findByUploadedByAdminTrue();


    // ============================================================
    // 🔵 8. תמונות לפי file type / size / validation
    // ============================================================

    List<UserPhoto> findByUser_IdAndFileType(String fileType);

    List<UserPhoto> findByUser_IdAndFileSizeBytesGreaterThan(Long size);

    List<UserPhoto> findByUser_IdAndFileSizeBytesLessThan(Long size);


    // ============================================================
    // 🔵 9. תמונות לפי תאריכים (Admin / Owner)
    // ============================================================

    List<UserPhoto> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<UserPhoto> findByDeletedAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 10. אופטימיזציה למנגנון "בחירת ראשית"
    // ============================================================

    List<UserPhoto> findByUser_IdAndDeletedFalseOrderByIdAsc(Long userId);


    // ============================================================
    // 🔵 11. תמונות נעולות אחרי חתונה
    // ============================================================

    List<UserPhoto> findByUser_IdAndLockedAfterWeddingTrue(Long userId);

    List<UserPhoto> findByLockedAfterWeddingTrue();


    // ============================================================
    // 🔵 12. תמונות כלל מערכת (Admin)
    // ============================================================

    List<UserPhoto> findByDeletedFalseOrderByCreatedAtDesc();

    List<UserPhoto> findByDeletedFalse();

    List<UserPhoto> findByDeletedTrue();

    Optional<UserPhoto> findByIdAndDeletedFalse(Long photoId);

    Optional<UserPhoto> findByIdAndUser_Id(Long photoId, Long userId);


    // ============================================================
    // 🔵 13. Cleanup — מחיקה פיזית
    // ============================================================

    List<UserPhoto> findByDeletedTrueAndDeletedAtBefore(LocalDateTime deleteBefore);

}