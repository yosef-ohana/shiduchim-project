package com.example.myproject.service;

import com.example.myproject.model.*;
import com.example.myproject.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional
public class WeddingService {

    private final WeddingRepository weddingRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final MatchRepository matchRepository;

    public WeddingService(
            WeddingRepository weddingRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            MatchRepository matchRepository
    ) {
        this.weddingRepository = weddingRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.matchRepository = matchRepository;
    }

    // ============================================================
    // 1. יצירת חתונה – אדמין / בעל אירוע
    // ============================================================

    /** יצירת חתונה ע"י אדמין */
    public Wedding createWeddingByAdmin(String name,
                                        LocalDateTime start,
                                        LocalDateTime end,
                                        Long adminUserId,
                                        Long ownerUserId,
                                        String bgImage,
                                        String bgVideo) {

        validateAdmin(adminUserId);

        Wedding w = createWeddingInternal(name, start, end, adminUserId, bgImage, bgVideo);

        if (ownerUserId != null) {
            User owner = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));

            if (!owner.isEventManager()) {
                throw new IllegalStateException("User is not event manager");
            }

            w.setOwner(owner);
        }

        return weddingRepository.save(w);
    }

    public Wedding createWeddingByOwner(String name,
                                        LocalDateTime start,
                                        LocalDateTime end,
                                        Long ownerUserId,
                                        String bgImage,
                                        String bgVideo) {

        validateEventOwner(ownerUserId); // הרשאת בעל אירוע

        Wedding w = createWeddingInternal(name, start, end, ownerUserId, bgImage, bgVideo);

        // נטען את המשתמש ונגדיר אותו כבעל האירוע (יסנכרן גם ownerUserId)
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        w.setOwner(owner); // כאן שני השדות מסתנכרנים
        return weddingRepository.save(w);
    }

    /** פונקציה פנימית אחת לשני הסוגים */
    private Wedding createWeddingInternal(String name,
                                          LocalDateTime start,
                                          LocalDateTime end,
                                          Long creatorId,
                                          String bgImage,
                                          String bgVideo) {

        if (start == null) throw new IllegalArgumentException("חייב זמן התחלה");

        // ברירת מחדל: 01:00 ביום הבא
        if (end == null) {
            LocalDate nextDay = start.toLocalDate().plusDays(1);
            end = LocalDateTime.of(nextDay, LocalTime.of(1, 0));
        }

        Wedding w = new Wedding();
        w.setName(name);
        w.setStartTime(start);
        w.setEndTime(end);
        w.setCreatedByUserId(creatorId);

        // רקעים
        w.setBackgroundImageUrl(bgImage);
        w.setBackgroundVideoUrl(bgVideo);
        updateBackgroundModeFromUrls(w);

        w.setActive(true);
        w.setCreatedAt(LocalDateTime.now());

        return weddingRepository.save(w);
    }

    // ============================================================
    // 2. עדכון חתונה – אדמין / בעל אירוע
    // ============================================================

    /** עדכון חתונה ע"י אדמין */
    public Wedding updateWeddingByAdmin(Long weddingId,
                                        String name,
                                        LocalDateTime start,
                                        LocalDateTime end,
                                        String bgImage,
                                        String bgVideo,
                                        Boolean active) {

        return updateWeddingInternal(weddingId, name, start, end, bgImage, bgVideo, active);
    }

    /** עדכון חתונה ע"י בעל האירוע */
    public Wedding updateWeddingByOwner(Long weddingId,
                                        Long ownerUserId,
                                        String name,
                                        LocalDateTime start,
                                        LocalDateTime end,
                                        String bgImage,
                                        String bgVideo,
                                        Boolean active) {

        validateOwnerOfWedding(ownerUserId, weddingId);
        return updateWeddingInternal(weddingId, name, start, end, bgImage, bgVideo, active);
    }

    /** ליבה של עדכון חתונה */
    private Wedding updateWeddingInternal(Long weddingId,
                                          String name,
                                          LocalDateTime start,
                                          LocalDateTime end,
                                          String bgImage,
                                          String bgVideo,
                                          Boolean active) {

        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("חתונה לא נמצאה"));

        if (name != null) w.setName(name);
        if (start != null) w.setStartTime(start);
        if (end != null) w.setEndTime(end);

        // עדכון רקעים
        if (bgImage != null) {
            if (bgImage.isBlank()) {
                w.setBackgroundImageUrl(null);
            } else {
                w.setBackgroundImageUrl(bgImage);
            }
        }

        if (bgVideo != null) {
            if (bgVideo.isBlank()) {
                w.setBackgroundVideoUrl(null);
            } else {
                w.setBackgroundVideoUrl(bgVideo);
            }
        }

        updateBackgroundModeFromUrls(w);

        if (active != null) w.setActive(active);

        w.setUpdatedAt(LocalDateTime.now());
        return weddingRepository.save(w);
    }

    // ============================================================
    // 3. מחיקת חתונה – אדמין בלבד
    // ============================================================

    public void deleteWeddingByAdmin(Long adminUserId, Long weddingId) {
        validateAdmin(adminUserId);
        weddingRepository.deleteById(weddingId);
    }

    // ============================================================
    // 4. בדיקות הרשאה
    // ============================================================

    private void validateAdmin(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!u.isAdmin()) throw new IllegalStateException("אין הרשאת אדמין");
    }

    private void validateEventOwner(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!u.isEventManager()) throw new IllegalStateException("לא בעל אירוע");
    }

    private void validateOwnerOfWedding(Long ownerId, Long weddingId) {
        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("חתונה לא נמצאה"));

        if (!Objects.equals(w.getOwnerUserId(), ownerId))
            throw new IllegalStateException("משתמש זה אינו בעל האירוע");
    }

    // ============================================================
    // 5. הצטרפות לחתונה (Join Wedding)
    // ============================================================

    public void joinWedding(Long userId, Long weddingId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wedding wedding = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new RuntimeException("Wedding not found"));

        LocalDateTime now = LocalDateTime.now();

        // האירוע חייב להיות פעיל (active=true)
        if (!wedding.isActive()) {
            throw new IllegalStateException("האירוע אינו פעיל — לא ניתן להצטרף.");
        }

        // אסור להצטרף אחרי שהאירוע הסתיים
        if (now.isAfter(wedding.getEndTime())) {
            throw new IllegalStateException("האירוע כבר הסתיים — אי אפשר להצטרף.");
        }

        // מותר להצטרף:
        // - לפני זמן ההתחלה
        // - בזמן האירוע (LIVE)

        // שמירת כניסה ראשונה
        if (user.getFirstWeddingId() == null) {
            user.setFirstWeddingId(weddingId);
        }

        // עדכון כניסה אחרונה
        user.setLastWeddingId(weddingId);

        // היסטוריית חתונות
        List<Long> history = user.getWeddingsHistory();
        if (history == null) history = new ArrayList<>();
        if (!history.contains(weddingId)) history.add(weddingId);
        user.setWeddingsHistory(history);

        // מעבר למצב Wedding Mode (לתצוגת הרקע)
        user.setActiveBackgroundWeddingId(weddingId);

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ============================================================
    // 6. שליפות משתתפים
    // ============================================================

    /** משתמשים שהאירוע האחרון שלהם הוא weddingId (CURRENT participants) */
    public List<User> getCurrentParticipants(Long weddingId) {
        return userRepository.findByLastWeddingId(weddingId);
    }

    /** משתמשים שהיו אי פעם בחתונה זו (היסטוריה מלאה) */
    public List<User> getHistoricalParticipants(Long weddingId) {
        return userRepository.findUsersWhoAttendedWedding(weddingId);
    }

    // ============================================================
    // 7. עדכון רקעים / עיצוב חתונה
    // ============================================================

    /**
     * עדכון רקע תמונה/וידאו בלבד עבור חתונה — משמש לדשבורד ניהול.
     */
    public Wedding updateWeddingBackground(Long weddingId,
                                           String bgImage,
                                           String bgVideo) {

        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("חתונה לא נמצאה"));

        if (bgImage != null) {
            if (bgImage.isBlank()) w.setBackgroundImageUrl(null);
            else w.setBackgroundImageUrl(bgImage);
        }

        if (bgVideo != null) {
            if (bgVideo.isBlank()) w.setBackgroundVideoUrl(null);
            else w.setBackgroundVideoUrl(bgVideo);
        }

        updateBackgroundModeFromUrls(w);

        w.setUpdatedAt(LocalDateTime.now());
        return weddingRepository.save(w);
    }

    /**
     * עוזר פנימי — קובע את backgroundMode לפי האם קיימת תמונה / וידאו.
     * - אם יש וידאו → VIDEO
     * - אחרת אם יש תמונה → IMAGE
     * - אחרת → DEFAULT
     */
    private void updateBackgroundModeFromUrls(Wedding w) {

        if (w.getBackgroundVideoUrl() != null && !w.getBackgroundVideoUrl().isBlank()) {
            w.setBackgroundMode("VIDEO");
            return;
        }

        if (w.getBackgroundImageUrl() != null && !w.getBackgroundImageUrl().isBlank()) {
            w.setBackgroundMode("IMAGE");
            return;
        }

        w.setBackgroundMode("DEFAULT");
    }


    public boolean isOwnerOfWedding(Long userId, Long weddingId) {

        if (userId == null || weddingId == null) {
            return false;
        }

        return weddingRepository.findById(weddingId)
                .map(w -> {
                    Long ownerIdFromRelation = (w.getOwner() != null ? w.getOwner().getId() : null);
                    Long ownerIdFromField = w.getOwnerUserId();
                    return Objects.equals(ownerIdFromRelation, userId)
                            || Objects.equals(ownerIdFromField, userId);
                })
                .orElse(false);
    }

    // ============================================================
    // 8. חתונות LIVE / פעילה כרגע / עתידית
    // ============================================================

    /** האם חתונה פעילה כרגע (active=true + בתוך טווח הזמנים) */
    public boolean isWeddingLive(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .map(w -> w.isActive()
                        && !w.getStartTime().isAfter(LocalDateTime.now())
                        && !w.getEndTime().isBefore(LocalDateTime.now()))
                .orElse(false);
    }

    /** חתונות שמתרחשות כעת */
    public List<Wedding> getLiveWeddings() {
        LocalDateTime now = LocalDateTime.now();
        return weddingRepository.findByStartTimeBeforeAndEndTimeAfter(now, now);
    }

    /** חתונות עתידיות */
    public List<Wedding> getUpcomingWeddings() {
        return weddingRepository.findByStartTimeAfter(LocalDateTime.now());
    }

    /** חתונות שכבר הסתיימו */
    public List<Wedding> getFinishedWeddings() {
        return weddingRepository.findByEndTimeBefore(LocalDateTime.now());
    }

    // ============================================================
    // 9. סטטיסטיקות חתונה (לפי אפיון 2025)
    // ============================================================

    /**
     * מחזיר סטטיסטיקות מלאות על חתונה:
     * - כמות משתתפים כרגע
     * - כמה היו אי פעם
     * - כמות מצ'ים
     * - כמה היו הדדיים
     */
    public WeddingStats getWeddingStats(Long weddingId) {

        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        long currentParticipants = userRepository.findByLastWeddingId(weddingId).size();
        long historicalParticipants = userRepository.findUsersWhoAttendedWedding(weddingId).size();
        long matchesTotal = matchRepository.countByMeetingWeddingId(weddingId);
        long matchesMutual = matchRepository.countByMeetingWeddingIdAndMutualApprovedTrue(weddingId);

        WeddingStats stats = new WeddingStats();
        stats.setWeddingId(weddingId);
        stats.setWeddingName(w.getName());
        stats.setActive(w.isActive());
        stats.setStartTime(w.getStartTime());
        stats.setEndTime(w.getEndTime());

        stats.setCurrentParticipants(currentParticipants);
        stats.setHistoricalParticipants(historicalParticipants);
        stats.setMatchesCount(matchesTotal);
        stats.setMutualMatchesCount(matchesMutual);

        return stats;
    }

    /**
     * DTO פנימי לסטטיסטיקות חתונה
     */
    public static class WeddingStats {

        private Long weddingId;
        private String weddingName;
        private boolean active;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long currentParticipants;
        private long historicalParticipants;
        private long matchesCount;
        private long mutualMatchesCount;

        // ===== Getters / Setters =====

        public Long getWeddingId() { return weddingId; }
        public void setWeddingId(Long weddingId) { this.weddingId = weddingId; }

        public String getWeddingName() { return weddingName; }
        public void setWeddingName(String weddingName) { this.weddingName = weddingName; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public long getCurrentParticipants() { return currentParticipants; }
        public void setCurrentParticipants(long currentParticipants) { this.currentParticipants = currentParticipants; }

        public long getHistoricalParticipants() { return historicalParticipants; }
        public void setHistoricalParticipants(long historicalParticipants) { this.historicalParticipants = historicalParticipants; }

        public long getMatchesCount() { return matchesCount; }
        public void setMatchesCount(long matchesCount) { this.matchesCount = matchesCount; }

        public long getMutualMatchesCount() { return mutualMatchesCount; }
        public void setMutualMatchesCount(long mutualMatchesCount) { this.mutualMatchesCount = mutualMatchesCount; }
    }
    // ============================================================
    // 10. סגירה ידנית של חתונה (Admin / בעל אירוע)
    // ============================================================

    /**
     * סגירה ידנית של חתונה (active=false).
     * אם endTime לא מוגדר – קובע אותו לעכשיו.
     */
    public void closeWeddingManually(Long weddingId) {

        Wedding wedding = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        if (!wedding.isActive() && wedding.getEndTime() != null &&
                wedding.getEndTime().isBefore(LocalDateTime.now())) {
            // כבר סגור – לא עושים כלום
            return;
        }

        wedding.setActive(false);

        if (wedding.getEndTime() == null) {
            wedding.setEndTime(LocalDateTime.now());
        }

        wedding.setUpdatedAt(LocalDateTime.now());
        weddingRepository.save(wedding);
    }

    // ============================================================
    // 11. סגירת חתונות שפג תוקפן (Cron / Scheduled)
    // ============================================================

    /**
     * סגירת כל החתונות שה-EndTime שלהן כבר עבר.
     * מיועד לרוץ כ-Job מתוזמן.
     */
    public void closeExpiredWeddings() {

        LocalDateTime now = LocalDateTime.now();
        List<Wedding> expired = weddingRepository.findByEndTimeBefore(now);

        for (Wedding w : expired) {
            if (w.isActive()) {
                w.setActive(false);
                w.setUpdatedAt(now);
                weddingRepository.save(w);
            }
        }
    }

    // ============================================================
    // 12. שליחת הודעת Broadcast לכל משתתפי חתונה
    // ============================================================

    /**
     * שולח התראה לכל המשתמשים שהחתונה האחרונה שלהם היא weddingId.
     * משמש לאדמין / בעל אירוע: "הכלה נכנסת", "עכשיו ריקודים", הודעות טכניות וכו'.
     */
    public void sendBroadcast(Long weddingId, String title, String message) {

        List<User> participants = userRepository.findByLastWeddingId(weddingId);
        LocalDateTime now = LocalDateTime.now();

        for (User user : participants) {

            if (!user.isAllowInAppNotifications()) continue;

            Notification n = new Notification();
            n.setRecipient(user);
            n.setType(NotificationType.EVENT_BROADCAST);
            n.setTitle(title);
            n.setMessage(message);
            n.setWeddingId(weddingId);
            n.setRead(false);
            n.setCreatedAt(now);

            notificationRepository.save(n);
        }
    }

    // ============================================================
    // 13. התראת "האירוע הסתיים" לכל המשתתפים
    // ============================================================

    /**
     * שולח התראה אוטומטית כאשר האירוע הסתיים.
     * ניתן לקרוא אחרי closeWeddingManually או אחרי closeExpiredWeddings.
     */
    public void notifyEventEnded(Long weddingId) {

        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        List<User> participants = userRepository.findByLastWeddingId(weddingId);
        LocalDateTime now = LocalDateTime.now();

        for (User u : participants) {

            if (!u.isAllowInAppNotifications()) continue;

            Notification n = new Notification();
            n.setRecipient(u);
            n.setType(NotificationType.WEDDING_ENDED);
            n.setTitle("האירוע הסתיים");
            n.setMessage("האירוע \"" + w.getName() + "\" הסתיים. כעת אפשר לראות גם מאגר כללי.");
            n.setWeddingId(weddingId);
            n.setCreatedAt(now);
            n.setRead(false);

            notificationRepository.save(n);
        }
    }

    // ============================================================
    // 14. מעבר ממצב חתונה למצב גלובלי אחרי האירוע
    // ============================================================

    /**
     * אחרי שהאירוע הסתיים – אפשר לשחרר את המשתמש מ-Wedding Mode
     * כך שהרקע יחזור לברירת מחדל (מאגר כללי),
     * אבל הסטטוס הלוגי "מותר לראות גלובלי" נגזר מהזמן (ע"י canViewGlobal).
     *
     * כאן אנחנו רק מנקים את activeBackgroundWeddingId.
     */
    public void allowGlobalPoolAfterEvent(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long lastWeddingId = user.getLastWeddingId();
        if (lastWeddingId == null) {
            // לא היה באירוע – אין מה לשחרר
            return;
        }

        // אם החתונה עדיין חיה – לא משחררים למאגר גלובלי
        if (!isWeddingFinished(lastWeddingId)) {
            throw new IllegalStateException("האירוע עדיין פעיל – אי אפשר לעבור למאגר הכללי.");
        }

        // שחרור רקע חתונה – מעבר ל-DEFAULT בצד לקוח
        user.setActiveBackgroundWeddingId(null);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    // ============================================================
    // 15. בדיקת מה מותר למשתמש לראות כעת (Wedding / Global)
    // ============================================================

    /**
     * האם המשתמש נמצא במצב "Wedding Mode בלבד"?
     * - יש לו activeBackgroundWeddingId
     * - והחתונה הזו עדיין LIVE
     */
    public boolean canViewWeddingOnly(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long activeWeddingId = user.getActiveBackgroundWeddingId();
        if (activeWeddingId == null) return false;

        return isWeddingLive(activeWeddingId);
    }

    /**
     * האם המשתמש יכול לראות גם מאגר גלובלי?
     * לוגיקה:
     * - אם לא היה עדיין באף חתונה → רואה גלובלי (מאגר כללי רגיל)
     * - אם היה בחתונה → רק אחרי שהיא הסתיימה (endTime עבר)
     */
    public boolean canViewGlobal(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long lastWeddingId = user.getLastWeddingId();
        if (lastWeddingId == null) {
            // לא היה באירוע → אפשר גלובלי
            return true;
        }

        // אם החתונה הסתיימה → מותר לראות גלובלי
        return isWeddingFinished(lastWeddingId);
    }

    // ============================================================
    // 16. מחיקה פיזית של חתונה (Admin Only) – לשימוש זהיר
    // ============================================================

    /**
     * מחיקה פיזית של חתונה.
     * חשוב: בקונטרולר לוודא שהמשתמש הוא Admin.
     */
    public void deleteWedding(Long weddingId) {
        weddingRepository.deleteById(weddingId);
    }

    // ============================================================
    // 17. עדכון מלא של חתונה (Admin Panel)
    // ============================================================

    /**
     * עדכון מלא של חתונה — לשימוש בפאנל ניהול:
     * - כל פרמטר שאינו null יעודכן
     * - מה ש-null נשאר כמו שהוא
     */
    public Wedding adminUpdateWedding(
            Long weddingId,
            String name,
            LocalDateTime start,
            LocalDateTime end,
            Long ownerUserId,
            String bgImage,
            String bgVideo,
            Boolean active
    ) {
        Wedding w = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        if (name != null) w.setName(name);
        if (start != null) w.setStartTime(start);
        if (end != null) w.setEndTime(end);
        if (ownerUserId != null) {
            User newOwner = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            w.setOwner(newOwner); // שוב, מסנכרן owner + ownerUserId
        }

        if (bgImage != null) {
            if (bgImage.isBlank()) w.setBackgroundImageUrl(null);
            else w.setBackgroundImageUrl(bgImage);
        }

        if (bgVideo != null) {
            if (bgVideo.isBlank()) w.setBackgroundVideoUrl(null);
            else w.setBackgroundVideoUrl(bgVideo);
        }

        if (active != null) w.setActive(active);

        // לעדכן backgroundMode בהתאם
        updateBackgroundModeFromUrls(w);

        w.setUpdatedAt(LocalDateTime.now());
        return weddingRepository.save(w);
    }

    // ============================================================
    // 18. פונקציות עזר
    // ============================================================

    /** האם חתונה הסתיימה? (לפי endTime בלבד) */
    public boolean isWeddingFinished(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .map(w -> w.getEndTime() != null &&
                        w.getEndTime().isBefore(LocalDateTime.now()))
                .orElse(false);
    }

    /** האם חתונה מסומנת כ-active בטבלה (בלי קשר לזמן) */
    public boolean isWeddingMarkedActive(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .map(Wedding::isActive)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isUserInWedding(Long userId, Long weddingId) {

        if (userId == null || weddingId == null) {
            return false;
        }

        // טוענים חתונה
        Wedding wedding = weddingRepository.findById(weddingId)
                .orElse(null);

        if (wedding == null) {
            return false;
        }

        // טוענים משתמש
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return false;
        }

        // בעל האירוע תמיד משתתף
        if (Objects.equals(wedding.getOwnerUserId(), userId)) {
            return true;
        }

        // משתמש נוכחי — האם מופיע ברשימת המשתתפים הנוכחיים?
        List<User> current = getCurrentParticipants(weddingId);
        boolean inCurrent = current.stream()
                .anyMatch(u -> Objects.equals(u.getId(), userId));

        if (inCurrent) return true;

        // משתמש היסטורי — לפי דרישות האפיון
        List<User> history = getHistoricalParticipants(weddingId);
        boolean inHistory = history.stream()
                .anyMatch(u -> Objects.equals(u.getId(), userId));

        return inHistory;
    }

    // ============================================================
    // 19. שליפת חתונה ע"י ID (לצורכי ניהול / רקעים / דשבורד)
    // ============================================================

    @Transactional(readOnly = true)
    public Wedding getWeddingById(Long weddingId) {
        return weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));
    }
    
}
