package com.campus.network.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AppIdentificationService {

    private static final Map<Integer, String> PORT_PROTOCOL_MAP = new LinkedHashMap<>();

    static {
        PORT_PROTOCOL_MAP.put(53, "DNS");
        PORT_PROTOCOL_MAP.put(67, "DHCP");
        PORT_PROTOCOL_MAP.put(68, "DHCP");
        PORT_PROTOCOL_MAP.put(80, "HTTP");
        PORT_PROTOCOL_MAP.put(110, "POP3");
        PORT_PROTOCOL_MAP.put(123, "NTP");
        PORT_PROTOCOL_MAP.put(143, "IMAP");
        PORT_PROTOCOL_MAP.put(161, "SNMP");
        PORT_PROTOCOL_MAP.put(389, "LDAP");
        PORT_PROTOCOL_MAP.put(443, "HTTPS");
        PORT_PROTOCOL_MAP.put(465, "SMTPS");
        PORT_PROTOCOL_MAP.put(514, "Syslog");
        PORT_PROTOCOL_MAP.put(587, "SMTP");
        PORT_PROTOCOL_MAP.put(636, "LDAPS");
        PORT_PROTOCOL_MAP.put(993, "IMAPS");
        PORT_PROTOCOL_MAP.put(995, "POP3S");
        PORT_PROTOCOL_MAP.put(1883, "MQTT");
        PORT_PROTOCOL_MAP.put(3306, "MySQL");
        PORT_PROTOCOL_MAP.put(3389, "RDP");
        PORT_PROTOCOL_MAP.put(5060, "SIP");
        PORT_PROTOCOL_MAP.put(5432, "PostgreSQL");
        PORT_PROTOCOL_MAP.put(554, "RTSP");
        PORT_PROTOCOL_MAP.put(8080, "HTTP-Alt");
        PORT_PROTOCOL_MAP.put(8443, "HTTPS-Alt");
        PORT_PROTOCOL_MAP.put(22, "SSH");
        PORT_PROTOCOL_MAP.put(21, "FTP");
        PORT_PROTOCOL_MAP.put(23, "Telnet");
        PORT_PROTOCOL_MAP.put(445, "SMB");
    }

    public String identifyByPort(Integer port) {
        if (port == null) {
            return "Unknown";
        }

        String knownProtocol = PORT_PROTOCOL_MAP.get(port);
        if (knownProtocol != null) {
            return knownProtocol;
        }

        if (port >= 6881 && port <= 6999) {
            return "BitTorrent";
        }

        if (port >= 1024 && port <= 49151) {
            return "Campus-App";
        }

        if (port > 49151) {
            return "Ephemeral";
        }

        return "Unknown";
    }

    public String identifyProtocol(Integer srcPort, Integer dstPort) {
        String dstProtocol = identifyByPort(dstPort);
        if (!"Unknown".equals(dstProtocol) && !"Campus-App".equals(dstProtocol) && !"Ephemeral".equals(dstProtocol)) {
            return dstProtocol;
        }

        String srcProtocol = identifyByPort(srcPort);
        if (!"Unknown".equals(srcProtocol)) {
            return srcProtocol;
        }

        return dstProtocol;
    }

    public String[] getSupportedProtocols() {
        return new String[] {
                "DNS", "DHCP", "HTTP", "HTTPS", "HTTP-Alt", "HTTPS-Alt",
                "SSH", "FTP", "Telnet", "SMTP", "SMTPS", "POP3", "POP3S",
                "IMAP", "IMAPS", "LDAP", "LDAPS", "SNMP", "NTP", "MQTT",
                "MySQL", "PostgreSQL", "RDP", "RTSP", "SIP", "SMB", "BitTorrent"
        };
    }
}
