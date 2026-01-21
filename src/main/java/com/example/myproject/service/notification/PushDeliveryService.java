package com.example.myproject.service.notification;

import com.example.myproject.model.NotificationUser;
import com.example.myproject.model.UserDeviceToken;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.service.System.SystemLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
public class PushDeliveryService {

    private final NotificationUserService notificationUserService;
    private final NotificationPreferencesService notificationPreferencesService;
    private final UserDeviceTokenService userDeviceTokenService;
    private final ExpoPushSenderService expoPushSenderService;
    private final SystemLogService systemLogService;

    private static LocalDateTime lastSkipOnlyLogAt;

    public PushDeliveryService(NotificationUserService notificationUserService,
                               NotificationPreferencesService notificationPreferencesService,
                               UserDeviceTokenService userDeviceTokenService,
                               ExpoPushSenderService expoPushSenderService,
                               SystemLogService systemLogService) {
        this.notificationUserService = notificationUserService;
        this.notificationPreferencesService = notificationPreferencesService;
        this.userDeviceTokenService = userDeviceTokenService;
        this.expoPushSenderService = expoPushSenderService;
        this.systemLogService = systemLogService;
    }

    /**
     * Pull N pending NotificationUser (delivered=false/NULL), respect preferences,
     * send push to all active tokens, and mark delivered if at least one send succeeded.
     */
    @Transactional
    public int deliverPendingGlobal(int limit) {
        if (limit <= 0) limit = 200;

        List<NotificationUser> batch = notificationUserService.getPendingDeliveryGlobal(limit);
        if (batch.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        List<Long> deliveredIds = new ArrayList<>();

        int invalidTokens = 0;
        int failedSends = 0;
        int skippedThrottled = 0;
        int skippedNoPush = 0;
        int skippedNoTokens = 0;

        for (NotificationUser nu : batch) {
            Long userId = (nu.getUser() != null ? nu.getUser().getId() : null);
            if (userId == null) continue;

            NotificationType type = (nu.getNotification() != null ? nu.getNotification().getType() : null);

            if (notificationPreferencesService.isThrottled(userId, now) && !isPriorityType(type)) {
                skippedThrottled++;
                continue;
            }

            EnumSet<NotificationPreferencesService.DeliveryChannel> allowed =
                    notificationPreferencesService.resolveAllowedChannels(userId, now);

            if (!allowed.contains(NotificationPreferencesService.DeliveryChannel.PUSH)) {
                skippedNoPush++;
                continue;
            }

            List<UserDeviceToken> tokens = userDeviceTokenService.getActiveTokens(userId);

            // ✅ תיקון נקודתי: כאן צריך לבדוק tokens.isEmpty (ולא לבדוק שוב PUSH)
            if (tokens.isEmpty()) {
                skippedNoTokens++;
                continue;
            }

            boolean sentAtLeastOne = false;

            for (UserDeviceToken t : tokens) {
                ExpoPushSenderService.SendStatus status = expoPushSenderService.send(t, nu);

                if (status == ExpoPushSenderService.SendStatus.OK) {
                    sentAtLeastOne = true;
                } else if (status == ExpoPushSenderService.SendStatus.INVALID_TOKEN) {
                    invalidTokens++;
                    // לכבות כדי לא להיתקע עליו שוב ושוב
                    userDeviceTokenService.deactivateToken(t.getToken());
                } else {
                    failedSends++;
                }
            }

            if (sentAtLeastOne) {
                deliveredIds.add(nu.getId());
            }
        }

        if (!deliveredIds.isEmpty()) {
            notificationUserService.markDeliveredBatch(deliveredIds);
        }

        int delivered = deliveredIds.size();

        // לוג מינימלי: רק כשיש אירוע אמיתי (נשלח / נכשל / כובו טוקנים)
        if (delivered > 0 || failedSends > 0 || invalidTokens > 0) {
            String details = "pushDelivery batch=" + batch.size()
                    + " delivered=" + delivered
                    + " failedSends=" + failedSends
                    + " invalidTokens=" + invalidTokens
                    + " skippedThrottled=" + skippedThrottled
                    + " skippedNoPush=" + skippedNoPush
                    + " skippedNoTokens=" + skippedNoTokens;

            boolean success = delivered > 0 && failedSends == 0 && invalidTokens == 0;

            SystemActionType actionType = (failedSends > 0 || invalidTokens > 0)
                    ? SystemActionType.NOTIFICATION_DELIVERY_FAILED
                    : SystemActionType.NOTIFICATION_SENT_PUSH;

            SystemSeverityLevel severity = (failedSends > 0 || invalidTokens > 0)
                    ? SystemSeverityLevel.WARNING
                    : SystemSeverityLevel.INFO;

            systemLogService.logSystem(
                    actionType,
                    SystemModule.SYSTEM_SCHEDULER,
                    severity,
                    success,
                    details
            );
        }

        // ✅ הוספה נקודתית: Skip-only log (מוגבל קצב) כדי שלא תתקע בלי שום לוג כשהכול "סקיפים"
        int skips = skippedThrottled + skippedNoPush + skippedNoTokens;

        boolean skipOnly = batch.size() > 0
                && delivered == 0
                && failedSends == 0
                && invalidTokens == 0
                && skips > 0;

        if (skipOnly) {
            boolean shouldLog = (lastSkipOnlyLogAt == null) || lastSkipOnlyLogAt.isBefore(now.minusMinutes(5));
            if (shouldLog) {
                lastSkipOnlyLogAt = now;

                String details = "pushDelivery(SKIP_ONLY) batch=" + batch.size()
                        + " delivered=" + delivered
                        + " failedSends=" + failedSends
                        + " invalidTokens=" + invalidTokens
                        + " skippedThrottled=" + skippedThrottled
                        + " skippedNoPush=" + skippedNoPush
                        + " skippedNoTokens=" + skippedNoTokens;

                systemLogService.logSystem(
                        SystemActionType.NOTIFICATION_DELIVERY_FAILED,
                        SystemModule.SYSTEM_SCHEDULER,
                        SystemSeverityLevel.INFO,
                        false,
                        details
                );
            }
        }

        return deliveredIds.size();
    }

    private static boolean isPriorityType(NotificationType type) {
        if (type == null) return false;

        // "Match תמיד" לפי SSOT: LIKE_* + MATCH_* + הודעת צ'אט נכנסת
        switch (type) {
            case LIKE_RECEIVED:
            case LIKE_BACK_RECEIVED:
            case MATCH_CONFIRMED:
            case MATCH_MUTUAL:
            case CHAT_MESSAGE_RECEIVED:
                return true;
            default:
                return false;
        }
    }
}
