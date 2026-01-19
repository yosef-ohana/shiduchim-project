package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserGlobalPoolService {

    private final UserRepository userRepository;

    // ✅ Added per patch
    private final SystemRulesService systemRulesService;
    private final UserStateEvaluatorService userStateEvaluatorService;

    // ✅ Updated constructor per patch
    public UserGlobalPoolService(UserRepository userRepository,
                                 SystemRulesService systemRulesService,
                                 UserStateEvaluatorService userStateEvaluatorService) {
        this.userRepository = userRepository;
        this.systemRulesService = systemRulesService;
        this.userStateEvaluatorService = userStateEvaluatorService;
    }

    // =====================================================
    // 🔵 בקשת הצטרפות למאגר גלובלי
    // abilities 8–10, 20, 33
    // =====================================================

    public User requestGlobalAccess(Long userId) {
        // ✅ Eligibility gate (SSOT)
        userStateEvaluatorService.assertEligibleForGlobal(userId);

        User user = getUserOrThrow(userId);

        boolean approvedOnce =
                user.getGlobalApprovedAt() != null
                        || user.isGlobalAccessApproved()
                        || user.getGlobalAccessState() == GlobalAccessState.APPROVED;

        // ✅ Approved once => no downgrade, only toggle visibility in pool
        if (approvedOnce) {
            user.setInGlobalPool(true);
            user.setGlobalAccessApproved(true);
            user.setGlobalAccessState(GlobalAccessState.APPROVED);
            user.setGlobalAccessRequest(false);
            return userRepository.save(user);
        }

        if (user.isInGlobalPool()) {
            return user; // כבר בפנים → אין מה לעשות
        }

        user.setGlobalAccessRequest(true);
        user.setGlobalAccessApproved(false);
        user.setInGlobalPool(false);
        user.setGlobalAccessState(GlobalAccessState.REQUESTED);
        user.setGlobalRequestedAt(LocalDateTime.now());
        user.setGlobalRejectedAt(null);
        // ❌ לא מאפסים globalApprovedAt כאן

        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 אישור / דחיית מאגר גלובלי (Admin/System/Owner)
    // =====================================================

    public User approveGlobalAccess(Long userId) {
        User user = getUserOrThrow(userId);
        user.setGlobalAccessApproved(true);
        user.setGlobalAccessRequest(false);
        user.setInGlobalPool(true);
        user.setGlobalAccessState(GlobalAccessState.APPROVED);

        // ✅ per patch: don't override if already set
        if (user.getGlobalApprovedAt() == null) {
            user.setGlobalApprovedAt(LocalDateTime.now());
        }

        user.setGlobalRejectedAt(null);
        return userRepository.save(user);
    }

    public User rejectGlobalAccess(Long userId, boolean keepRequestFlag) {
        User user = getUserOrThrow(userId);

        boolean approvedOnce =
                user.getGlobalApprovedAt() != null
                        || user.isGlobalAccessApproved()
                        || user.getGlobalAccessState() == GlobalAccessState.APPROVED;

        // ✅ Approved once => no downgrade, only temporary removal from pool
        if (approvedOnce) {
            user.setInGlobalPool(false);
            user.setGlobalAccessRequest(false);
            user.setGlobalAccessApproved(true);
            user.setGlobalAccessState(GlobalAccessState.APPROVED);
            return userRepository.save(user);
        }

        user.setGlobalAccessApproved(false);
        user.setInGlobalPool(false);
        user.setGlobalAccessState(GlobalAccessState.REJECTED);
        user.setGlobalRejectedAt(LocalDateTime.now());

        if (!keepRequestFlag) {
            user.setGlobalAccessRequest(false);
        }

        return userRepository.save(user);
    }

    public User removeFromGlobalPool(Long userId) {
        User user = getUserOrThrow(userId);

        boolean approvedOnce =
                user.getGlobalApprovedAt() != null
                        || user.isGlobalAccessApproved()
                        || user.getGlobalAccessState() == GlobalAccessState.APPROVED;

        // ✅ Approved once => no downgrade, only temporary removal from pool
        if (approvedOnce) {
            user.setInGlobalPool(false);
            user.setGlobalAccessRequest(false);
            user.setGlobalAccessApproved(true);
            user.setGlobalAccessState(GlobalAccessState.APPROVED);
            return userRepository.save(user);
        }

        user.setInGlobalPool(false);
        user.setGlobalAccessApproved(false);
        user.setGlobalAccessRequest(false);
        user.setGlobalAccessState(GlobalAccessState.NONE);
        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 שאילתות על המאגר הגלובלי
    // abilities 24, 26, 28
    // =====================================================

    public List<User> findAllInGlobalPool() {
        return userRepository.findByInGlobalPoolTrueAndHasPrimaryPhotoTrueOrderByAgeAsc();
    }

    public List<User> findGlobalByGender(String gender) {
        return userRepository
                .findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderOrderByAgeAsc(gender);
    }

    public List<User> findGlobalByGenderAndAge(String gender, Integer minAge, Integer maxAge) {
        return userRepository
                .findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderAndAgeBetweenOrderByAgeAsc(
                        gender, minAge, maxAge
                );
    }

    public List<User> findGlobalByAreaAndAge(String area, Integer minAge, Integer maxAge) {
        return userRepository
                .findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndAreaOfResidenceAndAgeBetweenOrderByAgeAsc(
                        area, minAge, maxAge
                );
    }

    public List<User> findGlobalAdvanced(String gender,
                                         String areaOfResidence,
                                         String religiousLevel,
                                         Integer minAge,
                                         Integer maxAge) {

        return userRepository
                .findByInGlobalPoolTrueAndHasPrimaryPhotoTrueAndGenderAndAreaOfResidenceAndReligiousLevelAndAgeBetweenOrderByAgeAsc(
                        gender, areaOfResidence, religiousLevel, minAge, maxAge
                );
    }

    public long countGlobalPoolUsers() {
        return userRepository.countByInGlobalPoolTrue();
    }

    // =====================================================
    // 🔵 AI – מאגר גלובלי + בוסט
    // =====================================================

    public List<User> findGlobalWithAiBoost(Double minScore) {
        return userRepository.findByInGlobalPoolTrueAndAiMatchBoostScoreGreaterThan(minScore);
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
