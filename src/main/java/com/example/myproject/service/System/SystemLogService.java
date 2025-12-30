package com.example.myproject.service.System;

import com.example.myproject.model.SystemLog;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.repository.SystemLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    public SystemLogService(SystemLogRepository systemLogRepository) {
        this.systemLogRepository = systemLogRepository;
    }

    // ============================================================
    // ✅ 1) כתיבת לוגים (Core)
    // ============================================================

    public SystemLog log(SystemActionType actionType,
                         SystemModule module,
                         SystemSeverityLevel severity,
                         boolean success,
                         Long userId,
                         String details) {

        return logFull(actionType, module, severity, success, userId,
                null, null,
                null,
                null, null, null,
                details, null,
                false
        );
    }

    public SystemLog logSystem(SystemActionType actionType,
                               SystemModule module,
                               SystemSeverityLevel severity,
                               boolean success,
                               String details) {

        return logFull(actionType, module, severity, success, null,
                null, null,
                null,
                null, null, null,
                details, null,
                true
        );
    }

    public SystemLog logRule(SystemActionType actionType,
                             SystemModule module,
                             SystemSeverityLevel severity,
                             boolean success,
                             Long userId,
                             Integer systemRuleId,
                             String details) {

        return logFull(actionType, module, severity, success, userId,
                null, null,
                systemRuleId,
                null, null, null,
                details, null,
                false
        );
    }

    public SystemLog logForEntity(SystemActionType actionType,
                                  SystemModule module,
                                  SystemSeverityLevel severity,
                                  boolean success,
                                  Long userId,
                                  String relatedEntityType,
                                  Long relatedEntityId,
                                  String details) {

        return logFull(actionType, module, severity, success, userId,
                relatedEntityType, relatedEntityId,
                null,
                null, null, null,
                details, null,
                false
        );
    }

    /**
     * Trace / Security / Debug log with requestId + ip + deviceInfo + contextJson.
     */
    public SystemLog logTrace(SystemActionType actionType,
                              SystemModule module,
                              SystemSeverityLevel severity,
                              boolean success,
                              Long userId,
                              String requestId,
                              String ipAddress,
                              String deviceInfo,
                              String details,
                              String contextJson,
                              boolean automated) {

        return logFull(actionType, module, severity, success, userId,
                null, null,
                null,
                requestId, ipAddress, deviceInfo,
                details, contextJson,
                automated
        );
    }

    /**
     * Full builder (לא מוסיף שדות – רק ממלא את הקיימים ב-Entity).
     */
    public SystemLog logFull(SystemActionType actionType,
                             SystemModule module,
                             SystemSeverityLevel severity,
                             boolean success,
                             Long userId,
                             String relatedEntityType,
                             Long relatedEntityId,
                             Integer systemRuleId,
                             String requestId,
                             String ipAddress,
                             String deviceInfo,
                             String details,
                             String contextJson,
                             boolean automated) {

        validateRequired(actionType, module, severity);

        SystemLog log = new SystemLog();
        log.setTimestamp(LocalDateTime.now());
        log.setActionType(actionType);
        log.setModule(module);
        log.setSeverity(severity);
        log.setSuccess(success);
        log.setUserId(userId);

        log.setRelatedEntityType(trimToNull(relatedEntityType));
        log.setRelatedEntityId(relatedEntityId);

        log.setSystemRuleId(systemRuleId);

        log.setRequestId(trimToNull(requestId));
        log.setIpAddress(trimToNull(ipAddress));
        log.setDeviceInfo(trimToNull(deviceInfo));

        log.setDetails(trimToNull(details));
        log.setContextJson(trimToNull(contextJson));

        log.setAutomated(automated);

        return systemLogRepository.save(log);
    }

    // ============================================================
    // ✅ 2) Wrappers ישירים לכל היכולות של הריפו (1–16)
    // ============================================================

    // 1) Time / User
    @Transactional(readOnly = true)
    public List<SystemLog> getByUser(Long userId) {
        return systemLogRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getBetween(LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.findByTimestampBetweenOrderByTimestampDesc(r.start, r.end);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserBetween(Long userId, LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, r.start, r.end);
    }

    // 2) ActionType
    @Transactional(readOnly = true)
    public List<SystemLog> getByAction(SystemActionType type) {
        return systemLogRepository.findByActionTypeOrderByTimestampDesc(type);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserAndAction(Long userId, SystemActionType type) {
        return systemLogRepository.findByUserIdAndActionTypeOrderByTimestampDesc(userId, type);
    }

    @Transactional(readOnly = true)
    public long countByAction(SystemActionType type) {
        return systemLogRepository.countByActionType(type);
    }

    // 3) Module
    @Transactional(readOnly = true)
    public List<SystemLog> getByModule(SystemModule module) {
        return systemLogRepository.findByModuleOrderByTimestampDesc(module);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserAndModule(Long userId, SystemModule module) {
        return systemLogRepository.findByUserIdAndModuleOrderByTimestampDesc(userId, module);
    }

    @Transactional(readOnly = true)
    public long countByModule(SystemModule module) {
        return systemLogRepository.countByModule(module);
    }

    // 4) Severity
    @Transactional(readOnly = true)
    public List<SystemLog> getBySeverity(SystemSeverityLevel severity) {
        return systemLogRepository.findBySeverityOrderByTimestampDesc(severity);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getBySeverityBetween(SystemSeverityLevel severity, LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.findBySeverityAndTimestampBetweenOrderByTimestampDesc(severity, r.start, r.end);
    }

    @Transactional(readOnly = true)
    public long countBySeverity(SystemSeverityLevel severity) {
        return systemLogRepository.countBySeverity(severity);
    }

    // 5) Success
    @Transactional(readOnly = true)
    public List<SystemLog> getBySuccess(boolean success) {
        return systemLogRepository.findBySuccessOrderByTimestampDesc(success);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserAndSuccess(Long userId, boolean success) {
        return systemLogRepository.findByUserIdAndSuccessOrderByTimestampDesc(userId, success);
    }

    // 6) Related Entity
    @Transactional(readOnly = true)
    public List<SystemLog> getByRelatedEntity(String type, Long id) {
        return systemLogRepository.findByRelatedEntityTypeAndRelatedEntityIdOrderByTimestampDesc(type, id);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserAndRelatedEntity(Long userId, String type, Long id) {
        return systemLogRepository.findByUserIdAndRelatedEntityTypeAndRelatedEntityIdOrderByTimestampDesc(userId, type, id);
    }

    // 7) System Rules
    @Transactional(readOnly = true)
    public List<SystemLog> getByRule(Integer ruleId) {
        return systemLogRepository.findBySystemRuleIdOrderByTimestampDesc(ruleId);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserAndRule(Long userId, Integer ruleId) {
        return systemLogRepository.findByUserIdAndSystemRuleIdOrderByTimestampDesc(userId, ruleId);
    }

    @Transactional(readOnly = true)
    public long countByRule(Integer ruleId) {
        return systemLogRepository.countBySystemRuleId(ruleId);
    }

    // 8) RequestId
    @Transactional(readOnly = true)
    public List<SystemLog> getByRequest(String requestId) {
        return systemLogRepository.findByRequestIdOrderByTimestampDesc(requestId);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserAndRequest(Long userId, String requestId) {
        return systemLogRepository.findByUserIdAndRequestIdOrderByTimestampDesc(userId, requestId);
    }

    // 9) IP / Device
    @Transactional(readOnly = true)
    public List<SystemLog> getByIp(String ipAddress) {
        return systemLogRepository.findByIpAddressOrderByTimestampDesc(ipAddress);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> searchByDeviceInfo(String text) {
        return systemLogRepository.findByDeviceInfoContainingIgnoreCaseOrderByTimestampDesc(text);
    }

    // 10) Free text
    @Transactional(readOnly = true)
    public List<SystemLog> searchInDetails(String text) {
        return safeList(systemLogRepository.findByDetailsContainingIgnoreCase(text));
    }

    @Transactional(readOnly = true)
    public List<SystemLog> searchInContextJson(String text) {
        return safeList(systemLogRepository.findByContextJsonContainingIgnoreCase(text));
    }

    // 11) Dashboard counters
    @Transactional(readOnly = true)
    public long countBetween(LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.countByTimestampBetween(r.start, r.end);
    }

    @Transactional(readOnly = true)
    public long countModuleBetween(SystemModule module, LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.countByModuleAndTimestampBetween(module, r.start, r.end);
    }

    @Transactional(readOnly = true)
    public long countSeverityBetween(SystemSeverityLevel severity, LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.countBySeverityAndTimestampBetween(severity, r.start, r.end);
    }

    // 12) Cleanup
    @Transactional(readOnly = true)
    public List<SystemLog> findOlderThan(LocalDateTime time) {
        return systemLogRepository.findByTimestampBefore(time);
    }

    /**
     * ✅ אופטימיזציה מומלצת:
     * להוסיף לריפו: long deleteByTimestampBefore(LocalDateTime time);
     * ואז זה מוחק ישירות ב-DB בלי למשוך רשומות לזיכרון.
     */
    public long purgeOlderThan(LocalDateTime time) {
        return systemLogRepository.deleteByTimestampBefore(time);
    }

    // 13) Automated vs Manual
    @Transactional(readOnly = true)
    public List<SystemLog> getAutomated() {
        return systemLogRepository.findByAutomatedTrueOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getManual() {
        return systemLogRepository.findByAutomatedFalseOrderByTimestampDesc();
    }

    // 14) Advanced combos
    @Transactional(readOnly = true)
    public List<SystemLog> getByModuleActionSeverity(SystemModule module, SystemActionType actionType, SystemSeverityLevel severity) {
        return systemLogRepository.findByModuleAndActionTypeAndSeverityOrderByTimestampDesc(module, actionType, severity);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByModuleSeveritySuccess(SystemModule module, SystemSeverityLevel severity, boolean success) {
        return systemLogRepository.findByModuleAndSeverityAndSuccessOrderByTimestampDesc(module, severity, success);
    }

    @Transactional(readOnly = true)
    public List<SystemLog> getByUserModuleSuccess(Long userId, SystemModule module, boolean success) {
        return systemLogRepository.findByUserIdAndModuleAndSuccessOrderByTimestampDesc(userId, module, success);
    }

    // 15) User counters
    @Transactional(readOnly = true)
    public long countByUser(Long userId) {
        return systemLogRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countFailedByUser(Long userId) {
        return systemLogRepository.countByUserIdAndSuccessFalse(userId);
    }

    @Transactional(readOnly = true)
    public long countByUserAndSeverity(Long userId, SystemSeverityLevel severity) {
        return systemLogRepository.countByUserIdAndSeverity(userId, severity);
    }

    // 16) Rule + time window
    @Transactional(readOnly = true)
    public List<SystemLog> getByRuleBetween(Integer ruleId, LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.findBySystemRuleIdAndTimestampBetweenOrderByTimestampDesc(ruleId, r.start, r.end);
    }

    @Transactional(readOnly = true)
    public long countByRuleBetween(Integer ruleId, LocalDateTime start, LocalDateTime end) {
        TimeRange r = normalizeRange(start, end);
        return systemLogRepository.countBySystemRuleIdAndTimestampBetween(ruleId, r.start, r.end);
    }

    // ============================================================
    // 🔧 Helpers
    // ============================================================

    private void validateRequired(SystemActionType actionType, SystemModule module, SystemSeverityLevel severity) {
        if (actionType == null) throw new IllegalArgumentException("SystemLog.actionType is required");
        if (module == null) throw new IllegalArgumentException("SystemLog.module is required");
        if (severity == null) throw new IllegalArgumentException("SystemLog.severity is required");
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private TimeRange normalizeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) throw new IllegalArgumentException("start/end are required");
        if (end.isBefore(start)) return new TimeRange(end, start);
        return new TimeRange(start, end);
    }

    private static class TimeRange {
        final LocalDateTime start;
        final LocalDateTime end;
        TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}