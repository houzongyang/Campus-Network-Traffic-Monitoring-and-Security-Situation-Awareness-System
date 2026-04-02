package com.campus.network.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GeoLocationService {

    private static final List<LocationSeed> PUBLIC_LOCATIONS = List.of(
            new LocationSeed("China", "Beijing", 39.9042, 116.4074),
            new LocationSeed("China", "Shanghai", 31.2304, 121.4737),
            new LocationSeed("Japan", "Tokyo", 35.6762, 139.6503),
            new LocationSeed("Germany", "Frankfurt", 50.1109, 8.6821),
            new LocationSeed("Singapore", "Singapore", 1.3521, 103.8198),
            new LocationSeed("United States", "Los Angeles", 34.0522, -118.2437)
    );

    private static final List<LocationSeed> CAMPUS_LOCATIONS = List.of(
            new LocationSeed("China", "Suzhou - Dormitory A", 31.3214, 120.6389),
            new LocationSeed("China", "Suzhou - Dormitory B", 31.3187, 120.6323),
            new LocationSeed("China", "Suzhou - Teaching Zone", 31.3047, 120.6428),
            new LocationSeed("China", "Suzhou - Administration", 31.3012, 120.6257),
            new LocationSeed("China", "Suzhou - Library", 31.3064, 120.6341)
    );

    public GeoLocation locate(String ip) {
        if (ip == null || ip.isBlank()) {
            return new GeoLocation("Unknown", "Unknown", null, null);
        }

        List<LocationSeed> seeds = isPrivateIp(ip) ? CAMPUS_LOCATIONS : PUBLIC_LOCATIONS;
        LocationSeed seed = seeds.get(Math.floorMod(ip.hashCode(), seeds.size()));
        return new GeoLocation(seed.country(), seed.city(), seed.latitude(), seed.longitude());
    }

    public boolean isPrivateIp(String ip) {
        if (ip == null) {
            return false;
        }

        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }

        if (!ip.startsWith("172.")) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        try {
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public record GeoLocation(String country, String city, Double latitude, Double longitude) {
    }

    private record LocationSeed(String country, String city, double latitude, double longitude) {
    }
}
