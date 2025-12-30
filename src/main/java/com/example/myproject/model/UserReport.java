package com.example.myproject.model;

import com.example.myproject.model.enums.ReportStatus;
import com.example.myproject.model.enums.ReportType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_reports")
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // מי דיווח
    private Long reporterId;

    // על מי מדווחים
    private Long targetId;

    @Enumerated(EnumType.STRING)
    private ReportType type;  // SPAM / HARASSMENT / FAKE_PROFILE / INAPPROPRIATE_PHOTO / OTHER

    @Column(columnDefinition = "TEXT")
    private String description;

    // תמיכה עתידית בהעלאת קבצים
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.OPEN; // Open / InReview / Closed / Rejected

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    // מי טיפל בדיווח (אדמין) — אופציונלי
    private Long handledByAdminId;

    // הערות אדמין
    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    // ============================================================
    // ✅ Getters / Setters (FULL)
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public void setReporterId(Long reporterId) {
        this.reporterId = reporterId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public ReportType getType() {
        return type;
    }

    public void setType(ReportType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
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

    public Long getHandledByAdminId() {
        return handledByAdminId;
    }

    public void setHandledByAdminId(Long handledByAdminId) {
        this.handledByAdminId = handledByAdminId;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}