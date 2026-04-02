package com.campus.network.config;

import com.campus.network.service.DataImportService;
import com.campus.network.service.DataImportService.DemoDataRefreshResult;
import com.campus.network.service.ThreatDetectionService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DemoDataRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRefreshScheduler.class);

    private final DataImportService dataImportService;
    private final ThreatDetectionService threatDetectionService;

    @Value("${app.demo.auto-init:true}")
    private boolean autoInit;

    public DemoDataRefreshScheduler(
            DataImportService dataImportService,
            ThreatDetectionService threatDetectionService
    ) {
        this.dataImportService = dataImportService;
        this.threatDetectionService = threatDetectionService;
    }

    @Scheduled(
            fixedDelayString = "${app.demo.stream-interval-ms:30000}",
            initialDelayString = "${app.demo.stream-initial-delay-ms:30000}"
    )
    public void appendDemoTraffic() {
        if (!autoInit) {
            return;
        }

        int appendedFlows = dataImportService.appendRealtimeDemoData();
        long cleanedRecords = dataImportService.cleanupExpiredDemoData();

        if (appendedFlows <= 0) {
            if (cleanedRecords > 0) {
                log.info("Demo traffic cleanup removed {} stale records.", cleanedRecords);
            }
            return;
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(15);
        threatDetectionService.runFullThreatDetection(startTime, endTime);
        log.info("Demo traffic stream appended {} flows and cleaned {} stale records.", appendedFlows, cleanedRecords);
    }

    @Scheduled(
            fixedDelayString = "${app.demo.rebuild-check-interval-ms:300000}",
            initialDelayString = "${app.demo.rebuild-check-initial-delay-ms:120000}"
    )
    public void rebuildStaleDemoDataIfNeeded() {
        if (!autoInit) {
            return;
        }

        DemoDataRefreshResult refreshResult = dataImportService.initializeDemoDataIfNeeded();
        if (refreshResult == DemoDataRefreshResult.SKIPPED) {
            return;
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(60);
        threatDetectionService.runFullThreatDetection(startTime, endTime);
        log.info("Demo data refresh result: {}", refreshResult);
    }
}
