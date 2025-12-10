package com.example.myproject.repository;

import com.example.myproject.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    // ============================================================
    // 🔵 1. קונפיג עדכני לפי environment
    // ============================================================

    Optional<SystemConfig> findTopByEnvironmentOrderByCreatedAtDesc(String environment);

    List<SystemConfig> findByEnvironmentOrderByCreatedAtDesc(String environment);

    Optional<SystemConfig> findTopByEnvironmentIsNullOrderByCreatedAtDesc();

    List<SystemConfig> findByEnvironmentIsNullOrderByCreatedAtDesc();


    // ============================================================
    // 🔵 2. בדיקות קיום וספירה
    // ============================================================

    boolean existsByEnvironment(String environment);

    long countByEnvironment(String environment);


    // ============================================================
    // 🔵 3. טעינת הגדרות בקבוצות (Warmup / Dashboard)
    // ============================================================

    List<SystemConfig> findByEnvironmentIn(List<String> environments);


    // ============================================================
    // 🔵 4. תחזוקה / ניקוי לפי תאריכים
    // ============================================================

    List<SystemConfig> findByCreatedAtBefore(LocalDateTime time);

    List<SystemConfig> findByUpdatedAtAfter(LocalDateTime time);

    List<SystemConfig> findByEnvironmentAndCreatedAtBetween(
            String environment,
            LocalDateTime start,
            LocalDateTime end
    );


    // ============================================================
    // 🔵 5. קונפיג אחרון בכלל המערכת
    // ============================================================

    Optional<SystemConfig> findTopByOrderByCreatedAtDesc();


    // ============================================================
    // 🔵 6. שאילתות לפי key (SystemRules §5)
    // ============================================================

    Optional<SystemConfig> findTopByConfigKeyOrderByCreatedAtDesc(String configKey);

    List<SystemConfig> findByConfigKeyOrderByCreatedAtDesc(String configKey);

    boolean existsByConfigKey(String configKey);

    List<SystemConfig> findByConfigKeyIn(List<String> keys);


    // ============================================================
    // 🔵 7. שאילתות לפי category (notifications / limits / ai ...)
    // ============================================================

    List<SystemConfig> findByCategoryOrderByCreatedAtDesc(String category);

    Optional<SystemConfig> findTopByCategoryOrderByCreatedAtDesc(String category);

    List<SystemConfig> findByCategoryInOrderByCreatedAtDesc(List<String> categories);


    // ============================================================
    // 🔵 8. key + environment override (SystemRules §6)
    // ============================================================

    Optional<SystemConfig> findTopByEnvironmentAndConfigKeyOrderByCreatedAtDesc(
            String environment,
            String configKey
    );

    List<SystemConfig> findByEnvironmentAndConfigKeyOrderByCreatedAtDesc(
            String environment,
            String configKey
    );


    // ============================================================
    // 🔵 9. Active Config Only (SystemConfig.active = true)
    // ============================================================

    List<SystemConfig> findByActiveTrue();

    List<SystemConfig> findByEnvironmentAndActiveTrue(String environment);

    Optional<SystemConfig> findTopByConfigKeyAndActiveTrueOrderByCreatedAtDesc(String configKey);

    Optional<SystemConfig> findTopByCategoryAndActiveTrueOrderByCreatedAtDesc(String category);

    // 🆕 גלובל Active בלבד (environment = null)
    List<SystemConfig> findByEnvironmentIsNullAndActiveTrueOrderByCreatedAtDesc();

    Optional<SystemConfig> findTopByEnvironmentIsNullAndActiveTrueOrderByCreatedAtDesc();


    // ============================================================
    // 🔵 10. Effective Date — קונפיג עתידי / נכנס לתוקף (SystemRules §17)
    // ============================================================

    List<SystemConfig> findByEffectiveAtBefore(LocalDateTime time);

    List<SystemConfig> findByEffectiveAtAfter(LocalDateTime time);

    Optional<SystemConfig> findTopByConfigKeyAndEffectiveAtBeforeOrderByEffectiveAtDesc(
            String configKey,
            LocalDateTime now
    );

    // 🆕 Active + Effective (מה שבפועל בתוקף עכשיו לכל המערכת)
    List<SystemConfig> findByActiveTrueAndEffectiveAtBefore(LocalDateTime time);

    List<SystemConfig> findByEnvironmentAndActiveTrueAndEffectiveAtBefore(
            String environment,
            LocalDateTime time
    );

    Optional<SystemConfig> findTopByConfigKeyAndActiveTrueAndEffectiveAtBeforeOrderByEffectiveAtDesc(
            String configKey,
            LocalDateTime now
    );

    Optional<SystemConfig> findTopByEnvironmentAndConfigKeyAndActiveTrueAndEffectiveAtBeforeOrderByEffectiveAtDesc(
            String environment,
            String configKey,
            LocalDateTime now
    );


    // ============================================================
    // 🔵 11. Auditing — מי עדכן מה (Admin Dashboard)
    // ============================================================

    List<SystemConfig> findByUpdatedByOrderByUpdatedAtDesc(String updatedBy);

    List<SystemConfig> findByUpdatedByAndUpdatedAtAfterOrderByUpdatedAtDesc(
            String updatedBy,
            LocalDateTime since
    );


    // ============================================================
    // 🔵 12. שאילתות משולבות (Category + Key + Active + Env)
    // ============================================================

    List<SystemConfig> findByCategoryAndEnvironmentAndActiveTrueOrderByCreatedAtDesc(
            String category,
            String environment
    );

    List<SystemConfig> findByConfigKeyAndCategoryAndActiveTrueOrderByCreatedAtDesc(
            String configKey,
            String category
    );

    // 🆕 רשימת קונפיגים Active לפי קטגוריה (ללא סינון Environment)
    List<SystemConfig> findByCategoryAndActiveTrueOrderByCreatedAtDesc(String category);

    // 🆕 קונפיג Active אחרון לפי קטגוריה + Environment
    Optional<SystemConfig> findTopByCategoryAndEnvironmentAndActiveTrueOrderByCreatedAtDesc(
            String category,
            String environment
    );


    // ============================================================
    // 🔵 13. שאילתות ל־SystemRules Load (טעינה מרוכזת)
    // ============================================================

    List<SystemConfig> findByActiveTrueOrderByCreatedAtDesc();

    List<SystemConfig> findByEnvironmentAndActiveTrueOrderByCreatedAtDesc(String environment);

    List<SystemConfig> findByCategoryInAndActiveTrueOrderByCreatedAtDesc(List<String> categories);
}