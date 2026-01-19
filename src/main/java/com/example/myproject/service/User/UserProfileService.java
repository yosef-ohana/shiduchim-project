package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserSettingsService userSettingsService;


    public UserProfileService(UserRepository userRepository,
                              UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.userSettingsService = userSettingsService;
    }

    // =====================================================
    // 🔵 עדכון פרופיל בסיסי
    // abilities 3, 6, 36
    // =====================================================

    public User updateBasicProfile(Long userId,
                                   Integer age,
                                   Integer heightCm,
                                   String areaOfResidence,
                                   String religiousLevel) {

        User user = getUserOrThrow(userId);

        user.setAge(age);
        user.setHeightCm(heightCm);
        user.setAreaOfResidence(areaOfResidence);
        user.setReligiousLevel(religiousLevel);

        recomputeBasicProfileCompleted(user);
        recomputeProfileState(user);
        user.setLastProfileUpdateAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private void recomputeBasicProfileCompleted(User user) {
        boolean complete =
                user.getFullName() != null &&
                        user.getPhone() != null &&
                        user.getEmail() != null &&
                        user.getGender() != null &&
                        user.getAge() != null &&
                        user.getAreaOfResidence() != null &&
                        user.getReligiousLevel() != null &&
                        user.isHasPrimaryPhoto();

        user.setBasicProfileCompleted(complete);
    }

    // =====================================================
    // 🔵 עדכון פרופיל מלא
    // abilities 4, 7, 32, 36
    // =====================================================

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
                                  Boolean smokes) {

        User user = getUserOrThrow(userId);

        user.setBodyType(bodyType);
        user.setOccupation(occupation);
        user.setEducation(education);
        user.setMilitaryService(militaryService);
        user.setMaritalStatus(maritalStatus);
        user.setOrigin(origin);
        user.setPersonalityTraits(personalityTraits);
        user.setHobbies(hobbies);
        user.setFamilyDescription(familyDescription);
        user.setLookingFor(lookingFor);
        user.setPreferredAgeFrom(preferredAgeFrom);
        user.setPreferredAgeTo(preferredAgeTo);
        user.setHeadCovering(headCovering);
        user.setHasDrivingLicense(hasDrivingLicense);
        user.setSmokes(smokes);

        recomputeFullProfileCompleted(user);
        // =====================================================
// ✅ Auto-unlock after completing full profile
// =====================================================
        if (user.isFullProfileCompleted() && userSettingsService.isCurrentlyLocked(userId)) {
            userSettingsService.unlockAfterWedding(userId, "Profile completed");
            user.setProfileLockedAfterWedding(false); // mirror/UI בלבד
        }

        recomputeProfileState(user);
        user.setLastProfileUpdateAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    private void recomputeFullProfileCompleted(User user) {
        boolean complete =
                user.getOccupation() != null &&
                        user.getEducation() != null &&
                        user.getMilitaryService() != null &&
                        user.getHobbies() != null &&
                        user.getPersonalityTraits() != null &&
                        user.getLookingFor() != null &&
                        user.getMaritalStatus() != null &&
                        user.getOrigin() != null &&
                        user.isHasPrimaryPhoto();

        user.setFullProfileCompleted(complete);
    }

    // =====================================================
    // 🔵 ProfileState לפי האפיון
    // NONE / BASIC_ONLY / FULL / LOCKED_AFTER_WEDDING
    // =====================================================

    public void recomputeProfileState(User user) {
        if (user.isProfileLockedAfterWedding()) {
            user.setProfileState(ProfileState.LOCKED_AFTER_WEDDING);
            return;
        }

        if (user.isFullProfileCompleted()) {
            user.setProfileState(ProfileState.FULL);
        } else if (user.isBasicProfileCompleted()) {
            user.setProfileState(ProfileState.BASIC_ONLY);
        } else {
            user.setProfileState(ProfileState.NONE);
        }
    }

    // =====================================================
    // 🔵 נעילת פרופיל אחרי חתונה (חוק "אחרי אירוע – נעל את הבסיס")
    // =====================================================

    public User lockProfileAfterWedding(Long userId) {
        User user = getUserOrThrow(userId);
        user.setProfileLockedAfterWedding(true);
        user.setProfileLockedAt(LocalDateTime.now());
        recomputeProfileState(user);
        return userRepository.save(user);
    }

    public User unlockProfile(Long userId) {
        User user = getUserOrThrow(userId);
        user.setProfileLockedAfterWedding(false);
        user.setProfileLockedAt(null);
        recomputeProfileState(user);
        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 צפיות פרופיל
    // =====================================================

    public void incrementProfileViews(Long userId) {
        User user = getUserOrThrow(userId);
        int current = (user.getProfileViewsCount() != null ? user.getProfileViewsCount() : 0);
        user.setProfileViewsCount(current + 1);
        userRepository.save(user);
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}