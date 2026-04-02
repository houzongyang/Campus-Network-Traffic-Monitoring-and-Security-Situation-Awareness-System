package com.campus.network.controller;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.service.FlowAnalysisService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flows")
public class FlowSearchController {

    private final NetworkFlowRepository flowRepository;
    private final FlowAnalysisService flowAnalysisService;

    public FlowSearchController(NetworkFlowRepository flowRepository, FlowAnalysisService flowAnalysisService) {
        this.flowRepository = flowRepository;
        this.flowAnalysisService = flowAnalysisService;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFlows(
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String dstIp,
            @RequestParam(required = false) String srcCidr,
            @RequestParam(required = false) String dstCidr,
            @RequestParam(required = false) Integer srcPort,
            @RequestParam(required = false) Integer dstPort,
            @RequestParam(required = false) String dstPortRange,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String appProtocol,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "-30") int minutesAgo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        LocalDateTime resolvedEndTime = parseDateTimeOrDefault(endTime, LocalDateTime.now());
        LocalDateTime resolvedStartTime = parseDateTimeOrDefault(
                startTime,
                resolvedEndTime.minusMinutes(Math.abs(minutesAgo))
        );
        if (resolvedStartTime.isAfter(resolvedEndTime)) {
            LocalDateTime temp = resolvedStartTime;
            resolvedStartTime = resolvedEndTime;
            resolvedEndTime = temp;
        }
        int[] portRange = parsePortRange(dstPortRange);
        int normalizedSize = Math.max(1, Math.min(size, 500));

        FlowAnalysisService.FlowSearchPage result = flowAnalysisService.searchFlowsAdvanced(
                srcIp,
                dstIp,
                srcCidr,
                dstCidr,
                srcPort,
                dstPort,
                portRange[0],
                portRange[1],
                protocol,
                appProtocol,
                resolvedStartTime,
                resolvedEndTime,
                page,
                normalizedSize
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", result.content().size());
        response.put("total", result.totalElements());
        response.put("page", result.page());
        response.put("size", result.size());
        response.put("totalPages", result.totalPages());
        response.put("flows", result.content());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search/export")
    public ResponseEntity<byte[]> exportSearchFlows(
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String dstIp,
            @RequestParam(required = false) String srcCidr,
            @RequestParam(required = false) String dstCidr,
            @RequestParam(required = false) Integer srcPort,
            @RequestParam(required = false) Integer dstPort,
            @RequestParam(required = false) String dstPortRange,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String appProtocol,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "-30") int minutesAgo,
            @RequestParam(defaultValue = "5000") int limit
    ) {
        LocalDateTime resolvedEndTime = parseDateTimeOrDefault(endTime, LocalDateTime.now());
        LocalDateTime resolvedStartTime = parseDateTimeOrDefault(
                startTime,
                resolvedEndTime.minusMinutes(Math.abs(minutesAgo))
        );
        if (resolvedStartTime.isAfter(resolvedEndTime)) {
            LocalDateTime temp = resolvedStartTime;
            resolvedStartTime = resolvedEndTime;
            resolvedEndTime = temp;
        }
        int[] portRange = parsePortRange(dstPortRange);
        int exportLimit = Math.max(1, Math.min(limit, 10000));

        FlowAnalysisService.FlowSearchPage result = flowAnalysisService.searchFlowsAdvanced(
                srcIp,
                dstIp,
                srcCidr,
                dstCidr,
                srcPort,
                dstPort,
                portRange[0],
                portRange[1],
                protocol,
                appProtocol,
                resolvedStartTime,
                resolvedEndTime,
                0,
                exportLimit
        );

        String csv = buildCsv(result.content());
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=flow-search-export.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/{flowId}")
    public ResponseEntity<Map<String, Object>> getFlowDetail(@PathVariable Long flowId) {
        Map<String, Object> response = new LinkedHashMap<>();
        return flowRepository.findById(flowId)
                .map(flow -> {
                    response.put("status", "success");
                    response.put("flow", flow);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    response.put("status", "not_found");
                    response.put("message", "Flow not found");
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping("/by-ip/{ip}")
    public ResponseEntity<Map<String, Object>> getFlowsByIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "-60") int minutesAgo
    ) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        List<NetworkFlow> flows = flowRepository.findBySrcIpOrDstIpOrderByTimestampDesc(ip, ip).stream()
                .filter(flow -> !flow.getTimestamp().isBefore(startTime) && !flow.getTimestamp().isAfter(endTime))
                .limit(120)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", flows.size());
        response.put("flows", flows);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ip-profile/{ip}")
    public ResponseEntity<Map<String, Object>> getIpProfile(
            @PathVariable String ip,
            @RequestParam(defaultValue = "-60") int minutesAgo
    ) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>(flowAnalysisService.buildIpProfile(ip, startTime, endTime));
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-protocol/{protocol}")
    public ResponseEntity<Map<String, Object>> getFlowsByProtocol(@PathVariable String protocol) {
        List<NetworkFlow> flows = flowRepository.findByAppProtocolOrderByTimestampDesc(protocol);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", flows.size());
        response.put("flows", flows.stream().limit(120).toList());
        return ResponseEntity.ok(response);
    }

    private LocalDateTime parseDateTimeOrDefault(String raw, LocalDateTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    private int[] parsePortRange(String rawPortRange) {
        if (rawPortRange == null || rawPortRange.isBlank()) {
            return new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE };
        }
        String normalized = rawPortRange.trim();
        if (!normalized.contains("-")) {
            try {
                int port = Integer.parseInt(normalized);
                return new int[] { port, port };
            } catch (NumberFormatException ex) {
                return new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE };
            }
        }

        String[] parts = normalized.split("-");
        if (parts.length != 2) {
            return new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE };
        }
        try {
            int from = Integer.parseInt(parts[0].trim());
            int to = Integer.parseInt(parts[1].trim());
            return new int[] { Math.min(from, to), Math.max(from, to) };
        } catch (NumberFormatException ex) {
            return new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE };
        }
    }

    private String buildCsv(List<NetworkFlow> flows) {
        List<String> lines = new ArrayList<>();
        lines.add("id,timestamp,srcIp,dstIp,srcPort,dstPort,protocol,appProtocol,bytesSent,bytesRecv,packetsSent,packetsRecv,region,direction");
        for (NetworkFlow flow : flows) {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add(safe(flow.getId()));
            joiner.add(safe(flow.getTimestamp()));
            joiner.add(csvEscape(flow.getSrcIp()));
            joiner.add(csvEscape(flow.getDstIp()));
            joiner.add(safe(flow.getSrcPort()));
            joiner.add(safe(flow.getDstPort()));
            joiner.add(csvEscape(flow.getProtocol()));
            joiner.add(csvEscape(flow.getAppProtocol()));
            joiner.add(safe(flow.getBytesSent()));
            joiner.add(safe(flow.getBytesRecv()));
            joiner.add(safe(flow.getPacketsSent()));
            joiner.add(safe(flow.getPacketsRecv()));
            joiner.add(csvEscape(flow.getRegion()));
            joiner.add(csvEscape(flow.getDirection()));
            lines.add(joiner.toString());
        }
        return String.join("\n", lines);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
