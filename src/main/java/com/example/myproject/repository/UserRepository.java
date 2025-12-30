package com.example.myproject.repository;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.model.enums.WeddingMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ============================================================
    // 🔵 1. הרשמה + התחברות + אימות (abilities 1–2 + חוק 22)
    // ============================================================

    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneOrEmail(String phone, String email);

    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);

    Optional<User> findByPhoneAndVerifiedTrue(String phone);
    Optional<User> findByEmailAndVerifiedTrue(String email);

    Optional<User> findByVerificationCode(String code);


    // ============================================================
    // 🔵 2. חסרים בפרופיל בסיסי (abilities 3, 6, 36)
    // ============================================================

    List<User> findByBasicProfileCompletedFalse();
    List<User> findByFullProfileCompletedFalse();

    List<User> findByHasPrimaryPhotoFalse();

    // שדות חסרים בסיסיים
    List<User> findByAgeIsNull();
    List<User> findByGenderIsNull();
    List<User> findByAreaOfResidenceIsNull();
    List<User> findByReligiousLevelIsNull();

    // תמונות חסרות / ספירה
    List<User> findByPhotosCountLessThan(int count);


    // ============================================================
    // 🔵 3. חסרים בפרופיל מלא (abilities 4, 7, 32, 36)
    // ============================================================

    List<User> findByOccupationIsNull();
    List<User> findByEducationIsNull();
    List<User> findByMilitaryServiceIsNull();
    List<User> findByHobbiesIsNull();
    List<User> findByPersonalityTraitsIsNull();
    List<User> findByLookingForIsNull();
    List<User> findByMaritalStatusIsNull();
    List<User> findByOriginIsNull();


    // סטטוס פרופיל לפי ENUM
    List<User> findByProfileState(ProfileState profileState);


    // ============================================================
    // 🔵 4. מאגר גלובלי + בקשות/אישורים (abilities 8–10, 20, 33)
    // ============================================================

    List<User> findByGlobalAccessRequestTrueAndGlobalAccessApprovedFalse();
    List<User> findByGlobalAccessApprovedTrue();
    List<User> findByInGlobalPoolTrue();
    long countByInGlobalPoolTrue();

    List<User> findByGlobalAccessState(GlobalAccessState state);

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueOrderByAgeAsc();


    // ============================================================
    // 🔵 5. פילטרים למאגרים (abilities 24, 26, 28)
    // ============================================================

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderOrderByAgeAsc(String gender);

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderAndAgeBetweenOrderByAgeAsc(
            String gender, Integer minAge, Integer maxAge
    );

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndAreaOfResidenceAndAgeBetweenOrderByAgeAsc(
            String areaOfResidence, Integer minAge, Integer maxAge
    );

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderAndAreaOfResidenceAndReligiousLevelAndAgeBetweenOrderByAgeAsc(
            String gender, String areaOfResidence, String religiousLevel, Integer minAge, Integer maxAge
    );


    // ============================================================
    // 🔵 6. חתונות — active / first / last / history (abilities 11–21, 31–35, 40)
    // ============================================================

    List<User> findByActiveWeddingId(Long weddingId);
    long countByActiveWeddingId(Long weddingId);

    List<User> findByLastWeddingId(Long weddingId);
    long countByLastWeddingId(Long weddingId);

    List<User> findByFirstWeddingId(Long weddingId);

    List<User> findByWeddingsHistoryContains(Long weddingId);

    List<User> findByActiveWeddingIdOrLastWeddingId(Long activeWeddingId, Long lastWeddingId);

    List<User> findByBackgroundMode(BackgroundMode mode);

    List<User> findByBackgroundWeddingId(Long weddingId);

    // שילובים חסרים
    List<User> findByWeddingExitAtBefore(LocalDateTime time);
    List<User> findByWeddingExitAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 7. WeddingMode (abilities 11–20)
    // ============================================================

    List<User> findByWeddingMode(WeddingMode mode);

    List<User> findByWeddingModeAndActiveWeddingId(WeddingMode mode, Long weddingId);

    List<User> findByWeddingModeAndHasPrimaryPhotoFalse(WeddingMode mode);


    // ============================================================
    // 🔵 8. נעילות, חסימות, גישה (abilities 18, 23, 29–30, 32–33, 39)
    // ============================================================

    List<User> findByProfileLockedAfterWeddingTrue();

    List<User> findByProfileLockedAfterWeddingTrueAndProfileLockedAtBefore(LocalDateTime time);

    List<User> findByDeletionRequestedTrue();

    // חסימות
    List<User> findByBlockedUserIdsContains(Long targetUserId);
    List<User> findByBlockedByUserIdsContains(Long actorUserId);


    // ============================================================
    // 🔵 9. תזמון — כניסות ויציאות (entry & exit)
    // ============================================================

    List<User> findByWeddingEntryAtAfter(LocalDateTime since);
    List<User> findByWeddingExitAtAfter(LocalDateTime since);

    List<User> findByWeddingEntryAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 10. דוחות / סטטיסטיקות (abilities מסמך 1–2–3)
    // ============================================================

    List<User> findByGender(String gender);
    List<User> findByAgeBetween(Integer minAge, Integer maxAge);
    List<User> findByAreaOfResidence(String area);
    List<User> findByReligiousLevel(String religiousLevel);

    List<User> findByGenderAndAreaOfResidenceAndReligiousLevel(
            String gender, String areaOfResidence, String religiousLevel
    );

    List<User> findByFullProfileCompletedTrueAndInGlobalPoolTrue();

    List<User> findByBasicProfileCompletedTrueAndFullProfileCompletedFalse();


    // ============================================================
    // 🔵 11. תמונות / AI (abilities 19, 26, 28)
    // ============================================================

    long countByHasPrimaryPhotoFalse();

    List<User> findByAiEmbeddingIsNotNull();

    List<User> findByAiMatchBoostScoreGreaterThan(Double score);

    // הרחבות AI
    List<User> findByActiveWeddingIdAndAiMatchBoostScoreGreaterThan(Long weddingId, Double score);
    List<User> findByInGlobalPoolTrueAndAiMatchBoostScoreGreaterThan(Double score);

    List<User> findByPhotosCount(int count);
    List<User> findByPhotosCountLessThanEqual(int count);
    List<User> findByHasPrimaryPhotoFalseAndPhotosCountGreaterThan(int count);


    // ============================================================
    // 🔵 12. הזמנות לאירוע
    // ============================================================

    List<User> findByInvitedByUserId(Long inviterId);


    // ============================================================
    // 🔵 13. עדכוני פרופיל
    // ============================================================

    List<User> findByLastProfileUpdateAtAfter(LocalDateTime timestamp);


    // ============================================================
    // 🔵 14. Heartbeat (ability 40)
    // ============================================================

    List<User> findByLastSeenBefore(LocalDateTime cutoff);
    List<User> findByLastSeenBetween(LocalDateTime start, LocalDateTime end);

    // ============================================================
// 🔵 14. Heartbeat (ability 40)
// ============================================================

// ❌ היה:
// List<User> findByLastSeenBefore(LocalDateTime cutoff);
// List<User> findByLastSeenBetween(LocalDateTime start, LocalDateTime end);

    // ✅ מתוקן לפי ה-Entity שלך:
    List<User> findByUpdatedAtBefore(LocalDateTime cutoff);
    List<User> findByUpdatedAtBetween(LocalDateTime start, LocalDateTime end);
}