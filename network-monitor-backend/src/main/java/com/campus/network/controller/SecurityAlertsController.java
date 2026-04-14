package com.campus.network.controller;

import com.campus.network.model.SecurityAlert;
import com.campus.network.repository.SecurityAlertRepository;
import com.campus.network.service.LatestDataTimeService;
import com.campus.network.service.ThreatDetectionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
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
@RequestMapping("/api/security")
public class SecurityAlertsController {

    private record AlertQueryResult(
            List<SecurityAlert> content,
            long total,
            int page,
            int size,
            int totalPages
    ) {
    }

    private final SecurityAlertRepository alertRepository;
    private final ThreatDetectionService threatDetectionService;
    private final LatestDataTimeService latestDataTimeService;

    public SecurityAlertsController(
            SecurityAlertRepository alertRepository,
            ThreatDetectionService threatDetectionService,
            LatestDataTimeService latestDataTimeService
    ) {
        this.alertRepository = alertRepository;
        this.threatDetectionService = threatDetectionService;
        this.latestDataTimeService = latestDataTimeService;
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts(
            @RequestParam(defaultValue = "-60") int minutesAgo,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String dstIp,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "detectedTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AlertQueryResult result = queryAlerts(
                minutesAgo,
                startTime,
                endTime,
                alertType,
                severity,
                srcIp,
                dstIp,
                keyword,
                sortBy,
                sortOrder,
                page,
                size
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", result.content().size());
        response.put("total", result.total());
        response.put("page", result.page());
        response.put("size", result.size());
        response.put("totalPages", result.totalPages());
        response.put("alerts", result.content());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts/export")
    public ResponseEntity<byte[]> exportAlerts(
            @RequestParam(defaultValue = "-60") int minutesAgo,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String dstIp,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "detectedTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "5000") int limit
    ) {
        int exportLimit = Math.max(1, Math.min(limit, 10000));
        AlertQueryResult result = queryAlerts(
                minutesAgo,
                startTime,
                endTime,
                alertType,
                severity,
                srcIp,
                dstIp,
                keyword,
                sortBy,
                sortOrder,
                0,
                exportLimit
        );

        String csv = buildAlertCsv(result.content());
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=security-alerts-export.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/alerts/{ip}")
    public ResponseEntity<Map<String, Object>> getAlertsForIp(@PathVariable String ip) {
        List<SecurityAlert> alerts = alertRepository.findBySrcIpOrDstIpOrderByDetectedTimeDesc(ip, ip);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", alerts.size());
        response.put("alerts", alerts);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/critical-alerts")
    public ResponseEntity<Map<String, Object>> getCriticalAlerts(@RequestParam(defaultValue = "-60") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));
        List<SecurityAlert> alerts = alertRepository.findCriticalAlerts(startTime, endTime);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("count", alerts.size());
        response.put("alerts", alerts);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alert-statistics")
    public ResponseEntity<Map<String, Object>> getAlertStatistics(@RequestParam(defaultValue = "-60") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));
        Map<String, Object> response = new LinkedHashMap<>(threatDetectionService.getThreatStatistics(startTime, endTime));
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run-detection")
    public ResponseEntity<Map<String, Object>> runThreatDetection(@RequestParam(defaultValue = "-60") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));
        threatDetectionService.runFullThreatDetection(startTime, endTime);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Threat detection completed.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/geo-distribution")
    public ResponseEntity<Map<String, Object>> getGeoDistribution(@RequestParam(defaultValue = "-60") int minutesAgo) {
        LocalDateTime endTime = latestDataTimeService.resolveWindowEnd();
        LocalDateTime startTime = endTime.minusMinutes(Math.abs(minutesAgo));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("points", threatDetectionService.getGeoDistribution(startTime, endTime));
        return ResponseEntity.ok(response);
    }

    private AlertQueryResult queryAlerts(
            int minutesAgo,
            String startTime,
            String endTime,
            String alertType,
            String severity,
            String srcIp,
            String dstIp,
            String keyword,
            String sortBy,
            String sortOrder,
            int page,
            int size
    ) {
        LocalDateTime resolvedEndTime = parseDateTimeOrDefault(endTime, latestDataTimeService.resolveWindowEnd());
        LocalDateTime resolvedStartTime = parseDateTimeOrDefault(
                startTime,
                resolvedEndTime.minusMinutes(Math.abs(minutesAgo))
        );
        if (resolvedStartTime.isAfter(resolvedEndTime)) {
            LocalDateTime temp = resolvedStartTime;
            resolvedStartTime = resolvedEndTime;
            resolvedEndTime = temp;
        }

        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 500));

        Comparator<SecurityAlert> comparator = resolveSortComparator(sortBy, sortOrder);
        List<SecurityAlert> filtered = alertRepository.findByDetectedTimeBetweenOrderByDetectedTimeDesc(resolvedStartTime, resolvedEndTime)
                .stream()
                .filter(alert -> matchesText(alert.getAlertType(), alertType))
                .filter(alert -> matchesText(alert.getSeverity(), severity))
                .filter(alert -> matchesContains(alert.getSrcIp(), srcIp))
                .filter(alert -> matchesContains(alert.getDstIp(), dstIp))
                .filter(alert -> matchesKeyword(alert, keyword))
                .sorted(comparator)
                .toList();

        long total = filtered.size();
        int fromIndex = Math.min(normalizedPage * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
        int totalPages = (int) Math.ceil(total / (double) normalizedSize);
        List<SecurityAlert> pageContent = filtered.subList(fromIndex, toIndex);

        return new AlertQueryResult(pageContent, total, normalizedPage, normalizedSize, totalPages);
    }

    private Comparator<SecurityAlert> resolveSortComparator(String sortBy, String sortOrder) {
        String normalizedSortBy = sortBy == null ? "detectedTime" : sortBy.trim();
        boolean desc = sortOrder == null || !"asc".equalsIgnoreCase(sortOrder.trim());
        Comparator<SecurityAlert> comparator = switch (normalizedSortBy) {
            case "severity" -> Comparator.comparingInt(alert -> severityWeight(alert.getSeverity()));
            case "alertType" -> Comparator.comparing(alert -> safe(alert.getAlertType()), String.CASE_INSENSITIVE_ORDER);
            case "srcIp" -> Comparator.comparing(alert -> safe(alert.getSrcIp()), String.CASE_INSENSITIVE_ORDER);
            case "dstIp" -> Comparator.comparing(alert -> safe(alert.getDstIp()), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(SecurityAlert::getDetectedTime);
        };

        return desc ? comparator.reversed() : comparator;
    }

    private int severityWeight(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toLowerCase()) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
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

    private boolean matchesText(String value, String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.equalsIgnoreCase(condition.trim());
    }

    private boolean matchesContains(String value, String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase().contains(condition.trim().toLowerCase());
    }

    private boolean matchesKeyword(SecurityAlert alert, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return safe(alert.getDescription()).toLowerCase().contains(normalized)
                || safe(alert.getThreatDetails()).toLowerCase().contains(normalized)
                || safe(alert.getCity()).toLowerCase().contains(normalized)
                || safe(alert.getCountry()).toLowerCase().contains(normalized);
    }

    private String buildAlertCsv(List<SecurityAlert> alerts) {
        List<String> lines = new ArrayList<>();
        lines.add("id,detectedTime,alertType,severity,srcIp,dstIp,country,city,latitude,longitude,description,confirmed");
        for (SecurityAlert alert : alerts) {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add(safe(alert.getId()));
            joiner.add(safe(alert.getDetectedTime()));
            joiner.add(csvEscape(alert.getAlertType()));
            joiner.add(csvEscape(alert.getSeverity()));
            joiner.add(csvEscape(alert.getSrcIp()));
            joiner.add(csvEscape(alert.getDstIp()));
            joiner.add(csvEscape(alert.getCountry()));
            joiner.add(csvEscape(alert.getCity()));
            joiner.add(safe(alert.getLatitude()));
            joiner.add(safe(alert.getLongitude()));
            joiner.add(csvEscape(alert.getDescription()));
            joiner.add(safe(alert.getConfirmed()));
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
