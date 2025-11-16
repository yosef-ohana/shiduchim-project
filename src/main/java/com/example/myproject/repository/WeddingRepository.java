package com.example.myproject.repository;

import com.example.myproject.model.Wedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface WeddingRepository extends JpaRepository<Wedding, Long> {

    // ✅ בדיקה אם כבר קיימת חתונה עם אותו שם ותאריך
    boolean existsByNameAndDate(String name, LocalDateTime date);
}
