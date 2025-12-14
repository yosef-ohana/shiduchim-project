package com.example.myproject.dto;

import com.example.myproject.model.Match;
import com.example.myproject.model.enums.MatchStatus;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.WeddingMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DTO אחיד לפידבק ל-UI + רמזים למערכת התראות/אודיט, עבור כל שינוי מצב ב-Match.
 *
 * ✅ מטרות:
 * 1) Frontend: לקבל טקסטים מדויקים למבצע הפעולה ולצד השני + uiCode יציב.
 * 2) NotificationService: לקבל "suggestedNotificationForOther" + otherUserId.
 * 3) Audit/System: לקבל auditAction + sourceModule + מצב לפני/אחרי.
 * 4) System Layer: אפשר להוסיף שדות/flags בלי לשבור API.
 */
public final class MatchActionFeedback {

    // ============================================================
    // 🔵 Enums UI (גנרי) — כדי לכסות את כל המקרים בלי לתלות ב-enums אחרים
    // ============================================================

    public enum UiSeverity { INFO, SUCCESS, WARNING, ERROR }

    /**
     * UI intent כללי: האם צריך להזיז בין רשימות/לעדכן מסכים וכו'
     * (לא מחייב את ה-Frontend, רק רמז מסודר)
     */
    public enum UiEffect {
        NONE,
        REFRESH_LISTS,        // לרענן רשימות (פיד/התאמות/צ'אטים)
        MOVE_TO_ARCHIVE,      // להעביר לארכיון UI
        REMOVE_FROM_FEED,     // להסיר מהמאגר הנוכחי (חסימה/מחיקה/נעילה)
        OPEN_CHAT,            // לפתוח/להציג צ'אט
        CLOSE_CHAT,           // לסגור/לחסום צ'אט
        SHOW_BANNER           // להציג הודעת באנר/טוסט משמעותית
    }

    // ============================================================
    // 🔵 Fields
    // ============================================================

    private final Match match;

    private final Long actorUserId;
    private final Long otherUserId;

    private final String messageForActor;
    private final String messageForOtherSide;

    /**
     * קוד יציב ל-Frontend: ACTION_MODE_SOURCE
     * לדוגמה: MATCH_FROZEN_WEDDING_MATCH_SERVICE
     */
    private final String uiCode;

    private final UiSeverity severity;
    private final UiEffect effect;

    /**
     * רמז ל-NotificationService (לא שולח בפועל כאן)
     */
    private final NotificationType suggestedNotificationForOther;

    /**
     * Action לאודיט/סיסטם
     */
    private final SystemActionType auditAction;

    private final WeddingMode mode;
    private final SystemModule sourceModule;

    /**
     * Snapshot שימושי ל-UI/System (לפני/אחרי)
     */
    private final MatchStatus beforeStatus;
    private final MatchStatus afterStatus;

    private final boolean becameMutualNow;
    private final boolean mutualBrokenNow;

    /**
     * שדות הרחבה גנריים (למשל reason / contextWeddingId / label וכו')
     */
    private final Map<String, String> extras;

    // ============================================================
    // 🔵 Constructor + Getters
    // ============================================================

    public MatchActionFeedback(Match match,
                               Long actorUserId,
                               Long otherUserId,
                               String messageForActor,
                               String messageForOtherSide,
                               String uiCode,
                               UiSeverity severity,
                               UiEffect effect,
                               NotificationType suggestedNotificationForOther,
                               SystemActionType auditAction,
                               WeddingMode mode,
                               SystemModule sourceModule,
                               MatchStatus beforeStatus,
                               MatchStatus afterStatus,
                               boolean becameMutualNow,
                               boolean mutualBrokenNow,
                               Map<String, String> extras) {

        this.match = match;
        this.actorUserId = actorUserId;
        this.otherUserId = otherUserId;
        this.messageForActor = messageForActor;
        this.messageForOtherSide = messageForOtherSide;
        this.uiCode = uiCode;
        this.severity = severity != null ? severity : UiSeverity.INFO;
        this.effect = effect != null ? effect : UiEffect.NONE;
        this.suggestedNotificationForOther = suggestedNotificationForOther;
        this.auditAction = auditAction;
        this.mode = mode != null ? mode : WeddingMode.NONE;
        this.sourceModule = sourceModule != null ? sourceModule : SystemModule.MATCH_SERVICE;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.becameMutualNow = becameMutualNow;
        this.mutualBrokenNow = mutualBrokenNow;
        this.extras = extras != null ? Collections.unmodifiableMap(new LinkedHashMap<>(extras)) : Collections.emptyMap();
    }

    public Match getMatch() { return match; }
    public Long getActorUserId() { return actorUserId; }
    public Long getOtherUserId() { return otherUserId; }

    public String getMessageForActor() { return messageForActor; }
    public String getMessageForOtherSide() { return messageForOtherSide; }

    public String getUiCode() { return uiCode; }
    public UiSeverity getSeverity() { return severity; }
    public UiEffect getEffect() { return effect; }

    public NotificationType getSuggestedNotificationForOther() { return suggestedNotificationForOther; }
    public SystemActionType getAuditAction() { return auditAction; }

    public WeddingMode getMode() { return mode; }
    public SystemModule getSourceModule() { return sourceModule; }

    public MatchStatus getBeforeStatus() { return beforeStatus; }
    public MatchStatus getAfterStatus() { return afterStatus; }

    public boolean isBecameMutualNow() { return becameMutualNow; }
    public boolean isMutualBrokenNow() { return mutualBrokenNow; }

    public Map<String, String> getExtras() { return extras; }

    // ============================================================
    // 🔵 Factory: בניית Feedback אחיד לכל פעולה
    // ============================================================

    /**
     * Builder מרכזי: מכסה את כל פעולות ה-Match (כולל הרחבות עתידיות)
     * בלי לשבור את ה-API.
     *
     * שימוש מומלץ מתוך MatchService:
     *   MatchActionFeedback fb = MatchActionFeedback.build(match, actorUserId, action, mode, sourceModule, beforeStatus, reason, becameMutual, mutualBroken);
     */
    public static MatchActionFeedback build(Match match,
                                            Long actorUserId,
                                            SystemActionType action,
                                            WeddingMode mode,
                                            SystemModule sourceModule,
                                            MatchStatus beforeStatus,
                                            String reason,
                                            boolean becameMutualNow,
                                            boolean mutualBrokenNow) {

        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(actorUserId, "actorUserId is required");
        Objects.requireNonNull(action, "action is required");

        Long otherUserId = resolveOtherUserId(match, actorUserId);
        MatchStatus afterStatus = match.getStatus();

        // uiCode יציב
        String uiCode = action.name() + "_" +
                (mode != null ? mode.name() : WeddingMode.NONE.name()) + "_" +
                (sourceModule != null ? sourceModule.name() : SystemModule.MATCH_SERVICE.name());

        // Context flags
        boolean weddingContext = (mode == WeddingMode.WEDDING);
        boolean globalContext = (mode == WeddingMode.GLOBAL);
        boolean pastWeddingContext = (mode == WeddingMode.PAST_WEDDING);

        boolean isAdminOrSystem = isAdminOrSystem(sourceModule);

        // Defaults
        String actorMsg = "בוצעה פעולה על ההתאמה.";
        String otherMsg = "בוצעה פעולה על ההתאמה.";
        NotificationType notif = null;

        UiSeverity severity = UiSeverity.INFO;
        UiEffect effect = UiEffect.REFRESH_LISTS;

        Map<String, String> extras = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            extras.put("reason", reason.trim());
        }
        if (beforeStatus != null) extras.put("beforeStatus", beforeStatus.name());
        if (afterStatus != null) extras.put("afterStatus", afterStatus.name());
        extras.put("action", action.name());
        extras.put("mode", mode != null ? mode.name() : WeddingMode.NONE.name());
        extras.put("sourceModule", sourceModule != null ? sourceModule.name() : SystemModule.MATCH_SERVICE.name());

        // ============================================================
        // 🔵 מיפוי הודעות לפי פעולה + הקשר (Wedding/Global/Past/Admin)
        // ============================================================

        switch (action) {

            // =========================
            // Freeze / Unfreeze
            // =========================
            case MATCH_FROZEN -> {
                severity = UiSeverity.WARNING;
                effect = UiEffect.REMOVE_FROM_FEED;

                if (isAdminOrSystem) {
                    actorMsg = "בוצעה הקפאה (מערכת/אדמין).";
                    otherMsg = "ההתאמה הוקפאה (מערכת/אדמין).";
                } else if (weddingContext) {
                    actorMsg = "הקפאת את ההתאמה במאגר החתונה. לא תראו אחד את השני במאגר החתונה עד שתבטל הקפאה.";
                    otherMsg = "ההתאמה הוקפאה במאגר החתונה. לא תראו את המשתמש במאגר החתונה כרגע.";
                } else if (globalContext) {
                    actorMsg = "הקפאת את ההתאמה במאגר הגלובלי. ההתאמה תושהה עד שתבטל הקפאה.";
                    otherMsg = "ההתאמה הוקפאה במאגר הגלובלי. ייתכן שהצ'אט/הצגה יושהו עד לביטול הקפאה.";
                } else if (pastWeddingContext) {
                    actorMsg = "הקפאת את ההתאמה לאחר סיום החתונה. ההתאמה תושהה עד שתבטל הקפאה.";
                    otherMsg = "ההתאמה הוקפאה לאחר סיום החתונה.";
                } else {
                    actorMsg = "ההתאמה הוקפאה.";
                    otherMsg = "ההתאמה הוקפאה.";
                }

                notif = NotificationType.MATCH_CLOSED; // רמז אפשרי
            }

            case MATCH_UNFROZEN -> {
                severity = UiSeverity.SUCCESS;
                effect = UiEffect.REFRESH_LISTS;

                if (isAdminOrSystem) {
                    actorMsg = "בוטלה הקפאה (מערכת/אדמין).";
                    otherMsg = "בוטלה הקפאה (מערכת/אדמין).";
                } else if (weddingContext) {
                    actorMsg = "ביטלת הקפאה במאגר החתונה. ההתאמה חוזרת להופיע לפי כללי החתונה.";
                    otherMsg = "בוטלה הקפאה במאגר החתונה. ההתאמה יכולה לחזור להופיע לפי הכללים.";
                } else if (globalContext) {
                    actorMsg = "ביטלת הקפאה במאגר הגלובלי. ההתאמה חוזרת לפעילות לפי הסטטוס.";
                    otherMsg = "בוטלה הקפאה במאגר הגלובלי. ההתאמה חוזרת לפעילות לפי הסטטוס.";
                } else if (pastWeddingContext) {
                    actorMsg = "ביטלת הקפאה לאחר סיום החתונה. ההתאמה חוזרת לפעילות לפי הסטטוס.";
                    otherMsg = "בוטלה הקפאה לאחר סיום החתונה.";
                } else {
                    actorMsg = "בוטלה הקפאה.";
                    otherMsg = "בוטלה הקפאה.";
                }

                notif = NotificationType.MATCH_CONFIRMED; // רמז אפשרי
            }

            // =========================
            // Block / Unblock
            // =========================
            case MATCH_BLOCKED -> {
                severity = UiSeverity.WARNING;
                effect = UiEffect.CLOSE_CHAT;

                if (isAdminOrSystem) {
                    actorMsg = "בוצעה חסימה (מערכת/אדמין).";
                    otherMsg = "ההתאמה נחסמה (מערכת/אדמין).";
                } else if (weddingContext) {
                    actorMsg = "חסמת את המשתמש בתוך מאגר החתונה. לא תראו אחד את השני במאגר החתונה.";
                    otherMsg = "נחסמת בתוך מאגר החתונה. לא תראה את המשתמש במאגר החתונה.";
                } else if (globalContext) {
                    actorMsg = "חסמת את המשתמש במאגר הגלובלי. ההתאמה תוסר מהרשימות והצ'אט ייחסם.";
                    otherMsg = "הגישה להתאמה נחסמה במאגר הגלובלי.";
                } else if (pastWeddingContext) {
                    actorMsg = "חסמת את המשתמש לאחר סיום החתונה.";
                    otherMsg = "הגישה להתאמה נחסמה לאחר סיום החתונה.";
                } else {
                    actorMsg = "חסימה בוצעה.";
                    otherMsg = "ההתאמה נחסמה.";
                }

                notif = NotificationType.MATCH_CLOSED;
            }

            case MATCH_UNBLOCKED -> {
                severity = UiSeverity.SUCCESS;
                effect = UiEffect.REFRESH_LISTS;

                if (isAdminOrSystem) {
                    actorMsg = "בוטלה חסימה (מערכת/אדמין).";
                    otherMsg = "בוטלה חסימה (מערכת/אדמין).";
                } else if (weddingContext) {
                    actorMsg = "ביטלת חסימה במאגר החתונה. ההתאמה חוזרת להופיע לפי כללי החתונה.";
                    otherMsg = "בוטלה חסימה במאגר החתונה. ההתאמה יכולה לחזור להופיע לפי הכללים.";
                } else if (globalContext) {
                    actorMsg = "ביטלת חסימה במאגר הגלובלי. ההתאמה יכולה לחזור לפעילות לפי הסטטוס.";
                    otherMsg = "בוטלה חסימה במאגר הגלובלי. ההתאמה יכולה לחזור לפעילות לפי הסטטוס.";
                } else if (pastWeddingContext) {
                    actorMsg = "ביטלת חסימה לאחר סיום החתונה.";
                    otherMsg = "בוטלה חסימה לאחר סיום החתונה.";
                } else {
                    actorMsg = "בוטלה חסימה.";
                    otherMsg = "בוטלה חסימה.";
                }

                notif = NotificationType.MATCH_CONFIRMED;
            }

            // =========================
            // Mutual Confirmed
            // =========================
            case MATCH_MUTUAL_CONFIRMED -> {
                severity = UiSeverity.SUCCESS;
                effect = UiEffect.SHOW_BANNER;

                if (weddingContext) {
                    actorMsg = "אישרתם הדדית — יש לכם התאמה! (מאגר החתונה)";
                    otherMsg = "אישרתם הדדית — יש לכם התאמה! (מאגר החתונה)";
                } else if (globalContext) {
                    actorMsg = "אישרתם הדדית — יש לכם התאמה! (מאגר גלובלי)";
                    otherMsg = "אישרתם הדדית — יש לכם התאמה! (מאגר גלובלי)";
                } else if (pastWeddingContext) {
                    actorMsg = "אישרתם הדדית — יש לכם התאמה! (לאחר החתונה)";
                    otherMsg = "אישרתם הדדית — יש לכם התאמה! (לאחר החתונה)";
                } else {
                    actorMsg = "אישרתם הדדית — יש לכם התאמה!";
                    otherMsg = "אישרתם הדדית — יש לכם התאמה!";
                }

                notif = NotificationType.MATCH_MUTUAL;
            }

            // =========================
            // Archive
            // =========================
            case MATCH_ARCHIVED -> {
                severity = UiSeverity.INFO;
                effect = UiEffect.MOVE_TO_ARCHIVE;

                if (isAdminOrSystem) {
                    actorMsg = "ההתאמה הועברה לארכיון (מערכת/אדמין).";
                    otherMsg = "ההתאמה הועברה לארכיון (מערכת/אדמין).";
                } else {
                    actorMsg = "העברת את ההתאמה לארכיון.";
                    otherMsg = "ההתאמה הועברה לארכיון.";
                }

                notif = NotificationType.MATCH_CLOSED;
            }

            // =========================
            // Generic Update (כולל unapprove / unarchive / reactivate וכו')
            // =========================
            case MATCH_UPDATED -> {
                severity = UiSeverity.INFO;
                effect = UiEffect.REFRESH_LISTS;

                // “כיסוי על כל היתר” — נבנה מסרים עקביים גם אם זה הגיע ממערכת
                if (becameMutualNow) {
                    actorMsg = "הפעולה שלך הפכה את ההתאמה להדדית — יש לכם התאמה!";
                    otherMsg = "ההתאמה הפכה להדדית — יש לכם התאמה!";
                    severity = UiSeverity.SUCCESS;
                    effect = UiEffect.SHOW_BANNER;
                    notif = NotificationType.MATCH_MUTUAL;
                    extras.put("becameMutualNow", "true");
                } else if (mutualBrokenNow) {
                    actorMsg = "האישור ההדדי בוטל. ההתאמה ירדה ממצב הדדי.";
                    otherMsg = "האישור ההדדי בוטל. ההתאמה ירדה ממצב הדדי.";
                    severity = UiSeverity.WARNING;
                    notif = NotificationType.MATCH_CLOSED;
                    extras.put("mutualBrokenNow", "true");
                } else {
                    actorMsg = "עודכן מצב ההתאמה.";
                    otherMsg = "עודכן מצב ההתאמה.";
                }

                // אם יש reason, נשלב ברמז UI (לא חובה)
                if (reason != null && !reason.isBlank()) {
                    extras.put("hasReason", "true");
                }
            }

            default -> {
                // נשארים על ברירות מחדל
                severity = UiSeverity.INFO;
                effect = UiEffect.REFRESH_LISTS;
            }
        }

        return new MatchActionFeedback(
                match,
                actorUserId,
                otherUserId,
                actorMsg,
                otherMsg,
                uiCode,
                severity,
                effect,
                notif,
                action,
                mode != null ? mode : WeddingMode.NONE,
                sourceModule != null ? sourceModule : SystemModule.MATCH_SERVICE,
                beforeStatus,
                afterStatus,
                becameMutualNow,
                mutualBrokenNow,
                extras
        );
    }

    // ============================================================
    // 🔵 Helpers
    // ============================================================

    private static Long resolveOtherUserId(Match match, Long actorUserId) {
        if (match.getUser1() == null || match.getUser2() == null) return null;

        Long u1 = match.getUser1().getId();
        Long u2 = match.getUser2().getId();

        if (u1 != null && u1.equals(actorUserId)) return u2;
        if (u2 != null && u2.equals(actorUserId)) return u1;

        return null; // actor לא חלק מה-match (אמור להיחסם בשכבת service)
    }

    private static boolean isAdminOrSystem(SystemModule sourceModule) {
        if (sourceModule == null) return false;
        return sourceModule == SystemModule.WEDDING_ADMIN_CONTROLLER
                || sourceModule == SystemModule.USER_ADMIN_CONTROLLER
                || sourceModule == SystemModule.SYSTEM_CONTROLLER
                || sourceModule == SystemModule.SYSTEM_CORE
                || sourceModule == SystemModule.SYSTEM_RULES
                || sourceModule == SystemModule.SYSTEM_SECURITY_CENTER
                || sourceModule == SystemModule.SYSTEM_AUDIT_TRAIL;
    }
}