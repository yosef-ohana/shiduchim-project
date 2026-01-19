package com.example.myproject.service;

import com.example.myproject.model.SystemLog;
import com.example.myproject.model.SystemSettings;
import com.example.myproject.model.User;
import com.example.myproject.model.UserPhoto;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.repository.SystemLogRepository;
import com.example.myproject.repository.SystemSettingsRepository;
import com.example.myproject.repository.UserPhotoRepository;
import com.example.myproject.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UserPhotoService (MASTER 2025 - FINAL ALIGN)
 *
 * ✅ מיושר ל:
 * - UserPhoto entity שסיפקת (imageUrl + getUrl/setUrl, primaryPhoto/main/deleted/positionIndex/metadataJson/fileType/fileSizeBytes/lockedAfterWedding וכו')
 * - SystemSettings (keyName/value/description/updatedAt) דרך SystemSettingsRepository.findByKeyName
 * - SystemLog (timestamp/userId/details/contextJson/...) + enums: SystemActionType/SystemModule/SystemSeverityLevel
 */
@Service
public class UserPhotoService {

    // =========================
    // Public models
    // =========================

    public enum ContextMode {
        WEDDING_POOL,
        GLOBAL_POOL,
        PROFILE_GALLERY
    }

    public enum ChangeType {
        ADDED,
        PRIMARY_CHANGED,
        REORDERED,
        DELETED_SOFT,
        DELETED_HARD,
        RESTORED,
        NOT_WORTHY_FLAGGED,
        NOT_WORTHY_CLEARED,
        LOCK_TOGGLED,
        GALLERY_BECAME_EMPTY,
        GALLERY_BECAME_VALID
    }

    public static class UserPhotoChangedEvent {
        private final Long userId;
        private final Long photoId;
        private final ChangeType type;
        private final Map<String, String> meta;

        public UserPhotoChangedEvent(Long userId, Long photoId, ChangeType type, Map<String, String> meta) {
            this.userId = userId;
            this.photoId = photoId;
            this.type = type;
            this.meta = meta == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(meta));
        }

        public Long getUserId() { return userId; }
        public Long getPhotoId() { return photoId; }
        public ChangeType getType() { return type; }
        public Map<String, String> getMeta() { return meta; }
    }

    public static class GalleryStatus {
        public final Long userId;
        public final int activeCount;
        public final int deletedCount;
        public final int notWorthyCount;
        public final boolean hasPrimary;
        public final boolean blockedNoPhotos;
        public final int maxAllowed;
        public final String messageForUser;

        public GalleryStatus(Long userId,
                             int activeCount,
                             int deletedCount,
                             int notWorthyCount,
                             boolean hasPrimary,
                             boolean blockedNoPhotos,
                             int maxAllowed,
                             String messageForUser) {
            this.userId = userId;
            this.activeCount = activeCount;
            this.deletedCount = deletedCount;
            this.notWorthyCount = notWorthyCount;
            this.hasPrimary = hasPrimary;
            this.blockedNoPhotos = blockedNoPhotos;
            this.maxAllowed = maxAllowed;
            this.messageForUser = messageForUser;
        }
    }

    // =========================
    // Dependencies
    // =========================

    private final UserPhotoRepository userPhotoRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<SystemLogRepository> systemLogRepositoryProvider;
    private final ObjectProvider<ApplicationEventPublisher> eventPublisherProvider;
    private final ObjectProvider<EntityManager> entityManagerProvider;
    private final ObjectProvider<SystemSettingsRepository> systemSettingsRepositoryProvider;

    // =========================
    // SystemSettings keys
    // =========================

    private static final String KEY_MAX_FILE_SIZE_BYTES    = "system.userphoto.maxFileSizeBytes";
    private static final String KEY_ALLOW_PDF              = "system.userphoto.allowPdf";
    private static final String KEY_LOCK_PRIMARY_FIRST     = "system.userphoto.lockPrimaryFirst";
    private static final String KEY_HARD_DELETE_AFTER_DAYS = "system.userphoto.hardDeleteAfterDays";
    private static final String KEY_MAX_PHOTOS             = "system.userphoto.maxPhotos";
    private static final String KEY_ALLOWED_TYPES          = "system.userphoto.allowedTypes"; // csv: jpg,jpeg,png,webp[,pdf]

    // =========================
    // Defaults (fallbacks)
    // =========================

    private static final int DEFAULT_MAX_PHOTOS = 6;

    private final long defaultMaxFileSizeBytes;
    private final boolean defaultAllowPdf;
    private final boolean defaultLockPrimaryFirst;
    private final int defaultHardDeleteAfterDays;

    // =========================
    // Policy cache
    // =========================

    private static final int POLICY_CACHE_SECONDS = 30;

    private static class PolicySnapshot {
        final long maxFileSizeBytes;
        final boolean allowPdf;
        final boolean lockPrimaryFirst;
        final int hardDeleteAfterDays;
        final int maxPhotos;
        final Set<String> allowedTypesLower;

        PolicySnapshot(long maxFileSizeBytes,
                       boolean allowPdf,
                       boolean lockPrimaryFirst,
                       int hardDeleteAfterDays,
                       int maxPhotos,
                       Set<String> allowedTypesLower) {
            this.maxFileSizeBytes = maxFileSizeBytes;
            this.allowPdf = allowPdf;
            this.lockPrimaryFirst = lockPrimaryFirst;
            this.hardDeleteAfterDays = hardDeleteAfterDays;
            this.maxPhotos = maxPhotos;
            this.allowedTypesLower = allowedTypesLower;
        }
    }

    private volatile PolicySnapshot policyCache;
    private volatile LocalDateTime policyCacheAt;

    public UserPhotoService(
            UserPhotoRepository userPhotoRepository,
            UserRepository userRepository,
            ObjectProvider<SystemLogRepository> systemLogRepositoryProvider,
            ObjectProvider<ApplicationEventPublisher> eventPublisherProvider,
            ObjectProvider<EntityManager> entityManagerProvider,
            ObjectProvider<SystemSettingsRepository> systemSettingsRepositoryProvider,

            @Value("${app.userphoto.maxFileSizeBytes:8388608}") long maxFileSizeBytes,
            @Value("${app.userphoto.allowPdf:false}") boolean allowPdf,
            @Value("${app.userphoto.lockPrimaryFirst:true}") boolean lockPrimaryFirst,
            @Value("${app.userphoto.hardDeleteAfterDays:30}") int hardDeleteAfterDays
    ) {
        this.userPhotoRepository = userPhotoRepository;
        this.userRepository = userRepository;
        this.systemLogRepositoryProvider = systemLogRepositoryProvider;
        this.eventPublisherProvider = eventPublisherProvider;
        this.entityManagerProvider = entityManagerProvider;
        this.systemSettingsRepositoryProvider = systemSettingsRepositoryProvider;

        this.defaultMaxFileSizeBytes = maxFileSizeBytes;
        this.defaultAllowPdf = allowPdf;
        this.defaultLockPrimaryFirst = lockPrimaryFirst;
        this.defaultHardDeleteAfterDays = Math.max(1, hardDeleteAfterDays);

        this.policyCache = null;
        this.policyCacheAt = null;
    }

    // =========================================================
    // Policy resolution
    // =========================================================

    private PolicySnapshot getPolicy() {
        LocalDateTime now = LocalDateTime.now();
        PolicySnapshot cached = policyCache;
        LocalDateTime cachedAt = policyCacheAt;

        if (cached != null && cachedAt != null && cachedAt.plusSeconds(POLICY_CACHE_SECONDS).isAfter(now)) {
            return cached;
        }

        PolicySnapshot fresh = loadPolicyFromSettings();
        policyCache = fresh;
        policyCacheAt = now;
        return fresh;
    }

    private PolicySnapshot loadPolicyFromSettings() {
        Long maxSize = readLong(KEY_MAX_FILE_SIZE_BYTES);
        Boolean allowPdf = readBool(KEY_ALLOW_PDF);
        Boolean lockFirst = readBool(KEY_LOCK_PRIMARY_FIRST);
        Integer hardDays = readInt(KEY_HARD_DELETE_AFTER_DAYS);
        Integer maxPhotos = readInt(KEY_MAX_PHOTOS);
        String allowedTypes = readString(KEY_ALLOWED_TYPES);

        long resolvedMaxSize = (maxSize != null && maxSize > 0) ? maxSize : defaultMaxFileSizeBytes;
        boolean resolvedAllowPdf = (allowPdf != null) ? allowPdf : defaultAllowPdf;
        boolean resolvedLockFirst = (lockFirst != null) ? lockFirst : defaultLockPrimaryFirst;
        int resolvedHardDays = (hardDays != null && hardDays > 0) ? hardDays : defaultHardDeleteAfterDays;

        int resolvedMaxPhotos = (maxPhotos != null && maxPhotos > 0) ? maxPhotos : DEFAULT_MAX_PHOTOS;
        resolvedMaxPhotos = Math.max(1, Math.min(resolvedMaxPhotos, 12));
        resolvedHardDays = Math.max(1, Math.min(resolvedHardDays, 3650));

        Set<String> resolvedAllowed;
        if (allowedTypes != null && !allowedTypes.isBlank()) {
            resolvedAllowed = parseAllowedTypes(allowedTypes);
        } else {
            LinkedHashSet<String> base = new LinkedHashSet<>(List.of("jpg", "jpeg", "png", "webp"));
            if (resolvedAllowPdf) base.add("pdf");
            resolvedAllowed = base;
        }

        Set<String> lower = resolvedAllowed.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .map(s -> s.equals("jpg") ? "jpeg" : s)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new PolicySnapshot(
                resolvedMaxSize,
                resolvedAllowPdf,
                resolvedLockFirst,
                resolvedHardDays,
                resolvedMaxPhotos,
                Collections.unmodifiableSet(lower)
        );
    }

    private Set<String> parseAllowedTypes(String csv) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String s = (part == null ? "" : part.trim().toLowerCase());
            if (s.isBlank()) continue;
            if (s.contains("/")) s = s.substring(s.indexOf('/') + 1);
            if (s.equals("jpg")) s = "jpeg";
            out.add(s);
        }
        return out;
    }

    private String readString(String key) {
        SystemSettingsRepository repo = systemSettingsRepositoryProvider.getIfAvailable();
        if (repo == null) return null;
        try { return repo.findByKeyName(key).map(SystemSettings::getValue).orElse(null); }
        catch (Exception e) { return null; }
    }

    private Boolean readBool(String key) {
        String v = readString(key);
        if (v == null) return null;
        String s = v.trim().toLowerCase();
        if (s.isBlank()) return null;
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y") || s.equals("on")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n") || s.equals("off")) return false;
        return null;
    }

    private Integer readInt(String key) {
        String v = readString(key);
        if (v == null) return null;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return null; }
    }

    private Long readLong(String key) {
        String v = readString(key);
        if (v == null) return null;
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return null; }
    }

    // =========================================================
    // 1) Add / Register
    // =========================================================

    @Transactional
    public UserPhoto addPhoto(Long userId,
                              String imageUrl,
                              String fileType,
                              Long fileSizeBytes,
                              String metadataJson,
                              boolean makePrimary,
                              boolean uploadedByAdmin) {

        Objects.requireNonNull(userId, "userId is required");
        requireNonBlank(imageUrl, "imageUrl is required");

        PolicySnapshot policy = getPolicy();
        User user = requireUser(userId);

        validateFileType(fileType, policy);
        validateFileSize(fileSizeBytes, policy);

        int activeCount = countActive(userId);
        if (activeCount >= policy.maxPhotos) {
            throw new IllegalStateException("Gallery limit reached (" + policy.maxPhotos + "). Delete a photo to add more.");
        }

        int nextPosition = computeNextPositionIndex(userId);

        UserPhoto photo = new UserPhoto();
        photo.setUser(user);
        photo.setUrl(imageUrl);

        if (fileType != null) photo.setFileType(normalizeFileType(fileType));
        if (fileSizeBytes != null) photo.setFileSizeBytes(fileSizeBytes);
        if (metadataJson != null) photo.setMetadataJson(metadataJson);

        photo.setPositionIndex(nextPosition);
        photo.setDeleted(false);
        photo.setDeletedAt(null);
        photo.setUploadedByAdmin(uploadedByAdmin);

        boolean shouldBePrimary = makePrimary || (activeCount == 0);
        if (shouldBePrimary) {
            userPhotoRepository.clearPrimaryForUser(userId);
            photo.setPrimaryPhoto(true);
            photo.setMain(true);
        } else {
            photo.setPrimaryPhoto(false);
            photo.setMain(false);
        }

        photo.setMetadataJson(ensureNotWorthyFalse(photo.getMetadataJson()));

        UserPhoto saved = userPhotoRepository.save(photo);

        if (policy.lockPrimaryFirst && saved.isPrimaryPhoto()) {
            forcePrimaryFirst(userId);
        }

        syncPhotosCountIfExists(userId);

        audit(
                SystemActionType.USER_PHOTO_UPLOADED,
                SystemSeverityLevel.INFO,
                true,
                userId,
                saved.getId(),
                false,
                Map.of(
                        "byAdmin", String.valueOf(uploadedByAdmin),
                        "makePrimary", String.valueOf(shouldBePrimary),
                        "type", safeStr(fileType),
                        "size", safeStr(fileSizeBytes)
                ),
                "Added photo"
        );

        publish(new UserPhotoChangedEvent(userId, saved.getId(), ChangeType.ADDED,
                Map.of("primary", String.valueOf(shouldBePrimary))));

        publishGalleryValidityEvent(userId);

        return saved;
    }

    // =========================================================
    // 2) Read / List
    // =========================================================

    @Transactional(readOnly = true)
    public UserPhoto getPhotoOrThrow(Long photoId) {
        Objects.requireNonNull(photoId, "photoId is required");
        return userPhotoRepository.findById(photoId)
                .orElseThrow(() -> new NoSuchElementException("UserPhoto not found: " + photoId));
    }

    @Transactional(readOnly = true)
    public Optional<UserPhoto> getPhotoForUser(Long userId, Long photoId) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");
        return userPhotoRepository.findByIdAndUser_Id(photoId, userId);
    }

    @Transactional(readOnly = true)
    public List<UserPhoto> listActivePhotosForGallery(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return userPhotoRepository.findByUser_IdAndDeletedFalseOrderByPositionIndexAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Page<UserPhoto> listActivePhotosForGallery(Long userId, Pageable pageable) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(pageable, "pageable is required");
        return userPhotoRepository.findByUser_IdAndDeletedFalse(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<UserPhoto> listVisiblePhotosForPools(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return listActivePhotosForGallery(userId).stream()
                .filter(p -> !isNotWorthy(p))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserPhoto> listAllPhotosIncludingDeleted(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return userPhotoRepository.findByUser_IdOrderByPositionIndexAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<UserPhoto> listDeletedPhotos(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return userPhotoRepository.findByUser_IdAndDeletedTrueOrderByDeletedAtDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserPhoto> getPrimaryPhoto(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return userPhotoRepository.findFirstByUser_IdAndDeletedFalseAndPrimaryPhotoTrue(userId);
    }

    // =========================================================
    // 3) Full Gallery Status
    // =========================================================

    @Transactional(readOnly = true)
    public GalleryStatus getGalleryStatus(Long userId) {
        Objects.requireNonNull(userId, "userId is required");

        PolicySnapshot policy = getPolicy();

        int active = countActive(userId);
        int deleted = (int) userPhotoRepository.countByUser_IdAndDeletedTrue(userId);
        int notWorthy = countNotWorthyActive(userId);
        boolean hasPrimary = getPrimaryPhoto(userId).isPresent();
        boolean blocked = active <= 0;

        String msg;
        if (blocked) msg = "כדי להשתמש במערכת, עליך להעלות לפחות תמונה אחת.";
        else if (!hasPrimary) msg = "פרופילך לא יוצג במאגרים עד שתגדיר תמונה ראשית.";
        else if (notWorthy > 0) msg = "יש לך תמונה שסומנה כלא ראויה. מומלץ להחליף אותה.";
        else msg = "יש לך " + active + " מתוך " + policy.maxPhotos + " תמונות.";

        return new GalleryStatus(userId, active, deleted, notWorthy, hasPrimary, blocked, policy.maxPhotos, msg);
    }

    // =========================================================
    // 4) helpers
    // =========================================================

    @Transactional(readOnly = true)
    public boolean userHasAtLeastOneActivePhoto(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return countActive(userId) > 0;
    }

    @Transactional(readOnly = true)
    public void assertHasAtLeastOnePhoto(Long userId) {
        if (!userHasAtLeastOneActivePhoto(userId)) {
            throw new IllegalStateException("User must have at least one active photo");
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserPhoto> ensurePrimaryExistsIfPossible(Long userId) {
        Objects.requireNonNull(userId, "userId is required");

        PolicySnapshot policy = getPolicy();

        Optional<UserPhoto> existing = getPrimaryPhoto(userId);
        if (existing.isPresent()) return existing;

        List<UserPhoto> active = listActivePhotosForGallery(userId);
        if (active.isEmpty()) return Optional.empty();

        UserPhoto first = active.get(0);
        userPhotoRepository.clearPrimaryForUser(userId);
        first.setPrimaryPhoto(true);
        first.setMain(true);

        UserPhoto saved = userPhotoRepository.save(first);

        if (policy.lockPrimaryFirst) forcePrimaryFirst(userId);

        audit(
                SystemActionType.USER_PHOTO_SET_PRIMARY,
                SystemSeverityLevel.INFO,
                true,
                userId,
                saved.getId(),
                true,
                Map.of("auto", "true"),
                "Auto-selected primary photo"
        );

        publish(new UserPhotoChangedEvent(userId, saved.getId(), ChangeType.PRIMARY_CHANGED, Map.of("auto", "true")));
        return Optional.of(saved);
    }

    // =========================================================
    // 5) Primary & Reorder
    // =========================================================

    @Transactional
    public UserPhoto setPrimaryPhoto(Long userId, Long photoId, boolean enforceLocked, boolean adminOverride) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        PolicySnapshot policy = getPolicy();

        UserPhoto photo = getPhotoOrThrow(photoId);
        requireOwnership(userId, photo);

        if (photo.isDeleted()) throw new IllegalStateException("Cannot set primary: photo is deleted");
        if (enforceLocked) enforceNotLocked(photo, adminOverride);

        userPhotoRepository.clearPrimaryForUser(userId);
        photo.setPrimaryPhoto(true);
        photo.setMain(true);

        UserPhoto saved = userPhotoRepository.save(photo);

        if (policy.lockPrimaryFirst) forcePrimaryFirst(userId);

        syncPhotosCountIfExists(userId);

        audit(
                SystemActionType.USER_PHOTO_SET_PRIMARY,
                SystemSeverityLevel.INFO,
                true,
                userId,
                saved.getId(),
                false,
                Map.of("byAdmin", String.valueOf(adminOverride)),
                "Set primary photo"
        );

        publish(new UserPhotoChangedEvent(userId, saved.getId(), ChangeType.PRIMARY_CHANGED, Map.of()));
        publishGalleryValidityEvent(userId);

        return saved;
    }

    @Transactional
    public void reorder(Long userId, List<Long> orderedPhotoIds, boolean includeDeleted) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(orderedPhotoIds, "orderedPhotoIds is required");
        if (orderedPhotoIds.isEmpty()) return;

        PolicySnapshot policy = getPolicy();

        List<UserPhoto> photos = includeDeleted ? listAllPhotosIncludingDeleted(userId) : listActivePhotosForGallery(userId);
        Map<Long, UserPhoto> byId = photos.stream().collect(Collectors.toMap(UserPhoto::getId, p -> p));

        for (Long pid : orderedPhotoIds) {
            if (pid == null) throw new IllegalArgumentException("orderedPhotoIds contains null");
            if (!byId.containsKey(pid)) throw new IllegalArgumentException("PhotoId not found in user's scope: " + pid);
        }

        List<Long> finalOrder = new ArrayList<>(orderedPhotoIds);

        if (policy.lockPrimaryFirst && !includeDeleted) {
            Optional<UserPhoto> primary = getPrimaryPhoto(userId);
            if (primary.isPresent()) {
                Long primaryId = primary.get().getId();
                finalOrder.remove(primaryId);
                finalOrder.add(0, primaryId);
            }
        }

        int idx = 0;
        for (Long pid : finalOrder) {
            userPhotoRepository.updatePosition(pid, idx++);
        }

        audit(
                SystemActionType.USER_PHOTO_REORDERED,
                SystemSeverityLevel.INFO,
                true,
                userId,
                null,
                false,
                Map.of("count", String.valueOf(finalOrder.size()), "includeDeleted", String.valueOf(includeDeleted)),
                "Reordered photos"
        );

        publish(new UserPhotoChangedEvent(userId, null, ChangeType.REORDERED, Map.of("count", String.valueOf(finalOrder.size()))));
        touchProfileUpdated(userId);
    }

    @Transactional
    public void moveToPosition(Long userId, Long photoId, int newPosition) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        List<UserPhoto> active = listActivePhotosForGallery(userId);
        if (active.isEmpty()) return;

        List<Long> ids = active.stream().map(UserPhoto::getId).collect(Collectors.toList());
        if (!ids.contains(photoId)) throw new IllegalArgumentException("photoId is not an active photo of user");

        ids.remove(photoId);
        int safePos = Math.max(0, Math.min(newPosition, ids.size()));
        ids.add(safePos, photoId);

        reorder(userId, ids, false);
    }

    private void forcePrimaryFirst(Long userId) {
        Optional<UserPhoto> p = getPrimaryPhoto(userId);
        if (p.isEmpty()) return;

        List<UserPhoto> active = listActivePhotosForGallery(userId);
        if (active.isEmpty()) return;

        Long primaryId = p.get().getId();

        List<Long> order = new ArrayList<>();
        order.add(primaryId);
        for (UserPhoto up : active) {
            if (!Objects.equals(up.getId(), primaryId)) order.add(up.getId());
        }
        reorder(userId, order, false);
    }

    // =========================================================
    // 6) Soft Delete / Restore / Hard Delete
    // =========================================================

    @Transactional
    public void softDeletePhoto(Long userId, Long photoId, boolean enforceLocked, boolean adminOverride) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        UserPhoto photo = getPhotoOrThrow(photoId);
        requireOwnership(userId, photo);

        if (enforceLocked) enforceNotLocked(photo, adminOverride);
        if (photo.isDeleted()) return;

        boolean wasPrimary = photo.isPrimaryPhoto();

        photo.setDeleted(true);
        photo.setDeletedAt(LocalDateTime.now());
        photo.setPrimaryPhoto(false);
        photo.setMain(false);

        userPhotoRepository.save(photo);

        if (wasPrimary) ensurePrimaryExistsIfPossible(userId);

        syncPhotosCountIfExists(userId);

        audit(
                SystemActionType.USER_PHOTO_DELETED_SOFT,
                SystemSeverityLevel.INFO,
                true,
                userId,
                photoId,
                false,
                Map.of("wasPrimary", String.valueOf(wasPrimary), "byAdmin", String.valueOf(adminOverride)),
                "Soft deleted photo"
        );

        publish(new UserPhotoChangedEvent(userId, photoId, ChangeType.DELETED_SOFT, Map.of("wasPrimary", String.valueOf(wasPrimary))));
        publishGalleryValidityEvent(userId);
    }

    @Transactional
    public UserPhoto restorePhoto(Long userId, Long photoId, boolean makePrimaryIfMissing) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        PolicySnapshot policy = getPolicy();

        UserPhoto photo = getPhotoOrThrow(photoId);
        requireOwnership(userId, photo);

        if (!photo.isDeleted()) return photo;

        int activeCount = countActive(userId);
        if (activeCount >= policy.maxPhotos) {
            throw new IllegalStateException("Gallery is full (" + policy.maxPhotos + "). Delete a photo to restore.");
        }

        photo.setDeleted(false);
        photo.setDeletedAt(null);

        boolean hasPrimary = getPrimaryPhoto(userId).isPresent();
        if (!hasPrimary && makePrimaryIfMissing) {
            userPhotoRepository.clearPrimaryForUser(userId);
            photo.setPrimaryPhoto(true);
            photo.setMain(true);
        }

        UserPhoto saved = userPhotoRepository.save(photo);

        if (policy.lockPrimaryFirst && saved.isPrimaryPhoto()) {
            forcePrimaryFirst(userId);
        }

        syncPhotosCountIfExists(userId);

        audit(
                SystemActionType.USER_PHOTO_VALIDATION_PASSED,
                SystemSeverityLevel.INFO,
                true,
                userId,
                photoId,
                false,
                Map.of("makePrimaryIfMissing", String.valueOf(makePrimaryIfMissing)),
                "Restored photo"
        );

        publish(new UserPhotoChangedEvent(userId, photoId, ChangeType.RESTORED, Map.of()));
        publishGalleryValidityEvent(userId);

        return saved;
    }

    @Transactional
    public void hardDelete(Long userId, Long photoId, boolean adminOverride) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        UserPhoto photo = getPhotoOrThrow(photoId);
        requireOwnership(userId, photo);

        boolean wasPrimary = photo.isPrimaryPhoto();

        userPhotoRepository.delete(photo);

        if (wasPrimary) ensurePrimaryExistsIfPossible(userId);

        syncPhotosCountIfExists(userId);

        audit(
                SystemActionType.USER_PHOTO_DELETED_HARD,
                SystemSeverityLevel.WARNING,
                true,
                userId,
                photoId,
                false,
                Map.of("wasPrimary", String.valueOf(wasPrimary), "byAdmin", String.valueOf(adminOverride)),
                "Hard deleted photo"
        );

        publish(new UserPhotoChangedEvent(userId, photoId, ChangeType.DELETED_HARD, Map.of("wasPrimary", String.valueOf(wasPrimary))));
        publishGalleryValidityEvent(userId);
    }

    // =========================================================
    // 7) Not-worthy
    // =========================================================

    @Transactional
    public UserPhoto markNotWorthy(Long userId, Long photoId, String reason, boolean adminOverride) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        UserPhoto photo = getPhotoOrThrow(photoId);
        requireOwnership(userId, photo);

        photo.setMetadataJson(setNotWorthy(photo.getMetadataJson(), true, reason));
        UserPhoto saved = userPhotoRepository.save(photo);

        audit(
                SystemActionType.USER_PHOTO_VALIDATION_FAILED,
                SystemSeverityLevel.NOTICE,
                true,
                userId,
                photoId,
                false,
                Map.of("reason", safeStr(reason), "byAdmin", String.valueOf(adminOverride)),
                "Marked photo not-worthy"
        );

        publish(new UserPhotoChangedEvent(userId, photoId, ChangeType.NOT_WORTHY_FLAGGED, Map.of("reason", safeStr(reason))));
        return saved;
    }

    @Transactional
    public UserPhoto clearNotWorthy(Long userId, Long photoId, boolean adminOverride) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(photoId, "photoId is required");

        UserPhoto photo = getPhotoOrThrow(photoId);
        requireOwnership(userId, photo);

        photo.setMetadataJson(setNotWorthy(photo.getMetadataJson(), false, null));
        UserPhoto saved = userPhotoRepository.save(photo);

        audit(
                SystemActionType.USER_PHOTO_VALIDATION_PASSED,
                SystemSeverityLevel.INFO,
                true,
                userId,
                photoId,
                false,
                Map.of("byAdmin", String.valueOf(adminOverride)),
                "Cleared not-worthy mark"
        );

        publish(new UserPhotoChangedEvent(userId, photoId, ChangeType.NOT_WORTHY_CLEARED, Map.of()));
        return saved;
    }

    /**
     * אדמין: הסרה מידית לתוכן אסור.
     * (Hard delete + אירוע לוג + event)
     */
    @Transactional
    public void adminImmediateRemoveForProhibitedContent(Long userId, Long photoId, String reason) {
        hardDelete(userId, photoId, true);
        publish(new UserPhotoChangedEvent(userId, photoId, ChangeType.DELETED_HARD,
                Map.of("prohibited", "true", "reason", safeStr(reason))));
    }

    // =========================================================
    // 8) Lock / Unlock (תשתית קיימת בישות וב-repo)
    // =========================================================

    @Transactional
    public int lockAllActiveAfterWedding(Long userId, String reason) {
        Objects.requireNonNull(userId, "userId is required");
        int updated = userPhotoRepository.lockAllActiveAfterWedding(userId);

        audit(
                SystemActionType.USER_PHOTO_LOCKED_AFTER_WEDDING,
                SystemSeverityLevel.NOTICE,
                true,
                userId,
                null,
                true,
                Map.of("count", String.valueOf(updated), "reason", safeStr(reason)),
                "Locked all active photos after wedding"
        );

        publish(new UserPhotoChangedEvent(userId, null, ChangeType.LOCK_TOGGLED,
                Map.of("locked", "true", "count", String.valueOf(updated))));
        return updated;
    }

    @Transactional
    public int unlockAllByAdmin(Long userId, String reason) {
        Objects.requireNonNull(userId, "userId is required");
        int updated = userPhotoRepository.unlockAllByAdmin(userId);

        audit(
                SystemActionType.USER_PHOTO_UNLOCKED_BY_ADMIN,
                SystemSeverityLevel.WARNING,
                true,
                userId,
                null,
                true,
                Map.of("count", String.valueOf(updated), "reason", safeStr(reason)),
                "Unlocked all photos by admin"
        );

        publish(new UserPhotoChangedEvent(userId, null, ChangeType.LOCK_TOGGLED,
                Map.of("locked", "false", "count", String.valueOf(updated))));
        return updated;
    }

    // =========================================================
    // 9) Cleanup purge
    // =========================================================

    @Transactional
    public int purgeSoftDeletedOlderThanXDays(int maxBatch) {
        int batch = Math.max(1, Math.min(maxBatch, 500));

        EntityManager em = entityManagerProvider.getIfAvailable();
        if (em == null) return 0;

        PolicySnapshot policy = getPolicy();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.hardDeleteAfterDays);

        TypedQuery<UserPhoto> q = em.createQuery(
                "select p from UserPhoto p where p.deleted = true and p.deletedAt is not null and p.deletedAt < :cutoff",
                UserPhoto.class
        );
        q.setParameter("cutoff", cutoff);
        q.setMaxResults(batch);

        List<UserPhoto> victims = q.getResultList();
        if (victims.isEmpty()) return 0;

        int purged = 0;
        for (UserPhoto p : victims) {
            Long uid = (p.getUser() != null ? p.getUser().getId() : null);
            Long pid = p.getId();
            try {
                em.remove(em.contains(p) ? p : em.merge(p));
                purged++;

                if (uid != null) {
                    audit(
                            SystemActionType.USER_PHOTO_DELETED_HARD,
                            SystemSeverityLevel.WARNING,
                            true,
                            uid,
                            pid,
                            true,
                            Map.of("purged", "true", "cutoff", cutoff.toString()),
                            "Purged soft-deleted photo"
                    );
                    publish(new UserPhotoChangedEvent(uid, pid, ChangeType.DELETED_HARD, Map.of("purged", "true")));
                    syncPhotosCountIfExists(uid);
                    publishGalleryValidityEvent(uid);
                }
            } catch (Exception ignored) {}
        }
        return purged;
    }

    // =========================================================
    // 10) FileType/Size queries
    // =========================================================

    @Transactional(readOnly = true)
    public List<UserPhoto> findActiveByFileType(Long userId, String fileType) {
        Objects.requireNonNull(userId, "userId is required");
        PolicySnapshot policy = getPolicy();
        validateFileType(fileType, policy);
        return userPhotoRepository.findByUser_IdAndDeletedFalseAndFileTypeIgnoreCaseOrderByPositionIndexAscIdAsc(
                userId, normalizeFileType(fileType)
        );
    }

    @Transactional(readOnly = true)
    public List<UserPhoto> findActiveByFileSizeGreaterThan(Long userId, long minBytes) {
        Objects.requireNonNull(userId, "userId is required");
        if (minBytes < 0) throw new IllegalArgumentException("minBytes must be >= 0");
        return userPhotoRepository.findByUser_IdAndDeletedFalseAndFileSizeBytesGreaterThanOrderByPositionIndexAscIdAsc(userId, minBytes);
    }

    @Transactional(readOnly = true)
    public List<UserPhoto> findActiveByFileSizeLessThan(Long userId, long maxBytes) {
        Objects.requireNonNull(userId, "userId is required");
        if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must be >= 0");
        return userPhotoRepository.findByUser_IdAndDeletedFalseAndFileSizeBytesLessThanOrderByPositionIndexAscIdAsc(userId, maxBytes);
    }

    // =========================================================
    // Internal helpers
    // =========================================================

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    private void requireOwnership(Long userId, UserPhoto photo) {
        if (photo.getUser() == null || photo.getUser().getId() == null) {
            throw new IllegalStateException("Photo has no user linkage");
        }
        if (!Objects.equals(photo.getUser().getId(), userId)) {
            throw new SecurityException("Photo does not belong to user");
        }
    }

    private int computeNextPositionIndex(Long userId) {
        Integer max = userPhotoRepository.findMaxPositionIndexByUserId(userId);
        return (max == null) ? 0 : (max + 1);
    }

    private int countActive(Long userId) {
        return (int) userPhotoRepository.countByUser_IdAndDeletedFalse(userId);
    }

    private int countNotWorthyActive(Long userId) {
        return (int) listActivePhotosForGallery(userId).stream().filter(this::isNotWorthy).count();
    }

    private void enforceNotLocked(UserPhoto photo, boolean adminOverride) {
        if (adminOverride) return;
        if (photo.isLockedAfterWedding()) {
            throw new IllegalStateException("Photo is locked after wedding and cannot be modified");
        }
    }

    private void validateFileSize(Long fileSizeBytes, PolicySnapshot policy) {
        if (fileSizeBytes == null) return;
        if (fileSizeBytes < 0) throw new IllegalArgumentException("fileSizeBytes must be >= 0");
        if (fileSizeBytes > policy.maxFileSizeBytes) {
            throw new IllegalArgumentException("File too large. max=" + policy.maxFileSizeBytes + " bytes");
        }
    }

    private void validateFileType(String fileType, PolicySnapshot policy) {
        if (fileType == null || fileType.isBlank()) return;
        String ft = normalizeFileType(fileType);
        if (!policy.allowedTypesLower.contains(ft)) {
            throw new IllegalArgumentException("Unsupported fileType: " + ft + " allowed=" + policy.allowedTypesLower);
        }
    }

    private String normalizeFileType(String fileType) {
        String ft = fileType.trim().toLowerCase();
        if (ft.contains("/")) ft = ft.substring(ft.indexOf('/') + 1);
        if (ft.equals("jpg")) return "jpeg";
        return ft;
    }

    private void requireNonBlank(String s, String msg) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(msg);
    }

    private String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // -------------------------
    // notWorthy stored in metadataJson
    // -------------------------

    private boolean isNotWorthy(UserPhoto p) {
        String m = p.getMetadataJson();
        if (m == null) return false;
        return m.contains("\"notWorthy\":true");
    }

    private String ensureNotWorthyFalse(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return "{\"notWorthy\":false}";
        if (metadataJson.contains("\"notWorthy\":true") || metadataJson.contains("\"notWorthy\":false")) return metadataJson;
        return metadataJson + "\n" + "{\"notWorthy\":false}";
    }

    private String setNotWorthy(String metadataJson, boolean value, String reason) {
        String base = (metadataJson == null ? "" : metadataJson);
        String block = "{\"notWorthy\":" + (value ? "true" : "false")
                + (reason != null && !reason.isBlank() ? ",\"reason\":\"" + escapeJson(reason) + "\"" : "")
                + ",\"updatedAt\":\"" + LocalDateTime.now() + "\"}";
        return base.isBlank() ? block : (base + "\n" + block);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // -------------------------
    // photosCount sync - רק אם יש setter ב-User
    // -------------------------

    private void syncPhotosCountIfExists(Long userId) {
        try {
            User u = requireUser(userId);
            int active = countActive(userId);

            Method m1 = findSetter(u.getClass(), "setPhotosCount", Integer.class);
            Method m2 = findSetter(u.getClass(), "setPhotosCount", int.class);

            if (m1 != null) m1.invoke(u, Integer.valueOf(active));
            else if (m2 != null) m2.invoke(u, active);
            else return;

            userRepository.save(u);
        } catch (Exception ignored) {}
        touchProfileUpdated(userId);

    }

    // =====================================================
    // ✅ Touch user profile updated timestamp (MASTER-ONE)
    // =====================================================
    private void touchProfileUpdated(Long userId) {
        if (userId == null) return;

        userRepository.findById(userId).ifPresent(u -> {
            u.setLastProfileUpdateAt(LocalDateTime.now());
            userRepository.save(u);
        });
    }


    private Method findSetter(Class<?> clazz, String name, Class<?> param) {
        try { return clazz.getMethod(name, param); } catch (Exception e) { return null; }
    }

    // -------------------------
    // Gallery validity events
    // -------------------------

    private void publishGalleryValidityEvent(Long userId) {
        GalleryStatus st = getGalleryStatus(userId);
        if (st.blockedNoPhotos) {
            publish(new UserPhotoChangedEvent(userId, null, ChangeType.GALLERY_BECAME_EMPTY, Map.of()));
        } else if (st.hasPrimary) {
            publish(new UserPhotoChangedEvent(userId, null, ChangeType.GALLERY_BECAME_VALID, Map.of()));
        }
    }

    // -------------------------
    // Audit (✅ מסונכרן ל-SystemLog שסיפקת)
    // -------------------------

    private void audit(SystemActionType actionType,
                       SystemSeverityLevel severity,
                       boolean success,
                       Long userId,
                       Long photoId,
                       boolean automated,
                       Map<String, String> context,
                       String details) {

        SystemLogRepository repo = systemLogRepositoryProvider.getIfAvailable();
        if (repo == null) return;

        try {
            SystemLog log = new SystemLog();
            log.setTimestamp(LocalDateTime.now());
            log.setUserId(userId);
            log.setActionType(actionType != null ? actionType : SystemActionType.UNKNOWN_EVENT);
            log.setModule(SystemModule.USER_PHOTO_SERVICE);
            log.setSeverity(severity != null ? severity : SystemSeverityLevel.INFO);
            log.setSuccess(success);

            log.setRelatedEntityType("UserPhoto");
            log.setRelatedEntityId(photoId);

            log.setDetails(details);

            if (context != null && !context.isEmpty()) {
                String ctxJson = context.entrySet().stream()
                        .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(String.valueOf(e.getValue())) + "\"")
                        .collect(Collectors.joining(",", "{", "}"));
                log.setContextJson(ctxJson);
            }

            log.setAutomated(automated);

            repo.save(log);
        } catch (Exception ignored) {}
    }

    // -------------------------
    // Publish
    // -------------------------

    private void publish(UserPhotoChangedEvent event) {
        ApplicationEventPublisher publisher = eventPublisherProvider.getIfAvailable();
        if (publisher == null) return;
        try { publisher.publishEvent(event); } catch (Exception ignored) {}
    }
}