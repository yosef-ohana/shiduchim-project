package com.example.myproject.repository;

import com.example.myproject.model.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {

    Optional<UserDeviceToken> findByToken(String token);

    List<UserDeviceToken> findByUser_IdAndActiveTrueOrderByUpdatedAtDesc(Long userId);

    long countByUser_IdAndActiveTrue(Long userId);
}
