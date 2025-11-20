package com.example.myproject.service;

import com.example.myproject.model.Notification;
import com.example.myproject.model.NotificationType;
import com.example.myproject.model.User;
import com.example.myproject.repository.NotificationRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // =====================================================================
    // 1️⃣ יצירת התראה מלאה (הגרסה הרשמית של אפיון 2025)
    // =====================================================================

    public Notification createNotification(Long recipientId,
                                           NotificationType type,
                                           String title,
                                           String message,
                                           Long relatedUserId,
                                           Long weddingId,
                                           Long matchId,
                                           Long chatMessageId,
                                           String metadata,
                                           String category,
                                           String source,
                                           int priorityLevel) {

        if (recipientId == null)
            throw new IllegalArgumentException("recipientId cannot be null.");

        if (type == null)
            throw new IllegalArgumentException("NotificationType cannot be null.");

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + recipientId));

        if (!shouldSendTo(recipient))
            return null;

        Notification n = new Notification(
                recipient,
                type,
                title,
                message,
                relatedUserId,
                weddingId,
                matchId,
                chatMessageId,
                metadata,
                category,
                source,
                priorityLevel
        );

        return notificationRepository.save(n);
    }

    /**
     * גרסת Backwards compatibility.
     */
    public Notification createNotification(Long recipientId,
                                           NotificationType type,
                                           String title,
                                           String message,
                                           Long relatedUserId,
                                           Long weddingId,
                                           Long matchId,
                                           Long chatMessageId,
                                           String metadata) {

        return createNotification(
                recipientId,
                type,
                title,
                message,
                relatedUserId,
                weddingId,
                matchId,
                chatMessageId,
                metadata,
                null,
                "system",
                1
        );
    }

    // =====================================================================
    // 2️⃣ בדיקה האם לשלוח התראה למשתמש
    // =====================================================================

    private boolean shouldSendTo(User user) {
        return user.isAllowInAppNotifications() &&
                !user.isPushDisabled();
    }

    // =====================================================================
    // 3️⃣ התראות לייקים / התאמות / פרופיל / גלובלי
    // =====================================================================

    public void notifyLikeReceived(Long recipientId,
                                   Long fromUserId,
                                   Long weddingId,
                                   Long matchId) {

        User sender = (fromUserId != null)
                ? userRepository.findById(fromUserId).orElse(null)
                : null;

        String name = sender != null ? sender.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.LIKE_RECEIVED,
                "קיבלת לייק חדש",
                name + " שלח/ה לך לייק",
                fromUserId,
                weddingId,
                matchId,
                null,
                null,
                "match",
                "system",
                1
        );
    }

    public void notifyMatchApproved(Long matchId,
                                    Long recipientUserId,
                                    Long otherUserId) {

        User other = userRepository.findById(otherUserId).orElse(null);
        String name = other != null ? other.getFullName() : "הצד השני";

        createNotification(
                recipientUserId,
                NotificationType.MATCH_CONFIRMED,
                "התאמה אושרה",
                name + " אישר/ה את ההתאמה. כעת ממתינים לאישור שלך.",
                otherUserId,
                null,
                matchId,
                null,
                null,
                "match",
                "system",
                2
        );
    }

    public void notifyMatchMutual(Long matchId,
                                  Long recipientUserId,
                                  Long otherUserId) {

        User other = userRepository.findById(otherUserId).orElse(null);
        String name = (other != null ? other.getFullName() : "הצד השני");

        createNotification(
                recipientUserId,
                NotificationType.MATCH_MUTUAL,
                "יש התאמה הדדית!",
                "את/ה ו-" + name + " אישרתם אחד את השני. הצ'אט פתוח.",
                otherUserId,
                null,
                matchId,
                null,
                null,
                "match",
                "system",
                3
        );
    }

    public void sendProfileIncompleteReminder(Long userId) {
        createNotification(
                userId,
                NotificationType.PROFILE_INCOMPLETE_REMINDER,
                "השלמת הפרופיל שלך",
                "עדיין לא השלמת פרופיל מלא. זה משפר את סיכויי ההתאמות.",
                null,
                null,
                null,
                null,
                null,
                "profile",
                "system",
                1
        );
    }

    public void notifyFullProfileCompleted(Long userId) {
        createNotification(
                userId,
                NotificationType.PROFILE_COMPLETED,
                "הפרופיל הושלם",
                "מעתה הפרופיל שלך מוצג בצורה מיטבית.",
                null,
                null,
                null,
                null,
                null,
                "profile",
                "system",
                1
        );
    }

    public void notifyGlobalAccessRequest(Long userId) {
        createNotification(
                userId,
                NotificationType.GLOBAL_ACCESS_REQUESTED,
                "בקשת גישה למאגר הכללי",
                "הבקשה נשלחה ותיבדק בקרוב.",
                null,
                null,
                null,
                null,
                null,
                "system",
                "system",
                2
        );
    }

    public void notifyGlobalAccessApproved(Long userId) {
        createNotification(
                userId,
                NotificationType.GLOBAL_ACCESS_APPROVED,
                "אושר! יש לך גישה למאגר הכללי",
                "כעת תוכלו להופיע ולהיראות בכל המערכת.",
                null,
                null,
                null,
                null,
                null,
                "system",
                "admin",
                3
        );
    }

    public void notifyEnteredGlobalPool(Long userId) {
        createNotification(
                userId,
                NotificationType.ENTERED_GLOBAL_POOL,
                "נכנסת למאגר הכללי",
                "מעכשיו פרופילך מופיע גם במאגר הכללי.",
                null,
                null,
                null,
                null,
                null,
                "system",
                "system",
                2
        );
    }

    // 📌 NEW — לפי אפיון 2025: סיכום צפיות בפרופיל
    public void notifyProfileViewsSummary(Long userId,
                                          String period,
                                          int viewsCount) {

        String safePeriod = (period == null || period.isBlank())
                ? "בשבוע האחרון"
                : period;

        String metadata = "{ \"period\":\"" + safePeriod + "\", \"viewsCount\":" + viewsCount + "}";

        createNotification(
                userId,
                NotificationType.PROFILE_VIEWS_SUMMARY,
                "סיכום צפיות בפרופיל – " + safePeriod,
                "ב" + safePeriod + " צפו בפרופיל שלך " + viewsCount + " אנשים.",
                null,
                null,
                null,
                null,
                metadata,
                "profile",
                "system",
                1
        );
    }

    // 📌 NEW – לפי אפיון 2025: אישור תמונת פרופיל
    public void notifyProfilePhotoApproved(Long userId) {

        createNotification(
                userId,
                NotificationType.PROFILE_PHOTO_APPROVED,
                "תמונת הפרופיל אושרה",
                "תמונת הפרופיל שלך נבדקה ואושרה.",
                null,
                null,
                null,
                null,
                null,
                "profile",
                "admin",
                1
        );
    }

    // 📌 NEW – לפי אפיון 2025: דחיית תמונת פרופיל
    public void notifyProfilePhotoRejected(Long userId, String reason) {

        String metadata = (reason != null && !reason.isBlank())
                ? "{ \"reason\":\"" + reason + "\" }"
                : null;

        createNotification(
                userId,
                NotificationType.PROFILE_PHOTO_REJECTED,
                "תמונת הפרופיל נדחתה",
                (reason != null ? "סיבה: " + reason : "התמונה נדחתה. אנא העלה/י תמונה תקינה."),
                null,
                null,
                null,
                null,
                metadata,
                "profile",
                "admin",
                2
        );
    }

    // =====================================================================
    // 4️⃣ התראות חתונה / בעל אירוע / QR / סוף חתונה
    // =====================================================================

    // משתמש נכנס לחתונה (סרק QR)
    public void notifyWeddingEntry(Long userId, Long weddingId) {
        createNotification(
                userId,
                NotificationType.WEDDING_ENTRY,
                "ברוך הבא לחתונה",
                "נכנסת לאירוע. עכשיו אפשר לראות ולהיראות בין המשתתפים.",
                null,
                weddingId,
                null,
                null,
                null,
                "wedding",
                "system",
                1
        );
    }

    // החתונה הסתיימה (למשתמש)
    public void notifyWeddingEndedForUser(Long userId, Long weddingId) {
        createNotification(
                userId,
                NotificationType.WEDDING_ENDED,
                "האירוע הסתיים",
                "החתונה הסתיימה. עדיין ניתן לראות התאמות שנוצרו כאן.",
                null,
                weddingId,
                null,
                null,
                null,
                "wedding",
                "system",
                1
        );
    }

    // בעל אירוע — משתמש חדש נכנס לחתונה
    public void notifyOwnerNewUserJoinedWedding(Long ownerUserId,
                                                Long participantUserId,
                                                Long weddingId) {

        User p = userRepository.findById(participantUserId).orElse(null);
        String name = p != null ? p.getFullName() : "משתמש חדש";

        createNotification(
                ownerUserId,
                NotificationType.WEDDING_OWNER_NEW_PARTICIPANT,
                "משתמש חדש נכנס לאירוע",
                name + " הצטרף/ה לחתונה שלך.",
                participantUserId,
                weddingId,
                null,
                null,
                null,
                "wedding",
                "wedding-owner",
                1
        );
    }

    // בעל אירוע — משתתף השלים פרופיל מלא
    public void notifyOwnerUserCompletedProfile(Long ownerUserId,
                                                Long participantUserId,
                                                Long weddingId) {

        User p = userRepository.findById(participantUserId).orElse(null);
        String name = p != null ? p.getFullName() : "משתתף";

        createNotification(
                ownerUserId,
                NotificationType.WEDDING_OWNER_PROFILE_COMPLETED,
                "משתמש השלים פרופיל",
                name + " השלים/ה פרופיל מלא באירוע שלך.",
                participantUserId,
                weddingId,
                null,
                null,
                null,
                "wedding",
                "wedding-owner",
                1
        );
    }

    // בעל אירוע — התאמה חדשה נוצרה בחתונה
    public void notifyOwnerNewMatchInWedding(Long ownerUserId,
                                             Long matchId,
                                             Long userAId,
                                             Long userBId,
                                             Long weddingId) {

        String meta = "{\"userAId\":" + userAId + ",\"userBId\":" + userBId + "}";

        createNotification(
                ownerUserId,
                NotificationType.WEDDING_OWNER_NEW_MATCH,
                "התאמה חדשה באירוע",
                "נוצרה התאמה חדשה בין שני משתתפים.",
                null,
                weddingId,
                matchId,
                null,
                meta,
                "wedding",
                "wedding-owner",
                2
        );
    }

    // בעל אירוע — מישהו ביקש גישה גלובלית מתוך החתונה
    public void notifyOwnerGlobalAccessRequest(Long ownerUserId,
                                               Long participantUserId,
                                               Long weddingId) {

        User p = userRepository.findById(participantUserId).orElse(null);
        String name = p != null ? p.getFullName() : "משתתף";

        createNotification(
                ownerUserId,
                NotificationType.WEDDING_OWNER_GLOBAL_REQUEST,
                "בקשת גישה גלובלית",
                name + " ביקש/ה גישה למאגר הכללי מתוך האירוע.",
                participantUserId,
                weddingId,
                null,
                null,
                null,
                "wedding",
                "wedding-owner",
                2
        );
    }


    // =====================================================================
    // 5️⃣ התראות צ'אט / Opening Messages
    // =====================================================================

    // הודעת צ'אט רגילה
    public void notifyChatMessageReceived(Long recipientId,
                                          Long fromUserId,
                                          Long matchId,
                                          Long chatMessageId) {

        User sender = fromUserId != null
                ? userRepository.findById(fromUserId).orElse(null)
                : null;

        String name = sender != null ? sender.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                "הודעה חדשה בצ'אט",
                name + " שלח/ה לך הודעה.",
                fromUserId,
                null,
                matchId,
                chatMessageId,
                null,
                "chat",
                "system",
                2
        );
    }

    // Opening Message התקבלה
    public void notifyFirstMessageReceived(Long recipientId,
                                           Long fromUserId,
                                           Long matchId,
                                           Long chatMessageId) {

        User sender = fromUserId != null
                ? userRepository.findById(fromUserId).orElse(null)
                : null;

        String name = sender != null ? sender.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.FIRST_MESSAGE_RECEIVED,
                "קיבלת הודעה ראשונית",
                name + " שלח/ה לך הודעה ראשונית. אפשר לאשר או לדחות.",
                fromUserId,
                null,
                matchId,
                chatMessageId,
                null,
                "chat",
                "system",
                2
        );
    }

    // Opening אושר → צ'אט נפתח
    public void notifyFirstMessageAccepted(Long recipientId,
                                           Long otherUserId,
                                           Long matchId) {

        User other = userRepository.findById(otherUserId).orElse(null);
        String name = other != null ? other.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.FIRST_MESSAGE_ACCEPTED,
                "ההודעה הראשונית אושרה",
                name + " אישר/ה את הפנייה. הצ'אט ביניכם נפתח.",
                otherUserId,
                null,
                matchId,
                null,
                null,
                "chat",
                "system",
                3
        );
    }

    // Opening נדחה
    public void notifyFirstMessageRejected(Long recipientId,
                                           Long otherUserId,
                                           Long matchId) {

        User other = userRepository.findById(otherUserId).orElse(null);
        String name = other != null ? other.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.FIRST_MESSAGE_REJECTED,
                "ההודעה הראשונית נדחתה",
                name + " בחר/ה שלא לפתוח צ'אט מהודעה זו.",
                otherUserId,
                null,
                matchId,
                null,
                null,
                "chat",
                "system",
                1
        );
    }


    // =====================================================================
    // 6️⃣ פעולות משתמש: Dislike / Freeze / Unfreeze
    // =====================================================================

    public void notifyUserDisliked(Long recipientId, Long byUserId) {
        User other = userRepository.findById(byUserId).orElse(null);
        String name = other != null ? other.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.USER_DISLIKED,
                "משתמש בחר שלא להתקדם",
                name + " סימן/ה שלא מעוניין/ת כרגע.",
                byUserId,
                null,
                null,
                null,
                null,
                "match",
                "system",
                1
        );
    }

    public void notifyUserFreezeApplied(Long recipientId, Long byUserId) {
        User other = userRepository.findById(byUserId).orElse(null);
        String name = other != null ? other.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.USER_FROZEN,
                "הפרופיל שלך מוקפא",
                name + " סימן/ה אותך ברשימת 'מקפיא'.",
                byUserId,
                null,
                null,
                null,
                null,
                "match",
                "system",
                1
        );
    }

    public void notifyUserUnfreezeApplied(Long recipientId, Long byUserId) {
        User other = userRepository.findById(byUserId).orElse(null);
        String name = other != null ? other.getFullName() : "משתמש";

        createNotification(
                recipientId,
                NotificationType.USER_UNFROZEN,
                "הפרופיל שלך הוסר מהקפאה",
                name + " הוציא/ה אותך מרשימת 'מקפיא'.",
                byUserId,
                null,
                null,
                null,
                null,
                "match",
                "system",
                1
        );
    }


    // =====================================================================
    // 7️⃣ AI – התאמה מומלצת
    // =====================================================================

    public void notifyAISuggestedMatch(Long recipientId,
                                       Long suggestedUserId,
                                       String explanation) {

        String meta = "{\"suggestedUserId\":" + suggestedUserId + "}";

        createNotification(
                recipientId,
                NotificationType.AI_SUGGESTED_MATCH,   // ← לפי האפיון החדש
                "המלצת התאמה חכמה",
                (explanation != null && !explanation.isBlank())
                        ? explanation
                        : "המערכת מצאה התאמה שעשויה להתאים לך.",
                suggestedUserId,
                null,
                null,
                null,
                meta,
                "match",
                "ai",
                2
        );
    }

    // =====================================================================
    // 8️⃣ שליפות / ספירה למשתמש – כולל קטגוריה/סוג/עדיפות
    // =====================================================================

    /** 50 ההתראות האחרונות למשתמש (פיד התראות). */
    public List<Notification> getLatestNotificationsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found " + userId));

        return notificationRepository
                .findTop50ByRecipientOrderByCreatedAtDesc(user)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** כל ההתראות למשתמש (מהחדש לישן). */
    public List<Notification> getAllNotificationsForUser(Long userId) {
        User user = userRepository.getReferenceById(userId);

        return notificationRepository.findByRecipient(user)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** כל ההתראות שלא נקראו למשתמש. */
    public List<Notification> getUnreadNotificationsForUser(Long userId) {
        User user = userRepository.getReferenceById(userId);

        return notificationRepository.findByRecipientAndReadFalse(user)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** התראות לפי קטגוריה (match/chat/system/profile/wedding). */
    public List<Notification> getNotificationsByCategory(Long userId, String category) {
        return notificationRepository
                .findByRecipientIdAndCategory(userId, category)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** לפי סוג NotificationType. */
    public List<Notification> getNotificationsByType(Long userId, NotificationType type) {
        User user = userRepository.getReferenceById(userId);

        return notificationRepository.findByRecipientAndType(user, type)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** לפי עדיפות 1–3. */
    public List<Notification> getNotificationsByPriorityLevel(Long userId, int priorityLevel) {
        return notificationRepository.findByRecipientIdAndPriorityLevel(userId, priorityLevel)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** ספירת התראות לא נקראו. */
    public long countUnreadNotificationsForUser(Long userId) {
        return notificationRepository
                .findByRecipientIdAndReadFalse(userId)
                .stream()
                .filter(n -> !n.isDeleted())
                .count();
    }


    // =====================================================================
    // 9️⃣ פופאפים — popupSeen
    // =====================================================================

    /** התראות שלא נצפו כפופאפ. */
    public List<Notification> getUnseenPopupsForUser(Long userId) {
        return notificationRepository.findByRecipientIdAndPopupSeenFalse(userId)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** סימון פופאפ בודד כ"נצפה". */
    public void markNotificationPopupSeen(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.isDeleted()
                    && !n.isPopupSeen()
                    && n.getRecipient().getId().equals(userId)) {

                n.setPopupSeen(true);
                notificationRepository.save(n);
            }
        });
    }

    /** סימון כל הפופאפים כ"נצפו". */
    public void markAllPopupsSeenForUser(Long userId) {
        List<Notification> list =
                notificationRepository.findByRecipientIdAndPopupSeenFalse(userId);

        for (Notification n : list) {
            if (!n.isDeleted()) {
                n.setPopupSeen(true);
                notificationRepository.save(n);
            }
        }
    }


    // =====================================================================
    // 🔟 סימון כנקרא / מחיקה לוגית / מחיקה פיזית
    // =====================================================================

    /** סימון התראה בודדת כנקראה. */
    public void markNotificationAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.isDeleted()
                    && !n.isRead()
                    && n.getRecipient() != null
                    && n.getRecipient().getId().equals(userId)) {

                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    /** סימון כל ההתראות של משתמש כנקראו. */
    public void markAllNotificationsAsReadForUser(Long userId) {
        List<Notification> list = notificationRepository.findByRecipientIdAndReadFalse(userId);

        for (Notification n : list) {
            if (!n.isDeleted()) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        }
    }

    /** מחיקה לוגית — נשאר ב־DB לצרכי סטטיסטיקה. */
    public void softDeleteNotification(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipient() != null
                    && n.getRecipient().getId().equals(userId)) {

                n.setDeleted(true);
                notificationRepository.save(n);
            }
        });
    }

    /** מחיקה פיזית — לאדמין בלבד. */
    public void hardDeleteNotification(Long notificationId) {
        if (notificationRepository.existsById(notificationId)) {
            notificationRepository.deleteById(notificationId);
        }
    }

    /** מחיקת התראות לפני תאריך מסוים (פיזי). */
    public void deleteNotificationsBefore(LocalDateTime threshold) {
        List<Notification> list = notificationRepository.findByCreatedAtBefore(threshold);
        notificationRepository.deleteAll(list);
    }

    /** מחיקה לוגית של התראות ישנות. */
    public void softDeleteOldNotifications(LocalDateTime threshold) {
        List<Notification> list = notificationRepository.findByCreatedAtBefore(threshold);

        for (Notification n : list) {
            if (!n.isDeleted()) {
                n.setDeleted(true);
                notificationRepository.save(n);
            }
        }
    }

    /** ניקוי פופאפים ישנים. */
    public void cleanOldPopups(LocalDateTime threshold) {
        List<Notification> list = notificationRepository.findByCreatedAtBefore(threshold);

        for (Notification n : list) {
            if (!n.isDeleted() && n.isPopupSeen()) {
                n.setDeleted(true);
                notificationRepository.save(n);
            }
        }
    }

    /** ניקוי הודעות מערכת עתיקות. */
    public void cleanOldSystemAnnouncements(LocalDateTime threshold) {
        List<Notification> list = notificationRepository.findByType(NotificationType.SYSTEM_ANNOUNCEMENT);

        for (Notification n : list) {
            if (n.getCreatedAt() != null
                    && n.getCreatedAt().isBefore(threshold)) {

                n.setDeleted(true);
                notificationRepository.save(n);
            }
        }
    }


    // =====================================================================
    // 1️⃣1️⃣ שליפות ל־Admin / סטטיסטיקה
    // =====================================================================

    /** כל ההתראות שלא נקראו במערכת — לאדמין. */
    public List<Notification> getAllUnreadNotificationsForAdmin() {
        return notificationRepository.findByReadFalse()
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** התראות שנמחקו לוגית. */
    public List<Notification> getDeletedNotificationsForAdmin() {
        return notificationRepository.findByDeletedTrue();
    }

    /** כל ההתראות של חתונה מסוימת. */
    public List<Notification> getNotificationsForWedding(Long weddingId) {
        return notificationRepository.findByWeddingId(weddingId)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** כל ההתראות של Match מסוים. */
    public List<Notification> getNotificationsForMatch(Long matchId) {
        return notificationRepository.findByMatchId(matchId)
                .stream()
                .filter(n -> !n.isDeleted())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

}