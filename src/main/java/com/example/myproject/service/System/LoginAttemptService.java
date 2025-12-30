package com.example.myproject.service.System;

import com.example.myproject.model.LoginAttempt;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.repository.LoginAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LoginAttemptService {

    // -----------------------
    // Defaults (אם אין SystemSettings)
    // -----------------------
    private static final int DEF_WINDOW_MINUTES = 10;
    private static final int DEF_MAX_FAILS_BEFORE_BLOCK = 3;
    private static final int DEF_BLOCK_MINUTES = 5;

    private static final int DEF_FAILS_BEFORE_OTP = 2;

    // OTP brute-force defaults (best-effort)
    private static final int DEF_OTP_WINDOW_MINUTES = 10;
    private static final int DEF_OTP_MAX_FAILS_BEFORE_BLOCK = 3;
    private static final int DEF_OTP_BLOCK_MINUTES = 15;

    // IP brute-force defaults
    private static final int DEF_IP_WINDOW_MINUTES = 5;
    private static final int DEF_IP_MAX_FAILS_BEFORE_BLOCK = 12;
    private static final int DEF_IP_BLOCK_MINUTES = 10;

    // Device anomaly defaults
    private static final int DEF_MAX_DISTINCT_DEVICES_PER_ID_IN_WINDOW = 4;
    private static final int DEF_MAX_DISTINCT_IDS_PER_DEVICE_IN_WINDOW = 6;

    // Takeover suspicion defaults
    private static final int DEF_MAX_DISTINCT_IPS_PER_ID_IN_WINDOW = 3;

    private static final int DEF_RETENTION_DAYS = 30;

    // -----------------------
    // Keys ב-SystemSettings
    // -----------------------
    private static final String KEY_WINDOW_MINUTES = "security.login.windowMinutes";
    private static final String KEY_MAX_FAILS = "security.login.maxFailsBeforeBlock";
    private static final String KEY_BLOCK_MINUTES = "security.login.blockMinutes";
    private static final String KEY_OTP_FAILS = "security.login.failsBeforeOtp";
    private static final String KEY_RETENTION_DAYS = "security.login.retentionDays";

    private static final String KEY_OTP_WINDOW_MINUTES = "security.login.otp.windowMinutes";
    private static final String KEY_OTP_MAX_FAILS = "security.login.otp.maxFailsBeforeBlock";
    private static final String KEY_OTP_BLOCK_MINUTES = "security.login.otp.blockMinutes";

    private static final String KEY_IP_WINDOW_MINUTES = "security.login.ip.windowMinutes";
    private static final String KEY_IP_MAX_FAILS = "security.login.ip.maxFailsBeforeBlock";
    private static final String KEY_IP_BLOCK_MINUTES = "security.login.ip.blockMinutes";

    private static final String KEY_MAX_DEVICES_PER_ID = "security.login.device.maxDistinctDevicesPerIdInWindow";
    private static final String KEY_MAX_IDS_PER_DEVICE = "security.login.device.maxDistinctIdsPerDeviceInWindow";

    private static final String KEY_MAX_IPS_PER_ID = "security.login.ip.maxDistinctIpsPerIdInWindow";

    private final LoginAttemptRepository repo;
    private final SystemSettingsService systemSettings; // יכול להיות null בשלבים מוקדמים
    private final SystemLogService systemLog;           // יכול להיות null בשלבים מוקדמים

    public LoginAttemptService(LoginAttemptRepository repo,
                               SystemSettingsService systemSettings,
                               SystemLogService systemLog) {
        this.repo = repo;
        this.systemSettings = systemSettings;
        this.systemLog = systemLog;
    }

    // ============================================================
    // ✅ Gate (לפני אימות) — IDENTIFIER / IP / OTP / Device anomalies
    // ============================================================

    @Transactional(readOnly = true)
    public GateStatus evaluateGate(String emailOrPhone,
                                   String ipAddress,
                                   String deviceId,
                                   String userAgent) {

        String id = normalizeId(emailOrPhone);
        LocalDateTime now = LocalDateTime.now();

        String ip = trimToNull(ipAddress);
        String dev = trimToNull(deviceId);
        String ua = trimToNull(userAgent);

        int windowMinutes = getInt(KEY_WINDOW_MINUTES, DEF_WINDOW_MINUTES);
        LocalDateTime since = now.minusMinutes(windowMinutes);

        // 1) חסימה קיימת לפי ניסיון אחרון של IDENTIFIER
        Optional<LoginAttempt> last = repo.findLatestAttempt(id);
        if (last.isPresent()
                && last.get().isTemporaryBlocked()
                && last.get().getBlockedUntil() != null
                && last.get().getBlockedUntil().isAfter(now)) {

            long failures = repo.countFailuresSince(id, since);
            return GateStatus.blocked(last.get().getBlockedUntil(), GateBlockReason.IDENTIFIER, true, (int) failures);
        }

        // 2) חסימת IP (Brute force)
        if (ip != null) {
            int ipWindow = getInt(KEY_IP_WINDOW_MINUTES, DEF_IP_WINDOW_MINUTES);
            int ipMaxFails = getInt(KEY_IP_MAX_FAILS, DEF_IP_MAX_FAILS_BEFORE_BLOCK);
            int ipBlockMinutes = getInt(KEY_IP_BLOCK_MINUTES, DEF_IP_BLOCK_MINUTES);

            LocalDateTime sinceIp = now.minusMinutes(ipWindow);
            long ipFailures = repo.countFailuresByIpSince(ip, sinceIp);

            if (ipFailures >= ipMaxFails) {
                LocalDateTime until = now.plusMinutes(ipBlockMinutes);
                return GateStatus.blocked(until, GateBlockReason.IP, true, (int) ipFailures);
            }
        }

        // 3) requiresOtp לפי כישלונות IDENTIFIER בחלון
        int otpFails = getInt(KEY_OTP_FAILS, DEF_FAILS_BEFORE_OTP);

        long failures = repo.countFailuresSince(id, since);
        boolean requiresOtp = failures >= otpFails;

        // 4) requiresOtp לפי device חדש / userAgent חדש
        boolean newDevice = (dev != null) && !repo.existsKnownDevice(id, dev);
        boolean newUserAgent = (ua != null) && !repo.existsKnownUserAgent(id, ua);
        if (newDevice || newUserAgent) requiresOtp = true;

        // 5) שינוי IP פתאומי למשתמש (best-effort)
        if (ip != null) {
            Optional<LoginAttempt> lastSuccess = repo.findLatestSuccessAttempt(id);
            if (lastSuccess.isPresent()) {
                String lastSuccessIp = trimToNull(lastSuccess.get().getIpAddress());
                if (lastSuccessIp != null && !lastSuccessIp.equals(ip)) {
                    // IP חדש יחסית להתנהגות מוצלחת אחרונה => דורש OTP
                    requiresOtp = true;
                }
            }
        }

        // 6) Device+IP anomaly (best-effort): אותו device קפץ IP מהר
        if (dev != null && ip != null) {
            Optional<LoginAttempt> lastByDevice = repo.findLatestAttemptByDevice(dev);
            if (lastByDevice.isPresent()) {
                String prevIp = trimToNull(lastByDevice.get().getIpAddress());
                if (prevIp != null && !prevIp.equals(ip)) {
                    requiresOtp = true;
                }
            }
        }

        // 7) התנהגות חשודה: יותר מדי מכשירים למזהה / יותר מדי מזהים למכשיר
        int maxDevicesPerId = getInt(KEY_MAX_DEVICES_PER_ID, DEF_MAX_DISTINCT_DEVICES_PER_ID_IN_WINDOW);
        int maxIdsPerDevice = getInt(KEY_MAX_IDS_PER_DEVICE, DEF_MAX_DISTINCT_IDS_PER_DEVICE_IN_WINDOW);

        long distinctDevices = repo.countDistinctDevicesByIdentifierSince(id, since);
        if (distinctDevices >= maxDevicesPerId) requiresOtp = true;

        if (dev != null) {
            long distinctIds = repo.countDistinctIdentifiersByDeviceSince(dev, since);
            if (distinctIds >= maxIdsPerDevice) requiresOtp = true;
        }

        // 8) takeover suspicion: יותר מדי IP שונים למזהה בחלון
        int maxIpsPerId = getInt(KEY_MAX_IPS_PER_ID, DEF_MAX_DISTINCT_IPS_PER_ID_IN_WINDOW);
        if (maxIpsPerId > 0) {
            long distinctIps = repo.countDistinctIpsByIdentifierSince(id, since);
            if (distinctIps >= maxIpsPerId) requiresOtp = true;
        }

        return GateStatus.open(requiresOtp, (int) failures);
    }

    // ============================================================
    // ✅ recordAttempt (Login pass/fail AFTER auth step)
    // ============================================================

    public AttemptDecision recordAttempt(String emailOrPhone,
                                         boolean success,
                                         Long userId,
                                         String ipAddress,
                                         String deviceId,
                                         String userAgent) {

        String id = normalizeId(emailOrPhone);
        LocalDateTime now = LocalDateTime.now();

        int windowMinutes = getInt(KEY_WINDOW_MINUTES, DEF_WINDOW_MINUTES);
        int maxFails = getInt(KEY_MAX_FAILS, DEF_MAX_FAILS_BEFORE_BLOCK);
        int blockMinutes = getInt(KEY_BLOCK_MINUTES, DEF_BLOCK_MINUTES);
        int otpFails = getInt(KEY_OTP_FAILS, DEF_FAILS_BEFORE_OTP);
        int retentionDays = getInt(KEY_RETENTION_DAYS, DEF_RETENTION_DAYS);

        LocalDateTime since = now.minusMinutes(windowMinutes);

        long failuresBefore = repo.countFailuresSince(id, since);
        long failuresAfter = success ? failuresBefore : (failuresBefore + 1);

        String ip = trimToNull(ipAddress);
        String dev = trimToNull(deviceId);
        String ua = trimToNull(userAgent);

        boolean newDevice = (dev != null) && !repo.existsKnownDevice(id, dev);
        boolean newUserAgent = (ua != null) && !repo.existsKnownUserAgent(id, ua);

        // ✅ אם login הצליח, מניחים ש-OTP כבר עבר אם היה צריך
        boolean requiresOtp = !success && ((failuresAfter >= otpFails) || newDevice || newUserAgent);

        boolean blockNow = !success && failuresAfter >= maxFails;
        LocalDateTime blockedUntil = blockNow ? now.plusMinutes(blockMinutes) : null;

        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmailOrPhone(id);
        attempt.setAttemptTime(now);
        attempt.setSuccess(success);
        attempt.setIpAddress(ip);
        attempt.setDeviceId(dev);
        attempt.setUserAgent(ua);
        attempt.setRequiresOtp(requiresOtp);

        if (blockNow) {
            attempt.setTemporaryBlocked(true);
            attempt.setBlockedUntil(blockedUntil);
        }

        attempt.setExpiresAt(now.plusDays(retentionDays));
        repo.save(attempt);

        RiskAssessment risk = computeRisk(id, ip, dev, ua, since, (int) failuresAfter, success);
        logAttemptEvent(userId, success, id, ip, dev, ua, requiresOtp, blockedUntil, risk);

        if (blockNow) {
            return AttemptDecision.blocked(blockedUntil, requiresOtp, (int) failuresAfter, risk);
        }
        return AttemptDecision.open(requiresOtp, (int) failuresAfter, risk);
    }

    // ============================================================
    // ✅ OTP attempt tracking (best-effort, בלי שינוי Entity)
    // ============================================================

    public OtpDecision recordOtpAttempt(String emailOrPhone,
                                        boolean success,
                                        Long userId,
                                        String ipAddress,
                                        String deviceId,
                                        String userAgent) {

        String id = normalizeId(emailOrPhone);
        LocalDateTime now = LocalDateTime.now();

        int otpWindow = getInt(KEY_OTP_WINDOW_MINUTES, DEF_OTP_WINDOW_MINUTES);
        int otpMaxFails = getInt(KEY_OTP_MAX_FAILS, DEF_OTP_MAX_FAILS_BEFORE_BLOCK);
        int otpBlockMinutes = getInt(KEY_OTP_BLOCK_MINUTES, DEF_OTP_BLOCK_MINUTES);
        int retentionDays = getInt(KEY_RETENTION_DAYS, DEF_RETENTION_DAYS);

        LocalDateTime since = now.minusMinutes(otpWindow);

        long otpFailsBefore = repo.countOtpFailuresSince(id, since);
        long otpFailsAfter = success ? otpFailsBefore : (otpFailsBefore + 1);

        boolean blockNow = !success && otpFailsAfter >= otpMaxFails;
        LocalDateTime blockedUntil = blockNow ? now.plusMinutes(otpBlockMinutes) : null;

        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmailOrPhone(id);
        attempt.setAttemptTime(now);
        attempt.setSuccess(success);
        attempt.setIpAddress(trimToNull(ipAddress));
        attempt.setDeviceId(trimToNull(deviceId));
        attempt.setUserAgent(trimToNull(userAgent));
        attempt.setRequiresOtp(true);

        if (blockNow) {
            attempt.setTemporaryBlocked(true);
            attempt.setBlockedUntil(blockedUntil);
        }

        attempt.setExpiresAt(now.plusDays(retentionDays));
        repo.save(attempt);

        RiskAssessment risk = computeRisk(
                id,
                attempt.getIpAddress(),
                attempt.getDeviceId(),
                attempt.getUserAgent(),
                since,
                (int) otpFailsAfter,
                success
        );

        // לוג אבטחה “OTP attempt”
        logOtpEvent(userId, success, id, attempt.getIpAddress(), attempt.getDeviceId(), attempt.getUserAgent(), blockedUntil, risk);

        if (blockNow) return OtpDecision.blocked(blockedUntil, (int) otpFailsAfter, risk);
        return OtpDecision.open((int) otpFailsAfter, risk);
    }

    // ============================================================
    // ✅ Capability #1: Gate audit (no DB attempt write)
    // ============================================================

    public void logGateDecision(String emailOrPhone,
                                String ip,
                                String deviceId,
                                String userAgent,
                                boolean blocked,
                                LocalDateTime blockedUntil,
                                boolean otpRequired,
                                String reason) {

        if (systemLog == null) return;

        String id = normalizeId(emailOrPhone);

        SystemActionType actionType = safeEnum(SystemActionType.class, "LOGIN_GATE", null);
        SystemModule module = safeEnum(SystemModule.class, "SECURITY", null);

        SystemSeverityLevel severity = blocked
                ? safeEnum(SystemSeverityLevel.class, "WARNING", SystemSeverityLevel.WARNING)
                : safeEnum(SystemSeverityLevel.class, "INFO", SystemSeverityLevel.INFO);

        String details = "loginGate blocked=" + blocked +
                ", otpRequired=" + otpRequired +
                (blockedUntil != null ? ", blockedUntil=" + blockedUntil : "") +
                (reason != null ? ", reason=" + reason : "");

        String contextJson = "{"
                + "\"identifier\":\"" + escapeJson(id) + "\","
                + "\"ip\":\"" + escapeJson(trimToNull(ip)) + "\","
                + "\"deviceId\":\"" + escapeJson(trimToNull(deviceId)) + "\","
                + "\"userAgent\":\"" + escapeJson(trimToNull(userAgent)) + "\","
                + "\"blocked\":" + blocked + ","
                + "\"otpRequired\":" + otpRequired + ","
                + "\"blockedUntil\":\"" + (blockedUntil != null ? blockedUntil : "") + "\","
                + "\"reason\":\"" + escapeJson(reason) + "\""
                + "}";

        systemLog.logTrace(
                actionType,
                module,
                severity,
                !blocked,
                null,
                null,
                trimToNull(ip),
                trimToNull(deviceId),
                details,
                contextJson,
                true
        );
    }

    // ============================================================
    // ✅ Admin / Dashboard
    // ============================================================

    @Transactional(readOnly = true)
    public AttemptsSummary getSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime f = Objects.requireNonNull(from);
        LocalDateTime t = Objects.requireNonNull(to);
        long success = repo.countByAttemptTimeBetweenAndSuccess(f, t, true);
        long fail = repo.countByAttemptTimeBetweenAndSuccess(f, t, false);
        return new AttemptsSummary(success, fail);
    }

    @Transactional(readOnly = true)
    public List<TopAttacker> topIps(LocalDateTime from, LocalDateTime to, int limit) {
        List<Object[]> rows = repo.topIpsBetween(from, to);
        return mapTop(rows, limit);
    }

    @Transactional(readOnly = true)
    public List<TopAttacker> topDevices(LocalDateTime from, LocalDateTime to, int limit) {
        List<Object[]> rows = repo.topDevicesBetween(from, to);
        return mapTop(rows, limit);
    }

    @Transactional(readOnly = true)
    public List<LoginAttempt> getAttemptsByIp(String ip, LocalDateTime from, LocalDateTime to, int limit) {
        String cleanIp = trimToNull(ip);
        if (cleanIp == null) return List.of();
        int lim = Math.max(1, limit);
        return repo.findByIpBetween(cleanIp, from, to)
                .stream()
                .limit(lim)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LoginAttempt> getAttemptsByIdentifier(String emailOrPhone, LocalDateTime from, LocalDateTime to, int limit) {
        String id = normalizeId(emailOrPhone);
        int lim = Math.max(1, limit);
        return repo.findByIdentifierBetween(id, from, to)
                .stream()
                .limit(lim)
                .collect(Collectors.toList());
    }

    // ✅ Capability #20: failures of a specific IP in a time range (for admin dashboard)
    @Transactional(readOnly = true)
    public long countIpFailuresBetween(String ip, LocalDateTime from, LocalDateTime to) {
        String cleanIp = trimToNull(ip);
        if (cleanIp == null) return 0;
        if (from == null || to == null) return 0;
        return repo.countFailuresByIpBetween(cleanIp, from, to);
    }

    // ============================================================
    // ✅ Cleanup
    // ============================================================

    public long purgeExpired() {
        return repo.deleteExpired(LocalDateTime.now());
    }

    public long purgeOlderThan(LocalDateTime cutoff) {
        return repo.deleteByAttemptTimeBefore(cutoff);
    }

    // ============================================================
    // ✅ Risk Engine (computed-only)
    // ============================================================

    private RiskAssessment computeRisk(String identifier,
                                       String ip,
                                       String deviceId,
                                       String userAgent,
                                       LocalDateTime windowSince,
                                       int failuresAfter,
                                       boolean success) {

        int score = 0;

        score += Math.min(40, failuresAfter * 10);

        if (ip != null) {
            long ipFails = repo.countFailuresByIpSince(ip, windowSince);
            score += Math.min(25, (int) ipFails * 2);

            // “חדש” יחסית להתנהגות מוצלחת אחרונה
            Optional<LoginAttempt> lastSuccess = repo.findLatestSuccessAttempt(identifier);
            if (lastSuccess.isPresent()) {
                String lastSuccessIp = trimToNull(lastSuccess.get().getIpAddress());
                if (lastSuccessIp != null && !lastSuccessIp.equals(ip)) {
                    score += 20;
                }
            }
        }

        if (deviceId != null) {
            long distinctIds = repo.countDistinctIdentifiersByDeviceSince(deviceId, windowSince);
            score += Math.min(20, (int) distinctIds * 3);

            boolean newDevice = !repo.existsKnownDevice(identifier, deviceId);
            if (newDevice) score += 10;
        }

        if (userAgent != null) {
            boolean newUA = !repo.existsKnownUserAgent(identifier, userAgent);
            if (newUA) score += 10;
        }

        if (success) score = Math.max(0, score - 10);

        RiskLevel level;
        if (score >= 70) level = RiskLevel.HIGH;
        else if (score >= 35) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;

        boolean requiresHumanReview = (level == RiskLevel.HIGH);

        return new RiskAssessment(score, level, requiresHumanReview);
    }

    // ============================================================
    // Settings helper
    // ============================================================

    private int getInt(String key, int def) {
        try {
            if (systemSettings == null) return def;
            String env = systemSettings.resolveEnv();
            return systemSettings.getEffectiveInt(env, SystemSettingsService.Scope.SYSTEM, null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    // ============================================================
    // Logging helpers
    // ============================================================

    private void logAttemptEvent(Long userId,
                                 boolean success,
                                 String identifier,
                                 String ip,
                                 String deviceId,
                                 String userAgent,
                                 boolean requiresOtp,
                                 LocalDateTime blockedUntil,
                                 RiskAssessment risk) {
        if (systemLog == null) return;

        SystemActionType actionType = safeEnum(SystemActionType.class, "LOGIN_ATTEMPT", null);
        SystemModule module = safeEnum(SystemModule.class, "SECURITY", null);

        SystemSeverityLevel severity;
        if (risk != null && risk.level == RiskLevel.HIGH) {
            severity = safeEnum(SystemSeverityLevel.class, "SECURITY", SystemSeverityLevel.WARNING);
        } else {
            severity = success
                    ? safeEnum(SystemSeverityLevel.class, "INFO", SystemSeverityLevel.INFO)
                    : safeEnum(SystemSeverityLevel.class, "WARNING", SystemSeverityLevel.WARNING);
        }

        String details = "loginAttempt success=" + success +
                ", id=" + identifier +
                ", otp=" + requiresOtp +
                (blockedUntil != null ? ", blockedUntil=" + blockedUntil : "") +
                (risk != null ? ", risk=" + risk.level + "(" + risk.score + ")" : "");

        String contextJson = "{"
                + "\"identifier\":\"" + escapeJson(identifier) + "\","
                + "\"ip\":\"" + escapeJson(ip) + "\","
                + "\"deviceId\":\"" + escapeJson(deviceId) + "\","
                + "\"userAgent\":\"" + escapeJson(userAgent) + "\","
                + "\"otp\":" + requiresOtp + ","
                + "\"blockedUntil\":\"" + (blockedUntil != null ? blockedUntil : "") + "\","
                + "\"riskScore\":" + (risk != null ? risk.score : 0) + ","
                + "\"riskLevel\":\"" + (risk != null ? risk.level : RiskLevel.LOW) + "\","
                + "\"requiresHumanReview\":" + (risk != null && risk.requiresHumanReview)
                + "}";

        systemLog.logTrace(
                actionType,
                module,
                severity,
                success,
                userId,
                null,
                ip,
                deviceId, // אצלך SystemLogService קורא לזה deviceInfo
                details,
                contextJson,
                true
        );
    }

    private void logOtpEvent(Long userId,
                             boolean success,
                             String identifier,
                             String ip,
                             String deviceId,
                             String userAgent,
                             LocalDateTime blockedUntil,
                             RiskAssessment risk) {
        if (systemLog == null) return;

        SystemActionType actionType = safeEnum(SystemActionType.class, "LOGIN_OTP_ATTEMPT", null);
        SystemModule module = safeEnum(SystemModule.class, "SECURITY", null);

        SystemSeverityLevel severity = success
                ? safeEnum(SystemSeverityLevel.class, "INFO", SystemSeverityLevel.INFO)
                : safeEnum(SystemSeverityLevel.class, "WARNING", SystemSeverityLevel.WARNING);

        String details = "otpAttempt success=" + success +
                ", id=" + identifier +
                (blockedUntil != null ? ", blockedUntil=" + blockedUntil : "") +
                (risk != null ? ", risk=" + risk.level + "(" + risk.score + ")" : "");

        String contextJson = "{"
                + "\"identifier\":\"" + escapeJson(identifier) + "\","
                + "\"ip\":\"" + escapeJson(ip) + "\","
                + "\"deviceId\":\"" + escapeJson(deviceId) + "\","
                + "\"userAgent\":\"" + escapeJson(userAgent) + "\","
                + "\"blockedUntil\":\"" + (blockedUntil != null ? blockedUntil : "") + "\","
                + "\"riskScore\":" + (risk != null ? risk.score : 0) + ","
                + "\"riskLevel\":\"" + (risk != null ? risk.level : RiskLevel.LOW) + "\""
                + "}";

        systemLog.logTrace(
                actionType,
                module,
                severity,
                success,
                userId,
                null,
                ip,
                deviceId,
                details,
                contextJson,
                true
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private <E extends Enum<E>> E safeEnum(Class<E> cls, String preferred, E fallback) {
        try {
            if (preferred != null) return Enum.valueOf(cls, preferred);
        } catch (Exception ignore) {}
        if (fallback != null) return fallback;
        E[] all = cls.getEnumConstants();
        return (all != null && all.length > 0) ? all[0] : null;
    }

    // ============================================================
    // Normalization
    // ============================================================

    private String normalizeId(String emailOrPhone) {
        if (emailOrPhone == null) throw new IllegalArgumentException("emailOrPhone is required");
        String id = emailOrPhone.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("emailOrPhone is blank");
        return id.toLowerCase();
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private List<TopAttacker> mapTop(List<Object[]> rows, int limit) {
        if (rows == null) return List.of();
        int lim = Math.max(1, limit);
        List<TopAttacker> out = new ArrayList<>();
        for (Object[] r : rows) {
            if (r == null || r.length < 2) continue;
            String key = (r[0] != null) ? String.valueOf(r[0]) : null;
            long count = (r[1] instanceof Number) ? ((Number) r[1]).longValue() : 0L;
            if (key != null) out.add(new TopAttacker(key, count));
            if (out.size() >= lim) break;
        }
        return out;
    }

    // ============================================================
    // DTOs / Enums
    // ============================================================

    public enum GateBlockReason { IDENTIFIER, IP }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public static class RiskAssessment {
        public final int score;
        public final RiskLevel level;
        public final boolean requiresHumanReview;

        public RiskAssessment(int score, RiskLevel level, boolean requiresHumanReview) {
            this.score = score;
            this.level = level;
            this.requiresHumanReview = requiresHumanReview;
        }
    }

    public static class GateStatus {
        public final boolean blocked;
        public final LocalDateTime blockedUntil;
        public final GateBlockReason blockReason;
        public final boolean requiresOtp;
        public final int failuresInWindow;

        private GateStatus(boolean blocked, LocalDateTime blockedUntil, GateBlockReason reason, boolean requiresOtp, int failuresInWindow) {
            this.blocked = blocked;
            this.blockedUntil = blockedUntil;
            this.blockReason = reason;
            this.requiresOtp = requiresOtp;
            this.failuresInWindow = Math.max(0, failuresInWindow);
        }

        public static GateStatus blocked(LocalDateTime until, GateBlockReason reason, boolean requiresOtp, int failuresInWindow) {
            return new GateStatus(true, until, reason, requiresOtp, failuresInWindow);
        }

        public static GateStatus open(boolean requiresOtp, int failuresInWindow) {
            return new GateStatus(false, null, null, requiresOtp, failuresInWindow);
        }
    }

    public static class AttemptDecision {
        public final boolean blocked;
        public final LocalDateTime blockedUntil;
        public final boolean requiresOtp;
        public final int failuresInWindow;
        public final RiskAssessment risk;

        private AttemptDecision(boolean blocked, LocalDateTime blockedUntil, boolean requiresOtp, int failuresInWindow, RiskAssessment risk) {
            this.blocked = blocked;
            this.blockedUntil = blockedUntil;
            this.requiresOtp = requiresOtp;
            this.failuresInWindow = Math.max(0, failuresInWindow);
            this.risk = risk;
        }

        public static AttemptDecision blocked(LocalDateTime until, boolean requiresOtp, int failuresInWindow, RiskAssessment risk) {
            return new AttemptDecision(true, until, requiresOtp, failuresInWindow, risk);
        }

        public static AttemptDecision open(boolean requiresOtp, int failuresInWindow, RiskAssessment risk) {
            return new AttemptDecision(false, null, requiresOtp, failuresInWindow, risk);
        }
    }

    public static class OtpDecision {
        public final boolean blocked;
        public final LocalDateTime blockedUntil;
        public final int otpFailuresInWindow;
        public final RiskAssessment risk;

        private OtpDecision(boolean blocked, LocalDateTime blockedUntil, int otpFailuresInWindow, RiskAssessment risk) {
            this.blocked = blocked;
            this.blockedUntil = blockedUntil;
            this.otpFailuresInWindow = Math.max(0, otpFailuresInWindow);
            this.risk = risk;
        }

        public static OtpDecision blocked(LocalDateTime until, int fails, RiskAssessment risk) {
            return new OtpDecision(true, until, fails, risk);
        }

        public static OtpDecision open(int fails, RiskAssessment risk) {
            return new OtpDecision(false, null, fails, risk);
        }
    }

    public static class AttemptsSummary {
        public final long success;
        public final long failed;

        public AttemptsSummary(long success, long failed) {
            this.success = success;
            this.failed = failed;
        }
    }

    public static class TopAttacker {
        public final String key;
        public final long count;

        public TopAttacker(String key, long count) {
            this.key = key;
            this.count = count;
        }
    }
}