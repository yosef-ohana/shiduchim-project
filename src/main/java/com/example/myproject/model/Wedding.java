package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "weddings")
public class Wedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ שם החתונה או קוד ייחודי (נניח "חתונת יעקב ודנה")
    @Column(nullable = false)
    private String name;

    // ✅ מיקום האירוע
    private String location;

    // ✅ תאריך ושעה של החתונה
    private LocalDateTime date;

    // ✅ מי יצר את החתונה (בדר"כ אדמין)
    private String createdBy;

    // ✅ תאריך יצירה של הרשומה
    private LocalDateTime createdAt = LocalDateTime.now();

    // ✅ קשר דו־כיווני: חתונה אחת -> משתמשים רבים
    @OneToMany(mappedBy = "wedding", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<User> users = new HashSet<>();

    // ✅ Constructors
    public Wedding() {}

    public Wedding(String name, String location, LocalDateTime date, String createdBy) {
        this.name = name;
        this.location = location;
        this.date = date;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    // ✅ מניעת כפילויות — לפי שם ותאריך (אם יש שתי חתונות עם אותו שם ותאריך, זו אותה חתונה)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Wedding)) return false;
        Wedding other = (Wedding) o;
        return name.equalsIgnoreCase(other.name)
                && date != null
                && other.date != null
                && date.toLocalDate().equals(other.date.toLocalDate());
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode() + (date != null ? date.getDayOfYear() : 0);
    }

    // ✅ Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Set<User> getUsers() { return users; }
    public void setUsers(Set<User> users) { this.users = users; }

    // ✅ הוספת משתמש לחתונה (ללא כפילויות)
    public void addUser(User user) {
        if (user != null && !users.contains(user)) {
            users.add(user);
            user.setWedding(this);
        }
    }
}
