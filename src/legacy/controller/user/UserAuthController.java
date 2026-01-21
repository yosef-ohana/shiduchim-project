package com.example.myproject.controller.user;

import com.example.myproject.model.User;
import com.example.myproject.service.User.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/auth")
public class UserAuthController {

    private final UserAuthService userAuthService;

    public UserAuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    // =====================================================
    // Actor helper (SSOT)
    // =====================================================

    private Long actorId(Long headerId, Principal principal) {
        // If an SSOT Addendum exists with real JWT/Security extraction, use it ONLY.
        if (headerId == null) throw new IllegalArgumentException("Missing X-User-Id header");
        return headerId;
    }

    // =====================================================
    // 1) Register
    // =====================================================

    @PostMapping("/register-user")
    public ResponseEntity<User> registerUser(@RequestBody RegisterUserRequest req) {
        if (req == null) throw new IllegalArgumentException("Missing body");
        if (isBlank(req.fullName)) throw new IllegalArgumentException("fullName is required");
        if (isBlank(req.phone)) throw new IllegalArgumentException("phone is required");
        if (isBlank(req.email)) throw new IllegalArgumentException("email is required");
        if (isBlank(req.gender)) throw new IllegalArgumentException("gender is required");

        User created = userAuthService.registerUser(
                req.fullName.trim(),
                req.phone.trim(),
                req.email.trim(),
                req.gender.trim(),
                trimToNull(req.signupSource)
        );

        return ResponseEntity.status(201).body(created);
    }

    // =====================================================
    // 2) Regenerate verification code (for current user)
    // =====================================================

    @PostMapping("/regenerate-verification-code")
    public ResponseEntity<User> regenerateVerificationCode(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal,
            @RequestBody RegenerateVerificationCodeRequest req
    ) {
        if (req == null) throw new IllegalArgumentException("Missing body");
        if (isBlank(req.method)) throw new IllegalArgumentException("method is required");

        Long userId = actorId(xUserId, principal);

        User updated = userAuthService.regenerateVerificationCode(userId, req.method.trim());
        return ResponseEntity.ok(updated);
    }

    // =====================================================
    // 3) Verify by code
    // =====================================================

    @PostMapping("/verify-by-code")
    public ResponseEntity<Boolean> verifyByCode(@RequestBody VerifyByCodeRequest req) {
        if (req == null) throw new IllegalArgumentException("Missing body");
        if (isBlank(req.code)) throw new IllegalArgumentException("code is required");

        boolean ok = userAuthService.verifyByCode(req.code.trim());
        return ResponseEntity.ok(ok);
    }

    // =====================================================
    // 4) Login begin (Gate + OTP decision)
    // =====================================================

    @PostMapping("/login-begin")
    public ResponseEntity<UserAuthService.AuthLoginResult> loginBegin(
            @RequestBody LoginBeginRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest http
    ) {
        if (req == null) throw new IllegalArgumentException("Missing body");
        if (isBlank(req.phoneOrEmail)) throw new IllegalArgumentException("phoneOrEmail is required");

        String ip = (http != null ? http.getRemoteAddr() : null);

        UserAuthService.AuthLoginResult result = userAuthService.loginBegin(
                req.phoneOrEmail.trim(),
                ip,
                trimToNull(deviceId),
                trimToNull(userAgent)
        );

        return ResponseEntity.ok(result);
    }

    // =====================================================
    // 5) Login after OTP (complete login)
    // =====================================================

    @PostMapping("/login-after-otp")
    public ResponseEntity<UserAuthService.AuthLoginResult> loginAfterOtp(
            @RequestBody LoginAfterOtpRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest http
    ) {
        if (req == null) throw new IllegalArgumentException("Missing body");
        if (isBlank(req.phoneOrEmail)) throw new IllegalArgumentException("phoneOrEmail is required");

        String ip = (http != null ? http.getRemoteAddr() : null);

        UserAuthService.AuthLoginResult result = userAuthService.loginAfterOtp(
                req.phoneOrEmail.trim(),
                req.otpPassed != null && req.otpPassed,
                ip,
                trimToNull(deviceId),
                trimToNull(userAgent)
        );

        return ResponseEntity.ok(result);
    }

    // =====================================================
    // 6) Legacy wrapper (compat)
    // =====================================================

    @GetMapping("/login-by-phone-or-email")
    public ResponseEntity<User> loginByPhoneOrEmail(
            @RequestParam("phoneOrEmail") String phoneOrEmail,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest http
    ) {
        if (isBlank(phoneOrEmail)) throw new IllegalArgumentException("phoneOrEmail is required");

        String ip = (http != null ? http.getRemoteAddr() : null);
        Optional<User> userOpt = userAuthService.loginByPhoneOrEmail(phoneOrEmail.trim(), ip, trimToNull(userAgent));
        return userOpt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // =====================================================
    // 7) Heartbeat (update lastSeen only)
    // =====================================================

    @PostMapping("/update-user-heartbeat")
    public ResponseEntity<Void> updateUserHeartbeat(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal
    ) {
        Long userId = actorId(xUserId, principal);
        userAuthService.updateUserHeartbeat(userId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // 8) Request deletion (start 30-day window)
    // =====================================================

    @PostMapping("/request-deletion")
    public ResponseEntity<User> requestDeletion(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal
    ) {
        Long userId = actorId(xUserId, principal);
        User updated = userAuthService.requestDeletion(userId);
        return ResponseEntity.ok(updated);
    }

    // =====================================================
    // 9) Get current user (utility)
    // =====================================================

    @GetMapping("/me")
    public ResponseEntity<User> me(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal
    ) {
        Long userId = actorId(xUserId, principal);
        User user = userAuthService.getUserOrThrow(userId);
        return ResponseEntity.ok(user);
    }

    // =====================================================
    // Internal DTOs (SSOT)
    // =====================================================

    public static class RegisterUserRequest {
        public String fullName;
        public String phone;
        public String email;
        public String gender;
        public String signupSource;
    }

    public static class RegenerateVerificationCodeRequest {
        public String method;
    }

    public static class VerifyByCodeRequest {
        public String code;
    }

    public static class LoginBeginRequest {
        public String phoneOrEmail;
    }

    public static class LoginAfterOtpRequest {
        public String phoneOrEmail;
        public Boolean otpPassed;
    }

    // =====================================================
    // Small utils (no business logic)
    // =====================================================

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
