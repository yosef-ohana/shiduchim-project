package com.example.myproject.repository;

import com.example.myproject.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // חיפוש לפי אימייל
    Optional<User> findByEmail(String email);

    // חיפוש לפי קוד אימות
    Optional<User> findByVerificationCode(String verificationCode);

    // שליפת כל המשתמשים לפי מזהה חתונה (FK)
    List<User> findByWeddingId(Long weddingId);
}
