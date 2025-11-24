package com.example.myproject.dto;

import java.time.LocalDateTime;
import java.util.List;

public class UserProfileResponse {

    // ========= מידע בסיסי ========
    private Long id;
    private String fullName;
    private String gender;
    private Integer age;
    private Integer heightCm;
    private String areaOfResidence;
    private String religiousLevel;

    // ========= פרופיל מורחב ========
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
    private Boolean smokes;
    private Boolean hasDrivingLicense;
    private String headCovering;

    // ========= סטטוס פרופיל ========
    private boolean basicProfileCompleted;
    private boolean fullProfileCompleted;
    private boolean hasAtLeastOnePhoto;
    private boolean hasPrimaryPhoto;

    // ========= מאגר גלובלי ========
    private boolean inGlobalPool;
    private boolean globalAccessRequest;
    private boolean globalAccessApproved;
    private boolean canEnterGlobalPool;

    // ========= חתונות ========
    private Long activeWeddingId;
    private Long backgroundWeddingId;
    private String backgroundMode;

    private Long firstWeddingId;
    private Long lastWeddingId;
    private List<Long> weddingsHistory;

    // ========= תמונות ========
    private Integer photosCount;
    private String primaryPhotoUrl;
    private List<PhotoDto> photos;

    // ========= תאריכים ========
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // =====================================================
    // 🔵 DTO פנימי של תמונה
    // =====================================================
    public static class PhotoDto {
        private Long id;
        private String url;
        private boolean primary;
        private boolean deleted;
        private Integer positionIndex;

        public PhotoDto() {}

        public PhotoDto(Long id, String url, boolean primary, boolean deleted, Integer positionIndex) {
            this.id = id;
            this.url = url;
            this.primary = primary;
            this.deleted = deleted;
            this.positionIndex = positionIndex;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public boolean isPrimary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }

        public Integer getPositionIndex() { return positionIndex; }
        public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }
    }

    // =====================================================
    // 🔵 בנאים
    // =====================================================

    public UserProfileResponse() {}

    public UserProfileResponse(Long id,
                               String fullName,
                               String gender,
                               Integer age,
                               Integer heightCm,
                               String areaOfResidence,
                               String religiousLevel,
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
                               Boolean smokes,
                               Boolean hasDrivingLicense,
                               String headCovering,
                               boolean basicProfileCompleted,
                               boolean fullProfileCompleted,
                               boolean hasAtLeastOnePhoto,
                               boolean hasPrimaryPhoto,
                               boolean inGlobalPool,
                               boolean globalAccessRequest,
                               boolean globalAccessApproved,
                               boolean canEnterGlobalPool,
                               Long activeWeddingId,
                               Long backgroundWeddingId,
                               String backgroundMode,
                               Long firstWeddingId,
                               Long lastWeddingId,
                               List<Long> weddingsHistory,
                               Integer photosCount,
                               String primaryPhotoUrl,
                               List<PhotoDto> photos,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {

        this.id = id;
        this.fullName = fullName;
        this.gender = gender;
        this.age = age;
        this.heightCm = heightCm;
        this.areaOfResidence = areaOfResidence;
        this.religiousLevel = religiousLevel;

        this.bodyType = bodyType;
        this.occupation = occupation;
        this.education = education;
        this.militaryService = militaryService;
        this.maritalStatus = maritalStatus;
        this.origin = origin;
        this.personalityTraits = personalityTraits;
        this.hobbies = hobbies;
        this.familyDescription = familyDescription;
        this.lookingFor = lookingFor;
        this.smokes = smokes;
        this.hasDrivingLicense = hasDrivingLicense;
        this.headCovering = headCovering;

        this.basicProfileCompleted = basicProfileCompleted;
        this.fullProfileCompleted = fullProfileCompleted;
        this.hasAtLeastOnePhoto = hasAtLeastOnePhoto;
        this.hasPrimaryPhoto = hasPrimaryPhoto;

        this.inGlobalPool = inGlobalPool;
        this.globalAccessRequest = globalAccessRequest;
        this.globalAccessApproved = globalAccessApproved;
        this.canEnterGlobalPool = canEnterGlobalPool;

        this.activeWeddingId = activeWeddingId;
        this.backgroundWeddingId = backgroundWeddingId;
        this.backgroundMode = backgroundMode;

        this.firstWeddingId = firstWeddingId;
        this.lastWeddingId = lastWeddingId;
        this.weddingsHistory = weddingsHistory;

        this.photosCount = photosCount;
        this.primaryPhotoUrl = primaryPhotoUrl;
        this.photos = photos;

        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // =====================================================
    // 🔵 Getters & Setters
    // =====================================================

    // === מידע בסיסי ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

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

    // === פרופיל מורחב ===
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

    public Boolean getSmokes() { return smokes; }
    public void setSmokes(Boolean smokes) { this.smokes = smokes; }

    public Boolean getHasDrivingLicense() { return hasDrivingLicense; }
    public void setHasDrivingLicense(Boolean hasDrivingLicense) { this.hasDrivingLicense = hasDrivingLicense; }

    public String getHeadCovering() { return headCovering; }
    public void setHeadCovering(String headCovering) { this.headCovering = headCovering; }

    // === סטטוסי פרופיל ===
    public boolean isBasicProfileCompleted() { return basicProfileCompleted; }
    public void setBasicProfileCompleted(boolean basicProfileCompleted) { this.basicProfileCompleted = basicProfileCompleted; }

    public boolean isFullProfileCompleted() { return fullProfileCompleted; }
    public void setFullProfileCompleted(boolean fullProfileCompleted) { this.fullProfileCompleted = fullProfileCompleted; }

    public boolean isHasAtLeastOnePhoto() { return hasAtLeastOnePhoto; }
    public void setHasAtLeastOnePhoto(boolean hasAtLeastOnePhoto) { this.hasAtLeastOnePhoto = hasAtLeastOnePhoto; }

    public boolean isHasPrimaryPhoto() { return hasPrimaryPhoto; }
    public void setHasPrimaryPhoto(boolean hasPrimaryPhoto) { this.hasPrimaryPhoto = hasPrimaryPhoto; }

    // === גלובלי ===
    public boolean isInGlobalPool() { return inGlobalPool; }
    public void setInGlobalPool(boolean inGlobalPool) { this.inGlobalPool = inGlobalPool; }

    public boolean isGlobalAccessRequest() { return globalAccessRequest; }
    public void setGlobalAccessRequest(boolean globalAccessRequest) { this.globalAccessRequest = globalAccessRequest; }

    public boolean isGlobalAccessApproved() { return globalAccessApproved; }
    public void setGlobalAccessApproved(boolean globalAccessApproved) { this.globalAccessApproved = globalAccessApproved; }

    public boolean isCanEnterGlobalPool() { return canEnterGlobalPool; }
    public void setCanEnterGlobalPool(boolean canEnterGlobalPool) { this.canEnterGlobalPool = canEnterGlobalPool; }

    // === חתונות ===
    public Long getActiveWeddingId() { return activeWeddingId; }
    public void setActiveWeddingId(Long activeWeddingId) { this.activeWeddingId = activeWeddingId; }

    public Long getBackgroundWeddingId() { return backgroundWeddingId; }
    public void setBackgroundWeddingId(Long backgroundWeddingId) { this.backgroundWeddingId = backgroundWeddingId; }

    public String getBackgroundMode() { return backgroundMode; }
    public void setBackgroundMode(String backgroundMode) { this.backgroundMode = backgroundMode; }

    public Long getFirstWeddingId() { return firstWeddingId; }
    public void setFirstWeddingId(Long firstWeddingId) { this.firstWeddingId = firstWeddingId; }

    public Long getLastWeddingId() { return lastWeddingId; }
    public void setLastWeddingId(Long lastWeddingId) { this.lastWeddingId = lastWeddingId; }

    public List<Long> getWeddingsHistory() { return weddingsHistory; }
    public void setWeddingsHistory(List<Long> weddingsHistory) { this.weddingsHistory = weddingsHistory; }

    // === תמונות ===
    public Integer getPhotosCount() { return photosCount; }
    public void setPhotosCount(Integer photosCount) { this.photosCount = photosCount; }

    public String getPrimaryPhotoUrl() { return primaryPhotoUrl; }
    public void setPrimaryPhotoUrl(String primaryPhotoUrl) { this.primaryPhotoUrl = primaryPhotoUrl; }

    public List<PhotoDto> getPhotos() { return photos; }
    public void setPhotos(List<PhotoDto> photos) { this.photos = photos; }

    // === תאריכים ===
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}