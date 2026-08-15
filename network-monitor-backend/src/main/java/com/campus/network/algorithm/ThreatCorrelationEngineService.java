package com.campus.network.algorithm;

import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.SecurityAlertRepository;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * 多维威胁关联推理引擎（MDTCE）。
 *
 * 原创性边界：本项目原创部分是面向校园网络告警数据的图建模、关联评分、阶段递进增强、
 * 攻击链输出和风险热力汇总等组合设计与工程落地。
 * 基础理论和模型不是本项目原创：Cyber Kill Chain 是通用安全分析模型，Jaccard 相似度用于集合重叠度量，
 * 指数衰减用于时间接近度建模，高斯核思想用于地理接近度平滑，并查集用于高效连通分量合并。
 *
 * 算法流程：将告警抽象为图节点，两两计算 IP 重叠、时间接近度、地理接近度和杀伤链阶段递进，
 * 达到阈值后建边，再用并查集聚合高相关告警，输出覆盖多个阶段的攻击链候选。
 */
@Service
public class ThreatCorrelationEngineService {

    private static final int CORRELATION_WINDOW_MINUTES = 30;
    private static final double TEMPORAL_DECAY_LAMBDA = 0.1;
    private static final double IP_OVERLAP_WEIGHT = 0.45;
    private static final double TEMPORAL_WEIGHT = 0.30;
    private static final double GEO_WEIGHT = 0.25;
    private static final double CHAIN_SCORE_THRESHOLD = 0.50;
    private static final double EDGE_SCORE_THRESHOLD = 0.20;
    private static final double KILL_CHAIN_PROGRESS_BONUS = 1.3;
    private static final int KILL_CHAIN_STAGE_COUNT = 4;

    private static final Map<String, Integer> KILL_CHAIN_STAGE = Map.of(
            "PortScan", 1,
            "SlowPortScan", 1,
            "Phishing", 2,
            "DDoS", 3,
            "WormPropagation", 3,
            "DataExfiltration", 4,
            "EWMA-EAD", 3
    );

    private static final Map<String, String> STAGE_NAMES = Map.of(
            "1", "Reconnaissance",
            "2", "Weaponization & Delivery",
            "3", "Exploitation & Command",
            "4", "Exfiltration & Impact"
    );

    private final SecurityAlertRepository alertRepository;

    public ThreatCorrelationEngineService(SecurityAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * 在给定时间窗口内运行完整告警关联引擎。
     * 将告警抽象为图节点，通过 IP、时间、地理三类相似度建边并检测攻击链。
     */
    public Map<String, Object> runCorrelationAnalysis(LocalDateTime startTime, LocalDateTime endTime) {
        List<SecurityAlert> alerts = alertRepository.findByDetectedTimeBetweenOrderByDetectedTimeDesc(startTime, endTime);

        if (alerts.size() < 2) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("algorithm", "MDTCE");
            result.put("attackChains", List.of());
            result.put("correlationGraph", List.of());
            result.put("riskHeatmap", Map.of());
            result.put("totalAlerts", alerts.size());
            result.put("message", "Insufficient alerts for correlation analysis");
            return result;
        }

        List<AlertNode> nodes = alerts.stream()
                .map(this::toAlertNode)
                .sorted(Comparator.comparing(n -> n.detectedTime))
                .toList();

        List<CorrelationEdge> edges = buildCorrelationGraph(nodes);
        List<Map<String, Object>> attackChains = detectAttackChains(nodes, edges);
        Map<String, Object> riskHeatmap = buildSourceRiskHeatmap(nodes, edges);
        List<Map<String, Object>> graphVisualization = buildGraphVisualization(nodes, edges);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", "MDTCE");
        result.put("analysisWindow", Map.of("start", startTime, "end", endTime));
        result.put("totalAlerts", alerts.size());
        result.put("correlationEdges", edges.size());
        result.put("attackChains", attackChains);
        result.put("correlationGraph", graphVisualization);
        result.put("sourceRiskHeatmap", riskHeatmap);
        result.put("killChainStages", STAGE_NAMES);
        return result;
    }

    /**
     * 构建告警关联图：两两计算关联分，仅保留达到边阈值的告警对。
     * 边权重 = 0.45×IP重叠 + 0.30×时间邻近 + 0.25×地理接近。
     */
    private List<CorrelationEdge> buildCorrelationGraph(List<AlertNode> nodes) {
        List<CorrelationEdge> edges = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                AlertNode a = nodes.get(i);
                AlertNode b = nodes.get(j);

                long minutesBetween = Math.abs(Duration.between(a.detectedTime, b.detectedTime).toMinutes());
                if (minutesBetween > CORRELATION_WINDOW_MINUTES) continue;
                if (a.alertType.equals(b.alertType) && a.srcIp.equals(b.srcIp)) continue;

                CorrelationScore score = scoreAlertPair(a, b, minutesBetween);
                if (score.totalScore() >= EDGE_SCORE_THRESHOLD) {
                    edges.add(new CorrelationEdge(i, j, score.totalScore(), score.ipOverlap(),
                            score.temporalProximity(), score.geoProximity(), score.killChainProgression()));
                }
            }
        }
        return edges;
    }

    /**
     * 告警对评分：IP 的 Jaccard 重叠度、时间接近度、地理接近度加权，并对杀伤链递进做温和放大。
     */
    private CorrelationScore scoreAlertPair(AlertNode a, AlertNode b, long minutesBetween) {
        double ipScore = computeIpOverlap(a, b);
        double temporalScore = Math.exp(-TEMPORAL_DECAY_LAMBDA * minutesBetween);
        double geoScore = computeGeoProximity(a, b);

        double weightedScore = IP_OVERLAP_WEIGHT * ipScore
                + TEMPORAL_WEIGHT * temporalScore
                + GEO_WEIGHT * geoScore;

        boolean killChainProgression = isKillChainProgression(a, b);
        double totalScore = killChainProgression
                ? Math.min(1.0, weightedScore * KILL_CHAIN_PROGRESS_BONUS)
                : weightedScore;

        return new CorrelationScore(totalScore, ipScore, temporalScore, geoScore, killChainProgression);
    }

    /**
     * 攻击链发现：用并查集合并高置信边，连通分量即潜在攻击链，
     * 输出包含两个及以上告警的分量，并按阶段覆盖度和严重度排序。
     */
    private List<Map<String, Object>> detectAttackChains(List<AlertNode> nodes, List<CorrelationEdge> edges) {
        int[] parent = new int[nodes.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;

        for (CorrelationEdge edge : edges) {
            if (edge.correlationScore >= CHAIN_SCORE_THRESHOLD) {
                union(parent, edge.fromIdx, edge.toIdx);
            }
        }

        Map<Integer, List<Integer>> components = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            components.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }

        List<Map<String, Object>> chains = new ArrayList<>();
        int chainId = 0;
        for (Map.Entry<Integer, List<Integer>> entry : components.entrySet()) {
            List<Integer> members = entry.getValue();
            if (members.size() < 2) continue;

            List<AlertNode> chainNodes = members.stream()
                    .map(nodes::get)
                    .sorted(Comparator.comparing(n -> n.detectedTime))
                    .toList();

            Set<Integer> stages = chainNodes.stream()
                    .map(n -> KILL_CHAIN_STAGE.getOrDefault(n.alertType, 0))
                    .filter(s -> s > 0)
                    .collect(Collectors.toSet());

            double chainSeverity = computeChainSeverity(chainNodes, stages);

            Map<String, Object> chain = new LinkedHashMap<>();
            chain.put("chainId", ++chainId);
            chain.put("alertCount", chainNodes.size());
            chain.put("killChainStages", stages.stream().sorted().toList());
            chain.put("stageNames", stages.stream().sorted()
                    .map(s -> STAGE_NAMES.getOrDefault(String.valueOf(s), "Unknown"))
                    .toList());
            chain.put("stagesCovered", stages.size());
            chain.put("totalStages", KILL_CHAIN_STAGE_COUNT);
            chain.put("chainSeverity", AlgorithmMathUtils.round(chainSeverity, 3));
            chain.put("severityLevel", chainSeverity >= 0.8 ? "critical" : chainSeverity >= 0.6 ? "high" : chainSeverity >= 0.4 ? "medium" : "low");
            chain.put("timeSpan", Map.of(
                    "start", chainNodes.get(0).detectedTime,
                    "end", chainNodes.get(chainNodes.size() - 1).detectedTime,
                    "durationMinutes", Duration.between(chainNodes.get(0).detectedTime,
                            chainNodes.get(chainNodes.size() - 1).detectedTime).toMinutes()
            ));
            chain.put("involvedIps", chainNodes.stream().map(n -> n.srcIp).distinct().toList());
            chain.put("alerts", chainNodes.stream().map(n -> Map.of(
                    "alertType", n.alertType,
                    "severity", n.severity,
                    "srcIp", n.srcIp,
                    "dstIp", n.dstIp != null ? n.dstIp : "-",
                    "detectedTime", n.detectedTime,
                    "killChainStage", KILL_CHAIN_STAGE.getOrDefault(n.alertType, 0),
                    "stageName", STAGE_NAMES.getOrDefault(
                            String.valueOf(KILL_CHAIN_STAGE.getOrDefault(n.alertType, 0)), "Unknown")
            )).toList());
            chains.add(chain);
        }

        chains.sort((a, b) -> Double.compare(
                (double) b.get("chainSeverity"),
                (double) a.get("chainSeverity")
        ));
        return chains;
    }

    /**
     * 根据关联告警数量和严重等级构建源 IP 风险热力汇总。
     * 风险热力图同时考虑原始告警严重度、关联边数量和告警类型多样性。
     */
    private Map<String, Object> buildSourceRiskHeatmap(List<AlertNode> nodes, List<CorrelationEdge> edges) {
        Map<String, Double> ipRisk = new HashMap<>();
        Map<String, Set<String>> ipAlertTypes = new HashMap<>();
        Map<String, Integer> ipConnectionCount = new HashMap<>();

        for (AlertNode node : nodes) {
            double baseSeverity = severityToScore(node.severity);
            ipRisk.merge(node.srcIp, baseSeverity, Double::sum);
            ipAlertTypes.computeIfAbsent(node.srcIp, k -> new HashSet<>()).add(node.alertType);
        }

        for (CorrelationEdge edge : edges) {
            String ip1 = nodes.get(edge.fromIdx).srcIp;
            String ip2 = nodes.get(edge.toIdx).srcIp;
            ipConnectionCount.merge(ip1, 1, Integer::sum);
            ipConnectionCount.merge(ip2, 1, Integer::sum);
        }

        Map<String, Object> heatmap = new LinkedHashMap<>();
        ipRisk.forEach((ip, baseRisk) -> {
            int connections = ipConnectionCount.getOrDefault(ip, 0);
            int diversity = ipAlertTypes.getOrDefault(ip, Set.of()).size();
            double compositeRisk = baseRisk * (1 + 0.2 * connections) * (1 + 0.3 * (diversity - 1));
            double normalizedRisk = Math.min(1.0, compositeRisk / 10.0);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("riskScore", AlgorithmMathUtils.round(normalizedRisk, 3));
            entry.put("alertCount", (int) Math.round(baseRisk / 0.5));
            entry.put("alertTypes", ipAlertTypes.getOrDefault(ip, Set.of()));
            entry.put("correlationConnections", connections);
            entry.put("riskLevel", normalizedRisk >= 0.8 ? "critical" : normalizedRisk >= 0.6 ? "high" : normalizedRisk >= 0.3 ? "medium" : "low");
            heatmap.put(ip, entry);
        });
        return heatmap;
    }

    private List<Map<String, Object>> buildGraphVisualization(List<AlertNode> nodes, List<CorrelationEdge> edges) {
        return edges.stream()
                .sorted(Comparator.comparingDouble((CorrelationEdge e) -> e.correlationScore).reversed())
                .limit(100)
                .map(e -> {
                    Map<String, Object> edgeMap = new LinkedHashMap<>();
                    edgeMap.put("from", Map.of(
                            "alertType", nodes.get(e.fromIdx).alertType,
                            "srcIp", nodes.get(e.fromIdx).srcIp,
                            "time", nodes.get(e.fromIdx).detectedTime
                    ));
                    edgeMap.put("to", Map.of(
                            "alertType", nodes.get(e.toIdx).alertType,
                            "srcIp", nodes.get(e.toIdx).srcIp,
                            "time", nodes.get(e.toIdx).detectedTime
                    ));
                    edgeMap.put("correlationScore", AlgorithmMathUtils.round(e.correlationScore, 3));
                    edgeMap.put("ipOverlap", AlgorithmMathUtils.round(e.ipOverlap, 3));
                    edgeMap.put("temporalProximity", AlgorithmMathUtils.round(e.temporalProximity, 3));
                    edgeMap.put("geoProximity", AlgorithmMathUtils.round(e.geoProximity, 3));
                    edgeMap.put("isKillChainProgression", e.isKillChainProgression);
                    return edgeMap;
                })
                .toList();
    }

    /**
     * IP 重叠度：按 Jaccard 相似度“交集 / 并集”衡量两个告警涉及 IP 的共享程度。
     */
    private double computeIpOverlap(AlertNode a, AlertNode b) {
        Set<String> ipsA = new HashSet<>();
        ipsA.add(a.srcIp);
        if (a.dstIp != null) ipsA.add(a.dstIp);

        Set<String> ipsB = new HashSet<>();
        ipsB.add(b.srcIp);
        if (b.dstIp != null) ipsB.add(b.dstIp);

        Set<String> intersection = new HashSet<>(ipsA);
        intersection.retainAll(ipsB);

        Set<String> union = new HashSet<>(ipsA);
        union.addAll(ipsB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double computeGeoProximity(AlertNode a, AlertNode b) {
        if (a.latitude == null || b.latitude == null) return 0.0;
        double latDiff = a.latitude - b.latitude;
        double lonDiff = a.longitude - b.longitude;
        double distance = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
        return Math.exp(-distance / 10.0);
    }

    private boolean isKillChainProgression(AlertNode a, AlertNode b) {
        int stageA = KILL_CHAIN_STAGE.getOrDefault(a.alertType, 0);
        int stageB = KILL_CHAIN_STAGE.getOrDefault(b.alertType, 0);
        if (stageA == 0 || stageB == 0) return false;

        AlertNode earlier = a.detectedTime.isBefore(b.detectedTime) ? a : b;
        AlertNode later = a.detectedTime.isBefore(b.detectedTime) ? b : a;
        int earlierStage = KILL_CHAIN_STAGE.getOrDefault(earlier.alertType, 0);
        int laterStage = KILL_CHAIN_STAGE.getOrDefault(later.alertType, 0);

        return laterStage > earlierStage;
    }

    private double computeChainSeverity(List<AlertNode> chainNodes, Set<Integer> stages) {
        double stageCompleteness = (double) stages.size() / KILL_CHAIN_STAGE_COUNT;
        double maxSeverity = chainNodes.stream()
                .mapToDouble(n -> severityToScore(n.severity))
                .max().orElse(0.0);
        double avgSeverity = chainNodes.stream()
                .mapToDouble(n -> severityToScore(n.severity))
                .average().orElse(0.0);
        return 0.4 * stageCompleteness + 0.35 * (maxSeverity / 4.0) + 0.25 * (avgSeverity / 4.0);
    }

    private double severityToScore(String severity) {
        return switch (severity) {
            case "critical" -> 4.0;
            case "high" -> 3.0;
            case "medium" -> 2.0;
            case "low" -> 1.0;
            default -> 0.5;
        };
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) parent[rootA] = rootB;
    }

    private AlertNode toAlertNode(SecurityAlert alert) {
        return new AlertNode(
                alert.getId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getSrcIp(),
                alert.getDstIp(),
                alert.getDetectedTime(),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getCountry(),
                alert.getCity()
        );
    }

    private record AlertNode(Long id, String alertType, String severity, String srcIp, String dstIp,
                             LocalDateTime detectedTime, Double latitude, Double longitude,
                             String country, String city) {}

    private record CorrelationScore(double totalScore, double ipOverlap, double temporalProximity,
                                    double geoProximity, boolean killChainProgression) {}

    private record CorrelationEdge(int fromIdx, int toIdx, double correlationScore,
                                   double ipOverlap, double temporalProximity, double geoProximity,
                                   boolean isKillChainProgression) {}
}
