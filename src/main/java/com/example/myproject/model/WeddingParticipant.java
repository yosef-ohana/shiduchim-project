package com.example.myproject.model;

import com.example.myproject.model.enums.WeddingParticipantRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "wedding_participants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wedding_participant", columnNames = {"wedding_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_wp_wedding", columnList = "wedding_id"),
                @Index(name = "idx_wp_user", columnList = "user_id"),
                @Index(name = "idx_wp_wedding_blocked", columnList = "wedding_id, blocked"),
                @Index(name = "idx_wp_wedding_left", columnList = "wedding_id, left_at"),
                // Useful combined filter for "active list" queries
                @Index(name = "idx_wp_wedding_active", columnList = "wedding_id, blocked, left_at")
        }
)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WeddingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic lock to prevent race conditions (double-join / double-leave etc.)
     */
    @Version
    private Long version;

    // ---- Relations
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wedding_id", nullable = false)
    @JsonIgnoreProperties({"participants", "coOwners"}) // prevents accidental recursion in some serializers
    private Wedding wedding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"weddings", "matches"}) // keep it safe; adapt to your User fields if needed
    private User user;

    // ---- Participation lifecycle
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // ---- Role in wedding
    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_wedding", nullable = false, length = 32)
    private WeddingParticipantRole roleInWedding = WeddingParticipantRole.PARTICIPANT;

    // ---- Block scoped-to-wedding (for “remove + block”)
    @Column(name = "blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_by_user_id")
    private Long blockedByUserId;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    // ---- Optional: lightweight heartbeat (future)
    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    // ---- Audit
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    public WeddingParticipant() {}

    public WeddingParticipant(Wedding wedding, User user) {
        this.wedding = wedding;
        this.user = user;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (joinedAt == null) joinedAt = now;
        if (roleInWedding == null) roleInWedding = WeddingParticipantRole.PARTICIPANT;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Convenience
    public boolean isActiveInWedding() {
        return !blocked && leftAt == null;
    }

    public boolean isOwnerOrCoOwner() {
        return roleInWedding == WeddingParticipantRole.OWNER || roleInWedding == WeddingParticipantRole.CO_OWNER;
    }

    // ---- Getters/Setters
    public Long getId() { return id; }
    public Long getVersion() { return version; }

    public Wedding getWedding() { return wedding; }
    public void setWedding(Wedding wedding) { this.wedding = wedding; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public LocalDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(LocalDateTime leftAt) { this.leftAt = leftAt; }

    public WeddingParticipantRole getRoleInWedding() { return roleInWedding; }
    public void setRoleInWedding(WeddingParticipantRole roleInWedding) {
        this.roleInWedding = (roleInWedding == null) ? WeddingParticipantRole.PARTICIPANT : roleInWedding;
    }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public LocalDateTime getBlockedAt() { return blockedAt; }
    public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }

    public Long getBlockedByUserId() { return blockedByUserId; }
    public void setBlockedByUserId(Long blockedByUserId) { this.blockedByUserId = blockedByUserId; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public LocalDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Long getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(Long updatedByUserId) { this.updatedByUserId = updatedByUserId; }
}