package com.example.myproject.model.enums;

/**
 * קטגוריות פעולה לפי אפיון 1-2-3 וכל 41 חוקי המערכת.
 */
public enum UserActionCategory {

    // 1. אינטראקציות בין משתמשים
    SOCIAL,

    // 2. התאמות וזרימת Match
    MATCH,

    // 3. צ'אט ותקשורת
    CHAT,

    // 4. חתונות ואירועים
    EVENT,

    // 5. בקשות וזרימת מאגר גלובלי
    GLOBAL_POOL,

    // 6. ניהול פרופיל וחשבון
    PROFILE,

    // 7. פעולות מערכת / אדמין
    SYSTEM,

    // 8. ניתוח, AI ו-Analytics
    ANALYTICS,

    // fallback
    UNKNOWN
}