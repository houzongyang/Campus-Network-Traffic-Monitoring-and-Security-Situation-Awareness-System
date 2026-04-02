package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.repository.SecurityAlertRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DataImportService {

    public enum DemoDataRefreshResult {
        SKIPPED,
        GENERATED,
        REBUILT
    }

    private static final Logger log = LoggerFactory.getLogger(DataImportService.class);
    private static final Map<String, List<String>> REGION_IPS = new LinkedHashMap<>();
    private static final List<String> EXTERNAL_IPS = List.of(
            "203.0.113.20",
            "203.0.113.77",
            "198.51.100.23",
            "198.51.100.89",
            "34.117.59.81",
            "45.83.12.14",
            "91.240.118.8",
            "103.25.15.4"
    );
    private static final int[] COMMON_PORTS = {
            53, 80, 110, 123, 143, 443, 465, 587, 993, 995, 1883, 3306, 3389, 5432, 8080, 8443, 445, 22
    };

    static {
        REGION_IPS.put("dormitory_a", List.of("10.10.10.11", "10.10.10.12", "10.10.10.13", "10.10.10.14", "10.10.10.15"));
        REGION_IPS.put("dormitory_b", List.of("10.10.20.11", "10.10.20.12", "10.10.20.13", "10.10.20.14", "10.10.20.15"));
        REGION_IPS.put("teaching_zone", List.of("10.10.30.11", "10.10.30.12", "10.10.30.13", "10.10.30.14", "10.10.30.15"));
        REGION_IPS.put("admin_zone", List.of("10.10.40.11", "10.10.40.12", "10.10.40.13", "10.10.40.14", "10.10.40.15"));
        REGION_IPS.put("library", List.of("10.10.50.11", "10.10.50.12", "10.10.50.13", "10.10.50.14", "10.10.50.15"));
    }

    private final NetworkFlowRepository flowRepository;
    private final SecurityAlertRepository alertRepository;
    private final AppIdentificationService appIdentificationService;

    @Value("${app.demo.init-flow-count:6000}")
    private int initFlowCount;

    @Value("${app.demo.stale-minutes:5}")
    private long staleMinutes;

    @Value("${app.demo.stream-batch-flow-count:90}")
    private int streamBatchFlowCount;

    @Value("${app.demo.stream-window-seconds:45}")
    private long streamWindowSeconds;

    @Value("${app.demo.retention-minutes:120}")
    private long retentionMinutes;

    @Value("${app.demo.target-throughput-gbps:12}")
    private double targetThroughputGbps;

    @Value("${app.demo.target-throughput-jitter-ratio:0.18}")
    private double targetThroughputJitterRatio;

    private final Random streamRandom = new Random();

    public DataImportService(
            NetworkFlowRepository flowRepository,
            SecurityAlertRepository alertRepository,
            AppIdentificationService appIdentificationService
    ) {
        this.flowRepository = flowRepository;
        this.alertRepository = alertRepository;
        this.appIdentificationService = appIdentificationService;
    }

    public synchronized DemoDataRefreshResult initializeDemoDataIfNeeded() {
        long flowCount = flowRepository.count();
        if (flowCount == 0) {
            generateDemoData();
            return DemoDataRefreshResult.GENERATED;
        }

        LocalDateTime latestTimestamp = flowRepository.findFirstByOrderByTimestampDesc()
                .map(NetworkFlow::getTimestamp)
                .orElse(null);

        if (latestTimestamp == null) {
            generateDemoData();
            return DemoDataRefreshResult.GENERATED;
        }

        long ageMinutes = Duration.between(latestTimestamp, LocalDateTime.now()).toMinutes();
        if (ageMinutes >= staleMinutes) {
            log.info(
                    "Rebuild demo data because latest flow timestamp {} is older than {} minutes.",
                    latestTimestamp,
                    staleMinutes
            );
            rebuildDemoData();
            return DemoDataRefreshResult.REBUILT;
        }

        log.info("Skip demo flow generation because recent demo data already exists. latestFlowTime={}", latestTimestamp);
        return DemoDataRefreshResult.SKIPPED;
    }

    private void rebuildDemoData() {
        alertRepository.deleteAllInBatch();
        flowRepository.deleteAllInBatch();
        generateDemoData();
    }

    private void generateDemoData() {
        log.info("Generate demo flow data for smart-campus scenario.");
        generateBaselineFlows(initFlowCount);
        generateDdosScenario();
        generatePortScanScenario();
        generateWormScenario();
        generatePhishingScenario();
        generateDataExfiltrationScenario();
    }

    public synchronized int appendRealtimeDemoData() {
        LocalDateTime now = LocalDateTime.now();
        if (flowRepository.count() == 0) {
            generateDemoData();
            return (int) flowRepository.count();
        }

        LocalDateTime latestTimestamp = flowRepository.findFirstByOrderByTimestampDesc()
                .map(NetworkFlow::getTimestamp)
                .orElse(now.minusSeconds(streamWindowSeconds));

        LocalDateTime windowStart = latestTimestamp.plusSeconds(1);
        LocalDateTime minimumStart = now.minusSeconds(Math.max(streamWindowSeconds, 15L));
        if (windowStart.isBefore(minimumStart)) {
            windowStart = minimumStart;
        }

        if (!windowStart.isBefore(now)) {
            return 0;
        }

        List<NetworkFlow> flows = new ArrayList<>();
        flows.addAll(generateRealtimeBaselineFlows(windowStart, now, streamBatchFlowCount));
        flows.addAll(generateRealtimeAnomalyFlows(windowStart, now));

        if (flows.isEmpty()) {
            return 0;
        }

        flowRepository.saveAll(flows);
        return flows.size();
    }

    public synchronized long cleanupExpiredDemoData() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(retentionMinutes);
        long deletedAlerts = alertRepository.deleteByDetectedTimeBefore(cutoff);
        long deletedFlows = flowRepository.deleteByTimestampBefore(cutoff);
        return deletedAlerts + deletedFlows;
    }

    private List<NetworkFlow> generateRealtimeBaselineFlows(LocalDateTime windowStart, LocalDateTime windowEnd, int count) {
        List<NetworkFlow> flows = new ArrayList<>();
        List<String> regions = new ArrayList<>(REGION_IPS.keySet());
        long targetWindowBytes = calculateTargetWindowBytes(windowStart, windowEnd, count);
        long averageBytesPerFlow = Math.max(64_000L, targetWindowBytes / Math.max(count, 1));

        for (int index = 0; index < count; index++) {
            String region = regions.get(streamRandom.nextInt(regions.size()));
            List<String> regionHosts = REGION_IPS.get(region);
            boolean outbound = streamRandom.nextDouble() < 0.62D;

            String srcIp = outbound ? pick(regionHosts, streamRandom) : pick(EXTERNAL_IPS, streamRandom);
            String dstIp = outbound
                    ? pick(streamRandom.nextBoolean() ? pickOtherRegionHosts(region) : EXTERNAL_IPS, streamRandom)
                    : pick(regionHosts, streamRandom);
            int dstPort = COMMON_PORTS[streamRandom.nextInt(COMMON_PORTS.length)];
            int srcPort = 10000 + streamRandom.nextInt(50000);
            double ratio = 0.55D + streamRandom.nextDouble() * 0.90D;
            long totalBytes = Math.max(64_000L, Math.round(averageBytesPerFlow * ratio));
            long packets = Math.max(20L, totalBytes / (900 + streamRandom.nextInt(500)));
            int durationSeconds = 3 + streamRandom.nextInt(45);
            LocalDateTime startTime = randomTimeBetween(windowStart, windowEnd.minusSeconds(1));
            LocalDateTime endTime = startTime.plusSeconds(durationSeconds);

            flows.add(buildFlow(
                    srcIp,
                    dstIp,
                    srcPort,
                    dstPort,
                    streamRandom.nextDouble() < 0.82D ? "TCP" : "UDP",
                    totalBytes / 2,
                    totalBytes / 2,
                    packets / 2,
                    packets / 2,
                    startTime,
                    endTime,
                    resolveRegion(outbound ? srcIp : dstIp),
                    outbound ? "outbound" : "inbound"
            ));
        }

        return flows;
    }

    private List<NetworkFlow> generateRealtimeAnomalyFlows(LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<NetworkFlow> flows = new ArrayList<>();

        if (streamRandom.nextDouble() < 0.10D) {
            flows.addAll(generateRealtimeDdosBurst(windowStart, windowEnd));
        }

        if (streamRandom.nextDouble() < 0.08D) {
            flows.addAll(generateRealtimePortScan(windowStart, windowEnd));
        }

        if (streamRandom.nextDouble() < 0.06D) {
            flows.addAll(generateRealtimeExfiltration(windowStart, windowEnd));
        }

        return flows;
    }

    private List<NetworkFlow> generateRealtimeDdosBurst(LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<NetworkFlow> flows = new ArrayList<>();
        String attacker = pick(EXTERNAL_IPS, streamRandom);
        String target = pick(REGION_IPS.get("admin_zone"), streamRandom);

        for (int index = 0; index < 30; index++) {
            LocalDateTime start = randomTimeBetween(windowStart, windowEnd);
            flows.add(buildFlow(
                    attacker,
                    target,
                    20000 + index,
                    443,
                    "TCP",
                    13_000_000L,
                    13_000_000L,
                    2_200L,
                    1_900L,
                    start,
                    start.plusSeconds(4),
                    "admin_zone",
                    "inbound"
            ));
        }

        return flows;
    }

    private List<NetworkFlow> generateRealtimePortScan(LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<NetworkFlow> flows = new ArrayList<>();
        String attacker = pick(EXTERNAL_IPS, streamRandom);
        String target = pick(REGION_IPS.get("teaching_zone"), streamRandom);

        for (int index = 0; index < 20; index++) {
            LocalDateTime start = randomTimeBetween(windowStart, windowEnd);
            flows.add(buildFlow(
                    attacker,
                    target,
                    30000 + index,
                    20 + index,
                    "TCP",
                    9_000L,
                    5_000L,
                    18L,
                    12L,
                    start,
                    start.plusSeconds(2),
                    "teaching_zone",
                    "inbound"
            ));
        }

        return flows;
    }

    private List<NetworkFlow> generateRealtimeExfiltration(LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<NetworkFlow> flows = new ArrayList<>();
        String source = pick(REGION_IPS.get("admin_zone"), streamRandom);
        String target = "34.117.59.81";

        for (int index = 0; index < 8; index++) {
            LocalDateTime start = randomTimeBetween(windowStart, windowEnd);
            flows.add(buildFlow(
                    source,
                    target,
                    55000 + index,
                    443,
                    "TCP",
                    31_000_000L,
                    29_000_000L,
                    16_000L,
                    1_300L,
                    start,
                    start.plusSeconds(10),
                    "admin_zone",
                    "outbound"
            ));
        }

        return flows;
    }

    private LocalDateTime randomTimeBetween(LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (!windowStart.isBefore(windowEnd)) {
            return windowStart;
        }

        long seconds = Math.max(1L, Duration.between(windowStart, windowEnd).getSeconds());
        return windowStart.plusSeconds(streamRandom.nextInt((int) seconds + 1));
    }

    private long calculateTargetWindowBytes(LocalDateTime windowStart, LocalDateTime windowEnd, int flowCount) {
        long seconds = Math.max(1L, Duration.between(windowStart, windowEnd).getSeconds());
        long bytes = calculateTargetBytesBySeconds(seconds, streamRandom);
        return Math.max(bytes, (long) Math.max(flowCount, 1) * 64_000L);
    }

    private long calculateTargetBytesBySeconds(long seconds, Random random) {
        double normalizedGbps = Math.max(1.0D, targetThroughputGbps);
        double jitterRatio = Math.max(0.0D, Math.min(targetThroughputJitterRatio, 0.95D));
        double jitterFactor = 1.0D + (random.nextDouble() * 2.0D - 1.0D) * jitterRatio;
        double effectiveGbps = Math.max(0.5D, normalizedGbps * jitterFactor);
        double bytesPerSecond = effectiveGbps * 1_000_000_000D / 8.0D;
        return Math.round(bytesPerSecond * Math.max(seconds, 1L));
    }

    public void generateBaselineFlows(int count) {
        Random random = new Random(20260401L);
        LocalDateTime now = LocalDateTime.now();
        List<NetworkFlow> batch = new ArrayList<>();
        List<String> regions = new ArrayList<>(REGION_IPS.keySet());
        long targetHourBytes = calculateTargetBytesBySeconds(3600L, random);
        long averageBytesPerFlow = Math.max(64_000L, targetHourBytes / Math.max(count, 1));

        for (int index = 0; index < count; index++) {
            String region = regions.get(random.nextInt(regions.size()));
            List<String> regionHosts = REGION_IPS.get(region);
            boolean outbound = random.nextBoolean();

            String srcIp = outbound ? pick(regionHosts, random) : pick(EXTERNAL_IPS, random);
            String dstIp = outbound ? pick(random.nextBoolean() ? pickOtherRegionHosts(region) : EXTERNAL_IPS, random) : pick(regionHosts, random);
            int dstPort = COMMON_PORTS[random.nextInt(COMMON_PORTS.length)];
            int srcPort = 10000 + random.nextInt(50000);
            double ratio = 0.50D + random.nextDouble() * 1.00D;
            long totalBytes = Math.max(64_000L, Math.round(averageBytesPerFlow * ratio));
            long packets = Math.max(12, totalBytes / (920 + random.nextInt(520)));
            int durationSeconds = 5 + random.nextInt(240);
            int minutesAgo = random.nextInt(60);
            LocalDateTime startTime = now.minusMinutes(minutesAgo).minusSeconds(random.nextInt(60));
            LocalDateTime endTime = startTime.plusSeconds(durationSeconds);

            batch.add(buildFlow(
                    srcIp,
                    dstIp,
                    srcPort,
                    dstPort,
                    random.nextBoolean() ? "TCP" : "UDP",
                    totalBytes / 2,
                    totalBytes / 2,
                    packets / 2,
                    packets / 2,
                    startTime,
                    endTime,
                    region,
                    outbound ? "outbound" : "inbound"
            ));

            if (batch.size() >= 300) {
                flowRepository.saveAll(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            flowRepository.saveAll(batch);
        }
    }

    private void generateDdosScenario() {
        List<NetworkFlow> flows = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(3);
        String attacker = "198.51.100.23";
        String target = "10.10.40.11";

        for (int index = 0; index < 120; index++) {
            LocalDateTime start = baseTime.plusSeconds(index % 40);
            flows.add(buildFlow(
                    attacker,
                    target,
                    20_000 + index,
                    443,
                    "TCP",
                    8_000_000L,
                    7_500_000L,
                    2_000L,
                    1_600L,
                    start,
                    start.plusSeconds(6),
                    "admin_zone",
                    "inbound"
            ));
        }

        flowRepository.saveAll(flows);
    }

    private void generatePortScanScenario() {
        List<NetworkFlow> flows = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(12);
        String scanner = "198.51.100.89";
        String target = "10.10.20.15";

        for (int port = 20; port < 110; port++) {
            LocalDateTime start = baseTime.plusSeconds(port);
            flows.add(buildFlow(
                    scanner,
                    target,
                    30_000 + port,
                    port,
                    "TCP",
                    640L,
                    256L,
                    4L,
                    2L,
                    start,
                    start.plusSeconds(1),
                    "dormitory_b",
                    "inbound"
            ));
        }

        flowRepository.saveAll(flows);
    }

    private void generateWormScenario() {
        List<NetworkFlow> flows = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(18);
        String infected = "10.10.30.13";
        List<String> targets = List.of(
                "10.10.10.11", "10.10.10.12", "10.10.20.11", "10.10.20.12", "10.10.20.13",
                "10.10.40.11", "10.10.40.12", "10.10.40.13", "10.10.50.11", "10.10.50.12",
                "10.10.50.13", "10.10.10.14", "10.10.20.14", "10.10.40.14", "10.10.50.14"
        );
        int[] wormPorts = {445, 3389, 22};

        int flowIndex = 0;
        for (String target : targets) {
            for (int port : wormPorts) {
                LocalDateTime start = baseTime.plusSeconds(flowIndex * 3L);
                flows.add(buildFlow(
                        infected,
                        target,
                        40_000 + flowIndex,
                        port,
                        "TCP",
                        80_000L,
                        60_000L,
                        160L,
                        140L,
                        start,
                        start.plusSeconds(4),
                        "teaching_zone",
                        "east-west"
                ));
                flowIndex++;
            }
        }

        flowRepository.saveAll(flows);
    }

    private void generatePhishingScenario() {
        List<NetworkFlow> flows = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(25);
        String sender = "203.0.113.77";
        List<String> victims = List.of(
                "10.10.10.11", "10.10.10.12", "10.10.20.11", "10.10.20.12",
                "10.10.30.11", "10.10.30.12", "10.10.40.11", "10.10.40.12",
                "10.10.50.11", "10.10.50.12"
        );

        int flowIndex = 0;
        for (String victim : victims) {
            LocalDateTime start = baseTime.plusSeconds(flowIndex * 8L);
            flows.add(buildFlow(
                    sender,
                    victim,
                    50_000 + flowIndex,
                    flowIndex % 2 == 0 ? 443 : 25,
                    "TCP",
                    8_000L,
                    12_000L,
                    14L,
                    20L,
                    start,
                    start.plusSeconds(3),
                    resolveRegion(victim),
                    "inbound"
            ));
            flowIndex++;
        }

        flowRepository.saveAll(flows);
    }

    private void generateDataExfiltrationScenario() {
        List<NetworkFlow> flows = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(8);
        String source = "10.10.40.13";
        String target = "34.117.59.81";

        for (int index = 0; index < 12; index++) {
            LocalDateTime start = baseTime.plusSeconds(index * 20L);
            flows.add(buildFlow(
                    source,
                    target,
                    55_000 + index,
                    443,
                    "TCP",
                    40_000_000L,
                    2_000_000L,
                    20_000L,
                    1_400L,
                    start,
                    start.plusSeconds(18),
                    "admin_zone",
                    "outbound"
            ));
        }

        flowRepository.saveAll(flows);
    }

    private List<String> pickOtherRegionHosts(String currentRegion) {
        List<String> candidateHosts = new ArrayList<>();
        REGION_IPS.forEach((region, hosts) -> {
            if (!region.equals(currentRegion)) {
                candidateHosts.addAll(hosts);
            }
        });
        return candidateHosts;
    }

    private String resolveRegion(String ip) {
        return REGION_IPS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(ip))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("internet");
    }

    private NetworkFlow buildFlow(
            String srcIp,
            String dstIp,
            int srcPort,
            int dstPort,
            String protocol,
            long bytesSent,
            long bytesRecv,
            long packetsSent,
            long packetsRecv,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String region,
            String direction
    ) {
        NetworkFlow flow = new NetworkFlow();
        flow.setSrcIp(srcIp);
        flow.setDstIp(dstIp);
        flow.setSrcPort(srcPort);
        flow.setDstPort(dstPort);
        flow.setProtocol(protocol);
        flow.setBytesSent(bytesSent);
        flow.setBytesRecv(bytesRecv);
        flow.setPacketsSent(Math.max(packetsSent, 1L));
        flow.setPacketsRecv(Math.max(packetsRecv, 1L));
        flow.setAppProtocol(appIdentificationService.identifyProtocol(srcPort, dstPort));
        flow.setStartTime(startTime);
        flow.setEndTime(endTime);
        flow.setTimestamp(startTime);
        flow.setRegion(region);
        flow.setDirection(direction);
        return flow;
    }

    private <T> T pick(List<T> items, Random random) {
        return items.get(random.nextInt(items.size()));
    }
}
