package com.campus.network;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NetworkMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetworkMonitorApplication.class, args);
    }

}
