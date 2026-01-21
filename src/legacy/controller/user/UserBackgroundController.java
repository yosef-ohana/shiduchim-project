package com.example.myproject.controller.user;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.WeddingMode;
import com.example.myproject.service.User.UserBackgroundService;
import com.example.myproject.service.WeddingBackgroundService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/user/background")
@PreAuthorize("hasRole('USER')")
public class UserBackgroundController {

    private final WeddingBackgroundService weddingBackgroundService;
    private final UserBackgroundService userBackgroundService;

    public UserBackgroundController(
            WeddingBackgroundService weddingBackgroundService,
            UserBackgroundService userBackgroundService
    ) {
        this.weddingBackgroundService = weddingBackgroundService;
        this.userBackgroundService = userBackgroundService;
    }

    // =====================================================
    // Resolve (מה שהקליינט צריך כדי להציג רקע)
    // =====================================================

    /**
     * Resolve לפי mode (GLOBAL/WEDDING/PAST_WEDDING) + weddingId אופציונלי.
     * בפועל קורא ל- WeddingBackgroundService.resolveForMode(boolean isWeddingMode, Long weddingIdIfAny)
     */
    @GetMapping("/resolve")
    public ResponseEntity<WeddingBackgroundService.BackgroundResolution> resolveForMode(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId,
            @RequestParam @NotNull WeddingMode mode,
            @RequestParam(required = false) Long weddingId
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);

        boolean isWeddingMode = isWeddingMode(mode);
        if (isWeddingMode && weddingId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "weddingId is required for WEDDING/PAST_WEDDING mode");
        }

        // actorUserId לא נדרש בשירות כרגע, אבל נשמר כאן כדי שתוכל להוסיף gate לפי SSOT בהמשך בלי לשבור API
        WeddingBackgroundService.BackgroundResolution res =
                weddingBackgroundService.resolveForMode(isWeddingMode, weddingId);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/global/current")
    public ResponseEntity<WeddingBackgroundService.BackgroundResolution> getCurrentGlobal() {
        return ResponseEntity.ok(weddingBackgroundService.resolveGlobalBackground());
    }

    @GetMapping("/wedding/{weddingId}/current")
    public ResponseEntity<WeddingBackgroundService.BackgroundResolution> getCurrentWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(weddingBackgroundService.resolveWeddingBackground(weddingId));
    }

    // =====================================================
    // Apply (שמירת בחירת רקע על המשתמש)
    // =====================================================

    @PostMapping("/apply/global")
    public ResponseEntity<User> applyGlobalBackground(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);
        return ResponseEntity.ok(userBackgroundService.applyGlobalBackground(actorUserId));
    }

    @PostMapping("/apply/wedding/{weddingId}")
    public ResponseEntity<User> applyWeddingBackground(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId,
            @PathVariable Long weddingId
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);
        return ResponseEntity.ok(userBackgroundService.applyWeddingBackground(actorUserId, weddingId));
    }

    @PostMapping("/apply/default")
    public ResponseEntity<User> applyDefaultBackground(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);
        return ResponseEntity.ok(userBackgroundService.applyDefaultBackground(actorUserId));
    }

    // =====================================================
    // Helpers
    // =====================================================

    private boolean isWeddingMode(WeddingMode mode) {
        return mode == WeddingMode.WEDDING || mode == WeddingMode.PAST_WEDDING;
    }

    private Long requireActorUserId(Principal principal, Long devUserId) {
        // Dev/Postman fallback (מומלץ למחוק בפרודקשן)
        if (devUserId != null) return devUserId;

        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthenticated");
        }
        try {
            return Long.parseLong(principal.getName().trim());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Authenticated but cannot resolve numeric userId from Principal.getName()"
            );
        }
    }
}
