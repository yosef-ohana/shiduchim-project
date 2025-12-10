package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.model.enums.WeddingMode;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    public UserStateEvaluatorService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        boolean deletionRequested = user.isDeletionRequested();
        boolean basicCompleted = user.isBasicProfileCompleted();
        boolean fullCompleted = user.isFullProfileCompleted();
        boolean hasPrimaryPhoto = user.isHasPrimaryPhoto();
        boolean profileLocked = user.isProfileLockedAfterWedding();
        ProfileState profileState = user.getProfileState();
        GlobalAccessState globalState = user.getGlobalAccessState();
        WeddingMode weddingMode = user.getWeddingMode();
        BackgroundMode backgroundMode = user.getBackgroundMode();
        boolean verified = user.isVerified();
        boolean inGlobalPool = user.isInGlobalPool();

        // --- האם מותר לעדכן פרופיל ---
        boolean canUpdateProfile = !deletionRequested && !profileLocked;
        if (!canUpdateProfile) {
            if (deletionRequested) {
                reasons.add("בקשת מחיקת חשבון פעילה – לא ניתן לעדכן פרופיל.");
            }
            if (profileLocked) {
                reasons.add("הפרופיל נעול לאחר חתונה – חוק נעילת פרופיל אחרי אירוע.");
            }
        }

        // --- האם מותר לשנות תמונות ---
        boolean canChangePhotos = !deletionRequested && !profileLocked;
        if (!canChangePhotos) {
            reasons.add("שינוי תמונות חסום עקב נעילת פרופיל או מחיקת חשבון.");
        }

        // --- האם מותר לבקש/להיכנס למאגר גלובלי ---
        boolean canEnterGlobalPool = true;
        if (!verified) {
            canEnterGlobalPool = false;
            reasons.add("המשתמש לא מאומת – אי אפשר להיכנס למאגר גלובלי.");
        }
        if (!basicCompleted) {
            canEnterGlobalPool = false;
            reasons.add("פרופיל בסיסי לא מושלם – דרישת חובה למאגר גלובלי.");
        }
        if (!hasPrimaryPhoto) {
            canEnterGlobalPool = false;
            reasons.add("אין תמונה ראשית – חוק 'תמונה ראשית חובה' למאגר גלובלי.");
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
        boolean canEnterWedding = !deletionRequested;
        if (!user.isCanViewWedding()) {
            canEnterWedding = false;
            reasons.add("חסימת גישה לחתונות – canViewWedding=false.");
        }

        // --- האם מותר לעבור למצב Global Mode ---
        boolean canSwitchToGlobalMode = inGlobalPool && !deletionRequested;
        if (inGlobalPool && !hasPrimaryPhoto) {
            canSwitchToGlobalMode = false;
            reasons.add("מאגר גלובלי דורש תמונה ראשית – אי אפשר לעבור ל-GLOBAL mode בלי תמונה.");
        }

        // --- האם מותר לראות פרופילים של מין אחר/אותו מין ---
        boolean canViewOppositeGenderProfiles = user.isAllowProfileViewByOppositeGender();
        boolean canViewSameGenderProfiles = user.isAllowProfileViewBySameGender();

        // --- האם מותר לשלוח לייק / הודעה ---
        boolean canLike = hasPrimaryPhoto && !deletionRequested;
        boolean canSendMessage = hasPrimaryPhoto && !deletionRequested;

        if (!hasPrimaryPhoto) {
            reasons.add("לייקים והודעות דורשים תמונה ראשית לפי כללי המערכת.");
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