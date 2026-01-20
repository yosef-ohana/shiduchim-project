package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.GlobalAccessState;
import com.example.myproject.model.enums.ProfileState;
import com.example.myproject.model.enums.WeddingMode;
import com.example.myproject.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserQueryAndReportService {

    private final UserRepository userRepository;

    public UserQueryAndReportService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // =====================================================
    // 🔵 מי חסר לו פרופיל בסיסי / מלא / תמונה
    // abilities 3–7, 36
    // =====================================================

    public List<User> findUsersWithIncompleteBasicProfile() {
        return userRepository.findByBasicProfileCompletedFalse();
    }

    public List<User> findUsersWithIncompleteFullProfile() {
        return userRepository.findByFullProfileCompletedFalse();
    }

    public List<User> findUsersWithoutPrimaryPhoto() {
        return userRepository.findByHasPrimaryPhotoFalse();
    }

    // חסרים בפועל בשדות
    public List<User> findUsersMissingAge() {
        return userRepository.findByAgeIsNull();
    }

    public List<User> findUsersMissingGender() {
        return userRepository.findByGenderIsNull();
    }

    public List<User> findUsersMissingArea() {
        return userRepository.findByAreaOfResidenceIsNull();
    }

    public List<User> findUsersMissingReligiousLevel() {
        return userRepository.findByReligiousLevelIsNull();
    }

    // =====================================================
    // 🔵 מצב פרופיל לפי ProfileState
    // =====================================================

    public List<User> findByProfileState(ProfileState state) {
        return userRepository.findByProfileState(state);
    }

    // =====================================================
    // 🔵 GlobalAccessState
    // =====================================================

    public List<User> findByGlobalAccessState(GlobalAccessState state) {
        return userRepository.findByGlobalAccessState(state);
    }

    public List<User> findUsersRequestedGlobalButNotApproved() {
        return userRepository.findByGlobalAccessRequestTrueAndGlobalAccessApprovedFalse();
    }

    // =====================================================
    // 🔵 WeddingMode / חתונות
    // =====================================================

    public List<User> findByWeddingMode(WeddingMode mode) {
        return userRepository.findByWeddingMode(mode);
    }

    public List<User> findByWeddingModeAndActiveWedding(WeddingMode mode, Long weddingId) {
        return userRepository.findByWeddingModeAndActiveWeddingId(mode, weddingId);
    }

    // כניסות / יציאות
    public List<User> findUsersEnteredWeddingAfter(LocalDateTime since) {
        return userRepository.findByWeddingEntryAtAfter(since);
    }

    public List<User> findUsersExitedWeddingAfter(LocalDateTime since) {
        return userRepository.findByWeddingExitAtAfter(since);
    }

    public List<User> findUsersExitedWeddingBetween(LocalDateTime start, LocalDateTime end) {
        return userRepository.findByWeddingExitAtBetween(start, end);
    }

    // =====================================================
    // 🔵 נעילות / מחיקות
    // =====================================================

    public List<User> findProfileLockedAfterWedding() {
        return userRepository.findByProfileLockedAfterWeddingTrue();
    }

    public List<User> findProfileLockedBefore(LocalDateTime time) {
        return userRepository.findByProfileLockedAfterWeddingTrueAndProfileLockedAtBefore(time);
    }

    public List<User> findDeletionRequested() {
        return userRepository.findByDeletionRequestedTrue();
    }

    // =====================================================
    // 🔵 דוחות / פילטרים
    // abilities מסמך 1–2–3 (סטטיסטיקות)
    // =====================================================

    public List<User> findByGender(String gender) {
        return userRepository.findByGender(gender);
    }

    public List<User> findByAgeRange(Integer minAge, Integer maxAge) {
        return userRepository.findByAgeBetween(minAge, maxAge);
    }

    public List<User> findByArea(String area) {
        return userRepository.findByAreaOfResidence(area);
    }

    public List<User> findByReligiousLevel(String religiousLevel) {
        return userRepository.findByReligiousLevel(religiousLevel);
    }

    public List<User> findByGenderAreaReligious(String gender, String area, String religLevel) {
        return userRepository.findByGenderAndAreaOfResidenceAndReligiousLevel(
                gender, area, religLevel
        );
    }

    public List<User> findUsersFullProfileAndGlobal() {
        return userRepository.findByFullProfileCompletedTrueAndInGlobalPoolTrue();
    }

    public List<User> findUsersBasicButNotFull() {
        return userRepository.findByBasicProfileCompletedTrueAndFullProfileCompletedFalse();
    }

    // =====================================================
    // 🔵 Heartbeat – משתמשים לא פעילים
    // =====================================================

    public List<User> findUsersLastSeenBefore(LocalDateTime cutoff) {
        return userRepository.findByLastSeenBefore(cutoff);
    }

    public List<User> findUsersLastSeenBetween(LocalDateTime start, LocalDateTime end) {
        return userRepository.findByLastSeenBetween(start, end);
    }

    // =====================================================
    // 🔵 עדכון אחרון של פרופיל
    // =====================================================

    public List<User> findUsersUpdatedProfileAfter(LocalDateTime since) {
        return userRepository.findByLastProfileUpdateAtAfter(since);
    }

    // =====================================================
// 🔵 Photo Quality / Invites Reports (SSOT wrappers)
// =====================================================

    public java.util.List<User> listUsersByPhotosCount(int photosCount) {
        if (photosCount < 0) photosCount = 0;
        return userRepository.findByPhotosCount(photosCount);
    }

    public java.util.List<User> listUsersByPhotosCountAtMost(int maxPhotosCount) {
        if (maxPhotosCount < 0) maxPhotosCount = 0;
        // אצלך ב-UserRepository כבר קיים: findByPhotosCountLessThanEqual
        return userRepository.findByPhotosCountLessThanEqual(maxPhotosCount);
    }

    public java.util.List<User> listUsersMissingPrimaryButHasPhotos() {
        // אצלך ב-UserRepository כבר קיים: findByHasPrimaryPhotoFalseAndPhotosCountGreaterThan
        return userRepository.findByHasPrimaryPhotoFalseAndPhotosCountGreaterThan(0);
    }

    public java.util.List<User> listUsersInvitedBy(Long invitedByUserId) {
        if (invitedByUserId == null) return java.util.List.of();
        // אצלך ב-UserRepository כבר קיים: findByInvitedByUserId
        return userRepository.findByInvitedByUserId(invitedByUserId);
    }
}