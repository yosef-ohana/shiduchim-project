package com.example.myproject.repository;

import com.example.myproject.model.UserReport;
import com.example.myproject.model.enums.ReportStatus;
import com.example.myproject.model.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות — לפי דיווחים של משתמש (Reporter)
    // ============================================================

    List<UserReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    long countByReporterId(Long reporterId);

    // דירוג אמינות מדווח (Credibility Score)
    long countByReporterIdAndStatus(Long reporterId, ReportStatus status);

    List<UserReport> findByReporterIdAndStatusOrderByCreatedAtDesc(
            Long reporterId,
            ReportStatus status
    );


    // ============================================================
    // 🔵 2. על מי מדווחים — Target User
    // ============================================================

    List<UserReport> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    long countByTargetId(Long targetId);

    // כמה דיווחים קיבל המשתמש בתקופה מסוימת (Escalation Rule)
    long countByTargetIdAndCreatedAtAfter(Long targetId, LocalDateTime since);

    List<UserReport> findByTargetIdAndCreatedAtAfter(Long targetId, LocalDateTime since);


    // ============================================================
    // 🔵 3. לפי סטטוס (OPEN / IN_REVIEW / CLOSED / REJECTED)
    // ============================================================

    List<UserReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    long countByStatus(ReportStatus status);

    Optional<UserReport> findTopByTargetIdAndStatusOrderByCreatedAtDesc(
            Long targetId,
            ReportStatus status
    );


    // ============================================================
    // 🔵 4. לפי סוג דיווח (SPAM / FAKE_PROFILE / HARASSMENT / PHOTO ...)
    // ============================================================

    List<UserReport> findByTypeOrderByCreatedAtDesc(ReportType type);

    long countByType(ReportType type);


    // ============================================================
    // 🔵 5. שילובים (Target + Type) / (Target + Status)
    // ============================================================

    List<UserReport> findByTargetIdAndTypeOrderByCreatedAtDesc(
            Long targetId,
            ReportType type
    );

    List<UserReport> findByTargetIdAndStatusOrderByCreatedAtDesc(
            Long targetId,
            ReportStatus status
    );

    long countByTargetIdAndType(Long targetId, ReportType type);


    // ============================================================
    // 🔵 6. דיווחים הקשורים לתמונות (INAPPROPRIATE_PHOTO)
    // ============================================================

    List<UserReport> findByTypeAndTargetIdOrderByCreatedAtDesc(
            ReportType type,
            Long targetId
    );

    long countByTargetIdAndTypeAndCreatedAtAfter(
            Long targetId,
            ReportType type,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 7. לפי תאריך — Dashboard Filters
    // ============================================================

    List<UserReport> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end
    );

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 8. טיפול אדמין (handledByAdminId)
    // ============================================================

    List<UserReport> findByHandledByAdminIdOrderByUpdatedAtDesc(Long adminId);

    long countByHandledByAdminId(Long adminId);


    // ============================================================
    // 🔵 9. אוטומציה / AI / Escalation
    // ============================================================

    // תור טיפול — כל התיקים הפתוחים (לפי רשימת סטטוסים)
    List<UserReport> findByStatusInOrderByCreatedAtAsc(List<ReportStatus> statuses);

    // לוגיקה של AI (קיבוץ אירועים)
    List<UserReport> findByTargetIdAndTypeAndCreatedAtBetween(
            Long targetId,
            ReportType type,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 10. אנטי-ספאם — Reporter Abuse Prevention
    // ============================================================

    long countByReporterIdAndCreatedAtAfter(
            Long reporterId,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 11. ניקוי דיווחים ישנים (CRON)
    // ============================================================

    List<UserReport> findByCreatedAtBefore(LocalDateTime olderThan);

    List<UserReport> findByUpdatedAtBefore(LocalDateTime olderThan);


    // ============================================================
    // 🔵 12. דיווחים חמורים — CE Level Alerts
    // ============================================================

    List<UserReport> findByTypeInOrderByCreatedAtDesc(List<ReportType> types);

    long countByTargetIdAndTypeIn(Long targetId, List<ReportType> types);


    // ============================================================
    // 🔵 13. שליפות אגרגטיביות — Monthly Analytics
    // ============================================================

    long countByTypeAndStatus(ReportType type, ReportStatus status);

    long countByTypeAndCreatedAtBetween(
            ReportType type,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 14. פילטרים משולבים מתקדמים — Dashboard / AI / SystemRules
    // ============================================================

    // דיווחים לפי סטטוסים מרובים + טווח זמן (תור לפי עדיפות)
    List<UserReport> findByStatusInAndCreatedAtBetweenOrderByCreatedAtAsc(
            List<ReportStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );

    // כל הדיווחים על Target לפי סטטוסים מרובים (היסטוריה מלאה)
    List<UserReport> findByTargetIdAndStatusInOrderByCreatedAtDesc(
            Long targetId,
            List<ReportStatus> statuses
    );

    // ספירת תיקים פתוחים/בטיפול (לפי רשימת סטטוסים)
    long countByStatusIn(List<ReportStatus> statuses);
}