package com.campus.network.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DashboardRealtimePushService {

    private final FlowAnalysisService flowAnalysisService;
    private final DashboardMetricsWebSocketHandler dashboardMetricsWebSocketHandler;

    @Value("${app.demo.auto-init:true}")
    private boolean autoInit;

    public DashboardRealtimePushService(
            FlowAnalysisService flowAnalysisService,
            DashboardMetricsWebSocketHandler dashboardMetricsWebSocketHandler
    ) {
        this.flowAnalysisService = flowAnalysisService;
        this.dashboardMetricsWebSocketHandler = dashboardMetricsWebSocketHandler;
    }

    @Scheduled(
            fixedDelayString = "${app.dashboard.ws-push-interval-ms:2000}",
            initialDelayString = "${app.dashboard.ws-push-initial-delay-ms:3000}"
    )
    public void pushRealtimeMetrics() {
        if (!autoInit) {
            return;
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(5);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("throughputMbps", round(flowAnalysisService.calculateThroughputMbps(startTime, endTime)));
        metrics.put("pps", round(flowAnalysisService.calculatePps(startTime, endTime)));
        metrics.put("activeIps", flowAnalysisService.countActiveIps(startTime, endTime));
        metrics.put("timestamp", endTime);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "dashboard_metrics");
        payload.put("metrics", metrics);
        payload.put("timestamp", endTime);

        dashboardMetricsWebSocketHandler.broadcast(payload);
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
