package com.campus.network.algorithm;

import com.campus.network.model.NetworkFlow;
import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.repository.SecurityAlertRepository;
import com.campus.network.service.GeoLocationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * EWMA-熵自适应异常检测算法（EWMA-EAD）。
 *
 * 原创性边界：本项目原创部分是将校园网络流量的体量偏离、协议分布偏离和时间突发度
 * 组合为三维异常评分流程，并将该流程接入告警生成、地理位置补全和时间线解释输出。
 * 基础理论和公式不是本项目原创：EWMA 指数加权移动平均用于自适应基线跟踪，
 * Shannon 信息熵用于度量协议分布离散程度，Z-score 标准分用于衡量当前观测相对基线的偏离，
 * 变异系数用于描述到达间隔的突发性，S 形函数用于通用归一化。
 *学术性、工程性
 * 算法流程：先用历史时间桶训练 EWMA 均值和方差，再对检测窗口逐桶计算体量、协议熵和突发度，
 * 将三类 Z-score 标准分经 S 形函数归一化后按权重合成为异常分，超过阈值时生成去重告警。
 *
 * 综合异常分公式：
 * 异常分 = α × 体量偏离 + β × 熵偏离 + γ × 突发度偏离。
 */
@Service
public class EwmaEntropyAnomalyDetectionService {

    private static final double EWMA_ALPHA = 0.3;
    private static final double EWMA_VARIANCE_ALPHA = 0.3;
    private static final double SCORE_WEIGHT_VOLUME = 0.4;
    private static final double SCORE_WEIGHT_ENTROPY = 0.35;
    private static final double SCORE_WEIGHT_BURSTINESS = 0.25;
    private static final double ANOMALY_SCORE_THRESHOLD = 0.65;
    private static final int BASELINE_BUCKETS = 12;
    private static final int BUCKET_MINUTES = 5;

    private final NetworkFlowRepository flowRepository;
    private final SecurityAlertRepository alertRepository;
    private final GeoLocationService geoLocationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EwmaEntropyAnomalyDetectionService(
            NetworkFlowRepository flowRepository,
            SecurityAlertRepository alertRepository,
            GeoLocationService geoLocationService
    ) {
        this.flowRepository = flowRepository;
        this.alertRepository = alertRepository;
        this.geoLocationService = geoLocationService;
    }

    /**
     * 运行完整 EWMA-EAD 检测流程。
     * 将检测窗口与历史基线窗口对比，输出时间线、参数和生成的告警数量。
     * 训练历史 EWMA 基线，逐桶计算三维异常分，并按阈值生成去重告警。
     */
    public Map<String, Object> runDetection(LocalDateTime detectionStart, LocalDateTime detectionEnd) {
        LocalDateTime baselineStart = detectionStart.minusHours(1);

        List<NetworkFlow> baselineFlows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(baselineStart, detectionStart);
        List<NetworkFlow> detectionFlows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(detectionStart, detectionEnd);

        List<BucketStats> baselineBuckets = buildTimeBuckets(baselineFlows, baselineStart, detectionStart, BUCKET_MINUTES);
        List<BucketStats> detectionBuckets = buildTimeBuckets(detectionFlows, detectionStart, detectionEnd, BUCKET_MINUTES);

        EwmaState volumeEwma = trainEwmaFromBuckets(baselineBuckets, BucketStats::totalBytes);
        EwmaState entropyEwma = trainEwmaFromBuckets(baselineBuckets, BucketStats::protocolEntropy);
        EwmaState burstinessEwma = trainEwmaFromBuckets(baselineBuckets, BucketStats::burstinessCoefficient);

        List<Map<String, Object>> anomalyTimeline = new ArrayList<>();
        List<SecurityAlert> generatedAlerts = new ArrayList<>();

        for (BucketStats bucket : detectionBuckets) {
            AnomalyScore score = scoreBucket(bucket, volumeEwma, entropyEwma, burstinessEwma);

            volumeEwma = updateEwma(volumeEwma, bucket.totalBytes());
            entropyEwma = updateEwma(entropyEwma, bucket.protocolEntropy());
            burstinessEwma = updateEwma(burstinessEwma, bucket.burstinessCoefficient());

            anomalyTimeline.add(buildTimelinePoint(bucket, score));

            if (score.isAnomaly()) {
                generatedAlerts.add(buildEwmaAlert(bucket, score));
            }
        }

        saveNewAlerts(generatedAlerts, detectionStart, detectionEnd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "EWMA-EAD");
        result.put("detectionWindow", Map.of("start", detectionStart, "end", detectionEnd));
        result.put("baselineWindow", Map.of("start", baselineStart, "end", detectionStart));
        result.put("parameters", Map.of(
                "ewmaAlpha", EWMA_ALPHA,
                "scoreWeights", Map.of("volume", SCORE_WEIGHT_VOLUME, "entropy", SCORE_WEIGHT_ENTROPY, "burstiness", SCORE_WEIGHT_BURSTINESS),
                "anomalyThreshold", ANOMALY_SCORE_THRESHOLD
        ));
        result.put("anomalyTimeline", anomalyTimeline);
        result.put("alertsGenerated", generatedAlerts.size());
        result.put("totalBuckets", detectionBuckets.size());
        result.put("anomalousBuckets", anomalyTimeline.stream().filter(p -> (boolean) p.get("isAnomaly")).count());
        return result;
    }

    /**
     * 三维评分核心：先计算 EWMA 残差对应的 Z-score 标准分，再做绝对值 S 形归一化，最后加权合成。
     */
    private AnomalyScore scoreBucket(
            BucketStats bucket,
            EwmaState volumeEwma,
            EwmaState entropyEwma,
            EwmaState burstinessEwma
    ) {
        double normalizedVolume = AlgorithmMathUtils.absoluteSigmoid(
                computeZScore(bucket.totalBytes(), volumeEwma), 2);
        double normalizedEntropy = AlgorithmMathUtils.absoluteSigmoid(
                computeZScore(bucket.protocolEntropy(), entropyEwma), 2);
        double normalizedBurstiness = AlgorithmMathUtils.absoluteSigmoid(
                computeZScore(bucket.burstinessCoefficient(), burstinessEwma), 2);

        double compositeScore = SCORE_WEIGHT_VOLUME * normalizedVolume
                + SCORE_WEIGHT_ENTROPY * normalizedEntropy
                + SCORE_WEIGHT_BURSTINESS * normalizedBurstiness;

        return new AnomalyScore(
                normalizedVolume,
                normalizedEntropy,
                normalizedBurstiness,
                compositeScore,
                identifyDominantFactor(normalizedVolume, normalizedEntropy, normalizedBurstiness)
        );
    }

    /**
     * 构造时间线输出点：保留综合分、三类偏离分和原始桶指标，便于前端解释。
     */
    private Map<String, Object> buildTimelinePoint(BucketStats bucket, AnomalyScore score) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("time", bucket.startTime());
        point.put("anomalyScore", AlgorithmMathUtils.round(score.compositeScore(), 4));
        point.put("volumeDeviation", AlgorithmMathUtils.round(score.volumeDeviation(), 4));
        point.put("entropyDeviation", AlgorithmMathUtils.round(score.entropyDeviation(), 4));
        point.put("burstiDeviation", AlgorithmMathUtils.round(score.burstinessDeviation(), 4));
        point.put("totalBytes", bucket.totalBytes());
        point.put("protocolEntropy", AlgorithmMathUtils.round(bucket.protocolEntropy(), 4));
        point.put("burstiness", AlgorithmMathUtils.round(bucket.burstinessCoefficient(), 4));
        point.put("isAnomaly", score.isAnomaly());
        return point;
    }

    /**
     * 按时间桶聚合流量，并计算每个桶的核心统计量：
     * 流量体量、协议熵和突发度。
     * 分桶统计每个 5 分钟窗口的流量体量、协议熵、突发性和主导 IP。
     */
    private List<BucketStats> buildTimeBuckets(List<NetworkFlow> flows, LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        List<BucketStats> buckets = new ArrayList<>();
        LocalDateTime cursor = alignDown(start, bucketMinutes);

        while (!cursor.isAfter(end)) {
            LocalDateTime bucketStart = cursor;
            LocalDateTime bucketEnd = cursor.plusMinutes(bucketMinutes);
            List<NetworkFlow> bucketFlows = flows.stream()
                    .filter(f -> !f.getTimestamp().isBefore(bucketStart) && f.getTimestamp().isBefore(bucketEnd))
                    .toList();

            double totalBytes = bucketFlows.stream().mapToLong(f -> f.getBytesSent() + f.getBytesRecv()).sum();
            double protocolEntropy = computeShannonEntropy(bucketFlows);
            double burstiness = computeBurstiness(bucketFlows);
            String topSrcIp = findTopIp(bucketFlows, true);
            String topDstIp = findTopIp(bucketFlows, false);

            buckets.add(new BucketStats(bucketStart, totalBytes, protocolEntropy, burstiness, topSrcIp, topDstIp, bucketFlows.size()));
            cursor = bucketEnd;
        }
        return buckets;
    }

    /**
     * 协议分布的 Shannon 信息熵。
     * 计算方式：按各协议概率乘以其二进制对数后的负和求得。
     * 该指标用于衡量协议使用多样性；突然下降通常表示协议过度集中，
     * 突然升高通常表示协议异常分散，两者都可能对应行为变化。
     * 协议熵用于识别“协议过度集中”或“协议异常分散”的隐蔽行为变化。
     */
    private double computeShannonEntropy(List<NetworkFlow> flows) {
        if (flows.isEmpty()) return 0.0;

        Map<String, Long> protocolCounts = flows.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getAppProtocol() == null ? "Unknown" : f.getAppProtocol(),
                        Collectors.counting()
                ));

        return AlgorithmMathUtils.shannonEntropy(protocolCounts.values());
    }

    /**
     * 时间突发度系数。
     * 使用到达间隔的变异系数衡量请求是否呈现突发集中。
     * 公式：CV = σ / μ，其中 σ 为到达间隔标准差，μ 为到达间隔均值。
     * CV 明显偏高时，表示流量更接近脉冲式或集中爆发模式。
     * CV 越大表示流量越集中爆发，适合捕获脉冲式扫描或突发攻击。
     */
    private double computeBurstiness(List<NetworkFlow> flows) {
        if (flows.size() < 3) return 0.0;

        List<Long> timestamps = flows.stream()
                .map(f -> f.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC))
                .sorted()
                .toList();

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < timestamps.size(); i++) {
            intervals.add(timestamps.get(i) - timestamps.get(i - 1));
        }

        if (intervals.isEmpty()) return 0.0;

        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(1.0);
        if (mean < 0.001) return 0.0;

        double variance = intervals.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        return stdDev / mean;
    }

    /**
     * 从历史基线时间桶训练 EWMA 状态。
     */
    private EwmaState trainEwmaFromBuckets(List<BucketStats> buckets, ToDoubleFunction<BucketStats> extractor) {
        if (buckets.isEmpty()) {
            return new EwmaState(0.0, 1.0);
        }

        double ewma = extractor.applyAsDouble(buckets.get(0));
        double ewmaVariance = 0.0;

        for (int i = 1; i < buckets.size(); i++) {
            double value = extractor.applyAsDouble(buckets.get(i));
            double diff = value - ewma;
            ewma = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * ewma;
            ewmaVariance = EWMA_VARIANCE_ALPHA * (diff * diff) + (1 - EWMA_VARIANCE_ALPHA) * ewmaVariance;
        }

        return new EwmaState(ewma, Math.max(ewmaVariance, 0.001));
    }

    /**
     * 使用新观测值在线更新 EWMA 状态。
     */
    private EwmaState updateEwma(EwmaState state, double value) {
        double diff = value - state.mean();
        double newMean = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * state.mean();
        double newVariance = EWMA_VARIANCE_ALPHA * (diff * diff) + (1 - EWMA_VARIANCE_ALPHA) * state.variance();
        return new EwmaState(newMean, Math.max(newVariance, 0.001));
    }

    /**
     * 根据 EWMA 状态计算 Z-score 标准分偏离。
     * Z-score 标准分 =（当前观测 - EWMA 均值）/ EWMA 标准差。
     */
    private double computeZScore(double value, EwmaState state) {
        double stdDev = Math.sqrt(state.variance());
        if (stdDev < 0.001) return 0.0;
        return (value - state.mean()) / stdDev;
    }

    private String identifyDominantFactor(double volume, double entropy, double burstiness) {
        if (volume >= entropy && volume >= burstiness) return "VolumeAnomaly";
        if (entropy >= volume && entropy >= burstiness) return "EntropyAnomaly";
        return "BurstinessAnomaly";
    }

    /**
     * 将高于阈值的异常桶转换为安全告警，并补充主导 IP 的地理信息。
     */
    private SecurityAlert buildEwmaAlert(BucketStats bucket, AnomalyScore score) {
        SecurityAlert alert = new SecurityAlert();
        alert.setAlertType("EWMA-EAD");
        alert.setSeverity(score.compositeScore() >= 0.85 ? "critical" : score.compositeScore() >= 0.75 ? "high" : "medium");
        alert.setSrcIp(bucket.topSrcIp() != null ? bucket.topSrcIp() : "-");
        alert.setDstIp(bucket.topDstIp());
        alert.setDetectedTime(bucket.startTime());
        alert.setDescription("EWMA-Entropy adaptive detection: " + score.dominantFactor()
                + " (score=" + AlgorithmMathUtils.round(score.compositeScore(), 3) + ")");
        alert.setThreatDetails(toJson(Map.of(
                "anomalyScore", AlgorithmMathUtils.round(score.compositeScore(), 4),
                "dominantFactor", score.dominantFactor(),
                "volumeDeviation", AlgorithmMathUtils.round(score.volumeDeviation(), 4),
                "entropyDeviation", AlgorithmMathUtils.round(score.entropyDeviation(), 4),
                "burstiDeviation", AlgorithmMathUtils.round(score.burstinessDeviation(), 4),
                "bucketFlowCount", bucket.flowCount(),
                "totalBytes", bucket.totalBytes(),
                "protocolEntropy", AlgorithmMathUtils.round(bucket.protocolEntropy(), 4)
        )));
        alert.setConfirmed(Boolean.FALSE);

        if (bucket.topSrcIp() != null) {
            GeoLocationService.GeoLocation loc = geoLocationService.locate(bucket.topSrcIp());
            alert.setCountry(loc.country());
            alert.setCity(loc.city());
            alert.setLatitude(loc.latitude());
            alert.setLongitude(loc.longitude());
        }
        return alert;
    }

    private void saveNewAlerts(List<SecurityAlert> alerts, LocalDateTime start, LocalDateTime end) {
        Set<String> existing = alertRepository.findByDetectedTimeBetweenOrderByDetectedTimeDesc(
                        start.minusMinutes(30), end.plusMinutes(30))
                .stream()
                .filter(a -> "EWMA-EAD".equals(a.getAlertType()))
                .map(a -> a.getDetectedTime().toString())
                .collect(Collectors.toCollection(HashSet::new));

        for (SecurityAlert alert : alerts) {
            if (existing.add(alert.getDetectedTime().toString())) {
                alertRepository.save(alert);
            }
        }
    }

    private String findTopIp(List<NetworkFlow> flows, boolean source) {
        return flows.stream()
                .collect(Collectors.groupingBy(
                        f -> source ? f.getSrcIp() : f.getDstIp(),
                        Collectors.summingLong(f -> f.getBytesSent() + f.getBytesRecv())
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private LocalDateTime alignDown(LocalDateTime time, int bucketMinutes) {
        int aligned = time.getMinute() / bucketMinutes * bucketMinutes;
        return time.withMinute(aligned).withSecond(0).withNano(0);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private record BucketStats(LocalDateTime startTime, double totalBytes, double protocolEntropy,
                               double burstinessCoefficient, String topSrcIp, String topDstIp, int flowCount) {}

    private record AnomalyScore(double volumeDeviation, double entropyDeviation, double burstinessDeviation,
                                double compositeScore, String dominantFactor) {
        boolean isAnomaly() {
            return compositeScore >= ANOMALY_SCORE_THRESHOLD;
        }
    }

    private record EwmaState(double mean, double variance) {}
}
