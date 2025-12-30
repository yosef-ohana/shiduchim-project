package com.example.myproject.service;

import com.example.myproject.model.SystemLog;
import com.example.myproject.model.UserReport;
import com.example.myproject.model.enums.ReportStatus;
import com.example.myproject.model.enums.ReportType;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.repository.SystemLogRepository;
import com.example.myproject.repository.UserReportRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ✅ UserReportService — MASTER 2025 (FINAL + OPTIMAL + COMPAT)
 *
 * מיושר לישות UserReport (baseline):
 * id, reporterId, targetId, type, description, attachmentUrl,
 * status (default OPEN), createdAt, updatedAt, handledByAdminId, adminNotes
 *
 * מיושר ל-UserReportRepository capabilities (1–14) + תוספת אופטימיזציה:
 * - Bulk delete (deleteByCreatedAtBefore / deleteByUpdatedAtBefore)
 *
 * כולל:
 * - Create + validations + anti-spam rate-limit
 * - Wrappers מלאים לכל מתודות הריפו
 * - Admin handling + notes + handledByAdminId
 * - Queue helpers + escalation helpers + analytics + cleanup helpers
 * - Audit אופציונלי ל-SystemLog (קומפילציה-סייף ע"י Reflection)
 */
@Service
@Transactional
public class UserReportService {

    private final UserReportRepository userReportRepository;

    // Optional audit (לא שובר קומפילציה גם אם לא מחובר בפועל)
    private final ObjectProvider<SystemLogRepository> systemLogRepositoryProvider;

    // -------------------------
    // Defaults (אפשר להעביר בעתיד ל-SystemSettings)
    // -------------------------
    private static final int DEFAULT_MAX_REPORTS_PER_HOUR = 6;   // Anti-abuse
    private static final int DEFAULT_MAX_REPORTS_PER_DAY  = 30;  // Anti-abuse

    // Safety defaults for cleanup
    private static final int DEFAULT_PURGE_BATCH_LIMIT = 500;

    public UserReportService(UserReportRepository userReportRepository,
                             ObjectProvider<SystemLogRepository> systemLogRepositoryProvider) {
        this.userReportRepository = userReportRepository;
        this.systemLogRepositoryProvider = systemLogRepositoryProvider;
    }

    // ============================================================
    // Guards / Loaders
    // ============================================================

    private UserReport requireReport(Long reportId) {
        if (reportId == null) throw new IllegalArgumentException("reportId is required");
        return userReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("UserReport not found: " + reportId));
    }

    private static void requireNonNull(Object v, String msg) {
        if (v == null) throw new IllegalArgumentException(msg);
    }

    private static void requireNonBlank(String v, String msg) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(msg);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static LocalDateTime[] normalizeRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime s = (start == null) ? LocalDateTime.of(1970, 1, 1, 0, 0) : start;
        LocalDateTime e = (end == null) ? now().plusYears(100) : end;
        if (e.isBefore(s)) {
            LocalDateTime tmp = s;
            s = e;
            e = tmp;
        }
        return new LocalDateTime[]{s, e};
    }

    private static List<ReportStatus> normalizeStatuses(List<ReportStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return List.of();
        return statuses.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<ReportType> normalizeTypes(List<ReportType> types) {
        if (types == null || types.isEmpty()) return List.of();
        return types.stream().filter(Objects::nonNull).distinct().toList();
    }

    // ============================================================
    // 1) Create / Submit (User → System)
    // ============================================================

    /**
     * יצירת דיווח חדש (OPEN) + Anti-spam + validations.
     */
    public UserReport createReport(Long reporterId,
                                   Long targetId,
                                   ReportType type,
                                   String description,
                                   String attachmentUrl) {

        requireNonNull(reporterId, "reporterId is required");
        requireNonNull(targetId, "targetId is required");
        requireNonNull(type, "type is required");
        requireNonBlank(description, "description is required");

        if (Objects.equals(reporterId, targetId)) {
            throw new IllegalArgumentException("Reporter cannot report himself");
        }

        // Anti-spam (Reporter Abuse Prevention) — repo capability #10
        enforceReporterRateLimit(reporterId, DEFAULT_MAX_REPORTS_PER_HOUR, DEFAULT_MAX_REPORTS_PER_DAY);

        UserReport r = new UserReport();
        r.setReporterId(reporterId);
        r.setTargetId(targetId);
        r.setType(type);

        r.setDescription(description.trim());
        r.setAttachmentUrl((attachmentUrl == null || attachmentUrl.isBlank()) ? null : attachmentUrl.trim());

        r.setStatus(ReportStatus.OPEN);

        // hooks קיימים? לא מזיק.
        if (r.getCreatedAt() == null) r.setCreatedAt(now());
        if (r.getUpdatedAt() == null) r.setUpdatedAt(now());

        UserReport saved = userReportRepository.save(r);

        audit(
                "USER_REPORT_CREATED",
                "INFO",
                true,
                reporterId,
                saved.getId(),
                Map.of(
                        "targetId", String.valueOf(targetId),
                        "type", String.valueOf(type),
                        "status", String.valueOf(saved.getStatus())
                ),
                "User report submitted"
        );

        return saved;
    }

    /**
     * Anti-spam: מקסימום X דיווחים לשעה + Y ליום.
     * משתמש ב-repo capability #10.
     */
    public void enforceReporterRateLimit(Long reporterId, int maxPerHour, int maxPerDay) {
        requireNonNull(reporterId, "reporterId is required");
        int perHour = Math.max(1, maxPerHour);
        int perDay = Math.max(perHour, maxPerDay);

        LocalDateTime t = now();

        long lastHour = userReportRepository.countByReporterIdAndCreatedAtAfter(reporterId, t.minusHours(1));
        if (lastHour >= perHour) {
            throw new IllegalStateException("REPORT_RATE_LIMIT_EXCEEDED_HOURLY");
        }

        long lastDay = userReportRepository.countByReporterIdAndCreatedAtAfter(reporterId, t.minusDays(1));
        if (lastDay >= perDay) {
            throw new IllegalStateException("REPORT_RATE_LIMIT_EXCEEDED_DAILY");
        }
    }

    // ============================================================
    // 2) Read by Id
    // ============================================================

    @Transactional(readOnly = true)
    public UserReport getByIdOrThrow(Long reportId) {
        return requireReport(reportId);
    }

    // ============================================================
    // 3) Reporter queries (Repo #1)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByReporter(Long reporterId) {
        requireNonNull(reporterId, "reporterId is required");
        return userReportRepository.findByReporterIdOrderByCreatedAtDesc(reporterId);
    }

    @Transactional(readOnly = true)
    public long countReportsByReporter(Long reporterId) {
        requireNonNull(reporterId, "reporterId is required");
        return userReportRepository.countByReporterId(reporterId);
    }

    @Transactional(readOnly = true)
    public long countReportsByReporterAndStatus(Long reporterId, ReportStatus status) {
        requireNonNull(reporterId, "reporterId is required");
        requireNonNull(status, "status is required");
        return userReportRepository.countByReporterIdAndStatus(reporterId, status);
    }

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByReporterAndStatus(Long reporterId, ReportStatus status) {
        requireNonNull(reporterId, "reporterId is required");
        requireNonNull(status, "status is required");
        return userReportRepository.findByReporterIdAndStatusOrderByCreatedAtDesc(reporterId, status);
    }

    // ============================================================
    // 4) Target queries (Repo #2)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByTarget(Long targetId) {
        requireNonNull(targetId, "targetId is required");
        return userReportRepository.findByTargetIdOrderByCreatedAtDesc(targetId);
    }

    @Transactional(readOnly = true)
    public long countReportsByTarget(Long targetId) {
        requireNonNull(targetId, "targetId is required");
        return userReportRepository.countByTargetId(targetId);
    }

    @Transactional(readOnly = true)
    public long countReportsForTargetSince(Long targetId, LocalDateTime since) {
        requireNonNull(targetId, "targetId is required");
        if (since == null) since = now().minusDays(7);
        return userReportRepository.countByTargetIdAndCreatedAtAfter(targetId, since);
    }

    @Transactional(readOnly = true)
    public List<UserReport> getReportsForTargetSince(Long targetId, LocalDateTime since) {
        requireNonNull(targetId, "targetId is required");
        if (since == null) since = now().minusDays(7);
        return userReportRepository.findByTargetIdAndCreatedAtAfter(targetId, since);
    }

    // ============================================================
    // 5) Status queries (Repo #3)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByStatus(ReportStatus status) {
        requireNonNull(status, "status is required");
        return userReportRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public long countByStatus(ReportStatus status) {
        requireNonNull(status, "status is required");
        return userReportRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public Optional<UserReport> getLastForTargetByStatus(Long targetId, ReportStatus status) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(status, "status is required");
        return userReportRepository.findTopByTargetIdAndStatusOrderByCreatedAtDesc(targetId, status);
    }

    // ============================================================
    // 6) Type queries (Repo #4)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByType(ReportType type) {
        requireNonNull(type, "type is required");
        return userReportRepository.findByTypeOrderByCreatedAtDesc(type);
    }

    @Transactional(readOnly = true)
    public long countByType(ReportType type) {
        requireNonNull(type, "type is required");
        return userReportRepository.countByType(type);
    }

    // ============================================================
    // 7) Combined Target+Type / Target+Status (Repo #5)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByTargetAndType(Long targetId, ReportType type) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(type, "type is required");
        return userReportRepository.findByTargetIdAndTypeOrderByCreatedAtDesc(targetId, type);
    }

    @Transactional(readOnly = true)
    public List<UserReport> getReportsByTargetAndStatus(Long targetId, ReportStatus status) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(status, "status is required");
        return userReportRepository.findByTargetIdAndStatusOrderByCreatedAtDesc(targetId, status);
    }

    @Transactional(readOnly = true)
    public long countByTargetAndType(Long targetId, ReportType type) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(type, "type is required");
        return userReportRepository.countByTargetIdAndType(targetId, type);
    }

    // ============================================================
    // 8) Photo-related reports (Repo #6)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getPhotoReportsForTarget(Long targetId, ReportType photoType) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(photoType, "photoType is required");
        return userReportRepository.findByTypeAndTargetIdOrderByCreatedAtDesc(photoType, targetId);
    }

    @Transactional(readOnly = true)
    public long countPhotoReportsForTargetSince(Long targetId, ReportType photoType, LocalDateTime since) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(photoType, "photoType is required");
        if (since == null) since = now().minusDays(30);
        return userReportRepository.countByTargetIdAndTypeAndCreatedAtAfter(targetId, photoType, since);
    }

    // ============================================================
    // 9) Date range filters (Repo #7)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getReportsCreatedBetween(LocalDateTime start, LocalDateTime end) {
        LocalDateTime[] range = normalizeRange(start, end);
        return userReportRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(range[0], range[1]);
    }

    @Transactional(readOnly = true)
    public long countReportsCreatedBetween(LocalDateTime start, LocalDateTime end) {
        LocalDateTime[] range = normalizeRange(start, end);
        return userReportRepository.countByCreatedAtBetween(range[0], range[1]);
    }

    // ============================================================
    // 10) Admin handling (Repo #8) + status transitions
    // ============================================================

    /**
     * עדכון אדמיני כולל סטטוס + handledByAdminId + adminNotes
     */
    public UserReport adminUpdate(Long reportId,
                                  Long adminId,
                                  ReportStatus newStatus,
                                  String adminNotes) {

        requireNonNull(reportId, "reportId is required");
        requireNonNull(adminId, "adminId is required");
        requireNonNull(newStatus, "newStatus is required");

        UserReport r = requireReport(reportId);

        r.setHandledByAdminId(adminId);
        r.setStatus(newStatus);
        r.setAdminNotes((adminNotes == null || adminNotes.isBlank()) ? null : adminNotes.trim());
        r.setUpdatedAt(now());

        UserReport saved = userReportRepository.save(r);

        audit(
                "USER_REPORT_ADMIN_UPDATED",
                "INFO",
                true,
                adminId,
                saved.getId(),
                Map.of(
                        "newStatus", String.valueOf(newStatus),
                        "targetId", String.valueOf(saved.getTargetId()),
                        "type", String.valueOf(saved.getType())
                ),
                "Admin updated user report"
        );

        return saved;
    }

    // ✅ שינוי 1 מתוך 4: IN_REVIEW -> IN_PROGRESS
    public UserReport markInReview(Long reportId, Long adminId, String notes) {
        return adminUpdate(reportId, adminId, ReportStatus.IN_PROGRESS, notes);
    }

    public UserReport closeReport(Long reportId, Long adminId, String notes) {
        return adminUpdate(reportId, adminId, ReportStatus.CLOSED, notes);
    }

    // ✅ שינוי 2 מתוך 4: REJECTED -> CLOSED
    public UserReport rejectReport(Long reportId, Long adminId, String notes) {
        return adminUpdate(reportId, adminId, ReportStatus.CLOSED, notes);
    }

    @Transactional(readOnly = true)
    public List<UserReport> getReportsHandledByAdmin(Long adminId) {
        requireNonNull(adminId, "adminId is required");
        return userReportRepository.findByHandledByAdminIdOrderByUpdatedAtDesc(adminId);
    }

    @Transactional(readOnly = true)
    public long countReportsHandledByAdmin(Long adminId) {
        requireNonNull(adminId, "adminId is required");
        return userReportRepository.countByHandledByAdminId(adminId);
    }

    // ============================================================
    // 11) Automation / AI / Escalation (Repo #9)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getQueueByStatuses(List<ReportStatus> statuses) {
        List<ReportStatus> st = normalizeStatuses(statuses);
        if (st.isEmpty()) return List.of();
        return userReportRepository.findByStatusInOrderByCreatedAtAsc(st);
    }

    @Transactional(readOnly = true)
    public List<UserReport> getTargetTypeWindow(Long targetId, ReportType type, LocalDateTime start, LocalDateTime end) {
        requireNonNull(targetId, "targetId is required");
        requireNonNull(type, "type is required");
        LocalDateTime[] range = normalizeRange(start, end);
        return userReportRepository.findByTargetIdAndTypeAndCreatedAtBetween(targetId, type, range[0], range[1]);
    }

    @Transactional(readOnly = true)
    public boolean shouldEscalateTarget(Long targetId, LocalDateTime since, long threshold) {
        requireNonNull(targetId, "targetId is required");
        if (threshold <= 0) threshold = 3;
        long cnt = countReportsForTargetSince(targetId, since);
        return cnt >= threshold;
    }

    // ============================================================
    // 12) Anti-spam helper (Repo #10) — wrapper
    // ============================================================

    @Transactional(readOnly = true)
    public long countReportsByReporterSince(Long reporterId, LocalDateTime since) {
        requireNonNull(reporterId, "reporterId is required");
        if (since == null) since = now().minusHours(1);
        return userReportRepository.countByReporterIdAndCreatedAtAfter(reporterId, since);
    }

    // ============================================================
    // 13) Cleanup / Cron helpers (Repo #11)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> findCreatedBefore(LocalDateTime olderThan) {
        if (olderThan == null) olderThan = now().minusDays(180);
        return userReportRepository.findByCreatedAtBefore(olderThan);
    }

    @Transactional(readOnly = true)
    public List<UserReport> findUpdatedBefore(LocalDateTime olderThan) {
        if (olderThan == null) olderThan = now().minusDays(180);
        return userReportRepository.findByUpdatedAtBefore(olderThan);
    }

    public int purgeOldReportsByCreatedAt(LocalDateTime olderThan, int maxBatch) {
        if (olderThan == null) olderThan = now().minusDays(365);
        int batch = Math.max(1, Math.min(maxBatch, DEFAULT_PURGE_BATCH_LIMIT));

        List<UserReport> old = userReportRepository.findByCreatedAtBefore(olderThan);
        if (old.isEmpty()) return 0;

        if (old.size() > batch) old = old.subList(0, batch);
        userReportRepository.deleteAll(old);

        audit(
                "USER_REPORT_PURGE_CREATED_AT_BATCH",
                "WARNING",
                true,
                null,
                null,
                Map.of("count", String.valueOf(old.size()), "olderThan", String.valueOf(olderThan)),
                "Purged old reports by createdAt (batch)"
        );

        return old.size();
    }

    public long purgeOldReportsByCreatedAtBulk(LocalDateTime olderThan) {
        if (olderThan == null) olderThan = now().minusDays(365);
        long deleted = userReportRepository.deleteByCreatedAtBefore(olderThan);

        audit(
                "USER_REPORT_PURGE_CREATED_AT_BULK",
                "WARNING",
                true,
                null,
                null,
                Map.of("count", String.valueOf(deleted), "olderThan", String.valueOf(olderThan)),
                "Purged old reports by createdAt (bulk)"
        );

        return deleted;
    }

    public int purgeOldReportsByUpdatedAt(LocalDateTime olderThan, int maxBatch) {
        if (olderThan == null) olderThan = now().minusDays(365);
        int batch = Math.max(1, Math.min(maxBatch, DEFAULT_PURGE_BATCH_LIMIT));

        List<UserReport> old = userReportRepository.findByUpdatedAtBefore(olderThan);
        if (old.isEmpty()) return 0;

        if (old.size() > batch) old = old.subList(0, batch);
        userReportRepository.deleteAll(old);

        audit(
                "USER_REPORT_PURGE_UPDATED_AT_BATCH",
                "WARNING",
                true,
                null,
                null,
                Map.of("count", String.valueOf(old.size()), "olderThan", String.valueOf(olderThan)),
                "Purged old reports by updatedAt (batch)"
        );

        return old.size();
    }

    public long purgeOldReportsByUpdatedAtBulk(LocalDateTime olderThan) {
        if (olderThan == null) olderThan = now().minusDays(365);
        long deleted = userReportRepository.deleteByUpdatedAtBefore(olderThan);

        audit(
                "USER_REPORT_PURGE_UPDATED_AT_BULK",
                "WARNING",
                true,
                null,
                null,
                Map.of("count", String.valueOf(deleted), "olderThan", String.valueOf(olderThan)),
                "Purged old reports by updatedAt (bulk)"
        );

        return deleted;
    }

    // ============================================================
    // 14) Severe reports (Repo #12)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getSevereReports(List<ReportType> types) {
        List<ReportType> tt = normalizeTypes(types);
        if (tt.isEmpty()) return List.of();
        return userReportRepository.findByTypeInOrderByCreatedAtDesc(tt);
    }

    @Transactional(readOnly = true)
    public long countSevereForTarget(Long targetId, List<ReportType> types) {
        requireNonNull(targetId, "targetId is required");
        List<ReportType> tt = normalizeTypes(types);
        if (tt.isEmpty()) return 0;
        return userReportRepository.countByTargetIdAndTypeIn(targetId, tt);
    }

    // ============================================================
    // 15) Analytics (Repo #13)
    // ============================================================

    @Transactional(readOnly = true)
    public long countByTypeAndStatus(ReportType type, ReportStatus status) {
        requireNonNull(type, "type is required");
        requireNonNull(status, "status is required");
        return userReportRepository.countByTypeAndStatus(type, status);
    }

    @Transactional(readOnly = true)
    public long countByTypeBetween(ReportType type, LocalDateTime start, LocalDateTime end) {
        requireNonNull(type, "type is required");
        LocalDateTime[] range = normalizeRange(start, end);
        return userReportRepository.countByTypeAndCreatedAtBetween(type, range[0], range[1]);
    }

    // ============================================================
    // 16) Advanced filters (Repo #14)
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserReport> getQueueByStatusesAndWindow(List<ReportStatus> statuses, LocalDateTime start, LocalDateTime end) {
        List<ReportStatus> st = normalizeStatuses(statuses);
        if (st.isEmpty()) return List.of();
        LocalDateTime[] range = normalizeRange(start, end);
        return userReportRepository.findByStatusInAndCreatedAtBetweenOrderByCreatedAtAsc(st, range[0], range[1]);
    }

    @Transactional(readOnly = true)
    public List<UserReport> getTargetHistoryByStatuses(Long targetId, List<ReportStatus> statuses) {
        requireNonNull(targetId, "targetId is required");
        List<ReportStatus> st = normalizeStatuses(statuses);
        if (st.isEmpty()) return List.of();
        return userReportRepository.findByTargetIdAndStatusInOrderByCreatedAtDesc(targetId, st);
    }

    @Transactional(readOnly = true)
    public long countByStatuses(List<ReportStatus> statuses) {
        List<ReportStatus> st = normalizeStatuses(statuses);
        if (st.isEmpty()) return 0;
        return userReportRepository.countByStatusIn(st);
    }

    // ============================================================
    // Credibility helper (מבוסס repo #1)
    // ============================================================

    public static class ReporterCredibility {
        public final Long reporterId;
        public final long total;
        public final long open;
        public final long inReview;
        public final long closed;
        public final long rejected;

        public ReporterCredibility(Long reporterId, long total, long open, long inReview, long closed, long rejected) {
            this.reporterId = reporterId;
            this.total = total;
            this.open = open;
            this.inReview = inReview;
            this.closed = closed;
            this.rejected = rejected;
        }
    }

    @Transactional(readOnly = true)
    public ReporterCredibility getReporterCredibility(Long reporterId) {
        requireNonNull(reporterId, "reporterId is required");

        long total = userReportRepository.countByReporterId(reporterId);
        long open = userReportRepository.countByReporterIdAndStatus(reporterId, ReportStatus.OPEN);

        // ✅ שינוי 3 מתוך 4: IN_REVIEW -> IN_PROGRESS
        long inReview = userReportRepository.countByReporterIdAndStatus(reporterId, ReportStatus.IN_PROGRESS);

        long closed = userReportRepository.countByReporterIdAndStatus(reporterId, ReportStatus.CLOSED);

        // ✅ שינוי 4 מתוך 4: REJECTED -> CLOSED
        long rejected = userReportRepository.countByReporterIdAndStatus(reporterId, ReportStatus.CLOSED);

        return new ReporterCredibility(reporterId, total, open, inReview, closed, rejected);
    }

    // ============================================================
    // Optional Audit (Runtime-safe enum mapping + Reflection-safe setters)
    // ============================================================

    private void audit(String actionName,
                       String severityName,
                       boolean success,
                       Long actorUserId,
                       Long relatedReportId,
                       Map<String, String> context,
                       String details) {

        SystemLogRepository repo = systemLogRepositoryProvider.getIfAvailable();
        if (repo == null) return;

        try {
            SystemLog log = new SystemLog();

            // Timestamp / UserId
            invokeFirstMatch(log, new String[]{"setTimestamp", "setCreatedAt"}, LocalDateTime.class, now());
            invokeFirstMatch(log, new String[]{"setUserId", "setActorUserId"}, Long.class, actorUserId);

            SystemActionType action = resolveAction(actionName);
            SystemModule module = resolveModule("USER_SERVICE");
            SystemSeverityLevel severity = resolveSeverity(severityName);

            invokeFirstMatch(log, new String[]{"setActionType"}, SystemActionType.class, action);
            invokeFirstMatch(log, new String[]{"setModule"}, SystemModule.class, module);
            invokeFirstMatch(log, new String[]{"setSeverity"}, SystemSeverityLevel.class, severity);

            // Success, details
            invokeFirstMatch(log, new String[]{"setSuccess"}, boolean.class, success);
            invokeFirstMatch(log, new String[]{"setDetails", "setMessage", "setText"}, String.class, details);

            // Related entity
            invokeFirstMatch(log, new String[]{"setRelatedEntityType"}, String.class, "UserReport");
            invokeFirstMatch(log, new String[]{"setRelatedEntityId"}, Long.class, relatedReportId);

            // Context JSON
            if (context != null && !context.isEmpty()) {
                String json = toJson(context);
                invokeFirstMatch(log, new String[]{"setContextJson", "setContext"}, String.class, json);
            }

            // Automated flag
            invokeFirstMatch(log, new String[]{"setAutomated"}, boolean.class, false);

            repo.save(log);
        } catch (Exception ignored) {
        }
    }

    private SystemActionType resolveAction(String preferredName) {
        try {
            if (preferredName != null && !preferredName.isBlank()) {
                return SystemActionType.valueOf(preferredName.trim());
            }
        } catch (Exception ignored) {}

        try { return SystemActionType.valueOf("USER_ACTION_RECORDED"); } catch (Exception ignored) {}
        try { return SystemActionType.valueOf("UNKNOWN_EVENT"); } catch (Exception ignored) {}
        return null;
    }

    private SystemModule resolveModule(String fallbackName) {
        try { return SystemModule.valueOf("USER_SERVICE"); } catch (Exception ignored) {}
        try {
            if (fallbackName != null && !fallbackName.isBlank()) {
                return SystemModule.valueOf(fallbackName.trim());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private SystemSeverityLevel resolveSeverity(String name) {
        if (name == null || name.isBlank()) name = "INFO";
        try { return SystemSeverityLevel.valueOf(name.trim()); } catch (Exception ignored) {}
        try { return SystemSeverityLevel.valueOf("INFO"); } catch (Exception ignored) {}
        return null;
    }

    // ============================================================
    // ✅ שינוי #2: invokeFirstMatch עם candidateTypes (כמו שביקשת)
    // ============================================================

    private static void invokeFirstMatch(Object target, String[] methodNames, Object value, Class<?>... candidateTypes) {
        for (String m : methodNames) {
            for (Class<?> t : candidateTypes) {
                try {
                    Method method = target.getClass().getMethod(m, t);
                    method.invoke(target, value);
                    return;
                } catch (Exception ignored) {}
            }
        }
    }

    // ✅ השארתי את ה-signature הישן כ-Overload כדי לא לשנות אף קריאה קיימת
    private static void invokeFirstMatch(Object target, String[] methodNames, Class<?> paramType, Object value) {
        invokeFirstMatch(target, methodNames, value, paramType);
    }

    private String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            sb.append("\"").append(escapeJson(String.valueOf(e.getValue()))).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}