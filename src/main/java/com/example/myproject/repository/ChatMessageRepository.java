// ===============================
// ✅ ChatMessageRepository — MASTER (2025) — FINAL + OPTIMAL
// ===============================
package com.example.myproject.repository;

import com.example.myproject.model.ChatMessage;
import com.example.myproject.model.enums.ChatMessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // ============================================================
    // 🔵 0) Projections (כדי להחזיר aggregates בלי Object[])
    // ============================================================

    interface MatchUnreadCountRow {
        Long getMatchId();
        long getCnt();
    }

    // ============================================================
    // 🔵 1) Conversation (מאוחד בשאילתה אחת + deleted=false)
    // ============================================================

    @Query("""
           select cm
           from ChatMessage cm
           where (
                 (cm.sender.id = :userA and cm.recipient.id = :userB)
              or (cm.sender.id = :userB and cm.recipient.id = :userA)
           )
           and cm.deleted = false
           order by cm.createdAt asc
           """)
    List<ChatMessage> findConversationActive(@Param("userA") Long userA,
                                             @Param("userB") Long userB);

    // נשאר לשימושים נקודתיים אם תרצה (לא חובה)
    List<ChatMessage> findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(Long senderId, Long recipientId);
    List<ChatMessage> findByRecipient_IdAndSender_IdOrderByCreatedAtAsc(Long recipientId, Long senderId);

    // ✅ FIX (Section 11): filter deleted=false also in default conversation helper
    default List<ChatMessage> findConversation(Long userA, Long userB) {
        List<ChatMessage> a = findBySender_IdAndRecipient_IdOrderByCreatedAtAsc(userA, userB);
        List<ChatMessage> b = findByRecipient_IdAndSender_IdOrderByCreatedAtAsc(userA, userB);
        a.addAll(b);

        List<ChatMessage> out = new ArrayList<>();
        for (ChatMessage cm : a) {
            if (cm == null) continue;
            if (Boolean.TRUE.equals(cm.isDeleted())) continue;
            out.add(cm);
        }

        out.sort(Comparator.comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    // ============================================================
    // 🔵 2) לפי Match
    // ============================================================

    List<ChatMessage> findByMatch_IdOrderByCreatedAtAsc(Long matchId);

    List<ChatMessage> findByMatch_IdAndCreatedAtAfter(Long matchId, LocalDateTime time);

    // Unread (אופטימלי — לפי recipient כבר ב־DB, ולא פילטר ב־stream)
    List<ChatMessage> findByMatch_IdAndRecipient_IdAndReadFalseAndDeletedFalseOrderByCreatedAtAsc(Long matchId, Long recipientId);

    long countByMatch_IdAndRecipient_IdAndReadFalseAndDeletedFalse(Long matchId, Long recipientId);

    Optional<ChatMessage> findTopByMatch_IdAndDeletedFalseOrderByCreatedAtDesc(Long matchId);

    // ✅ ChatList Optimization #1: last messages for matchIds in one shot
    @Query("""
           select cm
           from ChatMessage cm
           where cm.match.id in :matchIds
             and cm.deleted = false
             and cm.createdAt = (
                 select max(cm2.createdAt)
                 from ChatMessage cm2
                 where cm2.match.id = cm.match.id
                   and cm2.deleted = false
             )
           """)
    List<ChatMessage> findLastMessagesForMatches(@Param("matchIds") List<Long> matchIds);

    // ✅ ChatList Optimization #2: unread counts for matchIds in one shot
    @Query("""
           select cm.match.id as matchId, count(cm) as cnt
           from ChatMessage cm
           where cm.match.id in :matchIds
             and cm.recipient.id = :userId
             and cm.read = false
             and cm.deleted = false
           group by cm.match.id
           """)
    List<MatchUnreadCountRow> countUnreadByMatchIds(@Param("userId") Long userId,
                                                    @Param("matchIds") List<Long> matchIds);

    // ============================================================
    // 🔵 3) Opening Message (כולל deleted=false)
    // ============================================================

    boolean existsByMatch_IdAndOpeningMessageTrueAndDeletedFalse(Long matchId);

    Optional<ChatMessage> findTopByMatch_IdAndOpeningMessageTrueAndDeletedFalseOrderByCreatedAtAsc(Long matchId);

    List<ChatMessage> findByMatch_IdAndOpeningMessageTrueAndDeletedFalse(Long matchId);

    List<ChatMessage> findByRecipient_IdAndOpeningMessageTrueAndReadFalseAndDeletedFalse(Long recipientId);

    List<ChatMessage> findByWedding_IdAndOpeningMessageTrueAndDeletedFalse(Long weddingId);

    List<ChatMessage> findByWedding_IdAndOpeningMessageTrueAndReadFalseAndDeletedFalse(Long weddingId);

    // ============================================================
    // 🔵 4) Opening Decision Messages (ללא סריקת היסטוריה)
    // ============================================================

    Optional<ChatMessage> findTopByMatch_IdAndSystemMessageTrueAndDeletedFalseAndContentStartingWithOrderByCreatedAtDesc(Long matchId, String prefix);

    boolean existsByMatch_IdAndSystemMessageTrueAndDeletedFalseAndContentStartingWith(Long matchId, String prefix);

    // ============================================================
    // 🔵 5) Pre-decision "reply once" check (DB-side, לא stream)
    // ============================================================

    boolean existsByMatch_IdAndSender_IdAndSystemMessageFalseAndOpeningMessageFalseAndDeletedFalseAndCreatedAtAfter(
            Long matchId, Long senderId, LocalDateTime afterTime
    );

    // ============================================================
    // 🔵 6) Wedding Context
    // ============================================================

    List<ChatMessage> findByWedding_Id(Long weddingId);

    List<ChatMessage> findByWedding_IdAndCreatedAtBetween(Long weddingId, LocalDateTime start, LocalDateTime end);

    // ============================================================
    // 🔵 7) System / flagged / deleted
    // ============================================================

    List<ChatMessage> findBySystemMessageTrue();
    List<ChatMessage> findByFlaggedTrue();
    List<ChatMessage> findByDeletedTrue();
    List<ChatMessage> findByDeletedTrueAndDeletedAtBefore(LocalDateTime time);

    // ============================================================
    // 🔵 8) לפי סוג הודעה
    // ============================================================

    List<ChatMessage> findByMessageType(ChatMessageType type);
    List<ChatMessage> findByMessageTypeAndSender_Id(ChatMessageType type, Long senderId);

    // ============================================================
    // 🔵 9) ConversationId (String)
    // ============================================================

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    List<ChatMessage> findByConversationIdAndDeletedFalseOrderByCreatedAtAsc(String conversationId);

    // ============================================================
    // 🔵 10) WebSocket Sync
    // ============================================================

    List<ChatMessage> findByRecipient_IdAndCreatedAtAfter(Long userId, LocalDateTime time);
    List<ChatMessage> findBySender_IdAndCreatedAtAfter(Long userId, LocalDateTime time);

    // ============================================================
    // 🔵 11) Date windows
    // ============================================================

    List<ChatMessage> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // ============================================================
    // 🔵 12) Undelivered queues
    // ============================================================

    List<ChatMessage> findByRecipient_IdAndDeliveredFalse(Long recipientId);
    List<ChatMessage> findBySystemMessageTrueAndDeliveredFalse();

    // ============================================================
    // 🔵 13) Bulk Updates (אופטימיזציה אמיתית)
    // ============================================================

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update ChatMessage cm
           set cm.read = true, cm.readAt = :now
           where cm.match.id = :matchId
             and cm.recipient.id = :recipientId
             and cm.read = false
             and cm.deleted = false
           """)
    int markMatchAsRead(@Param("matchId") Long matchId,
                        @Param("recipientId") Long recipientId,
                        @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update ChatMessage cm
           set cm.delivered = true
           where cm.recipient.id = :recipientId
             and cm.delivered = false
             and cm.deleted = false
           """)
    int markUndeliveredAsDeliveredForUser(@Param("recipientId") Long recipientId);

    // ============================================================
    // 🔵 14) "Last message between users" (ל־API חיצוני בלבד)
    // ============================================================

    ChatMessage findTopBySender_IdAndRecipient_IdOrderByCreatedAtDesc(Long senderId, Long recipientId);
    ChatMessage findTopByRecipient_IdAndSender_IdOrderByCreatedAtDesc(Long recipientId, Long senderId);

    default ChatMessage findLastMessageBetween(Long userA, Long userB) {
        ChatMessage a = findTopBySender_IdAndRecipient_IdOrderByCreatedAtDesc(userA, userB);
        ChatMessage b = findTopByRecipient_IdAndSender_IdOrderByCreatedAtDesc(userA, userB);
        if (a == null) return b;
        if (b == null) return a;

        LocalDateTime ta = a.getCreatedAt();
        LocalDateTime tb = b.getCreatedAt();
        if (ta == null) return b;
        if (tb == null) return a;
        return ta.isAfter(tb) ? a : b;
    }
}