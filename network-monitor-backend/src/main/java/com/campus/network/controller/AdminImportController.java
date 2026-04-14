package com.campus.network.controller;

import com.campus.network.service.PcapImportService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private final PcapImportService pcapImportService;

    public AdminImportController(PcapImportService pcapImportService) {
        this.pcapImportService = pcapImportService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ready");
        response.put("supportedFormats", new String[] { "pcap" });
        response.put("supportedLinkTypes", new int[] { 1, 101 });
        response.put("pathImportEndpoint", "/api/admin/import/pcap-path");
        response.put("uploadImportEndpoint", "/api/admin/import/pcap-upload");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pcap-path")
    public ResponseEntity<Map<String, Object>> importByPath(
            @RequestParam String path,
            @RequestParam(defaultValue = "true") boolean replaceExisting,
            @RequestParam(defaultValue = "true") boolean rebaseTimestamps,
            @RequestParam(defaultValue = "true") boolean runThreatDetection,
            @RequestParam(required = false) Integer maxPackets,
            @RequestParam(defaultValue = "60") Integer inactivitySeconds
    ) throws Exception {
        PcapImportService.ImportResult result = pcapImportService.importFromPath(
                Path.of(path),
                replaceExisting,
                rebaseTimestamps,
                runThreatDetection,
                maxPackets,
                inactivitySeconds
        );
        return ResponseEntity.ok(buildSuccessResponse(result));
    }

    @PostMapping(path = "/pcap-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importByUpload(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "true") boolean replaceExisting,
            @RequestParam(defaultValue = "true") boolean rebaseTimestamps,
            @RequestParam(defaultValue = "true") boolean runThreatDetection,
            @RequestParam(required = false) Integer maxPackets,
            @RequestParam(defaultValue = "60") Integer inactivitySeconds
    ) throws Exception {
        PcapImportService.ImportResult result = pcapImportService.importUploadedFile(
                file,
                replaceExisting,
                rebaseTimestamps,
                runThreatDetection,
                maxPackets,
                inactivitySeconds
        );
        return ResponseEntity.ok(buildSuccessResponse(result));
    }

    private Map<String, Object> buildSuccessResponse(PcapImportService.ImportResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("result", result);
        return response;
    }
}
