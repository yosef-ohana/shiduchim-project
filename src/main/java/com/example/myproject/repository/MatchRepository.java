package com.example.myproject.repository;

import com.example.myproject.model.Match;
import com.example.myproject.model.enums.MatchStatus;
import com.example.myproject.model.enums.MatchSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    // ============================================================
    // 🔵 1. בדיקות בסיסיות (קיום התאמה / שליפת התאמה בין 2 משתמשים)
    // ============================================================

    // מציאת Match בין שני משתמשים, בלי תלות בסדר (user1/user2)
    Optional<Match> findByUser1_IdAndUser2_Id(Long user1Id, Long user2Id);
    Optional<Match> findByUser2_IdAndUser1_Id(Long user1Id, Long user2Id);

    // האם קיימת התאמה בין 2 משתמשים?
    boolean existsByUser1_IdAndUser2_Id(Long user1Id, Long user2Id);
    boolean existsByUser2_IdAndUser1_Id(Long user1Id, Long user2Id);

    // שליפת match יחיד ללא תלות בסדר
    default Optional<Match> findMatchBetween(Long u1, Long u2) {
        Optional<Match> m1 = findByUser1_IdAndUser2_Id(u1, u2);
        if (m1.isPresent()) return m1;
        return findByUser2_IdAndUser1_Id(u1, u2);
    }


    // ============================================================
    // 🔵 2. שליפת התאמות למשתמש מסוים
    // ============================================================

    // כל ההתאמות של משתמש (ללא פילטור סטטוס)
    List<Match> findByUser1_IdOrUser2_Id(Long userId1, Long userId2);

    // התאמות פעילות בלבד (לפי סטטוס, לכל אחד מהמשתמשים בנפרד)
    List<Match> findByStatusAndUser1_Id(MatchStatus status, Long userId);
    List<Match> findByStatusAndUser2_Id(MatchStatus status, Long userId);

    // כל ה־MATCHES שהמשתמש מעורב בהם (גם חסומים / ארכיון)
    List<Match> findByUser1_Id(Long userId);
    List<Match> findByUser2_Id(Long userId);


    // ============================================================
    // 🔵 3. שאילתות הדדיות ואישורים
    // ============================================================

    // התאמות הדדיות בלבד (mutualApproved=true) למשתמש, מכל הסטטוסים
    List<Match> findByMutualApprovedTrueAndUser1_Id(Long userId);
    List<Match> findByMutualApprovedTrueAndUser2_Id(Long userId);

    // התאמות הדדיות בלבד (mutualApproved=true) + סטטוס מסוים (ACTIVE למשל)
    List<Match> findByMutualApprovedTrueAndStatusAndUser1_Id(MatchStatus status, Long userId);
    List<Match> findByMutualApprovedTrueAndStatusAndUser2_Id(MatchStatus status, Long userId);

    // מי שהשתיים כבר אישרו (מאשר החלפת סטטוס ל-ACTIVE)
    List<Match> findByStatusAndMutualApprovedTrue(MatchStatus status);


    // ============================================================
    // 🔵 4. התאמות לפי Wedding Context (origin / meeting)
    // ============================================================

    // התאמות שנוצרו בחלון של חתונה מסוימת (origin wedding)
    List<Match> findByOriginWeddingId(Long weddingId);

    // התאמות עדכניות הקשורות לחתונה (meeting wedding)
    List<Match> findByMeetingWeddingId(Long weddingId);

    // התאמות חיות בתוך חתונה (מצב LIVE)
    List<Match> findByMeetingWeddingIdAndStatus(Long weddingId, MatchStatus status);

    // התאמות שנוצרו בזמן החתונה ("Match בזמן חתונה חיה")
    List<Match> findBySourceAndMeetingWeddingId(
            MatchSourceType sourceType,
            Long weddingId
    );

    // ✅ השלמות: התאמות של משתמש מסוים בחתונה מסוימת (למסכים "ההתאמות שלי בחתונה X")
    List<Match> findByUser1_IdAndMeetingWeddingId(Long userId, Long weddingId);
    List<Match> findByUser2_IdAndMeetingWeddingId(Long userId, Long weddingId);

    // ✅ התאמות של משתמש שנוצרו בהקשר origin של חתונה מסוימת
    List<Match> findByUser1_IdAndOriginWeddingId(Long userId, Long weddingId);
    List<Match> findByUser2_IdAndOriginWeddingId(Long userId, Long weddingId);


    // ============================================================
    // 🔵 5. התאמות חסומות / מוקפאות / בארכיון
    // ============================================================

    // חסומים
    List<Match> findByBlockedByUser1TrueOrBlockedByUser2True();

    // מוקפאים
    List<Match> findByFrozenByUser1TrueOrFrozenByUser2True();

    // בארכיון
    List<Match> findByArchivedTrue();
    List<Match> findByArchivedFalse();

    // התאמות שנמחקו לוגית
    List<Match> findByDeletedTrue();


    // ============================================================
    // 🔵 6. סינון לפי סטטוס מלא
    // ============================================================

    List<Match> findByStatus(MatchStatus status);


    // ============================================================
    // 🔵 7. מבוסס תאריכים — לסטטיסטיקות ולמיון
    // ============================================================

    List<Match> findByCreatedAtAfter(LocalDateTime time);
    List<Match> findByUpdatedAtAfter(LocalDateTime time);

    // מיון לצ'אט — לפי זמן הודעה אחרונה
    List<Match> findByUser1_IdOrUser2_IdOrderByLastMessageAtDesc(Long userId1, Long userId2);

    // התאמות שתאריך ההודעה האחרונה שלהן בטווח
    List<Match> findByLastMessageAtBetween(LocalDateTime start, LocalDateTime end);

    // ✅ התאמות של חתונה מסוימת שנוצרו בטווח זמן (לתמיכה בהתראות "Match בחתונה חיה")
    List<Match> findByMeetingWeddingIdAndCreatedAtBetween(
            Long weddingId,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 8. תמיכה לצ'אט / unread count
    // ============================================================

    // התאמות שיש אצלם הודעות שלא נקראו
    List<Match> findByUser1_IdAndUnreadCountGreaterThan(Long userId, int count);
    List<Match> findByUser2_IdAndUnreadCountGreaterThan(Long userId, int count);


    // ============================================================
    // 🔵 9. Matching Engine / Recommended Matches
    // ============================================================
    // ❗ הריפו משמש כתמיכה בלבד – החישוב האמיתי נעשה ב-Service ו-SystemRules.
    // ❗ כאן בעיקר חשוב למנוע כפילויות.

    boolean existsByUser1_IdAndUser2_IdAndDeletedFalse(Long u1, Long u2);
    boolean existsByUser2_IdAndUser1_IdAndDeletedFalse(Long u1, Long u2);

    // לבדוק אם כבר קיים Match פעיל (מנע הצעה כפולה במנוע התאמות)
    boolean existsByUser1_IdAndUser2_IdAndStatus(Long user1Id, Long user2Id, MatchStatus status);
    boolean existsByUser2_IdAndUser1_IdAndStatus(Long user1Id, Long user2Id, MatchStatus status);


    // ============================================================
    // 🔵 10. סטטיסטיקות מתקדמות לאדמין / בעל אירוע
    // ============================================================

    long countByMeetingWeddingId(Long weddingId);
    long countByOriginWeddingId(Long weddingId);

    long countByStatus(MatchStatus status);

    long countByBlockedByUser1TrueOrBlockedByUser2True();
    long countByFrozenByUser1TrueOrFrozenByUser2True();
    long countByArchivedTrue();

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByLastMessageAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 11. תמיכה מלאה ב-UserAction ו-SystemRules
    // ============================================================

    // כל ההתאמות לפי סטטוסים (למשל ACTIVE + FROZEN) – בלי קשר למשתמש
    List<Match> findByStatusIn(List<MatchStatus> statuses);

    // כל המץ' שנוגעים במשתמש מסוים ופעילים (כמה סטטוסים יחד)
    List<Match> findByUser1_IdAndStatusIn(Long userId, List<MatchStatus> statuses);
    List<Match> findByUser2_IdAndStatusIn(Long userId, List<MatchStatus> statuses);


    // ============================================================
    // 🔵 12. Clean Query — לניקוי התאמות ישנות
    // ============================================================

    // התאמות בארכיון מעבר לזמן מסוים
    List<Match> findByArchivedTrueAndArchivedAtBefore(LocalDateTime time);

    // התאמות שנמחקו לפני זמן מסוים
    List<Match> findByDeletedTrueAndDeletedAtBefore(LocalDateTime time);


    // ============================================================
    // 🔵 13. תמיכה מלאה בהתראות MatchService / NotificationService
    // ============================================================

    // התאמות שנוצרו עכשיו (לשיגור התראות)
    List<Match> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // התאמות שהפכו עכשיו להדדיות (mutualApproved)
    List<Match> findByMutualApprovedTrueAndUpdatedAtAfter(LocalDateTime time);
}