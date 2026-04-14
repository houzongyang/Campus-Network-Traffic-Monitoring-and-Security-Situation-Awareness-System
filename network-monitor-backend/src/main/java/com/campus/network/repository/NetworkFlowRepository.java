package com.campus.network.repository;

import com.campus.network.model.NetworkFlow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NetworkFlowRepository extends JpaRepository<NetworkFlow, Long> {

    Optional<NetworkFlow> findFirstByOrderByTimestampDesc();

    long deleteByTimestampBefore(LocalDateTime timestamp);

    List<NetworkFlow> findBySrcIpOrDstIpOrderByTimestampDesc(String srcIp, String dstIp);

    List<NetworkFlow> findByAppProtocolOrderByTimestampDesc(String appProtocol);

    List<NetworkFlow> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime);

    Page<NetworkFlow> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    @Query(
            "SELECT COALESCE(SUM(nf.bytesSent + nf.bytesRecv), 0) "
                    + "FROM NetworkFlow nf WHERE nf.timestamp BETWEEN :startTime AND :endTime"
    )
    Long sumTotalBytesBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query(
            "SELECT COALESCE(SUM(nf.packetsSent + nf.packetsRecv), 0) "
                    + "FROM NetworkFlow nf WHERE nf.timestamp BETWEEN :startTime AND :endTime"
    )
    Long sumTotalPacketsBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT src_ip AS ip
                        FROM network_flows
                        WHERE timestamp BETWEEN :startTime AND :endTime
                        UNION
                        SELECT dst_ip AS ip
                        FROM network_flows
                        WHERE timestamp BETWEEN :startTime AND :endTime
                    ) active_ips
                    """,
            nativeQuery = true
    )
    long countDistinctActiveIpsBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query(
            value = """
                    SELECT COALESCE(app_protocol, 'Unknown') AS protocol_name,
                           SUM(bytes_sent + bytes_recv) AS total_value
                    FROM network_flows
                    WHERE timestamp BETWEEN :startTime AND :endTime
                    GROUP BY COALESCE(app_protocol, 'Unknown')
                    ORDER BY total_value DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Object[]> aggregateAppBytesBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    SELECT COALESCE(app_protocol, 'Unknown') AS protocol_name,
                           SUM(packets_sent + packets_recv) AS total_value
                    FROM network_flows
                    WHERE timestamp BETWEEN :startTime AND :endTime
                    GROUP BY COALESCE(app_protocol, 'Unknown')
                    ORDER BY total_value DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Object[]> aggregateAppPacketsBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    WITH traffic AS (
                        SELECT COALESCE(region, 'unknown') AS region_name,
                               SUM(bytes_sent + bytes_recv) AS total_bytes,
                               SUM(packets_sent + packets_recv) AS total_packets,
                               COUNT(*) AS flow_count
                        FROM network_flows
                        WHERE timestamp BETWEEN :startTime AND :endTime
                        GROUP BY COALESCE(region, 'unknown')
                    ),
                    active_ips AS (
                        SELECT region_name, COUNT(DISTINCT ip) AS active_ip_count
                        FROM (
                            SELECT COALESCE(region, 'unknown') AS region_name, src_ip AS ip
                            FROM network_flows
                            WHERE timestamp BETWEEN :startTime AND :endTime
                            UNION
                            SELECT COALESCE(region, 'unknown') AS region_name, dst_ip AS ip
                            FROM network_flows
                            WHERE timestamp BETWEEN :startTime AND :endTime
                        ) ips
                        GROUP BY region_name
                    )
                    SELECT traffic.region_name,
                           traffic.total_bytes,
                           traffic.total_packets,
                           traffic.flow_count,
                           COALESCE(active_ips.active_ip_count, 0)
                    FROM traffic
                    LEFT JOIN active_ips ON active_ips.region_name = traffic.region_name
                    ORDER BY traffic.total_bytes DESC
                    """,
            nativeQuery = true
    )
    List<Object[]> aggregateRegionTrafficBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query(
            value = """
                    SELECT to_timestamp(
                               floor(extract(epoch from timestamp) / :bucketSeconds) * :bucketSeconds
                           ) AS bucket_time,
                           SUM(bytes_sent + bytes_recv) AS total_bytes,
                           SUM(packets_sent + packets_recv) AS total_packets
                    FROM network_flows
                    WHERE timestamp BETWEEN :startTime AND :endTime
                    GROUP BY bucket_time
                    ORDER BY bucket_time
                    """,
            nativeQuery = true
    )
    List<Object[]> aggregateThroughputTrendBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("bucketSeconds") long bucketSeconds
    );

    @Query(
            "SELECT nf FROM NetworkFlow nf WHERE "
                    + "(:srcIp IS NULL OR nf.srcIp = :srcIp) AND "
                    + "(:dstIp IS NULL OR nf.dstIp = :dstIp) AND "
                    + "(:srcPort IS NULL OR nf.srcPort = :srcPort) AND "
                    + "(:dstPort IS NULL OR nf.dstPort = :dstPort) AND "
                    + "(:appProtocol IS NULL OR nf.appProtocol = :appProtocol) AND "
                    + "nf.timestamp BETWEEN :startTime AND :endTime"
    )
    Page<NetworkFlow> searchFlows(
            @Param("srcIp") String srcIp,
            @Param("dstIp") String dstIp,
            @Param("srcPort") Integer srcPort,
            @Param("dstPort") Integer dstPort,
            @Param("appProtocol") String appProtocol,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );

    @Query(
            "SELECT nf FROM NetworkFlow nf "
                    + "WHERE nf.timestamp BETWEEN :startTime AND :endTime "
                    + "ORDER BY (nf.bytesSent + nf.bytesRecv) DESC"
    )
    Page<NetworkFlow> findTopFlowsByBytes(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
}
