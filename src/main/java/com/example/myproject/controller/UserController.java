package com.example.myproject.controller;

import com.example.myproject.model.User;
import com.example.myproject.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * UserController
 * חשיפה של כל הפעולות העיקריות על משתמשים כ-REST API.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService; // שכבת שירות לניהול משתמשים

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ----------------------------------------------------
    // 1. רישום, אימות, התחברות
    // ----------------------------------------------------

    /**
     * רישום משתמש חדש (פרטים בסיסיים).
     */
    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {
        return userService.registerUser(
                request.getFullName(),
                request.getPhone(),
                request.getEmail(),
                request.getGender()
        );
    }

    /**
     * בקשת קוד אימות מחדש ב-SMS (לפי טלפון).
     */
    @PostMapping("/verification/sms")
    public ResponseEntity<Void> requestSmsCode(@RequestBody PhoneRequest request) {
        userService.requestSmsVerificationCode(request.getPhone());
        return ResponseEntity.ok().build();
    }

    /**
     * בקשת קוד אימות מחדש באימייל (לפי אימייל).
     */
    @PostMapping("/verification/email")
    public ResponseEntity<Void> requestEmailCode(@RequestBody EmailRequest request) {
        userService.requestEmailVerificationCode(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * אימות משתמש לפי טלפון + קוד.
     */
    @PostMapping("/verify/phone")
    public User verifyByPhone(@RequestBody VerifyPhoneRequest request) {
        return userService.verifyByPhone(request.getPhone(), request.getCode());
    }

    /**
     * אימות משתמש לפי אימייל + קוד.
     */
    @PostMapping("/verify/email")
    public User verifyByEmail(@RequestBody VerifyEmailRequest request) {
        return userService.verifyByEmail(request.getEmail(), request.getCode());
    }

    /**
     * "התחברות" לפי טלפון / אימייל.
     */
    @PostMapping("/login")
    public User login(@RequestBody LoginRequest request) {
        return userService.loginByPhoneOrEmail(request.getIdentifier());
    }

    // ----------------------------------------------------
    // 2. שליפה בסיסית של משתמשים
    // ----------------------------------------------------

    /**
     * הבאת פרטי משתמש לפי ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        Optional<User> userOpt = userService.getUserById(id);
        return userOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * כל המשתמשים שבמאגר הכללי.
     */
    @GetMapping("/global-pool")
    public List<User> getGlobalPoolUsers() {
        return userService.getGlobalPoolUsers();
    }

    // ----------------------------------------------------
    // 3. פרופיל בסיסי + מלא
    // ----------------------------------------------------

    /**
     * עדכון פרופיל בסיסי של משתמש.
     */
    @PutMapping("/{id}/basic-profile")
    public User updateBasicProfile(@PathVariable Long id,
                                   @RequestBody BasicProfileRequest request) {

        return userService.updateBasicProfile(
                id,
                request.getFullName(),
                request.getAge(),
                request.getHeightCm(),
                request.getAreaOfResidence(),
                request.getReligiousLevel()
        );
    }

    /**
     * עדכון פרופיל מלא של משתמש.
     */
    @PutMapping("/{id}/full-profile")
    public User updateFullProfile(@PathVariable Long id,
                                  @RequestBody FullProfileRequest request) {

        return userService.updateFullProfile(
                id,
                request.getBodyType(),
                request.getOccupation(),
                request.getEducation(),
                request.getMilitaryService(),
                request.getMaritalStatus(),
                request.getOrigin(),
                request.getPersonalityTraits(),
                request.getHobbies(),
                request.getFamilyDescription(),
                request.getLookingFor(),
                request.getPreferredAgeFrom(),
                request.getPreferredAgeTo(),
                request.getHeadCovering(),
                request.getHasDrivingLicense(),
                request.getSmokes(),
                request.getInquiriesPhone1(),
                request.getInquiriesPhone2()
        );
    }

    // ----------------------------------------------------
    // 4. העדפות התראות
    // ----------------------------------------------------

    /**
     * עדכון העדפות התראות (In-App, Email, SMS).
     */
    @PutMapping("/{id}/notification-preferences")
    public User updateNotificationPreferences(@PathVariable Long id,
                                              @RequestBody NotificationPrefsRequest request) {

        return userService.updateNotificationPreferences(
                id,
                request.isAllowInApp(),
                request.isAllowEmail(),
                request.isAllowSms()
        );
    }

    // ----------------------------------------------------
    // 5. מאגר כללי + גישה גלובלית
    // ----------------------------------------------------

    /**
     * עדכון סטטוס מאגר כללי (כניסה/יציאה).
     */
    @PutMapping("/{id}/global-pool")
    public User updateGlobalPool(@PathVariable Long id,
                                 @RequestBody GlobalPoolRequest request) {

        return userService.updateGlobalPoolStatus(id, request.isInGlobalPool());
    }

    /**
     * בקשת גישה גלובלית (שדה globalAccessRequest=true).
     */
    @PostMapping("/{id}/request-global")
    public User requestGlobalAccess(@PathVariable Long id) {
        return userService.requestGlobalAccess(id);
    }

    /**
     * אישור גישה גלובלית (שדה globalAccessApproved=true).
     * בפועל ייקרא ע"י ממשק מנהל.
     */
    @PostMapping("/{id}/approve-global")
    public User approveGlobalAccess(@PathVariable Long id) {
        return userService.approveGlobalAccess(id);
    }

    // ----------------------------------------------------
    // 6. מחיקת חשבון
    // ----------------------------------------------------

    /**
     * בקשה למחיקת חשבון (soft delete – 30 יום).
     */
    @PostMapping("/{id}/request-deletion")
    public ResponseEntity<Void> requestAccountDeletion(@PathVariable Long id) {
        userService.requestAccountDeletion(id);
        return ResponseEntity.ok().build();
    }

    /**
     * ביטול בקשת מחיקת חשבון.
     */
    @PostMapping("/{id}/cancel-deletion")
    public ResponseEntity<Void> cancelAccountDeletion(@PathVariable Long id) {
        userService.cancelAccountDeletion(id);
        return ResponseEntity.ok().build();
    }

    /**
     * הרצה ידנית של Purge – מחיקה פיזית של חשבונות שעברו 30 יום.
     * (למנהל מערכת / CRON).
     */
    @PostMapping("/purge-deleted")
    public ResponseEntity<Void> purgeDeleted() {
        userService.purgeDeletedUsersOlderThan30Days();
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------
    // 7. תזכורת להשלים פרופיל
    // ----------------------------------------------------

    /**
     * שליחת תזכורת למשתמש להשלים פרופיל.
     */
    @PostMapping("/{id}/profile-reminder")
    public ResponseEntity<Void> sendProfileReminder(@PathVariable Long id) {
        userService.sendProfileIncompleteReminderIfNeeded(id);
        return ResponseEntity.ok().build();
    }

    // ====================================================
    //             DTO פנימיים לבקשות JSON
    // ====================================================

    // --- רישום משתמש חדש ---
    public static class RegisterRequest {
        private String fullName;
        private String phone;
        private String email;
        private String gender;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
    }

    // --- בקשות פשוטות: טלפון / אימייל / לוגין ---
    public static class PhoneRequest {
        private String phone;
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class EmailRequest {
        private String email;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class LoginRequest {
        private String identifier;
        public String getIdentifier() { return identifier; }
        public void setIdentifier(String identifier) { this.identifier = identifier; }
    }

    // --- אימות קוד ---
    public static class VerifyPhoneRequest {
        private String phone;
        private String code;

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class VerifyEmailRequest {
        private String email;
        private String code;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    // --- פרופיל בסיסי ---
    public static class BasicProfileRequest {
        private String fullName;
        private Integer age;
        private Integer heightCm;
        private String areaOfResidence;
        private String religiousLevel;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }

        public Integer getHeightCm() { return heightCm; }
        public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }

        public String getAreaOfResidence() { return areaOfResidence; }
        public void setAreaOfResidence(String areaOfResidence) { this.areaOfResidence = areaOfResidence; }

        public String getReligiousLevel() { return religiousLevel; }
        public void setReligiousLevel(String religiousLevel) { this.religiousLevel = religiousLevel; }
    }

    // --- פרופיל מלא ---
    public static class FullProfileRequest {
        private String bodyType;
        private String occupation;
        private String education;
        private String militaryService;
        private String maritalStatus;
        private String origin;
        private String personalityTraits;
        private String hobbies;
        private String familyDescription;
        private String lookingFor;
        private Integer preferredAgeFrom;
        private Integer preferredAgeTo;
        private String headCovering;
        private Boolean hasDrivingLicense;
        private Boolean smokes;
        private String inquiriesPhone1;
        private String inquiriesPhone2;

        public String getBodyType() { return bodyType; }
        public void setBodyType(String bodyType) { this.bodyType = bodyType; }

        public String getOccupation() { return occupation; }
        public void setOccupation(String occupation) { this.occupation = occupation; }

        public String getEducation() { return education; }
        public void setEducation(String education) { this.education = education; }

        public String getMilitaryService() { return militaryService; }
        public void setMilitaryService(String militaryService) { this.militaryService = militaryService; }

        public String getMaritalStatus() { return maritalStatus; }
        public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }

        public String getPersonalityTraits() { return personalityTraits; }
        public void setPersonalityTraits(String personalityTraits) { this.personalityTraits = personalityTraits; }

        public String getHobbies() { return hobbies; }
        public void setHobbies(String hobbies) { this.hobbies = hobbies; }

        public String getFamilyDescription() { return familyDescription; }
        public void setFamilyDescription(String familyDescription) { this.familyDescription = familyDescription; }

        public String getLookingFor() { return lookingFor; }
        public void setLookingFor(String lookingFor) { this.lookingFor = lookingFor; }

        public Integer getPreferredAgeFrom() { return preferredAgeFrom; }
        public void setPreferredAgeFrom(Integer preferredAgeFrom) { this.preferredAgeFrom = preferredAgeFrom; }

        public Integer getPreferredAgeTo() { return preferredAgeTo; }
        public void setPreferredAgeTo(Integer preferredAgeTo) { this.preferredAgeTo = preferredAgeTo; }

        public String getHeadCovering() { return headCovering; }
        public void setHeadCovering(String headCovering) { this.headCovering = headCovering; }

        public Boolean getHasDrivingLicense() { return hasDrivingLicense; }
        public void setHasDrivingLicense(Boolean hasDrivingLicense) { this.hasDrivingLicense = hasDrivingLicense; }

        public Boolean getSmokes() { return smokes; }
        public void setSmokes(Boolean smokes) { this.smokes = smokes; }

        public String getInquiriesPhone1() { return inquiriesPhone1; }
        public void setInquiriesPhone1(String inquiriesPhone1) { this.inquiriesPhone1 = inquiriesPhone1; }

        public String getInquiriesPhone2() { return inquiriesPhone2; }
        public void setInquiriesPhone2(String inquiriesPhone2) { this.inquiriesPhone2 = inquiriesPhone2; }
    }

    // --- העדפות התראות ---
    public static class NotificationPrefsRequest {
        private boolean allowInApp;
        private boolean allowEmail;
        private boolean allowSms;

        public boolean isAllowInApp() { return allowInApp; }
        public void setAllowInApp(boolean allowInApp) { this.allowInApp = allowInApp; }

        public boolean isAllowEmail() { return allowEmail; }
        public void setAllowEmail(boolean allowEmail) { this.allowEmail = allowEmail; }

        public boolean isAllowSms() { return allowSms; }
        public void setAllowSms(boolean allowSms) { this.allowSms = allowSms; }
    }

    // --- מאגר כללי ---
    public static class GlobalPoolRequest {
        private boolean inGlobalPool;

        public boolean isInGlobalPool() { return inGlobalPool; }
        public void setInGlobalPool(boolean inGlobalPool) { this.inGlobalPool = inGlobalPool; }
    }
}