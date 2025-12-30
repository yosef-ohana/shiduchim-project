package com.example.myproject.repository;

import com.example.myproject.model.UserAction;
import com.example.myproject.model.enums.UserActionCategory;
import com.example.myproject.model.enums.UserActionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActionRepository extends JpaRepository<UserAction, Long> {

    // ============================================================
    // 🔵 1. פעולות לפי משתמש מבצע (Actor)
    // ============================================================

    List<UserAction> findByActor_IdOrderByCreatedAtDesc(Long actorId);
    List<UserAction> findByActor_IdAndActiveTrueOrderByCreatedAtDesc(Long actorId);
    List<UserAction> findByActor_IdAndCreatedAtAfter(Long actorId, LocalDateTime since);

    // ✅ OPTIMAL: Pageable (לא טוען הכל לזיכרון)
    List<UserAction> findByActor_IdOrderByCreatedAtDesc(Long actorId, Pageable pageable);
    List<UserAction> findByActor_IdAndActiveTrueOrderByCreatedAtDesc(Long actorId, Pageable pageable);
    List<UserAction> findByActor_IdAndCreatedAtAfter(Long actorId, LocalDateTime since, Pageable pageable);

    // ✅ counts DB-side
    long countByActor_Id(Long actorId);

    // יעיל ל-RateLimit (exists)
    boolean existsByActor_IdAndCreatedAtAfter(Long actorId, LocalDateTime since);

    // יעיל ל-Cooldown לפי סוג פעולה
    boolean existsByActor_IdAndActionTypeAndCreatedAtAfter(Long actorId, UserActionType type, LocalDateTime since);

    // ============================================================
    // 🔵 2. פעולות לפי משתמש יעד (Target)
    // ============================================================

    List<UserAction> findByTarget_IdOrderByCreatedAtDesc(Long targetId);
    List<UserAction> findByTarget_IdAndActiveTrueOrderByCreatedAtDesc(Long targetId);

    // ✅ OPTIMAL: Pageable
    List<UserAction> findByTarget_IdOrderByCreatedAtDesc(Long targetId, Pageable pageable);
    List<UserAction> findByTarget_IdAndActiveTrueOrderByCreatedAtDesc(Long targetId, Pageable pageable);

    // ✅ counts DB-side
    long countByTarget_Id(Long targetId);

    // ============================================================
    // 🔵 3. פעולות לפי סוג (Like / Dislike / Freeze / SuperLike / Block)
    // ============================================================

    List<UserAction> findByActor_IdAndActionTypeOrderByCreatedAtDesc(Long actorId, UserActionType type);
    List<UserAction> findByTarget_IdAndActionTypeOrderByCreatedAtDesc(Long targetId, UserActionType type);

    // ✅ OPTIMAL: Pageable
    List<UserAction> findByActor_IdAndActionTypeOrderByCreatedAtDesc(Long actorId, UserActionType type, Pageable pageable);
    List<UserAction> findByTarget_IdAndActionTypeOrderByCreatedAtDesc(Long targetId, UserActionType type, Pageable pageable);

    long countByActor_IdAndActionType(Long actorId, UserActionType type);
    long countByTarget_IdAndActionType(Long targetId, UserActionType type);

    // ============================================================
    // 🔵 4. פעולות לפי קטגוריה
    // ============================================================

    List<UserAction> findByActor_IdAndCategoryOrderByCreatedAtDesc(Long actorId, UserActionCategory category);
    List<UserAction> findByActor_IdAndCategoryAndActiveTrueOrderByCreatedAtDesc(Long actorId, UserActionCategory category);

    // ✅ OPTIMAL: Pageable
    List<UserAction> findByActor_IdAndCategoryOrderByCreatedAtDesc(Long actorId, UserActionCategory category, Pageable pageable);
    List<UserAction> findByActor_IdAndCategoryAndActiveTrueOrderByCreatedAtDesc(Long actorId, UserActionCategory category, Pageable pageable);

    // ============================================================
    // 🔵 5. רשימות מיוחדות
    // ============================================================

    List<UserAction> findByActor_IdAndActionTypeAndActiveTrueOrderByCreatedAtDesc(Long actorId, UserActionType type);
    List<UserAction> findByTarget_IdAndCategoryOrderByCreatedAtDesc(Long targetId, UserActionCategory category);

    // ✅ FIX (יעילות + כדי לא לטעון הכל כשעושים limit ב-service)
    List<UserAction> findByActor_IdAndActionTypeAndActiveTrueOrderByCreatedAtDesc(Long actorId, UserActionType type, Pageable pageable);

    // ============================================================
    // 🔵 6. פעולות בהקשר חתונה / מאגר
    // ============================================================

    List<UserAction> findByWeddingIdOrderByCreatedAtDesc(Long weddingId);
    List<UserAction> findByOriginWeddingIdOrderByCreatedAtDesc(Long weddingId);

    // ✅ OPTIMAL: Pageable
    List<UserAction> findByWeddingIdOrderByCreatedAtDesc(Long weddingId, Pageable pageable);
    List<UserAction> findByOriginWeddingIdOrderByCreatedAtDesc(Long weddingId, Pageable pageable);

    List<UserAction> findByActor_IdAndWeddingIdOrderByCreatedAtDesc(Long actorId, Long weddingId);
    List<UserAction> findByTarget_IdAndWeddingIdOrderByCreatedAtDesc(Long targetId, Long weddingId);

    List<UserAction> findByActor_IdAndOriginWeddingIdOrderByCreatedAtDesc(Long actorId, Long weddingId);

    // ============================================================
    // 🔵 7. פעולות בהקשר Match
    // ============================================================

    List<UserAction> findByMatchIdOrderByCreatedAtDesc(Long matchId);
    List<UserAction> findByActor_IdAndMatchId(Long actorId, Long matchId);
    List<UserAction> findByTarget_IdAndMatchId(Long targetId, Long matchId);

    // ✅ OPTIMAL: Pageable
    List<UserAction> findByMatchIdOrderByCreatedAtDesc(Long matchId, Pageable pageable);

    // ============================================================
    // 🔵 8. קבוצות פעולה (ActionGroup)
    // ============================================================

    List<UserAction> findByActionGroupId(Long groupId);
    List<UserAction> findByActor_IdAndActionGroupId(Long actorId, Long groupId);

    // ============================================================
    // 🔵 9. ניטור / Anti-Spam
    // ============================================================

    List<UserAction> findByActor_IdAndActionTypeAndCreatedAtAfter(Long actorId, UserActionType type, LocalDateTime since);

    // ✅ קיימת כבר
    List<UserAction> findByCreatedAtAfter(LocalDateTime since);

    // ✅ התוספת שסוגרת את השגיאה שדיברנו עליה
    List<UserAction> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since, Pageable pageable);

    // (אופציונלי אבל שימושי)
    List<UserAction> findByCreatedAtAfter(LocalDateTime since, Pageable pageable);

    // ============================================================
    // 🔵 10. פעולות לפי מקור (user / admin / system / ai)
    // ============================================================

    List<UserAction> findBySourceOrderByCreatedAtDesc(String source);
    List<UserAction> findByActor_IdAndSourceOrderByCreatedAtDesc(Long actorId, String source);

    // ============================================================
    // 🔵 11. חיפוש לפי metadata
    // ============================================================

    List<UserAction> findByMetadataContainingIgnoreCase(String text);

    // ============================================================
    // 🔵 12. ACTIVE / INACTIVE
    // ============================================================

    List<UserAction> findByActor_IdAndActiveTrue(Long actorId);
    List<UserAction> findByActor_IdAndActiveFalse(Long actorId);
    List<UserAction> findByTarget_IdAndActiveTrue(Long targetId);

    // ============================================================
    // 🔵 13. פילטרים מתקדמים משולבים
    // ============================================================

    List<UserAction> findByActor_IdAndActionTypeAndWeddingId(Long actorId, UserActionType type, Long weddingId);
    List<UserAction> findByActor_IdAndCategoryAndWeddingId(Long actorId, UserActionCategory category, Long weddingId);
    List<UserAction> findByActor_IdAndActionTypeAndOriginWeddingId(Long actorId, UserActionType type, Long originWeddingId);

    // ============================================================
    // 🔵 14. ספירות סטטיסטיות
    // ============================================================

    long countByWeddingId(Long weddingId);
    long countByActor_IdAndWeddingId(Long actorId, Long weddingId);

    long countByActionType(UserActionType type);
    long countByCategory(UserActionCategory category);

    // ============================================================
    // 🔵 15. ניקוי לוגים ישנים
    // ============================================================

    List<UserAction> findByCreatedAtBefore(LocalDateTime time);

    @Modifying
    @Transactional
    long deleteByCreatedAtBefore(LocalDateTime time);

    @Modifying
    @Transactional
    long deleteByCreatedAtBeforeAndActiveFalse(LocalDateTime time);

    // ============================================================
    // ✅ Idempotency (Duplicate detection) – TOP 1 (עם הקונטקסט!)
    // ============================================================

    @Query("""
        select ua
        from UserAction ua
        where ua.actor.id = :actorId
          and ua.target.id = :targetId
          and ua.actionType = :type
          and ua.category = :category
          and ua.active = true
          and ua.createdAt >= :since
          and (:weddingId is null or ua.weddingId = :weddingId)
          and (:originWeddingId is null or ua.originWeddingId = :originWeddingId)
          and (:matchId is null or ua.matchId = :matchId)
        order by ua.createdAt desc
    """)
    List<UserAction> findRecentDuplicateWithContext(
            @Param("actorId") Long actorId,
            @Param("targetId") Long targetId,
            @Param("type") UserActionType type,
            @Param("category") UserActionCategory category,
            @Param("since") LocalDateTime since,
            @Param("weddingId") Long weddingId,
            @Param("originWeddingId") Long originWeddingId,
            @Param("matchId") Long matchId,
            Pageable pageable
    );

    // ============================================================
    // 🔵 Actor/Target direct ops (FIXED to match entity relations)
    // ============================================================

    boolean existsByActor_IdAndTarget_IdAndActionType(Long actorId, Long targetId, UserActionType type);

    @Modifying
    @Transactional
    void deleteByActor_IdAndTarget_IdAndActionType(Long actorId, Long targetId, UserActionType type);

    @Modifying
    @Transactional
    void deleteByActor_IdAndTarget_Id(Long actorId, Long targetId);
}