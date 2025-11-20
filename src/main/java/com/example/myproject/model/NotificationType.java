package com.example.myproject.model;

/**
 * כל סוגי ההתראות במערכת.
 * כולל את כל מה שקיים אצלך + כל מה שנדרש ע"י NotificationService + אפיון 2025.
 */
public enum NotificationType {

    // ==================================================
    // 🔵 1. לייקים / התאמות (Match Flow)
    // ==================================================

    LIKE_RECEIVED,
    LIKE_BACK_RECEIVED,
    MATCH_CONFIRMED,
    MATCH_MUTUAL,
    MATCH_CLOSED,

    // ==================================================
    // 🔵 2. הודעות / צ'אט (כולל opening message)
    // ==================================================

    FIRST_MESSAGE_RECEIVED,
    FIRST_MESSAGE_SENT,
    FIRST_MESSAGE_ACCEPTED,
    FIRST_MESSAGE_REJECTED,

    CHAT_MESSAGE_RECEIVED,
    CHAT_APPROVED,
    CHAT_DECLINED,
    MESSAGE_RECEIVED,
    MESSAGE_READ,

    // ==================================================
    // 🔵 3. חתונות / אירועים
    // ==================================================

    WEDDING_ENTRY,
    WEDDING_ENDED,

    EVENT_BROADCAST,
    EVENT_REMINDER_START,
    EVENT_ENDED,

    // 🔵 בעל אירוע (Owner)
    WEDDING_OWNER_NEW_PARTICIPANT,
    WEDDING_OWNER_PROFILE_COMPLETED,
    WEDDING_OWNER_NEW_MATCH,
    WEDDING_OWNER_GLOBAL_REQUEST,

    // ==================================================
    // 🔵 4. מאגר כללי / בקשות
    // ==================================================

    GLOBAL_ACCESS_REQUESTED,
    GLOBAL_ACCESS_APPROVED,
    GLOBAL_ACCESS_DECLINED,

    ENTERED_GLOBAL_POOL,

    // ==================================================
    // 🔵 5. פרופיל / חשבון
    // ==================================================

    PROFILE_INCOMPLETE_REMINDER,
    PROFILE_COMPLETED,

    PROFILE_PHOTO_APPROVED,
    PROFILE_PHOTO_REJECTED,

    // ⭐ חדש לפי אפיון 2025 סעיף 6.4
    PROFILE_VIEWS_SUMMARY,

    ACCOUNT_DELETION_SCHEDULED,
    ACCOUNT_DELETION_CANCELLED,

    // ==================================================
    // 🔵 6. מערכת / מנהל
    // ==================================================

    SYSTEM_ANNOUNCEMENT,
    ADMIN_MESSAGE,

    // ==================================================
    // 🔵 7. AI / ניתוח התנהגות
    // ==================================================

    AI_BEHAVIOR_ALERT,
    AI_SUGGESTED_MATCH,

    // ==================================================
    // 🔵 8. פעולות משתמש (freeze/dislike)
    // ==================================================

    USER_DISLIKED,
    USER_FROZEN,
    USER_UNFROZEN,

    // ==================================================
    // 🔵 9. בדיקות / פיתוח
    // ==================================================

    TEST_PUSH_NOTIFICATION
}