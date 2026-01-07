package com.example.myproject.controller.user;

import com.example.myproject.model.LoginAttempt;
import com.example.myproject.service.System.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/security")
@PreAuthorize("hasRole('USER')")
public class UserSecurityController {

    private final LoginAttemptService loginAttemptService;

    public UserSecurityController(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Recent attempts for my identifier (auth.getName()).
     * משתמשים במתודה שקיימת בפועל ב-Service: getAttemptsByIdentifier(...)
     */
    @GetMapping("/login-attempts/recent")
    public ResponseEntity<List<LoginAttempt>> getMyRecentLoginAttempts(
            Authentication auth,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int daysBack
    ) {
        String identifier = safeIdentifier(auth);
        int lim = clamp(limit, 1, 200);
        int days = clamp(daysBack, 1, 365);

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);

        return ResponseEntity.ok(
                loginAttemptService.getAttemptsByIdentifier(identifier, from, to, lim)
        );
    }

    /**
     * Gate status (blocked/otpRequired/failures) using evaluateGate(...)
     */
    @GetMapping("/login-attempts/gate-status")
    public ResponseEntity<Map<String, Object>> getMyGateStatus(
            Authentication auth,
            HttpServletRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId
    ) {
        String identifier = safeIdentifier(auth);
        String ip = safeIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        LoginAttemptService.GateStatus gate = loginAttemptService.evaluateGate(
                identifier,
                ip,
                deviceId,
                userAgent
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("blocked", gate.blocked);
        resp.put("blockedUntil", gate.blockedUntil);
        resp.put("blockReason", gate.blockReason != null ? gate.blockReason.name() : null);
        resp.put("requiresOtp", gate.requiresOtp);
        resp.put("failuresInWindow", gate.failuresInWindow);

        resp.put("identifier", identifier);
        resp.put("ip", ip);
        resp.put("deviceId", deviceId);
        resp.put("checkedAt", LocalDateTime.now());

        return ResponseEntity.ok(resp);
    }

    /**
     * Logout ב-JWT בד"כ = לקוח זורק טוקן. נשאר endpoint "נוח ל-UI".
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(Authentication auth) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("message", "Logged out (client should drop token).");
        resp.put("at", LocalDateTime.now());
        resp.put("user", auth != null ? auth.getName() : null);
        return ResponseEntity.ok(resp);
    }

    // -----------------------
    // helpers
    // -----------------------

    private static String safeIdentifier(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new IllegalStateException("Missing authentication identifier");
        }
        return auth.getName().trim();
    }

    private static String safeIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
