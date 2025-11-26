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

    @Query("""
            SELECT u FROM User u
            WHERE u.fullProfileCompleted = true
            AND u.hasPrimaryPhoto = true
            """)
    List<User> findCompletedFullProfileWithPhoto();

    @Query("""
            SELECT u FROM User u
            WHERE u.basicProfileCompleted = true
            AND u.hasPrimaryPhoto = true
            """)
    List<User> findCompletedBasicProfileWithPhoto();


    // ===============================
    // 🔵 מאגר גלובלי
    // ===============================

    List<User> findByInGlobalPoolTrue();
    List<User> findByGlobalAccessRequestTrue();
    List<User> findByGlobalAccessApprovedTrue();

    // ⭐️ תוספת — רק מיועדי שידוכים (לא אדמין/מנהל)
    @Query("""
            SELECT u FROM User u
            WHERE u.inGlobalPool = true
            AND u.hasPrimaryPhoto = true
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findEligibleForGlobalPool();


    // ===============================
    // 🔵 חתונות — Wedding Context
    // ===============================

    List<User> findByBackgroundWeddingId(Long weddingId);

    @Query("""
            SELECT u FROM User u
            WHERE :weddingId MEMBER OF u.weddingsHistory
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findUsersWhoAttendedWedding(Long weddingId);

    List<User> findByFirstWeddingId(Long weddingId);

    List<User> findByLastWeddingId(Long weddingId);

    List<User> findByCanViewWeddingTrue();


    // ===============================
    // 🔵 הרשאות מערכת / בעלי אירוע
    // ===============================

    List<User> findByAdminTrue();
    List<User> findByEventManagerTrue();


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

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :name, '%'))
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findByFullNameContainingIgnoreCase(String name);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.areaOfResidence) LIKE LOWER(CONCAT('%', :area, '%'))
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findByAreaOfResidenceContainingIgnoreCase(String area);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.occupation) LIKE LOWER(CONCAT('%', :occ, '%'))
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findByOccupationContainingIgnoreCase(String occ);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.education) LIKE LOWER(CONCAT('%', :edu, '%'))
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findByEducationContainingIgnoreCase(String edu);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.origin) LIKE LOWER(CONCAT('%', :origin, '%'))
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findByOriginContainingIgnoreCase(String origin);

    @Query("""
            SELECT u FROM User u
            WHERE u.gender = :gender
            AND u.admin = false
            AND u.eventManager = false
            """)
    List<User> findByGender(String gender);


    // ===============================
    // 🔵 AI / ML תמיכה
    // ===============================

    List<User> findByAiEmbeddingIsNotNull();
    List<User> findByAiMatchBoostScoreGreaterThan(Double score);
}