package com.example.myproject.model.enums;

public enum MatchSourceType {

    UNKNOWN,        // ברירת מחדל
    WEDDING,        // התאמה בחופה/חתונה
    GLOBAL,         // התאמה דרך המאגר הגלובלי
    LIVE_WEDDING,   // התאמה בזמן לייב (זמן אמת)
    ADMIN,          // התאמה שנוצרה ע"י מנהל
    AI,             // התאמה שנוצרה ע"י מנוע חכם
    LIKE_ACTION     // התאמה בעקבות לייק של משתמש
}