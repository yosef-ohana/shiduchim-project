// ===============================
// ✅ ChatMessageService — MASTER (2025) — FINAL + OPTIMAL (FIXED)
// ===============================
package com.example.myproject.service;

import com.example.myproject.model.ChatMessage;
import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.model.enums.ChatMessageType;
import com.example.myproject.repository.ChatMessageRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import com.example.myproject.service.System.SystemSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.myproject.service.User.UserStateEvaluatorService;
import com.example.myproject.service.notification.NotificationService;


import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChatMessageService {

    private static final String OPENING_APPROVED_PREFIX = "[OPENING_APPROVED]";
    private static final String OPENING_REJECTED_PREFIX = "[OPENING_REJECTED]";
    private static final int DEFAULT_ANTI_SPAM_COOLDOWN_SECONDS = 2;

    // ✅ NEW (SSOT): pull cooldown from SystemSettings (fallback=2)
    private static final String K_CHAT_MESSAGE_COOLDOWN_SECONDS = "chat.message.cooldownSeconds";

    // נקודת זמן "מוקדמת מאוד" כדי להחזיר "הכל" בשאילתות after()
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WeddingRepository weddingRepository;
    private final MatchService matchService;

    // ✅ NEW dependency (SSOT)
    private final SystemSettingsService settings;

    private final UserStateEvaluatorService userStateEvaluatorService;
    private final NotificationService notificationService;


    public ChatMessageService(ChatMessageRepository chatMessageRepository,
                              UserRepository userRepository,
                              WeddingRepository weddingRepository,
                              MatchService matchService,
                              SystemSettingsService settings,
                              UserStateEvaluatorService userStateEvaluatorService,
                              NotificationService notificationService
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.weddingRepository = weddingRepository;
        this.matchService = matchService;
        this.settings = settings;
        this.userStateEvaluatorService = userStateEvaluatorService;
        this.notificationService = notificationService;

    }

    // ============================================================
    // ✅ 1 + 10 — Opening Message (הודעה ראשונית אחת בלבד)
    // FIX: אין ChatMessageType.OPENING_MESSAGE ב-enum → משתמשים ב-TEXT
    // ============================================================

    public ChatMessage sendOpeningMessage(Long matchId, Long senderUserId, String content) {
        requireText(content);

        Match match = matchService.getById(matchId);
        validateUserInMatch(match, senderUserId);
        validateNotBlocked(match);

        matchService.validateOpeningMessageAllowed(matchId, senderUserId);
        matchService.validateNotSpam(matchId, resolveAntiSpamCooldownSeconds()); // ✅ FIX

        Long recipientId = resolveOtherUserId(match, senderUserId);

        ChatMessage msg = buildMessage(
                match, senderUserId, recipientId,
                content.trim(),
                ChatMessageType.TEXT,     // ✅ FIX
                true,                     // openingMessage=true זה ההבדלה
                false
        );

        ChatMessage saved = chatMessageRepository.save(msg);
        matchService.onChatMessageSent(matchId, senderUserId);
        return saved;
    }

    // ============================================================
    // ✅ 11 — approve/reject פתיחת צ'אט (אופטימלי: בלי סריקות היסטוריה)
    // ============================================================

    public ChatMessage approveOpeningRequest(Long matchId, Long approverUserId) {
        Match match = matchService.getById(matchId);
        validateUserInMatch(match, approverUserId);
        validateNotBlocked(match);

        OpeningState state = getOpeningState(matchId);
        if (state == OpeningState.NONE) {
            throw new IllegalStateException("No opening request exists for match " + matchId);
        }

        if (state == OpeningState.APPROVED) {
            return findLastOpeningDecisionMessage(matchId, OPENING_APPROVED_PREFIX)
                    .orElseGet(() -> createSystemMessage(matchId, OPENING_APPROVED_PREFIX + " Chat opening approved."));
        }
        if (state == OpeningState.REJECTED) {
            return findLastOpeningDecisionMessage(matchId, OPENING_REJECTED_PREFIX)
                    .orElseGet(() -> createSystemMessage(matchId, OPENING_REJECTED_PREFIX + " Chat opening already rejected previously."));
        }

        ChatMessage opening = getOpeningMessageOrThrow(matchId);
        if (!opening.getRecipient().getId().equals(approverUserId)) {
            throw new IllegalArgumentException("Only the opening recipient can approve this request.");
        }

        return createSystemMessage(matchId, OPENING_APPROVED_PREFIX + " Chat approved ✅. You can now chat freely.");
    }

    public ChatMessage rejectOpeningRequest(Long matchId, Long approverUserId, String reason) {
        Match match = matchService.getById(matchId);
        validateUserInMatch(match, approverUserId);
        validateNotBlocked(match);

        OpeningState state = getOpeningState(matchId);
        if (state == OpeningState.NONE) {
            throw new IllegalStateException("No opening request exists for match " + matchId);
        }

        if (state == OpeningState.REJECTED) {
            return findLastOpeningDecisionMessage(matchId, OPENING_REJECTED_PREFIX)
                    .orElseGet(() -> createSystemMessage(matchId, OPENING_REJECTED_PREFIX + " Chat opening already rejected."));
        }

        if (state == OpeningState.APPROVED) {
            throw new IllegalStateException("Opening already approved; cannot reject now.");
        }

        ChatMessage opening = getOpeningMessageOrThrow(matchId);
        if (!opening.getRecipient().getId().equals(approverUserId)) {
            throw new IllegalArgumentException("Only the opening recipient can reject this request.");
        }

        String safeReason = (reason == null || reason.isBlank()) ? "" : " Reason: " + reason.trim();
        return createSystemMessage(matchId, OPENING_REJECTED_PREFIX + " Chat request rejected ❌." + safeReason);
    }

    // ============================================================
    // ✅ 1 (המשך) — reply אחד לפני החלטה (אופטימלי: exists ב־DB)
    // ============================================================

    public ChatMessage sendOnePreDecisionReply(Long matchId, Long replierUserId, String content) {
        requireText(content);

        Match match = matchService.getById(matchId);
        validateUserInMatch(match, replierUserId);
        validateNotBlocked(match);

        if (getOpeningState(matchId) != OpeningState.PENDING) {
            throw new IllegalStateException("Pre-decision reply is allowed only while opening is PENDING.");
        }

        ChatMessage opening = getOpeningMessageOrThrow(matchId);
        if (!opening.getRecipient().getId().equals(replierUserId)) {
            throw new IllegalArgumentException("Only the opening recipient can send the one pre-decision reply.");
        }

        boolean alreadyReplied = chatMessageRepository
                .existsByMatch_IdAndSender_IdAndSystemMessageFalseAndOpeningMessageFalseAndDeletedFalseAndCreatedAtAfter(
                        matchId, replierUserId, opening.getCreatedAt()
                );

        if (alreadyReplied) {
            throw new IllegalStateException("Only one pre-decision reply is allowed.");
        }

        matchService.validateNotSpam(matchId, resolveAntiSpamCooldownSeconds()); // ✅ FIX

        ChatMessage msg = buildMessage(
                match,
                replierUserId,
                opening.getSender().getId(),
                content.trim(),
                ChatMessageType.TEXT,
                false,
                false
        );

        ChatMessage saved = chatMessageRepository.save(msg);
        matchService.onChatMessageSent(matchId, replierUserId);
        return saved;
    }

    // ============================================================
    // ✅ 2 + 9 — הודעות רגילות רק אם Mutual או Opening אושר
    // ============================================================

    public ChatMessage sendMessage(Long matchId, Long senderUserId, String content) {
        requireText(content);

        Match match = matchService.getById(matchId);
        validateUserInMatch(match, senderUserId);
        validateNotBlocked(match);

        validateChatAllowed(matchId, senderUserId);
        matchService.validateNotSpam(matchId, resolveAntiSpamCooldownSeconds()); // ✅ FIX

        Long recipientId = resolveOtherUserId(match, senderUserId);
        Long meetingWeddingId = match.getMeetingWeddingId();

        // ========================
// ✅ SSOT Gate: UserStateEvaluator (compat wrapper)
// ========================
        userStateEvaluatorService.assertCanMessage(
                senderUserId,
                recipientId,
                matchId,
                meetingWeddingId
        );


        ChatMessage msg = buildMessage(
                match,
                senderUserId,
                recipientId,
                content.trim(),
                ChatMessageType.TEXT,
                false,
                false
        );

        ChatMessage saved = chatMessageRepository.save(msg);
        matchService.onChatMessageSent(matchId, senderUserId);

        // ✅ Notify recipient (SYNC): יש לך notifyChatMessage ב-NotificationService
        try {
            notificationService.notifyChatMessage(
                    saved.getId(),
                    senderUserId,
                    recipientId,
                    matchId,
                    meetingWeddingId
            );
        } catch (Exception ignore) {
            // לא מפילים שליחת הודעה בגלל Notification
        }

        return saved;
    }





    // ============================================================
    // ✅ 7 — Attachments placeholder (disabled)
    // ============================================================

    public ChatMessage sendAttachmentDisabled(Long matchId, Long senderUserId, String ignoredUrl) {
        throw new UnsupportedOperationException("Attachments are disabled in this version (2025).");
    }

    // ============================================================
    // ✅ 3 + 4 — Unread bubble + mark read (אופטימלי: UPDATE DB)
    // ============================================================

    public int markConversationAsRead(Long matchId, Long readerUserId) {
        Match match = matchService.getById(matchId);
        validateUserInMatch(match, readerUserId);

        int updated = chatMessageRepository.markMatchAsRead(matchId, readerUserId, LocalDateTime.now());
        matchService.markMatchAsReadForUser(matchId, readerUserId);
        return updated;
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForUserInMatch(Long matchId, Long userId) {
        return chatMessageRepository.countByMatch_IdAndRecipient_IdAndReadFalseAndDeletedFalse(matchId, userId);
    }

    // ============================================================
    // ✅ 5 — Chat list (FINAL OPTIMAL: 2 queries for all matches)
    // ============================================================

    public static class ChatThreadPreview {
        private final Long matchId;
        private final Long otherUserId;
        private final String lastMessage;
        private final LocalDateTime lastMessageAt;
        private final long unreadCount;
        private final Long originWeddingId;
        private final Long meetingWeddingId;

        public ChatThreadPreview(Long matchId,
                                 Long otherUserId,
                                 String lastMessage,
                                 LocalDateTime lastMessageAt,
                                 long unreadCount,
                                 Long originWeddingId,
                                 Long meetingWeddingId) {
            this.matchId = matchId;
            this.otherUserId = otherUserId;
            this.lastMessage = lastMessage;
            this.lastMessageAt = lastMessageAt;
            this.unreadCount = unreadCount;
            this.originWeddingId = originWeddingId;
            this.meetingWeddingId = meetingWeddingId;
        }

        public Long getMatchId() { return matchId; }
        public Long getOtherUserId() { return otherUserId; }
        public String getLastMessage() { return lastMessage; }
        public LocalDateTime getLastMessageAt() { return lastMessageAt; }
        public long getUnreadCount() { return unreadCount; }
        public Long getOriginWeddingId() { return originWeddingId; }
        public Long getMeetingWeddingId() { return meetingWeddingId; }
    }

    @Transactional(readOnly = true)
    public List<ChatThreadPreview> getChatListForUser(Long userId) {
        List<Match> matches = matchService.getUserMatchesSortedByLastMessage(userId);
        if (matches == null || matches.isEmpty()) return List.of();

        List<Long> matchIds = matches.stream().map(Match::getId).collect(Collectors.toList());

        // 1) last messages for all matches in one query
        Map<Long, ChatMessage> lastByMatch = new HashMap<>();
        List<ChatMessage> lastMessages = chatMessageRepository.findLastMessagesForMatches(matchIds);
        for (ChatMessage m : lastMessages) {
            if (m == null || m.getMatch() == null) continue;
            Long mid = m.getMatch().getId();
            ChatMessage existing = lastByMatch.get(mid);
            if (existing == null) {
                lastByMatch.put(mid, m);
            } else {
                LocalDateTime a = existing.getCreatedAt();
                LocalDateTime b = m.getCreatedAt();
                if (a == null && b != null) lastByMatch.put(mid, m);
                else if (a != null && b != null) {
                    if (b.isAfter(a)) lastByMatch.put(mid, m);
                    else if (b.isEqual(a) && m.getId() != null && existing.getId() != null && m.getId() > existing.getId()) {
                        lastByMatch.put(mid, m);
                    }
                }
            }
        }

        // 2) unread counts for all matches in one query
        Map<Long, Long> unreadByMatch = new HashMap<>();
        List<ChatMessageRepository.MatchUnreadCountRow> rows = chatMessageRepository.countUnreadByMatchIds(userId, matchIds);
        for (var r : rows) unreadByMatch.put(r.getMatchId(), r.getCnt());

        // build list
        List<ChatThreadPreview> out = new ArrayList<>();
        for (Match m : matches) {
            Long otherId = resolveOtherUserId(m, userId);

            ChatMessage last = lastByMatch.get(m.getId());
            String lastText = (last == null) ? "" : safeSnippet(last.getContent());
            LocalDateTime lastAt = (last == null) ? m.getLastMessageAt() : last.getCreatedAt();

            long unread = unreadByMatch.getOrDefault(m.getId(), 0L);

            out.add(new ChatThreadPreview(
                    m.getId(),
                    otherId,
                    lastText,
                    lastAt,
                    unread,
                    m.getOriginWeddingId(),
                    m.getMeetingWeddingId()
            ));
        }

        out.sort(Comparator.comparing(ChatThreadPreview::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    // ============================================================
    // ✅ 12 + 13 — Mutual opens chat + LIVE bubble
    // ============================================================

    public ChatMessage onMatchBecameMutual(Long matchId, boolean liveWeddingNow) {
        matchService.getById(matchId); // validate exists
        ChatMessage system = createSystemMessage(matchId, "יש התאמה! 🎉 התחילו להתכתב");
        if (liveWeddingNow) {
            createSystemMessage(matchId, "האירוע כרגע פעיל — אולי כדאי להיפגש 😊");
        }
        return system;
    }

    // ============================================================
    // ✅ 14 + 28 — Conversation Merge / Normalize
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationBetweenUsers(Long userAId, Long userBId) {
        return chatMessageRepository.findConversationActive(userAId, userBId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationForMatch(Long matchId) {
        return chatMessageRepository.findByMatch_IdOrderByCreatedAtAsc(matchId);
    }

    // ============================================================
    // ✅ 15 — Match scoped queries
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getUnreadMessagesForMatch(Long matchId, Long userId) {
        return chatMessageRepository.findByMatch_IdAndRecipient_IdAndReadFalseAndDeletedFalseOrderByCreatedAtAsc(matchId, userId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getNewMessagesForMatchSince(Long matchId, LocalDateTime since) {
        if (since == null) since = EPOCH;
        return chatMessageRepository.findByMatch_IdAndCreatedAtAfter(matchId, since).stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .collect(Collectors.toList());
    }

    // ============================================================
    // ✅ 16 — Opening messages queries
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getOpeningMessagesForMatch(Long matchId) {
        return chatMessageRepository.findByMatch_IdAndOpeningMessageTrueAndDeletedFalse(matchId);
    }

    @Transactional(readOnly = true)
    public boolean hasOpeningMessageForMatch(Long matchId) {
        return chatMessageRepository.existsByMatch_IdAndOpeningMessageTrueAndDeletedFalse(matchId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getUnreadOpeningMessagesForUser(Long userId) {
        return chatMessageRepository.findByRecipient_IdAndOpeningMessageTrueAndReadFalseAndDeletedFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getOpeningMessagesForWedding(Long weddingId) {
        return chatMessageRepository.findByWedding_IdAndOpeningMessageTrueAndDeletedFalse(weddingId);
    }

    // ============================================================
    // ✅ 17 — Wedding context messages
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesForWedding(Long weddingId) {
        return chatMessageRepository.findByWedding_Id(weddingId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesForWeddingInTimeWindow(Long weddingId, LocalDateTime start, LocalDateTime end) {
        return chatMessageRepository.findByWedding_IdAndCreatedAtBetween(weddingId, start, end);
    }

    // ============================================================
    // ✅ 18 — last message between users (API חיצוני בלבד)
    // ============================================================

    @Transactional(readOnly = true)
    public ChatMessage getLastMessageBetweenUsersOrNull(Long userAId, Long userBId) {
        return chatMessageRepository.findLastMessageBetween(userAId, userBId);
    }

    @Transactional(readOnly = true)
    public ChatMessage getLastMessageForMatchOrNull(Long matchId) {
        return chatMessageRepository.findTopByMatch_IdAndDeletedFalseOrderByCreatedAtDesc(matchId).orElse(null);
    }

    // ============================================================
    // ✅ 19 — moderation / system / deleted cleanup
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getSystemMessages() {
        return chatMessageRepository.findBySystemMessageTrue();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getFlaggedMessages() {
        return chatMessageRepository.findByFlaggedTrue();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getDeletedMessages() {
        return chatMessageRepository.findByDeletedTrue();
    }

    public int cleanupDeletedMessages(LocalDateTime beforeTime) {
        if (beforeTime == null) beforeTime = LocalDateTime.now().minusDays(30);
        List<ChatMessage> old = chatMessageRepository.findByDeletedTrueAndDeletedAtBefore(beforeTime);
        if (old.isEmpty()) return 0;
        chatMessageRepository.deleteAll(old);
        return old.size();
    }

    // ============================================================
    // ✅ 20 — MessageType filtering
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesByType(ChatMessageType type) {
        return chatMessageRepository.findByMessageType(type);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesByTypeAndSender(ChatMessageType type, Long senderId) {
        return chatMessageRepository.findByMessageTypeAndSender_Id(type, senderId);
    }

    // ============================================================
    // ✅ 21 — Threads by conversationId
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getConversationByThreadId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return List.of();

        Long cid;
        try {
            cid = Long.valueOf(conversationId);
        } catch (NumberFormatException e) {
            return List.of();
        }

        return chatMessageRepository.findByConversationIdAndDeletedFalseOrderByCreatedAtAsc(cid);
    }


    // ============================================================
    // ✅ 22 — WebSocket sync queries (since timestamp)
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getNewMessagesForUserSince(Long userId, LocalDateTime since) {
        if (since == null) since = EPOCH;

        List<ChatMessage> asRecipient = chatMessageRepository.findByRecipient_IdAndCreatedAtAfter(userId, since);
        List<ChatMessage> asSender = chatMessageRepository.findBySender_IdAndCreatedAtAfter(userId, since);

        return concat(asRecipient, asSender).stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // ============================================================
    // ✅ 23 — Date windows + counts
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesBetween(LocalDateTime start, LocalDateTime end) {
        return chatMessageRepository.findByCreatedAtBetween(start, end);
    }

    @Transactional(readOnly = true)
    public long countMessagesBetween(LocalDateTime start, LocalDateTime end) {
        return chatMessageRepository.countByCreatedAtBetween(start, end);
    }

    // ============================================================
    // ✅ 24 — Undelivered queues (כולל bulk update)
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getUndeliveredMessagesForUser(Long userId) {
        return chatMessageRepository.findByRecipient_IdAndDeliveredFalse(userId).stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getUndeliveredSystemMessages() {
        return chatMessageRepository.findBySystemMessageTrueAndDeliveredFalse().stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .collect(Collectors.toList());
    }

    public int markUndeliveredAsDeliveredForUser(Long userId) {
        return chatMessageRepository.markUndeliveredAsDeliveredForUser(userId);
    }

    // ============================================================
    // ✅ 25 — Sender-only / recipient-only analytics
    // FIX: אין findBySender_Id / findByRecipient_Id בריפו → משתמשים ב-after(EPOCH)
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesBySender(Long senderId) {
        return chatMessageRepository.findBySender_IdAndCreatedAtAfter(senderId, EPOCH).stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesByRecipient(Long recipientId) {
        return chatMessageRepository.findByRecipient_IdAndCreatedAtAfter(recipientId, EPOCH).stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .collect(Collectors.toList());
    }

    // ============================================================
    // ✅ 26 — unread between specific pair (sender+recipient)
    // FIX: אין findBySender_IdAndRecipient_IdAndReadFalse → משתמשים ב-OrderBy + פילטר
    // ============================================================

    @Transactional(readOnly = true)
    public List<ChatMessage> getUnreadMessagesBetweenPair(Long senderId, Long recipientId) {
        return chatMessageRepository.findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(senderId, recipientId).stream()
                .filter(m -> !Boolean.TRUE.equals(m.isDeleted()))
                .filter(m -> !Boolean.TRUE.equals(m.isRead()))
                .collect(Collectors.toList());
    }

    // ============================================================
    // ✅ 27 — validateChatAllowed (מניעת "Chat without context")
    // ============================================================

    public void validateChatAllowed(Long matchId, Long senderUserId) {
        Match match = matchService.getById(matchId);

        validateUserInMatch(match, senderUserId);
        validateNotBlocked(match);

        // ✅ Mutual תמיד פותח צ׳אט רגיל (גם אם Opening נדחה בעבר)
        if (match.isMutualApproved()) return;

        OpeningState state = getOpeningState(matchId);

        // ✅ Opening אושר → צ׳אט חופשי
        if (state == OpeningState.APPROVED) return;

        // ❌ Opening נדחה → רק mutual יכול לפתוח
        if (state == OpeningState.REJECTED) {
            throw new IllegalStateException("Chat opening was rejected. Only mutual match can open chat now.");
        }

        // NONE או PENDING → אין צ׳אט חופשי עדיין
        if (state == OpeningState.PENDING) {
            ChatMessage opening = getOpeningMessageOrThrow(matchId);

            if (opening.getRecipient() != null && opening.getRecipient().getId().equals(senderUserId)) {

                boolean alreadyReplied = chatMessageRepository
                        .existsByMatch_IdAndSender_IdAndSystemMessageFalseAndOpeningMessageFalseAndDeletedFalseAndCreatedAtAfter(
                                matchId, senderUserId, opening.getCreatedAt()
                        );

                if (alreadyReplied) {
                    throw new IllegalStateException("SECOND_MESSAGE_REQUIRES_OPENING_APPROVAL");
                }

                throw new IllegalStateException("PRE_DECISION_REPLY_ONLY");
            }
        }

        throw new IllegalStateException("Chat is not allowed yet. Requires mutual match or opening approval.");
    }

    // ============================================================
    // ✅ 8 — block closes chat (אכיפה)
    // ============================================================

    private void validateNotBlocked(Match match) {
        if (match.isBlockedByUser1() || match.isBlockedByUser2()) {
            throw new IllegalStateException("Chat is blocked for this match.");
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    public ChatMessage createSystemMessage(Long matchId, String content) {
        requireText(content);

        Match match = matchService.getById(matchId);

        Long senderId = match.getUser1().getId();
        Long recipientId = match.getUser2().getId();

        ChatMessage msg = buildMessage(
                match,
                senderId,
                recipientId,
                content.trim(),
                ChatMessageType.SYSTEM,
                false,
                true
        );

        return chatMessageRepository.save(msg);
    }

    private ChatMessage buildMessage(Match match,
                                     Long senderId,
                                     Long recipientId,
                                     String content,
                                     ChatMessageType type,
                                     boolean openingMessage,
                                     boolean systemMessage) {

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + senderId));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + recipientId));

        Wedding wedding = resolveWeddingFromMatch(match);

        ChatMessage msg = new ChatMessage();
        msg.setSender(sender);
        msg.setRecipient(recipient);
        msg.setMatch(match);
        msg.setWedding(wedding);

        msg.setContent(content);
        msg.setMessageType(type);

        msg.setOpeningMessage(openingMessage);
        msg.setSystemMessage(systemMessage);

        msg.setRead(false);
        msg.setReadAt(null);

        msg.setDelivered(false);
        msg.setDeleted(false);
        msg.setDeletedAt(null);

        msg.setFlagged(false);

        return msg;
    }

    private Wedding resolveWeddingFromMatch(Match match) {
        Long weddingId = match.getMeetingWeddingId();
        if (weddingId == null) return null;
        return weddingRepository.findById(weddingId).orElse(null);
    }

    private void validateUserInMatch(Match match, Long userId) {
        if (!match.involvesUser(userId)) {
            throw new IllegalArgumentException("User " + userId + " is not part of match " + match.getId());
        }
    }

    private Long resolveOtherUserId(Match match, Long userId) {
        if (match.getUser1() != null && Objects.equals(match.getUser1().getId(), userId)) {
            return match.getUser2().getId();
        }
        if (match.getUser2() != null && Objects.equals(match.getUser2().getId(), userId)) {
            return match.getUser1().getId();
        }
        throw new IllegalArgumentException("Cannot resolve other user for match=" + match.getId() + " user=" + userId);
    }

    private void requireText(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }
    }

    private String safeSnippet(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= 120 ? t : t.substring(0, 120) + "…";
    }

    private enum OpeningState { NONE, PENDING, APPROVED, REJECTED }

    @Transactional(readOnly = true)
    private OpeningState getOpeningState(Long matchId) {
        boolean hasOpening = chatMessageRepository.existsByMatch_IdAndOpeningMessageTrueAndDeletedFalse(matchId);
        if (!hasOpening) return OpeningState.NONE;

        boolean approved = chatMessageRepository.existsByMatch_IdAndSystemMessageTrueAndDeletedFalseAndContentStartingWith(matchId, OPENING_APPROVED_PREFIX);
        if (approved) return OpeningState.APPROVED;

        boolean rejected = chatMessageRepository.existsByMatch_IdAndSystemMessageTrueAndDeletedFalseAndContentStartingWith(matchId, OPENING_REJECTED_PREFIX);
        if (rejected) return OpeningState.REJECTED;

        return OpeningState.PENDING;
    }

    @Transactional(readOnly = true)
    private Optional<ChatMessage> findLastOpeningDecisionMessage(Long matchId, String prefix) {
        return chatMessageRepository.findTopByMatch_IdAndSystemMessageTrueAndDeletedFalseAndContentStartingWithOrderByCreatedAtDesc(matchId, prefix);
    }

    private ChatMessage getOpeningMessageOrThrow(Long matchId) {
        return chatMessageRepository.findTopByMatch_IdAndOpeningMessageTrueAndDeletedFalseOrderByCreatedAtAsc(matchId)
                .orElseThrow(() -> new IllegalStateException("Opening message not found for match " + matchId));
    }

    private <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>();
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }

    // ============================================================
    // ✅ FIX (Section 10): cooldown from SystemSettings (fallback=2)
    // ============================================================

    private int resolveAntiSpamCooldownSeconds() {
        return getIntSetting(K_CHAT_MESSAGE_COOLDOWN_SECONDS, DEFAULT_ANTI_SPAM_COOLDOWN_SECONDS);
    }

    private int getIntSetting(String key, int def) {
        try {
            if (key == null || key.isBlank()) return def;
            return settings.getEffectiveInt(
                    settings.resolveEnv(),
                    SystemSettingsService.Scope.SYSTEM,
                    null,
                    key.trim(),
                    def
            );
        } catch (Exception ignore) {
            return def;
        }
    }
}