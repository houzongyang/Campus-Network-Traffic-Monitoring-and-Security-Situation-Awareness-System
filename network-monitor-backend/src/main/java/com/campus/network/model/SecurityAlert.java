package com.campus.network.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "security_alerts",
        indexes = {
                @Index(name = "idx_alert_time", columnList = "detected_time"),
                @Index(name = "idx_alert_type", columnList = "alert_type"),
                @Index(name = "idx_alert_src_ip", columnList = "src_ip")
        }
)
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String alertType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 45)
    private String srcIp;

    @Column(length = 45)
    private String dstIp;

    @Column(nullable = false)
    private LocalDateTime detectedTime;

    @Column(length = 500)
    private String description;

    @Column(length = 64)
    private String country;

    @Column(length = 64)
    private String city;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(columnDefinition = "TEXT")
    private String threatDetails;

    @Column(nullable = false)
    private Boolean confirmed = Boolean.FALSE;

    public SecurityAlert() {
    }

    public SecurityAlert(
            Long id,
            String alertType,
            String severity,
            String srcIp,
            String dstIp,
            LocalDateTime detectedTime,
            String description,
            String country,
            String city,
            Double latitude,
            Double longitude,
            String threatDetails,
            Boolean confirmed
    ) {
        this.id = id;
        this.alertType = alertType;
        this.severity = severity;
        this.srcIp = srcIp;
        this.dstIp = dstIp;
        this.detectedTime = detectedTime;
        this.description = description;
        this.country = country;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.threatDetails = threatDetails;
        this.confirmed = confirmed;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSrcIp() {
        return srcIp;
    }

    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }

    public String getDstIp() {
        return dstIp;
    }

    public void setDstIp(String dstIp) {
        this.dstIp = dstIp;
    }

    public LocalDateTime getDetectedTime() {
        return detectedTime;
    }

    public void setDetectedTime(LocalDateTime detectedTime) {
        this.detectedTime = detectedTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getThreatDetails() {
        return threatDetails;
    }

    public void setThreatDetails(String threatDetails) {
        this.threatDetails = threatDetails;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }
}
