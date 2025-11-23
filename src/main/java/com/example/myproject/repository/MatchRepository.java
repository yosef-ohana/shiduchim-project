package com.example.myproject.repository;

import com.example.myproject.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    // ============================================================
    // 🔵 שליפות בסיסיות לפי שני משתמשים (נורמליזציה דו־כיוונית)
    // ============================================================

    Optional<Match> findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
            Long user1, Long user2,
            Long user2b, Long user1b
    );

    boolean existsByUser1IdAndUser2IdOrUser1IdAndUser2Id(
            Long user1, Long user2,
            Long user2b, Long user1b
    );

    // ============================================================
    // 🔵 שליפות לפי משתמש בודד
    // ============================================================

    List<Match> findByUser1IdAndActiveTrue(Long userId);
    List<Match> findByUser2IdAndActiveTrue(Long userId);

    List<Match> findByUser1IdOrUser2Id(Long userId1, Long userId2);

    // ============================================================
    // 🔵 סטטוסים (Active / Blocked / Frozen / Chat)
    // ============================================================

    List<Match> findByActiveTrue();
    List<Match> findByActiveFalse();

    List<Match> findByBlockedTrue();
    List<Match> findByFrozenTrue();

    List<Match> findByChatOpenedTrue();

    // ============================================================
    // 🔵 Match Source (wedding / global / admin / ai)
    // ============================================================

    List<Match> findByMatchSource(String source);

    List<Match> findByMatchSourceAndActiveTrue(String source);

    List<Match> findByMatchSourceAndMutualApprovedTrueAndActiveTrue(String source);

    // ============================================================
    // 🔵 ציון התאמה (matchScore)
    // ============================================================

    List<Match> findByMatchScoreGreaterThanEqual(double score);

    List<Match> findByMatchScore(double score);

    // ============================================================
    // 🔵 חתונה — סטטיסטיקות חתונה (דרוש ל-WeddingService)
    // ============================================================

    /** כל המצ'ים שהתבצעו בתוך חתונה */
    List<Match> findByMeetingWeddingId(Long weddingId);

    /** 🔥 נוספו מחדש – חובה לסטטיסטיקות חתונה */
    long countByMeetingWeddingId(Long weddingId);

    long countByMeetingWeddingIdAndMutualApprovedTrue(Long weddingId);

    // ============================================================
    // 🔵 התאמות הדדיות לפי משתמש
    // ============================================================

    List<Match> findByMutualApprovedTrueAndActiveTrueAndUser1IdOrMutualApprovedTrueAndActiveTrueAndUser2Id(
            Long user1, Long user2
    );

    List<Match> findByMutualApprovedTrue();

    /**
     * כל המַצ'ים שבהם:
     *  (user1 = userId1 AND user2Approved = true)
     *   OR
     *  (user2 = userId1 AND user1Approved = true)
     *
     * משמש ב-UserService כדי לבדוק מי אישר אותי / את מי אישרתי.
     */
    List<Match> findByUser1IdAndUser2ApprovedTrueOrUser2IdAndUser1ApprovedTrue(
            Long userId1,
            Long userId2
    );
}