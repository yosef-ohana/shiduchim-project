package com.example.myproject.repository;

import com.example.myproject.model.SystemLog;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    // ============================================================
    // 🔵 1. שליפות בסיסיות – לפי זמן / משתמש
    // ============================================================

    List<SystemLog> findByUserIdOrderByTimestampDesc(Long userId);

    List<SystemLog> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start,
            LocalDateTime end
    );

    List<SystemLog> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    // ============================================================
    // 🔵 2. לפי Action Type
    // ============================================================

    List<SystemLog> findByActionTypeOrderByTimestampDesc(SystemActionType type);

    List<SystemLog> findByUserIdAndActionTypeOrderByTimestampDesc(
            Long userId,
            SystemActionType type
    );

    long countByActionType(SystemActionType type);

    // ============================================================
    // 🔵 3. לפי Module
    // ============================================================

    List<SystemLog> findByModuleOrderByTimestampDesc(SystemModule module);

    List<SystemLog> findByUserIdAndModuleOrderByTimestampDesc(
            Long userId,
            SystemModule module
    );

    long countByModule(SystemModule module);

    // ============================================================
    // 🔵 4. לפי Severity
    // ============================================================

    List<SystemLog> findBySeverityOrderByTimestampDesc(SystemSeverityLevel severity);

    List<SystemLog> findBySeverityAndTimestampBetweenOrderByTimestampDesc(
            SystemSeverityLevel severity,
            LocalDateTime start,
            LocalDateTime end
    );

    long countBySeverity(SystemSeverityLevel severity);

    // ============================================================
    // 🔵 5. לפי הצלחה / כישלון
    // ============================================================

    List<SystemLog> findBySuccessOrderByTimestampDesc(boolean success);

    List<SystemLog> findByUserIdAndSuccessOrderByTimestampDesc(Long userId, boolean success);

    // ============================================================
    // 🔵 6. ישות קשורה
    // ============================================================

    List<SystemLog> findByRelatedEntityTypeAndRelatedEntityIdOrderByTimestampDesc(
            String relatedEntityType,
            Long relatedEntityId
    );

    List<SystemLog> findByUserIdAndRelatedEntityTypeAndRelatedEntityIdOrderByTimestampDesc(
            Long userId,
            String relatedEntityType,
            Long relatedEntityId
    );

    // ============================================================
    // 🔵 7. חוקים (SystemRules)
    // ============================================================

    List<SystemLog> findBySystemRuleIdOrderByTimestampDesc(Integer ruleId);

    List<SystemLog> findByUserIdAndSystemRuleIdOrderByTimestampDesc(Long userId, Integer ruleId);

    long countBySystemRuleId(Integer ruleId);

    // ============================================================
    // 🔵 8. Debug / Trace – לפי Request Id
    // ============================================================

    List<SystemLog> findByRequestIdOrderByTimestampDesc(String requestId);

    List<SystemLog> findByUserIdAndRequestIdOrderByTimestampDesc(Long userId, String requestId);

    // ============================================================
    // 🔵 9. אבטחה – IP / DeviceInfo
    // ============================================================

    List<SystemLog> findByIpAddressOrderByTimestampDesc(String ipAddress);

    List<SystemLog> findByDeviceInfoContainingIgnoreCaseOrderByTimestampDesc(String text);

    // ============================================================
    // 🔵 10. חיפוש טקסט חופשי
    // ============================================================

    List<SystemLog> findByDetailsContainingIgnoreCase(String text);

    List<SystemLog> findByContextJsonContainingIgnoreCase(String text);

    // ============================================================
    // 🔵 11. Dashboard Counters
    // ============================================================

    long countByTimestampBetween(LocalDateTime start, LocalDateTime end);

    long countByModuleAndTimestampBetween(
            SystemModule module,
            LocalDateTime start,
            LocalDateTime end
    );

    long countBySeverityAndTimestampBetween(
            SystemSeverityLevel severity,
            LocalDateTime start,
            LocalDateTime end
    );

    // ============================================================
    // 🔵 12. ניקוי לוגים (Cleanup)
    // ============================================================

    List<SystemLog> findByTimestampBefore(LocalDateTime time);

    // ✅ חשוב לסרביס שלך (purgeOlderThan) — מחיקה ישירה ב-DB
    long deleteByTimestampBefore(LocalDateTime time);

    // ============================================================
    // 🔵 13. Automated vs Manual
    // ============================================================

    List<SystemLog> findByAutomatedTrueOrderByTimestampDesc();

    List<SystemLog> findByAutomatedFalseOrderByTimestampDesc();

    // ============================================================
    // 🔵 14. פילטרים משולבים
    // ============================================================

    List<SystemLog> findByModuleAndActionTypeAndSeverityOrderByTimestampDesc(
            SystemModule module,
            SystemActionType actionType,
            SystemSeverityLevel severity
    );

    List<SystemLog> findByModuleAndSeverityAndSuccessOrderByTimestampDesc(
            SystemModule module,
            SystemSeverityLevel severity,
            boolean success
    );

    List<SystemLog> findByUserIdAndModuleAndSuccessOrderByTimestampDesc(
            Long userId,
            SystemModule module,
            boolean success
    );

    // ============================================================
    // 🔵 15. Counters לפי משתמש
    // ============================================================

    long countByUserId(Long userId);

    long countByUserIdAndSuccessFalse(Long userId);

    long countByUserIdAndSeverity(Long userId, SystemSeverityLevel severity);

    // ============================================================
    // 🔵 16. Rule + TimeWindow
    // ============================================================

    List<SystemLog> findBySystemRuleIdAndTimestampBetweenOrderByTimestampDesc(
            Integer ruleId,
            LocalDateTime start,
            LocalDateTime end
    );

    long countBySystemRuleIdAndTimestampBetween(
            Integer ruleId,
            LocalDateTime start,
            LocalDateTime end
    );
}