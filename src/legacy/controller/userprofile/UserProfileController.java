package com.example.myproject.controller.userprofile;

import com.example.myproject.model.User;
import com.example.myproject.service.User.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // =====================================================
    // Helper: Actor ID from header (SSOT rule)
    // =====================================================

    private Long actorId(Long headerId, Principal principal) {
        // If an SSOT Addendum exists with real JWT/Security extraction, use it ONLY.
        if (headerId == null) throw new IllegalArgumentException("Missing X-User-Id header");
        return headerId;
    }

    // =====================================================
    // updateBasicProfile(...)
    // =====================================================

    @PutMapping("/update-basic-profile")
    public ResponseEntity<User> updateBasicProfile(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal,
            @RequestBody UpdateBasicProfileRequest request
    ) {
        Long userId = actorId(xUserId, principal);

        if (request.age != null && request.age < 0) {
            throw new IllegalArgumentException("age must be >= 0");
        }
        if (request.heightCm != null && request.heightCm < 0) {
            throw new IllegalArgumentException("heightCm must be >= 0");
        }
        if (request.areaOfResidence != null && request.areaOfResidence.trim().isEmpty()) {
            throw new IllegalArgumentException("areaOfResidence cannot be blank");
        }
        if (request.religiousLevel != null && request.religiousLevel.trim().isEmpty()) {
            throw new IllegalArgumentException("religiousLevel cannot be blank");
        }

        User updated = userProfileService.updateBasicProfile(
                userId,
                request.age,
                request.heightCm,
                request.areaOfResidence,
                request.religiousLevel
        );

        return ResponseEntity.ok(updated);
    }

    public static class UpdateBasicProfileRequest {
        public Integer age;
        public Integer heightCm;
        public String areaOfResidence;
        public String religiousLevel;
    }

    // =====================================================
    // updateFullProfile(...)
    // =====================================================

    @PutMapping("/update-full-profile")
    public ResponseEntity<User> updateFullProfile(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal,
            @RequestBody UpdateFullProfileRequest request
    ) {
        Long userId = actorId(xUserId, principal);

        if (request.preferredAgeFrom != null && request.preferredAgeFrom < 0) {
            throw new IllegalArgumentException("preferredAgeFrom must be >= 0");
        }
        if (request.preferredAgeTo != null && request.preferredAgeTo < 0) {
            throw new IllegalArgumentException("preferredAgeTo must be >= 0");
        }
        if (request.preferredAgeFrom != null && request.preferredAgeTo != null
                && request.preferredAgeFrom > request.preferredAgeTo) {
            throw new IllegalArgumentException("preferredAgeFrom must be <= preferredAgeTo");
        }

        User updated = userProfileService.updateFullProfile(
                userId,
                request.bodyType,
                request.occupation,
                request.education,
                request.militaryService,
                request.maritalStatus,
                request.origin,
                request.personalityTraits,
                request.hobbies,
                request.familyDescription,
                request.lookingFor,
                request.preferredAgeFrom,
                request.preferredAgeTo,
                request.headCovering,
                request.hasDrivingLicense,
                request.smokes
        );

        return ResponseEntity.ok(updated);
    }

    public static class UpdateFullProfileRequest {
        public String bodyType;
        public String occupation;
        public String education;
        public String militaryService;
        public String maritalStatus;
        public String origin;
        public String personalityTraits;
        public String hobbies;
        public String familyDescription;
        public String lookingFor;
        public Integer preferredAgeFrom;
        public Integer preferredAgeTo;
        public String headCovering;
        public Boolean hasDrivingLicense;
        public Boolean smokes;
    }

    // =====================================================
    // lockProfileAfterWedding(...)
    // =====================================================

    @PostMapping("/lock-profile-after-wedding")
    public ResponseEntity<User> lockProfileAfterWedding(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal
    ) {
        Long userId = actorId(xUserId, principal);
        User updated = userProfileService.lockProfileAfterWedding(userId);
        return ResponseEntity.ok(updated);
    }

    // =====================================================
    // unlockProfile(...)
    // =====================================================

    @PostMapping("/unlock-profile")
    public ResponseEntity<User> unlockProfile(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal
    ) {
        Long userId = actorId(xUserId, principal);
        User updated = userProfileService.unlockProfile(userId);
        return ResponseEntity.ok(updated);
    }

    // =====================================================
    // incrementProfileViews(...)
    // =====================================================

    @PostMapping("/{userId}/increment-profile-views")
    public ResponseEntity<Void> incrementProfileViews(
            @RequestHeader("X-User-Id") Long xUserId,
            Principal principal,
            @PathVariable Long userId
    ) {
        actorId(xUserId, principal);

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (userId < 0) throw new IllegalArgumentException("userId must be >= 0");

        userProfileService.incrementProfileViews(userId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // Internal / hook methods (NOT exposed):
    // - recomputeProfileState(User user)
    // =====================================================
}