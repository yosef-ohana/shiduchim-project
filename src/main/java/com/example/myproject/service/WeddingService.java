package com.example.myproject.service;

import com.example.myproject.model.User;
import com.example.myproject.model.Wedding;
import com.example.myproject.repository.UserRepository;
import com.example.myproject.repository.WeddingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WeddingService {

    private final WeddingRepository weddingRepository;
    private final UserRepository userRepository;

    @Autowired
    public WeddingService(WeddingRepository weddingRepository, UserRepository userRepository) {
        this.weddingRepository = weddingRepository;
        this.userRepository = userRepository;
    }

    // ✅ יצירת חתונה חדשה (כולל בדיקת כפילות)
    public Wedding createWedding(Wedding wedding) {
        boolean exists = weddingRepository.existsByNameAndDate(wedding.getName(), wedding.getDate());
        if (exists) {
            throw new IllegalStateException("Wedding with same name and date already exists!");
        }

        wedding.setCreatedAt(LocalDateTime.now());
        return weddingRepository.save(wedding);
    }

    // ✅ שליפת כל החתונות
    public List<Wedding> getAllWeddings() {
        return weddingRepository.findAll();
    }

    // ✅ שליפת חתונה לפי מזהה
    public Optional<Wedding> getWeddingById(Long id) {
        return weddingRepository.findById(id);
    }

    // ✅ הוספת משתמש לחתונה (מונע כפילויות)
    public String addUserToWedding(Long userId, Long weddingId) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Wedding> weddingOpt = weddingRepository.findById(weddingId);

        if (userOpt.isEmpty() || weddingOpt.isEmpty()) {
            return "User or wedding not found.";
        }

        User user = userOpt.get();
        Wedding wedding = weddingOpt.get();

        // אם המשתמש כבר משויך לחתונה זו
        if (user.getWedding() != null && user.getWedding().getId().equals(weddingId)) {
            return "User is already part of this wedding.";
        }

        // שיוך חדש
        user.setWedding(wedding);
        userRepository.save(user);

        return "User added to wedding successfully!";
    }

    // ✅ מחיקה של חתונה (כולל טיפול בקשרים למשתמשים)
    public String deleteWedding(Long id) {
        Optional<Wedding> weddingOpt = weddingRepository.findById(id);
        if (weddingOpt.isEmpty()) {
            return "Wedding not found.";
        }

        Wedding wedding = weddingOpt.get();

        // ביטול שיוך למשתמשים (כדי למנוע שגיאות של FK)
        List<User> users = userRepository.findByWeddingId(id);
        for (User u : users) {
            u.setWedding(null);
            userRepository.save(u);
        }

        weddingRepository.delete(wedding);
        return "Wedding deleted successfully (and users detached).";
    }
}
