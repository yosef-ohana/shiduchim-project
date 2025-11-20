package com.example.myproject.service;

import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import com.example.myproject.repository.MatchRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    // 1. יצירה / שליפה / עדכון בסיסי של Match
    // ============================================================

    /**
     * יוצר Match חדש או מחזיר קיים בין שני משתמשים.
     * כולל:
     *  - מניעת כפילויות
     *  - נורמליזציה של user1/user2
     *  - עדכון originWeddingId
     *  - עדכון matchSource
     *  - עדכון matchScore
     *  - הפעלה מחדש אם היה סגור
     */
    public Match createOrGetMatch(Long userId1,
                                  Long userId2,
                                  Long meetingWeddingId,
                                  Long originWeddingId,
                                  Double matchScore,
                                  String matchSource) {

        // מניעת self-match
        if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
            throw new IllegalArgumentException("Match must be between two different users.");
        }

        // נורמליזציה (ID נמוך → user1)
        Long first = Math.min(userId1, userId2);
        Long second = Math.max(userId1, userId2);

        Optional<Match> existingOpt =
                matchRepository.findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                        first, second,
                        second, first
                );

        if (existingOpt.isPresent()) {
            Match m = existingOpt.get();

            // עדכון ציון התאמה חדש
            if (matchScore != null) {
                m.setMatchScore(matchScore);
            }

            // שימור חתונה שבה נפגשו לראשונה
            if (originWeddingId != null && m.getOriginWeddingId() == null) {
                m.setOriginWeddingId(originWeddingId);
            }

            // שמירת חתונה שהובילה למאצ'
            if (meetingWeddingId != null && m.getMeetingWeddingId() == null) {
                m.setMeetingWeddingId(meetingWeddingId);
            }

            // שמירת מקור התאמה
            if (matchSource != null &&
                    (m.getMatchSource() == null || m.getMatchSource().isBlank())) {
                m.setMatchSource(matchSource);
            }

            // החייאת מאצ' שהיה סגור
            m.setActive(true);
            m.setUpdatedAt(LocalDateTime.now());
            return matchRepository.save(m);
        }

        // אין Match → יצירה חדשה
        User user1 = userRepository.findById(first)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + first));

        User user2 = userRepository.findById(second)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + second));

        Match match = new Match();
        match.setUser1(user1);
        match.setUser2(user2);

        match.setMeetingWeddingId(meetingWeddingId);
        match.setOriginWeddingId(originWeddingId);
        match.setMatchScore(matchScore);

        // מקור התאמה ברירת מחדל
        if (matchSource == null || matchSource.isBlank()) {
            matchSource = meetingWeddingId != null ? "wedding" : "global";
        }
        match.setMatchSource(matchSource);

        match.setActive(true);
        match.setBlocked(false);
        match.setFrozen(false);
        match.setChatOpened(false);
        match.setMutualApproved(false);

        match.setUnreadCount(0);
        match.setReadByUser1(true);
        match.setReadByUser2(true);

        match.setFirstMessageSent(false);
        match.setLastMessageAt(null);

        match.setCreatedAt(LocalDateTime.now());
        match.setUpdatedAt(LocalDateTime.now());

        return matchRepository.save(match);
    }

    // ============================================================
    // 2. שליפת Match לפי ID
    // ============================================================

    @Transactional(readOnly = true)
    public Optional<Match> getMatchById(Long matchId) {
        return matchRepository.findById(matchId);
    }

    // ============================================================
    // 3. סגירה לוגית של Match (active=false)
    // ============================================================

    public void closeMatch(Long matchId) {
        matchRepository.findById(matchId).ifPresent(m -> {
            m.setActive(false);
            m.setUpdatedAt(LocalDateTime.now());
            matchRepository.save(m);
        });
    }

    /**
     * סגירת כל המַצים שבהם המשתמש משתתף (למשל מחיקת חשבון).
     */
    public void closeAllMatchesForUser(Long userId) {
        List<Match> matches = matchRepository.findByUser1IdOrUser2Id(userId, userId);

        for (Match m : matches) {
            if (m.isActive()) {
                m.setActive(false);
                m.setUpdatedAt(LocalDateTime.now());
            }
        }

        matchRepository.saveAll(matches);
    }

    // ============================================================
    // 4. שליפות לפי משתמש (פעיל / כלל)
    // ============================================================

    @Transactional(readOnly = true)
    public List<Match> getActiveMatchesForUser(Long userId) {
        List<Match> u1 = matchRepository.findByUser1IdAndActiveTrue(userId);
        List<Match> u2 = matchRepository.findByUser2IdAndActiveTrue(userId);

        List<Match> result = new ArrayList<>();
        result.addAll(u1);
        result.addAll(u2);

        return result;
    }

    @Transactional(readOnly = true)
    public List<Match> getAllMatchesForUser(Long userId) {
        return matchRepository.findByUser1IdOrUser2Id(userId, userId);
    }

    // ============================================================
    // 5. התאמות לפי חתונה (meetingWeddingId)
    // ============================================================

    @Transactional(readOnly = true)
    public List<Match> getMatchesByWedding(Long weddingId) {
        return matchRepository.findByMeetingWeddingId(weddingId);
    }

    @Transactional(readOnly = true)
    public List<Match> getActiveMatchesForUserInWedding(Long userId, Long weddingId) {
        List<Match> all = getActiveMatchesForUser(userId);
        List<Match> result = new ArrayList<>();

        for (Match m : all) {
            if (weddingId.equals(m.getMeetingWeddingId())) {
                result.add(m);
            }
        }

        return result;
    }

    // ============================================================
    // 6. התאמות הדדיות למשתמש (Mutual Matches)
    // ============================================================

    /**
     * כל המַצ'ים ההדדיים והפעילים של משתמש.
     * (mutualApproved = true && active = true)
     */
    @Transactional(readOnly = true)
    public List<Match> getMutualMatchesForUser(Long userId) {
        return matchRepository
                .findByMutualApprovedTrueAndActiveTrueAndUser1IdOrMutualApprovedTrueAndActiveTrueAndUser2Id(
                        userId, userId
                );
    }

    /**
     * כל המַצ'ים ההדדיים והפעילים של משתמש בחתונה מסוימת.
     */
    @Transactional(readOnly = true)
    public List<Match> getMutualMatchesForUserInWedding(Long userId, Long weddingId) {
        List<Match> mutual = getMutualMatchesForUser(userId);
        List<Match> result = new ArrayList<>();

        for (Match m : mutual) {
            if (weddingId.equals(m.getMeetingWeddingId())) {
                result.add(m);
            }
        }

        return result;
    }

    // ============================================================
    // 7. מנוע התאמות בסיסי (Scoring Engine) – לפי אפיון 2025
    // ============================================================

    /**
     * יצירת התאמות לכל המשתתפים בחתונה:
     * - מביא את כל המשתמשים שה- lastWeddingId שלהם = weddingId
     * - מחשב ציון התאמה
     * - יוצר / מעדכן Match רק אם score >= minScore
     * - originWeddingId & meetingWeddingId = weddingId
     */
    public List<Match> generateMatchesForWedding(Long weddingId, double minScore) {

        // כל המשתתפים בחתונה (לפי User.lastWeddingId)
        List<User> participants = userRepository.findByLastWeddingId(weddingId);
        List<Match> createdOrUpdated = new ArrayList<>();

        for (int i = 0; i < participants.size(); i++) {
            for (int j = i + 1; j < participants.size(); j++) {

                User u1 = participants.get(i);
                User u2 = participants.get(j);

                double score = calculateMatchScore(u1, u2);

                if (score < minScore) {
                    continue;
                }

                Match match = createOrGetMatch(
                        u1.getId(),
                        u2.getId(),
                        weddingId,       // meetingWeddingId
                        weddingId,       // originWeddingId – נפגשו לראשונה בחתונה זו
                        score,
                        "wedding"
                );

                createdOrUpdated.add(match);
            }
        }

        return createdOrUpdated;
    }

    /**
     * מנוע ניקוד התאמה בסיסי – לפי אפיון 2025:
     *  - מגדר שונה:                   +30
     *  - כל אחד בטווח הגיל של השני:  +20 לכל כיוון
     *  - אזור מגורים זהה:             +15
     *  - רמת דתיות זהה:               +15
     */
    private double calculateMatchScore(User u1, User u2) {

        double score = 0.0;

        // מגדר שונה
        if (u1.getGender() != null &&
                u2.getGender() != null &&
                !u1.getGender().equalsIgnoreCase(u2.getGender())) {
            score += 30.0;
        }

        // האם גיל אחד מתאים לטווח הגיל הרצוי של השני – שני כיוונים
        if (isAgeInPreference(u1, u2)) score += 20.0;
        if (isAgeInPreference(u2, u1)) score += 20.0;

        // אזור מגורים זהה
        if (u1.getAreaOfResidence() != null &&
                u2.getAreaOfResidence() != null &&
                u1.getAreaOfResidence().equalsIgnoreCase(u2.getAreaOfResidence())) {
            score += 15.0;
        }

        // רמת דתיות זהה
        if (u1.getReligiousLevel() != null &&
                u2.getReligiousLevel() != null &&
                u1.getReligiousLevel().equalsIgnoreCase(u2.getReligiousLevel())) {
            score += 15.0;
        }

        return score;
    }

    /**
     * האם גיל target נמצא בטווח ההעדפות של source.
     * אם אין טווח מוגדר – מתייחסים כ"מתאים לכולם".
     */
    private boolean isAgeInPreference(User source, User target) {

        // אין טווח – אין הגבלה
        if (source.getPreferredAgeFrom() == null &&
                source.getPreferredAgeTo() == null) {
            return true;
        }

        if (target.getAge() == null) {
            return false;
        }

        int age = target.getAge();
        Integer from = source.getPreferredAgeFrom();
        Integer to   = source.getPreferredAgeTo();

        if (from != null && age < from) return false;
        if (to   != null && age > to)   return false;

        return true;
    }

    // ============================================================
    // 8. אישור / ביטול אישור Match (Mutual Flow)
    // ============================================================

    /**
     * אישור Match ע"י אחד הצדדים.
     * אם שני הצדדים אישרו → mutualApproved=true + chatOpened=true.
     */
    public Match approveMatch(Long matchId, Long userId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        Long u1 = match.getUser1().getId();
        Long u2 = match.getUser2().getId();

        if (!userId.equals(u1) && !userId.equals(u2)) {
            throw new IllegalArgumentException("User does not belong to this match");
        }

        // עדכון שדה האישור המתאים
        if (userId.equals(u1)) {
            match.setUser1Approved(true);
        } else {
            match.setUser2Approved(true);
        }

        // אם שני הצדדים אישרו → הדדי + צ'אט פתוח
        if (match.isUser1Approved() && match.isUser2Approved()) {
            match.setMutualApproved(true);
            match.setChatOpened(true);

            // במצב התחלה – אין הודעות שלא נקראו
            match.setUnreadCount(0);
            match.setReadByUser1(true);
            match.setReadByUser2(true);
        }

        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    /**
     * ביטול אישור Match ע"י אחד הצדדים:
     * - מאפס אישור של אותו צד
     * - מבטל הדדיות וצ'אט אם היה
     */
    public Match unapproveMatch(Long matchId, Long userId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        Long u1 = match.getUser1().getId();
        Long u2 = match.getUser2().getId();

        if (!userId.equals(u1) && !userId.equals(u2)) {
            throw new IllegalArgumentException("User does not belong to this match");
        }

        if (userId.equals(u1)) {
            match.setUser1Approved(false);
        } else {
            match.setUser2Approved(false);
        }

        // הדדיות וצ'אט מתבטלים
        match.setMutualApproved(false);
        match.setChatOpened(false);

        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    // ============================================================
    // 9. חסימה / ביטול חסימה
    // ============================================================

    /**
     * חסימת Match:
     * - blocked=true
     * - active=false
     * - mutualApproved=false
     * - chatOpened=false
     */
    public Match blockMatch(Long matchId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setBlocked(true);
        match.setActive(false);
        match.setMutualApproved(false);
        match.setChatOpened(false);

        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    /**
     * ביטול חסימה – מחזיר blocked=false.
     * (לא פותח צ'אט ולא מחזיר הדדיות – זה נשאר לוגיקה נפרדת).
     */
    public Match unblockMatch(Long matchId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setBlocked(false);
        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    // ============================================================
    // 10. הקפאה / ביטול הקפאה
    // ============================================================

    /**
     * הקפאת Match (לפי החלטת מערכת/מנהל).
     * אפשר לשמור reason בשדה freezeReason (נניח ע"י קונטרולר אדמין).
     */
    public Match freezeMatch(Long matchId, String reason) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setFrozen(true);
        match.setFreezeReason(reason);
        match.setUpdatedAt(LocalDateTime.now());

        return matchRepository.save(match);
    }

    /**
     * ביטול הקפאה.
     */
    public Match unfreezeMatch(Long matchId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setFrozen(false);
        match.setFreezeReason(null);
        match.setUpdatedAt(LocalDateTime.now());

        return matchRepository.save(match);
    }

    // ============================================================
    // 11. פתיחת צ'אט / סגירת צ'אט ידנית (override)
    // ============================================================

    /**
     * פתיחת צ'אט ידנית (למשל ע"י אדמין).
     * לא מחייב הדדיות – זה override.
     */
    public Match openChat(Long matchId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setChatOpened(true);
        match.setUpdatedAt(LocalDateTime.now());

        return matchRepository.save(match);
    }

    /**
     * סגירת צ'אט (למשל בעת חסימה / סיום קשר).
     */
    public Match closeChat(Long matchId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setChatOpened(false);
        match.setUpdatedAt(LocalDateTime.now());

        return matchRepository.save(match);
    }

    // ============================================================
    // 12. בדיקות קיום Match / שליפת Match בין שני משתמשים
    // ============================================================

    /**
     * האם קיים Match בין שני משתמשים (באחד הכיוונים).
     */
    public boolean existsMatchBetween(Long userId1, Long userId2) {
        return matchRepository.existsByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                userId1, userId2,
                userId2, userId1
        );
    }

    /**
     * מציאת Match פעיל בין שני משתמשים (אם קיים).
     */
    @Transactional(readOnly = true)
    public Optional<Match> findActiveMatch(Long userId1, Long userId2) {

        Optional<Match> matchOpt =
                matchRepository.findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                        userId1, userId2,
                        userId2, userId1
                );

        if (matchOpt.isPresent() && matchOpt.get().isActive()) {
            return matchOpt;
        }

        return Optional.empty();
    }

    // ============================================================
    // 13. שליפות לפי סטטוס Match (Active / Blocked / Frozen / Chat)
    // ============================================================

    /** כל המַצים הפעילים במערכת. */
    @Transactional(readOnly = true)
    public List<Match> getAllActiveMatches() {
        return matchRepository.findByActiveTrue();
    }

    /** כל המַצים הלא־פעילים (נסגרו / בוטלו). */
    @Transactional(readOnly = true)
    public List<Match> getAllInactiveMatches() {
        return matchRepository.findByActiveFalse();
    }

    /** כל המַצים החסומים. */
    @Transactional(readOnly = true)
    public List<Match> getBlockedMatches() {
        return matchRepository.findByBlockedTrue();
    }

    /** כל המַצים המוקפאים. */
    @Transactional(readOnly = true)
    public List<Match> getFrozenMatches() {
        return matchRepository.findByFrozenTrue();
    }

    /** כל המַצים שבהם כבר נפתח צ'אט. */
    @Transactional(readOnly = true)
    public List<Match> getMatchesWithOpenChat() {
        return matchRepository.findByChatOpenedTrue();
    }

    // ============================================================
    // 14. שליפות לפי מקור התאמה (matchSource)
    // ============================================================

    /**
     * כל המַצים לפי מקור ("wedding" / "global" / "admin" / "ai" וכו').
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesBySource(String source) {
        return matchRepository.findByMatchSource(source);
    }

    /**
     * מַצים פעילים לפי מקור.
     */
    @Transactional(readOnly = true)
    public List<Match> getActiveMatchesBySource(String source) {
        return matchRepository.findByMatchSourceAndActiveTrue(source);
    }

    /**
     * מַצים הדדיים + פעילים לפי מקור.
     */
    @Transactional(readOnly = true)
    public List<Match> getMutualMatchesBySource(String source) {
        return matchRepository.findByMatchSourceAndMutualApprovedTrueAndActiveTrue(source);
    }

    // ============================================================
    // 15. שליפות לפי ציון התאמה (matchScore)
    // ============================================================

    /**
     * כל המַצים עם score >= ערך מסוים.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesWithMinScore(double minScore) {
        return matchRepository.findByMatchScoreGreaterThanEqual(minScore);
    }

    /**
     * כל המַצים עם score מדויק.
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesByExactScore(double score) {
        return matchRepository.findByMatchScore(score);
    }

    // ============================================================
    // 16. פעולות אדמין על מַצים (Admin Controls)
    // ============================================================

    /**
     * מחיקה מלאה (Hard Delete) של Match מהמערכת.
     */
    public void deleteMatchPermanently(Long matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new IllegalArgumentException("Match not found");
        }
        matchRepository.deleteById(matchId);
    }

    /**
     * שינוי מקור ההתאמה (matchSource) ע"י אדמין.
     */
    public Match updateMatchSource(Long matchId, String newSource) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setMatchSource(newSource);
        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    /**
     * שינוי ציון התאמה (matchScore) ידני ע"י אדמין.
     */
    public Match updateMatchScore(Long matchId, double newScore) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setMatchScore(newScore);
        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    /**
     * יצירת Match ע"י אדמין (override מלא, כולל מקור).
     * בפועל משתמש ב-createOrGetMatch כדי למנוע כפילויות.
     */
    public Match adminForceCreateMatch(Long userId1,
                                       Long userId2,
                                       Long meetingWeddingId,
                                       Long originWeddingId,
                                       Double matchScore,
                                       String source) {

        String finalSource = (source != null && !source.isBlank()) ? source : "admin";

        return createOrGetMatch(
                userId1,
                userId2,
                meetingWeddingId,
                originWeddingId,
                matchScore,
                finalSource
        );
    }

    // ============================================================
    // 17. ביטול התאמה הדדית (Unmatch)
    // ============================================================

    /**
     * ביטול התאמה:
     * - איפוס user1Approved / user2Approved
     * - mutualApproved=false
     * - chatOpened=false
     * - active=false
     */
    public Match unmatch(Long matchId, Long userId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User does not belong to this match");
        }

        match.setUser1Approved(false);
        match.setUser2Approved(false);
        match.setMutualApproved(false);
        match.setChatOpened(false);
        match.setActive(false);

        match.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(match);
    }

    // ============================================================
    // 18. רשימות למשתמש – "אני עשיתי לייק", "מחכים לי", "חסומים", "קפואים"
    // ============================================================

    /**
     * רשימת מַצים שבהם המשתמש כבר אישר (userXApproved=true),
     * אבל עדיין לא הדדי (mutualApproved=false).
     * טוב למסך "התאמות ששלחתי / אני מחכה להם".
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesILiked(Long userId) {

        List<Match> all = matchRepository.findByUser1IdOrUser2Id(userId, userId);
        List<Match> result = new ArrayList<>();

        for (Match m : all) {
            boolean iAmUser1 = (m.getUser1() != null && userId.equals(m.getUser1().getId()));
            boolean iAmUser2 = (m.getUser2() != null && userId.equals(m.getUser2().getId()));

            boolean iApproved =
                    (iAmUser1 && m.isUser1Approved()) ||
                            (iAmUser2 && m.isUser2Approved());

            if (iApproved && !m.isMutualApproved()) {
                result.add(m);
            }
        }

        return result;
    }

    /**
     * רשימת מַצים שבהם הצד השני כבר אישר,
     * ואני עדיין לא אישרתי (ממתינים לאישור שלי).
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesWaitingForMyApproval(Long userId) {

        List<Match> all = matchRepository.findByUser1IdOrUser2Id(userId, userId);
        List<Match> result = new ArrayList<>();

        for (Match m : all) {

            boolean iAmUser1 = (m.getUser1() != null && userId.equals(m.getUser1().getId()));
            boolean iAmUser2 = (m.getUser2() != null && userId.equals(m.getUser2().getId()));

            if (iAmUser1) {
                boolean otherApproved = m.isUser2Approved();
                boolean iApproved = m.isUser1Approved();
                if (otherApproved && !iApproved && !m.isMutualApproved()) {
                    result.add(m);
                }
            } else if (iAmUser2) {
                boolean otherApproved = m.isUser1Approved();
                boolean iApproved = m.isUser2Approved();
                if (otherApproved && !iApproved && !m.isMutualApproved()) {
                    result.add(m);
                }
            }
        }

        return result;
    }

    /**
     * רשימת כל המַצים ההדדיים והפעילים של המשתמש.
     * (למסך "התאמות הדדיות" הראשי).
     */
    @Transactional(readOnly = true)
    public List<Match> getMutualMatchesListForUser(Long userId) {
        return matchRepository
                .findByMutualApprovedTrueAndActiveTrueAndUser1IdOrMutualApprovedTrueAndActiveTrueAndUser2Id(
                        userId, userId
                );
    }

    /**
     * רשימת מַצים חסומים של המשתמש (blocked=true),
     * כלומר התאמות שנחסמו ע"י אחד הצדדים.
     */
    @Transactional(readOnly = true)
    public List<Match> getBlockedMatchesForUser(Long userId) {

        List<Match> allBlocked = matchRepository.findByBlockedTrue();
        List<Match> result = new ArrayList<>();

        for (Match m : allBlocked) {
            if (m.involvesUser(userId)) {
                result.add(m);
            }
        }

        return result;
    }

    /**
     * רשימת מַצים קפואים (frozen=true) של המשתמש.
     */
    @Transactional(readOnly = true)
    public List<Match> getFrozenMatchesForUser(Long userId) {

        List<Match> allFrozen = matchRepository.findByFrozenTrue();
        List<Match> result = new ArrayList<>();

        for (Match m : allFrozen) {
            if (m.involvesUser(userId)) {
                result.add(m);
            }
        }

        return result;
    }

    // ============================================================
    // 19. originWeddingId – "איפה נפגשו לראשונה"
    // ============================================================

    /**
     * כל המַצים שה-originWeddingId שלהם = weddingId.
     * (גם אם היום הם גלובליים / Admin / AI וכו').
     */
    @Transactional(readOnly = true)
    public List<Match> getMatchesOriginatedInWedding(Long weddingId) {

        List<Match> all = matchRepository.findAll();
        List<Match> result = new ArrayList<>();

        for (Match m : all) {
            if (weddingId != null && weddingId.equals(m.getOriginWeddingId())) {
                result.add(m);
            }
        }

        return result;
    }

    /**
     * עדכון originWeddingId למַץ קיים – אם בדיעבד זיהינו באיזו חתונה נפגשו לראשונה.
     */
    public Match updateOriginWedding(Long matchId, Long originWeddingId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        match.setOriginWeddingId(originWeddingId);
        match.setUpdatedAt(LocalDateTime.now());

        return matchRepository.save(match);
    }
}
