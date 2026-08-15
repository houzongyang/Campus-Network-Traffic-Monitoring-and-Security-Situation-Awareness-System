package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.FlowSearchJdbcRepository;
import com.campus.network.repository.NetworkFlowRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class FlowAnalysisService {

    public record FlowSearchPage(
            List<NetworkFlow> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {
    }

    public record RegionHierarchySnapshot(String level, List<Map<String, Object>> nodes) {
    }

    private final NetworkFlowRepository flowRepository;
    private final FlowSearchJdbcRepository flowSearchJdbcRepository;

    public FlowAnalysisService(
            NetworkFlowRepository flowRepository,
            FlowSearchJdbcRepository flowSearchJdbcRepository
    ) {
        this.flowRepository = flowRepository;
        this.flowSearchJdbcRepository = flowSearchJdbcRepository;
    }

    public double calculateThroughputMbps(LocalDateTime startTime, LocalDateTime endTime) {
        long totalBytes = zeroSafe(flowRepository.sumTotalBytesBetween(startTime, endTime));
        long durationSeconds = Math.max(1L, ChronoUnit.SECONDS.between(startTime, endTime));
        return (totalBytes * 8.0D) / (durationSeconds * 1_000_000D);
    }

    public double calculatePps(LocalDateTime startTime, LocalDateTime endTime) {
        long totalPackets = zeroSafe(flowRepository.sumTotalPacketsBetween(startTime, endTime));
        long durationSeconds = Math.max(1L, ChronoUnit.SECONDS.between(startTime, endTime));
        return totalPackets / (double) durationSeconds;
    }

    public long countActiveIps(LocalDateTime startTime, LocalDateTime endTime) {
        return flowRepository.countDistinctActiveIpsBetween(startTime, endTime);
    }

    public long countFlows(LocalDateTime startTime, LocalDateTime endTime) {
        return flowRepository.countByTimestampBetween(startTime, endTime);
    }

    public Map<String, Long> getAppProtocolDistribution(LocalDateTime startTime, LocalDateTime endTime, String metric) {
        List<Object[]> rows = "packets".equalsIgnoreCase(metric)
                ? flowRepository.aggregateAppPacketsBetween(startTime, endTime, 12)
                : flowRepository.aggregateAppBytesBetween(startTime, endTime, 12);
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] row : rows) {
            distribution.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return distribution;
    }

    public List<Map<String, Object>> getTopFlows(LocalDateTime startTime, LocalDateTime endTime, int limit, String metric) {
        return flowRepository.findTopFlowsByBytes(startTime, endTime, PageRequest.of(0, Math.max(limit, 1)))
                .getContent()
                .stream()
                .sorted(Comparator.comparingLong((NetworkFlow flow) -> metricValue(flow, metric)).reversed())
                .limit(limit)
                .map(flow -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", flow.getId());
                    item.put("srcIp", flow.getSrcIp());
                    item.put("dstIp", flow.getDstIp());
                    item.put("srcPort", flow.getSrcPort());
                    item.put("dstPort", flow.getDstPort());
                    item.put("protocol", flow.getProtocol());
                    item.put("appProtocol", flow.getAppProtocol());
                    item.put("region", flow.getRegion());
                    item.put("direction", flow.getDirection());
                    item.put("bytes", totalBytes(flow));
                    item.put("packets", totalPackets(flow));
                    item.put("metricValue", metricValue(flow, metric));
                    item.put("durationSeconds", Duration.between(flow.getStartTime(), flow.getEndTime()).toSeconds());
                    item.put("timestamp", flow.getTimestamp());
                    return item;
                })
                .toList();
    }

    public Map<String, Map<String, Object>> getRegionTraffic(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Map<String, Object>> regionStats = new LinkedHashMap<>();
        for (Object[] row : flowRepository.aggregateRegionTrafficBetween(startTime, endTime)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bytes", ((Number) row[1]).longValue());
            item.put("packets", ((Number) row[2]).longValue());
            item.put("flowCount", ((Number) row[3]).longValue());
            item.put("activeIps", ((Number) row[4]).longValue());
            regionStats.put(String.valueOf(row[0]), item);
        }
        return regionStats;
    }

    public RegionHierarchySnapshot getRegionHierarchy(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String metric,
            String region,
            String building,
            String switchId
    ) {
        String normalizedLevel = resolveHierarchyLevel(region, building, switchId);
        String normalizedMetric = "packets".equalsIgnoreCase(metric) ? "packets" : "bytes";

        List<NetworkFlow> flows = flowRepository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime)
                .stream()
                .filter(flow -> region == null || region.isBlank() || normalizeText(flow.getRegion(), "internet").equalsIgnoreCase(region.trim()))
                .filter(flow -> building == null || building.isBlank() || deriveBuilding(flow).equalsIgnoreCase(building.trim()))
                .filter(flow -> switchId == null || switchId.isBlank() || deriveSwitch(flow).equalsIgnoreCase(switchId.trim()))
                .toList();

        Map<String, List<NetworkFlow>> grouped = flows.stream()
                .collect(Collectors.groupingBy(flow -> hierarchyKeyByLevel(flow, normalizedLevel)));

        List<Map<String, Object>> nodes = grouped.entrySet().stream()
                .map(entry -> buildHierarchyNode(entry.getKey(), normalizedLevel, normalizedMetric, entry.getValue()))
                .sorted((left, right) -> Long.compare(
                        ((Number) right.get("metricValue")).longValue(),
                        ((Number) left.get("metricValue")).longValue()
                ))
                .limit(30)
                .toList();

        return new RegionHierarchySnapshot(normalizedLevel, nodes);
    }

    public List<Map<String, Object>> getThroughputTrend(LocalDateTime startTime, LocalDateTime endTime, int bucketMinutes) {
        int normalizedBucketMinutes = Math.max(bucketMinutes, 1);
        LocalDateTime alignedStartTime = alignDownToBucket(startTime, normalizedBucketMinutes);
        LocalDateTime alignedEndTime = alignDownToBucket(endTime, normalizedBucketMinutes);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Object[] row : flowRepository.aggregateThroughputTrendBetween(
                alignedStartTime,
                endTime,
                normalizedBucketMinutes * 60L
        )) {
            LocalDateTime bucketStart = toLocalDateTime(row[0]);
            LocalDateTime bucketEnd = bucketStart.plusMinutes(normalizedBucketMinutes);
            LocalDateTime effectiveBucketEnd = bucketEnd.isAfter(endTime) ? endTime : bucketEnd;
            long bytes = ((Number) row[1]).longValue();
            long packets = ((Number) row[2]).longValue();
            long durationSeconds = Math.max(1L, ChronoUnit.SECONDS.between(bucketStart, effectiveBucketEnd));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", bucketStart);
            point.put("bytes", bytes);
            point.put("packets", packets);
            point.put("throughputMbps", (bytes * 8.0D) / (durationSeconds * 1_000_000D));
            point.put("pps", packets / (double) durationSeconds);
            trend.add(point);
        }
        return trend;
    }

    public List<NetworkFlow> searchFlows(
            String srcIp,
            String dstIp,
            Integer srcPort,
            Integer dstPort,
            String appProtocol,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int limit
    ) {
        return flowSearchJdbcRepository.search(
                srcIp,
                dstIp,
                null,
                null,
                srcPort,
                dstPort,
                null,
                null,
                null,
                appProtocol,
                startTime,
                endTime,
                0,
                Math.max(limit, 1)
        ).flows();
    }

    public FlowSearchPage searchFlowsAdvanced(
            String srcIp,
            String dstIp,
            String srcCidr,
            String dstCidr,
            Integer srcPort,
            Integer dstPort,
            Integer dstPortFrom,
            Integer dstPortTo,
            String protocol,
            String appProtocol,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 500));
        FlowSearchJdbcRepository.QueryResult result = flowSearchJdbcRepository.search(
                srcIp,
                dstIp,
                srcCidr,
                dstCidr,
                srcPort,
                dstPort,
                dstPortFrom,
                dstPortTo,
                protocol,
                appProtocol,
                startTime,
                endTime,
                normalizedPage,
                normalizedSize
        );
        int totalPages = (int) Math.ceil(result.totalElements() / (double) normalizedSize);
        return new FlowSearchPage(result.flows(), result.totalElements(), normalizedPage, normalizedSize, totalPages);
    }

    public Map<String, Object> buildIpProfile(String ip, LocalDateTime startTime, LocalDateTime endTime) {
        List<NetworkFlow> flows = flowRepository.findBySrcIpOrDstIpOrderByTimestampDesc(ip, ip).stream()
                .filter(flow -> !flow.getTimestamp().isBefore(startTime) && !flow.getTimestamp().isAfter(endTime))
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalBytes", flows.stream().mapToLong(this::totalBytes).sum());
        summary.put("totalPackets", flows.stream().mapToLong(this::totalPackets).sum());
        summary.put("flowCount", flows.size());
        summary.put("peerCount", flows.stream()
                .map(flow -> flow.getSrcIp().equals(ip) ? flow.getDstIp() : flow.getSrcIp())
                .distinct()
                .count());
        summary.put("inboundBytes", flows.stream().filter(flow -> ip.equals(flow.getDstIp())).mapToLong(this::totalBytes).sum());
        summary.put("outboundBytes", flows.stream().filter(flow -> ip.equals(flow.getSrcIp())).mapToLong(this::totalBytes).sum());
        summary.put("topProtocols", flows.stream()
                .collect(Collectors.groupingBy(
                        flow -> normalizeText(flow.getAppProtocol(), "Unknown"),
                        Collectors.summingLong(this::totalBytes)
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                )));

        Map<String, Map<String, Object>> peerMap = new LinkedHashMap<>();
        for (NetworkFlow flow : flows) {
            String peerIp = flow.getSrcIp().equals(ip) ? flow.getDstIp() : flow.getSrcIp();
            Map<String, Object> peer = peerMap.computeIfAbsent(peerIp, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("peerIp", key);
                item.put("bytes", 0L);
                item.put("packets", 0L);
                item.put("flowCount", 0);
                item.put("lastSeen", flow.getTimestamp());
                return item;
            });
            peer.put("bytes", (Long) peer.get("bytes") + totalBytes(flow));
            peer.put("packets", (Long) peer.get("packets") + totalPackets(flow));
            peer.put("flowCount", (Integer) peer.get("flowCount") + 1);
            peer.put("lastSeen", flow.getTimestamp());
        }

        List<Map<String, Object>> peers = peerMap.values().stream()
                .sorted((left, right) -> Long.compare((Long) right.get("bytes"), (Long) left.get("bytes")))
                .limit(15)
                .toList();

        int bucketMinutes = Math.max(1, (int) Math.ceil(Duration.between(startTime, endTime).toMinutes() / 12.0D));
        List<Map<String, Object>> trend = getIpTrend(flows, startTime, endTime, bucketMinutes);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ip", ip);
        response.put("summary", summary);
        response.put("peers", peers);
        response.put("trend", trend);
        response.put("flows", flows.stream().limit(80).toList());
        return response;
    }

    private List<Map<String, Object>> getIpTrend(List<NetworkFlow> flows, LocalDateTime startTime, LocalDateTime endTime, int bucketMinutes) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDateTime cursor = startTime;

        while (!cursor.isAfter(endTime)) {
            LocalDateTime bucketStart = cursor;
            LocalDateTime bucketEnd = cursor.plusMinutes(bucketMinutes);
            long bytes = flows.stream()
                    .filter(flow -> !flow.getTimestamp().isBefore(bucketStart) && flow.getTimestamp().isBefore(bucketEnd))
                    .mapToLong(this::totalBytes)
                    .sum();
            long packets = flows.stream()
                    .filter(flow -> !flow.getTimestamp().isBefore(bucketStart) && flow.getTimestamp().isBefore(bucketEnd))
                    .mapToLong(this::totalPackets)
                    .sum();

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", bucketStart);
            point.put("bytes", bytes);
            point.put("packets", packets);
            trend.add(point);

            cursor = bucketEnd;
        }

        return trend;
    }

    private long metricValue(NetworkFlow flow, String metric) {
        if ("packets".equalsIgnoreCase(metric)) {
            return totalPackets(flow);
        }
        return totalBytes(flow);
    }

    private long totalBytes(NetworkFlow flow) {
        return flow.getBytesSent() + flow.getBytesRecv();
    }

    private long totalPackets(NetworkFlow flow) {
        return flow.getPacketsSent() + flow.getPacketsRecv();
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long zeroSafe(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }

    private LocalDateTime alignDownToBucket(LocalDateTime time, int bucketMinutes) {
        int normalizedBucketMinutes = Math.max(bucketMinutes, 1);
        int alignedMinute = time.getMinute() / normalizedBucketMinutes * normalizedBucketMinutes;
        return time.withMinute(alignedMinute).withSecond(0).withNano(0);
    }

    private String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String resolveHierarchyLevel(String region, String building, String switchId) {
        if (switchId != null && !switchId.isBlank()) {
            return "port";
        }
        if (building != null && !building.isBlank()) {
            return "switch";
        }
        if (region != null && !region.isBlank()) {
            return "building";
        }
        return "region";
    }

    private String hierarchyKeyByLevel(NetworkFlow flow, String level) {
        return switch (level) {
            case "building" -> deriveBuilding(flow);
            case "switch" -> deriveSwitch(flow);
            case "port" -> "port-" + flow.getDstPort();
            default -> normalizeText(flow.getRegion(), "internet");
        };
    }

    private Map<String, Object> buildHierarchyNode(String key, String level, String metric, List<NetworkFlow> flows) {
        Set<String> activeIps = new HashSet<>();
        flows.forEach(flow -> {
            activeIps.add(flow.getSrcIp());
            activeIps.add(flow.getDstIp());
        });

        long bytes = flows.stream().mapToLong(this::totalBytes).sum();
        long packets = flows.stream().mapToLong(this::totalPackets).sum();
        long metricValue = "packets".equalsIgnoreCase(metric) ? packets : bytes;

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", key);
        node.put("name", "port".equals(level) ? key.replace("port-", "") : key);
        node.put("level", level);
        node.put("nextLevel", nextHierarchyLevel(level));
        node.put("drillable", nextHierarchyLevel(level) != null);
        node.put("bytes", bytes);
        node.put("packets", packets);
        node.put("flowCount", flows.size());
        node.put("activeIps", activeIps.size());
        node.put("metricValue", metricValue);
        return node;
    }

    private String nextHierarchyLevel(String level) {
        return switch (level) {
            case "region" -> "building";
            case "building" -> "switch";
            case "switch" -> "port";
            default -> null;
        };
    }

    private String deriveBuilding(NetworkFlow flow) {
        String region = normalizeText(flow.getRegion(), "internet");
        if ("internet".equalsIgnoreCase(region)) {
            return "internet-gateway";
        }

        int host = extractHostNumber(resolveCampusIp(flow));
        int normalizedHost = Math.max(1, host - 10);
        int buildingIndex = Math.min(3, ((normalizedHost - 1) / 2) + 1);
        return region + "-building-" + buildingIndex;
    }

    private String deriveSwitch(NetworkFlow flow) {
        int host = extractHostNumber(resolveCampusIp(flow));
        int switchIndex = host > 0 ? ((host - 1) % 3) + 1 : 1;
        return deriveBuilding(flow) + "-sw-" + switchIndex;
    }

    private String resolveCampusIp(NetworkFlow flow) {
        if (isCampusIp(flow.getSrcIp())) {
            return flow.getSrcIp();
        }
        if (isCampusIp(flow.getDstIp())) {
            return flow.getDstIp();
        }
        return "";
    }

    private boolean isCampusIp(String ip) {
        return ip != null && ip.startsWith("10.10.");
    }

    private int extractHostNumber(String ip) {
        if (ip == null || ip.isBlank()) {
            return -1;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
