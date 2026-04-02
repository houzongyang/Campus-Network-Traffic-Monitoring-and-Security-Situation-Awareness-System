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
        name = "network_flows",
        indexes = {
                @Index(name = "idx_src_ip", columnList = "src_ip"),
                @Index(name = "idx_dst_ip", columnList = "dst_ip"),
                @Index(name = "idx_timestamp", columnList = "timestamp"),
                @Index(name = "idx_app_protocol", columnList = "app_protocol"),
                @Index(name = "idx_region", columnList = "region"),
                @Index(name = "idx_timestamp_src_dst", columnList = "timestamp,src_ip,dst_ip"),
                @Index(name = "idx_timestamp_protocol_app", columnList = "timestamp,protocol,app_protocol")
        }
)
public class NetworkFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String srcIp;

    @Column(nullable = false, length = 45)
    private String dstIp;

    @Column(nullable = false)
    private Integer srcPort;

    @Column(nullable = false)
    private Integer dstPort;

    @Column(nullable = false, length = 16)
    private String protocol;

    @Column(nullable = false)
    private Long bytesSent;

    @Column(nullable = false)
    private Long bytesRecv;

    @Column(nullable = false)
    private Long packetsSent;

    @Column(nullable = false)
    private Long packetsRecv;

    @Column(length = 50)
    private String appProtocol;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, length = 32)
    private String region;

    @Column(nullable = false, length = 16)
    private String direction;

    public NetworkFlow() {
    }

    public NetworkFlow(
            Long id,
            String srcIp,
            String dstIp,
            Integer srcPort,
            Integer dstPort,
            String protocol,
            Long bytesSent,
            Long bytesRecv,
            Long packetsSent,
            Long packetsRecv,
            String appProtocol,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime timestamp,
            String region,
            String direction
    ) {
        this.id = id;
        this.srcIp = srcIp;
        this.dstIp = dstIp;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.protocol = protocol;
        this.bytesSent = bytesSent;
        this.bytesRecv = bytesRecv;
        this.packetsSent = packetsSent;
        this.packetsRecv = packetsRecv;
        this.appProtocol = appProtocol;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timestamp = timestamp;
        this.region = region;
        this.direction = direction;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(Integer srcPort) {
        this.srcPort = srcPort;
    }

    public Integer getDstPort() {
        return dstPort;
    }

    public void setDstPort(Integer dstPort) {
        this.dstPort = dstPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Long getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(Long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public Long getBytesRecv() {
        return bytesRecv;
    }

    public void setBytesRecv(Long bytesRecv) {
        this.bytesRecv = bytesRecv;
    }

    public Long getPacketsSent() {
        return packetsSent;
    }

    public void setPacketsSent(Long packetsSent) {
        this.packetsSent = packetsSent;
    }

    public Long getPacketsRecv() {
        return packetsRecv;
    }

    public void setPacketsRecv(Long packetsRecv) {
        this.packetsRecv = packetsRecv;
    }

    public String getAppProtocol() {
        return appProtocol;
    }

    public void setAppProtocol(String appProtocol) {
        this.appProtocol = appProtocol;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
