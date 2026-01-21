package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.service.User.SystemRulesService;
import com.example.myproject.service.User.UserAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/system-rules")
public class AdminSystemRulesController {

    private final SystemRulesService systemRulesService;
    private final UserAuthService userAuthService;

    public AdminSystemRulesController(SystemRulesService systemRulesService,
                                      UserAuthService userAuthService) {
        this.systemRulesService = systemRulesService;
        this.userAuthService = userAuthService;
    }

    private Long actorId(Long headerId, Principal principal) {
        // If an SSOT Addendum exists with real JWT/Security extraction, use it ONLY.
        if (headerId == null) throw new IllegalArgumentException("Missing X-User-Id header");
        return headerId;
    }

    private void validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive number");
        }
    }

    /**
     * POST /api/admin/system-rules/{userId}/enforce-all-user-rules
     * Applies all core rules to a user and persists changes.
     */
    @PostMapping("/{userId}/enforce-all-user-rules")
    public ResponseEntity<User> enforceAllUserRules(@RequestHeader("X-User-Id") Long xUserId,
                                                    @PathVariable("userId") Long userId,
                                                    Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        User updated = systemRulesService.enforceAllUserRules(user);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/admin/system-rules/{userId}/enforce-primary-photo-rule
     */
    @PostMapping("/{userId}/enforce-primary-photo-rule")
    public ResponseEntity<Void> enforcePrimaryPhotoRule(@RequestHeader("X-User-Id") Long xUserId,
                                                        @PathVariable("userId") Long userId,
                                                        Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        systemRulesService.enforcePrimaryPhotoRule(user);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/system-rules/{userId}/enforce-global-access-state-rule
     */
    @PostMapping("/{userId}/enforce-global-access-state-rule")
    public ResponseEntity<Void> enforceGlobalAccessStateRule(@RequestHeader("X-User-Id") Long xUserId,
                                                             @PathVariable("userId") Long userId,
                                                             Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        systemRulesService.enforceGlobalAccessStateRule(user);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/system-rules/{userId}/enforce-profile-locked-after-wedding-rule
     */
    @PostMapping("/{userId}/enforce-profile-locked-after-wedding-rule")
    public ResponseEntity<Void> enforceProfileLockedAfterWeddingRule(@RequestHeader("X-User-Id") Long xUserId,
                                                                     @PathVariable("userId") Long userId,
                                                                     Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        systemRulesService.enforceProfileLockedAfterWeddingRule(user);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/system-rules/{userId}/enforce-profile-state-consistency
     */
    @PostMapping("/{userId}/enforce-profile-state-consistency")
    public ResponseEntity<Void> enforceProfileStateConsistency(@RequestHeader("X-User-Id") Long xUserId,
                                                               @PathVariable("userId") Long userId,
                                                               Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        systemRulesService.enforceProfileStateConsistency(user);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/system-rules/{userId}/apply-rules-on-wedding-enter
     */
    @PostMapping("/{userId}/apply-rules-on-wedding-enter")
    public ResponseEntity<User> applyRulesOnWeddingEnter(@RequestHeader("X-User-Id") Long xUserId,
                                                         @PathVariable("userId") Long userId,
                                                         Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        User updated = systemRulesService.applyRulesOnWeddingEnter(user);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/admin/system-rules/{userId}/apply-rules-on-wedding-exit
     */
    @PostMapping("/{userId}/apply-rules-on-wedding-exit")
    public ResponseEntity<User> applyRulesOnWeddingExit(@RequestHeader("X-User-Id") Long xUserId,
                                                        @PathVariable("userId") Long userId,
                                                        Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        User updated = systemRulesService.applyRulesOnWeddingExit(user);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/admin/system-rules/{userId}/apply-rules-on-global-approved
     */
    @PostMapping("/{userId}/apply-rules-on-global-approved")
    public ResponseEntity<User> applyRulesOnGlobalApproved(@RequestHeader("X-User-Id") Long xUserId,
                                                           @PathVariable("userId") Long userId,
                                                           Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        User updated = systemRulesService.applyRulesOnGlobalApproved(user);
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/admin/system-rules/{userId}/apply-rules-on-global-rejected?keepRequestFlag=false
     */
    @PostMapping("/{userId}/apply-rules-on-global-rejected")
    public ResponseEntity<User> applyRulesOnGlobalRejected(@RequestHeader("X-User-Id") Long xUserId,
                                                           @PathVariable("userId") Long userId,
                                                           @RequestParam(value = "keepRequestFlag", required = false, defaultValue = "false")
                                                           boolean keepRequestFlag,
                                                           Principal principal) {
        actorId(xUserId, principal);
        validatePositiveId(userId, "userId");

        User user = userAuthService.getUserOrThrow(userId);
        User updated = systemRulesService.applyRulesOnGlobalRejected(user, keepRequestFlag);
        return ResponseEntity.ok(updated);
    }
}
