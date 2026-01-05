package com.example.myproject.model;

import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * SystemLog – גרסת 2025
 * לוג מערכת מורחב, תומך בכל 19 מודולי ה-System,
 * בכל 41 חוקי המערכת, ובכל שירותי הפרויקט.
 */
@Entity
@Table(
        name = "system_logs",
        indexes = {
                @Index(name = "idx_log_timestamp", columnList = "timestamp"),
                @Index(name = "idx_log_user_id", columnList = "user_id"),
                @Index(name = "idx_log_action_type", columnList = "action_type"), // ✅ FIX
                @Index(name = "idx_log_severity", columnList = "severity"),
                @Index(name = "idx_log_module", columnList = "module"),
                @Index(name = "idx_log_related_entity", columnList = "related_entity_type, related_entity_id") // ✅ FIX
        }
)
public class SystemLog {

    // ==========================================================
    // 🔵 מזהה
    // ==========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================================================
    // 🔵 זמן
    // ==========================================================

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // ==========================================================
    // 🔵 משתמש שקשור לפעולה (אם יש)
    // ==========================================================

    @Column(name = "user_id")
    private Long userId;   // nullable – אירוע מערכת לא חייב משתמש

    // ==========================================================
    // 🔵 סוג פעולה
    // ==========================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SystemActionType actionType;

    // ==========================================================
    // 🔵 מודול מקור
    // ==========================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SystemModule module;

    // ==========================================================
    // 🔵 רמת חומרה
    // ==========================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemSeverityLevel severity;

    // ==========================================================
    // 🔵 הצלחה / כישלון
    // ==========================================================

    @Column(nullable = false)
    private boolean success;

    // ==========================================================
    // 🔵 ישות קשורה (User / Match / Wedding וכו)
    // ==========================================================

    @Column(length = 40)
    private String relatedEntityType;

    private Long relatedEntityId;

    // ==========================================================
    // 🔵 מערכת חוקים (אם הופעל חוק מסוים מתוך 41)
    // ==========================================================

    private Integer systemRuleId; // nullable – רק אם רץ חוק

    // ==========================================================
    // 🔵 Request Trace ID (ל־Distributed Logs)
    // ==========================================================

    @Column(length = 100)
    private String requestId;

    // ==========================================================
    // 🔵 פרטים טכניים (IP / מכשיר)
    // ==========================================================

    @Column(length = 100)
    private String ipAddress;

    @Column(length = 300)
    private String deviceInfo;

    // ==========================================================
    // 🔵 תיאור מלא (מה קרה בפועל)
    // ==========================================================

    @Column(columnDefinition = "TEXT")
    private String details;

    // ==========================================================
    // 🔵 קונטקסט נוסף (JSON מלא)
    // ==========================================================

    @Column(columnDefinition = "TEXT")
    private String contextJson; // {"field1":"value", "field2":"value"}

    // ==========================================================
    // 🔵 האם המערכת יצרה את האירוע
    // ==========================================================

    @Column(nullable = false)
    private boolean automated = false;

    // ==========================================================
    // 🔵 Constructors
    // ==========================================================

    public SystemLog() {
    }

    public SystemLog(SystemActionType actionType,
                     SystemModule module,
                     SystemSeverityLevel severity,
                     boolean success,
                     Long userId,
                     String details) {

        this.timestamp = LocalDateTime.now();
        this.actionType = actionType;
        this.module = module;
        this.severity = severity;
        this.success = success;
        this.userId = userId;
        this.details = details;
    }

    // ==========================================================
    // 🔵 Getters / Setters
    // ==========================================================

    public Long getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public SystemActionType getActionType() {
        return actionType;
    }

    public void setActionType(SystemActionType actionType) {
        this.actionType = actionType;
    }

    public SystemModule getModule() {
        return module;
    }

    public void setModule(SystemModule module) {
        this.module = module;
    }

    public SystemSeverityLevel getSeverity() {
        return severity;
    }

    public void setSeverity(SystemSeverityLevel severity) {
        this.severity = severity;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public void setRelatedEntityType(String relatedEntityType) {
        this.relatedEntityType = relatedEntityType;
    }

    public Long getRelatedEntityId() {
        return relatedEntityId;
    }

    public void setRelatedEntityId(Long relatedEntityId) {
        this.relatedEntityId = relatedEntityId;
    }

    public Integer getSystemRuleId() {
        return systemRuleId;
    }

    public void setSystemRuleId(Integer systemRuleId) {
        this.systemRuleId = systemRuleId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String contextJson) {
        this.contextJson = contextJson;
    }

    public boolean isAutomated() {
        return automated;
    }

    public void setAutomated(boolean automated) {
        this.automated = automated;
    }
}