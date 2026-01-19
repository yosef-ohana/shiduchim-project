package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.model.enums.WeddingMode;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class UserWeddingContextService {

    private final UserRepository userRepository;
    private final WeddingRepository weddingRepository;
    private final UserSettingsService userSettingsService;


    public UserWeddingContextService(UserRepository userRepository,
                                     WeddingRepository weddingRepository,
                                     UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.weddingRepository = weddingRepository;
        this.userSettingsService = userSettingsService;
    }


    // =====================================================
    // 🔵 כניסה לחתונה לפי accessCode
    // abilities 11–21, 31–35, 40
    // =====================================================

    public User enterWedding(Long userId, String accessCode) {
        User user = getUserOrThrow(userId);
        Wedding wedding = weddingRepository.findByAccessCode(accessCode)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found for access code"));

        LocalDateTime now = LocalDateTime.now();

        // עדכון first/last/history
        if (user.getFirstWeddingId() == null) {
            user.setFirstWeddingId(wedding.getId());
        }
        user.setLastWeddingId(wedding.getId());

        List<Long> history = user.getWeddingsHistory();
        if (history == null) {
            history = new ArrayList<>();
        }
        if (!history.contains(wedding.getId())) {
            history.add(wedding.getId());
        }
        user.setWeddingsHistory(history);

        // מצב חתונה
        user.setActiveWeddingId(wedding.getId());
        user.setWeddingEntryAt(now);
        user.setWeddingExitAt(null);
        user.setWeddingMode(WeddingMode.WEDDING);
        user.setCanViewWedding(true);

        // רקע חתונה
        user.setBackgroundWeddingId(wedding.getId());
        user.setBackgroundMode(BackgroundMode.WEDDING);
        user.setActiveBackgroundWeddingId(wedding.getId());

        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 יציאה מחתונה
    // =====================================================

    public User exitWedding(Long userId) {
        User user = getUserOrThrow(userId);

        Long prevWeddingId = user.getActiveWeddingId();

        user.setWeddingMode(WeddingMode.PAST_WEDDING);
        user.setLastWeddingId(prevWeddingId);
        user.setActiveWeddingId(null);

        // משאירים את שאר ההתנהגות כמו שהיה (לא נוגעים מעבר לנדרש)
        user.setWeddingExitAt(LocalDateTime.now());
        user.setBackgroundWeddingId(null);
        user.setBackgroundMode(BackgroundMode.GLOBAL);

        User saved = userRepository.save(user);

        // אם החתונה הסתיימה בפועל — נועלים (מקור אמת: UserSettingsService)
        if (prevWeddingId != null) {
            Wedding w = weddingRepository.findById(prevWeddingId).orElse(null);
            if (w != null && w.isFinished(LocalDateTime.now())) {
                userSettingsService.lockAfterWedding(userId, null, "Wedding ended");
            }
        }

        return saved;
    }


    // =====================================================
    // 🔵 שינוי מצב רקע (GLOBAL / WEDDING / DEFAULT)
    // =====================================================

    public User setBackgroundMode(Long userId, BackgroundMode mode, Long weddingId) {
        User user = getUserOrThrow(userId);

        if (mode == BackgroundMode.WEDDING && weddingId != null) {
            user.setBackgroundWeddingId(weddingId);
            user.setActiveBackgroundWeddingId(weddingId);
        } else if (mode != BackgroundMode.WEDDING) {
            user.setBackgroundWeddingId(null);
        }

        user.setBackgroundMode(mode != null ? mode : BackgroundMode.DEFAULT);
        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 החלפת WeddingMode (מצב חתונה/מאגר גלובלי)
    // =====================================================

    public User switchToWeddingMode(Long userId, Long weddingId) {
        User user = getUserOrThrow(userId);
        user.setWeddingMode(WeddingMode.WEDDING);
        user.setActiveWeddingId(weddingId);
        user.setBackgroundMode(BackgroundMode.WEDDING);
        user.setBackgroundWeddingId(weddingId);
        user.setActiveBackgroundWeddingId(weddingId);
        return userRepository.save(user);
    }

    public User switchToGlobalMode(Long userId) {
        User user = getUserOrThrow(userId);
        user.setWeddingMode(WeddingMode.GLOBAL);
        user.setBackgroundMode(BackgroundMode.GLOBAL);
        user.setBackgroundWeddingId(null);
        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 שאילתות חתונה (לשימוש בדוחות/סטטיסטיקות)
    // =====================================================

    public List<User> findUsersInWedding(Long weddingId) {
        return userRepository.findByActiveWeddingId(weddingId);
    }

    public long countUsersInWedding(Long weddingId) {
        return userRepository.countByActiveWeddingId(weddingId);
    }

    public List<User> findUsersByLastWedding(Long weddingId) {
        return userRepository.findByLastWeddingId(weddingId);
    }

    public List<User> findUsersByFirstWedding(Long weddingId) {
        return userRepository.findByFirstWeddingId(weddingId);
    }

    public List<User> findUsersByWeddingHistory(Long weddingId) {
        return userRepository.findByWeddingsHistoryContaining(weddingId);
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}