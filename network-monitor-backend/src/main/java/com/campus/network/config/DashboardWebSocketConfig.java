package com.campus.network.config;

import com.campus.network.service.DashboardMetricsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class DashboardWebSocketConfig implements WebSocketConfigurer {

    private final DashboardMetricsWebSocketHandler dashboardMetricsWebSocketHandler;

    public DashboardWebSocketConfig(DashboardMetricsWebSocketHandler dashboardMetricsWebSocketHandler) {
        this.dashboardMetricsWebSocketHandler = dashboardMetricsWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dashboardMetricsWebSocketHandler, "/ws/dashboard/metrics")
                .setAllowedOriginPatterns("*");
    }
}
