package com.example.myproject.service.System;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.myproject.service.notification.PushDeliveryService;


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
        // batch קטן כדי לא להעמיס
        pushDeliveryService.deliverPendingGlobal(200);
    }

}
