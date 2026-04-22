package com.example.clouddisk.scheduler;

import com.example.clouddisk.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class RecycleCleanupScheduler {

    @Autowired
    private FileService fileService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanRecycleBin() {
        fileService.autoCleanRecycleBin();
    }
}