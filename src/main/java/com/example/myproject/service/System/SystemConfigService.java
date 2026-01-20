package com.example.myproject.service.System;

import com.example.myproject.model.SystemConfig;
import com.example.myproject.model.SystemLog;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.repository.SystemConfigRepository;
import com.example.myproject.repository.SystemLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SystemConfigService (MASTER 2025 - FINAL OPTIMAL, NO ENTITY CHANGES)
 *
 * Entity fields available:
 *  - SystemConfig.environment (nullable => GLOBAL)
 *  - SystemConfig.jsonConfig (TEXT)
 *  - createdAt / updatedAt
 *
 * Repository capabilities assumed (as you pasted in the "REAL fields + JSON conventions" repo):
 *  - findByEnvironmentOrEnvironmentIsNullOrderByCreatedAtDesc(env)
 *  - findByEnvironmentAndConfigKeyInJsonOrderByCreatedAtDesc(env, key)
 *  - findByGlobalConfigKeyInJsonOrderByCreatedAtDesc(key)
 *  - findByCategoryInJsonOrderByCreatedAtDesc(category)
 *  - findByEnvironmentAndCategoryInJsonOrderByCreatedAtDesc(env, category)
 *  - findActiveInJsonOrderByCreatedAtDesc()
 *  - findByEnvironmentActiveInJsonOrderByCreatedAtDesc(env)
 *  - findGlobalActiveInJsonOrderByCreatedAtDesc()
 *  - and standard date maintenance queries.
 *
 * JSON conventions supported (best-effort):
 *  - configKey / key
 *  - category
 *  - value / val / data.value
 *  - active (boolean)
 *  - effectiveAt (ISO LocalDateTime) / effective_at
 *  - updatedBy / updated_by (optional)
 */
@Service
@Transactional
public class SystemConfigService {

    // =========================================================
    // Dependencies
    // =========================================================

    private final SystemConfigRepository systemConfigRepository;

    // Optional (won't break if you don't have it wired yet)
    private SystemLogRepository systemLogRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    public void setSystemLogRepository(SystemLogRepository systemLogRepository) {
        this.systemLogRepository = systemLogRepository;
    }

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    // =========================================================
    // Cache (in-memory)
    // =========================================================

    private static class CacheEntry {
        final Map<String, ParsedConfig> effectiveByKey; // key -> effective config
        final LocalDateTime builtAt;

        CacheEntry(Map<String, ParsedConfig> effectiveByKey, LocalDateTime builtAt) {
            this.effectiveByKey = effectiveByKey;
            this.builtAt = builtAt;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cacheByEnv = new ConcurrentHashMap<>();
    private final AtomicLong cacheVersion = new AtomicLong(0);

    /** Safety TTL (you can set via env later). */
    private volatile Duration cacheTtl = Duration.ofMinutes(5);

    // =========================================================
    // Parsed view of jsonConfig
    // =========================================================

    private static class ParsedConfig {
        final SystemConfig row;
        final String environment;   // null => global
        final String configKey;     // required for "effective" map
        final String category;      // optional
        final boolean active;       // default true if missing
        final LocalDateTime effectiveAt; // default createdAt if missing
        final String value;         // stringified value
        final String updatedBy;     // optional

        ParsedConfig(SystemConfig row,
                     String environment,
                     String configKey,
                     String category,
                     boolean active,
                     LocalDateTime effectiveAt,
                     String value,
                     String updatedBy) {
            this.row = row;
            this.environment = environment;
            this.configKey = configKey;
            this.category = category;
            this.active = active;
            this.effectiveAt = effectiveAt;
            this.value = value;
            this.updatedBy = updatedBy;
        }
    }

    // =========================================================
    // Startup warmup (safe)
    // =========================================================

    @PostConstruct
    public void warmupDefault() {
        // Best effort: warm global-only cache and a "default env" if you want.
        // Not forcing because you might have multiple envs.
        refreshCache(null, "SYSTEM_BOOT");
    }

    // =========================================================
    // Public API - Effective resolution
    // =========================================================

    @Transactional(readOnly = true)
    public Optional<String> getEffectiveValue(String environment, String configKey, LocalDateTime now) {
        ParsedConfig pc = getEffectiveParsed(environment, configKey, now);
        return pc == null ? Optional.empty() : Optional.ofNullable(pc.value);
    }

    @Transactional(readOnly = true)
    public Optional<SystemConfig> getEffectiveRow(String environment, String configKey, LocalDateTime now) {
        ParsedConfig pc = getEffectiveParsed(environment, configKey, now);
        return pc == null ? Optional.empty() : Optional.of(pc.row);
    }

    /**
     * Main resolver:
     *  1) env-specific effective (active + effectiveAt<=now) by configKey
     *  2) fallback to GLOBAL (environment null)
     */
    @Transactional(readOnly = true)
    public ParsedConfig getEffectiveParsed(String environment, String configKey, LocalDateTime now) {
        if (configKey == null || configKey.isBlank()) return null;
        LocalDateTime time = (now == null ? LocalDateTime.now() : now);

        // Try cache first
        CacheEntry cached = getOrBuildCache(environment, time);
        ParsedConfig hit = cached.effectiveByKey.get(configKey);
        if (hit != null) return hit;

        // Fallback to global cache if env provided
        if (environment != null && !environment.isBlank()) {
            CacheEntry globalCached = getOrBuildCache(null, time);
            return globalCached.effectiveByKey.get(configKey);
        }

        return null;
    }

    /**
     * Bulk load: returns Map<configKey, value> (effective for now).
     * Env overrides GLOBAL for same configKey.
     */
    @Transactional(readOnly = true)
    public Map<String, String> loadAllEffectiveValues(String environment, LocalDateTime now) {
        LocalDateTime time = (now == null ? LocalDateTime.now() : now);

        CacheEntry envCache = getOrBuildCache(environment, time);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, ParsedConfig> e : envCache.effectiveByKey.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : e.getValue().value);
        }

        // ensure fallback keys exist (only if env!=null)
        if (environment != null && !environment.isBlank()) {
            CacheEntry globalCache = getOrBuildCache(null, time);
            for (Map.Entry<String, ParsedConfig> e : globalCache.effectiveByKey.entrySet()) {
                out.putIfAbsent(e.getKey(), e.getValue() == null ? null : e.getValue().value);
            }
        }

        return out;
    }

    // =========================================================
    // Category API
    // =========================================================

    @Transactional(readOnly = true)
    public Map<String, String> loadCategoryValues(String category, String environment, LocalDateTime now) {
        if (category == null || category.isBlank()) return Map.of();
        LocalDateTime time = (now == null ? LocalDateTime.now() : now);

        // We can load from cache and filter by category (fast, consistent effectiveAt handling)
        Map<String, String> all = loadAllEffectiveValues(environment, time);
        Map<String, String> out = new LinkedHashMap<>();

        // Need category metadata -> use parsed cache (not only values)
        CacheEntry envCache = getOrBuildCache(environment, time);
        for (Map.Entry<String, ParsedConfig> e : envCache.effectiveByKey.entrySet()) {
            ParsedConfig pc = e.getValue();
            if (pc != null && equalsIgnoreCase(pc.category, category)) {
                out.put(e.getKey(), pc.value);
            }
        }

        // fallback to global
        if (environment != null && !environment.isBlank()) {
            CacheEntry globalCache = getOrBuildCache(null, time);
            for (Map.Entry<String, ParsedConfig> e : globalCache.effectiveByKey.entrySet()) {
                ParsedConfig pc = e.getValue();
                if (pc != null && equalsIgnoreCase(pc.category, category)) {
                    out.putIfAbsent(e.getKey(), pc.value);
                }
            }
        }

        return out;
    }

    // =========================================================
    // Feature flags
    // =========================================================

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String configKey, String environment, LocalDateTime now) {
        String v = getEffectiveValue(environment, configKey, now).orElse(null);
        return parseBoolean(v, false);
    }

    // =========================================================
// Typed getters (SystemRules friendly)
// =========================================================


    // =========================================================
// ✅ System/Admin Ban (Rule 27) — centralized via SystemConfig
// =========================================================
    @Transactional(readOnly = true)
    public boolean isUserBanned(Long userId) {
        return isUserBanned(null, userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean isUserBanned(String environment, Long userId, LocalDateTime now) {
        if (userId == null) return false;
        if (now == null) now = LocalDateTime.now();

        String[] keys = new String[] {
                "BAN_ALL",
                "BAN_USER_" + userId,
                "ban_user_" + userId,
                "userBan." + userId,
                "user_ban." + userId,
                "ban.user." + userId
        };

        for (String k : keys) {
            String v = getEffectiveValue(environment, k, now).orElse(null);
            if (v == null || v.isBlank()) continue;

            String s = v.trim();

            // permanent ban
            if ("true".equalsIgnoreCase(s)
                    || "perm".equalsIgnoreCase(s)
                    || "permanent".equalsIgnoreCase(s)
                    || "forever".equalsIgnoreCase(s)) {
                return true;
            }

            // until ISO datetime
            try {
                LocalDateTime until = LocalDateTime.parse(s);
                if (until != null && now.isBefore(until)) return true;
            } catch (Exception ignore) { }
        }

        return false;
    }

    // =========================================================
// ✅ Load/Degrade — centralized flag via SystemConfig
// =========================================================
    @Transactional(readOnly = true)
    public boolean shouldDegradeNonCriticalNow() {
        if (getBoolean(null, "DEGRADE_NON_CRITICAL", false)) return true;
        if (getBoolean(null, "degrade.nonCritical", false)) return true;
        if (getBoolean(null, "system.degradeNonCritical", false)) return true;

        String untilRaw = getString(null, "DEGRADE_NON_CRITICAL_UNTIL", null);
        if (untilRaw != null && !untilRaw.isBlank()) {
            try {
                LocalDateTime until = LocalDateTime.parse(untilRaw.trim());
                return LocalDateTime.now().isBefore(until);
            } catch (Exception ignore) { }
        }

        return false;
    }



    // =========================================================
    // Typed getters (SystemRules friendly)
    // =========================================================

    @Transactional(readOnly = true)
    public int getInt(String environment, String configKey, int defaultValue) {
        String v = getEffectiveValue(environment, configKey, LocalDateTime.now()).orElse(null);
        Integer parsed = parseInt(v);
        return parsed != null ? parsed : defaultValue;
    }

    @Transactional(readOnly = true)
    public long getLong(String environment, String configKey, long defaultValue) {
        String v = getEffectiveValue(environment, configKey, LocalDateTime.now()).orElse(null);
        Long parsed = parseLong(v);
        return parsed != null ? parsed : defaultValue;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String environment, String configKey, boolean defaultValue) {
        String v = getEffectiveValue(environment, configKey, LocalDateTime.now()).orElse(null);
        return parseBoolean(v, defaultValue);
    }

    @Transactional(readOnly = true)
    public String getString(String environment, String configKey, String defaultValue) {
        String v = getEffectiveValue(environment, configKey, LocalDateTime.now()).orElse(null);
        return (v == null ? defaultValue : v);
    }

    /**
     * Supports:
     *  - "120" => seconds
     *  - "PT2M" => ISO Duration
     *  - "2m", "10s", "1h" (best effort)
     */
    @Transactional(readOnly = true)
    public Duration getDuration(String environment, String configKey, Duration defaultValue) {
        String v = getEffectiveValue(environment, configKey, LocalDateTime.now()).orElse(null);
        Duration d = parseDuration(v);
        return d != null ? d : defaultValue;
    }

    // =========================================================
    // Admin / History / Debug
    // =========================================================

    @Transactional(readOnly = true)
    public List<SystemConfig> getHistoryByEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return systemConfigRepository.findByEnvironmentIsNullOrderByCreatedAtDesc();
        }
        return systemConfigRepository.findByEnvironmentOrderByCreatedAtDesc(environment);
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> getHistoryByKey(String environment, String configKey) {
        if (configKey == null || configKey.isBlank()) return List.of();

        if (environment == null || environment.isBlank()) {
            return systemConfigRepository.findByGlobalConfigKeyInJsonOrderByCreatedAtDesc(configKey);
        }
        return systemConfigRepository.findByEnvironmentAndConfigKeyInJsonOrderByCreatedAtDesc(environment, configKey);
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> getHistoryByKeyWithGlobalFallback(String environment, String configKey) {
        if (configKey == null || configKey.isBlank()) return List.of();

        List<SystemConfig> out = new ArrayList<>();
        if (environment != null && !environment.isBlank()) {
            out.addAll(systemConfigRepository.findByEnvironmentAndConfigKeyInJsonOrderByCreatedAtDesc(environment, configKey));
        }
        out.addAll(systemConfigRepository.findByGlobalConfigKeyInJsonOrderByCreatedAtDesc(configKey));

        out.sort(Comparator.comparing(SystemConfig::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> searchJson(String environment, String containsText) {
        if (containsText == null || containsText.isBlank()) return List.of();
        if (environment == null || environment.isBlank()) {
            return systemConfigRepository.findByJsonConfigContainingIgnoreCase(containsText);
        }
        return systemConfigRepository.findByEnvironmentAndJsonConfigContainingIgnoreCase(environment, containsText);
    }

    // =========================================================
    // Create / Schedule change (no entity change)
    // =========================================================

    /**
     * Creates a new SystemConfig row (append-only history).
     * effectiveAt controls when this config becomes effective.
     * active controls whether it is considered for effective resolution.
     *
     * NOTE: since entity doesn't have updatedBy/effectiveAt/etc fields,
     * they are stored inside jsonConfig.
     */
    public SystemConfig scheduleConfigChange(String environment,
                                             String configKey,
                                             String category,
                                             Object value,
                                             boolean active,
                                             LocalDateTime effectiveAt,
                                             String updatedBy) {

        if (configKey == null || configKey.isBlank()) {
            throw new IllegalArgumentException("configKey is required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configKey", configKey);
        if (category != null && !category.isBlank()) payload.put("category", category);

        payload.put("active", active);

        // effectiveAt: if null -> now (so it becomes effective immediately)
        LocalDateTime eff = (effectiveAt == null ? LocalDateTime.now() : effectiveAt);
        payload.put("effectiveAt", eff.toString());

        // value: store as-is (string/number/boolean/object)
        payload.put("value", value);

        if (updatedBy != null && !updatedBy.isBlank()) payload.put("updatedBy", updatedBy);

        String json = writeJsonSafe(payload);

        SystemConfig row = new SystemConfig();
        row.setEnvironment(blankToNull(environment));
        row.setJsonConfig(json);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());

        row = systemConfigRepository.save(row);

        auditConfig(SystemActionType.SECURITY_CONFIG_CHANGED,
                SystemSeverityLevel.NOTICE,
                "CONFIG_CHANGE",
                updatedBy,
                row.getId(),
                buildAuditContext(environment, configKey, category, active, eff, value));

        // refresh cache for env + global (because fallback)
        refreshCache(environment, "SCHEDULE_CHANGE");
        refreshCache(null, "SCHEDULE_CHANGE_GLOBAL");

        return row;
    }

    // =========================================================
    // Cache control
    // =========================================================

    public void setCacheTtlSeconds(long seconds) {
        if (seconds <= 0) return;
        this.cacheTtl = Duration.ofSeconds(seconds);
        refreshAllCaches("TTL_CHANGED");
    }

    public void refreshAllCaches(String reason) {
        cacheByEnv.clear();
        cacheVersion.incrementAndGet();
        auditConfig(SystemActionType.SYSTEM_CONFIG_RELOAD, SystemSeverityLevel.INFO,
                "CACHE_CLEAR", null, null, "{\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    public void refreshCache(String environment, String reason) {
        cacheByEnv.remove(normalizeEnvKey(environment));
        cacheVersion.incrementAndGet();
        auditConfig(SystemActionType.SYSTEM_CONFIG_RELOAD, SystemSeverityLevel.INFO,
                "CACHE_REFRESH", null, null, "{\"env\":\"" + escapeJson(environment) + "\",\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    // =========================================================
    // Internal - Build cache
    // =========================================================

    private CacheEntry getOrBuildCache(String environment, LocalDateTime now) {
        String envKey = normalizeEnvKey(environment);

        CacheEntry existing = cacheByEnv.get(envKey);
        if (existing != null && !isExpired(existing.builtAt, now)) {
            return existing;
        }

        CacheEntry rebuilt = buildCacheForEnv(environment, now);
        cacheByEnv.put(envKey, rebuilt);
        return rebuilt;
    }

    private boolean isExpired(LocalDateTime builtAt, LocalDateTime now) {
        if (builtAt == null) return true;
        Duration age = Duration.between(builtAt, now);
        return age.compareTo(cacheTtl) > 0;
    }

    /**
     * Build effective map for a single env key.
     * We load env+global rows together when env!=null (repo supports that),
     * but we still compute effective selection per key with correct priority:
     * env-specific beats global for same key (when both are effective).
     */
    private CacheEntry buildCacheForEnv(String environment, LocalDateTime now) {
        LocalDateTime time = (now == null ? LocalDateTime.now() : now);

        List<SystemConfig> rows;
        if (environment == null || environment.isBlank()) {
            // global-only
            rows = systemConfigRepository.findByEnvironmentIsNullOrderByCreatedAtDesc();
        } else {
            // env + global together
            rows = systemConfigRepository.findByEnvironmentOrEnvironmentIsNullOrderByCreatedAtDesc(environment);
        }

        // Parse all rows, collect by configKey, then pick effective candidate.
        // Priority rules:
        //  - consider only active==true
        //  - consider only effectiveAt<=now
        //  - for same key:
        //      * if there is any env-specific effective => choose the best among env-specific
        //      * else choose best among global
        //  - "best" = latest effectiveAt, then latest createdAt
        Map<String, List<ParsedConfig>> byKey = new HashMap<>();

        if (rows != null) {
            for (SystemConfig r : rows) {
                ParsedConfig pc = parseRow(r);
                if (pc == null || pc.configKey == null || pc.configKey.isBlank()) continue;
                byKey.computeIfAbsent(pc.configKey, k -> new ArrayList<>()).add(pc);
            }
        }

        Map<String, ParsedConfig> effective = new HashMap<>();
        for (Map.Entry<String, List<ParsedConfig>> e : byKey.entrySet()) {
            String key = e.getKey();
            ParsedConfig chosen = chooseEffectiveForKey(environment, key, e.getValue(), time);
            if (chosen != null) {
                effective.put(key, chosen);
            }
        }

        return new CacheEntry(Collections.unmodifiableMap(effective), LocalDateTime.now());
    }

    private ParsedConfig chooseEffectiveForKey(String environment, String key, List<ParsedConfig> candidates, LocalDateTime now) {
        if (candidates == null || candidates.isEmpty()) return null;

        String env = blankToNull(environment);

        List<ParsedConfig> envSpecific = new ArrayList<>();
        List<ParsedConfig> global = new ArrayList<>();

        for (ParsedConfig pc : candidates) {
            if (pc == null) continue;

            // active?
            if (!pc.active) continue;

            // effectiveAt <= now ?
            LocalDateTime eff = (pc.effectiveAt == null ? pc.row.getCreatedAt() : pc.effectiveAt);
            if (eff != null && eff.isAfter(now)) continue;

            if (env != null && env.equals(pc.environment)) {
                envSpecific.add(pc);
            } else if (pc.environment == null) {
                global.add(pc);
            }
        }

        Comparator<ParsedConfig> cmp = Comparator
                .comparing((ParsedConfig p) -> p.effectiveAt == null ? p.row.getCreatedAt() : p.effectiveAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(p -> p.row.getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();

        if (!envSpecific.isEmpty()) {
            envSpecific.sort(cmp);
            return envSpecific.get(0);
        }
        if (!global.isEmpty()) {
            global.sort(cmp);
            return global.get(0);
        }
        return null;
    }

    // =========================================================
    // JSON parsing (best-effort)
    // =========================================================

    private ParsedConfig parseRow(SystemConfig row) {
        if (row == null) return null;

        String env = row.getEnvironment(); // may be null => global
        String json = row.getJsonConfig();
        if (json == null || json.isBlank()) return null;

        Map<String, Object> map = readJsonSafe(json);
        if (map == null || map.isEmpty()) return null;

        String key = asString(firstNonNull(
                map.get("configKey"),
                map.get("key"),
                deepGet(map, "data.configKey"),
                deepGet(map, "data.key")
        ));

        String category = asString(firstNonNull(
                map.get("category"),
                deepGet(map, "data.category")
        ));

        Boolean active = asBoolean(firstNonNull(
                map.get("active"),
                map.get("isActive"),
                map.get("enabled"),
                deepGet(map, "data.active"),
                deepGet(map, "data.enabled")
        ));

        // default: active true (if missing)
        boolean isActive = (active == null) || active;

        LocalDateTime effectiveAt = parseDateTime(asString(firstNonNull(
                map.get("effectiveAt"),
                map.get("effective_at"),
                deepGet(map, "data.effectiveAt"),
                deepGet(map, "data.effective_at")
        )));

        Object vObj = firstNonNull(
                map.get("value"),
                map.get("val"),
                map.get("dataValue"),
                deepGet(map, "data.value"),
                deepGet(map, "data.val")
        );

        String value = stringifyValue(vObj);

        String updatedBy = asString(firstNonNull(
                map.get("updatedBy"),
                map.get("updated_by"),
                map.get("admin"),
                deepGet(map, "data.updatedBy")
        ));

        // fallback: if effectiveAt missing -> createdAt
        if (effectiveAt == null) effectiveAt = row.getCreatedAt();

        return new ParsedConfig(row, env, key, category, isActive, effectiveAt, value, updatedBy);
    }

    private Map<String, Object> readJsonSafe(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Try to "wrap" invalid JSON? (no - we keep best-effort strict)
            return null;
        }
    }

    private String writeJsonSafe(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            // last resort (shouldn't happen for map)
            return "{\"error\":\"json_write_failed\"}";
        }
    }

    private Object deepGet(Map<String, Object> map, String path) {
        if (map == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object cur = map;
        for (String p : parts) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(p);
            if (cur == null) return null;
        }
        return cur;
    }

    private Object firstNonNull(Object... items) {
        if (items == null) return null;
        for (Object o : items) {
            if (o != null) return o;
        }
        return null;
    }

    private String asString(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s;
        return String.valueOf(o);
    }

    private Boolean asBoolean(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o instanceof String s) {
            String v = s.trim().toLowerCase();
            if (v.equals("true") || v.equals("yes") || v.equals("1") || v.equals("on")) return true;
            if (v.equals("false") || v.equals("no") || v.equals("0") || v.equals("off")) return false;
        }
        return null;
    }

    private String stringifyValue(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        // object/array -> stringify as JSON
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // =========================================================
    // Parsing helpers
    // =========================================================

    private Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return null; }
    }

    private Long parseLong(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return null; }
    }

    private boolean parseBoolean(String s, boolean defaultValue) {
        if (s == null) return defaultValue;
        String v = s.trim().toLowerCase();
        if (v.equals("true") || v.equals("yes") || v.equals("1") || v.equals("on")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("0") || v.equals("off")) return false;
        return defaultValue;
    }

    private Duration parseDuration(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();

        // ISO-8601 Duration
        try {
            if (v.startsWith("P") || v.startsWith("p")) {
                return Duration.parse(v.toUpperCase());
            }
        } catch (Exception ignore) {}

        // numeric => seconds
        Long asLong = parseLong(v);
        if (asLong != null) return Duration.ofSeconds(asLong);

        // short forms: 10s / 2m / 1h
        try {
            String lower = v.toLowerCase();
            if (lower.endsWith("ms")) return Duration.ofMillis(Long.parseLong(lower.substring(0, lower.length() - 2).trim()));
            if (lower.endsWith("s")) return Duration.ofSeconds(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            if (lower.endsWith("m")) return Duration.ofMinutes(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            if (lower.endsWith("h")) return Duration.ofHours(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
            if (lower.endsWith("d")) return Duration.ofDays(Long.parseLong(lower.substring(0, lower.length() - 1).trim()));
        } catch (Exception ignore) {}

        return null;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String normalizeEnvKey(String environment) {
        String e = blankToNull(environment);
        return e == null ? "__GLOBAL__" : e;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    // =========================================================
    // Optional auditing (SystemLog)
    // =========================================================

    private void auditConfig(SystemActionType actionType,
                             SystemSeverityLevel severity,
                             String details,
                             String updatedBy,
                             Long relatedEntityId,
                             String contextJson) {

        if (systemLogRepository == null) return;

        try {
            SystemLog log = new SystemLog();

            // Use safe reflection so we don't depend on exact SystemLog fields
            safeInvoke(log, "setTimestamp", LocalDateTime.class, LocalDateTime.now());
            safeInvoke(log, "setUserId", Long.class, null); // unknown here
            safeInvoke(log, "setActionType", SystemActionType.class, actionType == null ? SystemActionType.UNKNOWN_EVENT : actionType);
            safeInvoke(log, "setModule", SystemModule.class, SystemModule.SYSTEM_CONFIG_PROVIDER);
            safeInvoke(log, "setSeverity", SystemSeverityLevel.class, severity == null ? SystemSeverityLevel.INFO : severity);
            safeInvoke(log, "setSuccess", boolean.class, true);

            String d = details == null ? "CONFIG_EVENT" : details;
            safeInvoke(log, "setDetails", String.class, d);

            if (updatedBy != null) safeInvoke(log, "setActorName", String.class, updatedBy);

            safeInvoke(log, "setRelatedEntityType", String.class, "SystemConfig");
            safeInvoke(log, "setRelatedEntityId", Long.class, relatedEntityId);
            safeInvoke(log, "setContextJson", String.class, contextJson);

            systemLogRepository.save(log);
        } catch (Exception ignore) {}
    }

    private static void safeInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Exception ignore) {}
    }

    private String buildAuditContext(String environment,
                                     String configKey,
                                     String category,
                                     boolean active,
                                     LocalDateTime effectiveAt,
                                     Object value) {

        String env = blankToNull(environment);
        String val = (value == null ? "null" : escapeJson(String.valueOf(value)));

        return "{"
                + "\"environment\":" + (env == null ? "null" : "\"" + escapeJson(env) + "\"") + ","
                + "\"configKey\":\"" + escapeJson(configKey) + "\","
                + "\"category\":" + (category == null ? "null" : "\"" + escapeJson(category) + "\"") + ","
                + "\"active\":" + active + ","
                + "\"effectiveAt\":\"" + (effectiveAt == null ? "" : escapeJson(effectiveAt.toString())) + "\","
                + "\"value\":\"" + val + "\""
                + "}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =====================================================
    // ✅ History / Auditing wrappers (MASTER-ONE)
    // =====================================================

    @Transactional(readOnly = true)
    public List<SystemConfig> getConfigHistoryByCategory(String category, String environment, int limit) {
        if (category == null || category.isBlank()) return List.of();

        int safeLimit = Math.max(1, Math.min(limit, 500));

        // ניסיון קודם עם environment מדויק (מתודה קיימת אצלך ב-ZIP)
        List<SystemConfig> rows =
                systemConfigRepository.findByEnvironmentAndCategoryInJsonOrderByCreatedAtDesc(environment, category);

        // fallback: environment = null (כמו שהוגדר במסמך)
        if ((rows == null || rows.isEmpty()) && environment != null) {
            rows = systemConfigRepository.findByEnvironmentAndCategoryInJsonOrderByCreatedAtDesc(null, category);
        }

        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream().limit(safeLimit).toList();
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> getChangesByAdmin(String updatedBy, LocalDateTime since, String environment, int limit) {
        if (updatedBy == null || updatedBy.isBlank()) return List.of();
        if (since == null) since = LocalDateTime.now().minusDays(30);

        int safeLimit = Math.max(1, Math.min(limit, 500));

        // דורש את מתודת ה-Repo מהבלוק B (updatedBy בתוך jsonConfig + Pageable)
        List<SystemConfig> rows = systemConfigRepository.findByUpdatedByInJsonAndUpdatedAtAfterOrderByUpdatedAtDesc(
                updatedBy,
                since,
                org.springframework.data.domain.PageRequest.of(0, safeLimit)
        );

        if (rows == null || rows.isEmpty()) return List.of();

        // סינון environment ב-Java + fallback ל-null (כמו שהוגדר במסמך)
        if (environment == null) return rows;

        String env = environment.trim();
        return rows.stream()
                .filter(sc -> sc.getEnvironment() == null || env.equalsIgnoreCase(sc.getEnvironment()))
                .toList();
    }

    @Transactional
    public long purgeOlderThan(LocalDateTime cutoff) {
        if (cutoff == null) cutoff = LocalDateTime.now().minusDays(365);

        // דורש deleteByCreatedAtBefore מהבלוק B
        long deleted = systemConfigRepository.deleteByCreatedAtBefore(cutoff);

        // חשוב: אחרי purge לנקות caches פנימיים
        refreshAllCaches("purgeOlderThan");
        return deleted;
    }

}