package com.campus.network.repository;

import com.campus.network.model.SecurityAlert;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    long deleteByDetectedTimeBefore(LocalDateTime detectedTime);

    List<SecurityAlert> findByDetectedTimeBetweenOrderByDetectedTimeDesc(LocalDateTime start, LocalDateTime end);

    List<SecurityAlert> findBySrcIpOrDstIpOrderByDetectedTimeDesc(String srcIp, String dstIp);

    @Query(
            "SELECT sa FROM SecurityAlert sa "
                    + "WHERE sa.severity IN ('high', 'critical') "
                    + "AND sa.detectedTime BETWEEN :startTime AND :endTime "
                    + "ORDER BY sa.detectedTime DESC"
    )
    List<SecurityAlert> findCriticalAlerts(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
