package com.example.myproject.model.enums;

public enum GlobalAccessState {
    NONE,        // לא ביקש + לא גלובלי
    REQUESTED,   // המתנה לאישור
    APPROVED,    // אושר גלובלי
    REJECTED     // נדחה על ידי מנהל
}