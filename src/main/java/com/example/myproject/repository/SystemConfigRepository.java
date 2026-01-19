package com.example.myproject.repository;

import com.example.myproject.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;


import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    // ============================================================
    // 🔵 1) Latest / History by environment (REAL fields)
    // ============================================================

    Optional<SystemConfig> findTopByEnvironmentOrderByCreatedAtDesc(String environment);

    List<SystemConfig> findByEnvironmentOrderByCreatedAtDesc(String environment);

    Optional<SystemConfig> findTopByEnvironmentIsNullOrderByCreatedAtDesc();

    List<SystemConfig> findByEnvironmentIsNullOrderByCreatedAtDesc();

    /**
     * נוח לטעינה מרוכזת: env + גלובלי יחד (service יעשה merge + precedence)
     */
    List<SystemConfig> findByEnvironmentOrEnvironmentIsNullOrderByCreatedAtDesc(String environment);

    // ============================================================
    // 🔵 2) Existence / Counting
    // ============================================================

    boolean existsByEnvironment(String environment);

    long countByEnvironment(String environment);

    // ============================================================
    // 🔵 3) Bulk loading (Warmup / Dashboard)
    // ============================================================

    List<SystemConfig> findByEnvironmentIn(Collection<String> environments);

    // ============================================================
    // 🔵 4) Maintenance by dates (REAL fields)
    // ============================================================

    List<SystemConfig> findByCreatedAtBefore(LocalDateTime time);

    List<SystemConfig> findByCreatedAtAfter(LocalDateTime time);

    List<SystemConfig> findByUpdatedAtAfter(LocalDateTime time);

    List<SystemConfig> findByUpdatedAtBefore(LocalDateTime time);

    List<SystemConfig> findByEnvironmentAndCreatedAtBetween(
            String environment,
            LocalDateTime start,
            LocalDateTime end
    );

    // ============================================================
    // 🔵 5) Latest overall (REAL fields)
    // ============================================================

    Optional<SystemConfig> findTopByOrderByCreatedAtDesc();

    Optional<SystemConfig> findTopByOrderByUpdatedAtDesc();

    List<SystemConfig> findAllByOrderByCreatedAtDesc();

    // ============================================================
    // 🟣 6) JSON search (Best-effort, no entity changes)
    // ============================================================

    List<SystemConfig> findByJsonConfigContainingIgnoreCase(String text);

    List<SystemConfig> findByEnvironmentAndJsonConfigContainingIgnoreCase(String environment, String text);

    List<SystemConfig> findByEnvironmentIsNullAndJsonConfigContainingIgnoreCase(String text);

    // ============================================================
    // 🟠 7) JSON “capabilities” (Best-effort) - Robust patterns
    //      Supports:
    //        - "configKey":"X" OR "key":"X"
    //        - with/without spaces after colon
    // ============================================================

    @Query("""
           select c from SystemConfig c
           where c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like lower(concat('%"configKey":"', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"configKey" : "', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"key":"', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"key" : "', :k, '"%'))
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findByConfigKeyInJsonOrderByCreatedAtDesc(@Param("k") String configKeyOrKey);

    @Query("""
           select c from SystemConfig c
           where c.environment = :env
             and c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like lower(concat('%"configKey":"', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"configKey" : "', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"key":"', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"key" : "', :k, '"%'))
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findByEnvironmentAndConfigKeyInJsonOrderByCreatedAtDesc(
            @Param("env") String environment,
            @Param("k") String configKeyOrKey
    );

    @Query("""
           select c from SystemConfig c
           where c.environment is null
             and c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like lower(concat('%"configKey":"', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"configKey" : "', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"key":"', :k, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"key" : "', :k, '"%'))
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findByGlobalConfigKeyInJsonOrderByCreatedAtDesc(@Param("k") String configKeyOrKey);

    // ============================================================
    // 🟠 8) Category via JSON (robust spacing)
    // ============================================================

    @Query("""
           select c from SystemConfig c
           where c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like lower(concat('%"category":"', :cat, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"category" : "', :cat, '"%'))
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findByCategoryInJsonOrderByCreatedAtDesc(@Param("cat") String category);

    @Query("""
           select c from SystemConfig c
           where c.environment = :env
             and c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like lower(concat('%"category":"', :cat, '"%'))
               or lower(c.jsonConfig) like lower(concat('%"category" : "', :cat, '"%'))
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findByEnvironmentAndCategoryInJsonOrderByCreatedAtDesc(
            @Param("env") String environment,
            @Param("cat") String category
    );

    // ============================================================
    // 🟠 9) Active via JSON (active OR enabled OR isActive)
    // ============================================================

    @Query("""
           select c from SystemConfig c
           where c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like '%"active":true%'
               or lower(c.jsonConfig) like '%"active" : true%'
               or lower(c.jsonConfig) like '%"enabled":true%'
               or lower(c.jsonConfig) like '%"enabled" : true%'
               or lower(c.jsonConfig) like '%"isactive":true%'
               or lower(c.jsonConfig) like '%"isactive" : true%'
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findActiveInJsonOrderByCreatedAtDesc();

    @Query("""
           select c from SystemConfig c
           where c.environment = :env
             and c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like '%"active":true%'
               or lower(c.jsonConfig) like '%"active" : true%'
               or lower(c.jsonConfig) like '%"enabled":true%'
               or lower(c.jsonConfig) like '%"enabled" : true%'
               or lower(c.jsonConfig) like '%"isactive":true%'
               or lower(c.jsonConfig) like '%"isactive" : true%'
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findByEnvironmentActiveInJsonOrderByCreatedAtDesc(@Param("env") String environment);

    @Query("""
           select c from SystemConfig c
           where c.environment is null
             and c.jsonConfig is not null
             and (
               lower(c.jsonConfig) like '%"active":true%'
               or lower(c.jsonConfig) like '%"active" : true%'
               or lower(c.jsonConfig) like '%"enabled":true%'
               or lower(c.jsonConfig) like '%"enabled" : true%'
               or lower(c.jsonConfig) like '%"isactive":true%'
               or lower(c.jsonConfig) like '%"isactive" : true%'
             )
           order by c.createdAt desc
           """)
    List<SystemConfig> findGlobalActiveInJsonOrderByCreatedAtDesc();


    // =====================================================
    // ✅ 10) History by "updatedBy" inside jsonConfig (DB-side)
    // =====================================================

    @Query("""
           select c from SystemConfig c
           where c.jsonConfig is not null
             and c.jsonConfig like concat('%\"updatedBy\":\"', :updatedBy, '\"%')
           order by c.updatedAt desc
           """)
    List<SystemConfig> findByUpdatedByInJsonOrderByUpdatedAtDesc(
            @Param("updatedBy") String updatedBy,
            Pageable pageable
    );

    @Query("""
           select c from SystemConfig c
           where c.updatedAt >= :since
             and c.jsonConfig is not null
             and c.jsonConfig like concat('%\"updatedBy\":\"', :updatedBy, '\"%')
           order by c.updatedAt desc
           """)
    List<SystemConfig> findByUpdatedByInJsonAndUpdatedAtAfterOrderByUpdatedAtDesc(
            @Param("updatedBy") String updatedBy,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    long deleteByCreatedAtBefore(LocalDateTime cutoff);

}