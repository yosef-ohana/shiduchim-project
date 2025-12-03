package com.example.myproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String keyName;

    @Column(nullable = false, length = 2000)
    private String value;

    @Column(length = 300)
    private String description;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}