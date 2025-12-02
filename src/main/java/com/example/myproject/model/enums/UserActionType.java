package com.example.myproject.model.enums;

public enum UserActionType {

    // ========== SOCIAL ==========
    LIKE,
    LIKE_BACK,
    DISLIKE,
    FREEZE,
    UNFREEZE,
    VIEW,

    // ========== MATCH ==========
    APPROVE_MATCH,
    CANCEL_MATCH,
    MATCH_CONFIRMED,
    MATCH_MUTUAL,
    BLOCK,
    UNBLOCK,

    // ========== CHAT ==========
    SEND_MESSAGE,
    REQUEST_CHAT,
    APPROVE_CHAT,
    DECLINE_CHAT,
    FIRST_MESSAGE_SENT,

    // ========== EVENT ==========
    ENTER_WEDDING,
    EXIT_WEDDING,
    VIEW_WEDDING_MEMBER,

    // ========== GLOBAL POOL ==========
    REQUEST_GLOBAL_ACCESS,
    APPROVE_GLOBAL_ACCESS,
    DECLINE_GLOBAL_ACCESS,
    ENTER_GLOBAL_POOL,
    EXIT_GLOBAL_POOL,

    // ========== PROFILE ==========
    PROFILE_UPDATED,
    PROFILE_BASIC_COMPLETED,
    PROFILE_FULL_COMPLETED,
    PROFILE_PHOTO_ADDED,
    PROFILE_PHOTO_DELETED,

    // ========== SYSTEM / ADMIN ==========
    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_DELETED,
    SYSTEM_BROADCAST,

    // ========== AI / ANALYTICS ==========
    AI_SCORE_CALCULATED,
    AI_BEHAVIOR_ANOMALY,

    UNKNOWN
}