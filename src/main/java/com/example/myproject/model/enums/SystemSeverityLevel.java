package com.example.myproject.model.enums;

/**
 * רמת חומרה של אירוע לוג.
 */
public enum SystemSeverityLevel {

    TRACE,      // גרעיני מאוד, לרוב רק בפיתוח
    DEBUG,      // דיבוג מפורט
    INFO,       // מידע שוטף
    NOTICE,     // משהו שראוי תשומת לב, אבל לא אזהרה
    WARNING,    // משהו חשוד/חריג, אך לא שגיאה
    ERROR,      // שגיאה לא קריטית (נכשל, אבל המערכת ממשיכה)
    CRITICAL,   // תקלה קריטית, משפיעה על משתמשים/תהליכים מרכזיים
    SECURITY,   // אירוע אבטחה / ניסיון פריצה / הרשאה לא חוקית
    SYSTEM      // אירועי מערכת כלליים (Startup/Shutdown/Degraded)
}