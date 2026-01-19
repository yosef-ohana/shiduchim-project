package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.model.enums.WeddingMode;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * UserStateEvaluatorService
 *
 * שירות המרכז את כל "מצב המשתמש" ברגע נתון:
 * - מצב פרופיל (Basic / Full / Locked)
 * - מצב תמונות
 * - מצב מאגר גלובלי
 * - מצב חתונה ורקעים
 * - האם מותר: להיכנס למאגר, לעדכן פרופיל, לשנות תמונות, לשלוח לייק/הודעה וכו'.
 *
 * מיועד לשימוש ע"י:
 * - Controllers (כדי להציג ל-Frontend תמונת מצב אחת מסודרת)
 * - System Layer (כדי לקבל החלטות לפי UserStateSummary)
 */
@Service
@Transactional(readOnly = true)
public class UserStateEvaluatorService {

    private final UserRepository userRepository;

    // ✅ NEW: source of truth for "currently locked"
    private final UserSettingsService userSettingsService;

    public UserStateEvaluatorService(UserRepository userRepository, UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.userSettingsService = userSettingsService;
    }

    // =====================================================
    // 🔵 נקודת כניסה ראשית – לפי userId
    // =====================================================

    public UserStateSummary evaluateUserState(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return evaluateUserState(user);
    }

    // =====================================================
    // 🔵 לוגיקת הערכה מלאה – לפי User
    // =====================================================

    public UserStateSummary evaluateUserState(User user) {
        List<String> reasons = new ArrayList<>();

        Long userId = user.getId();

        boolean deletionRequested = user.isDeletionRequested();
        boolean basicCompleted = user.isBasicProfileCompleted();
        boolean fullCompleted = user.isFullProfileCompleted();
        boolean hasPrimaryPhoto = user.isHasPrimaryPhoto();

        // ✅ SSOT (Compilation-safe): at least 1 photo (not necessarily primary)
        boolean hasAnyPhoto = hasAtLeastOnePhoto(user);

        // ✅ SSOT: lock source of truth comes from UserSettingsService
        boolean profileLocked = (userId != null) && userSettingsService.isCurrentlyLocked(userId);

        ProfileState profileState = user.getProfileState();
        GlobalAccessState globalState = user.getGlobalAccessState();
        WeddingMode weddingMode = user.getWeddingMode();
        BackgroundMode backgroundMode = user.getBackgroundMode();
        boolean verified = user.isVerified();
        boolean inGlobalPool = user.isInGlobalPool();

        // --- האם מותר לעדכן פרופיל ---
        // ✅ SSOT: גם אם נעול, עדיין מותר לערוך כדי להשתחרר מהנעילה.
        boolean canUpdateProfile = !deletionRequested;
        if (!canUpdateProfile) {
            reasons.add("בקשת מחיקת חשבון פעילה – לא ניתן לעדכן פרופיל.");
        }
        if (profileLocked) {
            reasons.add("החשבון נעול כרגע (UserSettings) – פעולות מאגר/אינטראקציות מוגבלות עד השלמת פרופיל מלא.");
        }

        // --- האם מותר לשנות תמונות ---
        // ✅ SSOT: גם אם נעול, עדיין מותר להעלות/לנהל תמונות כדי להשתחרר.
        boolean canChangePhotos = !deletionRequested;
        if (!canChangePhotos) {
            reasons.add("שינוי תמונות חסום עקב בקשת מחיקה פעילה.");
        }

        // --- האם מותר לבקש/להיכנס למאגר גלובלי ---
        // ✅ SSOT: Global דורש Full + primary + ≥1 photo + not locked + verified
        boolean canEnterGlobalPool = true;

        if (!verified) {
            canEnterGlobalPool = false;
            reasons.add("המשתמש לא מאומת – אי אפשר להיכנס למאגר גלובלי.");
        }
        if (!fullCompleted) {
            canEnterGlobalPool = false;
            reasons.add("פרופיל מלא לא מושלם – דרישת חובה למאגר גלובלי.");
        }
        if (!hasAnyPhoto) {
            canEnterGlobalPool = false;
            reasons.add("אין אף תמונה – חוק 'לפחות תמונה אחת' למאגר גלובלי.");
        }
        if (!hasPrimaryPhoto) {
            canEnterGlobalPool = false;
            reasons.add("אין תמונה ראשית – חוק 'תמונה ראשית חובה' למאגר גלובלי.");
        }
        if (profileLocked) {
            canEnterGlobalPool = false;
            reasons.add("החשבון נעול – לא ניתן להיכנס למאגר גלובלי עד שחרור נעילה.");
        }
        if (deletionRequested) {
            canEnterGlobalPool = false;
            reasons.add("לא ניתן להיכנס למאגר כשיש בקשת מחיקה פעילה.");
        }

        // --- האם מותר לצאת מהמאגר הגלובלי ---
        boolean canExitGlobalPool = inGlobalPool && !deletionRequested;
        if (!canExitGlobalPool && inGlobalPool) {
            reasons.add("לא ניתן לשנות מאגר בזמן תהליך מחיקה.");
        }

        // --- האם מותר להיכנס לחתונה ---
        boolean canEnterWedding = !deletionRequested && !profileLocked;
        if (!user.isCanViewWedding()) {
            canEnterWedding = false;
            reasons.add("חסימת גישה לחתונות – canViewWedding=false.");
        }
        if (profileLocked) {
            reasons.add("החשבון נעול – לא ניתן להיכנס למצב חתונה עד שחרור נעילה.");
        }

        // --- האם מותר לעבור למצב Global Mode ---
        // ✅ אם רוצים לעבור ל-GLOBAL mode - צריך לעמוד בתנאים של גלובלי
        boolean canSwitchToGlobalMode = inGlobalPool && !deletionRequested && canEnterGlobalPool;
        if (inGlobalPool && !canSwitchToGlobalMode) {
            reasons.add("אי אפשר לעבור ל-GLOBAL mode כי תנאי מאגר גלובלי לא מתקיימים כרגע.");
        }

        // --- האם מותר לראות פרופילים של מין אחר/אותו מין ---
        boolean canViewOppositeGenderProfiles = user.isAllowProfileViewByOppositeGender();
        boolean canViewSameGenderProfiles = user.isAllowProfileViewBySameGender();

        // --- האם מותר לשלוח לייק / הודעה ---
        // ✅ SSOT: אינטראקציות דורשות ≥1 photo + not locked
        boolean canLike = hasAnyPhoto && !deletionRequested && !profileLocked;
        boolean canSendMessage = hasAnyPhoto && !deletionRequested && !profileLocked;

        if (!hasAnyPhoto) {
            reasons.add("לייקים והודעות דורשים לפחות תמונה אחת לפי כללי המערכת.");
        }
        if (profileLocked) {
            reasons.add("לייקים והודעות חסומים כאשר החשבון נעול.");
        }
        if (deletionRequested) {
            reasons.add("משתמש בהליך מחיקה – אינטראקציות ננעלות.");
        }

        // בניית האובייקט המסכם
        return new UserStateSummary(
                user.getId(),
                basicCompleted,
                fullCompleted,
                profileState,
                hasPrimaryPhoto,
                verified,
                inGlobalPool,
                globalState,
                weddingMode,
                backgroundMode,
                profileLocked,
                deletionRequested,
                canUpdateProfile,
                canChangePhotos,
                canEnterGlobalPool,
                canExitGlobalPool,
                canEnterWedding,
                canSwitchToGlobalMode,
                canLike,
                canSendMessage,
                canViewOppositeGenderProfiles,
                canViewSameGenderProfiles,
                reasons
        );
    }

    // =====================================================
    // ✅ Helper: at least 1 photo (Compilation-safe)
    // =====================================================

    private boolean hasAtLeastOnePhoto(User user) {
        if (user == null) return false;

        // 1) אם קיימת תמונה ראשית - ברור שיש לפחות אחת
        if (user.isHasPrimaryPhoto()) return true;

        // 2) נסיון best-effort דרך Reflection (לא שובר קומפילציה אם אין שדה/מתודה)
        //    supports: getPhotosCount(), getUserPhotos(), getPhotos()
        try {
            Method m = user.getClass().getMethod("getPhotosCount");
            Object v = m.invoke(user);
            if (v instanceof Integer) return ((Integer) v) > 0;
            if (v instanceof Long) return ((Long) v) > 0;
        } catch (Exception ignore) {}

        try {
            Method m = user.getClass().getMethod("getUserPhotos");
            Object v = m.invoke(user);
            if (v instanceof Collection) return !((Collection<?>) v).isEmpty();
        } catch (Exception ignore) {}

        try {
            Method m = user.getClass().getMethod("getPhotos");
            Object v = m.invoke(user);
            if (v instanceof Collection) return !((Collection<?>) v).isEmpty();
        } catch (Exception ignore) {}

        return false;
    }

    // =====================================================
// ✅ SSOT Gate: Global eligibility
// =====================================================
    public void assertEligibleForGlobal(Long userId) {
        UserStateSummary state = evaluateUserState(userId);
        if (!state.isCanEnterGlobalPool()) {
            throw new IllegalStateException(String.join(" | ", state.getReasonsBlocked()));
        }
    }

    // =====================================================
    // 🔵 DTO מסכם – מצב משתמש מלא ל-Frontend/Controllers
    // =====================================================

    public static class UserStateSummary {

        private final Long userId;

        private final boolean basicProfileCompleted;
        private final boolean fullProfileCompleted;
        private final ProfileState profileState;

        private final boolean hasPrimaryPhoto;
        private final boolean verified;

        private final boolean inGlobalPool;
        private final GlobalAccessState globalAccessState;

        private final WeddingMode weddingMode;
        private final BackgroundMode backgroundMode;

        private final boolean profileLocked;
        private final boolean deletionRequested;

        private final boolean canUpdateProfile;
        private final boolean canChangePhotos;
        private final boolean canEnterGlobalPool;
        private final boolean canExitGlobalPool;
        private final boolean canEnterWedding;
        private final boolean canSwitchToGlobalMode;
        private final boolean canLike;
        private final boolean canSendMessage;
        private final boolean canViewOppositeGenderProfiles;
        private final boolean canViewSameGenderProfiles;

        private final List<String> reasonsBlocked;

        public UserStateSummary(
                Long userId,
                boolean basicProfileCompleted,
                boolean fullProfileCompleted,
                ProfileState profileState,
                boolean hasPrimaryPhoto,
                boolean verified,
                boolean inGlobalPool,
                GlobalAccessState globalAccessState,
                WeddingMode weddingMode,
                BackgroundMode backgroundMode,
                boolean profileLocked,
                boolean deletionRequested,
                boolean canUpdateProfile,
                boolean canChangePhotos,
                boolean canEnterGlobalPool,
                boolean canExitGlobalPool,
                boolean canEnterWedding,
                boolean canSwitchToGlobalMode,
                boolean canLike,
                boolean canSendMessage,
                boolean canViewOppositeGenderProfiles,
                boolean canViewSameGenderProfiles,
                List<String> reasonsBlocked
        ) {
            this.userId = userId;
            this.basicProfileCompleted = basicProfileCompleted;
            this.fullProfileCompleted = fullProfileCompleted;
            this.profileState = profileState;
            this.hasPrimaryPhoto = hasPrimaryPhoto;
            this.verified = verified;
            this.inGlobalPool = inGlobalPool;
            this.globalAccessState = globalAccessState;
            this.weddingMode = weddingMode;
            this.backgroundMode = backgroundMode;
            this.profileLocked = profileLocked;
            this.deletionRequested = deletionRequested;
            this.canUpdateProfile = canUpdateProfile;
            this.canChangePhotos = canChangePhotos;
            this.canEnterGlobalPool = canEnterGlobalPool;
            this.canExitGlobalPool = canExitGlobalPool;
            this.canEnterWedding = canEnterWedding;
            this.canSwitchToGlobalMode = canSwitchToGlobalMode;
            this.canLike = canLike;
            this.canSendMessage = canSendMessage;
            this.canViewOppositeGenderProfiles = canViewOppositeGenderProfiles;
            this.canViewSameGenderProfiles = canViewSameGenderProfiles;
            this.reasonsBlocked = reasonsBlocked != null ? reasonsBlocked : List.of();
        }

        public Long getUserId() { return userId; }

        public boolean isBasicProfileCompleted() { return basicProfileCompleted; }

        public boolean isFullProfileCompleted() { return fullProfileCompleted; }

        public ProfileState getProfileState() { return profileState; }

        public boolean isHasPrimaryPhoto() { return hasPrimaryPhoto; }

        public boolean isVerified() { return verified; }

        public boolean isInGlobalPool() { return inGlobalPool; }

        public GlobalAccessState getGlobalAccessState() { return globalAccessState; }

        public WeddingMode getWeddingMode() { return weddingMode; }

        public BackgroundMode getBackgroundMode() { return backgroundMode; }

        public boolean isProfileLocked() { return profileLocked; }

        public boolean isDeletionRequested() { return deletionRequested; }

        public boolean isCanUpdateProfile() { return canUpdateProfile; }

        public boolean isCanChangePhotos() { return canChangePhotos; }

        public boolean isCanEnterGlobalPool() { return canEnterGlobalPool; }

        public boolean isCanExitGlobalPool() { return canExitGlobalPool; }

        public boolean isCanEnterWedding() { return canEnterWedding; }

        public boolean isCanSwitchToGlobalMode() { return canSwitchToGlobalMode; }

        public boolean isCanLike() { return canLike; }

        public boolean isCanSendMessage() { return canSendMessage; }

        public boolean isCanViewOppositeGenderProfiles() { return canViewOppositeGenderProfiles; }

        public boolean isCanViewSameGenderProfiles() { return canViewSameGenderProfiles; }

        public List<String> getReasonsBlocked() { return reasonsBlocked; }
    }
}
