package com.example.myproject.service;

import com.example.myproject.dto.UserProfileResponse;
import com.example.myproject.model.Match;
import com.example.myproject.model.Notification;
import com.example.myproject.model.NotificationType;
import com.example.myproject.model.User;
import com.example.myproject.model.UserAction;
import com.example.myproject.model.UserActionCategory;
import com.example.myproject.model.UserActionType;
import com.example.myproject.model.UserPhoto;
import com.example.myproject.model.Wedding;
import com.example.myproject.repository.MatchRepository;
import com.example.myproject.repository.NotificationRepository;
import com.example.myproject.repository.UserActionRepository;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserActionRepository userActionRepository;
    private final MatchRepository matchRepository;
    private final WeddingRepository weddingRepository;
    private final UserPhotoService userPhotoService;

    private final Random random = new Random();

    public UserService(UserRepository userRepository,
                       NotificationRepository notificationRepository,
                       UserActionRepository userActionRepository,
                       MatchRepository matchRepository,
                       WeddingRepository weddingRepository,
                       UserPhotoService userPhotoService) {

        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.userActionRepository = userActionRepository;
        this.matchRepository = matchRepository;
        this.weddingRepository = weddingRepository;
        this.userPhotoService = userPhotoService;
    }

    // ===================================================================
    // 🔸 Helpers – כללי ברזל של 2025
    // ===================================================================

    /**
     * בדיקה: האם למשתמש יש לפחות תמונה ראשית אחת.
     * אם לא – זורקים 409 לוגי:
     * "כדי להשתמש במערכת או לבצע פעולה זו, עליך להעלות לפחות תמונה אחת"
     */
    private void assertHasPrimaryPhotoForAction(User user) {
        if (!user.isHasPrimaryPhoto()) {
            // mapped ע"י ControllerAdvice ל-HTTP 409
            throw new IllegalStateException("כדי להשתמש במערכת או לבצע פעולה זו, עליך להעלות לפחות תמונה אחת");
        }
    }

    /**
     * בדיקה: האם פרופיל הבסיס + הפרופיל המלא שלמים (כל שדות החובה).
     * אם לא – זורקים 409 לוגי:
     * "כדי להמשיך להשתמש במערכת, עליך למלא את כל פרטי החובה שבפרופיל"
     */
    private void assertProfileCompletedForAction(User user) {
        if (!user.isBasicProfileCompleted() || !user.isFullProfileCompleted()) {
            throw new IllegalStateException("כדי להמשיך להשתמש במערכת, עליך למלא את כל פרטי החובה שבפרופיל");
        }
    }

    /**
     * בדיקה מרוכזת: האם המשתמש רשאי לבצע פעולות חברתיות במערכת.
     * כל ההיסטוריה נשמרת – אבל ביצוע פעולות חדשות חסום עד שהכל מלא.
     * כולל חסימה של Admin / Event Manager.
     */
    private void assertUserEligibleForSocialActions(User user) {
        assertNotSystemUserForSocialActions(user);  // ⬅️ חדש
        assertHasPrimaryPhotoForAction(user);
        assertProfileCompletedForAction(user);
    }

    /**
     * כלי עזר קצר לשדה ריק.
     */
    private boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }

    // ===================================================================
    // 🔸 System Users Logic (Admin + Event Manager) – אפיון 2025
    // ===================================================================

    /**
     * משתמש מערכת = Admin / Event Manager
     * משתמשים אלו לא נחשבים "משתמשי שידוכים".
     */
    private boolean isSystemUser(User user) {
        return user.isAdmin() || user.isEventManager();
    }

    /**
     * חוסם משתמש מערכת מלבצע פעולות חברתיות (LIKE, DISLIKE, MATCH, FREEZE)
     */
    private void assertNotSystemUserForSocialActions(User user) {
        if (isSystemUser(user)) {
            throw new IllegalStateException(
                    "משתמש מערכת (Admin / Event Manager) אינו רשאי לבצע פעולות במנגנון השידוכים."
            );
        }
    }

    /**
     * חוסם משתמש מערכת מגישה למאגר הגלובלי
     */
    private void assertNotSystemUserForGlobalPool(User user) {
        if (isSystemUser(user)) {
            throw new IllegalStateException(
                    "משתמש מערכת לא יכול להיכנס למאגר הכללי."
            );
        }
    }

    /**
     * חוסם משתמש מערכת מלהיכנס למצב חתונה
     */
    private void assertNotSystemUserForWeddingMode(User user) {
        if (isSystemUser(user)) {
            throw new IllegalStateException(
                    "משתמש מערכת לא יכול להיכנס למצב חתונה."
            );
        }
    }

    // ===================================================================
    // 🔥 פונקציה מרכזית: שליפת פרופיל משתמש מלא (UserProfileResponse)
    // ===================================================================

    @Transactional(readOnly = true)
    public UserProfileResponse getFullUserProfile(Long userId) {

        User user = getUserOrThrow(userId);

        // שליפת כל התמונות הפעילות
        List<UserPhoto> activePhotos = userPhotoService.getActivePhotosForUser(userId);

        // שליפת כל התמונות (למסכים עתידיים / ניהול גלריה)
        List<UserPhoto> allPhotos = userPhotoService.getAllPhotosForUser(userId);

        // Primary photo
        UserPhoto primary = userPhotoService.getPrimaryPhotoForUser(userId);
        String primaryUrl = (primary != null ? primary.getImageUrl() : null);

        // האם יש לפחות תמונה?
        boolean hasAnyPhoto = !activePhotos.isEmpty();

        // האם יש primary?
        boolean hasPrimaryPhoto = (primary != null);

        // האם מותר למשתמש להיכנס למאגר הגלובלי?
        boolean canEnterGlobal =
                user.isFullProfileCompleted() &&
                        hasPrimaryPhoto;

        UserProfileResponse resp = new UserProfileResponse();

        // ========== מידע בסיסי ==========
        resp.setId(user.getId());
        resp.setFullName(user.getFullName());
        resp.setGender(user.getGender());
        resp.setAge(user.getAge());
        resp.setHeightCm(user.getHeightCm());
        resp.setAreaOfResidence(user.getAreaOfResidence());
        resp.setReligiousLevel(user.getReligiousLevel());

        // ========== פרופיל מורחב ==========
        resp.setBodyType(user.getBodyType());
        resp.setOccupation(user.getOccupation());
        resp.setEducation(user.getEducation());
        resp.setMilitaryService(user.getMilitaryService());
        resp.setMaritalStatus(user.getMaritalStatus());
        resp.setOrigin(user.getOrigin());
        resp.setPersonalityTraits(user.getPersonalityTraits());
        resp.setHobbies(user.getHobbies());
        resp.setFamilyDescription(user.getFamilyDescription());
        resp.setLookingFor(user.getLookingFor());
        resp.setSmokes(user.getSmokes());
        resp.setHasDrivingLicense(user.getHasDrivingLicense());
        resp.setHeadCovering(user.getHeadCovering());

        // ========== סטטוס פרופיל ==========
        resp.setBasicProfileCompleted(user.isBasicProfileCompleted());
        resp.setFullProfileCompleted(user.isFullProfileCompleted());
        resp.setHasAtLeastOnePhoto(hasAnyPhoto);
        resp.setHasPrimaryPhoto(hasPrimaryPhoto);

        // ========== מאגר גלובלי ==========
        resp.setInGlobalPool(user.isInGlobalPool());
        resp.setGlobalAccessApproved(user.isGlobalAccessApproved());
        resp.setGlobalAccessRequest(user.isGlobalAccessRequest());
        resp.setCanEnterGlobalPool(canEnterGlobal);

        // ========== חתונות ==========
        resp.setActiveWeddingId(user.getActiveWeddingId());
        resp.setBackgroundWeddingId(user.getBackgroundWeddingId());
        resp.setBackgroundMode(user.getBackgroundMode());

        resp.setFirstWeddingId(user.getFirstWeddingId());
        resp.setLastWeddingId(user.getLastWeddingId());
        resp.setWeddingsHistory(user.getWeddingsHistory());

        // ========== תמונות ==========
        resp.setPhotosCount(activePhotos.size());
        resp.setPrimaryPhotoUrl(primaryUrl);
        resp.setPhotos(
                allPhotos.stream()
                        .map(p -> new UserProfileResponse.PhotoDto(
                                p.getId(),
                                p.getImageUrl(),
                                p.isPrimaryPhoto(),
                                p.isDeleted(),
                                p.getPositionIndex()
                        ))
                        .toList()
        );

        // ========== תאריכים ==========
        resp.setCreatedAt(user.getCreatedAt());
        resp.setUpdatedAt(user.getUpdatedAt());

        return resp;
    }

    // ======================================================
    // 🔹 Utility – קוד אימות רנדומלי (6 ספרות)
    // ======================================================

    private String generateVerificationCode() {
        return String.valueOf(100000 + random.nextInt(900000));
    }

    // ======================================================
    // 🔹 יצירת חשבון משתמש חדש (Phone + Email חובה)
    //   (נכון גם למי שמגיע מחתונה וגם למי שנרשם מהאתר)
    // ======================================================

    @Transactional
    public User createUserAccount(String fullName,
                                  String phone,
                                  String email,
                                  String gender) {

        // בדיקות כפילות
        userRepository.findByPhone(phone).ifPresent(u -> {
            throw new IllegalStateException("טלפון כבר רשום במערכת");
        });
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new IllegalStateException("אימייל כבר רשום במערכת");
        });

        User user = new User();

        // שדות חובה (ה-Frontend יוודא שהכול מולא לפני שליחת הבקשה)
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setGender(gender);

        // מצב אימות
        user.setVerified(false);
        user.setVerificationCode(generateVerificationCode());

        // סטטוסי פרופיל (טרם כרטיס מלא)
        user.setBasicProfileCompleted(false);
        user.setFullProfileCompleted(false);
        user.setHasPrimaryPhoto(false);
        user.setPhotosCount(0);

        // מאגר גלובלי
        user.setInGlobalPool(false);
        user.setGlobalAccessApproved(false);
        user.setGlobalAccessRequest(false);

        // מחיקה
        user.setDeletionRequested(false);

        // רקע – ברירת מחדל (מאגר כללי)
        user.setBackgroundMode("DEFAULT");
        user.setActiveWeddingId(null);
        user.setBackgroundWeddingId(null);

        // זמנים
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 שליחת קוד אימות SMS מחדש
    // ======================================================

    @Transactional
    public void sendPhoneVerificationCode(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא קיים"));

        user.setVerificationCode(generateVerificationCode());
        user.setUpdatedAt(LocalDateTime.now());

        // NOTE: שליחת SMS אמיתית תיעשה בשירות חיצוני
        userRepository.save(user);
    }

    // ======================================================
    // 🔹 שליחת קוד אימות Email מחדש
    // ======================================================

    @Transactional
    public void sendEmailVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא קיים"));

        user.setVerificationCode(generateVerificationCode());
        user.setUpdatedAt(LocalDateTime.now());

        // NOTE: שליחת Email אמיתי תיעשה בשירות חיצוני
        userRepository.save(user);
    }

    // ======================================================
    // 🔹 אימות SMS לפי טלפון
    // ======================================================

    @Transactional
    public User verifyUserByPhone(String phone, String code) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));

        if (!code.equals(user.getVerificationCode())) {
            throw new IllegalArgumentException("קוד אימות שגוי");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setUpdatedAt(LocalDateTime.now());

        // ביטול בקשת מחיקה אם הייתה
        if (user.isDeletionRequested()) {
            user.setDeletionRequested(false);
            user.setDeletionRequestedAt(null);
        }

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 אימות Email לפי קוד
    // ======================================================

    @Transactional
    public User verifyUserByEmail(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));

        if (!code.equals(user.getVerificationCode())) {
            throw new IllegalArgumentException("קוד אימות שגוי");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setUpdatedAt(LocalDateTime.now());

        if (user.isDeletionRequested()) {
            user.setDeletionRequested(false);
            user.setDeletionRequestedAt(null);
        }

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 התחברות לפי טלפון / אימייל
    // ======================================================

    @Transactional(readOnly = true)
    public User loginUser(String phoneOrEmail) {

        Optional<User> phone = userRepository.findByPhone(phoneOrEmail);
        if (phone.isPresent()) {
            if (!phone.get().isVerified()) {
                throw new IllegalStateException("המשתמש לא אימת חשבון");
            }
            return phone.get();
        }

        Optional<User> email = userRepository.findByEmail(phoneOrEmail);
        if (email.isPresent()) {
            if (!email.get().isVerified()) {
                throw new IllegalStateException("המשתמש לא אימת חשבון");
            }
            return email.get();
        }

        throw new IllegalArgumentException("לא קיים משתמש עם פרטים אלו");
    }
    // ======================================================
    // 🔹 בקשת מחיקת חשבון (Soft Delete)
    // ======================================================

    @Transactional
    public void requestAccountDeletion(Long userId) {
        User user = getUserOrThrow(userId);

        if (user.isDeletionRequested()) return;

        user.setDeletionRequested(true);
        user.setDeletionRequestedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        // התראה למשתמש
        Notification n = new Notification();
        n.setRecipient(user);
        n.setType(NotificationType.ACCOUNT_DELETION_SCHEDULED);
        n.setTitle("בקשת מחיקת חשבון");
        n.setMessage("החשבון יימחק סופית בעוד 30 יום אלא אם תבטל.");
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    // ======================================================
    // 🔹 ביטול בקשת מחיקה
    // ======================================================

    @Transactional
    public void cancelAccountDeletion(Long userId) {
        User user = getUserOrThrow(userId);

        if (!user.isDeletionRequested()) return;

        user.setDeletionRequested(false);
        user.setDeletionRequestedAt(null);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        Notification n = new Notification();
        n.setRecipient(user);
        n.setType(NotificationType.ACCOUNT_DELETION_CANCELLED);
        n.setTitle("בקשה בוטלה");
        n.setMessage("מחיקת החשבון בוטלה.");
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    // ======================================================
    // 🔹 מחיקה פיזית אחרי 30 יום
    // ======================================================

    @Transactional
    public void purgeOldDeletedAccounts() {
        LocalDateTime threshold = LocalDateTime.now().minus(30, ChronoUnit.DAYS);

        userRepository.findAll().stream()
                .filter(User::isDeletionRequested)
                .filter(u -> u.getDeletionRequestedAt() != null &&
                        u.getDeletionRequestedAt().isBefore(threshold))
                .forEach(userRepository::delete);
    }

    // ======================================================
    // 🔹 עזר פנימי – שליפת משתמש או זריקת שגיאה
    // ======================================================

    public User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));
    }

    // ======================================================
    // 🔹 שליפה בסיסית – Get User
    // ======================================================

    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    // ======================================================
    //      UserService – Profile + Preferences + Global Pool + Background
    // ======================================================

    // ======================================================
    // 🔹 עדכון פרופיל בסיסי (Basic Profile)
    // ======================================================

    @Transactional
    public User updateBasicProfile(Long userId,
                                   String fullName,
                                   Integer age,
                                   Integer heightCm,
                                   String areaOfResidence,
                                   String religiousLevel) {

        User user = getUserOrThrow(userId);

        if (fullName != null && !fullName.isBlank())
            user.setFullName(fullName.trim());

        if (age != null && age > 0)
            user.setAge(age);

        if (heightCm != null && heightCm > 0)
            user.setHeightCm(heightCm);

        if (areaOfResidence != null && !areaOfResidence.isBlank())
            user.setAreaOfResidence(areaOfResidence.trim());

        if (religiousLevel != null && !religiousLevel.isBlank())
            user.setReligiousLevel(religiousLevel.trim());

        // בדיקה אם כל השדות הדרושים מלאים
        boolean basicCompleted =
                notEmpty(user.getFullName()) &&
                        user.getAge() != null &&
                        user.getHeightCm() != null &&
                        notEmpty(user.getAreaOfResidence()) &&
                        notEmpty(user.getReligiousLevel());

        user.setBasicProfileCompleted(basicCompleted);
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 עדכון פרופיל מלא (Full Profile)
    // ======================================================

    @Transactional
    public User updateFullProfile(Long userId,
                                  String bodyType,
                                  String occupation,
                                  String education,
                                  String militaryService,
                                  String maritalStatus,
                                  String origin,
                                  String personalityTraits,
                                  String hobbies,
                                  String familyDescription,
                                  String lookingFor,
                                  Integer preferredAgeFrom,
                                  Integer preferredAgeTo,
                                  String headCovering,
                                  Boolean hasDrivingLicense,
                                  Boolean smokes,
                                  String inquiriesPhone1,
                                  String inquiriesPhone2) {

        User user = getUserOrThrow(userId);

        if (bodyType != null) user.setBodyType(bodyType);
        if (occupation != null) user.setOccupation(occupation);
        if (education != null) user.setEducation(education);
        if (militaryService != null) user.setMilitaryService(militaryService);
        if (maritalStatus != null) user.setMaritalStatus(maritalStatus);
        if (origin != null) user.setOrigin(origin);
        if (personalityTraits != null) user.setPersonalityTraits(personalityTraits);
        if (hobbies != null) user.setHobbies(hobbies);
        if (familyDescription != null) user.setFamilyDescription(familyDescription);
        if (lookingFor != null) user.setLookingFor(lookingFor);
        if (preferredAgeFrom != null) user.setPreferredAgeFrom(preferredAgeFrom);
        if (preferredAgeTo != null) user.setPreferredAgeTo(preferredAgeTo);
        if (headCovering != null) user.setHeadCovering(headCovering);
        if (hasDrivingLicense != null) user.setHasDrivingLicense(hasDrivingLicense);
        if (smokes != null) user.setSmokes(smokes);
        if (inquiriesPhone1 != null) user.setInquiriesPhone1(inquiriesPhone1);
        if (inquiriesPhone2 != null) user.setInquiriesPhone2(inquiriesPhone2);

        boolean fullCompleted =
                user.isBasicProfileCompleted() &&
                        notEmpty(user.getBodyType()) &&
                        notEmpty(user.getOccupation()) &&
                        notEmpty(user.getEducation()) &&
                        notEmpty(user.getMilitaryService()) &&
                        notEmpty(user.getMaritalStatus()) &&
                        notEmpty(user.getOrigin()) &&
                        notEmpty(user.getPersonalityTraits()) &&
                        notEmpty(user.getHobbies()) &&
                        notEmpty(user.getFamilyDescription()) &&
                        notEmpty(user.getLookingFor()) &&
                        user.getPreferredAgeFrom() != null &&
                        user.getPreferredAgeTo() != null;

        user.setFullProfileCompleted(fullCompleted);
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 עדכון העדפות התראות (In-App / Email / SMS)
    // ======================================================

    @Transactional
    public User updateNotificationPreferences(Long userId,
                                              boolean allowInApp,
                                              boolean allowEmail,
                                              boolean allowSms) {

        User user = getUserOrThrow(userId);

        user.setAllowInAppNotifications(allowInApp);
        user.setAllowEmailNotifications(allowEmail);
        user.setAllowSmsNotifications(allowSms);

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 סימון האם יש תמונה ראשית (סטטוס בלבד)
    //     *לוגיקת טעינה / מחיקה / קבצים — ב־UserPhotoService*
    // ======================================================

    @Transactional
    public void updatePrimaryPhotoStatus(Long userId, boolean hasPhoto) {
        User user = getUserOrThrow(userId);
        user.setHasPrimaryPhoto(hasPhoto);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ======================================================
    // 🔹 בקשת גישה למאגר הגלובלי (request)
    //     (כללי ברזל: חייב פרופיל מלא + תמונה ראשית)
    // ======================================================

    @Transactional
    public User requestGlobalAccess(Long userId) {
        User user = getUserOrThrow(userId);

        // ⬅️ חדש: חסימת Admin / Event Manager
        assertNotSystemUserForGlobalPool(user);

        if (!user.isFullProfileCompleted() || !user.isHasPrimaryPhoto()) {
            throw new IllegalStateException("כדי לבקש גישה גלובלית יש להשלים פרופיל מלא + תמונה ראשית.");
        }

        user.setGlobalAccessRequest(true);
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        // התראה למשתמש – בקשה התקבלה
        createSimpleNotification(
                saved,
                NotificationType.GLOBAL_ACCESS_REQUESTED,
                "הבקשה למאגר הכללי התקבלה",
                "הבקשה שלך למאגר הכללי התקבלה ותטופל ע״י מנהל המערכת."
        );

        return saved;
    }

    // ======================================================
    // 🔹 אישור גישה גלובלית ע"י מנהל (approve)
    // ======================================================

    @Transactional
    public User approveGlobalAccess(Long userId) {
        User user = getUserOrThrow(userId);

        // ⬅️ חדש: חסימת Admin / Event Manager
        assertNotSystemUserForGlobalPool(user);

        if (!user.isFullProfileCompleted() || !user.isHasPrimaryPhoto()) {
            throw new IllegalStateException("אי אפשר לאשר מאגר כללי למשתמש בלי פרופיל מלא + תמונה ראשית.");
        }

        user.setGlobalAccessApproved(true);
        user.setGlobalAccessRequest(false);
        user.setInGlobalPool(true); // נכנס רשמית למאגר (ואין יציאה – כלל "תמיד גלובלי")
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        // התראה על אישור
        createSimpleNotification(
                saved,
                NotificationType.GLOBAL_ACCESS_APPROVED,
                "אושרת למאגר הכללי",
                "הפרופיל שלך אושר למאגר הכללי."
        );

        // התראה על כניסה רשמית למאגר
        createSimpleNotification(
                saved,
                NotificationType.ENTERED_GLOBAL_POOL,
                "נכנסת למאגר השידוכים הכללי",
                "הפרופיל שלך מופיע כעת במאגר הכללי לזיווגים."
        );

        return saved;
    }

    // ======================================================
    // 🔹 שליפת משתמשים במאגר הגלובלי (שירות מערכת/אדמין)
    // ======================================================

    @Transactional(readOnly = true)
    public List<User> getGlobalPoolUsers() {
        // ⬅️ סינון כפול – גם אם בטעות יסמן אדמין כ-inGlobalPool, לא יחזור החוצה
        return userRepository.findByInGlobalPoolTrue()
                .stream()
                .filter(u -> !isSystemUser(u))
                .toList();
    }

    // ======================================================
    // 🔹 תזכורת למשתמש שעדיין לא השלים פרופיל
    // ======================================================

    @Transactional
    public void sendProfileCompletionReminder(Long userId) {
        User user = getUserOrThrow(userId);

        if (user.isFullProfileCompleted()) return;

        Notification n = new Notification();
        n.setRecipient(user);
        n.setType(NotificationType.PROFILE_INCOMPLETE_REMINDER);
        n.setTitle("הפרופיל שלך עדיין חסר");
        n.setMessage("מומלץ להשלים את הפרופיל כדי לקבל התאמות טובות יותר.");
        n.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(n);
    }

    // ======================================================
    // 🔹 ספירת צפיות בפרופיל (לתמיכה ב־PROFILE_VIEWS_SUMMARY)
    // ======================================================

    @Transactional
    public void incrementProfileViews(Long viewedUserId) {
        User target = getUserOrThrow(viewedUserId);
        Integer current = target.getProfileViewsCount();
        if (current == null) current = 0;
        target.setProfileViewsCount(current + 1);
        target.setUpdatedAt(LocalDateTime.now());
        userRepository.save(target);
    }
    // ======================================================
    // 🔹 מצב חתונה / רקע (Wedding Mode vs Global)
    // ======================================================

    @Transactional
    public User enterWeddingMode(Long userId, Long weddingId) {

        User user = getUserOrThrow(userId);

        // ⬅️ חדש: Admin / Event Manager לא נכנסים למצב חתונה
        assertNotSystemUserForWeddingMode(user);

        Wedding wedding = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("חתונה לא נמצאה"));

        user.setActiveWeddingId(wedding.getId());
        user.setBackgroundWeddingId(wedding.getId());
        user.setBackgroundMode("WEDDING");

        user.setWeddingEntryAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        // התראה אופציונלית – כניסה לחתונה
        createSimpleNotification(
                saved,
                NotificationType.WEDDING_ENTRY,
                "נכנסת לחתונה",
                "אתה כרגע במצב חתונה: " + wedding.getName()
        );

        return saved;
    }

    @Transactional
    public User exitWeddingMode(Long userId) {

        User user = getUserOrThrow(userId);

        // ⬅️ חדש: גם כאן הגנה – ליתר ביטחון
        assertNotSystemUserForWeddingMode(user);

        user.setActiveWeddingId(null);
        user.setBackgroundWeddingId(null);
        user.setBackgroundMode("DEFAULT");

        user.setWeddingExitAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean isInWeddingMode(Long userId) {
        User user = getUserOrThrow(userId);
        // לא חייבים, אבל אם זה SystemUser – תמיד false
        if (isSystemUser(user)) {
            return false;
        }
        return user.getActiveWeddingId() != null;
    }

    // ======================================================
    //      UserService – Likes / Freeze / Dislike / Match Logic
    // ======================================================

    // ======================================================
    // 🔹 פעולה מרכזית: ביצוע פעולה על משתמש אחר
    // ======================================================

    @Transactional
    public String performUserInteraction(Long actorId,
                                         Long targetId,
                                         UserActionType actionType,
                                         Long weddingId) {

        User actor = getUserOrThrow(actorId);        // שולח הפעולה
        User target = getUserOrThrow(targetId);      // מי שמקבל את הפעולה

        if (actorId.equals(targetId))
            throw new IllegalArgumentException("משתמש אינו יכול לבצע פעולה על עצמו.");

        // לפי האפיון – בלי תמונה/פרופיל מלא: מותר רק התחברות ואזור אישי, לא פעולות חברתיות
        // וגם – משתמש מערכת (Admin / Event Manager) חסום
        assertUserEligibleForSocialActions(actor);

        // מנקים פעולות קודמות של actor על target כדי למנוע התנגשות
        deactivatePreviousActions(actor, target);

        return switch (actionType) {

            case LIKE -> handleLikeInteraction(actor, target, weddingId);

            case DISLIKE -> {
                createBasicAction(actor, target, UserActionType.DISLIKE,
                        UserActionCategory.SOCIAL, weddingId, "User disliked");
                // התראה אופציונלית בעתיד (USER_DISLIKED)
                yield "DISLIKE_OK";
            }

            case FREEZE -> {
                createBasicAction(actor, target, UserActionType.FREEZE,
                        UserActionCategory.SOCIAL, weddingId, "User froze");
                // התראה אופציונלית (USER_FROZEN)
                yield "FREEZE_OK";
            }

            case UNFREEZE -> {
                createBasicAction(actor, target, UserActionType.UNFREEZE,
                        UserActionCategory.SOCIAL, weddingId, "User unfreezed");
                // התראה אופציונלית (USER_UNFROZEN)
                yield "UNFREEZE_OK";
            }

            default -> throw new IllegalArgumentException("Action type not supported");
        };
    }

    // ======================================================
    // 🔹 יצירת Notification פשוט
    // ======================================================

    private void createSimpleNotification(User user,
                                          NotificationType type,
                                          String title,
                                          String message) {

        Notification n = new Notification();
        n.setRecipient(user);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(n);
    }

    // ======================================================
    // 🔹 לייק — הטיפול המלא (כולל יצירת Match)
    // ======================================================

    private String handleLikeInteraction(User actor,
                                         User target,
                                         Long weddingId) {

        // פעולה: LIKE (סושיאל – לפי האפיון)
        createBasicAction(actor, target,
                UserActionType.LIKE, UserActionCategory.SOCIAL,
                weddingId, "User liked");

        // האם target כבר עשה לייק על actor?
        UserAction reciprocal =
                userActionRepository.findTopByActorAndTargetAndActionTypeAndActiveTrueOrderByCreatedAtDesc(
                        target, actor, UserActionType.LIKE);

        if (reciprocal == null) {
            // נשלח התראה ל-target שהוא קיבל לייק (אופציונלי)
            createSimpleNotification(
                    target,
                    NotificationType.LIKE_RECEIVED,
                    "קיבלת לייק חדש",
                    actor.getFullName() + " התעניין בך."
            );
            return "LIKE_WAITING";        // עדיין אין הדדיות
        }

        // יש הדדיות → יצירת / עדכון Match
        Match match = matchRepository
                .findByUser1IdAndUser2IdOrUser1IdAndUser2Id(
                        actor.getId(), target.getId(),
                        target.getId(), actor.getId()
                )
                .orElseGet(() -> createNewMatch(actor, target, weddingId));

        updateMatchApprovalState(match, actor);

        // לוג פעולה: LIKE_BACK (כבר ברמת MATCH)
        createBasicAction(actor, target,
                UserActionType.LIKE_BACK, UserActionCategory.MATCH,
                weddingId, "Mutual like formed");

        // שליחת התראות
        String msg = actor.getFullName() + " ו-" + target.getFullName() + " – התאמה הדדית!";
        createSimpleNotification(actor, NotificationType.MATCH_MUTUAL, "יש התאמה!", msg);
        createSimpleNotification(target, NotificationType.MATCH_MUTUAL, "יש התאמה!", msg);

        return "MATCH_MUTUAL";
    }

    // ======================================================
    // 🔹 יצירת Match חדש
    // ======================================================

    private Match createNewMatch(User u1, User u2, Long weddingId) {

        Match match = new Match(
                u1,
                u2,
                weddingId,   // meetingWeddingId – באיזו חתונה נוצר המץ'
                weddingId,   // originWeddingId – איפה נפגשו לראשונה
                50.0,        // ניקוד בסיסי (בהמשך אפשר לחשב דינמית)
                "wedding"    // מקור המץ'
        );

        // היוזר הנוכחי הוא זה ששם לייק (user1 מאושר)
        match.setUser1Approved(true);
        match.setUser2Approved(false);

        return matchRepository.save(match);
    }

    // ======================================================
    // 🔹 עדכון אישורי Match לאחר לייק הדדי
    // ======================================================

    private void updateMatchApprovalState(Match match, User actor) {

        if (match.getUser1().getId().equals(actor.getId()))
            match.setUser1Approved(true);

        if (match.getUser2().getId().equals(actor.getId()))
            match.setUser2Approved(true);

        if (match.isUser1Approved() && match.isUser2Approved())
            match.setMutualApproved(true);

        match.setUpdatedAt(LocalDateTime.now());
        matchRepository.save(match);
    }

    // ======================================================
    // 🔹 מיפוי שם רשימה לפי סוג פעולה (listName)
    //     (תומך ברשימות 1–5 מהאפיון – LIKE / DISLIKE / FREEZE)
    // ======================================================

    private String deriveListName(UserActionType type) {
        return switch (type) {
            case LIKE, LIKE_BACK -> "LIKE";
            case DISLIKE -> "DISLIKE";
            case FREEZE, UNFREEZE -> "FREEZE";
            default -> null;
        };
    }

    // ======================================================
    // 🔹 יצירת פעולה בסיסית (UserAction)
    //      כולל listName לפי הרשימות באפיון
    // ======================================================

    private UserAction createBasicAction(User actor,
                                         User target,
                                         UserActionType type,
                                         UserActionCategory category,
                                         Long weddingId,
                                         String metadata) {

        UserAction action = new UserAction(
                actor,
                target,
                type,
                category,
                weddingId,       // wedding context
                weddingId,       // originWeddingId — בשלב זה אותה חתונה
                null,            // matchId (ניתן לעדכן בהמשך אם צריך)
                null,            // actionGroupId
                "user",          // מקור — משתמש רגיל
                false,           // autoGenerated
                metadata,
                deriveListName(type) // ⭐ שם רשימה
        );

        return userActionRepository.save(action);
    }

    // ======================================================
    // 🔹 נטרול פעולות קודמות (למניעת התנגשות)
    // ======================================================

    private void deactivatePreviousActions(User actor, User target) {
        List<UserAction> previous = userActionRepository.findByActorAndTarget(actor, target);

        boolean modified = false;

        for (UserAction ua : previous) {
            if (ua.isActive()) {
                ua.setActive(false);
                ua.setUpdatedAt(LocalDateTime.now());
                modified = true;
            }
        }

        if (modified)
            userActionRepository.saveAll(previous);
    }

    // ======================================================
    // 🔹 רשימות 1–5 לפי האפיון
    //     1. אני עשיתי להם לייק
    //     2. הם שמו לי לייק ומחכים לתגובה
    //     3. התאמות הדדיות – getMutualMatches
    //     4. לא מעוניין (DISLIKE)
    //     5. מקפיאים (FREEZE)
    // ======================================================

    // 1️⃣ "אנשים שאני עשיתי להם לייק"
    @Transactional(readOnly = true)
    public List<UserAction> getUsersILiked(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        return userActionRepository.findByActorAndActionTypeAndActiveTrue(
                me, UserActionType.LIKE
        );
    }

    // 2️⃣ "אנשים ששמו לי לייק ומחכים לתגובה ממני"
    @Transactional(readOnly = true)
    public List<UserAction> getUsersWhoLikedMeAndWaitingForMyResponse(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        // כל הלייקים הפעילים עליי
        List<UserAction> likesOnMe =
                userActionRepository.findByTargetAndActionTypeAndActiveTrue(
                        me, UserActionType.LIKE
                );

        // מסננים רק כאלה שאין ממני פעולה ברורה (LIKE / DISLIKE / FREEZE) כלפיהם
        return likesOnMe.stream()
                .filter(action -> {
                    User actor = action.getActor();

                    // הפעולה האחרונה שביצעתי כלפיו
                    UserAction lastFromMeToHim =
                            userActionRepository.findTopByActorAndTargetOrderByCreatedAtDesc(
                                    me, actor
                            );

                    if (lastFromMeToHim == null) {
                        // לא עשיתי עליו כלום → מחכה לתגובה
                        return true;
                    }

                    UserActionType t = lastFromMeToHim.getActionType();

                    // אם כבר סימנתי LIKE / DISLIKE / FREEZE – הוא לא "ממתין"
                    return !(t == UserActionType.LIKE
                            || t == UserActionType.DISLIKE
                            || t == UserActionType.FREEZE);
                })
                .toList();
    }

    // לשמירה אחורה על השם הקיים – ממפה לרשימה 2
    @Transactional(readOnly = true)
    public List<UserAction> getPendingLikes(Long userId) {
        return getUsersWhoLikedMeAndWaitingForMyResponse(userId);
    }

    // 5️⃣ "מקפיא" – FREEZE
    @Transactional(readOnly = true)
    public List<UserAction> getFrozenUsers(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        return userActionRepository.findByActorAndActionTypeAndActiveTrue(
                me, UserActionType.FREEZE
        );
    }

    // 4️⃣ "לא מעוניין" – DISLIKE
    @Transactional(readOnly = true)
    public List<UserAction> getDislikedUsers(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        return userActionRepository.findByActorAndActionTypeAndActiveTrue(
                me, UserActionType.DISLIKE
        );
    }

    // ======================================================
    // 🔹 התאמות הדדיות — Matches
    // ======================================================

    @Transactional(readOnly = true)
    public List<Match> getMutualMatches(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        return matchRepository.findByMutualApprovedTrue()
                .stream()
                .filter(m -> m.involvesUser(userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Match> getActiveMatches(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        return matchRepository.findByActiveTrue()
                .stream()
                .filter(m -> m.involvesUser(userId))
                .toList();
    }

    // ======================================================
    // 🔹 התאמות שממתינות לאישור שלי
    // ======================================================

    @Transactional(readOnly = true)
    public List<Match> getMatchesWaitingForMyApproval(Long userId) {
        User me = getUserOrThrow(userId);
        assertUserEligibleForSocialActions(me); // כולל חסימת SystemUser

        return matchRepository
                .findByUser1IdAndUser2ApprovedTrueOrUser2IdAndUser1ApprovedTrue(userId, userId);
    }

    // ======================================================
    // 🔹 יצירת משתמש "מנהל אירוע" ע"י אדמין
    // ======================================================

    public User createEventManager(String fullName,
                                   String phone,
                                   String email,
                                   String gender) {

        // 1. יצירת משתמש בסיסי
        User user = new User();
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setGender(gender);

        // 2. הגדרות מערכתיות
        user.setEventManager(true);
        user.setAdmin(false);

        // 3. ביטול כל שדות השידוכים / מאגרים
        user.setVerified(false);
        user.setBasicProfileCompleted(false);
        user.setFullProfileCompleted(false);
        user.setHasPrimaryPhoto(false);

        user.setInGlobalPool(false);
        user.setGlobalAccessApproved(false);
        user.setGlobalAccessRequest(false);

        // לא נראה בתצוגת כרטיסים של החתונה
        user.setCanViewWedding(false);

        // 4. חתונות / היסטוריה
        user.setActiveBackgroundWeddingId(null);
        user.setLastWeddingId(null);
        user.setFirstWeddingId(null);
        user.setWeddingsHistory(new ArrayList<>());

        // 5. התראות – נשאיר דיפולטיות (שיהיה אפשר לשלוח אליו אם נרצה)
        user.setAllowEmailNotifications(true);
        user.setAllowInAppNotifications(true);

        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

}