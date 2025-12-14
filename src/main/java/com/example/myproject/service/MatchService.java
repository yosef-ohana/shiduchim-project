package com.example.myproject.service;

import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import com.example.myproject.model.enums.MatchSourceType;
import com.example.myproject.model.enums.MatchStatus;
import com.example.myproject.repository.MatchRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.myproject.dto.MatchActionFeedback;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.WeddingMode;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MatchService {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    public MatchService(MatchRepository matchRepository,
                        UserRepository userRepository) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
    }

    // ============================================================
    // 🔵 פעימה 1 — CRUD בסיסי + שליפות בסיסיות
    // ============================================================

    // ------------------------------
    // 🔹 CRUD בסיסי
    // ------------------------------

    /**
     * יצירת / עדכון Match קיים.
     * (לוגיקת יצירה "חכמה" לפי UserAction ו־SystemRules תהיה בפעימה 3.)
     */
    public Match save(Match match) {
        return matchRepository.save(match);
    }

    /**
     * שליפת Match לפי מזהה.
     */
    @Transactional(readOnly = true)
    public Match getById(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }

    /**
     * שליפת כל ההתאמות במערכת (לשימוש אדמין / דשבורדים).
     */
    @Transactional(readOnly = true)
    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    /**
     * מחיקה לוגית של Match (deleted=true + deletedAt),
     * בלי למחוק מה־DB בפועל.
     */

    /**
     * מחיקה פיזית — לשימוש Admin / ניקוי קיצוני בלבד.
     */
    public void hardDeleteMatch(Long matchId) {
        matchRepository.deleteById(matchId);
    }

    // ------------------------------
    // 🔹 שליפות בסיסיות לפי משתמש
    // ------------------------------

    /**
     * כל ההתאמות שהמשתמש חלק מהן (ללא סינון סטטוס).
     * כולל ACTIVE, FROZEN, BLOCKED, ARCHIVED, DELETED=false.
     */
    @Transactional(readOnly = true)
    public List<Match> getAllUserMatches(Long userId) {
        return matchRepository.findByUser1_IdOrUser2_Id(userId, userId)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות לפי סטטוס מסוים (ACTIVE/FROZEN/BLOCKED/ARCHIVED)
     * עבור משתמש.
     */
    @Transactional(readOnly = true)
    public List<Match> getUserMatchesByStatus(Long userId, MatchStatus status) {
        List<Match> asUser1 = matchRepository.findByStatusAndUser1_Id(status, userId);
        List<Match> asUser2 = matchRepository.findByStatusAndUser2_Id(status, userId);

        return concatAndFilterDeleted(asUser1, asUser2);
    }

    /**
     * כל ההתאמות ההדדיות (mutualApproved=true) של המשתמש,
     * ללא תלות בסטטוס (משמש להיסטוריה / דוחות).
     */
    @Transactional(readOnly = true)
    public List<Match> getUserMutualMatches(Long userId) {
        List<Match> asUser1 = matchRepository.findByMutualApprovedTrueAndUser1_Id(userId);
        List<Match> asUser2 = matchRepository.findByMutualApprovedTrueAndUser2_Id(userId);

        return concatAndFilterDeleted(asUser1, asUser2);
    }

    /**
     * התאמות הדדיות *פעילות* בלבד (mutualApproved + status=ACTIVE).
     * זה המסך הרגיל של "ההתאמות שלי".
     */
    @Transactional(readOnly = true)
    public List<Match> getUserActiveMutualMatches(Long userId) {
        List<Match> asUser1 =
                matchRepository.findByMutualApprovedTrueAndStatusAndUser1_Id(MatchStatus.ACTIVE, userId);
        List<Match> asUser2 =
                matchRepository.findByMutualApprovedTrueAndStatusAndUser2_Id(MatchStatus.ACTIVE, userId);

        return concatAndFilterDeleted(asUser1, asUser2);
    }

    /**
     * כל ההתאמות של המשתמש, ממויינות לפי זמן הודעה אחרונה בצ'אט (למסך "צ'אטים").
     */
    @Transactional(readOnly = true)
    public List<Match> getUserMatchesSortedByLastMessage(Long userId) {
        return matchRepository
                .findByUser1_IdOrUser2_IdOrderByLastMessageAtDesc(userId, userId)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    // ------------------------------
    // 🔹 בדיקת קיום התאמה בין שני משתמשים
    // ------------------------------

    /**
     * בדיקה האם קיימת התאמה (מכל סוג סטטוס) בין שני משתמשים.
     * מסייע למנוע יצירת התאמה כפולה במנוע התאמות.
     */
    @Transactional(readOnly = true)
    public boolean matchExistsBetween(Long userId1, Long userId2) {
        return matchRepository.existsByUser1_IdAndUser2_Id(userId1, userId2)
                || matchRepository.existsByUser2_IdAndUser1_Id(userId1, userId2);
    }

    /**
     * בדיקה האם קיימת התאמה פעילה (ACTIVE) בין שני משתמשים.
     * משמשת למנוע הצגה כפולה באלגוריתם ההמלצות.
     */
    @Transactional(readOnly = true)
    public boolean activeMatchExistsBetween(Long userId1, Long userId2) {
        return matchRepository.existsByUser1_IdAndUser2_IdAndStatus(userId1, userId2, MatchStatus.ACTIVE)
                || matchRepository.existsByUser2_IdAndUser1_IdAndStatus(userId1, userId2, MatchStatus.ACTIVE);
    }

    /**
     * שליפה בטוחה של Match יחיד בין שני משתמשים, בלי תלות בסדר (user1/user2).
     */
    @Transactional(readOnly = true)
    public Match getMatchBetweenUsersOrNull(Long userId1, Long userId2) {
        return matchRepository.findMatchBetween(userId1, userId2).orElse(null);
    }

    // ------------------------------
    // 🔹 שליפות בסיסיות לפי חתונה
    // ------------------------------

    /**
     * כל ההתאמות שנוצרו / משויכות לחתונה מסוימת (meetingWeddingId),
     * ממויינות מהחדשות לישנות לפי createdAt.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesByWedding(Long weddingId) {
        return matchRepository.findByMeetingWeddingId(weddingId)
                .stream()
                .filter(m -> !m.isDeleted())
                .sorted(Comparator.comparing(Match::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות שמקורן בחתונה מסוימת (originWeddingId),
     * לצורך תווית "הכרתם בחתונה X" וסטטיסטיקות.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesByOriginWedding(Long weddingId) {
        return matchRepository.findByOriginWeddingId(weddingId)
                .stream()
                .filter(m -> !m.isDeleted())
                .sorted(Comparator.comparing(Match::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // ============================================================
    // 🔵 עזר פנימי – חיבור רשימות וסינון מחוקים
    // ============================================================

    private List<Match> concatAndFilterDeleted(List<Match> list1, List<Match> list2) {
        return java.util.stream.Stream.concat(
                        list1 != null ? list1.stream() : java.util.stream.Stream.empty(),
                        list2 != null ? list2.stream() : java.util.stream.Stream.empty()
                )
                .filter(m -> !m.isDeleted())
                .distinct()
                .collect(Collectors.toList());
    }

// ============================================================
    // 🔵 פעימה 2 — שליפות מתקדמות, סטטיסטיקות, ניקוי
    // ============================================================

    // ------------------------------
    // 🔹 סטטוסים מורכבים / רשימות סטטוסים
    // ------------------------------

    /**
     * כל ההתאמות של המשתמש לפי רשימת סטטוסים (למשל ACTIVE + FROZEN).
     * מכסה את היכולת: "התאמות לפי Multi-Status" (סעיף 27).
     */
    @Transactional(readOnly = true)
    public List<Match> getUserMatchesByStatuses(Long userId, List<MatchStatus> statuses) {
        List<Match> asUser1 = matchRepository.findByUser1_IdAndStatusIn(userId, statuses);
        List<Match> asUser2 = matchRepository.findByUser2_IdAndStatusIn(userId, statuses);
        return concatAndFilterDeleted(asUser1, asUser2);
    }

    /**
     * כל ההתאמות במערכת לפי סטטוס יחיד (לשימוש Admin / SystemRules).
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesByStatus(MatchStatus status) {
        return matchRepository.findByStatus(status)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות במערכת לפי כמה סטטוסים ביחד.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesByStatuses(List<MatchStatus> statuses) {
        return matchRepository.findByStatusIn(statuses)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    // ------------------------------
    // 🔹 התאמות לפי חתונה + משתמש (Wedding Context)
    // ------------------------------

    /**
     * כל ההתאמות של משתמש מסוים בתוך חתונה מסוימת (meetingWeddingId).
     * מסך: "ההתאמות שלי בחתונה X".
     */
    @Transactional(readOnly = true)
    public List<Match> getUserMatchesInWedding(Long userId, Long weddingId) {
        List<Match> asUser1 = matchRepository.findByUser1_IdAndMeetingWeddingId(userId, weddingId);
        List<Match> asUser2 = matchRepository.findByUser2_IdAndMeetingWeddingId(userId, weddingId);
        return concatAndFilterDeleted(asUser1, asUser2);
    }

    /**
     * כל ההתאמות של משתמש שמקורן בחתונה מסוימת (originWeddingId).
     * משמש לתווית: "הכרתם בחתונה X".
     */
    @Transactional(readOnly = true)
    public List<Match> getUserMatchesByOriginWedding(Long userId, Long originWeddingId) {
        List<Match> asUser1 = matchRepository.findByUser1_IdAndOriginWeddingId(userId, originWeddingId);
        List<Match> asUser2 = matchRepository.findByUser2_IdAndOriginWeddingId(userId, originWeddingId);
        return concatAndFilterDeleted(asUser1, asUser2);
    }

    // ------------------------------
    // 🔹 LIVE Wedding / חלון זמן חי
    // ------------------------------

    /**
     * כל ההתאמות בחתונה מסוימת שנוצרו בטווח זמן נתון.
     * זה הבסיס להתראות "יש לכם Match בזמן חתונה חיה".
     */
    @Transactional(readOnly = true)
    public List<Match> getLiveMatchesForWeddingInWindow(Long weddingId,
                                                        LocalDateTime start,
                                                        LocalDateTime end) {
        return matchRepository.findByMeetingWeddingIdAndCreatedAtBetween(weddingId, start, end)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות שנוצרו בטווח זמן מסוים במערכת (לאו דווקא חתונה).
     * משמש לחישוב סטטיסטיקות פר יום / שעה.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesCreatedBetween(LocalDateTime start, LocalDateTime end) {
        return matchRepository.findByCreatedAtBetween(start, end)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    // ------------------------------
    // 🔹 חסומים / מוקפאים / ארכיון / מחוקים
    // ------------------------------

    /**
     * רשימת כל ההתאמות החסומות במערכת (לשימוש אדמין / דוחות).
     */
    @Transactional(readOnly = true)
    public List<Match> getBlockedMatches() {
        return matchRepository.findByBlockedByUser1TrueOrBlockedByUser2True()
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות המוקפאות במערכת.
     */
    @Transactional(readOnly = true)
    public List<Match> getFrozenMatches() {
        return matchRepository.findByFrozenByUser1TrueOrFrozenByUser2True()
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות בארכיון (archived=true).
     */
    @Transactional(readOnly = true)
    public List<Match> getArchivedMatches() {
        return matchRepository.findByArchivedTrue()
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות המסומנות כ־deleted=true (לשימוש Debug / Admin).
     */
    @Transactional(readOnly = true)
    public List<Match> getDeletedMatches() {
        return matchRepository.findByDeletedTrue();
    }

    /**
     * כל ההתאמות שאינן בארכיון (archived=false).
     */
    @Transactional(readOnly = true)
    public List<Match> getNonArchivedMatches() {
        return matchRepository.findByArchivedFalse()
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    // ------------------------------
    // 🔹 ניקוי מערכת — Archiving & Deletion Jobs
    // ------------------------------

    /**
     * החזרת כל ההתאמות שבארכיון לפני זמן מסוים.
     * משמש ל־Cron Job שמנקה ארכיון ישן.
     */
    @Transactional(readOnly = true)
    public List<Match> getOldArchivedMatches(LocalDateTime before) {
        return matchRepository.findByArchivedTrueAndArchivedAtBefore(before);
    }

    /**
     * החזרת כל ההתאמות שנמחקו לוגית לפני זמן מסוים.
     */
    @Transactional(readOnly = true)
    public List<Match> getOldDeletedMatches(LocalDateTime before) {
        return matchRepository.findByDeletedTrueAndDeletedAtBefore(before);
    }

    /**
     * Job אופציונלי: ניקוי פיזי של התאמות שנמצאות בארכיון מעבר לחלון מוגדר.
     * (הקוד כאן רק מחזיר רשימה — ההחלטה אם delete() תהיה בשכבת System / Admin.)
     */
    public void hardDeleteOldDeletedMatches(LocalDateTime before) {
        List<Match> oldDeleted = getOldDeletedMatches(before);
        oldDeleted.forEach(m -> matchRepository.deleteById(m.getId()));
    }

    // ------------------------------
    // 🔹 Time-based Queries — Created / Updated / Chat Activity
    // ------------------------------

    /**
     * כל ההתאמות שנוצרו אחרי זמן מסוים.
     * משמש לדוחות "Match יומיים/שבועיים".
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesCreatedAfter(LocalDateTime since) {
        return matchRepository.findByCreatedAtAfter(since)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות שעודכנו אחרי זמן מסוים.
     * כולל שינוי סטטוס, חסימה, הקפאה, הודעות וכו'.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesUpdatedAfter(LocalDateTime since) {
        return matchRepository.findByUpdatedAtAfter(since)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    /**
     * כל ההתאמות עם הודעות אחרונות בטווח זמן מסוים.
     * משמש לדשבורדים של פעילות צ'אט.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesWithLastMessageBetween(LocalDateTime start, LocalDateTime end) {
        return matchRepository.findByLastMessageAtBetween(start, end)
                .stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toList());
    }

    // ------------------------------
    // 🔹 Unread / הודעות שלא נקראו
    // ------------------------------

    /**
     * כל ההתאמות שיש בהן הודעות שלא נקראו ע"י המשתמש.
     * (מבוצע בשני צדדים — user1/user2 — כדי לקבל תמונה מלאה.)
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesWithUnreadMessagesForUser(Long userId) {
        List<Match> asUser1 = matchRepository.findByUser1_IdAndUnreadCountGreaterThan(userId, 0);
        List<Match> asUser2 = matchRepository.findByUser2_IdAndUnreadCountGreaterThan(userId, 0);

        return concatAndFilterDeleted(asUser1, asUser2)
                .stream()
                .sorted(Comparator.comparing(Match::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * סימון שכל ההודעות במץ' מסוים נקראו ע"י משתמש (userId).
     * (הפחתת unreadCount, עדכון readByUser1/readByUser2.)
     */
    public void markMatchAsReadForUser(Long matchId, Long userId) {
        Match match = getById(matchId);
        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + matchId);
        }

        // התאמה לניהול Simplified: unreadCount ברמת Match
        match.setUnreadCount(0);

        if (match.getUser1() != null && match.getUser1().getId().equals(userId)) {
            match.setReadByUser1(true);
        }
        if (match.getUser2() != null && match.getUser2().getId().equals(userId)) {
            match.setReadByUser2(true);
        }

        matchRepository.save(match);
    }

    // ------------------------------
    // 🔹 מניעת כפילויות — Non-Deleted / לפי סטטוס
    // ------------------------------

    /**
     * בדיקה האם קיימת התאמה (לא-מחוקה) בין שני משתמשים.
     * מכסה את יכולת: "מנוע התאמות לא מציע מישהו שכבר יש איתו Match כלשהו".
     */
    @Transactional(readOnly = true)
    public boolean nonDeletedMatchExistsBetween(Long userId1, Long userId2) {
        return matchRepository.existsByUser1_IdAndUser2_IdAndDeletedFalse(userId1, userId2)
                || matchRepository.existsByUser2_IdAndUser1_IdAndDeletedFalse(userId1, userId2);
    }

    // ------------------------------
    // 🔹 סטטיסטיקות — Global / Wedding
    // ------------------------------

    /**
     * DTO פנימי לסטטיסטיקות Match — גלובלי או לפי חתונה.
     */
    public static class MatchAnalytics {

        private final long totalMatches;
        private final long activeMatches;
        private final long blockedMatches;
        private final long frozenMatches;
        private final long archivedMatches;
        private final long createdInPeriod;
        private final long chattedInPeriod;

        public MatchAnalytics(long totalMatches,
                              long activeMatches,
                              long blockedMatches,
                              long frozenMatches,
                              long archivedMatches,
                              long createdInPeriod,
                              long chattedInPeriod) {
            this.totalMatches = totalMatches;
            this.activeMatches = activeMatches;
            this.blockedMatches = blockedMatches;
            this.frozenMatches = frozenMatches;
            this.archivedMatches = archivedMatches;
            this.createdInPeriod = createdInPeriod;
            this.chattedInPeriod = chattedInPeriod;
        }

        public long getTotalMatches() { return totalMatches; }
        public long getActiveMatches() { return activeMatches; }
        public long getBlockedMatches() { return blockedMatches; }
        public long getFrozenMatches() { return frozenMatches; }
        public long getArchivedMatches() { return archivedMatches; }
        public long getCreatedInPeriod() { return createdInPeriod; }
        public long getChattedInPeriod() { return chattedInPeriod; }
    }

    /**
     * סטטיסטיקות גלובליות על Match בכל המערכת,
     * עם חלון זמן ליצירה וצ'אט (למשל 24 שעות אחרונות).
     */
    @Transactional(readOnly = true)
    public MatchAnalytics getGlobalMatchAnalytics(LocalDateTime periodStart, LocalDateTime periodEnd) {
        long total = matchRepository.count();
        long active = matchRepository.countByStatus(MatchStatus.ACTIVE);
        long blocked = matchRepository.countByBlockedByUser1TrueOrBlockedByUser2True();
        long frozen = matchRepository.countByFrozenByUser1TrueOrFrozenByUser2True();
        long archived = matchRepository.countByArchivedTrue();
        long createdInPeriod = matchRepository.countByCreatedAtBetween(periodStart, periodEnd);
        long chattedInPeriod = matchRepository.countByLastMessageAtBetween(periodStart, periodEnd);

        return new MatchAnalytics(
                total,
                active,
                blocked,
                frozen,
                archived,
                createdInPeriod,
                chattedInPeriod
        );
    }

    /**
     * סטטיסטיקות Match עבור חתונה מסוימת.
     */
    @Transactional(readOnly = true)
    public MatchAnalytics getWeddingMatchAnalytics(Long weddingId,
                                                   LocalDateTime periodStart,
                                                   LocalDateTime periodEnd) {

        long totalForWedding = matchRepository.countByMeetingWeddingId(weddingId);
        long originForWedding = matchRepository.countByOriginWeddingId(weddingId);

        // נשתמש בסטטיסטיקות גלובליות כבסיס, אבל מוכוונות לחתונה
        long active = matchRepository.findByMeetingWeddingIdAndStatus(weddingId, MatchStatus.ACTIVE)
                .stream()
                .filter(m -> !m.isDeleted())
                .count();

        long blocked = matchRepository.findByMeetingWeddingId(weddingId)
                .stream()
                .filter(m -> !m.isDeleted())
                .filter(m -> m.isBlockedByUser1() || m.isBlockedByUser2())
                .count();

        long frozen = matchRepository.findByMeetingWeddingId(weddingId)
                .stream()
                .filter(m -> !m.isDeleted())
                .filter(m -> m.isFrozenByUser1() || m.isFrozenByUser2())
                .count();

        long archived = matchRepository.findByMeetingWeddingId(weddingId)
                .stream()
                .filter(Match::isArchived)
                .filter(m -> !m.isDeleted())
                .count();

        long createdInPeriod = matchRepository.findByMeetingWeddingIdAndCreatedAtBetween(
                        weddingId, periodStart, periodEnd)
                .stream()
                .filter(m -> !m.isDeleted())
                .count();

        long chattedInPeriod = matchRepository.findByLastMessageAtBetween(periodStart, periodEnd)
                .stream()
                .filter(m -> !m.isDeleted())
                .filter(m -> weddingId.equals(m.getMeetingWeddingId()))
                .count();

        // ה-totalMatches כאן יהיה "כמה התאמות קשורות לחתונה",
        // ואנחנו יכולים להחליט אם זה totalForWedding או originForWedding או סכום שלהם.
        long totalMatches = totalForWedding + originForWedding;

        return new MatchAnalytics(
                totalMatches,
                active,
                blocked,
                frozen,
                archived,
                createdInPeriod,
                chattedInPeriod
        );
    }
// ============================================================
    // 🔵 פעימה 3 — Business Logic מלאה + שינויי מצב
    // ============================================================

    // ------------------------------
    // 🔹 אישורים / הדדיות (user1Approved / user2Approved / mutualApproved)
    // ------------------------------

    /**
     * תוצאת עדכון אישור מץ' עבור משתמש:
     * - match: המצב המעודכן
     * - becameMutualNow: האם כתוצאה מהפעולה הזו המץ' הפך עכשיו להדדי
     */
    public static class MatchApprovalResult {
        private final Match match;
        private final boolean becameMutualNow;

        public MatchApprovalResult(Match match, boolean becameMutualNow) {
            this.match = match;
            this.becameMutualNow = becameMutualNow;
        }

        public Match getMatch() { return match; }
        public boolean isBecameMutualNow() { return becameMutualNow; }
    }

    /**
     * עדכון האישור של משתמש במץ':
     * - אם המשתמש הוא user1 → user1Approved
     * - אם המשתמש הוא user2 → user2Approved
     * - המערכת מעדכנת אוטומטית mutualApproved + status (דרך ה־hooks ב-Entity)
     *
     * מחזיר MatchApprovalResult כדי ששכבות אחרות יידעו:
     * - האם עכשיו נהיה mutualApproved → לשלוח התראות "יש לכם התאמה!"
     */
    public MatchApprovalResult setUserApproval(Long matchId, Long userId, boolean approved) {
        Match match = getById(matchId);

        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + matchId);
        }

        boolean before = match.isMutualApproved();

        if (match.getUser1() != null && match.getUser1().getId().equals(userId)) {
            match.setUser1Approved(approved);
        } else if (match.getUser2() != null && match.getUser2().getId().equals(userId)) {
            match.setUser2Approved(approved);
        }

        Match saved = matchRepository.save(match);
        boolean after = saved.isMutualApproved();
        boolean becameMutual = (!before && after);

        return new MatchApprovalResult(saved, becameMutual);
    }

    /**
     * נוחות: אישור (approve) מפורש למשתמש.
     */
    public MatchApprovalResult approveMatchForUser(Long matchId, Long userId) {
        return setUserApproval(matchId, userId, true);
    }

    /**
     * נוחות: ביטול אישור (unapprove) למשתמש.
     */
    public MatchApprovalResult unapproveMatchForUser(Long matchId, Long userId) {
        return setUserApproval(matchId, userId, false);
    }

    // ------------------------------
    // 🔹 חסימה (Block) / הקפאה (Freeze) / ביטול
    // ------------------------------

    /**
     * חסימת מץ' ע"י משתמש מסוים.
     * - מגדיר blockedByUserX=true
     * - ה-Entity כבר מעדכן סטטוס ל-BLOCKED (recalcStatusFromFlags)
     */
    public Match blockMatch(Long matchId, Long userId, String reason) {
        Match match = getById(matchId);

        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + matchId);
        }

        if (match.getUser1() != null && match.getUser1().getId().equals(userId)) {
            match.setBlockedByUser1(true);
        } else if (match.getUser2() != null && match.getUser2().getId().equals(userId)) {
            match.setBlockedByUser2(true);
        }

        if (reason != null && !reason.isBlank()) {
            match.setFreezeReason(reason);
        }

        return matchRepository.save(match);
    }

    /**
     * ביטול חסימה — ברמת מערכת/אדמין בלבד בד"כ.
     * מחזיר את המץ' לסטטוס בהתאם לדגלים האחרים (frozen/mutual וכו').
     */
    public Match unblockMatch(Long matchId, Long userId) {
        Match match = getById(matchId);

        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + matchId);
        }

        if (match.getUser1() != null && match.getUser1().getId().equals(userId)) {
            match.setBlockedByUser1(false);
        } else if (match.getUser2() != null && match.getUser2().getId().equals(userId)) {
            match.setBlockedByUser2(false);
        }

        return matchRepository.save(match);
    }

    /**
     * הקפאת מץ' (Freeze) – למשל "להקפיא את ההתאמה" בלי למחוק.
     * Net effect: status → FROZEN (דרך recalcStatusFromFlags)
     */
    public Match freezeMatch(Long matchId, Long userId, String reason) {
        Match match = getById(matchId);

        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + matchId);
        }

        if (match.getUser1() != null && match.getUser1().getId().equals(userId)) {
            match.setFrozenByUser1(true);
        } else if (match.getUser2() != null && match.getUser2().getId().equals(userId)) {
            match.setFrozenByUser2(true);
        }

        if (reason != null && !reason.isBlank()) {
            match.setFreezeReason(reason);
        }

        return matchRepository.save(match);
    }

    /**
     * ביטול הקפאה (Unfreeze).
     */
    public Match unfreezeMatch(Long matchId, Long userId) {
        Match match = getById(matchId);

        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + matchId);
        }

        if (match.getUser1() != null && match.getUser1().getId().equals(userId)) {
            match.setFrozenByUser1(false);
        } else if (match.getUser2() != null && match.getUser2().getId().equals(userId)) {
            match.setFrozenByUser2(false);
        }

        return matchRepository.save(match);
    }

    // ------------------------------
    // 🔹 ארכוב / מחיקה לוגית
    // ------------------------------

    /**
     * העברת מץ' לארכיון (archived=true).
     * Note: ה-Entity כבר מעדכן סטטוס ל-ARCHIVED.
     */
    public Match archiveMatch(Long matchId) {
        Match match = getById(matchId);
        match.setArchived(true);
        return matchRepository.save(match);
    }

    /**
     * ביטול ארכוב (למשל ע"י אדמין).
     */
    public Match unarchiveMatch(Long matchId) {
        Match match = getById(matchId);
        match.setArchived(false);
        return matchRepository.save(match);
    }

    /**
     * מחיקה לוגית של מץ' (deleted=true).
     * לא מוחקים פיזית מה-DB – חשוב לשמירת היסטוריה.
     */
    public Match softDeleteMatch(Long matchId) {
        Match match = getById(matchId);
        match.setDeleted(true);
        return matchRepository.save(match);
    }

    // ------------------------------
    // 🔹 צ'אט / Opening Message / unreadCount
    // ------------------------------

    /**
     * קריאה מומלצת ע"י שכבת הצ'אט בכל פעם שנשלחת הודעה חדשה במץ':
     * - מעדכנת lastMessageAt
     * - מגדילה unreadCount (בצורה סימטרית)
     * - מסמנת chatOpened=true
     * - מסמנת מי קרא / מי לא קרא
     *
     * שים לב:
     *   את יצירת ההודעה עצמה (ChatMessage) עושה ChatMessageService.
     *   כאן רק מסנכרנים את מצב המץ'.
     */
    public Match onChatMessageSent(Long matchId, Long senderUserId) {
        Match match = getById(matchId);

        if (!match.involvesUser(senderUserId)) {
            throw new IllegalArgumentException("User " + senderUserId + " is not part of match " + matchId);
        }

        // צ'אט נפתח ברגע שנשלחת הודעה ראשונה
        if (!match.isChatOpened()) {
            match.setChatOpened(true);
        }

        // הודעה ראשונה? נעדכן flag ל-true (Opening Message rule)
        if (!match.isFirstMessageSent()) {
            match.setFirstMessageSent(true);
        }

        // זמן הודעה אחרונה
        match.setLastMessageAt(LocalDateTime.now());

        // unreadCount – באופן פשוט: כל הודעה חדשה מגדילה את המונה.
        Integer unread = match.getUnreadCount();
        if (unread == null) unread = 0;
        unread = unread + 1;
        match.setUnreadCount(unread);

        // מי שלח? הוא קרא את ההודעות; הצד השני עוד לא.
        if (match.getUser1() != null && match.getUser1().getId().equals(senderUserId)) {
            match.setReadByUser1(true);
            match.setReadByUser2(false);
        } else if (match.getUser2() != null && match.getUser2().getId().equals(senderUserId)) {
            match.setReadByUser2(true);
            match.setReadByUser1(false);
        }

        return matchRepository.save(match);
    }

    /**
     * קריאה נוחה לשילוב עם ChatMessageService:
     * - אחרי שנשלחה הודעה ראשונה, להבטיח סימון firstMessageSent + chatOpened.
     */
    public Match markFirstMessageSent(Long matchId, Long senderUserId) {
        Match match = getById(matchId);

        if (!match.involvesUser(senderUserId)) {
            throw new IllegalArgumentException("User " + senderUserId + " is not part of match " + matchId);
        }

        if (!match.isChatOpened()) {
            match.setChatOpened(true);
        }
        if (!match.isFirstMessageSent()) {
            match.setFirstMessageSent(true);
        }

        return matchRepository.save(match);
    }

    // ------------------------------
    // 🔹 חוקים מיוחדים — LIVE Wedding / מקור התאמה
    // ------------------------------

    /**
     * סימון התאמה כמגיעה מחתונה חיה (LIVE Wedding).
     * בפועל, אנו משתמשים ב-MatchSourceType כדי לסמן.
     * (NotificationService ישתמש בזה כדי לשלוח התראת "Match בזמן חתונה".)
     */
    public Match markMatchAsLiveWeddingSource(Long matchId) {
        Match match = getById(matchId);
        match.setSource(MatchSourceType.LIVE_WEDDING);
        return matchRepository.save(match);
    }

    /**
     * עדכון meetingWeddingId ל"קונטקסט נוכחי" (למשל כאשר משתמשים נפגשים שוב בחתונה אחרת).
     * לא משנה originWeddingId – כדי לשמור את ה"היכרות הראשונה".
     */
    public Match updateMeetingWeddingContext(Long matchId, Long currentWeddingId) {
        Match match = getById(matchId);
        match.setMeetingWeddingId(currentWeddingId);
        return matchRepository.save(match);
    }


    /**
     * נקודת כניסה רשמית של שכבת המערכת, כאשר מתרחש שינוי שמשפיע על Match.
     * לדוגמה:
     *  - עדכון פרופיל (Basic → Full)
     *  - כניסה/יציאה מהמאגר הגלובלי
     *  - שינוי במצב חתונה
     *  - חסימה מערכתית
     *
     * בשלב זה המימוש רק מפעיל בדיקות חוקים,
     * כדי לאפשר הרחבה עתידית.
     */
    public void applySystemRulesOnMatch(Match match) {

        if (match == null) return;

        // -------------------------------------------
        // 🔹 חוק 1 — התאמה לא יכולה להיות ACTIVE אם אחד המחוקים
        // -------------------------------------------
        if (match.isDeleted()) {
            match.setStatus(MatchStatus.ARCHIVED);
        }

        // -------------------------------------------
        // 🔹 חוק 2 — התאמה לא יכולה להיות ACTIVE אם אחד חסום
        // -------------------------------------------
        if (match.isBlockedByUser1() || match.isBlockedByUser2()) {
            match.setStatus(MatchStatus.BLOCKED);
        }

        // -------------------------------------------
        // 🔹 חוק 3 — הקפאה → סטטוס FROZEN
        // -------------------------------------------
        if (match.isFrozenByUser1() || match.isFrozenByUser2()) {
            match.setStatus(MatchStatus.FROZEN);
        }

        // -------------------------------------------
        // 🔹 חוק 4 — הדדיות מלאה → ACTIVE, אם אין חסימות/הקפאות/ארכוב
        // -------------------------------------------
        if (match.isMutualApproved()
                && !match.isArchived()
                && !match.isBlockedByUser1()
                && !match.isBlockedByUser2()
                && !match.isFrozenByUser1()
                && !match.isFrozenByUser2()
                && !match.isDeleted()) {

            match.setStatus(MatchStatus.ACTIVE);
        }

        // -------------------------------------------
        // 🔹 חוק 5 — אם chatOpened=false אבל נשלחה הודעה → לפתוח צ'אט
        // -------------------------------------------
        if (match.isFirstMessageSent() && !match.isChatOpened()) {
            match.setChatOpened(true);
        }

        // שמירה לאחר עדכון חוקי מערכת
        matchRepository.save(match);
    }

    /**
     * מופעל כאשר משתנה מצב משתמש המשפיע על התאמות שלו.
     */
    public void onUserStateChangedAffectingMatches(Long userId) {

        List<Match> matches = getAllUserMatches(userId);

        for (Match match : matches) {
            applySystemRulesOnMatch(match);
        }
    }

    /**
     * מופעל כאשר משתנה מצב חתונה — למשל:
     *   - החתונה מסתיימת
     *   - רקע משתנה
     *   - Wedding Mode משתנה
     */
    public void onWeddingStateChangedAffectingMatches(Long weddingId) {

        List<Match> list = getMatchesByWedding(weddingId);

        for (Match match : list) {
            applySystemRulesOnMatch(match);
        }
    }

    // ============================================================
// 🔵 פעימה 4 — SystemRules Integration (חלק 2 מתוך 3)
// ============================================================
// חוקים: Opening Message, Anti-Spam, AfterWeddingRules,
// GlobalPool Integration, Notification Triggers,
// UserStateEvaluator placeholders
// ============================================================


// ============================================================
// 🔹 Opening Message Rule — ניתן לשלוח הודעה ראשונה פעם אחת בלבד
// ============================================================

    public void validateOpeningMessageAllowed(Long matchId, Long senderUserId) {
        Match match = getById(matchId);

        if (!match.involvesUser(senderUserId)) {
            throw new IllegalArgumentException("User " + senderUserId + " is not part of match " + matchId);
        }

        if (match.isFirstMessageSent()) {
            throw new IllegalStateException("Opening message already sent for match " + matchId);
        }
    }


// ============================================================
// 🔹 Anti-Spam Rule — מניעת שליחת הודעות מהירה מדי
// ============================================================

    public void validateNotSpam(Long matchId, int cooldownSeconds) {
        Match match = getById(matchId);

        if (match.getLastMessageAt() == null) return;

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(cooldownSeconds);

        if (match.getLastMessageAt().isAfter(threshold)) {
            throw new IllegalStateException(
                    "Too many messages in a short time — Anti-Spam rule violated."
            );
        }
    }


// ============================================================
// 🔹 After-Wedding Rules — נעילת Match אחרי חתונה
// ============================================================

    public void applyAfterWeddingRulesForUser(Long userId) {

        List<Match> matches = getAllUserMatches(userId);

        for (Match match : matches) {
            if (match.isDeleted() || match.isArchived()) continue;

            boolean mutual = match.isMutualApproved();

            if (mutual) {
                match.setStatus(MatchStatus.ACTIVE);
            } else {
                match.setStatus(MatchStatus.PENDING);
            }

            matchRepository.save(match);
        }
    }


// ============================================================
// 🔹 GlobalPool Integration — שינוי מצב משתמש במאגר הגלובלי
// ============================================================

    public void onUserEnteredGlobalPool(Long userId) {
        List<Match> matches = getAllUserMatches(userId);
        for (Match match : matches) {
            applySystemRulesOnMatch(match);
        }
    }

    public void onUserExitedGlobalPool(Long userId) {
        List<Match> matches = getAllUserMatches(userId);
        for (Match match : matches) {
            applySystemRulesOnMatch(match);
        }
    }


// ============================================================
// 🔹 Notification Triggers — קריאות לשירות ההתראות
// ============================================================
// (שליחה בפועל תיעשה ב-NotificationService בעתיד)

    public void triggerOnBecameMutual(Match match) {
        // notificationService.sendMutualMatchNotification(match);
    }

    public void triggerOnOpeningMessage(Match match, Long senderUserId) {
        // notificationService.sendOpeningMessageNotification(match, senderUserId);
    }

    public void triggerOnBlock(Match match, Long userId) {
        // notificationService.sendBlockNotification(match, userId);
    }

    public void triggerOnFreeze(Match match, Long userId) {
        // notificationService.sendFreezeNotification(match, userId);
    }


// ============================================================
// 🔹 UserStateEvaluator — נקודת חיבור עתידית
// ============================================================

    public void evaluateUserStateImpactOnMatch(Long userId) {
        // לדוגמה עתידית:
        // UserState state = userStateEvaluator.evaluate(userId);
    }



// ============================================================
// 🔵 פעימה 4 — חלק 3 מתוך 3
// ============================================================
// מנוע יצירת Match חדש + מניעת כפילויות + שילוב UserAction
// ============================================================


// ============================================================
// 🔹 יצירת Match חדש — רק אם מותר לפי חוקי מערכת
// ============================================================

    /**
     * יצירת Match חדש בין שני משתמשים לפי חוקים:
     *  - לא קיים Match חי ביניהם
     *  - לא קיימת חסימה
     *  - לא קיימת מניעת פעולה עקב מצב משתמש
     *  - UserAction רשאי לבצע פעולה זו
     */
    public Match createNewMatch(Long user1Id, Long user2Id,
                                Long meetingWeddingId,
                                Long originWeddingId,
                                MatchSourceType source) {

        if (user1Id.equals(user2Id)) {
            throw new IllegalArgumentException("Cannot create match with same user.");
        }

        if (nonDeletedMatchExistsBetween(user1Id, user2Id)) {
            throw new IllegalStateException("Match already exists between users.");
        }

        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + user1Id));
        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + user2Id));

        Match match = new Match(
                user1,
                user2,
                meetingWeddingId,
                originWeddingId,
                null,                // score יחושב ע"י מנוע ההתאמה (System Layer)
                source != null ? source : MatchSourceType.UNKNOWN
        );

        match = matchRepository.save(match);

        // החלת חוקי מערכת על Match חדש
        applySystemRulesOnMatch(match);

        return match;
    }


// ============================================================
// 🔹 יצירת Match אוטומטית כאשר מתקבל UserAction מתאים
// ============================================================

    /**
     * Hook רשמי: מופעל כאשר מתרחש UserAction מסוג LIKE / WANT_TO_MEET.
     * כאן לא מבצעים לוגיקה כבדה — זו הרחבה מערכתית.
     */
    public Match onUserActionLike(Long actorUserId, Long targetUserId,
                                  Long meetingWeddingId, Long originWeddingId) {

        if (nonDeletedMatchExistsBetween(actorUserId, targetUserId)) {
            return getMatchBetweenUsersOrNull(actorUserId, targetUserId);
        }

        Match match = createNewMatch(
                actorUserId,
                targetUserId,
                meetingWeddingId,
                originWeddingId,
                MatchSourceType.LIKE_ACTION
        );

        return match;
    }


// ============================================================
// 🔹 AI Future Matching Engine — Hook עתידי
// ============================================================

    /**
     * נקודת חיבור עתידית למנוע AI:
     *  - חישוב ניקוד התאמה
     *  - ניתוח מאפיינים / תמונות / דינמיקה
     *  - התאמות חכמות גלובליות
     *
     * בשלב זה — רק שלד.
     */
    public void runAIMatchingEngineOnMatch(Long matchId) {
        Match match = getById(matchId);

        // דוגמה עתידית:
        // double score = aiEngine.calculateScore(match.getUser1(), match.getUser2());
        // match.setMatchScore(score);
        // matchRepository.save(match);
    }

    // ============================================================
// 🔵 UI Feedback Wrappers — מחזירים MatchActionFeedback ל-Frontend
// ============================================================

    public MatchActionFeedback freezeMatchWithFeedback(Long matchId,
                                                       Long actorUserId,
                                                       String reason,
                                                       WeddingMode mode,
                                                       SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        Match updated = freezeMatch(matchId, actorUserId, reason);

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_FROZEN,
                mode,
                sourceModule,
                beforeStatus,
                reason,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback unfreezeMatchWithFeedback(Long matchId,
                                                         Long actorUserId,
                                                         WeddingMode mode,
                                                         SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        Match updated = unfreezeMatch(matchId, actorUserId);

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_UNFROZEN,
                mode,
                sourceModule,
                beforeStatus,
                null,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback blockMatchWithFeedback(Long matchId,
                                                      Long actorUserId,
                                                      String reason,
                                                      WeddingMode mode,
                                                      SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        Match updated = blockMatch(matchId, actorUserId, reason);

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_BLOCKED,
                mode,
                sourceModule,
                beforeStatus,
                reason,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback unblockMatchWithFeedback(Long matchId,
                                                        Long actorUserId,
                                                        WeddingMode mode,
                                                        SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        Match updated = unblockMatch(matchId, actorUserId);

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_UNBLOCKED,
                mode,
                sourceModule,
                beforeStatus,
                null,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback approveMatchWithFeedback(Long matchId,
                                                        Long actorUserId,
                                                        WeddingMode mode,
                                                        SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        MatchApprovalResult res = approveMatchForUser(matchId, actorUserId);
        Match updated = res.getMatch();

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        SystemActionType action = becameMutualNow
                ? SystemActionType.MATCH_MUTUAL_CONFIRMED
                : SystemActionType.MATCH_UPDATED;

        return MatchActionFeedback.build(
                updated,
                actorUserId,
                action,
                mode,
                sourceModule,
                beforeStatus,
                null,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback unapproveMatchWithFeedback(Long matchId,
                                                          Long actorUserId,
                                                          WeddingMode mode,
                                                          SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        MatchApprovalResult res = unapproveMatchForUser(matchId, actorUserId);
        Match updated = res.getMatch();

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        // אצלך אין SystemActionType ל-"UNAPPROVE" לכן MATCH_UPDATED
        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_UPDATED,
                mode,
                sourceModule,
                beforeStatus,
                null,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback archiveMatchWithFeedback(Long matchId,
                                                        Long actorUserId,
                                                        WeddingMode mode,
                                                        SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        Match updated = archiveMatch(matchId);

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_ARCHIVED,
                mode,
                sourceModule,
                beforeStatus,
                null,
                becameMutualNow,
                mutualBrokenNow
        );
    }

    public MatchActionFeedback unarchiveMatchWithFeedback(Long matchId,
                                                          Long actorUserId,
                                                          WeddingMode mode,
                                                          SystemModule sourceModule) {

        Match matchBefore = getById(matchId);
        MatchStatus beforeStatus = matchBefore.getStatus();
        boolean beforeMutual = matchBefore.isMutualApproved();

        Match updated = unarchiveMatch(matchId);

        boolean afterMutual = updated.isMutualApproved();
        boolean becameMutualNow = (!beforeMutual && afterMutual);
        boolean mutualBrokenNow = (beforeMutual && !afterMutual);

        // אצלך אין Action ייעודי ל-UNARCHIVE -> MATCH_UPDATED
        return MatchActionFeedback.build(
                updated,
                actorUserId,
                SystemActionType.MATCH_UPDATED,
                mode,
                sourceModule,
                beforeStatus,
                null,
                becameMutualNow,
                mutualBrokenNow
        );
    }
}