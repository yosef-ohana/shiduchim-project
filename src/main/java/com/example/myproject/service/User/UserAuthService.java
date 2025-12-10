package com.example.myproject.service.User;

import com.example.myproject.model.LoginAttempt;
import com.example.myproject.model.User;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.repository.LoginAttemptRepository;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserAuthService {

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    public UserAuthService(UserRepository userRepository,
                           LoginAttemptRepository loginAttemptRepository) {
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    // =====================================================
    // 🔵 הרשמה ראשונית + יצירת משתמש
    // abilities 1–2 + חוקי אימות
    // =====================================================

    public User registerUser(String fullName,
                             String phone,
                             String email,
                             String gender,
                             String signupSource) {

        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User(fullName, phone, email, gender);
        user.setSignupSource(signupSource);
        user.setProfileState(ProfileState.NONE);
        user.setGlobalAccessState(GlobalAccessState.NONE);
        user.setVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setLastProfileUpdateAt(LocalDateTime.now());

        // יצירת קוד אימות ראשוני
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationMethod("SMS");

        return userRepository.save(user);
    }

    private String generateVerificationCode() {
        // אפשר להחליף לאלגוריתם 6 ספרות, כרגע UUID קצר
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // =====================================================
    // 🔵 שליחת קוד אימות מחדש
    // =====================================================

    public User regenerateVerificationCode(Long userId, String method) {
        User user = getUserOrThrow(userId);
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationMethod(method);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 אימות משתמש לפי קוד
    // =====================================================

    public boolean verifyByCode(String code) {
        Optional<User> optional = userRepository.findByVerificationCode(code);
        if (optional.isEmpty()) {
            return false;
        }
        User user = optional.get();
        user.setVerified(true);
        user.setVerificationCode(null);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        return true;
    }

    // =====================================================
    // 🔵 התחברות לפי טלפון/אימייל + לוגיקת LoginAttempt
    // =====================================================

    public Optional<User> loginByPhoneOrEmail(String phoneOrEmail,
                                              String ip,
                                              String deviceInfoIgnoredForNow) {

        Optional<User> optional = userRepository.findByPhoneOrEmail(phoneOrEmail, phoneOrEmail);

        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmailOrPhone(phoneOrEmail);
        attempt.setIpAddress(ip);
        attempt.setAttemptTime(LocalDateTime.now());
        attempt.setSuccess(optional.isPresent());
        // כאן בהמשך נוכל להרחיב ל־temporaryBlocked / requiresOtp וכו'
        loginAttemptRepository.save(attempt);

        if (optional.isEmpty()) {
            return Optional.empty();
        }

        User user = optional.get();
        if (!user.isVerified()) {
            // אפשר לזרוק שגיאה מותאמת אם תרצה
            return Optional.empty();
        }

        // Heartbeat התחברות – עדכון עדכני
        user.setUpdatedAt(LocalDateTime.now());
        return Optional.of(userRepository.save(user));
    }

    // =====================================================
    // 🔵 Heartbeat / lastSeen (פשוט כרגע על updatedAt)
    // =====================================================

    public void updateUserHeartbeat(Long userId) {
        User user = getUserOrThrow(userId);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // =====================================================
    // 🔵 מחיקת חשבון (בקשה + ביצוע)
    // =====================================================

    public User requestDeletion(Long userId) {
        User user = getUserOrThrow(userId);
        user.setDeletionRequested(true);
        user.setDeletionRequestedAt(LocalDateTime.now());

        // לדוגמה: תאריך מחיקה עוד 30 יום
        user.setDeletionDueDate(LocalDateTime.now().plusDays(30));
        return userRepository.save(user);
    }

    public void hardDeleteIfDue(User user, LocalDateTime now) {
        if (user.isDeletionRequested()
                && user.getDeletionDueDate() != null
                && now.isAfter(user.getDeletionDueDate())) {
            userRepository.delete(user);
        }
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}