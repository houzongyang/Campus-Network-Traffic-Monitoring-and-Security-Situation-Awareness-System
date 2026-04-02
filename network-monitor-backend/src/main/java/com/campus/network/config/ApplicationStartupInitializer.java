package com.campus.network.config;

import com.campus.network.service.DataImportService;
import com.campus.network.service.DataImportService.DemoDataRefreshResult;
import com.campus.network.service.ThreatDetectionService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupInitializer.class);

    private final DataImportService dataImportService;
    private final ThreatDetectionService threatDetectionService;

    @Value("${app.demo.auto-init:true}")
    private boolean autoInit;

    public ApplicationStartupInitializer(
            DataImportService dataImportService,
            ThreatDetectionService threatDetectionService
    ) {
        this.dataImportService = dataImportService;
        this.threatDetectionService = threatDetectionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        if (!autoInit) {
            log.info("Skip demo initialization because app.demo.auto-init is disabled.");
            return;
        }

        DemoDataRefreshResult refreshResult = dataImportService.initializeDemoDataIfNeeded();

        if (refreshResult != DemoDataRefreshResult.SKIPPED) {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(60);
            threatDetectionService.runFullThreatDetection(startTime, endTime);
        }

        log.info("Smart campus demo dataset is ready.");
    }
}
