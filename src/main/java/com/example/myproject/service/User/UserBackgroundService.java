package com.example.myproject.service.User;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.model.enums.BackgroundMode;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserBackgroundService
 *
 * אחראי על:
 * - שינוי רקע למצב חתונה / מאגר גלובלי / ברירת מחדל
 * - סנכרון backgroundWeddingId / activeBackgroundWeddingId
 * - שימוש ב-BackgroundMode enum כיחידת אמת
 *
 * עובד יחד עם UserWeddingContextService ולא מחליף אותו.
 */
@Service
@Transactional
public class UserBackgroundService {

    private final UserRepository userRepository;
    private final WeddingRepository weddingRepository;

    public UserBackgroundService(UserRepository userRepository,
                                 WeddingRepository weddingRepository) {
        this.userRepository = userRepository;
        this.weddingRepository = weddingRepository;
    }

    // =====================================================
    // 🔵 קביעת רקע חתונה (WEDDING MODE)
    // =====================================================

    public User applyWeddingBackground(Long userId, Long weddingId) {
        User user = getUserOrThrow(userId);
        Wedding wedding = weddingRepository.findById(weddingId)
                .orElseThrow(() -> new IllegalArgumentException("Wedding not found: " + weddingId));

        user.setBackgroundMode(BackgroundMode.WEDDING);
        user.setBackgroundWeddingId(wedding.getId());
        user.setActiveBackgroundWeddingId(wedding.getId());

        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 קביעת רקע גלובלי (GLOBAL MODE)
    // =====================================================

    public User applyGlobalBackground(Long userId) {
        User user = getUserOrThrow(userId);

        user.setBackgroundMode(BackgroundMode.GLOBAL);
        user.setBackgroundWeddingId(null);
        user.setActiveBackgroundWeddingId(null);

        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 החזרת רקע לברירת מחדל
    // =====================================================

    public User applyDefaultBackground(Long userId) {
        User user = getUserOrThrow(userId);

        user.setBackgroundMode(BackgroundMode.DEFAULT);
        user.setBackgroundWeddingId(null);
        user.setActiveBackgroundWeddingId(null);

        return userRepository.save(user);
    }

    // =====================================================
    // 🔵 עזר
    // =====================================================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}