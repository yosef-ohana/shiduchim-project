package com.example.myproject.service.notification;

import com.example.myproject.model.NotificationUser;
import com.example.myproject.model.UserDeviceToken;
import com.example.myproject.model.enums.NotificationType;
import org.springframework.stereotype.Service;

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

    public PushDeliveryService(NotificationUserService notificationUserService,
                               NotificationPreferencesService notificationPreferencesService,
                               UserDeviceTokenService userDeviceTokenService,
                               ExpoPushSenderService expoPushSenderService) {
        this.notificationUserService = notificationUserService;
        this.notificationPreferencesService = notificationPreferencesService;
        this.userDeviceTokenService = userDeviceTokenService;
        this.expoPushSenderService = expoPushSenderService;
    }

    /**
     * Pull N pending NotificationUser (delivered=false/NULL), respect preferences,
     * send push to all active tokens, and mark delivered if at least one send succeeded.
     */
    public int deliverPendingGlobal(int limit) {
        if (limit <= 0) limit = 200;

        List<NotificationUser> batch = notificationUserService.getPendingDeliveryGlobal(limit);
        if (batch.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        List<Long> deliveredIds = new ArrayList<>();

        for (NotificationUser nu : batch) {
            Long userId = (nu.getUser() != null ? nu.getUser().getId() : null);
            if (userId == null) continue;

            NotificationType type = (nu.getNotification() != null ? nu.getNotification().getType() : null);

            // Throttle: חוסם הכל חוץ מ-Match/Like/Chat
            if (notificationPreferencesService.isThrottled(userId, now) && !isPriorityType(type)) {
                continue;
            }

            EnumSet<NotificationPreferencesService.DeliveryChannel> allowed =
                    notificationPreferencesService.resolveAllowedChannels(userId, now);

            if (!allowed.contains(NotificationPreferencesService.DeliveryChannel.PUSH)) {
                // המשתמש כיבה PUSH / quiet hours -> לא שולחים.
                // משאירים לא-Delivered כדי לא "לסמן שקר" (תואם המודל הנוכחי שלך של delivered=ניסיון מסירה אמיתי).
                continue;
            }

            List<UserDeviceToken> tokens = userDeviceTokenService.getActiveTokens(userId);
            if (tokens.isEmpty()) {
                continue;
            }

            boolean sentAtLeastOne = false;

            for (UserDeviceToken t : tokens) {
                ExpoPushSenderService.SendStatus status = expoPushSenderService.send(t, nu);

                if (status == ExpoPushSenderService.SendStatus.OK) {
                    sentAtLeastOne = true;
                } else if (status == ExpoPushSenderService.SendStatus.INVALID_TOKEN) {
                    userDeviceTokenService.deactivateToken(t.getToken());
                }
            }

            if (sentAtLeastOne) {
                deliveredIds.add(nu.getId());
            }
        }

        if (!deliveredIds.isEmpty()) {
            notificationUserService.markDeliveredBatch(deliveredIds);
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
