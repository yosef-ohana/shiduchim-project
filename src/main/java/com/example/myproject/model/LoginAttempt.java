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

    // ✅ חסר לריפו (Device Monitoring)
    private String deviceId;

    // ✅ חסר לריפו (UserAgent Monitoring)
    private String userAgent;

    // האם המשתמש נחסם זמנית בעקבות 3 כישלונות
    private boolean temporaryBlocked = false;

    // עד איזה זמן החסימה תקפה
    private LocalDateTime blockedUntil;

    // דגל האם "צריך קוד אימות" (OTP) אחרי מספר נסיונות
    private boolean requiresOtp = false;

    // תאריך פקיעה עתידי של הרשומה (ללוגים)
    private LocalDateTime expiresAt;

    public LoginAttempt() {}

    public Long getId() { return id; }

    public String getEmailOrPhone() { return emailOrPhone; }
    public void setEmailOrPhone(String emailOrPhone) { this.emailOrPhone = emailOrPhone; }

    public LocalDateTime getAttemptTime() { return attemptTime; }
    public void setAttemptTime(LocalDateTime attemptTime) { this.attemptTime = attemptTime; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public boolean isTemporaryBlocked() { return temporaryBlocked; }
    public void setTemporaryBlocked(boolean temporaryBlocked) { this.temporaryBlocked = temporaryBlocked; }

    public LocalDateTime getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(LocalDateTime blockedUntil) { this.blockedUntil = blockedUntil; }

    public boolean isRequiresOtp() { return requiresOtp; }
    public void setRequiresOtp(boolean requiresOtp) { this.requiresOtp = requiresOtp; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}