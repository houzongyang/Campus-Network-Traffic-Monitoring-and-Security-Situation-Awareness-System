package com.campus.network.algorithm;

import com.campus.network.model.NetworkFlow;
import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.repository.SecurityAlertRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 时序模式挖掘与预测性告警算法（TPM-PA）。
 *
 * 原创性边界：本项目原创部分是将校园网络中的流量、包数、告警数和活跃 IP 数组合为多信号滑窗，
 * 在工程上形成趋势分解、未来时间桶预测、交叉信号放大和提前预警输出流程。
 * 基础理论和公式不是本项目原创：线性回归用于趋势拟合，Z-score 标准分用于偏离度衡量，
 * Pearson 相关系数用于描述信号间线性同步变化，S 形函数用于通用归一化。
 *
 * 算法流程：按固定时间桶聚合多类信号，对每类信号做线性趋势外推，计算未来预测值的 Z-score，
 * 再根据多个信号是否同时越过预警线进行温和放大，最终输出未来若干时间桶的预警分和等级。
 */
@Service
public class TemporalPredictiveAlertService {

    private static final int BUCKET_MINUTES = 5;
    private static final int PREDICTION_HORIZON_BUCKETS = 3;
    private static final double Z_SCORE_WARNING_THRESHOLD = 1.5;
    private static final double Z_SCORE_CRITICAL_THRESHOLD = 2.5;
    private static final double CROSS_SIGNAL_AMPLIFICATION = 1.4;

    private final NetworkFlowRepository flowRepository;
    private final SecurityAlertRepository alertRepository;

    public TemporalPredictiveAlertService(
            NetworkFlowRepository flowRepository,
            SecurityAlertRepository alertRepository
    ) {
        this.flowRepository = flowRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * 运行完整时序预测分析流程。
     * 分析历史时间桶模式，并生成近未来若干时间桶的预测结果。
     * 对流量、包数、告警数、活跃 IP 四类信号做趋势分解并预测未来 3 个时间桶。
     */
    public Map<String, Object> runPredictiveAnalysis(LocalDateTime analysisStart, LocalDateTime analysisEnd) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(analysisStart, analysisEnd);
        List<SecurityAlert> alerts = alertRepository.findByDetectedTimeBetweenOrderByDetectedTimeDesc(analysisStart, analysisEnd);

        List<TimeBucket> buckets = buildMultiSignalBuckets(flows, alerts, analysisStart, analysisEnd);
        if (buckets.size() < 4) {
            return Map.of("algorithm", "TPM-PA", "message", "Insufficient time buckets for analysis",
                    "predictions", List.of());
        }

        TrendDecomposition bytesTrend = decomposeTrend(buckets, TimeBucket::totalBytes);
        TrendDecomposition packetsTrend = decomposeTrend(buckets, TimeBucket::totalPackets);
        TrendDecomposition alertRateTrend = decomposeTrend(buckets, TimeBucket::alertCount);
        TrendDecomposition uniqueIpsTrend = decomposeTrend(buckets, TimeBucket::uniqueIpCount);

        List<Map<String, Object>> predictions = generatePredictions(
                buckets, bytesTrend, packetsTrend, alertRateTrend, uniqueIpsTrend);

        List<Map<String, Object>> historicalAnalysis = buildHistoricalAnalysis(
                buckets, bytesTrend, packetsTrend, alertRateTrend);

        Map<String, Object> trendSummary = buildTrendSummary(bytesTrend, packetsTrend, alertRateTrend, uniqueIpsTrend);

        Map<String, Object> crossSignalCorrelation = computeCrossSignalCorrelation(buckets);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "TPM-PA");
        result.put("analysisWindow", Map.of("start", analysisStart, "end", analysisEnd));
        result.put("bucketMinutes", BUCKET_MINUTES);
        result.put("totalBuckets", buckets.size());
        result.put("predictionHorizon", PREDICTION_HORIZON_BUCKETS * BUCKET_MINUTES + " minutes");
        result.put("trendSummary", trendSummary);
        result.put("crossSignalCorrelation", crossSignalCorrelation);
        result.put("historicalAnalysis", historicalAnalysis);
        result.put("predictions", predictions);
        result.put("earlyWarnings", predictions.stream()
                .filter(p -> (double) p.get("earlyWarningScore") >= 0.5)
                .toList());
        return result;
    }

    /**
     * 多信号分桶：按固定时间粒度汇总流量、包数、告警数、活跃 IP 和区域流量。
     */
    private List<TimeBucket> buildMultiSignalBuckets(List<NetworkFlow> flows, List<SecurityAlert> alerts,
                                                      LocalDateTime start, LocalDateTime end) {
        List<TimeBucket> buckets = new ArrayList<>();
        LocalDateTime cursor = alignDown(start, BUCKET_MINUTES);

        while (!cursor.isAfter(end)) {
            LocalDateTime bucketStart = cursor;
            LocalDateTime bucketEnd = cursor.plusMinutes(BUCKET_MINUTES);

            List<NetworkFlow> bucketFlows = flows.stream()
                    .filter(f -> !f.getTimestamp().isBefore(bucketStart) && f.getTimestamp().isBefore(bucketEnd))
                    .toList();

            long alertCount = alerts.stream()
                    .filter(a -> !a.getDetectedTime().isBefore(bucketStart) && a.getDetectedTime().isBefore(bucketEnd))
                    .count();

            double totalBytes = bucketFlows.stream().mapToLong(f -> f.getBytesSent() + f.getBytesRecv()).sum();
            double totalPackets = bucketFlows.stream().mapToLong(f -> f.getPacketsSent() + f.getPacketsRecv()).sum();
            long uniqueIps = bucketFlows.stream()
                    .flatMap(f -> java.util.stream.Stream.of(f.getSrcIp(), f.getDstIp()))
                    .distinct().count();

            Map<String, Long> regionBytes = bucketFlows.stream()
                    .collect(Collectors.groupingBy(
                            NetworkFlow::getRegion,
                            Collectors.summingLong(f -> f.getBytesSent() + f.getBytesRecv())
                    ));

            buckets.add(new TimeBucket(bucketStart, totalBytes, totalPackets, alertCount, uniqueIps, regionBytes, bucketFlows.size()));
            cursor = bucketEnd;
        }
        return buckets;
    }

    /**
     * 使用一元线性回归进行趋势分解。
     * 公式：观测值 = 截距 + 斜率 × 时间序号 + 残差。
     * 输出斜率、截距、均值、标准差和残差统计。
     * 使用最小二乘法拟合直线趋势，并保留残差用于回看历史偏离程度。
     */
    private TrendDecomposition decomposeTrend(List<TimeBucket> buckets, java.util.function.ToDoubleFunction<TimeBucket> extractor) {
        int n = buckets.size();
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = extractor.applyAsDouble(buckets.get(i));

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumX2 += (double) i * i;
        }

        double meanX = sumX / n;
        double meanY = sumY / n;
        double slope = (n * sumXY - sumX * sumY) / Math.max(1, n * sumX2 - sumX * sumX);
        double intercept = meanY - slope * meanX;

        double[] residuals = new double[n];
        double residualSum = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * i;
            residuals[i] = values[i] - predicted;
            residualSum += residuals[i] * residuals[i];
        }
        double residualStd = Math.sqrt(residualSum / Math.max(1, n));

        double variance = 0;
        for (double v : values) variance += (v - meanY) * (v - meanY);
        double stdDev = Math.sqrt(variance / Math.max(1, n));

        return new TrendDecomposition(slope, intercept, meanY, stdDev, residualStd, values, residuals);
    }

    /**
     * 生成未来若干个时间桶的预测结果，数量由预测窗口常量控制。
     * 每个预测点包含：
     * 各信号预测值。
     * 预测值对应的 Z-score 标准分。
     * 综合提前预警分。
     */
    private List<Map<String, Object>> generatePredictions(
            List<TimeBucket> buckets,
            TrendDecomposition bytesTrend,
            TrendDecomposition packetsTrend,
            TrendDecomposition alertTrend,
            TrendDecomposition ipsTrend
    ) {
        List<Map<String, Object>> predictions = new ArrayList<>();
        int n = buckets.size();
        LocalDateTime lastBucketTime = buckets.get(n - 1).startTime();

        for (int h = 1; h <= PREDICTION_HORIZON_BUCKETS; h++) {
            int futureIdx = n - 1 + h;
            LocalDateTime predTime = lastBucketTime.plusMinutes((long) h * BUCKET_MINUTES);

            double predBytes = bytesTrend.predict(futureIdx);
            double predPackets = packetsTrend.predict(futureIdx);
            double predAlerts = alertTrend.predict(futureIdx);
            double predIps = ipsTrend.predict(futureIdx);

            double bytesZScore = bytesTrend.zScore(predBytes);
            double packetsZScore = packetsTrend.zScore(predPackets);
            double alertsZScore = alertTrend.zScore(predAlerts);
            double ipsZScore = ipsTrend.zScore(predIps);
            PredictionSignalScore signalScore = scorePredictionSignals(
                    bytesZScore, packetsZScore, alertsZScore, ipsZScore);

            double bytesRateOfChange = bytesTrend.slope() / Math.max(1, bytesTrend.mean());
            double alertRateOfChange = alertTrend.slope() / Math.max(0.1, alertTrend.mean());

            Map<String, Object> pred = new LinkedHashMap<>();
            pred.put("predictedTime", predTime);
            pred.put("horizonBucket", h);
            pred.put("predictions", Map.of(
                    "bytes", Map.of("predicted", AlgorithmMathUtils.round(Math.max(0, predBytes), 0), "zScore", AlgorithmMathUtils.round(bytesZScore, 3)),
                    "packets", Map.of("predicted", AlgorithmMathUtils.round(Math.max(0, predPackets), 0), "zScore", AlgorithmMathUtils.round(packetsZScore, 3)),
                    "alertRate", Map.of("predicted", AlgorithmMathUtils.round(Math.max(0, predAlerts), 2), "zScore", AlgorithmMathUtils.round(alertsZScore, 3)),
                    "uniqueIps", Map.of("predicted", AlgorithmMathUtils.round(Math.max(0, predIps), 0), "zScore", AlgorithmMathUtils.round(ipsZScore, 3))
            ));
            pred.put("earlyWarningScore", AlgorithmMathUtils.round(signalScore.earlyWarningScore(), 4));
            pred.put("warningLevel", signalScore.warningLevel());
            pred.put("signalsAboveWarning", signalScore.signalsAboveWarning());
            pred.put("rateOfChange", Map.of(
                    "bytesPerBucket", AlgorithmMathUtils.round(bytesRateOfChange, 4),
                    "alertsPerBucket", AlgorithmMathUtils.round(alertRateOfChange, 4)
            ));
            predictions.add(pred);
        }
        return predictions;
    }

    /**
     * 交叉信号放大：≥2 个信号越过预警线乘 1.4，≥3 个信号再次乘 1.4。
     */
    private PredictionSignalScore scorePredictionSignals(double... zScores) {
        int signalsAboveWarning = 0;
        double maxZScore = 0.0;
        for (double zScore : zScores) {
            double absZScore = Math.abs(zScore);
            if (absZScore >= Z_SCORE_WARNING_THRESHOLD) {
                signalsAboveWarning++;
            }
            maxZScore = Math.max(maxZScore, absZScore);
        }

        double earlyWarningScore = AlgorithmMathUtils.sigmoid(maxZScore - 1.0);
        if (signalsAboveWarning >= 2) {
            earlyWarningScore = Math.min(1.0, earlyWarningScore * CROSS_SIGNAL_AMPLIFICATION);
        }
        if (signalsAboveWarning >= 3) {
            earlyWarningScore = Math.min(1.0, earlyWarningScore * CROSS_SIGNAL_AMPLIFICATION);
        }

        String warningLevel = earlyWarningScore >= 0.8 ? "critical"
                : earlyWarningScore >= 0.6 ? "high"
                : earlyWarningScore >= 0.4 ? "medium" : "low";
        return new PredictionSignalScore(signalsAboveWarning, maxZScore, earlyWarningScore, warningLevel);
    }

    private List<Map<String, Object>> buildHistoricalAnalysis(
            List<TimeBucket> buckets,
            TrendDecomposition bytesTrend,
            TrendDecomposition packetsTrend,
            TrendDecomposition alertTrend
    ) {
        List<Map<String, Object>> analysis = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            TimeBucket b = buckets.get(i);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", b.startTime());
            point.put("actual", Map.of(
                    "bytes", b.totalBytes(),
                    "packets", b.totalPackets(),
                    "alerts", b.alertCount(),
                    "uniqueIps", b.uniqueIpCount()
            ));
            point.put("trendLine", Map.of(
                    "bytes", AlgorithmMathUtils.round(bytesTrend.predict(i), 0),
                    "packets", AlgorithmMathUtils.round(packetsTrend.predict(i), 0),
                    "alerts", AlgorithmMathUtils.round(alertTrend.predict(i), 2)
            ));
            point.put("residuals", Map.of(
                    "bytes", AlgorithmMathUtils.round(bytesTrend.residuals()[i], 0),
                    "packets", AlgorithmMathUtils.round(packetsTrend.residuals()[i], 0),
                    "alerts", AlgorithmMathUtils.round(alertTrend.residuals()[i], 2)
            ));
            point.put("zScores", Map.of(
                    "bytes", AlgorithmMathUtils.round(bytesTrend.zScore(b.totalBytes()), 3),
                    "packets", AlgorithmMathUtils.round(packetsTrend.zScore(b.totalPackets()), 3),
                    "alerts", AlgorithmMathUtils.round(alertTrend.zScore(b.alertCount()), 3)
            ));
            analysis.add(point);
        }
        return analysis;
    }

    private Map<String, Object> buildTrendSummary(
            TrendDecomposition bytesTrend,
            TrendDecomposition packetsTrend,
            TrendDecomposition alertTrend,
            TrendDecomposition ipsTrend
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("bytes", buildSingleTrendSummary(bytesTrend, "Traffic volume"));
        summary.put("packets", buildSingleTrendSummary(packetsTrend, "Packet rate"));
        summary.put("alertRate", buildSingleTrendSummary(alertTrend, "Alert frequency"));
        summary.put("uniqueIps", buildSingleTrendSummary(ipsTrend, "Active IP count"));
        return summary;
    }

    private Map<String, Object> buildSingleTrendSummary(TrendDecomposition trend, String label) {
        String direction = trend.slope() > 0.01 * trend.mean() ? "increasing"
                : trend.slope() < -0.01 * trend.mean() ? "decreasing" : "stable";

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("label", label);
        s.put("mean", AlgorithmMathUtils.round(trend.mean(), 2));
        s.put("stdDev", AlgorithmMathUtils.round(trend.stdDev(), 2));
        s.put("slope", AlgorithmMathUtils.round(trend.slope(), 4));
        s.put("trendDirection", direction);
        s.put("volatility", AlgorithmMathUtils.round(trend.stdDev() / Math.max(1, trend.mean()), 4));
        return s;
    }

    /**
     * 计算各信号两两 Pearson 相关系数。
     * 该指标只描述线性同步变化强弱，不直接推断因果关系。
     */
    private Map<String, Object> computeCrossSignalCorrelation(List<TimeBucket> buckets) {
        double[] bytes = buckets.stream().mapToDouble(TimeBucket::totalBytes).toArray();
        double[] packets = buckets.stream().mapToDouble(TimeBucket::totalPackets).toArray();
        double[] alerts = buckets.stream().mapToDouble(TimeBucket::alertCount).toArray();
        double[] ips = buckets.stream().mapToDouble(TimeBucket::uniqueIpCount).toArray();

        Map<String, Object> correlations = new LinkedHashMap<>();
        correlations.put("bytes_packets", AlgorithmMathUtils.round(pearsonCorrelation(bytes, packets), 4));
        correlations.put("bytes_alerts", AlgorithmMathUtils.round(pearsonCorrelation(bytes, alerts), 4));
        correlations.put("bytes_uniqueIps", AlgorithmMathUtils.round(pearsonCorrelation(bytes, ips), 4));
        correlations.put("packets_alerts", AlgorithmMathUtils.round(pearsonCorrelation(packets, alerts), 4));
        correlations.put("packets_uniqueIps", AlgorithmMathUtils.round(pearsonCorrelation(packets, ips), 4));
        correlations.put("alerts_uniqueIps", AlgorithmMathUtils.round(pearsonCorrelation(alerts, ips), 4));
        return correlations;
    }

    /**
     * Pearson 相关系数：协方差除以两个序列标准差的乘积，分母过小时返回 0，避免数值不稳定。
     */
    private double pearsonCorrelation(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) { sumX += x[i]; sumY += y[i]; }
        double meanX = sumX / n, meanY = sumY / n;

        double covariance = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            covariance += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        double denominator = Math.sqrt(varX * varY);
        return denominator < 0.001 ? 0.0 : covariance / denominator;
    }

    private LocalDateTime alignDown(LocalDateTime time, int bucketMinutes) {
        int aligned = time.getMinute() / bucketMinutes * bucketMinutes;
        return time.withMinute(aligned).withSecond(0).withNano(0);
    }

    private record TimeBucket(LocalDateTime startTime, double totalBytes, double totalPackets,
                              double alertCount, double uniqueIpCount,
                              Map<String, Long> regionBytes, int flowCount) {}

    private record PredictionSignalScore(int signalsAboveWarning, double maxZScore,
                                         double earlyWarningScore, String warningLevel) {}

    private record TrendDecomposition(double slope, double intercept, double mean, double stdDev,
                                      double residualStd, double[] values, double[] residuals) {
        double predict(int index) {
            return intercept + slope * index;
        }

        double zScore(double value) {
            return stdDev < 0.001 ? 0.0 : (value - mean) / stdDev;
        }
    }
}
