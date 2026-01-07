package com.example.myproject.controller.user;

import com.example.myproject.model.User;
import com.example.myproject.service.User.UserGlobalPoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/global-pool")
public class UserGlobalPoolController {

    private final UserGlobalPoolService globalPoolService;

    public UserGlobalPoolController(UserGlobalPoolService globalPoolService) {
        this.globalPoolService = globalPoolService;
    }

    // =====================================================
    // 🔵 Join / Approve / Reject / Remove
    // =====================================================

    /**
     * משתמש מבקש להצטרף למאגר גלובלי (REQUESTED)
     * NOTE: כאן אנחנו עובדים עם userId מפורש כדי לא להכניס תלות ב-AuthService.
     */
    @PostMapping("/request/{userId}")
    public ResponseEntity<User> requestJoinGlobal(@PathVariable Long userId) {
        return ResponseEntity.ok(globalPoolService.requestGlobalAccess(userId));
    }

    /**
     * אישור הצטרפות (לרוב Admin/Owner/System)
     */
    @PostMapping("/approve/{userId}")
    public ResponseEntity<User> approve(@PathVariable Long userId) {
        return ResponseEntity.ok(globalPoolService.approveGlobalAccess(userId));
    }

    /**
     * דחייה: תואם בדיוק לסרביס (2 פרמטרים)
     * keepRequestFlag ברירת מחדל: false
     */
    @PostMapping("/reject/{userId}")
    public ResponseEntity<User> reject(
            @PathVariable Long userId,
            @RequestParam(name = "keepRequestFlag", required = false, defaultValue = "false") boolean keepRequestFlag
    ) {
        return ResponseEntity.ok(globalPoolService.rejectGlobalAccess(userId, keepRequestFlag));
    }

    /**
     * הסרה מהמאגר (NONE)
     */
    @PostMapping("/remove/{userId}")
    public ResponseEntity<User> remove(@PathVariable Long userId) {
        return ResponseEntity.ok(globalPoolService.removeFromGlobalPool(userId));
    }

    // =====================================================
    // 🔵 Queries
    // =====================================================

    /**
     * שליפת משתמשים מהמאגר הגלובלי עם פילטרים.
     *
     * לוגיקת בחירה (רק לפי מתודות שקיימות בסרביס שלך):
     * 1) אם aiMinScore קיים -> findGlobalWithAiBoost
     * 2) אם יש gender+area+religiousLevel (+age) -> findGlobalAdvanced
     * 3) אם יש gender + age-range -> findGlobalByGenderAndAge
     * 4) אם יש area + age-range -> findGlobalByAreaAndAge
     * 5) אם יש רק gender -> findGlobalByGender
     * 6) אחרת -> findAllInGlobalPool
     *
     * הערה: age מקבלים כ-Integer כדי שלא תקבל "always false" כמו בצילום.
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> list(
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "area", required = false) String area,
            @RequestParam(name = "religiousLevel", required = false) String religiousLevel,
            @RequestParam(name = "minAge", required = false) Integer minAge,
            @RequestParam(name = "maxAge", required = false) Integer maxAge,
            @RequestParam(name = "age", required = false) Integer age, // נוח לקונטרולרים ישנים / UI
            @RequestParam(name = "aiMinScore", required = false) Double aiMinScore
    ) {

        // אם הגיע age יחיד -> נהפוך אותו לטווח exact
        if (age != null) {
            minAge = age;
            maxAge = age;
        }

        // 1) AI boost
        if (aiMinScore != null) {
            return ResponseEntity.ok(globalPoolService.findGlobalWithAiBoost(aiMinScore));
        }

        boolean hasAgeRange = (minAge != null && maxAge != null);
        boolean hasGender = (gender != null && !gender.isBlank());
        boolean hasArea = (area != null && !area.isBlank());
        boolean hasReligious = (religiousLevel != null && !religiousLevel.isBlank());

        // 2) Advanced (המתודה דורשת את כל הפרמטרים כולל גיל, לפי הסרביס שלך)
        if (hasGender && hasArea && hasReligious) {
            // אם אין גיל, ניפול אחורה לליסט בלי advanced (כי בסרביס אין advanced בלי גיל)
            if (hasAgeRange) {
                return ResponseEntity.ok(
                        globalPoolService.findGlobalAdvanced(gender, area, religiousLevel, minAge, maxAge)
                );
            }
        }

        // 3) Gender + Age range
        if (hasGender && hasAgeRange) {
            return ResponseEntity.ok(globalPoolService.findGlobalByGenderAndAge(gender, minAge, maxAge));
        }

        // 4) Area + Age range
        if (hasArea && hasAgeRange) {
            return ResponseEntity.ok(globalPoolService.findGlobalByAreaAndAge(area, minAge, maxAge));
        }

        // 5) Gender only
        if (hasGender) {
            return ResponseEntity.ok(globalPoolService.findGlobalByGender(gender));
        }

        // 6) No filters
        return ResponseEntity.ok(globalPoolService.findAllInGlobalPool());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> count() {
        return ResponseEntity.ok(globalPoolService.countGlobalPoolUsers());
    }
}
