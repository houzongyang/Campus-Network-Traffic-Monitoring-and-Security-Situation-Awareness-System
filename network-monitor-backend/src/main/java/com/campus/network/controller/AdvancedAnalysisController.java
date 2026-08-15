package com.campus.network.controller;

import com.campus.network.algorithm.BehaviorFingerprintService;
import com.campus.network.algorithm.EwmaEntropyAnomalyDetectionService;
import com.campus.network.algorithm.TemporalPredictiveAlertService;
import com.campus.network.algorithm.ThreatCorrelationEngineService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advanced-analysis")
public class AdvancedAnalysisController {

    private final EwmaEntropyAnomalyDetectionService ewmaService;
    private final ThreatCorrelationEngineService correlationService;
    private final BehaviorFingerprintService fingerprintService;
    private final TemporalPredictiveAlertService predictiveService;

    public AdvancedAnalysisController(
            EwmaEntropyAnomalyDetectionService ewmaService,
            ThreatCorrelationEngineService correlationService,
            BehaviorFingerprintService fingerprintService,
            TemporalPredictiveAlertService predictiveService
    ) {
        this.ewmaService = ewmaService;
        this.correlationService = correlationService;
        this.fingerprintService = fingerprintService;
        this.predictiveService = predictiveService;
    }

    /**
     * EWMA-Entropy Adaptive Anomaly Detection.
     * Runs EWMA-based anomaly detection with protocol entropy analysis.
     */
    @PostMapping("/ewma-ead/detect")
    public ResponseEntity<Map<String, Object>> runEwmaDetection(
            @RequestParam(defaultValue = "-30") int minutesAgo
    ) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.plusMinutes(minutesAgo);
        Map<String, Object> result = ewmaService.runDetection(start, end);
        return ResponseEntity.ok(result);
    }

    /**
     * Multi-Dimensional Threat Correlation Engine.
     * Correlates existing alerts to find attack chains.
     */
    @PostMapping("/mdtce/correlate")
    public ResponseEntity<Map<String, Object>> runCorrelation(
            @RequestParam(defaultValue = "-60") int minutesAgo
    ) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.plusMinutes(minutesAgo);
        Map<String, Object> result = correlationService.runCorrelationAnalysis(start, end);
        return ResponseEntity.ok(result);
    }

    /**
     * Network Behavior Fingerprinting - All IPs.
     * Generates behavioral fingerprints and risk scores for all active IPs.
     */
    @GetMapping("/nbf-rs/all")
    public ResponseEntity<Map<String, Object>> analyzeAllBehavior(
            @RequestParam(defaultValue = "-30") int minutesAgo
    ) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.plusMinutes(minutesAgo);
        Map<String, Object> result = fingerprintService.analyzeAllIps(start, end);
        return ResponseEntity.ok(result);
    }

    /**
     * Network Behavior Fingerprinting - Single IP.
     * Generates detailed behavioral fingerprint for a specific IP.
     */
    @GetMapping("/nbf-rs/ip/{ip}")
    public ResponseEntity<Map<String, Object>> analyzeIpBehavior(
            @PathVariable String ip,
            @RequestParam(defaultValue = "-60") int minutesAgo
    ) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.plusMinutes(minutesAgo);
        Map<String, Object> result = fingerprintService.analyzeIp(ip, start, end);
        return ResponseEntity.ok(result);
    }

    /**
     * Temporal Pattern Mining with Predictive Alert.
     * Analyzes trends and predicts upcoming anomalies.
     */
    @PostMapping("/tpm-pa/predict")
    public ResponseEntity<Map<String, Object>> runPrediction(
            @RequestParam(defaultValue = "-60") int minutesAgo
    ) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.plusMinutes(minutesAgo);
        Map<String, Object> result = predictiveService.runPredictiveAnalysis(start, end);
        return ResponseEntity.ok(result);
    }

    /**
     * Run all four innovative algorithms and return a comprehensive report.
     */
    @PostMapping("/comprehensive")
    public ResponseEntity<Map<String, Object>> runComprehensiveAnalysis(
            @RequestParam(defaultValue = "-60") int minutesAgo
    ) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.plusMinutes(minutesAgo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysisWindow", Map.of("start", start, "end", end));

        try {
            result.put("ewmaEad", ewmaService.runDetection(start, end));
        } catch (Exception e) {
            result.put("ewmaEad", Map.of("error", e.getMessage()));
        }

        try {
            result.put("mdtce", correlationService.runCorrelationAnalysis(start, end));
        } catch (Exception e) {
            result.put("mdtce", Map.of("error", e.getMessage()));
        }

        try {
            result.put("nbfRs", fingerprintService.analyzeAllIps(start, end));
        } catch (Exception e) {
            result.put("nbfRs", Map.of("error", e.getMessage()));
        }

        try {
            result.put("tpmPa", predictiveService.runPredictiveAnalysis(start, end));
        } catch (Exception e) {
            result.put("tpmPa", Map.of("error", e.getMessage()));
        }

        return ResponseEntity.ok(result);
    }
}
