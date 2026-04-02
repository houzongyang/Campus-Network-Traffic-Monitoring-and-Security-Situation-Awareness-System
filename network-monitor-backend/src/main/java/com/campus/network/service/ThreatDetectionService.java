package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.repository.SecurityAlertRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class ThreatDetectionService {

    private static final long DDOS_THRESHOLD_BYTES = 700L * 1024 * 1024;
    private static final long EXFIL_THRESHOLD_BYTES = 450L * 1024 * 1024;
    private static final int PORT_SCAN_THRESHOLD = 18;
    private static final int SLOW_SCAN_PORT_THRESHOLD = 50;
    private static final double SLOW_SCAN_RATE_PER_SECOND = 2.0D;
    private static final int WORM_TARGET_THRESHOLD = 10;
    private static final int PHISHING_TARGET_THRESHOLD = 8;
    private static final int DEDUP_WINDOW_MINUTES = 10;

    private final NetworkFlowRepository flowRepository;
    private final SecurityAlertRepository alertRepository;
    private final GeoLocationService geoLocationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ThreatDetectionService(
            NetworkFlowRepository flowRepository,
            SecurityAlertRepository alertRepository,
            GeoLocationService geoLocationService
    ) {
        this.flowRepository = flowRepository;
        this.alertRepository = alertRepository;
        this.geoLocationService = geoLocationService;
    }

    public void runFullThreatDetection(LocalDateTime startTime, LocalDateTime endTime) {
        List<SecurityAlert> generatedAlerts = new ArrayList<>();
        generatedAlerts.addAll(detectDdosAttacks(startTime, endTime));
        generatedAlerts.addAll(detectPortScanAttacks(startTime, endTime));
        generatedAlerts.addAll(detectSlowPortScanAttacks(startTime, endTime));
        generatedAlerts.addAll(detectWormAttacks(startTime, endTime));
        generatedAlerts.addAll(detectPhishingAttacks(startTime, endTime));
        generatedAlerts.addAll(detectDataExfiltration(startTime, endTime));

        Set<String> existingSignatures = alertRepository
                .findByDetectedTimeBetweenOrderByDetectedTimeDesc(startTime.minusHours(1), endTime.plusHours(1))
                .stream()
                .map(this::alertSignature)
                .collect(Collectors.toCollection(HashSet::new));

        for (SecurityAlert alert : generatedAlerts) {
            if (existingSignatures.add(alertSignature(alert))) {
                enrichGeo(alert);
                alertRepository.save(alert);
            }
        }
    }

    public List<SecurityAlert> detectDdosAttacks(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        long dynamicThresholdBytes = calculateDynamicDdosThreshold(startTime);
        Map<String, List<NetworkFlow>> grouped = flows.stream()
                .filter(flow -> !isInternalIp(flow.getSrcIp()))
                .collect(Collectors.groupingBy(flow -> flow.getSrcIp() + "->" + flow.getDstIp()));

        List<SecurityAlert> alerts = new ArrayList<>();
        grouped.forEach((key, groupFlows) -> {
            long bytes = groupFlows.stream().mapToLong(this::totalBytes).sum();
            long packets = groupFlows.stream().mapToLong(this::totalPackets).sum();
            if (bytes >= dynamicThresholdBytes || packets >= 120_000 || groupFlows.size() >= 80) {
                String[] pair = key.split("->");
                alerts.add(buildAlert(
                        "DDoS",
                        "critical",
                        pair[0],
                        pair[1],
                        "External source generated burst traffic to a campus asset.",
                        Map.of(
                                "bytes", bytes,
                                "packets", packets,
                                "flowCount", groupFlows.size(),
                                "dynamicThresholdBytes", dynamicThresholdBytes
                        )
                ));
            }
        });
        return alerts;
    }

    public List<SecurityAlert> detectPortScanAttacks(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        int dynamicPortThreshold = calculateDynamicPortScanThreshold(startTime);
        Map<String, Set<Integer>> scannedPorts = new HashMap<>();

        flows.stream()
                .filter(flow -> !isInternalIp(flow.getSrcIp()))
                .forEach(flow -> scannedPorts.computeIfAbsent(flow.getSrcIp() + "->" + flow.getDstIp(), key -> new HashSet<>()).add(flow.getDstPort()));

        List<SecurityAlert> alerts = new ArrayList<>();
        scannedPorts.forEach((key, ports) -> {
            if (ports.size() >= dynamicPortThreshold) {
                String[] pair = key.split("->");
                alerts.add(buildAlert(
                        "PortScan",
                        "high",
                        pair[0],
                        pair[1],
                        "Source probed a wide range of destination ports on a campus endpoint.",
                        Map.of(
                                "portCount", ports.size(),
                                "ports", ports.stream().sorted().limit(20).toList(),
                                "dynamicThreshold", dynamicPortThreshold
                        )
                ));
            }
        });
        return alerts;
    }

    public List<SecurityAlert> detectSlowPortScanAttacks(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        Map<String, List<NetworkFlow>> grouped = flows.stream()
                .filter(flow -> !isInternalIp(flow.getSrcIp()))
                .collect(Collectors.groupingBy(flow -> flow.getSrcIp() + "->" + flow.getDstIp()));

        List<SecurityAlert> alerts = new ArrayList<>();
        grouped.forEach((key, groupFlows) -> {
            Set<Integer> uniquePorts = groupFlows.stream().map(NetworkFlow::getDstPort).collect(Collectors.toSet());
            if (uniquePorts.size() < SLOW_SCAN_PORT_THRESHOLD) {
                return;
            }

            LocalDateTime firstSeen = groupFlows.stream()
                    .map(NetworkFlow::getTimestamp)
                    .min(LocalDateTime::compareTo)
                    .orElse(startTime);
            LocalDateTime lastSeen = groupFlows.stream()
                    .map(NetworkFlow::getTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(endTime);
            long durationSeconds = Math.max(1L, java.time.Duration.between(firstSeen, lastSeen).toSeconds());
            double scanRate = uniquePorts.size() / (double) durationSeconds;

            if (scanRate <= SLOW_SCAN_RATE_PER_SECOND) {
                String[] pair = key.split("->");
                alerts.add(buildAlert(
                        "SlowPortScan",
                        "high",
                        pair[0],
                        pair[1],
                        "Low-rate scan observed across many destination ports in a long window.",
                        Map.of(
                                "portCount", uniquePorts.size(),
                                "scanRatePerSecond", scanRate,
                                "durationSeconds", durationSeconds,
                                "samplePorts", uniquePorts.stream().sorted().limit(30).toList()
                        )
                ));
            }
        });
        return alerts;
    }

    public List<SecurityAlert> detectWormAttacks(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        Map<String, Set<String>> lateralTargets = new HashMap<>();

        flows.stream()
                .filter(flow -> isInternalIp(flow.getSrcIp()) && isInternalIp(flow.getDstIp()))
                .filter(flow -> Set.of(445, 3389, 22).contains(flow.getDstPort()))
                .forEach(flow -> lateralTargets.computeIfAbsent(flow.getSrcIp(), key -> new HashSet<>()).add(flow.getDstIp()));

        List<SecurityAlert> alerts = new ArrayList<>();
        lateralTargets.forEach((srcIp, targets) -> {
            if (targets.size() >= WORM_TARGET_THRESHOLD) {
                alerts.add(buildAlert(
                        "WormPropagation",
                        "critical",
                        srcIp,
                        null,
                        "A campus host attempted east-west propagation across many internal endpoints.",
                        Map.of("targetCount", targets.size(), "sampleTargets", targets.stream().sorted().limit(8).toList())
                ));
            }
        });
        return alerts;
    }

    public List<SecurityAlert> detectPhishingAttacks(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        Map<String, Set<String>> victimMap = new HashMap<>();
        Map<String, Long> totalBytes = new HashMap<>();

        flows.stream()
                .filter(flow -> !isInternalIp(flow.getSrcIp()) && isInternalIp(flow.getDstIp()))
                .filter(flow -> Set.of("HTTP", "HTTPS", "SMTP", "SMTPS").contains(flow.getAppProtocol()))
                .forEach(flow -> {
                    victimMap.computeIfAbsent(flow.getSrcIp(), key -> new HashSet<>()).add(flow.getDstIp());
                    totalBytes.merge(flow.getSrcIp(), totalBytes(flow), Long::sum);
                });

        List<SecurityAlert> alerts = new ArrayList<>();
        victimMap.forEach((srcIp, victims) -> {
            long bytes = totalBytes.getOrDefault(srcIp, 0L);
            if (victims.size() >= PHISHING_TARGET_THRESHOLD && bytes < 2_000_000L) {
                alerts.add(buildAlert(
                        "Phishing",
                        "high",
                        srcIp,
                        null,
                        "A single external source touched many campus users with low-volume web or mail traffic.",
                        Map.of("victimCount", victims.size(), "sampleVictims", victims.stream().sorted().limit(8).toList(), "bytes", bytes)
                ));
            }
        });
        return alerts;
    }

    public List<SecurityAlert> detectDataExfiltration(LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime);
        long dynamicExfilThreshold = calculateDynamicExfiltrationThreshold(startTime);
        Map<String, List<NetworkFlow>> grouped = flows.stream()
                .filter(flow -> isInternalIp(flow.getSrcIp()) && !isInternalIp(flow.getDstIp()))
                .collect(Collectors.groupingBy(flow -> flow.getSrcIp() + "->" + flow.getDstIp()));

        List<SecurityAlert> alerts = new ArrayList<>();
        grouped.forEach((key, groupFlows) -> {
            long bytes = groupFlows.stream().mapToLong(this::totalBytes).sum();
            if (bytes >= dynamicExfilThreshold) {
                String[] pair = key.split("->");
                alerts.add(buildAlert(
                        "DataExfiltration",
                        "high",
                        pair[0],
                        pair[1],
                        "Large outbound transfer suggests possible data exfiltration.",
                        Map.of(
                                "bytes", bytes,
                                "flowCount", groupFlows.size(),
                                "dynamicThresholdBytes", dynamicExfilThreshold
                        )
                ));
            }
        });
        return alerts;
    }

    public Map<String, Object> getThreatStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        List<SecurityAlert> alerts = alertRepository.findByDetectedTimeBetweenOrderByDetectedTimeDesc(startTime, endTime);

        Map<String, Long> byType = alerts.stream()
                .collect(Collectors.groupingBy(SecurityAlert::getAlertType, Collectors.counting()));
        Map<String, Long> bySeverity = alerts.stream()
                .collect(Collectors.groupingBy(SecurityAlert::getSeverity, Collectors.counting()));

        List<Map<String, Object>> timeline = alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getDetectedTime().withMinute(alert.getDetectedTime().getMinute() / 10 * 10).withSecond(0).withNano(0),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("time", entry.getKey());
                    point.put("count", entry.getValue());
                    return point;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalAlerts", (long) alerts.size());
        response.put("byType", byType);
        response.put("bySeverity", bySeverity);
        response.put("timeline", timeline);
        return response;
    }

    public List<Map<String, Object>> getGeoDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return alertRepository.findByDetectedTimeBetweenOrderByDetectedTimeDesc(startTime, endTime).stream()
                .filter(alert -> alert.getLatitude() != null && alert.getLongitude() != null)
                .sorted(Comparator.comparing(SecurityAlert::getDetectedTime).reversed())
                .map(alert -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("srcIp", alert.getSrcIp());
                    item.put("dstIp", alert.getDstIp());
                    item.put("alertType", alert.getAlertType());
                    item.put("severity", alert.getSeverity());
                    item.put("country", alert.getCountry());
                    item.put("city", alert.getCity());
                    item.put("latitude", alert.getLatitude());
                    item.put("longitude", alert.getLongitude());
                    item.put("detectedTime", alert.getDetectedTime());
                    return item;
                })
                .toList();
    }

    private SecurityAlert buildAlert(
            String type,
            String severity,
            String srcIp,
            String dstIp,
            String description,
            Map<String, Object> details
    ) {
        SecurityAlert alert = new SecurityAlert();
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setSrcIp(srcIp);
        alert.setDstIp(dstIp);
        alert.setDetectedTime(LocalDateTime.now());
        alert.setDescription(description);
        alert.setThreatDetails(toJson(details));
        alert.setConfirmed(Boolean.FALSE);
        return alert;
    }

    private void enrichGeo(SecurityAlert alert) {
        GeoLocationService.GeoLocation location = geoLocationService.locate(alert.getSrcIp());
        alert.setCountry(location.country());
        alert.setCity(location.city());
        alert.setLatitude(location.latitude());
        alert.setLongitude(location.longitude());
    }

    private String alertSignature(SecurityAlert alert) {
        LocalDateTime detectedTime = alert.getDetectedTime() == null ? LocalDateTime.now() : alert.getDetectedTime();
        LocalDateTime window = detectedTime
                .withMinute(detectedTime.getMinute() / DEDUP_WINDOW_MINUTES * DEDUP_WINDOW_MINUTES)
                .withSecond(0)
                .withNano(0);
        return String.join(
                "|",
                alert.getAlertType(),
                safe(alert.getSrcIp()),
                window.toString()
        );
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean isInternalIp(String ip) {
        return geoLocationService.isPrivateIp(ip);
    }

    private long totalBytes(NetworkFlow flow) {
        return flow.getBytesSent() + flow.getBytesRecv();
    }

    private long totalPackets(NetworkFlow flow) {
        return flow.getPacketsSent() + flow.getPacketsRecv();
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private long calculateDynamicDdosThreshold(LocalDateTime referenceStartTime) {
        LocalDateTime baselineStart = referenceStartTime.minusHours(1);
        List<NetworkFlow> baseline = flowRepository.findByTimestampBetweenOrderByTimestampDesc(baselineStart, referenceStartTime);
        if (baseline.isEmpty()) {
            return DDOS_THRESHOLD_BYTES;
        }

        Map<String, Long> pairBytes = baseline.stream()
                .filter(flow -> !isInternalIp(flow.getSrcIp()))
                .collect(Collectors.groupingBy(
                        flow -> flow.getSrcIp() + "->" + flow.getDstIp(),
                        Collectors.summingLong(this::totalBytes)
                ));
        if (pairBytes.isEmpty()) {
            return DDOS_THRESHOLD_BYTES;
        }
        double average = pairBytes.values().stream().mapToLong(Long::longValue).average().orElse(DDOS_THRESHOLD_BYTES);
        return Math.max(DDOS_THRESHOLD_BYTES, Math.round(average * 3));
    }

    private int calculateDynamicPortScanThreshold(LocalDateTime referenceStartTime) {
        LocalDateTime baselineStart = referenceStartTime.minusHours(1);
        List<NetworkFlow> baseline = flowRepository.findByTimestampBetweenOrderByTimestampDesc(baselineStart, referenceStartTime);
        if (baseline.isEmpty()) {
            return PORT_SCAN_THRESHOLD;
        }

        Map<String, Long> uniquePorts = baseline.stream()
                .filter(flow -> !isInternalIp(flow.getSrcIp()))
                .collect(Collectors.groupingBy(
                        flow -> flow.getSrcIp() + "->" + flow.getDstIp(),
                        Collectors.mapping(NetworkFlow::getDstPort, Collectors.toSet())
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));

        if (uniquePorts.isEmpty()) {
            return PORT_SCAN_THRESHOLD;
        }

        double average = uniquePorts.values().stream().mapToLong(Long::longValue).average().orElse(PORT_SCAN_THRESHOLD);
        return Math.max(PORT_SCAN_THRESHOLD, (int) Math.round(average * 2.5D));
    }

    private long calculateDynamicExfiltrationThreshold(LocalDateTime referenceStartTime) {
        LocalDateTime baselineStart = referenceStartTime.minusHours(1);
        List<NetworkFlow> baseline = flowRepository.findByTimestampBetweenOrderByTimestampDesc(baselineStart, referenceStartTime);
        if (baseline.isEmpty()) {
            return EXFIL_THRESHOLD_BYTES;
        }

        Map<String, Long> outbound = baseline.stream()
                .filter(flow -> isInternalIp(flow.getSrcIp()) && !isInternalIp(flow.getDstIp()))
                .collect(Collectors.groupingBy(
                        flow -> flow.getSrcIp() + "->" + flow.getDstIp(),
                        Collectors.summingLong(this::totalBytes)
                ));
        if (outbound.isEmpty()) {
            return EXFIL_THRESHOLD_BYTES;
        }
        double average = outbound.values().stream().mapToLong(Long::longValue).average().orElse(EXFIL_THRESHOLD_BYTES);
        return Math.max(EXFIL_THRESHOLD_BYTES, Math.round(average * 3));
    }
}
