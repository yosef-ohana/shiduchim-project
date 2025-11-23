package com.example.myproject.repository;

import com.example.myproject.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ===============================
    // 🔵 זיהוי משתמשים
    // ===============================

    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);

    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);


    // ===============================
    // 🔵 אימות חשבון
    // ===============================

    List<User> findByVerifiedTrue();
    List<User> findByVerifiedFalse();


    // ===============================
    // 🔵 פרופיל בסיסי / מלא
    // ===============================

    List<User> findByBasicProfileCompletedTrue();
    List<User> findByFullProfileCompletedTrue();

    @Query("SELECT u FROM User u WHERE u.fullProfileCompleted = true AND u.hasPrimaryPhoto = true")
    List<User> findCompletedFullProfileWithPhoto();

    @Query("SELECT u FROM User u WHERE u.basicProfileCompleted = true AND u.hasPrimaryPhoto = true")
    List<User> findCompletedBasicProfileWithPhoto();


    // ===============================
    // 🔵 מאגר גלובלי
    // ===============================

    List<User> findByInGlobalPoolTrue();
    List<User> findByGlobalAccessRequestTrue();
    List<User> findByGlobalAccessApprovedTrue();

    @Query("SELECT u FROM User u WHERE u.inGlobalPool = true AND u.hasPrimaryPhoto = true")
    List<User> findEligibleForGlobalPool();


    // ===============================
    // 🔵 חתונות — Wedding Context
    // ===============================

    List<User> findByBackgroundWeddingId(Long weddingId);

    @Query("SELECT u FROM User u WHERE :weddingId MEMBER OF u.weddingsHistory")
    List<User> findUsersWhoAttendedWedding(Long weddingId);

    List<User> findByFirstWeddingId(Long weddingId);
    List<User> findByLastWeddingId(Long weddingId);

    List<User> findByCanViewWeddingTrue();


    // ===============================
    // 🔵 הרשאות מערכת / בעלי אירוע
    // ===============================

    List<User> findByAdminTrue();
    List<User> findByEventManagerTrue();

    // ❌ נמחק — כי אינו קיים ב־User ואינו מופיע באפיון 2025
    // List<User> findByEventOwnerForWeddingId(Long weddingId);


    // ===============================
    // 🔵 התראות
    // ===============================

    List<User> findByAllowInAppNotificationsTrue();
    List<User> findByAllowEmailNotificationsTrue();
    List<User> findByAllowSmsNotificationsTrue();


    // ===============================
    // 🔵 מחיקת חשבון
    // ===============================

    List<User> findByDeletionRequestedTrue();


    // ===============================
    // 🔵 חיפוש (Admin Dashboard)
    // ===============================

    List<User> findByFullNameContainingIgnoreCase(String name);
    List<User> findByAreaOfResidenceContainingIgnoreCase(String area);
    List<User> findByOccupationContainingIgnoreCase(String occ);
    List<User> findByEducationContainingIgnoreCase(String edu);
    List<User> findByOriginContainingIgnoreCase(String origin);
    List<User> findByGender(String gender);


    // ===============================
    // 🔵 AI / ML תמיכה
    // ===============================

    List<User> findByAiEmbeddingIsNotNull();
    List<User> findByAiMatchBoostScoreGreaterThan(Double score);
}