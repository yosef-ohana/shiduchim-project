package com.example.myproject.repository;                    // חבילה של הריפוזיטורי

import com.example.myproject.model.Wedding;                  // ייבוא ישות Wedding
import org.springframework.data.jpa.repository.JpaRepository; // בסיס ריפו של Spring Data JPA
import org.springframework.stereotype.Repository;            // מציין שזה Bean של ריפו

import java.time.LocalDateTime;                              // טיפוס זמן ותאריך
import java.util.List;                                       // רשימת תוצאות
import java.util.Optional;                                   // עטיפת תוצאה בודדת (עשוי לא להיות)

@Repository                                                  // ריפוזיטורי לניהול טבלת weddings
public interface WeddingRepository extends JpaRepository<Wedding, Long> { // CRUD + קוואריז מותאמים

    // ===============================
    // 🔵 בדיקת ייחודיות / שליפה בסיסית
    // ===============================

    boolean existsByName(String name);                       // האם קיימת חתונה בשם מסוים (למניעת כפילויות)

    Optional<Wedding> findById(Long id);                     // שליפה לפי מזהה (סטנדרטי, אבל משאירים למפורש)

    // ===============================
    // 🔵 סטטוס כללי של חתונות (פעיל / לא פעיל)
    // ===============================

    List<Wedding> findByActiveTrue();                        // כל החתונות הפעילות (active = true)

    List<Wedding> findByActiveFalse();                       // כל החתונות שאינן פעילות (active = false)


    // ===============================
    // 🔵 חתונות לפי טווחי תאריכים
    // ===============================

    List<Wedding> findByStartTimeBetween(                    // חתונות שהתחלתן בין שני זמנים
                                                             LocalDateTime start,                             // התחלה של הטווח
                                                             LocalDateTime end                                // סוף הטווח
    );

    List<Wedding> findByEndTimeBetween(                      // חתונות שהסופן בין שני זמנים
                                                             LocalDateTime start,                             // התחלה של הטווח
                                                             LocalDateTime end                                // סוף הטווח
    );


    // ===============================
    // 🔵 חתונות לפי בעל האירוע (ownerUserId)
    // ===============================

    List<Wedding> findByOwnerUserId(Long ownerUserId);       // כל החתונות של בעל אירוע מסוים

    List<Wedding> findByOwnerUserIdAndActiveTrue(            // חתונות פעילות של בעל אירוע מסוים
                                                             Long ownerUserId                                 // מזהה בעל האירוע
    );


    // ===============================
    // 🔵 רקעי תמונה / וידאו
    // ===============================

    List<Wedding> findByBackgroundImageUrlIsNotNull();       // חתונות שיש להן תמונת רקע מותאמת

    List<Wedding> findByBackgroundVideoUrlIsNotNull();       // חתונות שיש להן וידאו רקע מותאם


    // ===============================
    // 🔵 חתונות לפי זמן יצירה / מצב "חי"
    // ===============================

    List<Wedding> findByCreatedAtAfter(                      // חתונות שנוצרו אחרי זמן מסוים
                                                             LocalDateTime time                               // זמן סף
    );

    List<Wedding> findByStartTimeBeforeAndEndTimeAfter(      // חתונות "חי" עכשיו (בתוך טווח האירוע)
                                                             LocalDateTime now1,                              // זמן נוכחי (להשוואה ל-startTime)
                                                             LocalDateTime now2                               // זמן נוכחי (להשוואה ל-endTime)
    );


    // ===============================
    // 🔵 שימושים ל-WeddingService
    // ===============================

    Optional<Wedding> findByIdAndActiveTrue(Long id);        // שליפה של חתונה לפי ID רק אם היא פעילה

    List<Wedding> findByEndTimeBefore(LocalDateTime time);   // חתונות שכבר הסתיימו לפני זמן מסוים

    List<Wedding> findByStartTimeAfter(LocalDateTime time);  // חתונות שעדיין לא התחילו (עתידיות)
}