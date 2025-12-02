package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // יכול להיות אימייל או טלפון — אנחנו לא כופים פורמט
    @Column(nullable = false)
    private String emailOrPhone;

    // מתי היה הניסיון
    private LocalDateTime attemptTime = LocalDateTime.now();

    // הצלחה / כישלון
    private boolean success;

    // כתובת IP לצורך ניטור אבטחה
    private String ipAddress;

    // האם המשתמש נחסם זמנית בעקבות 3 כישלונות
    private boolean temporaryBlocked = false;

    // עד איזה זמן החסימה תקפה
    private LocalDateTime blockedUntil;

    // דגל האם "צריך קוד אימות" (OTP) אחרי מספר נסיונות
    private boolean requiresOtp = false;

    // תאריך פקיעה עתידי של הרשומה (ללוגים)
    private LocalDateTime expiresAt;
}