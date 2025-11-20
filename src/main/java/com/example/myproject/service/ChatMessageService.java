package com.example.myproject.service;

import com.example.myproject.model.*;
import com.example.myproject.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service                                    // מחלקת Service של Spring
@Transactional                              // כל פעולות ה־DB מתבצעות בטרנזקציה
public class ChatMessageService {

    // ============================================================
    // 🔵 תלות בריפוזיטוריס
    // ============================================================

    private final ChatMessageRepository chatRepo;     // הודעות
    private final UserRepository userRepo;            // משתמשים
    private final MatchRepository matchRepo;          // התאמות
    private final WeddingRepository weddingRepo;      // חתונות
    private final NotificationRepository notifRepo;   // התראות

    public ChatMessageService(ChatMessageRepository chatRepo,
                              UserRepository userRepo,
                              MatchRepository matchRepo,
                              WeddingRepository weddingRepo,
                              NotificationRepository notifRepo) {

        this.chatRepo = chatRepo;           // הזרקות תלויות
        this.userRepo = userRepo;
        this.matchRepo = matchRepo;
        this.weddingRepo = weddingRepo;
        this.notifRepo = notifRepo;
    }

    // ============================================================
    // 🔵 מחלקות עזר פנימיות – ולידציות
    // ============================================================

    /** בדיקות בסיס לשליחת כל הודעה */
    private void validateSendMessage(Long senderId,
                                     Long recipientId,
                                     String content) {

        if (senderId == null || recipientId == null)
            throw new IllegalArgumentException("Sender/Recipient cannot be null.");

        if (senderId.equals(recipientId))
            throw new IllegalArgumentException("Cannot send a message to yourself.");

        if (content == null || content.trim().isEmpty())
            throw new IllegalArgumentException("Message content cannot be empty.");
    }

    /** בדיקה לעמידה בדרישות פרופיל לפני Opening Message */
    private void validateOpeningProfile(User sender) {

        if (!sender.isBasicProfileCompleted())
            throw new IllegalStateException("You must complete your profile before sending an opening message.");

        if (sender.getPhotoUrls() == null || sender.getPhotoUrls().isEmpty())
            throw new IllegalStateException("A profile photo is required before sending an opening message.");
    }

    // ============================================================
    // 🔵 1. שליחת הודעה רגילה בתוך Match קיים
    // ============================================================

    public ChatMessage sendMessage(Long senderId,
                                   Long recipientId,
                                   Long matchId,
                                   Long weddingId,
                                   String content) {

        validateSendMessage(senderId, recipientId, content);        // בדיקות בסיס

        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User recipient = userRepo.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        // 1️⃣ מציאת Match תקין
        Match match = resolveMatch(senderId, recipientId, matchId);

        if (!match.isActive() || !match.isMutualApproved() || match.isBlocked() || match.isFrozen())
            throw new IllegalStateException("Chat is not allowed for this match.");

        // 2️⃣ זיהוי החתונה
        Wedding wedding = resolveWeddingFromRequestOrMatch(weddingId, match);

        // 3️⃣ יצירת הודעה חדשה
        ChatMessage msg = new ChatMessage();
        msg.setSender(sender);
        msg.setRecipient(recipient);
        msg.setMatch(match);
        msg.setWedding(wedding);
        msg.setContent(content.trim());
        msg.setMessageType("text");
        msg.setOpeningMessage(false);
        msg.setSystemMessage(false);
        msg.setDelivered(false);                       // עד ש־WebSocket יאשר
        msg.setCreatedAt(LocalDateTime.now());

        ChatMessage saved = chatRepo.save(msg);

        // 4️⃣ עדכון מונה הודעות לא נקראו ב־Match
        updateMatchUnreadCount(match);

        // 5️⃣ יצירת התראה
        createNotificationForMessage(sender, recipient, saved, match, wedding);

        return saved;
    }

    /**
     * אוברלואד עם deviceType – אם תרצה מהקונטרולר להעביר מאיזה מכשיר נשלחה ההודעה.
     */
    public ChatMessage sendMessage(Long senderId,
                                   Long recipientId,
                                   Long matchId,
                                   Long weddingId,
                                   String content,
                                   String deviceType) {

        ChatMessage msg = sendMessage(senderId, recipientId, matchId, weddingId, content);
        msg.setDeviceType(deviceType);
        msg.setUpdatedAt(LocalDateTime.now());
        return chatRepo.save(msg);
    }

    // ============================================================
    // 🔵 2. פונקציה פנימית — מציאת Match
    // ============================================================

    private Match resolveMatch(Long senderId, Long recipientId, Long matchId) {

        if (matchId != null) {   // אם נשלח matchId
            Match m = matchRepo.findById(matchId)
                    .orElseThrow(() -> new IllegalArgumentException("Match not found"));

            Long u1 = m.getUser1().getId();
            Long u2 = m.getUser2().getId();

            if (!senderId.equals(u1) && !senderId.equals(u2))
                throw new IllegalStateException("Sender does not belong to this match.");

            if (!recipientId.equals(u1) && !recipientId.equals(u2))
                throw new IllegalStateException("Recipient does not belong to this match.");

            return m;
        }

        // אין matchId → נמצא התאמה קיימת אם קיימת
        Optional<Match> existing =
                matchRepo.findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                        senderId, recipientId,
                        recipientId, senderId
                );

        if (existing.isEmpty())
            throw new IllegalStateException("No match exists — opening messages only.");

        return existing.get();
    }

    // ============================================================
    // 🔵 3. פונקציה פנימית — זיהוי חתונה
    // ============================================================

    private Wedding resolveWeddingFromRequestOrMatch(Long weddingId, Match match) {

        if (weddingId != null) {
            return weddingRepo.findById(weddingId)
                    .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));
        }

        if (match.getMeetingWeddingId() != null) {
            Long wid = match.getMeetingWeddingId();
            return weddingRepo.findById(wid).orElse(null);
        }

        return null;
    }

    // ============================================================
    // 🔵 4. התראה על הודעה רגילה
    // ============================================================

    private void createNotificationForMessage(User sender,
                                              User recipient,
                                              ChatMessage msg,
                                              Match match,
                                              Wedding wedding) {

        if (!recipient.isAllowInAppNotifications() || recipient.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(recipient);
        notif.setType(NotificationType.MESSAGE_RECEIVED);
        notif.setTitle("הודעה חדשה מ" + sender.getFullName());
        notif.setMessage(msg.getContent());
        notif.setRelatedUserId(sender.getId());
        notif.setMatchId(match != null ? match.getId() : null);
        notif.setWeddingId(wedding != null ? wedding.getId() : null);
        notif.setChatMessageId(msg.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // ============================================================
    // 🔵 5. Opening Message – שליחת הודעה ראשונית
    // ============================================================

    public ChatMessage sendOpeningMessage(Long senderId,
                                          Long recipientId,
                                          String content) {

        validateSendMessage(senderId, recipientId, content);     // בדיקות בסיס

        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User recipient = userRepo.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        validateOpeningProfile(sender);                         // דרישות פרופיל 2025

        // אסור לשלוח Opening אם יש Match
        boolean hasMatch =
                matchRepo.existsByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                        senderId, recipientId,
                        recipientId, senderId
                );

        if (hasMatch)
            throw new IllegalStateException("Cannot send opening message when match already exists.");

        // אסור לשלוח Opening פעמיים
        boolean alreadySentOpening =
                chatRepo.existsBySenderIdAndRecipientIdAndOpeningMessageTrue(senderId, recipientId);

        if (alreadySentOpening)
            throw new IllegalStateException("Opening message already sent.");

        // יצירת הודעה ראשונית
        ChatMessage msg = new ChatMessage();
        msg.setSender(sender);
        msg.setRecipient(recipient);
        msg.setOpeningMessage(true);
        msg.setMessageType("text");
        msg.setSystemMessage(false);
        msg.setContent(content.trim());
        msg.setDelivered(false);
        msg.setCreatedAt(LocalDateTime.now());

        ChatMessage saved = chatRepo.save(msg);

        // התראה למקבל
        createNotificationOpeningReceived(sender, recipient, saved);

        // התראה לשולח
        createNotificationOpeningSent(sender, recipient, saved);

        return saved;
    }

    // ============================================================
    // 🔵 6. התראות להודעות ראשוניות
    // ============================================================

    /** התראה למקבל Opening */
    private void createNotificationOpeningReceived(User sender,
                                                   User recipient,
                                                   ChatMessage msg) {

        if (!recipient.isAllowInAppNotifications() || recipient.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(recipient);
        notif.setType(NotificationType.FIRST_MESSAGE_RECEIVED);
        notif.setTitle("פנייה חדשה מ" + sender.getFullName());
        notif.setMessage(msg.getContent());
        notif.setRelatedUserId(sender.getId());
        notif.setChatMessageId(msg.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    /** התראה לשולח Opening */
    private void createNotificationOpeningSent(User sender,
                                               User recipient,
                                               ChatMessage msg) {

        if (!sender.isAllowInAppNotifications() || sender.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(sender);
        notif.setType(NotificationType.FIRST_MESSAGE_SENT);
        notif.setTitle("הפנייה נשלחה בהצלחה");
        notif.setMessage("הפנייה נשלחה אל " + recipient.getFullName());
        notif.setRelatedUserId(recipient.getId());
        notif.setChatMessageId(msg.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // ============================================================
    // 🔵 7. Opening Messages – שליפות ואישור/דחייה
    // ============================================================

    /**
     * כל ההודעות הראשוניות שממתינות לאישור אצל משתמש.
     * כולל רק:
     *  - openingMessage = true
     *  - match = null
     *  - deleted = false
     *  - ממויין מהחדש לישן
     */
    public List<ChatMessage> getPendingOpeningMessages(Long userId) {
        return chatRepo
                .findByRecipientIdAndOpeningMessageTrueAndMatchIsNullAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * אישור הודעה ראשונית:
     *  - בדיקה שהנמען הוא המאשר.
     *  - יצירת Match חדש (אם אין).
     *  - עדכון ההודעה כך שתשתייך למאץ' ולא תיחשב opening.
     *  - שליחת התראות לשני הצדדים (CHAT_APPROVED + MATCH_MUTUAL + FIRST_MESSAGE_ACCEPTED).
     */
    public Match approveOpeningMessage(Long messageId, Long recipientId) {

        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!msg.isOpeningMessage())
            throw new IllegalStateException("This is not an opening message.");

        if (msg.getRecipient() == null || !msg.getRecipient().getId().equals(recipientId))
            throw new IllegalStateException("Only recipient can approve this message.");

        Long senderId = msg.getSender().getId();
        Long recId    = msg.getRecipient().getId();

        // אם בזמן ההמתנה נוצר כבר Match — לא ניצור כפול, רק נחבר את ההודעה אליו
        Optional<Match> existing =
                matchRepo.findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                        senderId, recId,
                        recId, senderId
                );

        Match match;
        if (existing.isPresent()) {
            match = existing.get();
        } else {
            // יצירת Match חדש גלובלי (מקור: opening)
            match = new Match();
            match.setUser1(msg.getSender());
            match.setUser2(msg.getRecipient());
            match.setMatchScore(0.0);                // ניקוד ברירת מחדל – פנייה יזומה
            match.setMatchSource("opening");         // מקור לפי אפיון
            match.setActive(true);
            match.setBlocked(false);
            match.setFrozen(false);
            match.setMutualApproved(true);           // שני הצדדים למעשה בפנים
            match.setChatOpened(true);               // צ'אט נפתח מיידית
            match.setCreatedAt(LocalDateTime.now());
            match.setUpdatedAt(LocalDateTime.now());
            match.setUnreadCount(0);                 // יתחיל מ-0

            match = matchRepo.save(match);
        }

        // החיבור בין ההודעה למאץ'
        msg.setMatch(match);
        msg.setOpeningMessage(false);                // כבר לא נחשבת opening
        msg.setUpdatedAt(LocalDateTime.now());
        chatRepo.save(msg);

        // 🔔 התראה לשולח – הפנייה אושרה (CHAT_APPROVED + FIRST_MESSAGE_ACCEPTED)
        createNotificationChatApproved(msg.getSender(), msg.getRecipient(), match);
        createNotificationFirstMessageAccepted(msg.getSender(), msg.getRecipient(), match);

        // 🔔 התראה למאשר – נוצר מאץ' הדדי (MATCH_MUTUAL)
        createNotificationMatchMutual(msg.getRecipient(), msg.getSender(), match);

        return match;
    }

    /**
     * דחיית הודעה ראשונית:
     *  - רק הנמען יכול לדחות.
     *  - מחיקה לוגית בלבד (deleted=true).
     *  - שליחת התראה לשולח שהפנייה נדחתה (CHAT_DECLINED + FIRST_MESSAGE_REJECTED).
     */
    public void rejectOpeningMessage(Long messageId, Long recipientId) {

        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!msg.isOpeningMessage())
            throw new IllegalStateException("This is not an opening message.");

        if (msg.getRecipient() == null || !msg.getRecipient().getId().equals(recipientId))
            throw new IllegalStateException("Only recipient can reject this message.");

        msg.setDeleted(true);
        msg.setUpdatedAt(LocalDateTime.now());
        chatRepo.save(msg);

        // 🔔 התראה לשולח – צ'אט נדחה
        createNotificationChatDeclined(msg.getSender(), msg.getRecipient());
        createNotificationFirstMessageRejected(msg.getSender(), msg.getRecipient());
    }

    // התראה: צ'אט אושר (אישור opening → פתיחת צ'אט)
    private void createNotificationChatApproved(User sender,
                                                User recipient,
                                                Match match) {

        if (!sender.isAllowInAppNotifications() || sender.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(sender);                      // מי מקבל את ההתראה? השולח המקורי
        notif.setType(NotificationType.CHAT_APPROVED);
        notif.setTitle(recipient.getFullName() + " אישר/ה את הפנייה שלך");
        notif.setMessage("נפתח צ'אט חדש ביניכם.");
        notif.setRelatedUserId(recipient.getId());
        notif.setMatchId(match.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // התראה: צ'אט נדחה
    private void createNotificationChatDeclined(User sender,
                                                User recipient) {

        if (!sender.isAllowInAppNotifications() || sender.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(sender);
        notif.setType(NotificationType.CHAT_DECLINED);
        notif.setTitle(recipient.getFullName() + " דחה/תה את הפנייה");
        notif.setMessage("אפשר לנסות לפנות לאנשים אחרים שמתאימים לך.");
        notif.setRelatedUserId(recipient.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // התראה: First Message Accepted
    private void createNotificationFirstMessageAccepted(User sender,
                                                        User recipient,
                                                        Match match) {

        if (!sender.isAllowInAppNotifications() || sender.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(sender);
        notif.setType(NotificationType.FIRST_MESSAGE_ACCEPTED);
        notif.setTitle(recipient.getFullName() + " אישר/ה את הפנייה שלך");
        notif.setMessage("הפנייה נפתחה לצ'אט פעיל.");
        notif.setRelatedUserId(recipient.getId());
        notif.setMatchId(match.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // התראה: First Message Rejected
    private void createNotificationFirstMessageRejected(User sender,
                                                        User recipient) {

        if (!sender.isAllowInAppNotifications() || sender.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(sender);
        notif.setType(NotificationType.FIRST_MESSAGE_REJECTED);
        notif.setTitle(recipient.getFullName() + " דחה/תה את הפנייה");
        notif.setMessage("אפשר לנסות לפנות לאנשים אחרים שמתאימים לך.");
        notif.setRelatedUserId(recipient.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // התראה נוספת – MATCH_MUTUAL (אופציונלי, לפי אפיון 2025)
    private void createNotificationMatchMutual(User user,
                                               User otherSide,
                                               Match match) {

        if (!user.isAllowInAppNotifications() || user.isPushDisabled())
            return;

        Notification notif = new Notification();
        notif.setRecipient(user);
        notif.setType(NotificationType.MATCH_MUTUAL);
        notif.setTitle("יש התאמה הדדית עם " + otherSide.getFullName());
        notif.setMessage("הצ'אט ביניכם פתוח כעת.");
        notif.setRelatedUserId(otherSide.getId());
        notif.setMatchId(match.getId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);

        notifRepo.save(notif);
    }

    // ============================================================
    // 🔵 8. שליפות שיחה / הודעות לפי Match / חתונה / משתמש
    // ============================================================

    /**
     * כל השיחה הדו-כיוונית בין שני משתמשים, בלי קשר ל-Match ספציפי.
     */
    public List<ChatMessage> getConversation(Long user1Id, Long user2Id) {

        List<ChatMessage> list =
                chatRepo.findBySenderIdAndRecipientIdOrSenderIdAndRecipientId(
                        user1Id, user2Id,
                        user2Id, user1Id
                );

        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));  // מהישן לחדש
        return list;
    }

    /**
     * כל ההודעות של Match מסוים (צ'אט התאמה).
     */
    public List<ChatMessage> getMessagesByMatch(Long matchId) {

        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        List<ChatMessage> list = chatRepo.findByMatch(match);
        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return list;
    }

    /**
     * כל ההודעות הקשורות לחתונה מסוימת (Wedding Chat / הודעות הקשר אירועי).
     */
    public List<ChatMessage> getMessagesByWedding(Long weddingId) {

        Wedding w = weddingRepo.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found"));

        List<ChatMessage> list = chatRepo.findByWedding(w);
        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return list;
    }

    /**
     * 50 ההודעות האחרונות של משתמש (שלח/קיבל).
     */
    public List<ChatMessage> getRecentMessages(Long userId) {
        List<ChatMessage> list =
                chatRepo.findTop50BySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId);
        list.removeIf(ChatMessage::isDeleted);
        return list;
    }

    /**
     * 20 ההודעות האחרונות ש-A שלח ל-B.
     */
    public List<ChatMessage> getLast20Sent(Long senderId, Long recipientId) {
        List<ChatMessage> list =
                chatRepo.findTop20BySenderIdAndRecipientIdOrderByCreatedAtDesc(senderId, recipientId);
        list.removeIf(ChatMessage::isDeleted);
        return list;
    }

    /**
     * ההודעה האחרונה בין שני משתמשים (בכל כיוון).
     */
    public Optional<ChatMessage> getLastMessageBetween(Long u1, Long u2) {

        ChatMessage msg1 = chatRepo.findTop1BySenderIdAndRecipientIdOrderByCreatedAtDesc(u1, u2);
        ChatMessage msg2 = chatRepo.findTop1BySenderIdAndRecipientIdOrderByCreatedAtDesc(u2, u1);

        if (msg1 != null && msg1.isDeleted()) msg1 = null;
        if (msg2 != null && msg2.isDeleted()) msg2 = null;

        if (msg1 == null && msg2 == null) return Optional.empty();
        if (msg1 == null) return Optional.of(msg2);
        if (msg2 == null) return Optional.of(msg1);

        return Optional.of(
                msg1.getCreatedAt().isAfter(msg2.getCreatedAt()) ? msg1 : msg2
        );
    }

    // ============================================================
    // 🔵 9. הודעות שלא נקראו (Unread)
    // ============================================================

    /**
     * כל ההודעות הלא נקראות עבור משתמש (ללא deleted).
     */
    public List<ChatMessage> getUnreadMessages(Long userId) {

        List<ChatMessage> list = chatRepo.findByRecipientIdAndReadFalse(userId);
        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return list;
    }

    /**
     * ספירת הודעות לא נקראו.
     */
    public long countUnread(Long userId) {
        return getUnreadMessages(userId).size();
    }

    /**
     * הודעות לא נקראות בצ'אט של Match מסוים.
     */
    public List<ChatMessage> getUnreadInMatch(Long matchId, Long userId) {

        List<ChatMessage> list =
                chatRepo.findByMatchIdAndRecipientIdAndReadFalse(matchId, userId);

        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return list;
    }

    // ============================================================
    // 🔵 10. סימון הודעות / שיחות כנקראו + עדכון unreadCount
    // ============================================================

    /**
     * סימון הודעה בודדת כנקראה.
     * רק אם userId הוא ה-recipient.
     */
    public void markMessageAsRead(Long messageId, Long userId) {

        chatRepo.findById(messageId).ifPresent(msg -> {

            Long rid = (msg.getRecipient() != null) ? msg.getRecipient().getId() : null;

            if (rid != null && rid.equals(userId) && !msg.isRead()) {
                msg.setRead(true);                             // setRead יעדכן גם readAt
                msg.setUpdatedAt(LocalDateTime.now());
                chatRepo.save(msg);

                if (msg.getMatch() != null) {
                    updateMatchUnreadCount(msg.getMatch());
                }
            }
        });
    }

    /**
     * סימון כל השיחה בין userId לבין otherUserId כנקראה.
     */
    public void markConversationAsRead(Long userId, Long otherUserId) {

        List<ChatMessage> conv =
                chatRepo.findBySenderIdAndRecipientIdOrSenderIdAndRecipientId(
                        userId, otherUserId,
                        otherUserId, userId
                );

        for (ChatMessage msg : conv) {

            if (msg.getRecipient() != null &&
                    msg.getRecipient().getId().equals(userId) &&
                    !msg.isRead() &&
                    !msg.isDeleted()) {

                msg.setRead(true);
                msg.setUpdatedAt(LocalDateTime.now());
                chatRepo.save(msg);

                if (msg.getMatch() != null) {
                    updateMatchUnreadCount(msg.getMatch());
                }
            }
        }
    }

    /**
     * סימון כל ההודעות הלא נקראות של משתמש כנקראו.
     */
    public void markAllUnreadAsRead(Long userId) {

        List<ChatMessage> unread = chatRepo.findByRecipientIdAndReadFalse(userId);

        for (ChatMessage msg : unread) {
            if (!msg.isDeleted()) {
                msg.setRead(true);
                msg.setUpdatedAt(LocalDateTime.now());
                chatRepo.save(msg);

                if (msg.getMatch() != null) {
                    updateMatchUnreadCount(msg.getMatch());
                }
            }
        }
    }

    // ============================================================
    // 🔵 11. מחיקה לוגית / מחיקה מלאה (Admin)
    // ============================================================

    /**
     * מחיקה לוגית של הודעה (Soft Delete).
     * רק השולח או המקבל רשאים לבצע פעולה זו.
     */
    public void softDeleteMessage(Long messageId, Long userId) {

        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        Long sid = (msg.getSender() != null) ? msg.getSender().getId() : null;
        Long rid = (msg.getRecipient() != null) ? msg.getRecipient().getId() : null;

        if (!userId.equals(sid) && !userId.equals(rid))
            throw new IllegalStateException("You cannot delete a message you are not part of.");

        msg.setDeleted(true);
        msg.setUpdatedAt(LocalDateTime.now());
        chatRepo.save(msg);

        if (msg.getMatch() != null) {
            updateMatchUnreadCount(msg.getMatch());
        }
    }

    /**
     * מחיקה מלאה מה-DB (Admin בלבד).
     */
    public void adminDeleteMessage(Long messageId) {

        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        chatRepo.deleteById(messageId);

        if (msg.getMatch() != null) {
            updateMatchUnreadCount(msg.getMatch());
        }
    }

    // ============================================================
    // 🔵 12. הודעות מערכת (System Messages)
    // ============================================================

    /**
     * יצירת הודעת מערכת בתוך Match.
     * לדוגמה: "ההתאמה בוטלה", "המשתמש שינה הגדרות".
     */
    public ChatMessage createSystemMessage(Long matchId,
                                           String content) {

        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        ChatMessage msg = new ChatMessage();
        msg.setSender(null);                 // System Message
        msg.setRecipient(null);              // אין נמען ישיר — צד הלקוח יציג ל-2
        msg.setMatch(match);
        msg.setMessageType("system");
        msg.setSystemMessage(true);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setDeleted(false);

        return chatRepo.save(msg);
    }

    // ============================================================
    // 🔵 13. תמיכה בהודעות מסוג Image / Video / File
    // ============================================================

    /**
     * שליחת הודעת תמונה.
     */
    public ChatMessage sendImageMessage(Long senderId,
                                        Long recipientId,
                                        Long matchId,
                                        String imageUrl) {

        return sendTypedMessage(senderId, recipientId, matchId, imageUrl, "image");
    }

    /**
     * שליחת הודעת וידאו.
     */
    public ChatMessage sendVideoMessage(Long senderId,
                                        Long recipientId,
                                        Long matchId,
                                        String videoUrl) {

        return sendTypedMessage(senderId, recipientId, matchId, videoUrl, "video");
    }

    /**
     * מתודה כללית להודעות מסוג ספציפי (image / video / file)
     */
    private ChatMessage sendTypedMessage(Long senderId,
                                         Long recipientId,
                                         Long matchId,
                                         String content,
                                         String type) {

        ChatMessage msg = sendMessage(senderId, recipientId, matchId, null, content);
        msg.setMessageType(type);
        msg.setUpdatedAt(LocalDateTime.now());

        return chatRepo.save(msg);
    }

    // ============================================================
    // 🔵 14. חיפוש מתקדם (Text Search)
    // ============================================================

    /**
     * חיפוש הודעות לפי מחרוזת (לא תלוי Match).
     */
    public List<ChatMessage> searchMessages(String keyword) {

        if (keyword == null || keyword.trim().isEmpty())
            return List.of();

        List<ChatMessage> list =
                chatRepo.findByContentContainingIgnoreCase(keyword.trim());

        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return list;
    }

    // ============================================================
    // 🔵 15. ניקוי הודעות ישנות (Maintenance)
    // ============================================================

    /**
     * שליפה של הודעות שנוצרו לפני X ימים.
     */
    public List<ChatMessage> getMessagesOlderThan(int days) {

        LocalDateTime threshold = LocalDateTime.now().minusDays(days);

        return chatRepo.findByCreatedAtBefore(threshold);
    }

    /**
     * מחיקה מלאה של הודעות ישנות (Admin).
     * מחזיר כמה ההודעות שנמחקו.
     */
    public int deleteMessagesOlderThan(int days) {

        LocalDateTime threshold = LocalDateTime.now().minusDays(days);

        List<ChatMessage> old =
                chatRepo.findByCreatedAtBefore(threshold);

        int count = old.size();
        chatRepo.deleteAll(old);

        return count;
    }

    // ============================================================
    // 🔵 16. Conversation ID (ליצירת "חוט שיחה" לוגי)
    // ============================================================

    /**
     * יצירת Conversation ID משותף לכל ההודעות של Match.
     * אם כבר קיים — מחזיר אותו.
     */
    public Long ensureConversationId(Long matchId) {

        List<ChatMessage> msgs =
                chatRepo.findByMatchIdOrderByCreatedAtAsc(matchId);

        if (msgs.isEmpty())
            return null;

        ChatMessage first = msgs.get(0);

        // אם כבר יש מזהה שיחה — נחזיר אותו
        if (first.getConversationId() != null)
            return first.getConversationId();

        // אחרת ניצור מזהה חדש
        Long convId = System.nanoTime(); // מזהה ייחודי

        for (ChatMessage m : msgs) {
            m.setConversationId(convId);
            m.setUpdatedAt(LocalDateTime.now());
            chatRepo.save(m);
        }

        return convId;
    }

    // ============================================================
    // 🔵 17. Delivered (WebSocket) – הודעות שלא נמסרו
    // ============================================================

    /**
     * הודעות שטרם נמסרו למקבל (delivered=false) – לשימוש ב-connect של WebSocket.
     */
    public List<ChatMessage> getUndeliveredMessagesForUser(Long userId) {
        List<ChatMessage> list = chatRepo.findByDeliveredFalseAndRecipientId(userId);
        list.removeIf(ChatMessage::isDeleted);
        list.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return list;
    }

    /**
     * סימון כל ההודעות שלא נמסרו למשתמש כ-delivered=true
     * אחרי ש־WebSocket דחף אותן לצד הלקוח.
     */
    public void markMessagesAsDeliveredForUser(Long userId) {

        List<ChatMessage> list = chatRepo.findByDeliveredFalseAndRecipientId(userId);
        LocalDateTime now = LocalDateTime.now();

        for (ChatMessage msg : list) {
            msg.setDelivered(true);
            msg.setUpdatedAt(now);
            chatRepo.save(msg);
        }
    }

    // ============================================================
    // 🔵 18. עדכון מונה unreadCount ב־Match
    // ============================================================

    /**
     * מחשב מחדש את מספר ההודעות הלא נקראות (והלא־מוחקות) עבור שני הצדדים במאץ'.
     * ושומר ב-match.setUnreadCount(...).
     */
    private void updateMatchUnreadCount(Match match) {

        if (match == null || match.getId() == null)
            return;

        Long matchId = match.getId();
        Long u1Id = (match.getUser1() != null) ? match.getUser1().getId() : null;
        Long u2Id = (match.getUser2() != null) ? match.getUser2().getId() : null;

        int total = 0;

        if (u1Id != null) {
            List<ChatMessage> unreadForU1 =
                    chatRepo.findByMatchIdAndRecipientIdAndReadFalse(matchId, u1Id);
            unreadForU1.removeIf(ChatMessage::isDeleted);
            total += unreadForU1.size();
        }

        if (u2Id != null) {
            List<ChatMessage> unreadForU2 =
                    chatRepo.findByMatchIdAndRecipientIdAndReadFalse(matchId, u2Id);
            unreadForU2.removeIf(ChatMessage::isDeleted);
            total += unreadForU2.size();
        }

        match.setUnreadCount(total);
        match.setUpdatedAt(LocalDateTime.now());
        matchRepo.save(match);
    }
}