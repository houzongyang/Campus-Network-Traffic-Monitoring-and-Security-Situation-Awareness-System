package com.campus.network.controller;

import com.campus.network.service.AppIdentificationService;
import com.campus.network.service.DashboardOverviewService;
import com.campus.network.service.FlowAnalysisService;
import com.campus.network.service.LatestDataTimeService;
import com.campus.network.service.ThreatDetectionService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final FlowAnalysisService flowAnalysisService;
    private final AppIdentificationService appIdentificationService;
    private final ThreatDetectionService threatDetectionService;
    private final DashboardOverviewService dashboardOverviewService;
    private final LatestDataTimeService latestDataTimeService;

    public DashboardController(
            FlowAnalysisService flowAnalysisService,
            AppIdentificationService appIdentificationService,
            ThreatDetectionService threatDetectionService,
            DashboardOverviewService dashboardOverviewService,
            LatestDataTimeService latestDataTimeService
    ) {
        this.flowAnalysisService = flowAnalysisService;
        this.appIdentificationService = appIdentificationService;
        this.threatDetectionService = threatDetectionService;
        this.dashboardOverviewService = dashboardOverviewService;
        this.latestDataTimeService = latestDataTimeService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestParam(defaultValue = "-5") int metricsMinutesAgo,
            @RequestParam(defaultValue = "10") int topLimit,
            @RequestParam(defaultValue = "-5") int topMinutesAgo,
            @RequestParam(defaultValue = "bytes") String topMetric,
            @RequestParam(defaultValue = "-30") int regionMinutesAgo,
            @RequestParam(defaultValue = "-60") int trendMinutesAgo,
            @RequestParam(defaultValue = "5") int trendBucketMinutes
    ) {
        return ResponseEntity.ok(dashboardOverviewService.getOverview(
                metricsMinutesAgo,
                topLimit,
                topMinutesAgo,
                topMetric,
                regionMinutesAgo,
                trendMinutesAgo,
                trendBucketMinutes
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(@RequestParam(defaultValue = "-5") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("throughputMbps", round(flowAnalysisService.calculateThroughputMbps(startTime, endTime)));
        response.put("pps", round(flowAnalysisService.calculatePps(startTime, endTime)));
        response.put("activeIps", flowAnalysisService.countActiveIps(startTime, endTime));
        response.put("appDistributionBytes", flowAnalysisService.getAppProtocolDistribution(startTime, endTime, "bytes"));
        response.put("appDistributionPackets", flowAnalysisService.getAppProtocolDistribution(startTime, endTime, "packets"));
        response.put("timestamp", endTime);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-flows")
    public ResponseEntity<Map<String, Object>> getTopFlows(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "-5") int minutesAgo,
            @RequestParam(defaultValue = "bytes") String metric
    ) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("metric", metric);
        response.put("flows", flowAnalysisService.getTopFlows(startTime, endTime, limit, metric));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/region-traffic")
    public ResponseEntity<Map<String, Object>> getRegionTraffic(@RequestParam(defaultValue = "-30") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("regions", flowAnalysisService.getRegionTraffic(startTime, endTime));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/region-hierarchy")
    public ResponseEntity<Map<String, Object>> getRegionHierarchy(
            @RequestParam(defaultValue = "-30") int minutesAgo,
            @RequestParam(defaultValue = "bytes") String metric,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) String switchId
    ) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));
        FlowAnalysisService.RegionHierarchySnapshot snapshot = flowAnalysisService.getRegionHierarchy(
                startTime,
                endTime,
                metric,
                region,
                building,
                switchId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("metric", metric);
        response.put("level", snapshot.level());
        response.put("region", region);
        response.put("building", building);
        response.put("switchId", switchId);
        response.put("nodes", snapshot.nodes());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/throughput-trend")
    public ResponseEntity<Map<String, Object>> getThroughputTrend(
            @RequestParam(defaultValue = "-60") int minutesAgo,
            @RequestParam(defaultValue = "5") int bucketMinutes
    ) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("trend", flowAnalysisService.getThroughputTrend(startTime, endTime, bucketMinutes));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/supported-protocols")
    public ResponseEntity<Map<String, Object>> getSupportedProtocols() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("protocols", appIdentificationService.getSupportedProtocols());
        response.put("count", appIdentificationService.getSupportedProtocols().length);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/threat-statistics")
    public ResponseEntity<Map<String, Object>> getThreatStatistics(@RequestParam(defaultValue = "-60") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>(threatDetectionService.getThreatStatistics(startTime, endTime));
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/period-analysis")
    public ResponseEntity<Map<String, Object>> getPeriodAnalysis(
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end,
            @RequestParam(defaultValue = "-60") int minutesAgo,
            @RequestParam(defaultValue = "10") int bucketMinutes
    ) {
        LocalDateTime endTime = end != null ? end : latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = start != null ? start : endTime.minusMinutes(Math.abs(minutesAgo));
        if (!startTime.isBefore(endTime)) {
            startTime = endTime.minusMinutes(Math.abs(minutesAgo));
        }
        int normalizedBucketMinutes = Math.max(1, bucketMinutes);

        long flowCount = flowAnalysisService.countFlows(startTime, endTime);
        double throughputMbps = flowAnalysisService.calculateThroughputMbps(startTime, endTime);
        double pps = flowAnalysisService.calculatePps(startTime, endTime);
        long activeIps = flowAnalysisService.countActiveIps(startTime, endTime);
        Map<String, Object> threatStats = threatDetectionService.getThreatStatistics(startTime, endTime);
        List<Map<String, Object>> trend = flowAnalysisService.getThroughputTrend(startTime, endTime, normalizedBucketMinutes);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("flowCount", flowCount);
        metrics.put("throughputMbps", round(throughputMbps));
        metrics.put("pps", round(pps));
        metrics.put("activeIps", activeIps);
        metrics.put("totalAlerts", threatStats.getOrDefault("totalAlerts", 0L));
        metrics.put("criticalAlerts", mapLongValue(threatStats.get("bySeverity"), "critical"));
        metrics.put("highAlerts", mapLongValue(threatStats.get("bySeverity"), "high"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("durationMinutes", Math.max(1L, Duration.between(startTime, endTime).toMinutes()));
        summary.put("avgBytesPerFlow", flowCount > 0
                ? Math.round((throughputMbps * 1_000_000D / 8D) * Duration.between(startTime, endTime).toSeconds() / flowCount)
                : 0L);
        summary.put("topProtocolByBytes", topEntry(flowAnalysisService.getAppProtocolDistribution(startTime, endTime, "bytes")));
        summary.put("topAlertType", topEntryFromObject(threatStats.get("byType")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("window", Map.of("start", startTime, "end", endTime));
        response.put("bucketMinutes", normalizedBucketMinutes);
        response.put("metrics", metrics);
        response.put("summary", summary);
        response.put("trend", trend);
        response.put("alertTimeline", threatStats.getOrDefault("timeline", List.of()));
        response.put("alertSeverity", threatStats.getOrDefault("bySeverity", Map.of()));
        response.put("alertTypes", threatStats.getOrDefault("byType", Map.of()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "healthy");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private Map<String, Object> topEntry(Map<String, Long> values) {
        return values.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> Map.<String, Object>of("name", entry.getKey(), "value", entry.getValue()))
                .orElse(Map.of("name", "-", "value", 0L));
    }

    private long mapLongValue(Object source, String key) {
        if (!(source instanceof Map<?, ?> values)) {
            return 0L;
        }
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Map<String, Object> topEntryFromObject(Object source) {
        if (!(source instanceof Map<?, ?> values) || values.isEmpty()) {
            return Map.of("name", "-", "value", 0L);
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Number)
                .max((left, right) -> Long.compare(
                        ((Number) left.getValue()).longValue(),
                        ((Number) right.getValue()).longValue()
                ))
                .map(entry -> Map.<String, Object>of(
                        "name", String.valueOf(entry.getKey()),
                        "value", ((Number) entry.getValue()).longValue()
                ))
                .orElse(Map.of("name", "-", "value", 0L));
    }
}
