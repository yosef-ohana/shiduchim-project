package com.example.myproject.model.enums;

/**
 * מקור יצירת ההתאמה.
 * תואם למסמך 1–2–3 ולכל חוקי MatchService.
 */
public enum MatchSourceType {

    UNKNOWN,   // לא הוגדר / לא מצליחים לזהות
    WEDDING,   // נוצר בחתונה
    GLOBAL,    // נוצר במאגר הכללי
    ADMIN,     // מנהל יצר התאמה
    AI         // התאמה שנוצרה ע"י מערכת AI
}