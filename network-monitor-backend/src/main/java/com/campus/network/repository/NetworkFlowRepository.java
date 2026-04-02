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
