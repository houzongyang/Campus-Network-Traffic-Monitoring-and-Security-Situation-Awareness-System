package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.NetworkFlowRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Network Behavior Fingerprinting with Risk Scoring (NBF-RS)
 *
 * Innovative algorithm that fuses:
 *   1. IP Profile analysis (from FlowAnalysisService.buildIpProfile)
 *   2. Application protocol distribution (from FlowAnalysisService.getAppProtocolDistribution)
 *   3. Peer communication analysis (from FlowAnalysisService peer statistics)
 *   4. Geographic location (from GeoLocationService)
 *   5. Direction and region analysis (from DataImportService topology)
 *
 * Key innovation: Creates a multi-dimensional behavioral fingerprint vector for each
 * active IP, then computes a composite risk score using weighted multi-factor scoring.
 *
 * Fingerprint vector dimensions (7D):
 *   F1: Protocol Entropy (Shannon entropy of app-protocol distribution)
 *   F2: Peer Diversity Index (normalized unique peer count)
 *   F3: Traffic Asymmetry Ratio (outbound / total bytes)
 *   F4: Port Dispersion Index (entropy of destination port distribution)
 *   F5: Temporal Activity Concentration (Gini coefficient of time-bucket activity)
 *   F6: Connection Intensity (flows per minute per peer)
 *   F7: Average Packet Size Deviation (deviation from network-wide mean)
 *
 * Risk Score formula:
 *   RiskScore = Sum(w_i * normalize(f_i)) where w_i are learned/configured weights
 */
@Service
public class BehaviorFingerprintService {

    private static final double W_PROTOCOL_ENTROPY = 0.15;
    private static final double W_PEER_DIVERSITY = 0.15;
    private static final double W_ASYMMETRY = 0.20;
    private static final double W_PORT_DISPERSION = 0.15;
    private static final double W_TEMPORAL_CONCENTRATION = 0.10;
    private static final double W_CONNECTION_INTENSITY = 0.15;
    private static final double W_PACKET_SIZE_DEVIATION = 0.10;

    private static final int TEMPORAL_BUCKETS = 12;
    private static final double HIGH_RISK_THRESHOLD = 0.70;
    private static final double MEDIUM_RISK_THRESHOLD = 0.45;

    private final NetworkFlowRepository flowRepository;
    private final GeoLocationService geoLocationService;

    public BehaviorFingerprintService(NetworkFlowRepository flowRepository, GeoLocationService geoLocationService) {
        this.flowRepository = flowRepository;
        this.geoLocationService = geoLocationService;
    }

    /**
     * Compute behavioral fingerprints and risk scores for all active IPs.
     */
    public Map<String, Object> analyzeAllIps(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> allFlows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        if (allFlows.isEmpty()) {
            return Map.of("algorithm", "NBF-RS", "fingerprints", List.of(), "clusterAnalysis", Map.of());
        }

        double networkAvgPacketSize = computeNetworkAvgPacketSize(allFlows);

        Set<String> activeIps = new HashSet<>();
        allFlows.forEach(f -> {
            activeIps.add(f.getSrcIp());
            activeIps.add(f.getDstIp());
        });

        List<Map<String, Object>> fingerprints = new ArrayList<>();
        for (String ip : activeIps) {
            List<NetworkFlow> ipFlows = allFlows.stream()
                    .filter(f -> ip.equals(f.getSrcIp()) || ip.equals(f.getDstIp()))
                    .toList();

            if (ipFlows.size() < 3) continue;

            IpFingerprint fp = computeFingerprint(ip, ipFlows, startTime, endTime, networkAvgPacketSize);
            double riskScore = computeRiskScore(fp);
            fingerprints.add(buildFingerprintMap(ip, fp, riskScore));
        }

        fingerprints.sort((a, b) -> Double.compare(
                (double) b.get("riskScore"),
                (double) a.get("riskScore")
        ));

        Map<String, Object> clusterAnalysis = performBehaviorClustering(fingerprints);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "NBF-RS");
        result.put("analysisWindow", Map.of("start", startTime, "end", endTime));
        result.put("totalActiveIps", fingerprints.size());
        result.put("highRiskCount", fingerprints.stream()
                .filter(f -> (double) f.get("riskScore") >= HIGH_RISK_THRESHOLD).count());
        result.put("mediumRiskCount", fingerprints.stream()
                .filter(f -> {
                    double s = (double) f.get("riskScore");
                    return s >= MEDIUM_RISK_THRESHOLD && s < HIGH_RISK_THRESHOLD;
                }).count());
        result.put("fingerprints", fingerprints.stream().limit(50).toList());
        result.put("clusterAnalysis", clusterAnalysis);
        result.put("featureWeights", Map.of(
                "protocolEntropy", W_PROTOCOL_ENTROPY,
                "peerDiversity", W_PEER_DIVERSITY,
                "trafficAsymmetry", W_ASYMMETRY,
                "portDispersion", W_PORT_DISPERSION,
                "temporalConcentration", W_TEMPORAL_CONCENTRATION,
                "connectionIntensity", W_CONNECTION_INTENSITY,
                "packetSizeDeviation", W_PACKET_SIZE_DEVIATION
        ));
        return result;
    }

    /**
     * Get a single IP's behavioral fingerprint and risk assessment.
     */
    public Map<String, Object> analyzeIp(String ip, LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> allFlows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        double networkAvgPacketSize = computeNetworkAvgPacketSize(allFlows);

        List<NetworkFlow> ipFlows = allFlows.stream()
                .filter(f -> ip.equals(f.getSrcIp()) || ip.equals(f.getDstIp()))
                .toList();

        if (ipFlows.size() < 2) {
            return Map.of("algorithm", "NBF-RS", "ip", ip, "message", "Insufficient flow data");
        }

        IpFingerprint fp = computeFingerprint(ip, ipFlows, startTime, endTime, networkAvgPacketSize);
        double riskScore = computeRiskScore(fp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "NBF-RS");
        result.put("ip", ip);
        result.put("isInternal", geoLocationService.isPrivateIp(ip));
        result.put("riskScore", round(riskScore, 4));
        result.put("riskLevel", riskScore >= HIGH_RISK_THRESHOLD ? "high" : riskScore >= MEDIUM_RISK_THRESHOLD ? "medium" : "low");
        result.put("fingerprint", buildFingerprintVector(fp));
        result.put("factorContributions", buildFactorContributions(fp));
        result.put("flowCount", ipFlows.size());

        GeoLocationService.GeoLocation geo = geoLocationService.locate(ip);
        result.put("geoLocation", Map.of(
                "country", geo.country(),
                "city", geo.city(),
                "latitude", geo.latitude(),
                "longitude", geo.longitude()
        ));
        return result;
    }

    private IpFingerprint computeFingerprint(String ip, List<NetworkFlow> flows,
                                              LocalDateTime start, LocalDateTime end,
                                              double networkAvgPacketSize) {
        double protocolEntropy = computeProtocolEntropy(flows);
        double peerDiversity = computePeerDiversity(ip, flows);
        double asymmetryRatio = computeAsymmetryRatio(ip, flows);
        double portDispersion = computePortDispersion(flows);
        double temporalConcentration = computeTemporalConcentration(flows, start, end);
        double connectionIntensity = computeConnectionIntensity(ip, flows, start, end);
        double packetSizeDeviation = computePacketSizeDeviation(flows, networkAvgPacketSize);

        return new IpFingerprint(
                protocolEntropy,
                peerDiversity,
                asymmetryRatio,
                portDispersion,
                temporalConcentration,
                connectionIntensity,
                packetSizeDeviation
        );
    }

    /**
     * F1: Shannon entropy of application protocol distribution.
     * Low entropy = concentrated on few protocols = potentially suspicious.
     * Very high entropy = unusual diversity = potentially scanning.
     */
    private double computeProtocolEntropy(List<NetworkFlow> flows) {
        Map<String, Long> counts = flows.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getAppProtocol() == null ? "Unknown" : f.getAppProtocol(),
                        Collectors.counting()
                ));
        double total = flows.size();
        double entropy = 0.0;
        for (long count : counts.values()) {
            if (count > 0) {
                double p = count / total;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    /**
     * F2: Peer Diversity Index.
     * Normalized ratio of unique peers to total flows.
     * High diversity with low volume per peer can indicate scanning behavior.
     */
    private double computePeerDiversity(String ip, List<NetworkFlow> flows) {
        Set<String> peers = new HashSet<>();
        for (NetworkFlow f : flows) {
            if (ip.equals(f.getSrcIp())) peers.add(f.getDstIp());
            else peers.add(f.getSrcIp());
        }
        return flows.isEmpty() ? 0.0 : (double) peers.size() / Math.sqrt(flows.size());
    }

    /**
     * F3: Traffic Asymmetry Ratio.
     * ratio = outboundBytes / totalBytes.
     * Values close to 1.0 indicate heavy uploading (potential exfiltration).
     * Values close to 0.0 indicate heavy downloading (potential C2 beacon receiving).
     */
    private double computeAsymmetryRatio(String ip, List<NetworkFlow> flows) {
        long outbound = 0;
        long total = 0;
        for (NetworkFlow f : flows) {
            long bytes = f.getBytesSent() + f.getBytesRecv();
            total += bytes;
            if (ip.equals(f.getSrcIp())) {
                outbound += f.getBytesSent();
            } else {
                outbound += f.getBytesRecv();
            }
        }
        return total == 0 ? 0.5 : (double) outbound / total;
    }

    /**
     * F4: Port Dispersion Index.
     * Shannon entropy of destination port distribution.
     * High dispersion = accessing many different ports = potential scanning.
     */
    private double computePortDispersion(List<NetworkFlow> flows) {
        Map<Integer, Long> portCounts = flows.stream()
                .collect(Collectors.groupingBy(NetworkFlow::getDstPort, Collectors.counting()));

        double total = flows.size();
        double entropy = 0.0;
        for (long count : portCounts.values()) {
            if (count > 0) {
                double p = count / total;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    /**
     * F5: Temporal Activity Concentration (Gini Coefficient).
     * Measures how concentrated the IP's activity is in time.
     * High Gini = activity concentrated in few time buckets = bursty behavior.
     */
    private double computeTemporalConcentration(List<NetworkFlow> flows, LocalDateTime start, LocalDateTime end) {
        long totalMinutes = Math.max(1, ChronoUnit.MINUTES.between(start, end));
        int bucketMinutes = Math.max(1, (int) (totalMinutes / TEMPORAL_BUCKETS));

        int[] bucketCounts = new int[TEMPORAL_BUCKETS + 1];
        for (NetworkFlow f : flows) {
            long minuteOffset = ChronoUnit.MINUTES.between(start, f.getTimestamp());
            int bucketIdx = Math.min(TEMPORAL_BUCKETS, Math.max(0, (int) (minuteOffset / bucketMinutes)));
            bucketCounts[bucketIdx]++;
        }

        return computeGiniCoefficient(bucketCounts);
    }

    /**
     * Gini coefficient calculation.
     * 0 = perfectly equal distribution, 1 = perfectly concentrated.
     */
    private double computeGiniCoefficient(int[] values) {
        int n = values.length;
        if (n == 0) return 0.0;

        double sum = 0;
        for (int v : values) sum += v;
        if (sum == 0) return 0.0;

        int[] sorted = values.clone();
        java.util.Arrays.sort(sorted);

        double cumulativeSum = 0;
        double giniNumerator = 0;
        for (int i = 0; i < n; i++) {
            cumulativeSum += sorted[i];
            giniNumerator += (2.0 * (i + 1) - n - 1) * sorted[i];
        }
        return giniNumerator / (n * sum);
    }

    /**
     * F6: Connection Intensity.
     * Flows per minute per unique peer. High intensity = aggressive communication.
     */
    private double computeConnectionIntensity(String ip, List<NetworkFlow> flows,
                                               LocalDateTime start, LocalDateTime end) {
        Set<String> peers = new HashSet<>();
        for (NetworkFlow f : flows) {
            if (ip.equals(f.getSrcIp())) peers.add(f.getDstIp());
            else peers.add(f.getSrcIp());
        }

        double minutes = Math.max(1.0, ChronoUnit.MINUTES.between(start, end));
        double peerCount = Math.max(1.0, peers.size());
        return flows.size() / (minutes * peerCount);
    }

    /**
     * F7: Average Packet Size Deviation.
     * Measures how much this IP's average packet size deviates from network mean.
     * Large deviations can indicate unusual traffic patterns (e.g., tunneling, exfiltration).
     */
    private double computePacketSizeDeviation(List<NetworkFlow> flows, double networkAvg) {
        if (networkAvg < 1.0 || flows.isEmpty()) return 0.0;

        double ipAvg = flows.stream()
                .mapToDouble(f -> {
                    long totalPackets = f.getPacketsSent() + f.getPacketsRecv();
                    long totalBytes = f.getBytesSent() + f.getBytesRecv();
                    return totalPackets > 0 ? (double) totalBytes / totalPackets : 0.0;
                })
                .average()
                .orElse(networkAvg);

        return Math.abs(ipAvg - networkAvg) / networkAvg;
    }

    private double computeNetworkAvgPacketSize(List<NetworkFlow> flows) {
        long totalBytes = flows.stream().mapToLong(f -> f.getBytesSent() + f.getBytesRecv()).sum();
        long totalPackets = flows.stream().mapToLong(f -> f.getPacketsSent() + f.getPacketsRecv()).sum();
        return totalPackets > 0 ? (double) totalBytes / totalPackets : 512.0;
    }

    /**
     * Compute composite risk score using weighted normalized factors.
     * Anomaly-sensitive normalization: both extremes (too high or too low) contribute to risk.
     */
    private double computeRiskScore(IpFingerprint fp) {
        double normEntropy = anomalySensitiveNorm(fp.protocolEntropy(), 1.5, 3.5);
        double normPeerDiv = saturate(fp.peerDiversity() / 3.0);
        double normAsymmetry = 2.0 * Math.abs(fp.asymmetryRatio() - 0.5);
        double normPortDisp = anomalySensitiveNorm(fp.portDispersion(), 2.0, 5.0);
        double normTempConc = saturate(fp.temporalConcentration());
        double normConnInt = saturate(fp.connectionIntensity() / 2.0);
        double normPktDev = saturate(fp.packetSizeDeviation());

        return W_PROTOCOL_ENTROPY * normEntropy
                + W_PEER_DIVERSITY * normPeerDiv
                + W_ASYMMETRY * normAsymmetry
                + W_PORT_DISPERSION * normPortDisp
                + W_TEMPORAL_CONCENTRATION * normTempConc
                + W_CONNECTION_INTENSITY * normConnInt
                + W_PACKET_SIZE_DEVIATION * normPktDev;
    }

    /**
     * Anomaly-sensitive normalization: values outside [low, high] range are penalized.
     */
    private double anomalySensitiveNorm(double value, double normalLow, double normalHigh) {
        if (value < normalLow) return saturate((normalLow - value) / normalLow);
        if (value > normalHigh) return saturate((value - normalHigh) / normalHigh);
        return 0.0;
    }

    private double saturate(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Simple K-means-like behavior clustering based on fingerprint similarity.
     * Groups IPs into Normal, Suspicious, and Malicious behavioral clusters.
     */
    private Map<String, Object> performBehaviorClustering(List<Map<String, Object>> fingerprints) {
        List<Map<String, Object>> normal = new ArrayList<>();
        List<Map<String, Object>> suspicious = new ArrayList<>();
        List<Map<String, Object>> malicious = new ArrayList<>();

        for (Map<String, Object> fp : fingerprints) {
            double risk = (double) fp.get("riskScore");
            if (risk >= HIGH_RISK_THRESHOLD) malicious.add(fp);
            else if (risk >= MEDIUM_RISK_THRESHOLD) suspicious.add(fp);
            else normal.add(fp);
        }

        Map<String, Object> clusters = new LinkedHashMap<>();
        clusters.put("normal", Map.of(
                "count", normal.size(),
                "description", "Normal behavioral pattern, low risk",
                "avgRiskScore", normal.stream().mapToDouble(f -> (double) f.get("riskScore")).average().orElse(0.0)
        ));
        clusters.put("suspicious", Map.of(
                "count", suspicious.size(),
                "description", "Deviating from baseline, requires monitoring",
                "avgRiskScore", suspicious.stream().mapToDouble(f -> (double) f.get("riskScore")).average().orElse(0.0),
                "topIps", suspicious.stream().limit(10).map(f -> f.get("ip")).toList()
        ));
        clusters.put("malicious", Map.of(
                "count", malicious.size(),
                "description", "Highly anomalous behavior, potential threat",
                "avgRiskScore", malicious.stream().mapToDouble(f -> (double) f.get("riskScore")).average().orElse(0.0),
                "topIps", malicious.stream().limit(10).map(f -> f.get("ip")).toList()
        ));
        return clusters;
    }

    private Map<String, Object> buildFingerprintMap(String ip, IpFingerprint fp, double riskScore) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ip", ip);
        map.put("isInternal", geoLocationService.isPrivateIp(ip));
        map.put("riskScore", round(riskScore, 4));
        map.put("riskLevel", riskScore >= HIGH_RISK_THRESHOLD ? "high" : riskScore >= MEDIUM_RISK_THRESHOLD ? "medium" : "low");
        map.put("fingerprint", buildFingerprintVector(fp));
        return map;
    }

    private Map<String, Object> buildFingerprintVector(IpFingerprint fp) {
        Map<String, Object> vec = new LinkedHashMap<>();
        vec.put("F1_protocolEntropy", round(fp.protocolEntropy(), 4));
        vec.put("F2_peerDiversity", round(fp.peerDiversity(), 4));
        vec.put("F3_trafficAsymmetry", round(fp.asymmetryRatio(), 4));
        vec.put("F4_portDispersion", round(fp.portDispersion(), 4));
        vec.put("F5_temporalConcentration", round(fp.temporalConcentration(), 4));
        vec.put("F6_connectionIntensity", round(fp.connectionIntensity(), 4));
        vec.put("F7_packetSizeDeviation", round(fp.packetSizeDeviation(), 4));
        return vec;
    }

    private Map<String, Object> buildFactorContributions(IpFingerprint fp) {
        double normEntropy = anomalySensitiveNorm(fp.protocolEntropy(), 1.5, 3.5);
        double normPeerDiv = saturate(fp.peerDiversity() / 3.0);
        double normAsymmetry = 2.0 * Math.abs(fp.asymmetryRatio() - 0.5);
        double normPortDisp = anomalySensitiveNorm(fp.portDispersion(), 2.0, 5.0);
        double normTempConc = saturate(fp.temporalConcentration());
        double normConnInt = saturate(fp.connectionIntensity() / 2.0);
        double normPktDev = saturate(fp.packetSizeDeviation());

        Map<String, Object> contributions = new LinkedHashMap<>();
        contributions.put("protocolEntropy", Map.of("raw", round(fp.protocolEntropy(), 4), "normalized", round(normEntropy, 4), "weighted", round(W_PROTOCOL_ENTROPY * normEntropy, 4)));
        contributions.put("peerDiversity", Map.of("raw", round(fp.peerDiversity(), 4), "normalized", round(normPeerDiv, 4), "weighted", round(W_PEER_DIVERSITY * normPeerDiv, 4)));
        contributions.put("trafficAsymmetry", Map.of("raw", round(fp.asymmetryRatio(), 4), "normalized", round(normAsymmetry, 4), "weighted", round(W_ASYMMETRY * normAsymmetry, 4)));
        contributions.put("portDispersion", Map.of("raw", round(fp.portDispersion(), 4), "normalized", round(normPortDisp, 4), "weighted", round(W_PORT_DISPERSION * normPortDisp, 4)));
        contributions.put("temporalConcentration", Map.of("raw", round(fp.temporalConcentration(), 4), "normalized", round(normTempConc, 4), "weighted", round(W_TEMPORAL_CONCENTRATION * normTempConc, 4)));
        contributions.put("connectionIntensity", Map.of("raw", round(fp.connectionIntensity(), 4), "normalized", round(normConnInt, 4), "weighted", round(W_CONNECTION_INTENSITY * normConnInt, 4)));
        contributions.put("packetSizeDeviation", Map.of("raw", round(fp.packetSizeDeviation(), 4), "normalized", round(normPktDev, 4), "weighted", round(W_PACKET_SIZE_DEVIATION * normPktDev, 4)));
        return contributions;
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private record IpFingerprint(double protocolEntropy, double peerDiversity, double asymmetryRatio,
                                  double portDispersion, double temporalConcentration,
                                  double connectionIntensity, double packetSizeDeviation) {}
}
