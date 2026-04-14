package com.campus.network.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardOverviewService {

    private record CacheEntry(long cachedAtMillis, String cacheKey, Map<String, Object> payload) {
    }

    private final FlowAnalysisService flowAnalysisService;
    private final LatestDataTimeService latestDataTimeService;
    private final AtomicReference<CacheEntry> cacheRef = new AtomicReference<>();

    @Value("${app.dashboard.overview-cache-ms:1000}")
    private long overviewCacheMillis;

    public DashboardOverviewService(
            FlowAnalysisService flowAnalysisService,
            LatestDataTimeService latestDataTimeService
    ) {
        this.flowAnalysisService = flowAnalysisService;
        this.latestDataTimeService = latestDataTimeService;
    }

    public Map<String, Object> getOverview(
            int metricsMinutesAgo,
            int topLimit,
            int topMinutesAgo,
            String topMetric,
            int regionMinutesAgo,
            int trendMinutesAgo,
            int trendBucketMinutes
    ) {
        String cacheKey = String.join(
                "|",
                String.valueOf(Math.abs(metricsMinutesAgo)),
                String.valueOf(Math.max(topLimit, 1)),
                String.valueOf(Math.abs(topMinutesAgo)),
                topMetric == null ? "bytes" : topMetric,
                String.valueOf(Math.abs(regionMinutesAgo)),
                String.valueOf(Math.abs(trendMinutesAgo)),
                String.valueOf(Math.max(trendBucketMinutes, 1))
        );

        CacheEntry cached = cacheRef.get();
        long nowMillis = System.currentTimeMillis();
        if (cached != null && cached.cacheKey().equals(cacheKey) && nowMillis - cached.cachedAtMillis() <= overviewCacheMillis) {
            return cached.payload();
        }

        LocalDateTime now = latestDataTimeService.resolveWindowEnd();

        LocalDateTime metricsStart = now.minusMinutes(Math.abs(metricsMinutesAgo));
        LocalDateTime topStart = now.minusMinutes(Math.abs(topMinutesAgo));
        LocalDateTime regionStart = now.minusMinutes(Math.abs(regionMinutesAgo));
        LocalDateTime trendStart = now.minusMinutes(Math.abs(trendMinutesAgo));

        CompletableFuture<Double> throughputFuture = CompletableFuture.supplyAsync(
                () -> round(flowAnalysisService.calculateThroughputMbps(metricsStart, now)),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<Double> ppsFuture = CompletableFuture.supplyAsync(
                () -> round(flowAnalysisService.calculatePps(metricsStart, now)),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<Long> activeIpsFuture = CompletableFuture.supplyAsync(
                () -> flowAnalysisService.countActiveIps(metricsStart, now),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<Map<String, Long>> appBytesFuture = CompletableFuture.supplyAsync(
                () -> flowAnalysisService.getAppProtocolDistribution(metricsStart, now, "bytes"),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<Map<String, Long>> appPacketsFuture = CompletableFuture.supplyAsync(
                () -> flowAnalysisService.getAppProtocolDistribution(metricsStart, now, "packets"),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<List<Map<String, Object>>> topFlowsFuture = CompletableFuture.supplyAsync(
                () -> flowAnalysisService.getTopFlows(topStart, now, topLimit, topMetric),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<Map<String, Map<String, Object>>> regionFuture = CompletableFuture.supplyAsync(
                () -> flowAnalysisService.getRegionTraffic(regionStart, now),
                ForkJoinPool.commonPool()
        );
        CompletableFuture<List<Map<String, Object>>> trendFuture = CompletableFuture.supplyAsync(
                () -> flowAnalysisService.getThroughputTrend(trendStart, now, trendBucketMinutes),
                ForkJoinPool.commonPool()
        );

        CompletableFuture.allOf(
                throughputFuture,
                ppsFuture,
                activeIpsFuture,
                appBytesFuture,
                appPacketsFuture,
                topFlowsFuture,
                regionFuture,
                trendFuture
        ).join();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("throughputMbps", throughputFuture.join());
        metrics.put("pps", ppsFuture.join());
        metrics.put("activeIps", activeIpsFuture.join());
        metrics.put("appDistributionBytes", appBytesFuture.join());
        metrics.put("appDistributionPackets", appPacketsFuture.join());
        metrics.put("timestamp", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("metrics", metrics);
        response.put("topFlows", topFlowsFuture.join());
        response.put("regionTraffic", regionFuture.join());
        response.put("throughputTrend", trendFuture.join());
        response.put("refreshedAt", now);

        Map<String, Object> readonly = Map.copyOf(response);
        cacheRef.set(new CacheEntry(nowMillis, cacheKey, readonly));
        return readonly;
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
