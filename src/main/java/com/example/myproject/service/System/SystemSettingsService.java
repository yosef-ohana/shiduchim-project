package com.example.myproject.service.System;

import com.example.myproject.model.SystemSettings;
import com.example.myproject.repository.SystemSettingsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * SystemSettingsService (MASTER 2025 - FINAL OPTIMAL + COMPAT)
 *
 * ✅ ללא שינוי Entity
 * ✅ Scope: system / wedding / wedding.default / user / ai
 * ✅ Env overrides: תומך גם:
 *    - env.<env>.<key>
 *    - <env>.<key>
 * ✅ Auto Refresh + Delta לפי updatedAt
 * ✅ Admin: search/filter/export/import
 * ✅ Rule Engine loader helpers
 */
@Service
@Transactional
public class SystemSettingsService {

    public enum Scope { SYSTEM, WEDDING, USER, AI }

    // מינימום זמן בין refresh checks כדי לא להציף DB
    private static final Duration REFRESH_MIN_INTERVAL = Duration.ofSeconds(15);

    private static final String PREFIX_SYSTEM = "system.";
    private static final String PREFIX_WEDDING = "wedding.";
    private static final String PREFIX_WEDDING_DEFAULT = "wedding.default.";
    private static final String PREFIX_USER = "user.";
    private static final String PREFIX_AI = "ai.";

    // שני פורמטים נתמכים ל-env override:
    private static final String ENV_PREFIX_CANONICAL = "env."; // env.<env>.KEY
    // וגם: <env>.KEY (legacy/alternate)

    private final SystemSettingsRepository repo;
    private final ObjectMapper objectMapper;
    private final Environment springEnv;

    // =====================================================
    // ✅ CACHE
    // =====================================================

    private static class Cached {
        final String value;
        final String description;
        final LocalDateTime updatedAt;

        Cached(String value, String description, LocalDateTime updatedAt) {
            this.value = value;
            this.description = description;
            this.updatedAt = updatedAt;
        }
    }

    private static class CacheState {
        final Map<String, Cached> map;
        final LocalDateTime lastRefreshAt;
        final LocalDateTime latestUpdatedAtSeen;

        CacheState(Map<String, Cached> map, LocalDateTime lastRefreshAt, LocalDateTime latestUpdatedAtSeen) {
            this.map = map;
            this.lastRefreshAt = lastRefreshAt;
            this.latestUpdatedAtSeen = latestUpdatedAtSeen;
        }
    }

    private final AtomicReference<CacheState> cacheRef =
            new AtomicReference<>(new CacheState(new ConcurrentHashMap<>(), LocalDateTime.MIN, LocalDateTime.MIN));

    public SystemSettingsService(SystemSettingsRepository repo, ObjectMapper objectMapper, Environment springEnv) {
        this.repo = repo;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.springEnv = springEnv;
    }

    // =====================================================
    // ✅ ENV RESOLUTION
    // =====================================================

    /**
     * סדר עדיפות:
     * 1) APP_ENV
     * 2) Spring active profile (ראשון)
     * 3) system property "app.env"
     * fallback: "prod"
     */
    public String resolveEnv() {
        String env = safeTrim(System.getenv("APP_ENV"));
        if (!isBlank(env)) return env;

        try {
            String[] profiles = (springEnv == null) ? null : springEnv.getActiveProfiles();
            if (profiles != null && profiles.length > 0 && !isBlank(profiles[0])) return profiles[0].trim();
        } catch (Exception ignore) {}

        env = safeTrim(System.getProperty("app.env"));
        if (!isBlank(env)) return env;

        return "prod";
    }

    // =====================================================
    // ✅ KEY BUILD
    // =====================================================

    public String buildKey(Scope scope, Long scopeId, String key) {
        if (scope == null) scope = Scope.SYSTEM;
        if (isBlank(key)) throw new IllegalArgumentException("key is blank");
        String k = key.trim();

        return switch (scope) {
            case SYSTEM -> PREFIX_SYSTEM + k;
            case AI -> PREFIX_AI + k;
            case WEDDING -> {
                if (scopeId == null) throw new IllegalArgumentException("weddingId is null");
                yield PREFIX_WEDDING + scopeId + "." + k;
            }
            case USER -> {
                if (scopeId == null) throw new IllegalArgumentException("userId is null");
                yield PREFIX_USER + scopeId + "." + k;
            }
        };
    }

    public String buildWeddingDefaultKey(String key) {
        if (isBlank(key)) throw new IllegalArgumentException("key is blank");
        return PREFIX_WEDDING_DEFAULT + key.trim();
    }

    // =====================================================
    // ✅ CANDIDATE RESOLUTION ORDER (FULL)
    // =====================================================

    /**
     * סדר עדיפויות מלא (תאימות + אופטימלי):
     *
     * A) env canonical: env.<env>.<scopedKey>
     * B) env legacy:    <env>.<scopedKey>
     * C) scopedKey
     *
     * אם scope=WEDDING:
     *  - ואז wedding.default (env canonical/legacy + base)
     *
     * ואז fallback ל-system.<key> (env canonical/legacy + base)
     * ואז fallback ל-raw <key> (env canonical/legacy + base) כדי לא לשבור מערכות ישנות
     */
    private List<String> resolveCandidateKeys(String env, Scope scope, Long scopeId, String key) {
        env = safeTrim(env);
        String rawKey = key.trim();

        String scopedKey = buildKey(scope, scopeId, rawKey);
        String systemKey = PREFIX_SYSTEM + rawKey;

        List<String> candidates = new ArrayList<>();

        // 1) env overrides for scoped
        addEnvVariants(candidates, env, scopedKey);
        candidates.add(scopedKey);

        // 2) wedding.default fallback (רק אם חתונה)
        if (scope == Scope.WEDDING) {
            String wd = buildWeddingDefaultKey(rawKey);
            addEnvVariants(candidates, env, wd);
            candidates.add(wd);
        }

        // 3) system fallback
        addEnvVariants(candidates, env, systemKey);
        candidates.add(systemKey);

        // 4) raw fallback (תאימות אחורה)
        addEnvVariants(candidates, env, rawKey);
        candidates.add(rawKey);

        // unique keep order
        return new ArrayList<>(new LinkedHashSet<>(candidates));
    }

    private void addEnvVariants(List<String> out, String env, String keyName) {
        if (isBlank(env) || isBlank(keyName)) return;
        String e = env.trim();
        out.add(ENV_PREFIX_CANONICAL + e + "." + keyName); // env.prod.X
        out.add(e + "." + keyName);                       // prod.X
    }

    // =====================================================
    // ✅ CORE CRUD (single key)
    // =====================================================

    @Transactional(readOnly = true)
    public Optional<SystemSettings> findEntityByKeyName(String keyName) {
        if (isBlank(keyName)) return Optional.empty();
        try {
            return repo.findByKeyName(keyName.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public boolean existsKey(String keyName) {
        if (isBlank(keyName)) return false;
        try {
            return repo.existsByKeyName(keyName.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteKey(String keyName) {
        if (isBlank(keyName)) return;
        String k = keyName.trim();
        try { repo.deleteByKeyName(k); } catch (Exception ignore) { }
        cacheRef.get().map.remove(k);
    }

    public int deleteKeys(Collection<String> keyNames) {
        if (keyNames == null || keyNames.isEmpty()) return 0;

        List<String> norm = keyNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        // אם יש deleteByKeyNameIn בריפו הרשמי -> נשתמש; אחרת fallback
        boolean used = tryInvokeRepoVoid("deleteByKeyNameIn", new Class[]{Collection.class}, new Object[]{norm});
        if (!used) {
            for (String k : norm) deleteKey(k);
        } else {
            for (String k : norm) cacheRef.get().map.remove(k);
        }
        return norm.size();
    }

    public SystemSettings upsertRaw(String keyName, String value, String description) {
        if (isBlank(keyName)) throw new IllegalArgumentException("keyName is blank");
        String k = keyName.trim();

        SystemSettings entity = repo.findByKeyName(k).orElseGet(SystemSettings::new);

        safeInvoke(entity, "setKeyName", String.class, k);
        safeInvoke(entity, "setValue", String.class, value);
        safeInvoke(entity, "setDescription", String.class, description);
        safeInvoke(entity, "setUpdatedAt", LocalDateTime.class, LocalDateTime.now());
        safeSetIfNull(entity, "setCreatedAt", "getCreatedAt", LocalDateTime.class, LocalDateTime.now());

        SystemSettings saved = repo.save(entity);

        putCache(k,
                safeGetString(saved, "getValue"),
                safeGetString(saved, "getDescription"),
                safeGetTime(saved, "getUpdatedAt"));

        // bump seen token
        bumpLatestSeen(safeGetTime(saved, "getUpdatedAt"));
        return saved;
    }

    public SystemSettings upsert(Scope scope, Long scopeId, String key, String value, String description) {
        return upsertRaw(buildKey(scope, scopeId, key), value, description);
    }

    // =====================================================
    // ✅ EFFECTIVE GET (cache + refresh)
    // =====================================================

    @Transactional(readOnly = true)
    public Optional<String> getEffectiveString(String key) {
        return getEffectiveString(resolveEnv(), Scope.SYSTEM, null, key);
    }

    @Transactional(readOnly = true)
    public Optional<String> getEffectiveString(String env, Scope scope, Long scopeId, String key) {
        if (isBlank(key)) return Optional.empty();
        refreshCacheIfNeeded();

        for (String candidate : resolveCandidateKeys(env, scope, scopeId, key)) {
            Optional<String> v = getFromCacheOrDb(candidate);
            if (v.isPresent()) return v;
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public String getEffectiveStringOrDefault(String env, Scope scope, Long scopeId, String key, String def) {
        return getEffectiveString(env, scope, scopeId, key).orElse(def);
    }

    @Transactional(readOnly = true)
    public boolean getEffectiveBoolean(String env, Scope scope, Long scopeId, String key, boolean def) {
        String raw = getEffectiveStringOrDefault(env, scope, scopeId, key, null);
        if (raw == null) return def;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (Set.of("true","1","yes","y","on").contains(s)) return true;
        if (Set.of("false","0","no","n","off").contains(s)) return false;
        return def;
    }

    @Transactional(readOnly = true)
    public int getEffectiveInt(String env, Scope scope, Long scopeId, String key, int def) {
        String raw = getEffectiveStringOrDefault(env, scope, scopeId, key, null);
        if (raw == null) return def;
        try { return Integer.parseInt(raw.trim()); } catch (Exception e) { return def; }
    }

    @Transactional(readOnly = true)
    public long getEffectiveLong(String env, Scope scope, Long scopeId, String key, long def) {
        String raw = getEffectiveStringOrDefault(env, scope, scopeId, key, null);
        if (raw == null) return def;
        try { return Long.parseLong(raw.trim()); } catch (Exception e) { return def; }
    }

    @Transactional(readOnly = true)
    public double getEffectiveDouble(String env, Scope scope, Long scopeId, String key, double def) {
        String raw = getEffectiveStringOrDefault(env, scope, scopeId, key, null);
        if (raw == null) return def;
        try { return Double.parseDouble(raw.trim()); } catch (Exception e) { return def; }
    }

    @Transactional(readOnly = true)
    public Duration getEffectiveDurationSeconds(String env, Scope scope, Long scopeId, String key, Duration def) {
        long seconds = getEffectiveLong(env, scope, scopeId, key, -1);
        return (seconds < 0) ? def : Duration.ofSeconds(seconds);
    }

    @Transactional(readOnly = true)
    public <T> T getEffectiveJson(String env, Scope scope, Long scopeId, String key, Class<T> clazz, T def) {
        String raw = getEffectiveStringOrDefault(env, scope, scopeId, key, null);
        if (raw == null) return def;
        try { return objectMapper.readValue(raw, clazz); } catch (Exception e) { return def; }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEffectiveJsonMap(String env, Scope scope, Long scopeId, String key) {
        String raw = getEffectiveStringOrDefault(env, scope, scopeId, key, null);
        if (raw == null) return Collections.emptyMap();
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    // =====================================================
    // ✅ RULE ENGINE SUPPORT
    // =====================================================

    @Transactional(readOnly = true)
    public boolean isRuleEnabled(String env, Scope scope, Long scopeId, String ruleId, boolean def) {
        if (isBlank(ruleId)) return def;
        return getEffectiveBoolean(env, scope, scopeId, "rules." + ruleId + ".enabled", def);
    }

    @Transactional(readOnly = true)
    public String getRuleGroup(String env, Scope scope, Long scopeId, String ruleId, String def) {
        if (isBlank(ruleId)) return def;
        return getEffectiveStringOrDefault(env, scope, scopeId, "rules." + ruleId + ".group", def);
    }

    /**
     * טוען params שטוחים של rule לפי prefix, בצורה "Effective":
     * - קודם בסיס (scoped)
     * - ואז env override דורס
     */
    @Transactional(readOnly = true)
    public Map<String, String> loadRuleParamsFlatEffective(String env, Scope scope, Long scopeId, String ruleId) {
        if (isBlank(ruleId)) return Collections.emptyMap();

        String basePrefix = buildKey(scope, scopeId, "rules." + ruleId + ".");
        Map<String, String> out = new LinkedHashMap<>();

        // 1) base
        for (SystemSettings s : findByPrefixBestEffort(basePrefix)) {
            String k = safeGetString(s, "getKeyName");
            if (k == null || !k.startsWith(basePrefix)) continue;
            String shortKey = k.substring(basePrefix.length());
            if (!isBlank(shortKey)) out.put(shortKey, safeGetString(s, "getValue"));
        }

        // 2) env overrides (שני פורמטים)
        env = safeTrim(env);
        if (!isBlank(env)) {
            for (String envPrefix : List.of(
                    ENV_PREFIX_CANONICAL + env + "." + basePrefix,
                    env + "." + basePrefix
            )) {
                for (SystemSettings s : findByPrefixBestEffort(envPrefix)) {
                    String k = safeGetString(s, "getKeyName");
                    if (k == null || !k.startsWith(envPrefix)) continue;
                    String shortKey = k.substring(envPrefix.length());
                    if (!isBlank(shortKey)) out.put(shortKey, safeGetString(s, "getValue")); // override
                }
            }
        }

        return out;
    }

    // =====================================================
    // ✅ ADMIN / DASHBOARD
    // =====================================================

    @Transactional(readOnly = true)
    public List<SystemSettings> listAll() {
        return safeFindAllSorted();
    }

    @Transactional(readOnly = true)
    public List<SystemSettings> listByEnvironment(String env) {
        env = safeTrim(env);
        if (isBlank(env)) return Collections.emptyList();
        return listByPrefix(env + ".");
    }

    @Transactional(readOnly = true)
    public List<SystemSettings> listByPrefix(String prefix) {
        prefix = safeTrim(prefix);
        if (isBlank(prefix)) return Collections.emptyList();

        // אופטימיזציה: ננסה להשתמש ב-StartingWithAndUpdatedAtAfter עם MIN
        List<SystemSettings> fast = invokeRepoList(
                "findByKeyNameStartingWithAndUpdatedAtAfter",
                new Class[]{String.class, LocalDateTime.class},
                new Object[]{prefix, LocalDateTime.MIN}
        );
        if (!fast.isEmpty()) return sortByKey(fast);

        // fallback best effort
        return sortByKey(findByPrefixBestEffort(prefix));
    }

    @Transactional(readOnly = true)
    public List<SystemSettings> listByScope(Scope scope, Long scopeId) {
        if (scope == null) scope = Scope.SYSTEM;
        String prefix = switch (scope) {
            case SYSTEM -> PREFIX_SYSTEM;
            case AI -> PREFIX_AI;
            case WEDDING -> (scopeId == null) ? PREFIX_WEDDING : PREFIX_WEDDING + scopeId + ".";
            case USER -> (scopeId == null) ? PREFIX_USER : PREFIX_USER + scopeId + ".";
        };
        return listByPrefix(prefix);
    }

    @Transactional(readOnly = true)
    public List<SystemSettings> search(String q) {
        q = safeTrim(q);
        if (isBlank(q)) return Collections.emptyList();

        // נעדיף מתודות רשמיות, ואם חסרות - reflection fallback ואז scan
        List<SystemSettings> byKey = invokeRepoList("findByKeyNameContainingIgnoreCase", new Class[]{String.class}, new Object[]{q});
        List<SystemSettings> byDesc = invokeRepoList("findByDescriptionContainingIgnoreCase", new Class[]{String.class}, new Object[]{q});
        List<SystemSettings> byVal = invokeRepoList("findByValueContainingIgnoreCase", new Class[]{String.class}, new Object[]{q});

        if (!byKey.isEmpty() || !byDesc.isEmpty() || !byVal.isEmpty()) {
            Map<String, SystemSettings> merged = new LinkedHashMap<>();
            mergeUniqueByKeyName(merged, byKey);
            mergeUniqueByKeyName(merged, byDesc);
            mergeUniqueByKeyName(merged, byVal);
            return sortByKey(new ArrayList<>(merged.values()));
        }

        // fallback scan
        String qq = q.toLowerCase(Locale.ROOT);
        List<SystemSettings> out = new ArrayList<>();
        for (SystemSettings s : safeFindAllSorted()) {
            String k = safeOrEmpty(safeGetString(s, "getKeyName")).toLowerCase(Locale.ROOT);
            String d = safeOrEmpty(safeGetString(s, "getDescription")).toLowerCase(Locale.ROOT);
            String v = safeOrEmpty(safeGetString(s, "getValue")).toLowerCase(Locale.ROOT);
            if (k.contains(qq) || d.contains(qq) || v.contains(qq)) out.add(s);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<SystemSettings> listUpdatedAfter(LocalDateTime t) {
        if (t == null) t = LocalDateTime.now().minusDays(7);

        try {
            return repo.findByUpdatedAtAfter(t);
        } catch (Exception e) {
            List<SystemSettings> out = new ArrayList<>();
            for (SystemSettings s : safeFindAllSorted()) {
                LocalDateTime u = safeGetTime(s, "getUpdatedAt");
                if (u != null && u.isAfter(t)) out.add(s);
            }
            return out;
        }
    }

    @Transactional(readOnly = true)
    public List<SystemSettings> listUpdatedBefore(LocalDateTime t) {
        if (t == null) return Collections.emptyList();

        try {
            return repo.findByUpdatedAtBefore(t);
        } catch (Exception e) {
            List<SystemSettings> out = new ArrayList<>();
            for (SystemSettings s : safeFindAllSorted()) {
                LocalDateTime u = safeGetTime(s, "getUpdatedAt");
                if (u != null && u.isBefore(t)) out.add(s);
            }
            return out;
        }
    }

    public String exportCsv(String prefixOrNull) {
        List<SystemSettings> rows = isBlank(prefixOrNull) ? listAll() : listByPrefix(prefixOrNull.trim());
        StringBuilder sb = new StringBuilder();
        sb.append("keyName,value,description,updatedAt\n");
        for (SystemSettings s : rows) {
            sb.append(csv(safeGetString(s, "getKeyName"))).append(",")
                    .append(csv(safeGetString(s, "getValue"))).append(",")
                    .append(csv(safeGetString(s, "getDescription"))).append(",")
                    .append(csv(String.valueOf(safeGetTime(s, "getUpdatedAt"))))
                    .append("\n");
        }
        return sb.toString();
    }

    public int importFromMap(Map<String, Object> payload, String defaultDescription) {
        if (payload == null || payload.isEmpty()) return 0;
        int upserts = 0;

        for (Map.Entry<String, Object> e : payload.entrySet()) {
            String keyName = safeTrim(e.getKey());
            if (isBlank(keyName)) continue;

            Object raw = e.getValue();
            String value;
            String desc = defaultDescription;

            if (raw instanceof Map<?, ?> m) {
                Object v = m.get("value");
                Object d = m.get("description");
                value = (v == null) ? null : String.valueOf(v);
                if (d != null && !isBlank(String.valueOf(d))) desc = String.valueOf(d);
            } else {
                value = (raw == null) ? null : String.valueOf(raw);
            }

            upsertRaw(keyName, value, desc);
            upserts++;
        }
        return upserts;
    }

    // =====================================================
    // ✅ AUTO REFRESH / LIVE UPDATES + DELTA
    // =====================================================

    @Transactional(readOnly = true)
    public void refreshCacheIfNeeded() {
        CacheState st = cacheRef.get();
        LocalDateTime now = LocalDateTime.now();

        if (st.lastRefreshAt != null && Duration.between(st.lastRefreshAt, now).compareTo(REFRESH_MIN_INTERVAL) < 0) {
            return;
        }

        LocalDateTime latestDb = findLatestUpdatedAtInDb();
        if (latestDb != null && st.latestUpdatedAtSeen != null && !latestDb.isAfter(st.latestUpdatedAtSeen)) {
            cacheRef.set(new CacheState(st.map, now, st.latestUpdatedAtSeen));
            return;
        }

        LocalDateTime since = (st.latestUpdatedAtSeen != null) ? st.latestUpdatedAtSeen : LocalDateTime.MIN;
        List<SystemSettings> changed = listUpdatedAfter(since.minusSeconds(1));

        LocalDateTime maxSeen = (st.latestUpdatedAtSeen != null) ? st.latestUpdatedAtSeen : LocalDateTime.MIN;
        for (SystemSettings s : changed) {
            String k = safeGetString(s, "getKeyName");
            if (k == null) continue;

            LocalDateTime u = safeGetTime(s, "getUpdatedAt");
            if (u != null && u.isAfter(maxSeen)) maxSeen = u;

            putCache(k, safeGetString(s, "getValue"), safeGetString(s, "getDescription"), u);
        }

        cacheRef.set(new CacheState(st.map, now, maxSeen));
    }

    /**
     * Delta ממוקד ל-prefix (Job #7 / RuleEngine refresh):
     * משתמש ב-findByKeyNameStartingWithAndUpdatedAtAfter אם קיים.
     */
    @Transactional(readOnly = true)
    public List<SystemSettings> deltaByPrefix(String prefix, LocalDateTime after) {
        prefix = safeTrim(prefix);
        if (isBlank(prefix) || after == null) return Collections.emptyList();

        List<SystemSettings> fast = invokeRepoList(
                "findByKeyNameStartingWithAndUpdatedAtAfter",
                new Class[]{String.class, LocalDateTime.class},
                new Object[]{prefix, after}
        );
        if (!fast.isEmpty()) return fast;

        // fallback scan
        List<SystemSettings> out = new ArrayList<>();
        for (SystemSettings s : findByPrefixBestEffort(prefix)) {
            LocalDateTime u = safeGetTime(s, "getUpdatedAt");
            if (u != null && u.isAfter(after)) out.add(s);
        }
        return out;
    }

    // =====================================================
    // ✅ MAINTENANCE / CLEANUP
    // =====================================================

    public int purgeUpdatedBefore(LocalDateTime cutoff) {
        if (cutoff == null) cutoff = LocalDateTime.now().minusDays(180);
        List<SystemSettings> oldOnes = listUpdatedBefore(cutoff);

        List<String> keys = new ArrayList<>();
        for (SystemSettings s : oldOnes) {
            String k = safeGetString(s, "getKeyName");
            if (!isBlank(k)) keys.add(k);
        }
        return deleteKeys(keys);
    }

    public int purgeByPrefix(String prefix) {
        prefix = safeTrim(prefix);
        if (isBlank(prefix)) return 0;

        List<SystemSettings> rows = listByPrefix(prefix);
        List<String> keys = new ArrayList<>();
        for (SystemSettings s : rows) {
            String k = safeGetString(s, "getKeyName");
            if (!isBlank(k)) keys.add(k);
        }
        return deleteKeys(keys);
    }

    // =====================================================
    // ✅ INTERNAL: cache/db
    // =====================================================

    private Optional<String> getFromCacheOrDb(String keyName) {
        if (isBlank(keyName)) return Optional.empty();
        String k = keyName.trim();

        Cached c = cacheRef.get().map.get(k);
        if (c != null) return Optional.ofNullable(c.value);

        Optional<SystemSettings> ent = findEntityByKeyName(k);
        if (ent.isEmpty()) return Optional.empty();

        SystemSettings s = ent.get();
        String v = safeGetString(s, "getValue");
        putCache(k, v, safeGetString(s, "getDescription"), safeGetTime(s, "getUpdatedAt"));
        return Optional.ofNullable(v);
    }

    private void putCache(String keyName, String value, String description, LocalDateTime updatedAt) {
        if (isBlank(keyName)) return;
        cacheRef.get().map.put(keyName.trim(), new Cached(value, description, updatedAt));
    }

    private void bumpLatestSeen(LocalDateTime t) {
        if (t == null) return;
        CacheState st = cacheRef.get();
        if (st.latestUpdatedAtSeen == null || t.isAfter(st.latestUpdatedAtSeen)) {
            cacheRef.set(new CacheState(st.map, st.lastRefreshAt, t));
        }
    }

    private LocalDateTime findLatestUpdatedAtInDb() {
        try {
            Optional<SystemSettings> top = repo.findTopByOrderByUpdatedAtDesc();
            if (top.isEmpty()) return null;
            return safeGetTime(top.get(), "getUpdatedAt");
        } catch (Exception e) {
            LocalDateTime max = null;
            for (SystemSettings s : safeFindAllSorted()) {
                LocalDateTime u = safeGetTime(s, "getUpdatedAt");
                if (u != null && (max == null || u.isAfter(max))) max = u;
            }
            return max;
        }
    }

    // =====================================================
    // ✅ PREFIX QUERY (BEST EFFORT)
    // =====================================================

    private List<SystemSettings> findByPrefixBestEffort(String prefix) {
        if (isBlank(prefix)) return Collections.emptyList();

        // אם יש findByKeyNameStartingWith - נשתמש
        List<SystemSettings> fast = invokeRepoList(
                "findByKeyNameStartingWith",
                new Class[]{String.class},
                new Object[]{prefix}
        );
        if (!fast.isEmpty()) return fast;

        // fallback scan
        List<SystemSettings> out = new ArrayList<>();
        for (SystemSettings s : safeFindAllSorted()) {
            String k = safeGetString(s, "getKeyName");
            if (k != null && k.startsWith(prefix)) out.add(s);
        }
        return out;
    }

    // =====================================================
    // ✅ REPO REFLECTION (safe)
    // =====================================================

    @SuppressWarnings("unchecked")
    private List<SystemSettings> invokeRepoList(String methodName, Class<?>[] types, Object[] args) {
        try {
            Method m = repo.getClass().getMethod(methodName, types);
            Object v = m.invoke(repo, args);
            if (v instanceof List) return (List<SystemSettings>) v;
        } catch (Exception ignore) { }
        return Collections.emptyList();
    }

    private boolean tryInvokeRepoVoid(String methodName, Class<?>[] types, Object[] args) {
        try {
            Method m = repo.getClass().getMethod(methodName, types);
            m.invoke(repo, args);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<SystemSettings> safeFindAllSorted() {
        List<SystemSettings> all;
        try {
            all = repo.findAll();
        } catch (Exception e) {
            all = Collections.emptyList();
        }
        all = all.stream().filter(Objects::nonNull).collect(Collectors.toList());
        all.sort(Comparator.comparing(a -> safeOrEmpty(safeGetString(a, "getKeyName"))));
        return all;
    }

    private List<SystemSettings> sortByKey(List<SystemSettings> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<SystemSettings> out = rows.stream().filter(Objects::nonNull).collect(Collectors.toList());
        out.sort(Comparator.comparing(a -> safeOrEmpty(safeGetString(a, "getKeyName"))));
        return out;
    }

    private void mergeUniqueByKeyName(Map<String, SystemSettings> merged, List<SystemSettings> rows) {
        if (rows == null) return;
        for (SystemSettings s : rows) {
            String k = safeGetString(s, "getKeyName");
            if (!isBlank(k)) merged.putIfAbsent(k, s);
        }
    }

    // =====================================================
    // ✅ ENTITY SAFE REFLECTION (no entity changes)
    // =====================================================

    private void safeInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        if (target == null || methodName == null || paramType == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Exception ignore) { }
    }

    private void safeSetIfNull(Object target,
                               String setterName,
                               String getterName,
                               Class<?> paramType,
                               Object valueIfNull) {
        if (target == null) return;
        try {
            Method g = target.getClass().getMethod(getterName);
            Object cur = g.invoke(target);
            if (cur != null) return;

            Method s = target.getClass().getMethod(setterName, paramType);
            s.invoke(target, valueIfNull);
        } catch (Exception ignore) { }
    }

    private String safeGetString(Object target, String getterName) {
        if (target == null || getterName == null) return null;
        try {
            Method m = target.getClass().getMethod(getterName);
            Object v = m.invoke(target);
            return (v instanceof String) ? (String) v : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private LocalDateTime safeGetTime(Object target, String getterName) {
        if (target == null || getterName == null) return null;
        try {
            Method m = target.getClass().getMethod(getterName);
            Object v = m.invoke(target);
            return (v instanceof LocalDateTime) ? (LocalDateTime) v : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    // =====================================================
    // ✅ UTIL
    // =====================================================

    private String safeTrim(String s) { return s == null ? null : s.trim(); }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String safeOrEmpty(String s) { return s == null ? "" : s; }

    private String csv(String v) {
        if (v == null) return "\"\"";
        String x = v.replace("\"", "\"\"");
        return "\"" + x + "\"";
    }
}