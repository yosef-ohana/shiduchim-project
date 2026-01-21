package com.example.myproject.service.System;

import com.example.myproject.model.enums.SystemActionType;
import com.example.myproject.model.enums.SystemModule;
import com.example.myproject.model.enums.SystemSeverityLevel;
import com.example.myproject.service.notification.PushDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SystemBackgroundJobs {

    private final SystemLogService systemLogService;
    private final PushDeliveryService pushDeliveryService;
    private final SystemSettingsService systemSettingsService;

    public SystemBackgroundJobs(SystemLogService systemLogService,
                                SystemSettingsService systemSettingsService,
                                PushDeliveryService pushDeliveryService) {
        this.systemLogService = systemLogService;
        this.systemSettingsService = systemSettingsService;
        this.pushDeliveryService = pushDeliveryService;
    }

    // כל יום ב-03:15
    @Scheduled(cron = "0 15 3 * * *")
    public void purgeSystemLogs() {
        systemLogService.purgeOlderThan(LocalDateTime.now().minusDays(90));
    }

    @Scheduled(fixedDelay = 10000)
    public void deliverPushQueue() {
        try {
            // batch קטן כדי לא להעמיס
            pushDeliveryService.deliverPendingGlobal(200);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 500) msg = msg.substring(0, 500);

            systemLogService.logSystem(
                    SystemActionType.EXTERNAL_INTEGRATION_ERROR,
                    SystemModule.SYSTEM_SCHEDULER,
                    SystemSeverityLevel.ERROR,
                    false,
                    "push scheduler exception: " + e.getClass().getSimpleName() + (msg != null ? (": " + msg) : "")
            );
        }
    }
}
