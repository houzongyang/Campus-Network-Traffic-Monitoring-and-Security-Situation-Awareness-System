package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.repository.SecurityAlertRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * EWMA-Entropy Adaptive Anomaly Detection (EWMA-EAD)
 *
 * Innovative algorithm that fuses three existing subsystems:
 *   1. Dynamic threshold mechanism (from ThreatDetectionService)
 *   2. Throughput trend analysis (from FlowAnalysisService)
 *   3. Protocol distribution analysis (from FlowAnalysisService)
 *
 * Key innovation: Instead of relying on simple mean×multiplier thresholds,
 * this algorithm combines Exponentially Weighted Moving Average (EWMA) for
 * adaptive baseline tracking with Shannon entropy of protocol/port distributions
 * to detect anomalies that may not exceed volume thresholds but exhibit
 * abnormal behavioral patterns.
 *
 * Composite Anomaly Score formula:
 *   AnomalyScore = alpha × VolumeDeviation(EWMA) + beta × EntropyDeviation + gamma × TemporalBurstiness
 *
 * Where:
 *   - VolumeDeviation: Z-score of current volume against EWMA baseline
 *   - EntropyDeviation: Normalized divergence of current entropy from baseline entropy
 *   - TemporalBurstiness: Coefficient of variation of inter-arrival times
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
     * Run the full EWMA-EAD detection pipeline.
     * Analyzes the detection window against a historical baseline window.
     */
    public Map<String, Object> runDetection(LocalDateTime detectionStart, LocalDateTime detectionEnd) {
        LocalDateTime baselineStart = detectionStart.minusHours(1);

        List<NetworkFlow> baselineFlows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(baselineStart, detectionStart);
        List<NetworkFlow> detectionFlows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(detectionStart, detectionEnd);

        List<BucketStats> baselineBuckets = buildTimeBuckets(baselineFlows, baselineStart, detectionStart, BUCKET_MINUTES);
        List<BucketStats> detectionBuckets = buildTimeBuckets(detectionFlows, detectionStart, detectionEnd, BUCKET_MINUTES);

        EwmaState volumeEwma = trainEwmaFromBuckets(baselineBuckets, BucketStats::totalBytes);
        EwmaState entropyEwma = trainEwmaFromBuckets(baselineBuckets, BucketStats::protocolEntropy);
        EwmaState burstiEwma = trainEwmaFromBuckets(baselineBuckets, BucketStats::burstinessCoefficient);

        List<Map<String, Object>> anomalyTimeline = new ArrayList<>();
        List<SecurityAlert> generatedAlerts = new ArrayList<>();

        for (BucketStats bucket : detectionBuckets) {
            double volumeDeviation = computeZScore(bucket.totalBytes(), volumeEwma);
            double entropyDeviation = computeZScore(bucket.protocolEntropy(), entropyEwma);
            double burstiDeviation = computeZScore(bucket.burstinessCoefficient(), burstiEwma);

            double normalizedVolume = sigmoid(volumeDeviation);
            double normalizedEntropy = sigmoid(entropyDeviation);
            double normalizedBursti = sigmoid(burstiDeviation);

            double anomalyScore = SCORE_WEIGHT_VOLUME * normalizedVolume
                    + SCORE_WEIGHT_ENTROPY * normalizedEntropy
                    + SCORE_WEIGHT_BURSTINESS * normalizedBursti;

            volumeEwma = updateEwma(volumeEwma, bucket.totalBytes());
            entropyEwma = updateEwma(entropyEwma, bucket.protocolEntropy());
            burstiEwma = updateEwma(burstiEwma, bucket.burstinessCoefficient());

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", bucket.startTime());
            point.put("anomalyScore", round(anomalyScore, 4));
            point.put("volumeDeviation", round(normalizedVolume, 4));
            point.put("entropyDeviation", round(normalizedEntropy, 4));
            point.put("burstiDeviation", round(normalizedBursti, 4));
            point.put("totalBytes", bucket.totalBytes());
            point.put("protocolEntropy", round(bucket.protocolEntropy(), 4));
            point.put("burstiness", round(bucket.burstinessCoefficient(), 4));
            point.put("isAnomaly", anomalyScore >= ANOMALY_SCORE_THRESHOLD);
            anomalyTimeline.add(point);

            if (anomalyScore >= ANOMALY_SCORE_THRESHOLD) {
                String dominantFactor = identifyDominantFactor(normalizedVolume, normalizedEntropy, normalizedBursti);
                SecurityAlert alert = buildEwmaAlert(bucket, anomalyScore, dominantFactor,
                        normalizedVolume, normalizedEntropy, normalizedBursti);
                generatedAlerts.add(alert);
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
     * Build time-bucketed statistics from flows, computing per-bucket:
     * volume, protocol entropy, and burstiness.
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
     * Shannon Entropy of protocol distribution.
     * H = -Sum(p_i * log2(p_i))
     * Measures diversity of protocol usage; a sudden drop (concentration)
     * or spike (dispersion) indicates behavioral anomaly.
     */
    private double computeShannonEntropy(List<NetworkFlow> flows) {
        if (flows.isEmpty()) return 0.0;

        Map<String, Long> protocolCounts = flows.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getAppProtocol() == null ? "Unknown" : f.getAppProtocol(),
                        Collectors.counting()
                ));

        double total = flows.size();
        double entropy = 0.0;
        for (long count : protocolCounts.values()) {
            if (count > 0) {
                double p = count / total;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    /**
     * Temporal Burstiness Coefficient.
     * Measures the coefficient of variation of inter-arrival times.
     * CV = sigma / mu, where sigma is std deviation and mu is mean of inter-arrival intervals.
     * High burstiness (CV >> 1) indicates bursty traffic patterns typical of attacks.
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
     * Train EWMA state from historical baseline buckets.
     */
    private EwmaState trainEwmaFromBuckets(List<BucketStats> buckets, java.util.function.ToDoubleFunction<BucketStats> extractor) {
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
     * Update EWMA state with a new observation.
     */
    private EwmaState updateEwma(EwmaState state, double value) {
        double diff = value - state.mean();
        double newMean = EWMA_ALPHA * value + (1 - EWMA_ALPHA) * state.mean();
        double newVariance = EWMA_VARIANCE_ALPHA * (diff * diff) + (1 - EWMA_VARIANCE_ALPHA) * state.variance();
        return new EwmaState(newMean, Math.max(newVariance, 0.001));
    }

    /**
     * Compute Z-score deviation from EWMA state.
     */
    private double computeZScore(double value, EwmaState state) {
        double stdDev = Math.sqrt(state.variance());
        if (stdDev < 0.001) return 0.0;
        return (value - state.mean()) / stdDev;
    }

    /**
     * Sigmoid normalization: maps Z-score to [0, 1].
     * Uses absolute value so both positive and negative deviations are flagged.
     */
    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-Math.abs(z) + 2));
    }

    private String identifyDominantFactor(double volume, double entropy, double burstiness) {
        if (volume >= entropy && volume >= burstiness) return "VolumeAnomaly";
        if (entropy >= volume && entropy >= burstiness) return "EntropyAnomaly";
        return "BurstinessAnomaly";
    }

    private SecurityAlert buildEwmaAlert(BucketStats bucket, double score, String dominantFactor,
                                         double volDev, double entDev, double burstDev) {
        SecurityAlert alert = new SecurityAlert();
        alert.setAlertType("EWMA-EAD");
        alert.setSeverity(score >= 0.85 ? "critical" : score >= 0.75 ? "high" : "medium");
        alert.setSrcIp(bucket.topSrcIp() != null ? bucket.topSrcIp() : "-");
        alert.setDstIp(bucket.topDstIp());
        alert.setDetectedTime(bucket.startTime());
        alert.setDescription("EWMA-Entropy adaptive detection: " + dominantFactor
                + " (score=" + round(score, 3) + ")");
        alert.setThreatDetails(toJson(Map.of(
                "anomalyScore", round(score, 4),
                "dominantFactor", dominantFactor,
                "volumeDeviation", round(volDev, 4),
                "entropyDeviation", round(entDev, 4),
                "burstiDeviation", round(burstDev, 4),
                "bucketFlowCount", bucket.flowCount(),
                "totalBytes", bucket.totalBytes(),
                "protocolEntropy", round(bucket.protocolEntropy(), 4)
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

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
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

    private record EwmaState(double mean, double variance) {}
}
