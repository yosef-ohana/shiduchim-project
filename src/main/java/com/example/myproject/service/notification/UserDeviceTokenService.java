package com.example.myproject.service.notification;

import com.example.myproject.model.User;
import com.example.myproject.model.UserDeviceToken;
import com.example.myproject.model.enums.DeviceType;
import com.example.myproject.repository.UserDeviceTokenRepository;
import com.example.myproject.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserDeviceTokenService {

    private final UserDeviceTokenRepository tokenRepository;
    private final UserRepository userRepository;

    public UserDeviceTokenService(UserDeviceTokenRepository tokenRepository,
                                  UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    public UserDeviceToken registerToken(Long userId,
                                         String token,
                                         DeviceType deviceType,
                                         String deviceId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        String normalized = normalizeToken(token);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        UserDeviceToken entity = tokenRepository.findByToken(normalized).orElse(null);
        if (entity == null) {
            entity = new UserDeviceToken();
            entity.setToken(normalized);
        }

        entity.setUser(user);
        entity.setDeviceType(deviceType != null ? deviceType : DeviceType.UNKNOWN);
        entity.setDeviceId(trimToNull(deviceId));
        entity.setActive(true);
        entity.setLastSeenAt(LocalDateTime.now());

        return tokenRepository.save(entity);
    }

    public void deactivateToken(String token) {
        if (token == null || token.isBlank()) return;
        String normalized = token.trim();
        tokenRepository.findByToken(normalized).ifPresent(t -> {
            if (t.isActive()) {
                t.setActive(false);
                t.setLastSeenAt(LocalDateTime.now());
                tokenRepository.save(t);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<UserDeviceToken> getActiveTokens(Long userId) {
        if (userId == null) return List.of();
        return tokenRepository.findByUser_IdAndActiveTrueOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countActiveTokens(Long userId) {
        if (userId == null) return 0L;
        return tokenRepository.countByUser_IdAndActiveTrue(userId);
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token is required");
        String t = token.trim();
        if (t.length() < 10) throw new IllegalArgumentException("token looks invalid (too short)");
        if (t.length() > 512) throw new IllegalArgumentException("token too long");
        return t;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
