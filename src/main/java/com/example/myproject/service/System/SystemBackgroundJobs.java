package com.example.myproject.service.System;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SystemBackgroundJobs {

    private final SystemLogService systemLogService;

    public SystemBackgroundJobs(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    // כל יום ב-03:15
    @Scheduled(cron = "0 15 3 * * *")
    public void purgeSystemLogs() {
        systemLogService.purgeOlderThan(LocalDateTime.now().minusDays(90));
    }
}
