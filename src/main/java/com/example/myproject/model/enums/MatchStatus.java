package com.example.myproject.model.enums;

public enum MatchStatus {
    NONE,        // עדיין לא התחיל תהליך התאמה
    PENDING,     // אחד עשה LIKE ומחכים לצד השני
    ACTIVE,      // צ'אט פתוח / התאמה פעילה
    FROZEN,      // קפוא
    BLOCKED,     // חסימה
    ARCHIVED     // עבר לארכיון
}