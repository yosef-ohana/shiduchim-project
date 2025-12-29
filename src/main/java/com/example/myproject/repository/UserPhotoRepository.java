package com.example.myproject.repository;

import com.example.myproject.model.UserPhoto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {

    // ============================================================
    // 1) בסיסי לפי משתמש
    // ============================================================

    List<UserPhoto> findByUser_Id(Long userId);

    List<UserPhoto> findByUser_IdOrderByPositionIndexAscIdAsc(Long userId);

    List<UserPhoto> findByUser_IdAndDeletedFalseOrderByPositionIndexAscIdAsc(Long userId);

    Page<UserPhoto> findByUser_IdAndDeletedFalse(Long userId, Pageable pageable);

    List<UserPhoto> findByUser_IdAndDeletedTrueOrderByDeletedAtDescIdDesc(Long userId);

    Optional<UserPhoto> findByIdAndUser_Id(Long photoId, Long userId);

    Optional<UserPhoto> findByIdAndUser_IdAndDeletedFalse(Long photoId, Long userId);

    // ============================================================
    // 2) Primary / Main
    // ============================================================

    Optional<UserPhoto> findFirstByUser_IdAndDeletedFalseAndPrimaryPhotoTrue(Long userId);

    List<UserPhoto> findByUser_IdAndMainTrueAndDeletedFalse(Long userId);

    boolean existsByUser_IdAndDeletedFalseAndPrimaryPhotoTrue(Long userId);

    // ============================================================
    // 3) ספירות
    // ============================================================

    long countByUser_Id(Long userId);

    long countByUser_IdAndDeletedFalse(Long userId);

    long countByUser_IdAndDeletedTrue(Long userId);

    boolean existsByUser_IdAndDeletedFalse(Long userId);

    // ============================================================
    // 4) סדר / מיקום
    // ============================================================

    @Query("select max(p.positionIndex) from UserPhoto p where p.user.id = :userId")
    Integer findMaxPositionIndexByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("update UserPhoto p set p.positionIndex = :positionIndex where p.id = :photoId")
    int updatePosition(@Param("photoId") Long photoId, @Param("positionIndex") int positionIndex);

    @Modifying
    @Transactional
    @Query("update UserPhoto p set p.primaryPhoto = false, p.main = false where p.user.id = :userId")
    int clearPrimaryForUser(@Param("userId") Long userId);

    // ============================================================
    // 5) FileType / FileSize (מותאם לסרביס + deleted=false)
    // ============================================================

    List<UserPhoto> findByUser_IdAndDeletedFalseAndFileTypeIgnoreCaseOrderByPositionIndexAscIdAsc(Long userId, String fileType);

    List<UserPhoto> findByUser_IdAndDeletedFalseAndFileSizeBytesGreaterThanOrderByPositionIndexAscIdAsc(Long userId, long minBytes);

    List<UserPhoto> findByUser_IdAndDeletedFalseAndFileSizeBytesLessThanOrderByPositionIndexAscIdAsc(Long userId, long maxBytes);

    // ============================================================
    // 6) "לא ראויה" (metadataJson text search) — Admin/Owner
    // ============================================================

    List<UserPhoto> findByMetadataJsonContainingIgnoreCase(String flagText);

    List<UserPhoto> findByUser_IdAndMetadataJsonContainingIgnoreCase(Long userId, String flagText);

    // ============================================================
    // 7) העלאה ע"י אדמין
    // ============================================================

    List<UserPhoto> findByUploadedByAdminTrueAndUser_Id(Long userId);

    long countByUploadedByAdminTrue();

    List<UserPhoto> findByUploadedByAdminTrue();

    // ============================================================
    // 8) נעילה אחרי חתונה
    // ============================================================

    List<UserPhoto> findByUser_IdAndLockedAfterWeddingTrue(Long userId);

    List<UserPhoto> findByLockedAfterWeddingTrue();

    @Modifying
    @Transactional
    @Query("update UserPhoto p set p.lockedAfterWedding = true where p.user.id = :userId and p.deleted = false")
    int lockAllActiveAfterWedding(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("update UserPhoto p set p.lockedAfterWedding = false where p.user.id = :userId")
    int unlockAllByAdmin(@Param("userId") Long userId);

    // ============================================================
    // 9) תאריכים / Cleanup
    // ============================================================

    List<UserPhoto> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<UserPhoto> findByDeletedAtBetween(LocalDateTime start, LocalDateTime end);

    List<UserPhoto> findByDeletedTrueAndDeletedAtBefore(LocalDateTime deleteBefore);

    // ============================================================
    // 10) כלל מערכת (Admin)
    // ============================================================

    List<UserPhoto> findByDeletedFalseOrderByCreatedAtDesc();

    List<UserPhoto> findByDeletedFalse();

    List<UserPhoto> findByDeletedTrue();

    Optional<UserPhoto> findByIdAndDeletedFalse(Long photoId);
}