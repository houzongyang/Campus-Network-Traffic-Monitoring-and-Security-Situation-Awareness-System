package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.NetworkFlowRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class LatestDataTimeService {

    private final NetworkFlowRepository flowRepository;

    public LatestDataTimeService(NetworkFlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public LocalDateTime resolveWindowEnd() {
        return flowRepository.findFirstByOrderByTimestampDesc()
                .map(NetworkFlow::getTimestamp)
                .orElseGet(LocalDateTime::now);
    }

    public LocalDateTime resolveWindowStart(int minutesAgo) {
        return resolveWindowEnd().minusMinutes(Math.abs(minutesAgo));
    }
}
