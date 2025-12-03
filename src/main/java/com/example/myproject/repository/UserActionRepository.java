package com.example.myproject.repository;

import com.example.myproject.model.UserAction;
import com.example.myproject.model.enums.UserActionCategory;
import com.example.myproject.model.enums.UserActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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


    // ============================================================
    // 🔵 2. פעולות לפי משתמש יעד (Target)
    // ============================================================

    List<UserAction> findByTarget_IdOrderByCreatedAtDesc(Long targetId);
    List<UserAction> findByTarget_IdAndActiveTrueOrderByCreatedAtDesc(Long targetId);


    // ============================================================
    // 🔵 3. פעולות לפי סוג (Like / Dislike / Freeze / SuperLike / Block)
    // ============================================================

    List<UserAction> findByActor_IdAndActionTypeOrderByCreatedAtDesc(Long actorId, UserActionType type);
    List<UserAction> findByTarget_IdAndActionTypeOrderByCreatedAtDesc(Long targetId, UserActionType type);

    long countByActor_IdAndActionType(Long actorId, UserActionType type);
    long countByTarget_IdAndActionType(Long targetId, UserActionType type);


    // ============================================================
    // 🔵 4. פעולות לפי קטגוריה (LIKE / DISLIKE / FREEZE / MAYBE / SUPERLIKE)
    // ============================================================

    List<UserAction> findByActor_IdAndCategoryOrderByCreatedAtDesc(Long actorId, UserActionCategory category);
    List<UserAction> findByActor_IdAndCategoryAndActiveTrueOrderByCreatedAtDesc(Long actorId, UserActionCategory category);


    // ============================================================
    // 🔵 5. רשימות מיוחדות — Like / SuperLike / Freeze / Dislike / Received Likes
    // ============================================================

    // לייקים שאני נתתי
    List<UserAction> findByActor_IdAndActionTypeAndActiveTrueOrderByCreatedAtDesc(Long actorId, UserActionType type);

    // פריזים / דיסלייקים / סופרלייקים — פשוט לפי ActionType (אותה מתודה)

    // SuperLike שקיבלתי
    // לייקים שקיבלתי (כולל SuperLike) — לפי קטגוריה
    List<UserAction> findByTarget_IdAndCategoryOrderByCreatedAtDesc(Long targetId, UserActionCategory category);


    // ============================================================
    // 🔵 6. פעולות בהקשר חתונה / מאגר
    // ============================================================

    List<UserAction> findByWeddingIdOrderByCreatedAtDesc(Long weddingId);
    List<UserAction> findByOriginWeddingIdOrderByCreatedAtDesc(Long weddingId);

    List<UserAction> findByActor_IdAndWeddingIdOrderByCreatedAtDesc(Long actorId, Long weddingId);
    List<UserAction> findByTarget_IdAndWeddingIdOrderByCreatedAtDesc(Long targetId, Long weddingId);

    List<UserAction> findByActor_IdAndOriginWeddingIdOrderByCreatedAtDesc(Long actorId, Long weddingId);


    // ============================================================
    // 🔵 7. פעולות בהקשר Match
    // ============================================================

    List<UserAction> findByMatchIdOrderByCreatedAtDesc(Long matchId);
    List<UserAction> findByActor_IdAndMatchId(Long actorId, Long matchId);
    List<UserAction> findByTarget_IdAndMatchId(Long targetId, Long matchId);


    // ============================================================
    // 🔵 8. קבוצות פעולה (ActionGroup)
    // ============================================================

    List<UserAction> findByActionGroupId(Long groupId);
    List<UserAction> findByActor_IdAndActionGroupId(Long actorId, Long groupId);


    // ============================================================
    // 🔵 9. ניטור / Anti-Spam
    // ============================================================

    List<UserAction> findByActor_IdAndActionTypeAndCreatedAtAfter(
            Long actorId,
            UserActionType type,
            LocalDateTime since
    );

    List<UserAction> findByCreatedAtAfter(LocalDateTime since);


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
}