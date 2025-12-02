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
}