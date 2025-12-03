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
    // 🔵 1. שליפות בסיסיות — לפי דיווחים של משתמש
    // ============================================================

    List<UserReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    // כמה דיווחים המשתמש שלח (למניעת abuse)
    long countByReporterId(Long reporterId);


    // ============================================================
    // 🔵 2. על מי מדווחים — Target User
    // ============================================================

    List<UserReport> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    // כמה דיווחים קיבל המשתמש (לבדיקת משתמש בעייתי)
    long countByTargetId(Long targetId);


    // ============================================================
    // 🔵 3. לפי סטטוס (OPEN / IN_REVIEW / CLOSED / REJECTED)
    // ============================================================

    List<UserReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    long countByStatus(ReportStatus status);

    Optional<UserReport> findTopByTargetIdAndStatusOrderByCreatedAtDesc(
            Long targetId, ReportStatus status
    );


    // ============================================================
    // 🔵 4. לפי סוג דיווח (SPAM / FAKE_PROFILE / INAPPROPRIATE_PHOTO וכו')
    // ============================================================

    List<UserReport> findByTypeOrderByCreatedAtDesc(ReportType type);

    long countByType(ReportType type);


    // ============================================================
    // 🔵 5. שילובים (Target + Type) / (Target + Status)
    // ============================================================

    List<UserReport> findByTargetIdAndTypeOrderByCreatedAtDesc(
            Long targetId, ReportType type
    );

    List<UserReport> findByTargetIdAndStatusOrderByCreatedAtDesc(
            Long targetId, ReportStatus status
    );

    long countByTargetIdAndType(Long targetId, ReportType type);


    // ============================================================
    // 🔵 6. דיווחים שקשורים לתמונות (INAPPROPRIATE_PHOTO)
    // ============================================================

    List<UserReport> findByTypeAndTargetIdOrderByCreatedAtDesc(
            ReportType type,
            Long targetId
    );

    // למערכת התמונות: “כמה דיווחים על תמונה/משתמש לאחרונה”
    long countByTargetIdAndTypeAndCreatedAtAfter(
            Long targetId,
            ReportType type,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 7. דיווחים לפי תאריך / Filters ל־Dashboard
    // ============================================================

    List<UserReport> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end
    );

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    // ============================================================
    // 🔵 8. טיפול אדמין (InReview / Closed / Rejected)
    // ============================================================

    // מי טיפל
    List<UserReport> findByHandledByAdminIdOrderByUpdatedAtDesc(Long adminId);

    // כמה דיווחים אדמין טיפל בהם
    long countByHandledByAdminId(Long adminId);


    // ============================================================
    // 🔵 9. שאילתות מתקדמות לתהליכים אוטומטיים
    // ============================================================

    // כל התיקים הפתוחים → לצורך תור טיפול
    List<UserReport> findByStatusInOrderByCreatedAtAsc(
            List<ReportStatus> statuses
    );

    // כל הדיווחים האחרונים על אותו משתמש/סוג → למודול ה-AI
    List<UserReport> findByTargetIdAndTypeAndCreatedAtBetween(
            Long targetId,
            ReportType type,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 10. תמיכה במנגנון אנטי-ספאם
    // ============================================================

    // כמה דיווחים המשתמש שלח בטווח זמן מסוים
    long countByReporterIdAndCreatedAtAfter(
            Long reporterId,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 11. ניקוי לוגים (Cleaners / CRON)
    // ============================================================

    List<UserReport> findByCreatedAtBefore(LocalDateTime olderThan);

    List<UserReport> findByUpdatedAtBefore(LocalDateTime olderThan);


    // ============================================================
    // 🔵 12. איתור מקרה “חמור” — לבקרה
    // ============================================================

    // דיווחים חמורים (ספאם רב, FAKE_PROFILE, הטרדה)
    List<UserReport> findByTypeInOrderByCreatedAtDesc(List<ReportType> types);

    // כמה CE-level reports קיבל משתמש
    long countByTargetIdAndTypeIn(
            Long targetId,
            List<ReportType> types
    );


    // ============================================================
    // 🔵 13. שליפה חכמה — לצורך ניתוח אגרגציוני חודשי
    // ============================================================

    long countByTypeAndStatus(
            ReportType type,
            ReportStatus status
    );

    long countByTypeAndCreatedAtBetween(
            ReportType type,
            LocalDateTime start,
            LocalDateTime end
    );
}