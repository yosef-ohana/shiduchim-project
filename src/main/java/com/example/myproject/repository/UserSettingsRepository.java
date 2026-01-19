package com.example.myproject.repository;

import com.example.myproject.model.UserSettings;
import com.example.myproject.model.enums.DefaultMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    // ============================================================
    // 1) בסיסי לפי משתמש
    // ============================================================

    Optional<UserSettings> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);

    // ============================================================
    // 2) DefaultMode
    // ============================================================

    List<UserSettings> findByDefaultMode(DefaultMode defaultMode);

    long countByDefaultMode(DefaultMode defaultMode);

    List<UserSettings> findByDefaultModeNot(DefaultMode defaultMode);

    // ============================================================
    // 3) Same-gender view
    // ============================================================

    List<UserSettings> findByCanViewSameGenderTrue();

    long countByCanViewSameGenderTrue();

    // ============================================================
    // 4) Anti-Spam אישי
    // ============================================================

    List<UserSettings> findByAutoAntiSpamTrue();

    long countByAutoAntiSpamTrue();

    List<UserSettings> findByLikeCooldownSecondsLessThanEqual(Integer seconds);

    List<UserSettings> findByMessageCooldownSecondsLessThanEqual(Integer seconds);

    // ============================================================
    // 5) Dashboard / JSON usage
    // ============================================================

    long countByShortCardFieldsJsonIsNotNull();

    long countByUiPreferencesJsonIsNotNull();

    long countByExtraSettingsJsonIsNotNull();

    // ============================================================
    // 6) Lock After Wedding — semantics “נעול כרגע”
    // lockedAfterWedding=true AND (lockedUntil IS NULL OR lockedUntil > now)
    // ============================================================

    List<UserSettings> findByLockedAfterWeddingTrue();

    // Legacy / תאימות: לא כולל lockedUntil NULL
    List<UserSettings> findByLockedAfterWeddingTrueAndLockedUntilAfter(LocalDateTime now);

    long countByLockedAfterWeddingTrue();

    @Query("""
            select us
            from UserSettings us
            where us.lockedAfterWedding = true
              and (us.lockedUntil is null or us.lockedUntil > :now)
            """)
    List<UserSettings> findCurrentlyLockedIncludingNull(@Param("now") LocalDateTime now);

    @Query("""
            select count(us)
            from UserSettings us
            where us.lockedAfterWedding = true
              and (us.lockedUntil is null or us.lockedUntil > :now)
            """)
    long countCurrentlyLockedIncludingNull(@Param("now") LocalDateTime now);

    // Locks שפגו (ל-Job auto-unlock)
    List<UserSettings> findByLockedAfterWeddingTrueAndLockedUntilIsNotNullAndLockedUntilBefore(LocalDateTime now);

    // ============================================================
    // 7) Maintenance לפי זמן
    // ============================================================

    List<UserSettings> findByUpdatedAtAfter(LocalDateTime time);

    List<UserSettings> findByCreatedAtBefore(LocalDateTime time);

    // ============================================================
    // 8) סטטיסטיקות מתקדמות
    // ============================================================

    long countByAutoAntiSpamTrueAndLikeCooldownSecondsLessThanEqual(Integer seconds);

    long countByAutoAntiSpamTrueAndMessageCooldownSecondsLessThanEqual(Integer seconds);

    // Legacy / תאימות: לא כולל lockedUntil NULL
    long countByLockedAfterWeddingTrueAndLockedUntilAfter(LocalDateTime now);

    // =====================================================
    // ✅ Paging variants (for heavy admin queries)
    // =====================================================

    Page<UserSettings> findByDefaultMode(DefaultMode mode, Pageable pageable);

    Page<UserSettings> findByLockedAfterWeddingTrue(Pageable pageable);

    Page<UserSettings> findByLockedUntilIsNotNull(Pageable pageable);

}