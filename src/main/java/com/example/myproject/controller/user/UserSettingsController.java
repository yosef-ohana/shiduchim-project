package com.example.myproject.controller.user;

import com.example.myproject.model.UserSettings;
import com.example.myproject.model.enums.DefaultMode;
import com.example.myproject.service.User.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/user/settings")
@PreAuthorize("hasRole('USER')")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    // ------------------------------------------------------------
    // Core
    // ------------------------------------------------------------

    @GetMapping
    public ResponseEntity<UserSettings> getOrCreate(Authentication auth) {
        Long userId = currentUserId(auth);
        return ResponseEntity.ok(userSettingsService.getOrCreate(userId));
    }

    // ------------------------------------------------------------
    // Updates (User-side, קיימים ב-Service)
    // ------------------------------------------------------------

    @PutMapping("/default-mode")
    public ResponseEntity<UserSettings> updateDefaultMode(Authentication auth, @RequestBody UpdateDefaultModeRequest req) {
        Long userId = currentUserId(auth);
        if (req == null || req.defaultMode() == null) throw new IllegalArgumentException("defaultMode is required");
        return ResponseEntity.ok(userSettingsService.updateDefaultMode(userId, req.defaultMode()));
    }

    @PutMapping("/can-view-same-gender")
    public ResponseEntity<UserSettings> updateCanViewSameGender(Authentication auth, @RequestBody UpdateBooleanRequest req) {
        Long userId = currentUserId(auth);
        if (req == null) throw new IllegalArgumentException("body is required");
        return ResponseEntity.ok(userSettingsService.updateCanViewSameGender(userId, req.value()));
    }

    @PutMapping("/short-card-fields-json")
    public ResponseEntity<UserSettings> updateShortCardFieldsJson(Authentication auth, @RequestBody UpdateStringRequest req) {
        Long userId = currentUserId(auth);
        if (req == null) throw new IllegalArgumentException("body is required");
        return ResponseEntity.ok(userSettingsService.updateShortCardFieldsJson(userId, req.value()));
    }

    @PutMapping("/ui-preferences-json")
    public ResponseEntity<UserSettings> updateUiPreferencesJson(Authentication auth, @RequestBody UpdateStringRequest req) {
        Long userId = currentUserId(auth);
        if (req == null) throw new IllegalArgumentException("body is required");
        return ResponseEntity.ok(userSettingsService.updateUiPreferencesJson(userId, req.value()));
    }

    @PutMapping("/extra-settings-json")
    public ResponseEntity<UserSettings> updateExtraSettingsJson(Authentication auth, @RequestBody UpdateStringRequest req) {
        Long userId = currentUserId(auth);
        if (req == null) throw new IllegalArgumentException("body is required");
        return ResponseEntity.ok(userSettingsService.updateExtraSettingsJson(userId, req.value()));
    }

    /**
     * Anti-spam update (3 שדות) — זו המתודה שקיימת אצלך בפועל.
     * כל שדה יכול להיות null כדי לא לעדכן אותו.
     */
    @PutMapping("/anti-spam")
    public ResponseEntity<UserSettings> updateAntiSpam(Authentication auth, @RequestBody UpdateAntiSpamRequest req) {
        Long userId = currentUserId(auth);
        if (req == null) throw new IllegalArgumentException("body is required");
        return ResponseEntity.ok(
                userSettingsService.updateAntiSpam(
                        userId,
                        req.likeCooldownSeconds(),
                        req.messageCooldownSeconds(),
                        req.autoAntiSpam()
                )
        );
    }

    @PostMapping("/reset-to-defaults")
    public ResponseEntity<UserSettings> resetToDefaults(Authentication auth) {
        Long userId = currentUserId(auth);
        return ResponseEntity.ok(userSettingsService.resetToDefaults(userId));
    }

    // ------------------------------------------------------------
    // Lock after wedding (קיים ב-Service)
    // ------------------------------------------------------------

    @PostMapping("/lock-after-wedding")
    public ResponseEntity<UserSettings> lockAfterWedding(Authentication auth, @RequestBody LockAfterWeddingRequest req) {
        Long userId = currentUserId(auth);
        if (req == null) throw new IllegalArgumentException("body is required");
        return ResponseEntity.ok(
                userSettingsService.lockAfterWedding(userId, req.lockedUntil(), req.reason())
        );
    }

    @PostMapping("/unlock-after-wedding")
    public ResponseEntity<UserSettings> unlockAfterWedding(Authentication auth, @RequestBody OptionalReasonRequest req) {
        Long userId = currentUserId(auth);
        String reason = (req != null) ? req.reason() : null;
        return ResponseEntity.ok(userSettingsService.unlockAfterWedding(userId, reason));
    }

    // ------------------------------------------------------------
    // Read-only convenience endpoints (מבוסס Service)
    // ------------------------------------------------------------

    @GetMapping("/effective")
    public ResponseEntity<Map<String, Object>> getEffective(Authentication auth) {
        Long userId = currentUserId(auth);
        return ResponseEntity.ok(Map.of(
                "defaultMode", userSettingsService.getDefaultMode(userId),
                "canViewSameGender", userSettingsService.canViewSameGender(userId),
                "likeCooldownSeconds", userSettingsService.getEffectiveLikeCooldownSeconds(userId),
                "messageCooldownSeconds", userSettingsService.getEffectiveMessageCooldownSeconds(userId),
                "autoAntiSpam", userSettingsService.isAutoAntiSpamEnabled(userId),
                "currentlyLocked", userSettingsService.isCurrentlyLocked(userId)
        ));
    }

    @GetMapping("/lock-status")
    public ResponseEntity<Map<String, Object>> getLockStatus(Authentication auth) {
        Long userId = currentUserId(auth);
        UserSettings s = userSettingsService.getOrCreate(userId);

        Boolean lockedAfterWedding = readBool(s, "getLockedAfterWedding", "lockedAfterWedding");
        LocalDateTime lockedUntil = readDateTime(s, "getLockedUntil", "lockedUntil");
        boolean lockedNow = userSettingsService.isCurrentlyLocked(userId);

        return ResponseEntity.ok(Map.of(
                "lockedNow", lockedNow,
                "lockedAfterWedding", lockedAfterWedding,
                "lockedUntil", lockedUntil
        ));
    }

    // -----------------------
    // DTOs
    // -----------------------

    public record UpdateDefaultModeRequest(DefaultMode defaultMode) {}
    public record UpdateBooleanRequest(boolean value) {}
    public record UpdateStringRequest(String value) {}
    public record UpdateAntiSpamRequest(Integer likeCooldownSeconds, Integer messageCooldownSeconds, Boolean autoAntiSpam) {}
    public record LockAfterWeddingRequest(LocalDateTime lockedUntil, String reason) {}
    public record OptionalReasonRequest(String reason) {}

    // -----------------------
    // helpers
    // -----------------------

    private static Long currentUserId(Authentication auth) {
        if (auth == null) throw new IllegalStateException("Missing authentication");

        Object principal = auth.getPrincipal();
        Long fromPrincipal = tryExtractId(principal);
        if (fromPrincipal != null) return fromPrincipal;

        try {
            return Long.parseLong(auth.getName());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve userId from SecurityContext");
        }
    }

    private static Long tryExtractId(Object principal) {
        if (principal == null) return null;
        try {
            Method m = principal.getClass().getMethod("getId");
            Object v = m.invoke(principal);
            if (v instanceof Long l) return l;
            if (v instanceof Integer i) return i.longValue();
        } catch (Exception ignored) {}
        return null;
    }

    private static Boolean readBool(Object obj, String getterName, String fieldName) {
        Object val = read(obj, getterName, fieldName);
        return (val instanceof Boolean b) ? b : null;
    }

    private static LocalDateTime readDateTime(Object obj, String getterName, String fieldName) {
        Object val = read(obj, getterName, fieldName);
        return (val instanceof LocalDateTime dt) ? dt : null;
    }

    private static Object read(Object obj, String getterName, String fieldName) {
        if (obj == null) return null;

        try {
            Method m = obj.getClass().getMethod(getterName);
            return m.invoke(obj);
        } catch (Exception ignored) {}

        try {
            var f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {}

        return null;
    }
}
