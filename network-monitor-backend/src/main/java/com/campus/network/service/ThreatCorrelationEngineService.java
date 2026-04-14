package com.campus.network.service;

import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.SecurityAlertRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Multi-Dimensional Threat Correlation Engine (MDTCE)
 *
 * Innovative algorithm that fuses:
 *   1. All 6 existing threat detectors (ThreatDetectionService)
 *   2. Geographic location correlation (GeoLocationService)
 *   3. Temporal alert timeline analysis (ThreatDetectionService.getThreatStatistics)
 *
 * Key innovation: Builds a threat correlation graph that links related alerts
 * across temporal, spatial (IP/geo), and attack-type dimensions. Uses a sliding
 * time window to detect multi-stage attack chains modeled after the Cyber Kill Chain
 * (e.g., Reconnaissance -> Weaponization -> Exploitation -> Exfiltration).
 *
 * Core concepts:
 *   - Attack Chain Pattern: Temporal sequence of correlated threat types
 *   - Correlation Score: Weighted sum of IP overlap, temporal proximity, and geo clustering
 *   - Kill Chain Stage Mapping: Maps alert types to kill chain stages
 */
@Service
public class ThreatCorrelationEngineService {

    private static final int CORRELATION_WINDOW_MINUTES = 30;
    private static final double TEMPORAL_DECAY_LAMBDA = 0.1;
    private static final double IP_OVERLAP_WEIGHT = 0.45;
    private static final double TEMPORAL_WEIGHT = 0.30;
    private static final double GEO_WEIGHT = 0.25;
    private static final double CHAIN_SCORE_THRESHOLD = 0.50;

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
    private final GeoLocationService geoLocationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ThreatCorrelationEngineService(
            SecurityAlertRepository alertRepository,
            GeoLocationService geoLocationService
    ) {
        this.alertRepository = alertRepository;
        this.geoLocationService = geoLocationService;
    }

    /**
     * Run the full correlation engine over alerts in the given time window.
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
     * Build pairwise correlation graph between alerts.
     * Each edge has a correlation score based on IP overlap, temporal proximity, and geo clustering.
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

                double ipScore = computeIpOverlap(a, b);
                double temporalScore = Math.exp(-TEMPORAL_DECAY_LAMBDA * minutesBetween);
                double geoScore = computeGeoProximity(a, b);

                double correlationScore = IP_OVERLAP_WEIGHT * ipScore
                        + TEMPORAL_WEIGHT * temporalScore
                        + GEO_WEIGHT * geoScore;

                if (correlationScore >= 0.20) {
                    boolean isKillChainProgression = isKillChainProgression(a, b);
                    if (isKillChainProgression) {
                        correlationScore = Math.min(1.0, correlationScore * 1.3);
                    }
                    edges.add(new CorrelationEdge(i, j, correlationScore, ipScore, temporalScore, geoScore, isKillChainProgression));
                }
            }
        }
        return edges;
    }

    /**
     * Detect multi-stage attack chains using connected components in the correlation graph,
     * filtered by Kill Chain stage progression.
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
            chain.put("totalStages", 4);
            chain.put("chainSeverity", round(chainSeverity, 3));
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
     * Build a source IP risk heatmap based on the number and severity of correlated alerts.
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
            entry.put("riskScore", round(normalizedRisk, 3));
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
                    edgeMap.put("correlationScore", round(e.correlationScore, 3));
                    edgeMap.put("ipOverlap", round(e.ipOverlap, 3));
                    edgeMap.put("temporalProximity", round(e.temporalProximity, 3));
                    edgeMap.put("geoProximity", round(e.geoProximity, 3));
                    edgeMap.put("isKillChainProgression", e.isKillChainProgression);
                    return edgeMap;
                })
                .toList();
    }

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
        double stageCompleteness = stages.size() / 4.0;
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

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private record AlertNode(Long id, String alertType, String severity, String srcIp, String dstIp,
                             LocalDateTime detectedTime, Double latitude, Double longitude,
                             String country, String city) {}

    private record CorrelationEdge(int fromIdx, int toIdx, double correlationScore,
                                   double ipOverlap, double temporalProximity, double geoProximity,
                                   boolean isKillChainProgression) {}
}
