package com.example.myproject.repository;

import com.example.myproject.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    // ============================================================
    // 🔵 שליפות בסיסיות לפי שני המשתמשים
    // ============================================================

    List<Match> findByUser1Id(Long userId);
    List<Match> findByUser2Id(Long userId);

    List<Match> findByUser1IdOrUser2Id(Long user1Id, Long user2Id);

    Optional<Match> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    Optional<Match> findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
            Long user1Id,
            Long user2Id,
            Long reversedUser1,
            Long reversedUser2
    );

    boolean existsByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    boolean existsByUser1IdAndUser2IdOrUser1IdAndUser2Id(
            Long user1Id, Long user2Id,
            Long reversedUser1, Long reversedUser2
    );

    // ============================================================
    // 🔵 Active / Blocked / Frozen / Chat
    // ============================================================

    List<Match> findByActiveTrue();
    List<Match> findByActiveFalse();

    long countByActiveTrue();
    long countByActiveFalse();

    List<Match> findByBlockedTrue();
    List<Match> findByFrozenTrue();
    List<Match> findByChatOpenedTrue();

    // ============================================================
    // 🔵 אישורים / התאמות הדדיות
    // ============================================================

    List<Match> findByUser1ApprovedTrueOrUser2ApprovedTrue();

    List<Match> findByMutualApprovedTrue();
    List<Match> findByMutualApprovedTrueAndActiveTrue();

    List<Match>
    findByUser1IdAndUser2ApprovedTrueOrUser2IdAndUser1ApprovedTrue(
            Long user1Id,
            Long user2Id
    );

    List<Match>
    findByMutualApprovedTrueAndActiveTrueAndUser1IdOrMutualApprovedTrueAndActiveTrueAndUser2Id(
            Long user1Id,
            Long user2Id
    );

    // ============================================================
    // 🔵 לפי משתמש + Active
    // ============================================================

    List<Match> findByUser1IdAndActiveTrue(Long userId);
    List<Match> findByUser2IdAndActiveTrue(Long userId);

    // ============================================================
    // 🔵 לפי חתונה (meetingWeddingId)
    // ============================================================

    List<Match> findByMeetingWeddingId(Long weddingId);
    List<Match> findByMeetingWeddingIdAndActiveTrue(Long weddingId);

    List<Match> findByMeetingWeddingIdAndChatOpenedTrue(Long weddingId);
    List<Match> findByMeetingWeddingIdAndBlockedTrue(Long weddingId);
    List<Match> findByMeetingWeddingIdAndFrozenTrue(Long weddingId);

    long countByMeetingWeddingId(Long weddingId);
    long countByMeetingWeddingIdAndMutualApprovedTrue(Long weddingId);
    long countByMeetingWeddingIdAndActiveTrue(Long weddingId);
    long countByMeetingWeddingIdAndChatOpenedTrue(Long weddingId);

    // ============================================================
    // 🔵 ניקוד התאמה
    // ============================================================

    List<Match> findByMatchScoreGreaterThanEqual(Double minScore);
    List<Match> findByMatchScore(Double score);

    // ============================================================
    // 🔵 לפי מקור התאמה
    // ============================================================

    List<Match> findByMatchSource(String source);
    List<Match> findByMatchSourceAndActiveTrue(String source);
    List<Match> findByMatchSourceAndMutualApprovedTrueAndActiveTrue(String source);

    // ============================================================
    // 🔵 לפי הודעות / unreadCount / זמן הודעה אחרונה
    // ============================================================

    List<Match> findByUnreadCountGreaterThan(Integer minUnread);

    List<Match> findByLastMessageAtIsNotNullOrderByLastMessageAtDesc();

    List<Match> findByLastMessageAtAfterOrderByLastMessageAtDesc(java.time.LocalDateTime time);

    // ============================================================
    // 🔵 לפי open-message states
    // ============================================================

    List<Match> findByFirstMessageSentTrue();
    List<Match> findByFirstMessageSentFalse();

    // ============================================================
    // 🔵 לפי "מי עוד לא קרא" – לתמיכה ב־NotificationService החדש
    // ============================================================

    List<Match> findByReadByUser1False();
    List<Match> findByReadByUser2False();

    List<Match> findByInvolvesUser1AndReadByUser2False(Long user1Id);
    List<Match> findByInvolvesUser2AndReadByUser1False(Long user2Id);

}