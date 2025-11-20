package com.example.myproject.service;

import com.example.myproject.model.*;
import com.example.myproject.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    private final Random random = new Random();

    public UserService(UserRepository userRepository,
                       NotificationRepository notificationRepository,
                       UserActionRepository userActionRepository,
                       MatchRepository matchRepository,
                       WeddingRepository weddingRepository) {

        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.userActionRepository = userActionRepository;
        this.matchRepository = matchRepository;
        this.weddingRepository = weddingRepository;
    }

    // ======================================================
    // 🔹 Utility – קוד אימות רנדומלי (6 ספרות)
    // ======================================================

    private String generateVerificationCode() {
        return String.valueOf(100000 + random.nextInt(900000));
    }

    // ======================================================
    // 🔹 יצירת חשבון משתמש חדש (Phone + Email חובה)
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

        // שדות חובה
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setGender(gender);

        // מצב אימות
        user.setVerified(false);
        user.setVerificationCode(generateVerificationCode());

        // סטטוסי פרופיל
        user.setBasicProfileCompleted(false);
        user.setFullProfileCompleted(false);
        user.setHasPrimaryPhoto(false);

        // מאגר גלובלי
        user.setInGlobalPool(false);
        user.setGlobalAccessApproved(false);
        user.setGlobalAccessRequest(false);

        // מחיקה
        user.setDeletionRequested(false);

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
//      UserService – Part 2/3
//      Profile + Preferences + Global Pool
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

    // כלי עזר קצר לשדה ריק
    private boolean notEmpty(String s) {
        return s != null && !s.isBlank();
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
    // ======================================================

    @Transactional
    public User requestGlobalAccess(Long userId) {
        User user = getUserOrThrow(userId);

        if (!user.isFullProfileCompleted() || !user.isHasPrimaryPhoto()) {
            throw new IllegalStateException("כדי לבקש גישה גלובלית יש להשלים פרופיל מלא + תמונה ראשית.");
        }

        user.setGlobalAccessRequest(true);
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 אישור גישה גלובלית ע"י מנהל (approve)
    // ======================================================

    @Transactional
    public User approveGlobalAccess(Long userId) {
        User user = getUserOrThrow(userId);

        user.setGlobalAccessApproved(true);
        user.setGlobalAccessRequest(false);
        user.setInGlobalPool(true); // נכנס רשמית למאגר
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ======================================================
    // 🔹 שליפת משתמשים עם מאגר גלובלי
    // ======================================================

    @Transactional(readOnly = true)
    public List<User> getGlobalPoolUsers() {
        return userRepository.findByInGlobalPoolTrue();
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
//      UserService – Part 3/3
//      Likes / Freeze / Dislike / Match Logic
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

        // מנקים פעולות קודמות של actor על target כדי למנוע התנגשות
        deactivatePreviousActions(actor, target);

        return switch (actionType) {

            case LIKE -> handleLikeInteraction(actor, target, weddingId);

            case DISLIKE -> {
                createBasicAction(actor, target, UserActionType.DISLIKE,
                        UserActionCategory.SOCIAL, weddingId, "User disliked");
                yield "DISLIKE_OK";
            }

            case FREEZE -> {
                createBasicAction(actor, target, UserActionType.FREEZE,
                        UserActionCategory.SOCIAL, weddingId, "User froze");
                yield "FREEZE_OK";
            }

            case UNFREEZE -> {
                createBasicAction(actor, target, UserActionType.UNFREEZE,
                        UserActionCategory.SOCIAL, weddingId, "User unfreezed");
                yield "UNFREEZE_OK";
            }

            default -> throw new IllegalArgumentException("Action type not supported");
        };
    }


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
    // 🔹 לייק — הטיפול המלא
    // ======================================================

    private String handleLikeInteraction(User actor,
                                         User target,
                                         Long weddingId) {

        // פעולה: LIKE
        createBasicAction(actor, target,
                UserActionType.LIKE, UserActionCategory.MATCH,
                weddingId, "User liked");

        // האם target כבר עשה לייק על actor?
        UserAction reciprocal =
                userActionRepository.findTopByActorAndTargetAndActionTypeAndActiveTrueOrderByCreatedAtDesc(
                        target, actor, UserActionType.LIKE);

        if (reciprocal == null) {
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

        // לוג פעולה: LIKE_BACK
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
                weddingId,   // originWeddingId – איפה הכירו לראשונה (בשלב זה זו אותה חתונה)
                50.0,        // ניקוד בסיסי
                "wedding"    // מקור המץ'
        );

        match.setUser1Approved(true);       // היוזר ששם לייק ראשון
        match.setUser2Approved(false);      // השני יאשר כשיעשה לייק/אישור

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
    // 🔹 יצירת פעולה בסיסית (UserAction)
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
                weddingId,       // originWeddingId — נזהה איפה נפגשו
                null,            // matchId (יתווסף רק אחרי התאמה)
                null,            // actionGroup
                "user",          // מקור — משתמש רגיל
                false,           // autoGenerated
                metadata
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
    // 🔹 רשימות לייקים / קפואים / לא מעוניין
    // ======================================================

    @Transactional(readOnly = true)
    public List<UserAction> getPendingLikes(Long userId) {
        User me = getUserOrThrow(userId);
        return userActionRepository.findByTargetAndActionTypeAndActiveTrue(
                me, UserActionType.LIKE
        );
    }

    @Transactional(readOnly = true)
    public List<UserAction> getFrozenUsers(Long userId) {
        User me = getUserOrThrow(userId);
        return userActionRepository.findByActorAndActionTypeAndActiveTrue(
                me, UserActionType.FREEZE
        );
    }

    @Transactional(readOnly = true)
    public List<UserAction> getDislikedUsers(Long userId) {
        User me = getUserOrThrow(userId);
        return userActionRepository.findByActorAndActionTypeAndActiveTrue(
                me, UserActionType.DISLIKE
        );
    }


    // ======================================================
    // 🔹 התאמות הדדיות — Matches
    // ======================================================

    @Transactional(readOnly = true)
    public List<Match> getMutualMatches(Long userId) {
        return matchRepository.findByMutualApprovedTrue()
                .stream()
                .filter(m -> m.involvesUser(userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Match> getActiveMatches(Long userId) {
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

        return matchRepository
                .findByUser1IdAndUser2ApprovedTrueOrUser2IdAndUser1ApprovedTrue(userId, userId);
    }

    @Transactional
    public User enterWeddingMode(Long userId, Long weddingId) {

        User user = getUserOrThrow(userId);

        Wedding wedding = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("חתונה לא נמצאה"));

        user.setActiveWeddingId(wedding.getId());
        user.setBackgroundWeddingId(wedding.getId());
        user.setBackgroundMode("WEDDING");

        user.setWeddingEntryAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public User exitWeddingMode(Long userId) {

        User user = getUserOrThrow(userId);

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
        return user.getActiveWeddingId() != null;
    }
}
