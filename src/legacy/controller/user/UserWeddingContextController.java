package com.example.myproject.controller.user;

import com.example.myproject.model.User;
import com.example.myproject.model.UserSettings;
import com.example.myproject.model.enums.DefaultMode;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import com.example.myproject.service.User.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/wedding-context")
@PreAuthorize("hasRole('USER')")
public class UserWeddingContextController {

    private final UserRepository userRepository;
    private final WeddingRepository weddingRepository;
    private final UserSettingsService userSettingsService;

    public UserWeddingContextController(
            UserRepository userRepository,
            WeddingRepository weddingRepository,
            UserSettingsService userSettingsService
    ) {
        this.userRepository = userRepository;
        this.weddingRepository = weddingRepository;
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ResponseEntity<WeddingContextDto> getContext(Authentication auth) {
        Long userId = currentUserId(auth);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserSettings settings = userSettingsService.getOrCreate(userId);

        Long activeWeddingId = readLong(user, "getActiveWeddingId", "activeWeddingId");
        if (activeWeddingId == null) activeWeddingId = readLong(user, "getLastWeddingId", "lastWeddingId");

        @SuppressWarnings("unchecked")
        List<Long> weddingsHistory = (List<Long>) read(user, "getWeddingsHistory", "weddingsHistory");

        DefaultMode defaultMode = (DefaultMode) read(settings, "getDefaultMode", "defaultMode");

        return ResponseEntity.ok(new WeddingContextDto(
                userId,
                defaultMode,
                activeWeddingId,
                weddingsHistory,
                LocalDateTime.now()
        ));
    }

    @PostMapping("/active/{weddingId}")
    public ResponseEntity<Map<String, Object>> setActiveWedding(Authentication auth, @PathVariable Long weddingId) {
        Long userId = currentUserId(auth);

        if (weddingId == null) throw new IllegalArgumentException("weddingId is required");
        if (!weddingRepository.existsById(weddingId)) {
            throw new IllegalArgumentException("Wedding not found: " + weddingId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        boolean updated = write(user, "setActiveWeddingId", "activeWeddingId", weddingId);
        if (!updated) {
            // fallback נפוץ: יש אנשים ששומרים currentWeddingId במקום activeWeddingId
            updated = write(user, "setCurrentWeddingId", "currentWeddingId", weddingId);
        }
        userRepository.save(user);

        // עדכון default mode ל-WEDDING (אם קיים אצלך בערכים של enum)
        try {
            userSettingsService.updateDefaultMode(userId, DefaultMode.WEDDING);
        } catch (Exception ignored) {
            // אם אצלך אין WEDDING אלא ערך אחר – אל תיפול. תעדכן ידנית אחרי.
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "activeWeddingId", weddingId,
                "userId", userId
        ));
    }

    @PostMapping("/use-global")
    public ResponseEntity<Map<String, Object>> useGlobal(Authentication auth) {
        Long userId = currentUserId(auth);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // ננסה לנקות activeWeddingId/currentWeddingId
        write(user, "setActiveWeddingId", "activeWeddingId", null);
        write(user, "setCurrentWeddingId", "currentWeddingId", null);

        userRepository.save(user);

        // עדכון default mode ל-GLOBAL אם קיים
        try {
            userSettingsService.updateDefaultMode(userId, DefaultMode.GLOBAL);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "mode", "GLOBAL",
                "userId", userId
        ));
    }

    @PutMapping("/default-mode")
    public ResponseEntity<UserSettings> updateDefaultMode(Authentication auth, @RequestBody UpdateDefaultModeRequest req) {
        Long userId = currentUserId(auth);
        if (req == null || req.defaultMode == null) throw new IllegalArgumentException("defaultMode is required");
        return ResponseEntity.ok(userSettingsService.updateDefaultMode(userId, req.defaultMode));
    }

    // -----------------------
    // DTOs
    // -----------------------

    public record UpdateDefaultModeRequest(DefaultMode defaultMode) {}

    public record WeddingContextDto(
            Long userId,
            DefaultMode defaultMode,
            Long activeWeddingId,
            List<Long> weddingsHistory,
            LocalDateTime at
    ) {}

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

    private static Long readLong(Object obj, String getterName, String fieldName) {
        Object val = read(obj, getterName, fieldName);
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        return null;
    }

    /**
     * כותב דרך setter אם קיים, ואם לא – דרך field.
     * מחזיר true אם הצליח.
     */
    private static boolean write(Object obj, String setterName, String fieldName, Object value) {
        if (obj == null) return false;

        // setter
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    m.invoke(obj, value);
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // field
        try {
            var f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
            return true;
        } catch (Exception ignored) {}

        return false;
    }
}
