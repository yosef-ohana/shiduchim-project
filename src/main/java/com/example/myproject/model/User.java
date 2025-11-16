package com.example.myproject.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "USERS")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ פרטים בסיסיים
    private String name;
    private String email;
    private String phone;
    private String city;
    private String gender;
    private Integer age;
    private Integer religiousLevel;

    // ✅ אימות
    private boolean verified = false;
    private String verificationCode;

    // ✅ מערכת גישה גלובלית
    private boolean globalAccessRequest = false;
    private boolean globalAccessApproved = false;

    // ✅ הרשאות אדמין
    private boolean isAdmin = false;

    // ✅ קשר לחתונה
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wedding_id")
    private Wedding wedding;

    // ✅ קשרים דו־כיווניים עם Match – מנוהלים בצד ה־User
    @OneToMany(mappedBy = "user1", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "user1-matches")
    private List<Match> matchesInitiated;

    @OneToMany(mappedBy = "user2", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "user2-matches")
    private List<Match> matchesReceived;

    // ===== Constructors =====
    public User() {}

    public User(Long id, String name, String email, String phone, String city, String gender,
                Integer age, Integer religiousLevel, boolean verified, String verificationCode,
                boolean globalAccessRequest, boolean globalAccessApproved, boolean isAdmin, Wedding wedding) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.city = city;
        this.gender = gender;
        this.age = age;
        this.religiousLevel = religiousLevel;
        this.verified = verified;
        this.verificationCode = verificationCode;
        this.globalAccessRequest = globalAccessRequest;
        this.globalAccessApproved = globalAccessApproved;
        this.isAdmin = isAdmin;
        this.wedding = wedding;
    }

    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Integer getReligiousLevel() { return religiousLevel; }
    public void setReligiousLevel(Integer religiousLevel) { this.religiousLevel = religiousLevel; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public boolean isGlobalAccessRequest() { return globalAccessRequest; }
    public void setGlobalAccessRequest(boolean globalAccessRequest) { this.globalAccessRequest = globalAccessRequest; }

    public boolean isGlobalAccessApproved() { return globalAccessApproved; }
    public void setGlobalAccessApproved(boolean globalAccessApproved) { this.globalAccessApproved = globalAccessApproved; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public Wedding getWedding() { return wedding; }
    public void setWedding(Wedding wedding) { this.wedding = wedding; }

    public List<Match> getMatchesInitiated() { return matchesInitiated; }
    public void setMatchesInitiated(List<Match> matchesInitiated) { this.matchesInitiated = matchesInitiated; }

    public List<Match> getMatchesReceived() { return matchesReceived; }
    public void setMatchesReceived(List<Match> matchesReceived) { this.matchesReceived = matchesReceived; }
}
