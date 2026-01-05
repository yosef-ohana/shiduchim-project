package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.System.LoginAttemptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserAuthService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public UserAuthService(UserRepository userRepository,
                           LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    // =====================================================
    // הרשמה
    // =====================================================

    public User registerUser(String fullName,
                             String phone,
                             String email,
                             String gender,
                             String signupSource) {

        if (userRepository.existsByPhone(phone)) throw new IllegalArgumentException("Phone already exists");
        if (userRepository.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");

        User user = new User(fullName, phone, email, gender);
        user.setSignupSource(signupSource);
        user.setProfileState(ProfileState.NONE);
        user.setGlobalAccessState(GlobalAccessState.NONE);
        user.setVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setLastProfileUpdateAt(LocalDateTime.now());

        user.setVerificationCode(generateVerificationCode());
        user.setVerificationMethod("SMS");

        return userRepository.save(user);
    }

    private String generateVerificationCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public User regenerateVerificationCode(Long userId, String method) {
        User user = getUserOrThrow(userId);
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationMethod(method);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public boolean verifyByCode(String code) {
        Optional<User> optional = userRepository.findByVerificationCode(code);
        if (optional.isEmpty()) return false;

        User user = optional.get();
        user.setVerified(true);
        user.setVerificationCode(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

    // =====================================================
    // ✅ STEP 1: Begin login (Gate + OTP decision)
    // - לא מבצע authentication כאן אם צריך OTP
    // - לא חושף האם משתמש קיים
    // =====================================================

    public AuthLoginResult loginBegin(String phoneOrEmail,
                                      String ip,
                                      String deviceId,
                                      String userAgent) {

        LoginAttemptService.GateStatus gate =
                loginAttemptService.evaluateGate(phoneOrEmail, ip, deviceId, userAgent);

        if (gate.blocked) {
            // ✅ Capability #1: Gate audit (בלי לשמור LoginAttempt כדי לא לזהם counters)
            loginAttemptService.logGateDecision(
                    phoneOrEmail,
                    ip,
                    deviceId,
                    userAgent,
                    true,
                    gate.blockedUntil,
                    gate.requiresOtp,
                    "BLOCKED_" + gate.blockReason
            );

            return AuthLoginResult.blocked(gate.blockedUntil, gate.blockReason, true, gate.failuresInWindow);
        }

        if (gate.requiresOtp) {
            // ✅ Capability #1: Gate audit (otp required)
            loginAttemptService.logGateDecision(
                    phoneOrEmail,
                    ip,
                    deviceId,
                    userAgent,
                    false,
                    null,
                    true,
                    "OTP_REQUIRED"
            );

            // לא בודקים user קיים/לא קיים כדי למנוע enumeration
            return AuthLoginResult.otpRequired(gate.failuresInWindow);
        }

        // ✅ Gate open audit (optional but useful)
        loginAttemptService.logGateDecision(
                phoneOrEmail,
                ip,
                deviceId,
                userAgent,
                false,
                null,
                false,
                "OPEN"
        );

        // אם לא צריך OTP — אפשר לעבור לשלב 2 בלי OTP
        return loginAfterOtp(phoneOrEmail, true, ip, deviceId, userAgent);
    }

    // =====================================================
    // ✅ STEP 2: Complete login (after OTP passed)
    // otpPassed=true אומר שהלקוח עבר OTP בהצלחה (בעתיד דרך OTPService)
    // =====================================================

    public AuthLoginResult loginAfterOtp(String phoneOrEmail,
                                         boolean otpPassed,
                                         String ip,
                                         String deviceId,
                                         String userAgent) {

        // אם OTP נכשל — נרשום OTP attempt ונחזיר כשלון (לא חושפים קיימות)
        if (!otpPassed) {
            LoginAttemptService.OtpDecision otpDecision =
                    loginAttemptService.recordOtpAttempt(phoneOrEmail, false, null, ip, deviceId, userAgent);

            return AuthLoginResult.failed(true, otpDecision.otpFailuresInWindow);
        }

        Optional<User> optional = userRepository.findByPhoneOrEmail(phoneOrEmail, phoneOrEmail);

        boolean success = false;
        Long userId = null;
        User userOut = null;

        if (optional.isPresent()) {
            User user = optional.get();
            userId = user.getId();

            // חוק: חייב verified
            if (user.isVerified()) {
                success = true;
                user.setUpdatedAt(LocalDateTime.now());
                userOut = userRepository.save(user);
            }
        }

        LoginAttemptService.AttemptDecision decision =
                loginAttemptService.recordAttempt(phoneOrEmail, success, userId, ip, deviceId, userAgent);

        if (decision.blocked) {
            return AuthLoginResult.blocked(
                    decision.blockedUntil,
                    LoginAttemptService.GateBlockReason.IDENTIFIER,
                    decision.requiresOtp,
                    decision.failuresInWindow
            );
        }

        if (success) {
            return AuthLoginResult.success(userOut, false, decision.failuresInWindow);
        }

        // לא חושפים אם המשתמש קיים/לא קיים/לא verified
        return AuthLoginResult.failed(decision.requiresOtp, decision.failuresInWindow);
    }

    // =====================================================
    // Legacy wrapper (תאימות)
    // =====================================================

    public Optional<User> loginByPhoneOrEmail(String phoneOrEmail,
                                              String ip,
                                              String deviceInfoIgnoredForNow) {
        AuthLoginResult res = loginBegin(phoneOrEmail, ip, null, null);
        return res.user != null ? Optional.of(res.user) : Optional.empty();
    }

    // ✅ Heartbeat (SSOT): update only lastSeen (not updatedAt)
    public void updateUserHeartbeat(Long userId) {
        User user = getUserOrThrow(userId);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
    }

    public User requestDeletion(Long userId) {
        User user = getUserOrThrow(userId);
        user.setDeletionRequested(true);
        user.setDeletionRequestedAt(LocalDateTime.now());
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

    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    // =====================================================
    // DTO
    // =====================================================

    public static class AuthLoginResult {
        public final boolean success;
        public final boolean failed;
        public final boolean blocked;
        public final boolean otpRequired;

        public final LocalDateTime blockedUntil;
        public final LoginAttemptService.GateBlockReason blockReason;
        public final boolean requiresOtp;
        public final int failuresInWindow;
        public final User user;

        private AuthLoginResult(boolean success,
                                boolean failed,
                                boolean blocked,
                                boolean otpRequired,
                                LocalDateTime blockedUntil,
                                LoginAttemptService.GateBlockReason blockReason,
                                boolean requiresOtp,
                                int failuresInWindow,
                                User user) {
            this.success = success;
            this.failed = failed;
            this.blocked = blocked;
            this.otpRequired = otpRequired;
            this.blockedUntil = blockedUntil;
            this.blockReason = blockReason;
            this.requiresOtp = requiresOtp;
            this.failuresInWindow = failuresInWindow;
            this.user = user;
        }

        public static AuthLoginResult success(User user, boolean requiresOtp, int fails) {
            return new AuthLoginResult(true, false, false, false, null, null, requiresOtp, fails, user);
        }

        public static AuthLoginResult failed(boolean requiresOtp, int fails) {
            return new AuthLoginResult(false, true, false, false, null, null, requiresOtp, Math.max(0, fails), null);
        }

        public static AuthLoginResult otpRequired(int fails) {
            return new AuthLoginResult(false, false, false, true, null, null, true, Math.max(0, fails), null);
        }

        public static AuthLoginResult blocked(LocalDateTime until, LoginAttemptService.GateBlockReason reason, boolean requiresOtp, int fails) {
            return new AuthLoginResult(false, false, true, false, until, reason, requiresOtp, Math.max(0, fails), null);
        }
    }
}