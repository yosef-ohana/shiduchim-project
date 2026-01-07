package com.example.myproject.controller.user;

import com.example.myproject.model.User;
import com.example.myproject.model.UserSettings;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.service.User.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/user/state")
@PreAuthorize("hasRole('USER')")
public class UserStateEvaluatorController {

    private final UserRepository userRepository;
    private final UserSettingsService userSettingsService;

    public UserStateEvaluatorController(UserRepository userRepository, UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ResponseEntity<UserStateSnapshot> getMyState(Authentication auth) {
        Long userId = currentUserId(auth);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserSettings settings = userSettingsService.getOrCreate(userId);

        Boolean hasPrimaryPhoto =
                firstNonNull(
                        readBoolByGetter(user, "isHasPrimaryPhoto"),
                        readBoolByGetter(user, "getHasPrimaryPhoto"),
                        readBoolByField(user, "hasPrimaryPhoto")
                );

        Boolean hasAnyPhoto =
                firstNonNull(
                        readBoolByGetter(user, "isHasAnyPhoto"),
                        readBoolByGetter(user, "getHasAnyPhoto"),
                        readBoolByField(user, "hasAnyPhoto"),
                        hasPrimaryPhoto // fallback הגיוני
                );

        Boolean inGlobalPool =
                firstNonNull(
                        readBoolByGetter(user, "isInGlobalPool"),
                        readBoolByGetter(user, "getInGlobalPool"),
                        readBoolByField(user, "inGlobalPool")
                );

        Object globalAccessState =
                firstNonNull(
                        readObjByGetter(user, "getGlobalAccessState"),
                        readObjByField(user, "globalAccessState")
                );

        Object profileState =
                firstNonNull(
                        readObjByGetter(user, "getProfileState"),
                        readObjByField(user, "profileState")
                );

        Boolean lockedAfterWedding =
                firstNonNull(
                        readBoolByGetter(settings, "getLockedAfterWedding"),
                        readBoolByField(settings, "lockedAfterWedding")
                );

        LocalDateTime lockedUntil =
                firstNonNull(
                        readDateTimeByGetter(settings, "getLockedUntil"),
                        readDateTimeByField(settings, "lockedUntil")
                );

        boolean lockedNow = userSettingsService.isCurrentlyLocked(userId);

        UserStateSnapshot snapshot = new UserStateSnapshot(
                userId,
                hasAnyPhoto,
                hasPrimaryPhoto,
                inGlobalPool,
                globalAccessState != null ? String.valueOf(globalAccessState) : null,
                profileState != null ? String.valueOf(profileState) : null,
                lockedNow,
                lockedAfterWedding,
                lockedUntil,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/ui-hints")
    public ResponseEntity<Map<String, Object>> getUiHints(Authentication auth) {
        UserStateSnapshot s = getMyState(auth).getBody();
        if (s == null) return ResponseEntity.ok(Map.of("ok", false));

        return ResponseEntity.ok(Map.of(
                "lockedNow", s.lockedNow(),
                "hasAnyPhoto", s.hasAnyPhoto(),
                "hasPrimaryPhoto", s.hasPrimaryPhoto(),
                "inGlobalPool", s.inGlobalPool(),
                "globalAccessState", s.globalAccessState(),
                "profileState", s.profileState()
        ));
    }

    // -----------------------
    // DTO
    // -----------------------

    public record UserStateSnapshot(
            Long userId,
            Boolean hasAnyPhoto,
            Boolean hasPrimaryPhoto,
            Boolean inGlobalPool,
            String globalAccessState,
            String profileState,
            boolean lockedNow,
            Boolean lockedAfterWedding,
            LocalDateTime lockedUntil,
            LocalDateTime evaluatedAt
    ) {}

    // -----------------------
    // helpers (minimal + clean)
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

    private static Boolean readBoolByGetter(Object obj, String getterName) {
        Object v = readObjByGetter(obj, getterName);
        return (v instanceof Boolean b) ? b : null;
    }

    private static Boolean readBoolByField(Object obj, String fieldName) {
        Object v = readObjByField(obj, fieldName);
        return (v instanceof Boolean b) ? b : null;
    }

    private static LocalDateTime readDateTimeByGetter(Object obj, String getterName) {
        Object v = readObjByGetter(obj, getterName);
        return (v instanceof LocalDateTime dt) ? dt : null;
    }

    private static LocalDateTime readDateTimeByField(Object obj, String fieldName) {
        Object v = readObjByField(obj, fieldName);
        return (v instanceof LocalDateTime dt) ? dt : null;
    }

    private static Object readObjByGetter(Object obj, String getterName) {
        if (obj == null || getterName == null) return null;
        try {
            Method m = obj.getClass().getMethod(getterName);
            return m.invoke(obj);
        } catch (Exception ignored) {}
        return null;
    }

    private static Object readObjByField(Object obj, String fieldName) {
        if (obj == null || fieldName == null) return null;
        try {
            var f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {}
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) return null;
        for (T v : values) if (v != null) return v;
        return null;
    }
}
