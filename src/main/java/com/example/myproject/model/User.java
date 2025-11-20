package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    // =====================================================
    // 🔵 מזהה
    // =====================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // 🔵 מידע אישי בסיסי (חובה)
    // =====================================================
    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 10)
    private String gender;

    private Integer age;
    private Integer heightCm;
    private String areaOfResidence;
    private String religiousLevel;

    // =====================================================
    // 🔵 פרופיל מורחב
    // =====================================================
    private String bodyType;
    private String occupation;
    private String education;
    private String militaryService;
    private String maritalStatus;
    private String origin;

    @Column(length = 2000)
    private String personalityTraits;

    @Column(length = 2000)
    private String hobbies;

    @Column(length = 2000)
    private String familyDescription;

    @Column(length = 2000)
    private String lookingFor;

    private Integer preferredAgeFrom;
    private Integer preferredAgeTo;

    private String headCovering;
    private Boolean hasDrivingLicense;
    private Boolean smokes;

    private String inquiriesPhone1;
    private String inquiriesPhone2;

    // =====================================================
    // 🔵 העדפות והתנהגות במערכת
    // =====================================================
    private Boolean wantsSeriousRelationship;
    private Boolean wantsMatchmakingAssist;
    private Boolean openToLongDistance;

    private String religiosityPreference;
    private String smokingPreference;

    // =====================================================
    // 🔵 סטטוס פרופיל
    // =====================================================
    private boolean basicProfileCompleted = false;
    private boolean fullProfileCompleted = false;

    // =====================================================
    // 🔵 אימות חשבון
    // =====================================================
    private boolean verified = false;
    private String verificationCode;
    private String verificationMethod;

    // =====================================================
    // 🔵 מאגר גלובלי / בקשות
    // =====================================================
    private boolean inGlobalPool = false;
    private boolean globalAccessRequest = false;
    private boolean globalAccessApproved = false;

    private String signupSource;

    // =====================================================
    // 🔵 חתונות והיסטוריה
    // =====================================================
    private Long firstWeddingId;
    private Long lastWeddingId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_weddings_history", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "wedding_id")
    private List<Long> weddingsHistory;

    private Long activeBackgroundWeddingId;

    // =====================================================
    // 🔵 גישה ורשאות
    // =====================================================
    private boolean allowProfileViewByOppositeGender = true;
    private boolean allowProfileViewBySameGender = false;

    private boolean firstMessageSent = false;
    private boolean chatApproved = false;

    private boolean admin = false;
    private boolean eventManager = false;

    @Column(name = "can_view_wedding", nullable = false)
    private boolean canViewWedding = true;

    // =====================================================
    // 🔵 התראות
    // =====================================================
    private boolean allowInAppNotifications = true;
    private boolean allowEmailNotifications = true;
    private boolean allowSmsNotifications = true;
    private boolean pushDisabled = false;

    // =====================================================
    // 🔵 מחיקת חשבון
    // =====================================================
    private boolean deletionRequested = false;
    private LocalDateTime deletionRequestedAt;
    private LocalDateTime deletionDueDate;

    // =====================================================
    // 🔵 תמונות
    // =====================================================
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserPhoto> photos;

    private Integer photosCount = 0;

    @Column(name = "has_primary_photo", nullable = false)
    private boolean hasPrimaryPhoto = false;

    // =====================================================
    // 🔵 קשרים לוגיים נוספים
    // =====================================================
    @OneToMany(mappedBy = "actor")
    private List<UserAction> actionsDone;

    @OneToMany(mappedBy = "target")
    private List<UserAction> actionsReceived;

    @OneToMany(mappedBy = "sender")
    private List<ChatMessage> sentMessages;

    @OneToMany(mappedBy = "recipient")
    private List<ChatMessage> receivedMessages;

    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notification> notifications;

    // =====================================================
    // 🔵 AI
    // =====================================================
    @Column(columnDefinition = "TEXT")
    private String aiEmbedding;

    private Double aiMatchBoostScore = 1.0;

    // =====================================================
    // 🔵 תאריכים
    // =====================================================
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private Integer profileViewsCount = 0;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==========================
// 🔵 מצב חתונה / רקע למשתמש
// ==========================

    @Column(name = "active_wedding_id")
    private Long activeWeddingId;          // באיזו חתונה אני נמצא כרגע (אם בכלל)

    @Column(name = "background_wedding_id")
    private Long backgroundWeddingId;      // מאיזו חתונה לטעון רקע כרגע

    @Column(name = "background_mode", length = 20)
    private String backgroundMode = "DEFAULT";
// DEFAULT = מאגר כללי / רקע רגיל
// WEDDING = מצב חתונה – טוען רקע מהחתונה

    @Column(name = "wedding_entry_at")
    private LocalDateTime weddingEntryAt;  // מתי נכנסתי לחתונה האחרונה

    @Column(name = "wedding_exit_at")
    private LocalDateTime weddingExitAt;   // מתי יצאתי מהחתונה (אם יצאתי)

    // =====================================================
    // 🔵 בנאים
    // =====================================================
    public User() {}

    public User(String fullName, String phone, String email, String gender) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.createdAt = LocalDateTime.now();
    }

    // =====================================================
    // 🔵 Getters & Setters – חלק 1 (המשך בחלק 2)
    // =====================================================

    public Long getId() { return id; }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getGender() { return gender; }

    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }

    public void setAge(Integer age) { this.age = age; }

    public Integer getHeightCm() { return heightCm; }

    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }

    public String getAreaOfResidence() { return areaOfResidence; }

    public void setAreaOfResidence(String areaOfResidence) { this.areaOfResidence = areaOfResidence; }

    public String getReligiousLevel() { return religiousLevel; }

    public void setReligiousLevel(String religiousLevel) { this.religiousLevel = religiousLevel; }

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

    public Boolean getWantsSeriousRelationship() { return wantsSeriousRelationship; }
    public void setWantsSeriousRelationship(Boolean wantsSeriousRelationship) { this.wantsSeriousRelationship = wantsSeriousRelationship; }

    public Boolean getWantsMatchmakingAssist() { return wantsMatchmakingAssist; }
    public void setWantsMatchmakingAssist(Boolean wantsMatchmakingAssist) { this.wantsMatchmakingAssist = wantsMatchmakingAssist; }

    public Boolean getOpenToLongDistance() { return openToLongDistance; }
    public void setOpenToLongDistance(Boolean openToLongDistance) { this.openToLongDistance = openToLongDistance; }

    public String getReligiosityPreference() { return religiosityPreference; }
    public void setReligiosityPreference(String religiosityPreference) { this.religiosityPreference = religiosityPreference; }

    public String getSmokingPreference() { return smokingPreference; }
    public void setSmokingPreference(String smokingPreference) { this.smokingPreference = smokingPreference; }

    public boolean isBasicProfileCompleted() { return basicProfileCompleted; }
    public void setBasicProfileCompleted(boolean basicProfileCompleted) { this.basicProfileCompleted = basicProfileCompleted; }

    public boolean isFullProfileCompleted() { return fullProfileCompleted; }
    public void setFullProfileCompleted(boolean fullProfileCompleted) { this.fullProfileCompleted = fullProfileCompleted; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public boolean isInGlobalPool() { return inGlobalPool; }
    public void setInGlobalPool(boolean inGlobalPool) { this.inGlobalPool = inGlobalPool; }

    public boolean isGlobalAccessRequest() { return globalAccessRequest; }
    public void setGlobalAccessRequest(boolean globalAccessRequest) { this.globalAccessRequest = globalAccessRequest; }

    public boolean isGlobalAccessApproved() { return globalAccessApproved; }
    public void setGlobalAccessApproved(boolean globalAccessApproved) { this.globalAccessApproved = globalAccessApproved; }

    public String getSignupSource() { return signupSource; }
    public void setSignupSource(String signupSource) { this.signupSource = signupSource; }

    public Long getFirstWeddingId() { return firstWeddingId; }
    public void setFirstWeddingId(Long firstWeddingId) { this.firstWeddingId = firstWeddingId; }

    public Long getLastWeddingId() { return lastWeddingId; }
    public void setLastWeddingId(Long lastWeddingId) { this.lastWeddingId = lastWeddingId; }

    public List<Long> getWeddingsHistory() { return weddingsHistory; }
    public void setWeddingsHistory(List<Long> weddingsHistory) { this.weddingsHistory = weddingsHistory; }

    public Long getActiveBackgroundWeddingId() { return activeBackgroundWeddingId; }
    public void setActiveBackgroundWeddingId(Long activeBackgroundWeddingId) { this.activeBackgroundWeddingId = activeBackgroundWeddingId; }

    public boolean isAllowProfileViewByOppositeGender() { return allowProfileViewByOppositeGender; }
    public void setAllowProfileViewByOppositeGender(boolean allowProfileViewByOppositeGender) { this.allowProfileViewByOppositeGender = allowProfileViewByOppositeGender; }

    public boolean isAllowProfileViewBySameGender() { return allowProfileViewBySameGender; }
    public void setAllowProfileViewBySameGender(boolean allowProfileViewBySameGender) { this.allowProfileViewBySameGender = allowProfileViewBySameGender; }

    public boolean isFirstMessageSent() { return firstMessageSent; }
    public void setFirstMessageSent(boolean firstMessageSent) { this.firstMessageSent = firstMessageSent; }

    public boolean isChatApproved() { return chatApproved; }
    public void setChatApproved(boolean chatApproved) { this.chatApproved = chatApproved; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }

    public boolean isEventManager() { return eventManager; }
    public void setEventManager(boolean eventManager) { this.eventManager = eventManager; }

    public boolean isAllowInAppNotifications() { return allowInAppNotifications; }
    public void setAllowInAppNotifications(boolean allowInAppNotifications) { this.allowInAppNotifications = allowInAppNotifications; }

    public boolean isAllowEmailNotifications() { return allowEmailNotifications; }
    public void setAllowEmailNotifications(boolean allowEmailNotifications) { this.allowEmailNotifications = allowEmailNotifications; }

    public boolean isAllowSmsNotifications() { return allowSmsNotifications; }
    public void setAllowSmsNotifications(boolean allowSmsNotifications) { this.allowSmsNotifications = allowSmsNotifications; }

    public boolean isPushDisabled() { return pushDisabled; }
    public void setPushDisabled(boolean pushDisabled) { this.pushDisabled = pushDisabled; }

    public boolean isDeletionRequested() { return deletionRequested; }
    public void setDeletionRequested(boolean deletionRequested) { this.deletionRequested = deletionRequested; }

    public LocalDateTime getDeletionRequestedAt() { return deletionRequestedAt; }
    public void setDeletionRequestedAt(LocalDateTime deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }

    public LocalDateTime getDeletionDueDate() { return deletionDueDate; }
    public void setDeletionDueDate(LocalDateTime deletionDueDate) { this.deletionDueDate = deletionDueDate; }

    public List<UserPhoto> getPhotos() { return photos; }
    public void setPhotos(List<UserPhoto> photos) { this.photos = photos; }

    public Integer getPhotosCount() { return photosCount; }
    public void setPhotosCount(Integer photosCount) { this.photosCount = photosCount; }

    public boolean isHasPrimaryPhoto() { return hasPrimaryPhoto; }
    public void setHasPrimaryPhoto(boolean hasPrimaryPhoto) { this.hasPrimaryPhoto = hasPrimaryPhoto; }

    public boolean isCanViewWedding() { return canViewWedding; }
    public void setCanViewWedding(boolean canViewWedding) { this.canViewWedding = canViewWedding; }

    public List<UserAction> getActionsDone() { return actionsDone; }
    public void setActionsDone(List<UserAction> actionsDone) { this.actionsDone = actionsDone; }

    public List<UserAction> getActionsReceived() { return actionsReceived; }
    public void setActionsReceived(List<UserAction> actionsReceived) { this.actionsReceived = actionsReceived; }

    public List<ChatMessage> getSentMessages() { return sentMessages; }
    public void setSentMessages(List<ChatMessage> sentMessages) { this.sentMessages = sentMessages; }

    public List<ChatMessage> getReceivedMessages() { return receivedMessages; }
    public void setReceivedMessages(List<ChatMessage> receivedMessages) { this.receivedMessages = receivedMessages; }

    public List<Notification> getNotifications() { return notifications; }
    public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }

    public String getAiEmbedding() { return aiEmbedding; }
    public void setAiEmbedding(String aiEmbedding) { this.aiEmbedding = aiEmbedding; }

    public Double getAiMatchBoostScore() { return aiMatchBoostScore; }
    public void setAiMatchBoostScore(Double aiMatchBoostScore) { this.aiMatchBoostScore = aiMatchBoostScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Transient
    public List<String> getPhotoUrls() {
        if (photos == null) return List.of();
        return photos.stream()
                .filter(p -> !p.isDeleted())
                .map(UserPhoto::getUrl)
                .toList();
    }

    @Transient
    public String getPrimaryPhotoUrl() {
        if (photos == null) return null;

        return photos.stream()
                .filter(p -> !p.isDeleted())
                .filter(UserPhoto::isPrimaryPhoto)
                .map(UserPhoto::getUrl)
                .findFirst()
                .orElse(null);
    }

    public Integer getProfileViewsCount() {
        return profileViewsCount;
    }

    public void setProfileViewsCount(Integer profileViewsCount) {
        this.profileViewsCount = profileViewsCount;
    }

    public Long getActiveWeddingId() {
        return activeWeddingId;
    }

    public void setActiveWeddingId(Long activeWeddingId) {
        this.activeWeddingId = activeWeddingId;
    }

    public Long getBackgroundWeddingId() {
        return backgroundWeddingId;
    }

    public void setBackgroundWeddingId(Long backgroundWeddingId) {
        this.backgroundWeddingId = backgroundWeddingId;
    }

    public String getBackgroundMode() {
        return backgroundMode;
    }

    public void setBackgroundMode(String backgroundMode) {
        this.backgroundMode = backgroundMode;
    }

    public LocalDateTime getWeddingEntryAt() {
        return weddingEntryAt;
    }

    public void setWeddingEntryAt(LocalDateTime weddingEntryAt) {
        this.weddingEntryAt = weddingEntryAt;
    }

    public LocalDateTime getWeddingExitAt() {
        return weddingExitAt;
    }

    public void setWeddingExitAt(LocalDateTime weddingExitAt) {
        this.weddingExitAt = weddingExitAt;
    }
}