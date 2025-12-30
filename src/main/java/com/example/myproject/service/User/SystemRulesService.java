package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SystemRulesService
 *
 * אחראי על יישום "חוקי המערכת" ברמת User:
 * - חוק "תמונה ראשית חובה"
 * - חוק "נעילת פרופיל אחרי חתונה"
 * - חוקי מאגר גלובלי (Requested / Approved / Rejected / None)
 * - עדכון ProfileState בהתאם למצב השדות
 *
 * שירות זה אינו מחליף את UserProfileService / UserGlobalPoolService,
 * אלא משמש כמנוע-עזר שמוודא שהחוקים נשמרים בכל נקודה קריטית.
 */
@Service
@Transactional
public class SystemRulesService {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

    public SystemRulesService(UserRepository userRepository,
                              UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
    }

    // =====================================================
    // 🔵 יישום כל החוקים הרלוונטיים על משתמש יחיד
    // =====================================================

    public User enforceAllUserRules(User user) {
        enforcePrimaryPhotoRule(user);
        enforceGlobalAccessStateRule(user);
        enforceProfileLockedAfterWeddingRule(user);
        enforceProfileStateConsistency(user);

        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 חוק "תמונה ראשית חובה"
    // =====================================================

    /**
     * אם למשתמש אין תמונה ראשית אבל photosCount > 0 – ננסה לסמן אחת כראשית.
     * (בפועל, UserPhotoService אחראי לזה, אבל כאן אנחנו מוודאים את הדגל hasPrimaryPhoto)
     */
    public void enforcePrimaryPhotoRule(User user) {
        boolean hasPrimary = user.isHasPrimaryPhoto();
        Integer count = user.getPhotosCount() != null ? user.getPhotosCount() : 0;

        if (count == 0 && hasPrimary) {
            // לא ייתכן – אין תמונות, אבל hasPrimaryPhoto=true
            user.setHasPrimaryPhoto(false);
        }

        if (count > 0 && !hasPrimary) {
            // יש תמונות אבל אין primary – המערכת יכולה לבחור ראשית כברירת מחדל
            // כאן לא נקבע איזו תמונה – זה קורה ב-UserPhotoService.
            // רק מסמנים שהמצב עדיין לא "מסודר".
            // אפשר להשאיר hasPrimaryPhoto=false ולהכריח את ה-Frontend לבחור.
        }
    }

    // =====================================================
    // 🔵 חוקי מאגר גלובלי (GlobalAccessState)
    // =====================================================

    /**
     * מוודא ש-GlobalAccessState מסונכרן עם הדגלים:
     * - inGlobalPool
     * - globalAccessRequest
     * - globalAccessApproved
     */
    public void enforceGlobalAccessStateRule(User user) {
        boolean inPool = user.isInGlobalPool();
        boolean requested = user.isGlobalAccessRequest();
        boolean approved = user.isGlobalAccessApproved();

        GlobalAccessState state;

        // ✅ מצב תקין: אושר + נמצא במאגר
        if (approved && inPool) {
            state = GlobalAccessState.APPROVED;

            // ✅ אם יש חותמת דחייה — זה REJECTED (גם אם keepRequestFlag=true)
        } else if (user.getGlobalRejectedAt() != null && !approved && !inPool) {
            state = GlobalAccessState.REJECTED;

            // ✅ בקשה ממתינה
        } else if (requested && !approved && !inPool) {
            state = GlobalAccessState.REQUESTED;

            // ✅ כל היתר
        } else {
            state = GlobalAccessState.NONE;
        }

        user.setGlobalAccessState(state);
    }

    // =====================================================
    // 🔵 נעילת פרופיל אחרי חתונה
    // =====================================================

    /**
     * כלל כללי: אם profileLockedAfterWedding=true – ProfileState חייב להיות LOCKED_AFTER_WEDDING.
     * אם הדגל false – מחזירים לפי Basic/Full.
     */
    public void enforceProfileLockedAfterWeddingRule(User user) {
        if (user.isProfileLockedAfterWedding()) {
            user.setProfileState(ProfileState.LOCKED_AFTER_WEDDING);
        }
    }

    // =====================================================
    // 🔵 סנכרון ProfileState לפי Basic/Full + נעילה
    // =====================================================

    public void enforceProfileStateConsistency(User user) {
        if (user.isProfileLockedAfterWedding()) {
            user.setProfileState(ProfileState.LOCKED_AFTER_WEDDING);
            return;
        }

        // משתמשים בפונקציה הרשמית של UserProfileService
        userProfileService.recomputeProfileState(user);
    }

    // =====================================================
    // 🔵 Hooks – קריאות ממוקדות לפי אירועים מערכתיים
    // =====================================================

    /**
     * קריאה מומלצת כשמשתמש נכנס לחתונה.
     * כאן אפשר להוסיף בעתיד חוקים כמו "מי שלא השלים פרופיל בסיסי – לא נכנס".
     */
    public User applyRulesOnWeddingEnter(User user) {
        // כרגע רק מוודאים תקינות פרופיל/מאגר.
        enforceAllUserRules(user);
        user.setWeddingEntryAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * קריאה מומלצת כשמשתמש יוצא מחתונה.
     * חוק אפשרי: אם אחרי החתונה אין פרופיל מלא – נועל את הפרופיל הבסיסי עד השלמה.
     */
    public User applyRulesOnWeddingExit(User user) {
        user.setWeddingExitAt(LocalDateTime.now());

        if (!user.isFullProfileCompleted()) {
            user.setProfileLockedAfterWedding(true);
            user.setProfileLockedAt(LocalDateTime.now());
            user.setProfileState(ProfileState.LOCKED_AFTER_WEDDING);
        }

        return userRepository.save(user);
    }

    /**
     * קריאה מומלצת לאחר אישור כניסה למאגר הגלובלי.
     */
    public User applyRulesOnGlobalApproved(User user) {
        user.setGlobalApprovedAt(LocalDateTime.now());
        user.setInGlobalPool(true);
        user.setGlobalAccessApproved(true);
        user.setGlobalAccessRequest(false);
        enforceGlobalAccessStateRule(user);
        return userRepository.save(user);
    }

    /**
     * קריאה לאחר דחיית מאגר גלובלי.
     */
    public User applyRulesOnGlobalRejected(User user, boolean keepRequestFlag) {
        user.setGlobalAccessApproved(false);
        user.setInGlobalPool(false);
        user.setGlobalRejectedAt(LocalDateTime.now());

        if (!keepRequestFlag) {
            user.setGlobalAccessRequest(false);
        }

        enforceGlobalAccessStateRule(user);
        return userRepository.save(user);
    }
}