package com.example.myproject.controller;

import com.example.myproject.model.Match;
import com.example.myproject.model.User;
import com.example.myproject.model.UserAction;
import com.example.myproject.model.enums.UserActionType;
import com.example.myproject.model.Wedding;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import com.example.myproject.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final WeddingRepository weddingRepository;

    public UserController(UserService userService,
                          UserRepository userRepository,
                          WeddingRepository weddingRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.weddingRepository = weddingRepository;
    }

    // ============================================================
    // 🔵 DTO פנימיים – Request Bodies
    // ============================================================

    public static class CreateUserRequest {
        public String fullName;
        public String phone;
        public String email;
        public String gender;
    }

    public static class LoginRequest {
        public String phoneOrEmail;
    }

    public static class PhoneVerificationRequest {
        public String phone;
        public String code;
    }

    public static class EmailVerificationRequest {
        public String email;
        public String code;
    }

    public static class BasicProfileRequest {
        public String fullName;
        public Integer age;
        public Integer heightCm;
        public String areaOfResidence;
        public String religiousLevel;
    }

    public static class FullProfileRequest {
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
        public String inquiriesPhone1;
        public String inquiriesPhone2;
    }

    public static class NotificationPrefsRequest {
        public boolean allowInApp;
        public boolean allowEmail;
        public boolean allowSms;
    }

    public static class PrimaryPhotoStatusRequest {
        public boolean hasPrimaryPhoto;
    }

    public static class UserInteractionRequest {
        public Long actorId;
        public Long targetId;
        public String actionType;
        public Long weddingId;
    }

    // ============================================================
    // 🔵 טיפול בשגיאות כלליות
    // ============================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    // ============================================================
    // 1. יצירת חשבון
    // ============================================================

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody CreateUserRequest req) {
        User user = userService.createUserAccount(req.fullName, req.phone, req.email, req.gender);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    // ============================================================
    // 2. התחברות
    // ============================================================

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody LoginRequest req) {
        User user = userService.loginUser(req.phoneOrEmail);
        return ResponseEntity.ok(user);
    }

    // ============================================================
    // 3. שליחת קוד אימות מחדש
    // ============================================================

    @PostMapping("/verification/phone/resend")
    public ResponseEntity<Map<String, String>> resendPhoneVerification(@RequestBody PhoneVerificationRequest req) {
        userService.sendPhoneVerificationCode(req.phone);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "קוד אימות נשלח מחדש לטלפון"));
    }

    @PostMapping("/verification/phone/send")
    public ResponseEntity<Map<String, String>> sendPhoneVerification(@RequestBody PhoneVerificationRequest req) {
        userService.sendPhoneVerificationCode(req.phone);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "קוד אימות נשלח לטלפון"
        ));
    }

    @PostMapping("/verification/email/send")
    public ResponseEntity<Map<String, String>> sendEmailVerification(@RequestBody EmailVerificationRequest req) {
        userService.sendEmailVerificationCode(req.email);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "קוד אימות נשלח לאימייל"
        ));
    }

    @PostMapping("/verification/email/resend")
    public ResponseEntity<Map<String, String>> resendEmailVerification(@RequestBody EmailVerificationRequest req) {
        userService.sendEmailVerificationCode(req.email);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "קוד אימות נשלח מחדש לאימייל"));
    }

    // ============================================================
    // 4. אימות חשבון
    // ============================================================

    @PostMapping("/verification/phone/confirm")
    public ResponseEntity<User> verifyByPhone(@RequestBody PhoneVerificationRequest req) {
        User user = userService.verifyUserByPhone(req.phone, req.code);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/verification/email/confirm")
    public ResponseEntity<User> verifyByEmail(@RequestBody EmailVerificationRequest req) {
        User user = userService.verifyUserByEmail(req.email, req.code);
        return ResponseEntity.ok(user);
    }

    // ============================================================
    // 5. שליפת משתמש בודד
    // ============================================================

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // ============================================================
    // 6. מחיקת חשבון / ביטול / ניקוי
    // ============================================================

    @PostMapping("/{id}/deletion/request")
    public ResponseEntity<Map<String, String>> requestDeletion(@PathVariable Long id) {
        userService.requestAccountDeletion(id);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "בקשת מחיקה נרשמה"));
    }

    @PostMapping("/{id}/deletion/cancel")
    public ResponseEntity<Map<String, String>> cancelDeletion(@PathVariable Long id) {
        userService.cancelAccountDeletion(id);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "בקשת מחיקה בוטלה"));
    }

    @DeleteMapping("/admin/purge-deleted")
    public ResponseEntity<Map<String, String>> purgeDeletedAccounts() {
        userService.purgeOldDeletedAccounts();
        return ResponseEntity.ok(Map.of("status", "OK", "message", "בוצע ניקוי חשבונות"));
    }

    // ============================================================
    // 7. פרופיל בסיסי
    // ============================================================

    @PutMapping("/{id}/profile/basic")
    public ResponseEntity<User> updateBasicProfile(@PathVariable Long id,
                                                   @RequestBody BasicProfileRequest req) {
        User updated = userService.updateBasicProfile(
                id, req.fullName, req.age, req.heightCm, req.areaOfResidence, req.religiousLevel);
        return ResponseEntity.ok(updated);
    }

    // ============================================================
    // 8. פרופיל מלא
    // ============================================================

    @PutMapping("/{id}/profile/full")
    public ResponseEntity<User> updateFullProfile(@PathVariable Long id,
                                                  @RequestBody FullProfileRequest req) {
        User updated = userService.updateFullProfile(
                id,
                req.bodyType, req.occupation, req.education, req.militaryService,
                req.maritalStatus, req.origin, req.personalityTraits, req.hobbies,
                req.familyDescription, req.lookingFor, req.preferredAgeFrom, req.preferredAgeTo,
                req.headCovering, req.hasDrivingLicense, req.smokes, req.inquiriesPhone1, req.inquiriesPhone2
        );
        return ResponseEntity.ok(updated);
    }

    // ============================================================
    // 9. העדפות התראות
    // ============================================================

    @PutMapping("/{id}/notifications/preferences")
    public ResponseEntity<User> updateNotificationPrefs(@PathVariable Long id,
                                                        @RequestBody NotificationPrefsRequest req) {
        User updated = userService.updateNotificationPreferences(
                id, req.allowInApp, req.allowEmail, req.allowSms);
        return ResponseEntity.ok(updated);
    }

    // ============================================================
    // 10. עדכון סטטוס תמונה ראשית
    // ============================================================

    @PutMapping("/{id}/photos/primary/status")
    public ResponseEntity<Map<String, String>> updatePrimaryPhotoStatus(@PathVariable Long id,
                                                                        @RequestBody PrimaryPhotoStatusRequest req) {
        userService.updatePrimaryPhotoStatus(id, req.hasPrimaryPhoto);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "עודכן"));
    }

    // ============================================================
    // 11. גישה למאגר גלובלי
    // ============================================================

    @PostMapping("/{id}/global/request")
    public ResponseEntity<User> requestGlobalAccess(@PathVariable Long id) {
        return ResponseEntity.ok(userService.requestGlobalAccess(id));
    }

    @PostMapping("/{id}/global/approve")
    public ResponseEntity<User> approveGlobalAccess(@PathVariable Long id) {
        return ResponseEntity.ok(userService.approveGlobalAccess(id));
    }

    @GetMapping("/global-pool")
    public ResponseEntity<List<User>> getGlobalPoolUsers() {
        return ResponseEntity.ok(userService.getGlobalPoolUsers());
    }

    // ============================================================
    // 12. תזכורת למילוי פרופיל
    // ============================================================

    @PostMapping("/{id}/profile/reminder")
    public ResponseEntity<Map<String, String>> sendProfileCompletionReminder(@PathVariable Long id) {
        userService.sendProfileCompletionReminder(id);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "תזכורת נשלחה"));
    }

    // ============================================================
    // 13. ספירת צפיות בפרופיל
    // ============================================================

    @PostMapping("/{id}/profile/view")
    public ResponseEntity<Map<String, String>> incrementProfileViews(@PathVariable Long id) {
        userService.incrementProfileViews(id);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "נצפתה צפייה"));
    }

    // ============================================================
    // 14. מצב חתונה
    // ============================================================

    @PostMapping("/{id}/wedding/enter")
    public ResponseEntity<User> enterWeddingMode(@PathVariable Long id,
                                                 @RequestParam Long weddingId) {
        return ResponseEntity.ok(userService.enterWeddingMode(id, weddingId));
    }

    @PostMapping("/{id}/wedding/exit")
    public ResponseEntity<User> exitWeddingMode(@PathVariable Long id) {
        return ResponseEntity.ok(userService.exitWeddingMode(id));
    }

    @GetMapping("/{id}/wedding/is-in-mode")
    public ResponseEntity<Map<String, Object>> isInWeddingMode(@PathVariable Long id) {
        boolean in = userService.isInWeddingMode(id);
        return ResponseEntity.ok(Map.of("userId", id, "inWeddingMode", in));
    }

    // ============================================================
    // 15. פעולות משתמשים (LIKE / FREEZE / DISLIKE)
    // ============================================================

    @PostMapping("/interactions")
    public ResponseEntity<Map<String, String>> performInteraction(@RequestBody UserInteractionRequest req) {

        if (req.actorId == null || req.targetId == null || req.actionType == null) {
            throw new IllegalArgumentException("actorId, targetId ו-actionType הם חובה");
        }

        UserActionType type = UserActionType.valueOf(req.actionType.toUpperCase());
        String result = userService.performUserInteraction(
                req.actorId, req.targetId, type, req.weddingId);

        return ResponseEntity.ok(Map.of("status", "OK", "result", result));
    }

    // ============================================================
    // 16. רשימות (1–5)
    // ============================================================

    @GetMapping("/{id}/lists/liked")
    public ResponseEntity<List<UserAction>> getUsersILiked(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUsersILiked(id));
    }

    @GetMapping("/{id}/lists/pending-likes")
    public ResponseEntity<List<UserAction>> getUsersWhoLikedMeWaiting(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUsersWhoLikedMeAndWaitingForMyResponse(id));
    }

    @GetMapping("/{id}/lists/pending-likes/alias")
    public ResponseEntity<List<UserAction>> getPendingLikesAlias(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getPendingLikes(id));
    }

    @GetMapping("/{id}/lists/disliked")
    public ResponseEntity<List<UserAction>> getDislikedUsers(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getDislikedUsers(id));
    }

    @GetMapping("/{id}/lists/frozen")
    public ResponseEntity<List<UserAction>> getFrozenUsers(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getFrozenUsers(id));
    }

    // ============================================================
    // 17. התאמות (Matches)
    // ============================================================

    @GetMapping("/{id}/matches/mutual")
    public ResponseEntity<List<Match>> getMutualMatches(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getMutualMatches(id));
    }

    @GetMapping("/{id}/matches/active")
    public ResponseEntity<List<Match>> getActiveMatches(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getActiveMatches(id));
    }

    @GetMapping("/{id}/matches/waiting-approval")
    public ResponseEntity<List<Match>> getMatchesWaitingForApproval(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getMatchesWaitingForMyApproval(id));
    }

    // ============================================================
    // 18. שאילתות Admin / Dashboard
    // ============================================================

    @GetMapping("/by-phone")
    public ResponseEntity<User> getByPhone(@RequestParam String phone) {
        return userRepository.findByPhone(phone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/by-email")
    public ResponseEntity<User> getByEmail(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/exists/phone")
    public ResponseEntity<Map<String, Object>> existsByPhone(@RequestParam String phone) {
        return ResponseEntity.ok(Map.of("phone", phone, "exists", userRepository.existsByPhone(phone)));
    }

    @GetMapping("/exists/email")
    public ResponseEntity<Map<String, Object>> existsByEmail(@RequestParam String email) {
        return ResponseEntity.ok(Map.of("email", email, "exists", userRepository.existsByEmail(email)));
    }

    @GetMapping("/verified")
    public ResponseEntity<List<User>> getVerifiedUsers() {
        return ResponseEntity.ok(userRepository.findByVerifiedTrue());
    }

    @GetMapping("/verified/pending")
    public ResponseEntity<List<User>> getUnverifiedUsers() {
        return ResponseEntity.ok(userRepository.findByVerifiedFalse());
    }

    @GetMapping("/profiles/basic-completed")
    public ResponseEntity<List<User>> getBasicCompleted() {
        return ResponseEntity.ok(userRepository.findByBasicProfileCompletedTrue());
    }

    @GetMapping("/profiles/full-completed")
    public ResponseEntity<List<User>> getFullCompleted() {
        return ResponseEntity.ok(userRepository.findByFullProfileCompletedTrue());
    }

    @GetMapping("/profiles/full-completed/with-photo")
    public ResponseEntity<List<User>> getFullCompletedWithPhoto() {
        return ResponseEntity.ok(userRepository.findCompletedFullProfileWithPhoto());
    }

    @GetMapping("/profiles/basic-completed/with-photo")
    public ResponseEntity<List<User>> getBasicCompletedWithPhoto() {
        return ResponseEntity.ok(userRepository.findCompletedBasicProfileWithPhoto());
    }

    @GetMapping("/global/requests")
    public ResponseEntity<List<User>> getGlobalAccessRequests() {
        return ResponseEntity.ok(userRepository.findByGlobalAccessRequestTrue());
    }

    @GetMapping("/global/approved")
    public ResponseEntity<List<User>> getGlobalAccessApproved() {
        return ResponseEntity.ok(userRepository.findByGlobalAccessApprovedTrue());
    }

    @GetMapping("/global/eligible")
    public ResponseEntity<List<User>> getEligibleForGlobalPool() {
        return ResponseEntity.ok(userRepository.findEligibleForGlobalPool());
    }

    @GetMapping("/by-background-wedding/{weddingId}")
    public ResponseEntity<List<User>> getByBackgroundWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(userRepository.findByBackgroundWeddingId(weddingId));
    }

    @GetMapping("/by-wedding-history/{weddingId}")
    public ResponseEntity<List<User>> getUsersWhoAttendedWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(userRepository.findUsersWhoAttendedWedding(weddingId));
    }

    @GetMapping("/by-first-wedding/{weddingId}")
    public ResponseEntity<List<User>> getByFirstWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(userRepository.findByFirstWeddingId(weddingId));
    }

    @GetMapping("/by-last-wedding/{weddingId}")
    public ResponseEntity<List<User>> getByLastWedding(@PathVariable Long weddingId) {
        return ResponseEntity.ok(userRepository.findByLastWeddingId(weddingId));
    }

    @GetMapping("/can-view-wedding")
    public ResponseEntity<List<User>> getUsersCanViewWedding() {
        return ResponseEntity.ok(userRepository.findByCanViewWeddingTrue());
    }

    // === ✔️ כאן היה הבאג → תוקן! ===
    @GetMapping("/event-owners/{weddingId}")
    public ResponseEntity<User> getEventOwnerForWedding(@PathVariable Long weddingId) {

        Wedding wedding = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("חתונה לא נמצאה"));

        User owner = userRepository.findById(wedding.getOwnerUserId())
                .orElseThrow(() -> new IllegalArgumentException("בעל האירוע לא נמצא"));

        return ResponseEntity.ok(owner);
    }

    @GetMapping("/notifications/in-app")
    public ResponseEntity<List<User>> getAllowInAppNotifications() {
        return ResponseEntity.ok(userRepository.findByAllowInAppNotificationsTrue());
    }

    @GetMapping("/notifications/email")
    public ResponseEntity<List<User>> getAllowEmailNotifications() {
        return ResponseEntity.ok(userRepository.findByAllowEmailNotificationsTrue());
    }

    @GetMapping("/notifications/sms")
    public ResponseEntity<List<User>> getAllowSmsNotifications() {
        return ResponseEntity.ok(userRepository.findByAllowSmsNotificationsTrue());
    }

    @GetMapping("/deletion/requests")
    public ResponseEntity<List<User>> getDeletionRequestedUsers() {
        return ResponseEntity.ok(userRepository.findByDeletionRequestedTrue());
    }

    @GetMapping("/search/by-name")
    public ResponseEntity<List<User>> searchByName(@RequestParam String name) {
        return ResponseEntity.ok(userRepository.findByFullNameContainingIgnoreCase(name));
    }

    @GetMapping("/search/by-area")
    public ResponseEntity<List<User>> searchByArea(@RequestParam String area) {
        return ResponseEntity.ok(userRepository.findByAreaOfResidenceContainingIgnoreCase(area));
    }

    @GetMapping("/search/by-occupation")
    public ResponseEntity<List<User>> searchByOccupation(@RequestParam String occupation) {
        return ResponseEntity.ok(userRepository.findByOccupationContainingIgnoreCase(occupation));
    }

    @GetMapping("/search/by-education")
    public ResponseEntity<List<User>> searchByEducation(@RequestParam String education) {
        return ResponseEntity.ok(userRepository.findByEducationContainingIgnoreCase(education));
    }

    @GetMapping("/search/by-origin")
    public ResponseEntity<List<User>> searchByOrigin(@RequestParam String origin) {
        return ResponseEntity.ok(userRepository.findByOriginContainingIgnoreCase(origin));
    }

    @GetMapping("/search/by-gender")
    public ResponseEntity<List<User>> searchByGender(@RequestParam String gender) {
        return ResponseEntity.ok(userRepository.findByGender(gender));
    }

    @GetMapping("/ai/with-embedding")
    public ResponseEntity<List<User>> getUsersWithEmbedding() {
        return ResponseEntity.ok(userRepository.findByAiEmbeddingIsNotNull());
    }

    @GetMapping("/ai/boosted")
    public ResponseEntity<List<User>> getUsersWithAiBoost(@RequestParam("minScore") Double minScore) {
        return ResponseEntity.ok(userRepository.findByAiMatchBoostScoreGreaterThan(minScore));
    }
}