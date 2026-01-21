package com.example.myproject.dev;

import com.example.myproject.model.User;
import com.example.myproject.model.enums.DeviceType;
import com.example.myproject.model.enums.NotificationType;
import com.example.myproject.service.notification.NotificationPreferencesService;
import com.example.myproject.service.notification.NotificationService;
import com.example.myproject.service.notification.PushDeliveryService;
import com.example.myproject.service.notification.UserDeviceTokenService;
import com.example.myproject.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@Profile("smoke")
public class SmokePushRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationPreferencesService notificationPreferencesService;
    private final UserDeviceTokenService userDeviceTokenService;
    private final PushDeliveryService pushDeliveryService;

    public SmokePushRunner(UserRepository userRepository,
                           NotificationService notificationService,
                           NotificationPreferencesService notificationPreferencesService,
                           UserDeviceTokenService userDeviceTokenService,
                           PushDeliveryService pushDeliveryService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationPreferencesService = notificationPreferencesService;
        this.userDeviceTokenService = userDeviceTokenService;
        this.pushDeliveryService = pushDeliveryService;
    }

    @Override
    public void run(String... args) {
        User u = userRepository.findAll(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);

        if (u == null) {
            System.out.println("SMOKE: No users found in DB. Create 1 user first, then rerun.");
            return;
        }

        Long userId = u.getId();

        // 1) לוודא שאין throttle וש-PUSH פתוח
        notificationPreferencesService.releaseThrottle(userId);
        notificationPreferencesService.setQuietHours(userId, false, null, null);
        notificationPreferencesService.setChannels(userId, true, true, false); // push + inApp enabled

        // 2) לרשום טוקן דמה (כדי שלא ניפול על skippedNoTokens)
        //    (אם אין לך טוקן Expo אמיתי - זה בסדר, נקבל FAILED/INVALID ונראה את הלוגים עובדים)
        String fakeExpoToken = "ExponentPushToken[SMOKE_TEST_TOKEN]";
        userDeviceTokenService.registerToken(userId, fakeExpoToken, DeviceType.IOS, "smoke-device");

        // 3) ליצור NotificationUser pending אמיתי דרך NotificationService.create(...)
        NotificationService.CreateNotificationRequest req = new NotificationService.CreateNotificationRequest();
        req.recipientUserId = userId;
        req.type = NotificationType.CHAT_MESSAGE_RECEIVED; // priority לפי isPriorityType אצלך
        req.title = "Smoke Test";
        req.message = "Push smoke test message";
        req.popupEligible = true;
        req.priorityLevel = 1;

        notificationService.create(req);

        // 4) להריץ משלוח מיד (לא לחכות ל-scheduler)
        int delivered = pushDeliveryService.deliverPendingGlobal(200);
        System.out.println("SMOKE: deliverPendingGlobal delivered=" + delivered + " (check system_logs)");
    }
}
