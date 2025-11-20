package com.example.myproject.repository;                        // ריפו יוזר

import com.example.myproject.model.User;                          // ישות User
import org.springframework.data.jpa.repository.JpaRepository;      // בסיס JPA
import org.springframework.data.jpa.repository.Query;              // לשאילתות מותאמות
import org.springframework.stereotype.Repository;                  // מסמן כריפו

import java.util.List;
import java.util.Optional;

@Repository                                                       // ריפוזיטורי JPA
public interface UserRepository extends JpaRepository<User, Long> {

    // ===============================
    // 🔵 זיהוי משתמשים
    // ===============================

    Optional<User> findByPhone(String phone);                     // לפי טלפון
    Optional<User> findByEmail(String email);                     // לפי אימייל
    Optional<User> findById(Long id);                             // לפי מזהה

    boolean existsByPhone(String phone);                          // האם טלפון קיים
    boolean existsByEmail(String email);                          // האם אימייל קיים


    // ===============================
    // 🔵 אימות חשבון
    // ===============================

    List<User> findByVerifiedTrue();                              // מאומתים
    List<User> findByVerifiedFalse();                             // לא מאומתים


    // ===============================
    // 🔵 פרופיל בסיסי / מלא
    // ===============================

    List<User> findByBasicProfileCompletedTrue();                 // השלים בסיסי
    List<User> findByFullProfileCompletedTrue();                  // השלים מלא

    // --- חדשים: פרופיל + תמונה ראשית חובה ---
    @Query("SELECT u FROM User u WHERE u.fullProfileCompleted = true AND u.hasPrimaryPhoto = true")
    List<User> findCompletedFullProfileWithPhoto();               // השלים מלא + תמונה ראשית

    @Query("SELECT u FROM User u WHERE u.basicProfileCompleted = true AND u.hasPrimaryPhoto = true")
    List<User> findCompletedBasicProfileWithPhoto();              // בסיסי + תמונה ראשית


    // ===============================
    // 🔵 מאגר גלובלי
    // ===============================

    List<User> findByInGlobalPoolTrue();                          // במאגר גלובלי
    List<User> findByGlobalAccessRequestTrue();                   // ביקש גישה
    List<User> findByGlobalAccessApprovedTrue();                  // אושר

    @Query("SELECT u FROM User u WHERE u.inGlobalPool = true AND u.hasPrimaryPhoto = true")
    List<User> findEligibleForGlobalPool();                       // מוכן לגלובלי (לפי חוקי האפיון)


    // ===============================
    // 🔵 חתונות (Wedding Context)
    // ===============================

    List<User> findByBackgroundWeddingId(Long weddingId);
    // --- משתמש שהיה אי פעם בחתונה (List<Long>) ---
    @Query("SELECT u FROM User u WHERE :weddingId MEMBER OF u.weddingsHistory")
    List<User> findUsersWhoAttendedWedding(Long weddingId);       // אופטימלי ונכון ל־JPA

    List<User> findByFirstWeddingId(Long weddingId);              // החתונה הראשונה שלו
    List<User> findByLastWeddingId(Long weddingId);               // החתונה האחרונה שלו

    // --- משתמשים עם הרשאת צפייה בחתונה ---
    List<User> findByCanViewWeddingTrue();                        // יכול לראות את חתונה הנוכחית


    // ===============================
    // 🔵 הרשאות מערכת / בעלי אירוע
    // ===============================

    List<User> findByAdminTrue();                                 // מנהלי מערכת

    List<User> findByEventManagerTrue();                          // בעלי אירועים

    List<User> findByEventOwnerForWeddingId(Long weddingId);      // בעל אירוע לפי חתונה


    // ===============================
    // 🔵 התראות
    // ===============================

    List<User> findByAllowInAppNotificationsTrue();               // מאפשר התראות In-App
    List<User> findByAllowEmailNotificationsTrue();               // מאפשר מייל
    List<User> findByAllowSmsNotificationsTrue();                 // מאפשר SMS


    // ===============================
    // 🔵 מחיקת חשבון
    // ===============================

    List<User> findByDeletionRequestedTrue();                     // ביקש מחיקה


    // ===============================
    // 🔵 חיפוש (Admin Dashboard)
    // ===============================

    List<User> findByFullNameContainingIgnoreCase(String name);       // לפי שם
    List<User> findByAreaOfResidenceContainingIgnoreCase(String area);// אזור
    List<User> findByOccupationContainingIgnoreCase(String occ);      // עיסוק
    List<User> findByEducationContainingIgnoreCase(String edu);       // השכלה
    List<User> findByOriginContainingIgnoreCase(String origin);       // מוצא
    List<User> findByGender(String gender);                           // מגדר


    // ===============================
    // 🔵 AI / ML תמיכה
    // ===============================

    List<User> findByAiEmbeddingIsNotNull();                        // יש embedding
    List<User> findByAiMatchBoostScoreGreaterThan(Double score);     // בוסט AI
}