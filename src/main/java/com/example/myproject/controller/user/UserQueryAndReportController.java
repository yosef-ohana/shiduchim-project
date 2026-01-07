package com.example.myproject.controller.user;

import com.example.myproject.model.User;
import com.example.myproject.model.UserReport;
import com.example.myproject.model.enums.ReportType;
import com.example.myproject.service.MatchService;
import com.example.myproject.service.UserReportService;
import com.example.myproject.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/user/query-and-report")
@PreAuthorize("hasRole('USER')")
public class UserQueryAndReportController {

    private final UserRepository userRepository;
    private final UserReportService userReportService;
    private final MatchService matchService;

    public UserQueryAndReportController(
            UserRepository userRepository,
            UserReportService userReportService,
            MatchService matchService
    ) {
        this.userRepository = userRepository;
        this.userReportService = userReportService;
        this.matchService = matchService;
    }

    // =====================================================
    // Query
    // =====================================================

    /**
     * שליפת משתמש לפי id (Query בסיסי).
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<User> getUserById(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId,
            @PathVariable Long userId
    ) {
        requireActorUserId(principal, devUserId); // gate בסיסי
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        return ResponseEntity.ok(u);
    }

    /**
     * האם יש Match פעיל בין המשתמש לבין משתמש אחר.
     * Target בפועל בקוד: MatchService.activeMatchExistsBetween(Long user1Id, Long user2Id)
     */
    @GetMapping("/matches/active-exists")
    public ResponseEntity<Boolean> activeMatchExistsBetween(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId,
            @RequestParam Long otherUserId
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);
        boolean exists = matchService.activeMatchExistsBetween(actorUserId, otherUserId);
        return ResponseEntity.ok(exists);
    }

    // =====================================================
    // Report
    // =====================================================

    /**
     * יצירת Report על משתמש.
     * Target: UserReportService.createReport(Long reporterId, Long targetId, ReportType type, String description, String attachmentUrl)
     */
    @PostMapping("/reports")
    public ResponseEntity<UserReport> createReport(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId,
            @Valid @RequestBody CreateReportRequest req
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);

        UserReport report = userReportService.createReport(
                actorUserId,
                req.targetId,
                req.type,
                req.description,
                req.attachmentUrl
        );
        return ResponseEntity.ok(report);
    }

    /**
     * שליפת הדוחות שפתחתי.
     * Target: UserReportService.getReportsByReporter(Long reporterId)
     */
    @GetMapping("/reports/mine")
    public ResponseEntity<List<UserReport>> myReports(
            Principal principal,
            @RequestHeader(value = "X-User-Id", required = false) Long devUserId
    ) {
        Long actorUserId = requireActorUserId(principal, devUserId);
        return ResponseEntity.ok(userReportService.getReportsByReporter(actorUserId));
    }

    // =====================================================
    // DTOs
    // =====================================================

    public static class CreateReportRequest {
        @NotNull
        public Long targetId;

        @NotNull
        public ReportType type;

        @Size(max = 2000)
        public String description;

        @Size(max = 500)
        public String attachmentUrl;
    }

    private Long requireActorUserId(Principal principal, Long devUserId) {
        // Dev/Postman fallback (מומלץ למחוק בפרודקשן)
        if (devUserId != null) return devUserId;

        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthenticated");
        }
        try {
            return Long.parseLong(principal.getName().trim());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Authenticated but cannot resolve numeric userId from Principal.getName()"
            );
        }
    }
}
