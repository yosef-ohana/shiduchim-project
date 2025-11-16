package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // שני המשתמשים בהתאמה
    @ManyToOne
    @JoinColumn(name = "user_id_1")
    private User user1;

    @ManyToOne
    @JoinColumn(name = "user_id_2")
    private User user2;

    // ניקוד ההתאמה
    private Double matchScore;

    // אישור משתמש 1
    private boolean user1Approved = false;

    // אישור משתמש 2
    private boolean user2Approved = false;

    // אישור הדדי סופי
    private boolean mutualApproved = false;

    // תאריך יצירה
    private LocalDateTime createdAt = LocalDateTime.now();

    public Match() {}

    public Match(User user1, User user2, Double matchScore) {
        this.user1 = user1;
        this.user2 = user2;
        this.matchScore = matchScore;
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser1() { return user1; }
    public void setUser1(User user1) { this.user1 = user1; }

    public User getUser2() { return user2; }
    public void setUser2(User user2) { this.user2 = user2; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public boolean isUser1Approved() { return user1Approved; }
    public void setUser1Approved(boolean user1Approved) { this.user1Approved = user1Approved; }

    public boolean isUser2Approved() { return user2Approved; }
    public void setUser2Approved(boolean user2Approved) { this.user2Approved = user2Approved; }

    public boolean isMutualApproved() { return mutualApproved; }
    public void setMutualApproved(boolean mutualApproved) { this.mutualApproved = mutualApproved; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
