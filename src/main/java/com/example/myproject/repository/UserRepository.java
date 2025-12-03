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

    // אימות לפי קוד
    Optional<User> findByVerificationCode(String code);


    // ============================================================
    // 🔵 2. פרופיל בסיסי / מלא / סטטוס פרופיל (abilities 3–7, 32, 36–37)
    // ============================================================

    List<User> findByBasicProfileCompletedFalse();
    List<User> findByFullProfileCompletedFalse();


    List<User> findByHasPrimaryPhotoFalse();

    // סטטוס פרופיל לפי ENUM
    List<User> findByProfileState(ProfileState profileState);


    // ============================================================
    // 🔵 3. מאגר גלובלי + בקשות/אישורים (abilities 8–10, 20, 33)
    // ============================================================

    // מי שביקש גלובלי
    List<User> findByGlobalAccessRequestTrueAndGlobalAccessApprovedFalse();

    // מי שאושר לגלובלי
    List<User> findByGlobalAccessApprovedTrue();

    // מי שנמצא במאגר הגלובלי בפועל
    List<User> findByInGlobalPoolTrue();

    long countByInGlobalPoolTrue();

    // לפי סטטוס ENUM של globalAccessState
    List<User> findByGlobalAccessState(GlobalAccessState state);

    // פילטר מרכזי למאגר גלובלי – למיון לפי גיל
    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueOrderByAgeAsc();


    // ============================================================
    // 🔵 4. פילטרים לכרטיסי גלובלי (abilities 24, 26, 28)
    // ============================================================

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderOrderByAgeAsc(
            String gender
    );

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderAndAgeBetweenOrderByAgeAsc(
            String gender,
            Integer minAge,
            Integer maxAge
    );

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndAreaOfResidenceAndAgeBetweenOrderByAgeAsc(
            String areaOfResidence,
            Integer minAge,
            Integer maxAge
    );

    List<User> findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderAndAreaOfResidenceAndReligiousLevelAndAgeBetweenOrderByAgeAsc(
            String gender,
            String areaOfResidence,
            String religiousLevel,
            Integer minAge,
            Integer maxAge
    );


    // ============================================================
    // 🔵 5. חתונות — active / first / last / history (abilities 11–21, 31–35, 40)
    // ============================================================

    // מי שנמצא כרגע בחתונה
    List<User> findByActiveWeddingId(Long weddingId);
    long countByActiveWeddingId(Long weddingId);

    // מי שהחתונה האחרונה שלהם היא X
    List<User> findByLastWeddingId(Long weddingId);
    long countByLastWeddingId(Long weddingId);

    // מי שהחתונה הראשונה שלהם היא X
    List<User> findByFirstWeddingId(Long weddingId);

    // חיפוש ב-weddingsHistory (List<Long>)
    List<User> findByWeddingsHistoryContains(Long weddingId);

    // מי שקשור לחתונה דרך: activeWeddingId OR lastWeddingId
    List<User> findByActiveWeddingIdOrLastWeddingId(Long activeWeddingId, Long lastWeddingId);

    // שינויי רקע בהתאם ל-backgroundMode
    List<User> findByBackgroundMode(BackgroundMode mode);

    // מי שמשתמש בחתונה כ־background source
    List<User> findByBackgroundWeddingId(Long weddingId);


    // ============================================================
    // 🔵 6. מצב חתונה / WeddingMode (abilities 11–20)
    // ============================================================

    List<User> findByWeddingMode(WeddingMode mode);

    List<User> findByWeddingModeAndActiveWeddingId(
            WeddingMode mode,
            Long weddingId
    );

    // מי שכרגע ב-WeddingMode אבל לא מחזיק תמונה ראשית (בקרה)
    List<User> findByWeddingModeAndHasPrimaryPhotoFalse(WeddingMode mode);


    // ============================================================
    // 🔵 7. נעילות, חסימות, גישה (abilities 18, 23, 29–30, 32–33, 39)
    // ============================================================

    // נעול אחרי חתונה
    List<User> findByProfileLockedAfterWeddingTrue();

    // לפי זמן נעילה
    List<User> findByProfileLockedAfterWeddingTrueAndProfileLockedAtBefore(LocalDateTime time);

    // משתמשים שביקשו מחיקה
    List<User> findByDeletionRequestedTrue();


    // ============================================================
    // 🔵 8. תזמון / זמנים / כניסות ויציאות (weddingEntryAt / weddingExitAt)
    // ============================================================

    List<User> findByWeddingEntryAtAfter(LocalDateTime since);
    List<User> findByWeddingExitAtAfter(LocalDateTime since);

    // מי נכנס לאירוע אחרי זמן מסוים
    List<User> findByWeddingEntryAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 9. דוחות / סטטיסטיקות / ניתוח נתונים (מסמך 1–2–3)
    // ============================================================

    List<User> findByGender(String gender);

    List<User> findByAgeBetween(Integer minAge, Integer maxAge);

    List<User> findByAreaOfResidence(String area);

    List<User> findByReligiousLevel(String religiousLevel);

    // חיתוך מרכזי לסטטיסטיקות
    List<User> findByGenderAndAreaOfResidenceAndReligiousLevel(
            String gender,
            String areaOfResidence,
            String religiousLevel
    );

    // משתמשים עם פרופיל מלא + גלובלי (אנליטיקה)
    List<User> findByFullProfileCompletedTrueAndInGlobalPoolTrue();

    // משתמשים עם פרופיל בסיסי בלבד
    List<User> findByBasicProfileCompletedTrueAndFullProfileCompletedFalse();


    // ============================================================
    // 🔵 10. תמונות / תמונות ראשיות / AI (abilities 19, 26, 28)
    // ============================================================

    // מי שאין לו תמונה ראשית
    long countByHasPrimaryPhotoFalse();

    // תמיכה בשדה aiEmbedding (ניתוח קל)
    List<User> findByAiEmbeddingIsNotNull();

    List<User> findByAiMatchBoostScoreGreaterThan(Double score);


    // ============================================================
    // 🔵 11. הזמנות לאירוע
    // ============================================================

    List<User> findByInvitedByUserId(Long inviterId);


    // ============================================================
    // 🔵 12. עדכוני פרופיל
    // ============================================================

    List<User> findByLastProfileUpdateAtAfter(LocalDateTime timestamp);

}