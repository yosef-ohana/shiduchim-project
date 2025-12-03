package com.example.myproject.model;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String environment;

    @Column(columnDefinition = "TEXT")
    private String jsonConfig;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getJsonConfig() {
        return jsonConfig;
    }

    public void setJsonConfig(String jsonConfig) {
        this.jsonConfig = jsonConfig;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}