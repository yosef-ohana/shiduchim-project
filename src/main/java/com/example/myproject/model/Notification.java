package com.example.myproject.model;

import com.example.myproject.model.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notification_type", columnList = "type"),
                @Index(name = "idx_notification_created_at", columnList = "created_at"),

                @Index(name = "idx_notification_related_user_id", columnList = "related_user_id"),
                @Index(name = "idx_notification_wedding_id", columnList = "wedding_id"),
                @Index(name = "idx_notification_match_id", columnList = "match_id"),
                @Index(name = "idx_notification_chat_message_id", columnList = "chat_message_id"),
                @Index(name = "idx_notification_category", columnList = "category"),
                @Index(name = "idx_notification_source", columnList = "source")
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "related_user_id")
    private Long relatedUserId;

    @Column(name = "wedding_id")
    private Long weddingId;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "chat_message_id")
    private Long chatMessageId;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String source;

    @Column(nullable = false)
    private int priorityLevel = 1;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Notification() {}

    public Notification(NotificationType type,
                        String title,
                        String message,
                        Long relatedUserId,
                        Long weddingId,
                        Long matchId,
                        Long chatMessageId,
                        String metadata,
                        String category,
                        String source,
                        int priorityLevel) {

        this.type = type;
        this.title = title;
        this.message = message;
        this.relatedUserId = relatedUserId;
        this.weddingId = weddingId;
        this.matchId = matchId;
        this.chatMessageId = chatMessageId;
        this.metadata = metadata;
        this.category = category;
        this.source = source;
        this.priorityLevel = priorityLevel;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Long getRelatedUserId() { return relatedUserId; }
    public void setRelatedUserId(Long relatedUserId) { this.relatedUserId = relatedUserId; }

    public Long getWeddingId() { return weddingId; }
    public void setWeddingId(Long weddingId) { this.weddingId = weddingId; }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Long getChatMessageId() { return chatMessageId; }
    public void setChatMessageId(Long chatMessageId) { this.chatMessageId = chatMessageId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public int getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(int priorityLevel) { this.priorityLevel = priorityLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}