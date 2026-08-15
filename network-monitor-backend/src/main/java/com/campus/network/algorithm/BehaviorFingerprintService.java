package com.campus.network.algorithm;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.service.GeoLocationService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 网络行为指纹与风险评分算法（NBF-RS）。
 *
 * 原创性边界：本项目原创部分是面向校园网络场景的七维行为指纹组合、异常敏感归一化、
 * 加权风险评分流程、贡献度解释输出以及与现有流量画像/地理位置能力的工程落地。
 * 基础理论和度量方法不是本项目原创：Shannon 信息熵用于度量协议和端口分布离散程度，
 * Gini 系数来源于经济学中的不平等度量，加权求和与归一化属于通用统计建模方法。
 *
 * 算法输入来自系统已有能力：IP 流量画像、应用协议分布、对端通信统计、地理位置判断、
 * 方向与区域字段。算法将这些输入组织为七维向量，再计算可解释的综合风险分。
 *
 * 指纹维度说明：
 * F1：协议熵，使用 Shannon 信息熵度量应用协议分布。
 * F2：对端多样性，衡量唯一通信对端数量相对流量规模的离散程度。
 * F3：流量不对称度，衡量上传/下载方向是否明显偏斜。
 * F4：端口离散度，使用 Shannon 信息熵度量目标端口分布。
 * F5：时间集中度，采用 Gini 系数刻画活动是否集中在少数时间桶。
 * F6：连接强度，衡量单位时间、单位对端上的流数量。
 * F7：平均包大小偏差，衡量单 IP 包大小相对全网均值的偏离。
 *
 * 风险分公式：综合风险分 = Σ(权重 × 归一化特征)。
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
     * 计算所有活跃 IP 的行为指纹和风险评分。
     * 为每个活跃 IP 构建 7 维行为指纹，按加权风险分排序并聚类输出。
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
            FingerprintRisk risk = scoreFingerprint(fp);
            fingerprints.add(buildFingerprintMap(ip, fp, risk.totalScore()));
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
     * 查询单个 IP 的行为指纹和风险评估结果。
     * 中文说明：单 IP 查询复用同一套 7 维指纹和风险评分公式，保证全局/局部结果一致。
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
        FingerprintRisk risk = scoreFingerprint(fp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "NBF-RS");
        result.put("ip", ip);
        result.put("isInternal", geoLocationService.isPrivateIp(ip));
        result.put("riskScore", AlgorithmMathUtils.round(risk.totalScore(), 4));
        result.put("riskLevel", risk.totalScore() >= HIGH_RISK_THRESHOLD ? "high" : risk.totalScore() >= MEDIUM_RISK_THRESHOLD ? "medium" : "low");
        result.put("fingerprint", buildFingerprintVector(fp));
        result.put("factorContributions", buildFactorContributions(fp, risk));
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
        // F1~F7：保持每个维度独立计算，便于说明行为指纹向量的含义。
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
     * F1：应用协议 Shannon 信息熵；过低代表协议过度集中，过高代表异常多样化访问。
     */
    private double computeProtocolEntropy(List<NetworkFlow> flows) {
        Map<String, Long> counts = flows.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getAppProtocol() == null ? "Unknown" : f.getAppProtocol(),
                        Collectors.counting()
                ));
        return AlgorithmMathUtils.shannonEntropy(counts.values());
    }

    /**
     * F2：对端多样性，计算方式为“对端数量 / 流数量平方根”；对端越分散越接近扫描或横向移动行为。
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
     * F3：流量不对称度，计算方式为“出向字节数 / 总字节数”；接近 1 偏上传，接近 0 偏下载。
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
     * F4：目标端口分散度，端口熵越高表示访问端口越分散。
     */
    private double computePortDispersion(List<NetworkFlow> flows) {
        Map<Integer, Long> portCounts = flows.stream()
                .collect(Collectors.groupingBy(NetworkFlow::getDstPort, Collectors.counting()));

        return AlgorithmMathUtils.shannonEntropy(portCounts.values());
    }

    /**
     * F5：时间集中度（Gini 系数）；活动越集中在少数时间桶，突发风险越高。
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
     * Gini 系数计算：先排序，再按通用 Gini 公式衡量时间桶流量分布的不均衡程度。
     * G 越接近 0 表示时间分布越均匀，越接近 1 表示活动越集中。
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
     * F6：连接强度，计算方式为“流数量 /（分钟数 × 对端数量）”，刻画单位时间单对端通信密度。
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
     * F7：平均包大小偏差；偏离全网均值越大，越可能存在隧道、外传等异常模式。
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

    /**
     * 全网平均包大小：作为 F7 偏差特征的参照基线，缺失时使用保守默认值。
     */
    private double computeNetworkAvgPacketSize(List<NetworkFlow> flows) {
        long totalBytes = flows.stream().mapToLong(f -> f.getBytesSent() + f.getBytesRecv()).sum();
        long totalPackets = flows.stream().mapToLong(f -> f.getPacketsSent() + f.getPacketsRecv()).sum();
        return totalPackets > 0 ? (double) totalBytes / totalPackets : 512.0;
    }

    /**
     * 风险评分：Σ(权重 × 归一化特征)，同时保留归一化后的中间值用于贡献度解释。
     */
    private FingerprintRisk scoreFingerprint(IpFingerprint fp) {
        double normEntropy = anomalySensitiveNorm(fp.protocolEntropy(), 1.5, 3.5);
        double normPeerDiv = AlgorithmMathUtils.clamp01(fp.peerDiversity() / 3.0);
        double normAsymmetry = 2.0 * Math.abs(fp.asymmetryRatio() - 0.5);
        double normPortDisp = anomalySensitiveNorm(fp.portDispersion(), 2.0, 5.0);
        double normTempConc = AlgorithmMathUtils.clamp01(fp.temporalConcentration());
        double normConnInt = AlgorithmMathUtils.clamp01(fp.connectionIntensity() / 2.0);
        double normPktDev = AlgorithmMathUtils.clamp01(fp.packetSizeDeviation());

        double totalScore = W_PROTOCOL_ENTROPY * normEntropy
                + W_PEER_DIVERSITY * normPeerDiv
                + W_ASYMMETRY * normAsymmetry
                + W_PORT_DISPERSION * normPortDisp
                + W_TEMPORAL_CONCENTRATION * normTempConc
                + W_CONNECTION_INTENSITY * normConnInt
                + W_PACKET_SIZE_DEVIATION * normPktDev;

        return new FingerprintRisk(normEntropy, normPeerDiv, normAsymmetry, normPortDisp,
                normTempConc, normConnInt, normPktDev, totalScore);
    }

    /**
     * 异常敏感归一化：仅当特征落在正常范围外时产生风险分，正常范围内返回 0。
     */
    private double anomalySensitiveNorm(double value, double normalLow, double normalHigh) {
        if (value < normalLow) return AlgorithmMathUtils.clamp01((normalLow - value) / normalLow);
        if (value > normalHigh) return AlgorithmMathUtils.clamp01((value - normalHigh) / normalHigh);
        return 0.0;
    }

    /**
     * 简化行为聚类：根据风险分段和指纹相似性进行分组。
     * 将 IP 汇总为正常、可疑和高风险三类行为簇，便于前端展示。
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
        map.put("riskScore", AlgorithmMathUtils.round(riskScore, 4));
        map.put("riskLevel", riskScore >= HIGH_RISK_THRESHOLD ? "high" : riskScore >= MEDIUM_RISK_THRESHOLD ? "medium" : "low");
        map.put("fingerprint", buildFingerprintVector(fp));
        return map;
    }

    private Map<String, Object> buildFingerprintVector(IpFingerprint fp) {
        Map<String, Object> vec = new LinkedHashMap<>();
        vec.put("F1_protocolEntropy", AlgorithmMathUtils.round(fp.protocolEntropy(), 4));
        vec.put("F2_peerDiversity", AlgorithmMathUtils.round(fp.peerDiversity(), 4));
        vec.put("F3_trafficAsymmetry", AlgorithmMathUtils.round(fp.asymmetryRatio(), 4));
        vec.put("F4_portDispersion", AlgorithmMathUtils.round(fp.portDispersion(), 4));
        vec.put("F5_temporalConcentration", AlgorithmMathUtils.round(fp.temporalConcentration(), 4));
        vec.put("F6_connectionIntensity", AlgorithmMathUtils.round(fp.connectionIntensity(), 4));
        vec.put("F7_packetSizeDeviation", AlgorithmMathUtils.round(fp.packetSizeDeviation(), 4));
        return vec;
    }

    private Map<String, Object> buildFactorContributions(IpFingerprint fp, FingerprintRisk risk) {
        Map<String, Object> contributions = new LinkedHashMap<>();
        contributions.put("protocolEntropy", contribution(fp.protocolEntropy(), risk.normalizedEntropy(), W_PROTOCOL_ENTROPY));
        contributions.put("peerDiversity", contribution(fp.peerDiversity(), risk.normalizedPeerDiversity(), W_PEER_DIVERSITY));
        contributions.put("trafficAsymmetry", contribution(fp.asymmetryRatio(), risk.normalizedAsymmetry(), W_ASYMMETRY));
        contributions.put("portDispersion", contribution(fp.portDispersion(), risk.normalizedPortDispersion(), W_PORT_DISPERSION));
        contributions.put("temporalConcentration", contribution(fp.temporalConcentration(), risk.normalizedTemporalConcentration(), W_TEMPORAL_CONCENTRATION));
        contributions.put("connectionIntensity", contribution(fp.connectionIntensity(), risk.normalizedConnectionIntensity(), W_CONNECTION_INTENSITY));
        contributions.put("packetSizeDeviation", contribution(fp.packetSizeDeviation(), risk.normalizedPacketSizeDeviation(), W_PACKET_SIZE_DEVIATION));
        return contributions;
    }

    private Map<String, Object> contribution(double raw, double normalized, double weight) {
        return Map.of(
                "raw", AlgorithmMathUtils.round(raw, 4),
                "normalized", AlgorithmMathUtils.round(normalized, 4),
                "weighted", AlgorithmMathUtils.round(weight * normalized, 4)
        );
    }

    private record IpFingerprint(double protocolEntropy, double peerDiversity, double asymmetryRatio,
                                  double portDispersion, double temporalConcentration,
                                  double connectionIntensity, double packetSizeDeviation) {}

    private record FingerprintRisk(double normalizedEntropy, double normalizedPeerDiversity,
                                   double normalizedAsymmetry, double normalizedPortDispersion,
                                   double normalizedTemporalConcentration, double normalizedConnectionIntensity,
                                   double normalizedPacketSizeDeviation, double totalScore) {}
}
