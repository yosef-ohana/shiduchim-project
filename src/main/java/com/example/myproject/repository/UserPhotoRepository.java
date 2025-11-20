package com.example.myproject.repository;                           // ריפו תמונות משתמש

import com.example.myproject.model.User;                             // ישות משתמש
import com.example.myproject.model.UserPhoto;                        // ישות תמונה
import org.springframework.data.jpa.repository.JpaRepository;         // בסיס JPA
import org.springframework.stereotype.Repository;                    // מסמן כריפו

import java.time.LocalDateTime;                                      // טיפוסי זמן
import java.util.List;                                               // רשימות

@Repository                                                          // ריפו JPA
public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {

    // ==============================
    // 🔵 לפי אובייקט User
    // ==============================

    List<UserPhoto> findByUser(User user);                           // כל התמונות (כולל מחוקות)
    List<UserPhoto> findByUserAndDeletedFalse(User user);            // כל התמונות הפעילות

    UserPhoto findByUserAndPrimaryPhotoTrueAndDeletedFalse(User user);   // תמונה ראשית פעילה
    boolean existsByUserAndPrimaryPhotoTrueAndDeletedFalse(User user);   // האם יש תמונה ראשית

    long countByUserAndDeletedFalse(User user);                      // ספירת תמונות פעילות

    List<UserPhoto> findByUserAndDeletedFalseOrderByPositionIndexAsc(
            User user
    );                                                                // גלריה מסודרת לפי position

    UserPhoto findByUserAndPositionIndexAndDeletedFalse(
            User user, Integer positionIndex
    );                                                                // תמונה פעילה במיקום מסוים

    UserPhoto findFirstByUserAndDeletedFalseOrderByCreatedAtAsc(
            User user
    );                                                                // התמונה הפעילה הוותיקה ביותר


    // ==============================
    // 🔵 לפי userId — ל־Service/Controller
    // ==============================

    List<UserPhoto> findByUserIdAndDeletedFalse(Long userId);        // גלריה פעילה לפי userId

    UserPhoto findByUserIdAndPrimaryPhotoTrueAndDeletedFalse(Long userId);   // תמונה ראשית לפי userId

    long countByUserIdAndDeletedFalse(Long userId);                  // ספירה לפי userId

    List<UserPhoto> findByUserIdAndDeletedFalseOrderByPositionIndexAsc(
            Long userId
    );                                                                // גלריה מסודרת לפי userId


    // ==============================
    // 🔵 ערכים חסרים / פונקציות השלמה
    // ==============================

    List<UserPhoto> findByUserAndPrimaryPhotoFalseAndDeletedFalse(
            User user
    );                                                                // כל התמונות הפעילות *שאינן ראשיות* (מועיל בהחלפה)

    List<UserPhoto> findByUserIdAndPrimaryPhotoFalseAndDeletedFalse(
            Long userId
    );                                                                // גרסת userId

    List<UserPhoto> findByUserAndDeletedFalseAndPositionIndexIsNotNull(
            User user
    );                                                                // כל מיקומים קיימים — למציאת position פנוי


    // ==============================
    // 🔵 מחיקה / תחזוקה
    // ==============================

    List<UserPhoto> findByDeletedTrue();                             // כל המחוקות לוגית

    List<UserPhoto> findByCreatedAtAfterAndDeletedFalse(
            LocalDateTime time
    );                                                                // תמונות פעילות אחרי זמן מסוים

    void deleteByUser(User user);                                    // מחיקה פיזית (משמש רק לאיפוס משתמש)
}