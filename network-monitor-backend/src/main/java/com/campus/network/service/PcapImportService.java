package com.campus.network.service;

import com.campus.network.model.NetworkFlow;
import com.campus.network.repository.NetworkFlowRepository;
import com.campus.network.repository.SecurityAlertRepository;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PcapImportService {

    public record ImportResult(
            String sourceName,
            long packetsRead,
            long packetsImported,
            long packetsSkipped,
            long flowsImported,
            int linkType,
            boolean rebasedTimestamps,
            LocalDateTime sourceStartTime,
            LocalDateTime sourceEndTime,
            LocalDateTime importedStartTime,
            LocalDateTime importedEndTime,
            long durationMillis
    ) {
    }

    private record PcapMetadata(long packetsRead, Instant sourceStart, Instant sourceEnd, int linkType) {
    }

    private record FlowKey(String srcIp, String dstIp, int srcPort, int dstPort, String protocol) {
    }

    private record ParsedPacket(
            String srcIp,
            String dstIp,
            int srcPort,
            int dstPort,
            String protocol,
            String appProtocol,
            String region,
            String direction,
            long bytes,
            Instant timestamp
    ) {
    }

    private static final Logger log = LoggerFactory.getLogger(PcapImportService.class);

    private static final int LINKTYPE_ETHERNET = 1;
    private static final int LINKTYPE_RAW = 101;
    private static final int ETHER_TYPE_IPV4 = 0x0800;
    private static final int ETHER_TYPE_IPV6 = 0x86DD;
    private static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 60L;
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final long CLEANUP_PACKET_INTERVAL = 10_000L;

    private final NetworkFlowRepository flowRepository;
    private final SecurityAlertRepository alertRepository;
    private final AppIdentificationService appIdentificationService;
    private final ThreatDetectionService threatDetectionService;
    private final GeoLocationService geoLocationService;
    private final Path importRootPath;

    public PcapImportService(
            NetworkFlowRepository flowRepository,
            SecurityAlertRepository alertRepository,
            AppIdentificationService appIdentificationService,
            ThreatDetectionService threatDetectionService,
            GeoLocationService geoLocationService,
            @Value("${app.import.root-path:}") String importRootPath
    ) {
        this.flowRepository = flowRepository;
        this.alertRepository = alertRepository;
        this.appIdentificationService = appIdentificationService;
        this.threatDetectionService = threatDetectionService;
        this.geoLocationService = geoLocationService;
        this.importRootPath = normalizeImportRoot(importRootPath);
    }

    public ImportResult importFromPath(
            Path path,
            boolean replaceExisting,
            boolean rebaseTimestamps,
            boolean runThreatDetection,
            Integer maxPackets,
            Integer inactivitySeconds
    ) throws IOException {
        Path resolvedPath = resolveImportPath(path);
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            throw new IOException("PCAP file does not exist: " + resolvedPath);
        }

        long startedAt = System.currentTimeMillis();
        PcapMetadata metadata = inspectMetadata(resolvedPath, normalizeMaxPackets(maxPackets));
        if (metadata.sourceStart() == null || metadata.sourceEnd() == null) {
            throw new IOException("No supported packets were found in PCAP: " + resolvedPath);
        }

        Duration timeShift = rebaseTimestamps
                ? Duration.between(metadata.sourceEnd(), Instant.now().minusSeconds(5))
                : Duration.ZERO;

        if (replaceExisting) {
            alertRepository.deleteAllInBatch();
            flowRepository.deleteAllInBatch();
        }

        ImportAccumulator accumulator = importPackets(
                resolvedPath,
                metadata.linkType(),
                timeShift,
                normalizeMaxPackets(maxPackets),
                normalizeIdleTimeout(inactivitySeconds)
        );

        LocalDateTime importedStart = toLocalDateTime(metadata.sourceStart().plus(timeShift));
        LocalDateTime importedEnd = toLocalDateTime(metadata.sourceEnd().plus(timeShift));
        if (runThreatDetection && accumulator.flowsImported > 0) {
            threatDetectionService.runFullThreatDetection(importedStart, importedEnd);
        }

        return new ImportResult(
                resolvedPath.toAbsolutePath().toString(),
                metadata.packetsRead(),
                accumulator.packetsImported,
                accumulator.packetsSkipped,
                accumulator.flowsImported,
                metadata.linkType(),
                rebaseTimestamps,
                toLocalDateTime(metadata.sourceStart()),
                toLocalDateTime(metadata.sourceEnd()),
                importedStart,
                importedEnd,
                System.currentTimeMillis() - startedAt
        );
    }

    public ImportResult importUploadedFile(
            MultipartFile file,
            boolean replaceExisting,
            boolean rebaseTimestamps,
            boolean runThreatDetection,
            Integer maxPackets,
            Integer inactivitySeconds
    ) throws IOException {
        Path tempFile = Files.createTempFile("network-monitor-import-", ".pcap");
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            return importFromPath(
                    tempFile,
                    replaceExisting,
                    rebaseTimestamps,
                    runThreatDetection,
                    maxPackets,
                    inactivitySeconds
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private PcapMetadata inspectMetadata(Path path, Integer maxPackets) throws IOException {
        try (InputStream rawInput = new BufferedInputStream(Files.newInputStream(path))) {
            PcapReader reader = new PcapReader(rawInput);
            Instant firstTimestamp = null;
            Instant lastTimestamp = null;
            long packetsRead = 0L;

            while (true) {
                PcapPacket packet = reader.nextPacket();
                if (packet == null) {
                    break;
                }

                packetsRead++;
                ParsedPacket parsedPacket = decodePacket(packet.data(), reader.linkType(), packet.originalLength(), packet.timestamp(), Duration.ZERO);
                if (parsedPacket != null) {
                    if (firstTimestamp == null) {
                        firstTimestamp = parsedPacket.timestamp();
                    }
                    lastTimestamp = parsedPacket.timestamp();
                }

                if (maxPackets != null && packetsRead >= maxPackets) {
                    break;
                }
            }

            return new PcapMetadata(packetsRead, firstTimestamp, lastTimestamp, reader.linkType());
        }
    }

    private ImportAccumulator importPackets(
            Path path,
            int linkType,
            Duration timeShift,
            Integer maxPackets,
            int inactivitySeconds
    ) throws IOException {
        Map<FlowKey, MutableFlowAggregate> activeFlows = new HashMap<>();
        List<NetworkFlow> batch = new ArrayList<>(DEFAULT_BATCH_SIZE);
        ImportAccumulator accumulator = new ImportAccumulator();

        try (InputStream rawInput = new BufferedInputStream(Files.newInputStream(path))) {
            PcapReader reader = new PcapReader(rawInput);
            if (reader.linkType() != linkType) {
                throw new IOException("PCAP link type changed during import.");
            }

            while (true) {
                PcapPacket packet = reader.nextPacket();
                if (packet == null) {
                    break;
                }

                accumulator.packetsRead++;
                ParsedPacket parsedPacket = decodePacket(
                        packet.data(),
                        reader.linkType(),
                        packet.originalLength(),
                        packet.timestamp(),
                        timeShift
                );
                if (parsedPacket == null) {
                    accumulator.packetsSkipped++;
                } else {
                    accumulator.packetsImported++;
                    mergePacket(activeFlows, parsedPacket, inactivitySeconds, batch, accumulator);
                }

                if (accumulator.packetsRead % CLEANUP_PACKET_INTERVAL == 0 && parsedPacket != null) {
                    flushIdleFlows(activeFlows, parsedPacket.timestamp(), inactivitySeconds, batch, accumulator);
                }

                if (batch.size() >= DEFAULT_BATCH_SIZE) {
                    saveBatch(batch);
                }

                if (maxPackets != null && accumulator.packetsRead >= maxPackets) {
                    break;
                }
            }
        }

        flushAll(activeFlows, batch, accumulator);
        saveBatch(batch);
        return accumulator;
    }

    private void mergePacket(
            Map<FlowKey, MutableFlowAggregate> activeFlows,
            ParsedPacket packet,
            int inactivitySeconds,
            List<NetworkFlow> batch,
            ImportAccumulator accumulator
    ) {
        FlowKey key = new FlowKey(packet.srcIp(), packet.dstIp(), packet.srcPort(), packet.dstPort(), packet.protocol());
        MutableFlowAggregate aggregate = activeFlows.get(key);
        if (aggregate == null) {
            activeFlows.put(key, new MutableFlowAggregate(packet));
            return;
        }

        if (Duration.between(aggregate.lastSeen, packet.timestamp()).getSeconds() > inactivitySeconds) {
            batch.add(aggregate.toEntity());
            accumulator.flowsImported++;
            activeFlows.put(key, new MutableFlowAggregate(packet));
            return;
        }

        aggregate.accept(packet);
    }

    private void flushIdleFlows(
            Map<FlowKey, MutableFlowAggregate> activeFlows,
            Instant currentTimestamp,
            int inactivitySeconds,
            List<NetworkFlow> batch,
            ImportAccumulator accumulator
    ) {
        Iterator<Map.Entry<FlowKey, MutableFlowAggregate>> iterator = activeFlows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<FlowKey, MutableFlowAggregate> entry = iterator.next();
            MutableFlowAggregate aggregate = entry.getValue();
            if (Duration.between(aggregate.lastSeen, currentTimestamp).getSeconds() <= inactivitySeconds) {
                continue;
            }

            batch.add(aggregate.toEntity());
            accumulator.flowsImported++;
            iterator.remove();
        }
    }

    private void flushAll(
            Map<FlowKey, MutableFlowAggregate> activeFlows,
            List<NetworkFlow> batch,
            ImportAccumulator accumulator
    ) {
        for (MutableFlowAggregate aggregate : activeFlows.values()) {
            batch.add(aggregate.toEntity());
            accumulator.flowsImported++;
        }
        activeFlows.clear();
    }

    private void saveBatch(List<NetworkFlow> batch) {
        if (batch.isEmpty()) {
            return;
        }
        flowRepository.saveAll(batch);
        batch.clear();
    }

    private ParsedPacket decodePacket(
            byte[] data,
            int linkType,
            int originalLength,
            Instant timestamp,
            Duration timeShift
    ) {
        try {
            if (linkType == LINKTYPE_RAW) {
                return decodeIpPacket(data, 0, originalLength, timestamp.plus(timeShift));
            }

            if (linkType == LINKTYPE_ETHERNET) {
                return decodeEthernetPacket(data, originalLength, timestamp.plus(timeShift));
            }
        } catch (RuntimeException ex) {
            log.debug("Skip malformed packet: {}", ex.getMessage());
        }

        return null;
    }

    private ParsedPacket decodeEthernetPacket(byte[] data, int originalLength, Instant timestamp) {
        if (data.length < 14) {
            return null;
        }

        int offset = 14;
        int etherType = unsignedShort(data, 12);
        if (etherType == 0x8100 && data.length >= 18) {
            etherType = unsignedShort(data, 16);
            offset = 18;
        }

        if (etherType == ETHER_TYPE_IPV4 || etherType == ETHER_TYPE_IPV6) {
            return decodeIpPacket(data, offset, originalLength, timestamp);
        }

        return null;
    }

    private ParsedPacket decodeIpPacket(byte[] data, int offset, int originalLength, Instant timestamp) {
        if (data.length <= offset) {
            return null;
        }

        int version = (data[offset] >> 4) & 0x0F;
        if (version == 4) {
            return decodeIpv4Packet(data, offset, originalLength, timestamp);
        }
        if (version == 6) {
            return decodeIpv6Packet(data, offset, originalLength, timestamp);
        }
        return null;
    }

    private ParsedPacket decodeIpv4Packet(byte[] data, int offset, int originalLength, Instant timestamp) {
        if (data.length < offset + 20) {
            return null;
        }

        int ihl = (data[offset] & 0x0F) * 4;
        if (ihl < 20 || data.length < offset + ihl) {
            return null;
        }

        String srcIp = ipv4ToString(data, offset + 12);
        String dstIp = ipv4ToString(data, offset + 16);
        int protocolNumber = unsignedByte(data[offset + 9]);
        int packetLength = unsignedShort(data, offset + 2);
        long bytes = packetLength > 0 ? packetLength : Math.max(originalLength, data.length - offset);

        TransportInfo transportInfo = decodeTransport(protocolNumber, data, offset + ihl, data.length - (offset + ihl));
        return new ParsedPacket(
                srcIp,
                dstIp,
                transportInfo.srcPort,
                transportInfo.dstPort,
                protocolName(protocolNumber),
                resolveAppProtocol(protocolNumber, transportInfo.srcPort, transportInfo.dstPort),
                resolveRegion(srcIp, dstIp),
                resolveDirection(srcIp, dstIp),
                bytes,
                timestamp
        );
    }

    private ParsedPacket decodeIpv6Packet(byte[] data, int offset, int originalLength, Instant timestamp) {
        if (data.length < offset + 40) {
            return null;
        }

        int payloadLength = unsignedShort(data, offset + 4);
        int nextHeader = unsignedByte(data[offset + 6]);
        String srcIp = ipv6ToString(data, offset + 8);
        String dstIp = ipv6ToString(data, offset + 24);
        long bytes = payloadLength > 0 ? payloadLength + 40L : Math.max(originalLength, data.length - offset);

        int transportOffset = offset + 40;
        int currentHeader = nextHeader;
        while (isIpv6ExtensionHeader(currentHeader) && transportOffset + 8 <= data.length) {
            if (currentHeader == 44) {
                currentHeader = unsignedByte(data[transportOffset]);
                transportOffset += 8;
                continue;
            }
            int extensionLengthUnits = unsignedByte(data[transportOffset + 1]);
            currentHeader = unsignedByte(data[transportOffset]);
            transportOffset += (extensionLengthUnits + 1) * 8;
        }

        TransportInfo transportInfo = decodeTransport(currentHeader, data, transportOffset, data.length - transportOffset);
        return new ParsedPacket(
                srcIp,
                dstIp,
                transportInfo.srcPort,
                transportInfo.dstPort,
                protocolName(currentHeader),
                resolveAppProtocol(currentHeader, transportInfo.srcPort, transportInfo.dstPort),
                resolveRegion(srcIp, dstIp),
                resolveDirection(srcIp, dstIp),
                bytes,
                timestamp
        );
    }

    private boolean isIpv6ExtensionHeader(int nextHeader) {
        return nextHeader == 0 || nextHeader == 43 || nextHeader == 44
                || nextHeader == 50 || nextHeader == 51 || nextHeader == 60;
    }

    private TransportInfo decodeTransport(int protocolNumber, byte[] data, int offset, int availableLength) {
        if ((protocolNumber == 6 || protocolNumber == 17) && offset >= 0 && availableLength >= 4 && data.length >= offset + 4) {
            return new TransportInfo(unsignedShort(data, offset), unsignedShort(data, offset + 2));
        }

        if ((protocolNumber == 1 || protocolNumber == 58) && offset >= 0 && availableLength >= 2 && data.length >= offset + 2) {
            return new TransportInfo(unsignedByte(data[offset]), unsignedByte(data[offset + 1]));
        }

        return new TransportInfo(0, 0);
    }

    private String resolveAppProtocol(int protocolNumber, int srcPort, int dstPort) {
        if (protocolNumber == 6 || protocolNumber == 17) {
            return appIdentificationService.identifyProtocol(srcPort, dstPort);
        }
        return protocolName(protocolNumber);
    }

    private String resolveDirection(String srcIp, String dstIp) {
        boolean srcPrivate = geoLocationService.isPrivateIp(srcIp);
        boolean dstPrivate = geoLocationService.isPrivateIp(dstIp);
        if (srcPrivate && dstPrivate) {
            return "east-west";
        }
        if (srcPrivate) {
            return "outbound";
        }
        if (dstPrivate) {
            return "inbound";
        }
        return "external";
    }

    private String resolveRegion(String srcIp, String dstIp) {
        if (geoLocationService.isPrivateIp(srcIp)) {
            return privateRegion(srcIp);
        }
        if (geoLocationService.isPrivateIp(dstIp)) {
            return privateRegion(dstIp);
        }
        return "internet";
    }

    private String privateRegion(String ip) {
        if (ip == null || ip.isBlank()) {
            return "private-network";
        }

        if (ip.startsWith("10.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                return "private-10-" + parts[1];
            }
            return "private-10";
        }

        if (ip.startsWith("192.168.")) {
            return "private-192-168";
        }

        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                return "private-172-" + parts[1];
            }
            return "private-172";
        }

        return "private-network";
    }

    private String protocolName(int protocolNumber) {
        return switch (protocolNumber) {
            case 1 -> "ICMP";
            case 6 -> "TCP";
            case 17 -> "UDP";
            case 47 -> "GRE";
            case 50 -> "ESP";
            case 58 -> "ICMPv6";
            default -> "IP-" + protocolNumber;
        };
    }

    private int unsignedByte(byte value) {
        return value & 0xFF;
    }

    private int unsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private String ipv4ToString(byte[] data, int offset) {
        return (data[offset] & 0xFF) + "."
                + (data[offset + 1] & 0xFF) + "."
                + (data[offset + 2] & 0xFF) + "."
                + (data[offset + 3] & 0xFF);
    }

    private String ipv6ToString(byte[] data, int offset) {
        try {
            byte[] address = new byte[16];
            System.arraycopy(data, offset, address, 0, 16);
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException ex) {
            return "::";
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Integer normalizeMaxPackets(Integer maxPackets) {
        if (maxPackets == null || maxPackets <= 0) {
            return null;
        }
        return maxPackets;
    }

    private int normalizeIdleTimeout(Integer inactivitySeconds) {
        if (inactivitySeconds == null || inactivitySeconds <= 0) {
            return (int) DEFAULT_IDLE_TIMEOUT_SECONDS;
        }
        return inactivitySeconds;
    }

    private Path normalizeImportRoot(String rawImportRoot) {
        if (rawImportRoot == null || rawImportRoot.isBlank()) {
            return null;
        }
        return Paths.get(rawImportRoot.trim()).toAbsolutePath().normalize();
    }

    private Path resolveImportPath(Path requestedPath) throws IOException {
        Path normalizedRequested = requestedPath.normalize();
        if (importRootPath == null) {
            return normalizedRequested.toAbsolutePath().normalize();
        }

        Path resolvedPath = normalizedRequested.isAbsolute()
                ? normalizedRequested.toAbsolutePath().normalize()
                : importRootPath.resolve(normalizedRequested).normalize();

        if (!resolvedPath.startsWith(importRootPath)) {
            throw new IOException("Requested PCAP path is outside configured import root: " + resolvedPath);
        }

        return resolvedPath;
    }

    private static final class TransportInfo {
        private final int srcPort;
        private final int dstPort;

        private TransportInfo(int srcPort, int dstPort) {
            this.srcPort = srcPort;
            this.dstPort = dstPort;
        }
    }

    private static final class ImportAccumulator {
        private long packetsRead;
        private long packetsImported;
        private long packetsSkipped;
        private long flowsImported;
    }

    private static final class MutableFlowAggregate {
        private final String srcIp;
        private final String dstIp;
        private final int srcPort;
        private final int dstPort;
        private final String protocol;
        private final String appProtocol;
        private final String region;
        private final String direction;
        private final Instant firstSeen;
        private Instant lastSeen;
        private long bytesSent;
        private long packetsSent;

        private MutableFlowAggregate(ParsedPacket packet) {
            this.srcIp = packet.srcIp();
            this.dstIp = packet.dstIp();
            this.srcPort = packet.srcPort();
            this.dstPort = packet.dstPort();
            this.protocol = packet.protocol();
            this.appProtocol = packet.appProtocol();
            this.region = packet.region();
            this.direction = packet.direction();
            this.firstSeen = packet.timestamp();
            this.lastSeen = packet.timestamp();
            this.bytesSent = packet.bytes();
            this.packetsSent = 1L;
        }

        private void accept(ParsedPacket packet) {
            this.lastSeen = packet.timestamp();
            this.bytesSent += packet.bytes();
            this.packetsSent++;
        }

        private NetworkFlow toEntity() {
            NetworkFlow flow = new NetworkFlow();
            flow.setSrcIp(srcIp);
            flow.setDstIp(dstIp);
            flow.setSrcPort(srcPort);
            flow.setDstPort(dstPort);
            flow.setProtocol(protocol);
            flow.setBytesSent(bytesSent);
            flow.setBytesRecv(0L);
            flow.setPacketsSent(packetsSent);
            flow.setPacketsRecv(0L);
            flow.setAppProtocol(appProtocol);
            flow.setStartTime(LocalDateTime.ofInstant(firstSeen, ZoneId.systemDefault()));
            flow.setEndTime(LocalDateTime.ofInstant(lastSeen, ZoneId.systemDefault()));
            flow.setTimestamp(LocalDateTime.ofInstant(firstSeen, ZoneId.systemDefault()));
            flow.setRegion(region);
            flow.setDirection(direction);
            return flow;
        }
    }

    private static final class PcapPacket {
        private final Instant timestamp;
        private final int originalLength;
        private final byte[] data;

        private PcapPacket(Instant timestamp, int originalLength, byte[] data) {
            this.timestamp = timestamp;
            this.originalLength = originalLength;
            this.data = data;
        }

        private Instant timestamp() {
            return timestamp;
        }

        private int originalLength() {
            return originalLength;
        }

        private byte[] data() {
            return data;
        }
    }

    private static final class PcapReader {
        private final InputStream inputStream;
        private final boolean littleEndian;
        private final boolean nanoResolution;
        private final int linkType;

        private PcapReader(InputStream inputStream) throws IOException {
            this.inputStream = inputStream;
            byte[] header = inputStream.readNBytes(24);
            if (header.length < 24) {
                throw new EOFException("PCAP header is incomplete.");
            }

            if (matchesMagic(header, 0xD4, 0xC3, 0xB2, 0xA1)) {
                this.littleEndian = true;
                this.nanoResolution = false;
            } else if (matchesMagic(header, 0x4D, 0x3C, 0xB2, 0xA1)) {
                this.littleEndian = true;
                this.nanoResolution = true;
            } else if (matchesMagic(header, 0xA1, 0xB2, 0xC3, 0xD4)) {
                this.littleEndian = false;
                this.nanoResolution = false;
            } else if (matchesMagic(header, 0xA1, 0xB2, 0x3C, 0x4D)) {
                this.littleEndian = false;
                this.nanoResolution = true;
            } else {
                throw new IOException(
                        "Unsupported PCAP magic: "
                                + String.format("0x%02X%02X%02X%02X", header[0], header[1], header[2], header[3])
                );
            }

            this.linkType = readInt(header, 20, littleEndian);
        }

        private int linkType() {
            return linkType;
        }

        private PcapPacket nextPacket() throws IOException {
            byte[] packetHeader = inputStream.readNBytes(16);
            if (packetHeader.length == 0) {
                return null;
            }
            if (packetHeader.length < 16) {
                throw new EOFException("PCAP packet header is incomplete.");
            }

            long seconds = Integer.toUnsignedLong(readInt(packetHeader, 0, littleEndian));
            long fraction = Integer.toUnsignedLong(readInt(packetHeader, 4, littleEndian));
            int capturedLength = readInt(packetHeader, 8, littleEndian);
            int originalLength = readInt(packetHeader, 12, littleEndian);
            if (capturedLength < 0) {
                throw new IOException("Negative captured length in PCAP.");
            }

            byte[] data = inputStream.readNBytes(capturedLength);
            if (data.length < capturedLength) {
                throw new EOFException("PCAP packet body is incomplete.");
            }

            long nanos = nanoResolution ? fraction : fraction * 1_000L;
            Instant timestamp = Instant.ofEpochSecond(seconds, nanos);
            return new PcapPacket(timestamp, originalLength, data);
        }

        private static int readInt(byte[] buffer, int offset, boolean littleEndian) {
            if (littleEndian) {
                return (buffer[offset] & 0xFF)
                        | ((buffer[offset + 1] & 0xFF) << 8)
                        | ((buffer[offset + 2] & 0xFF) << 16)
                        | ((buffer[offset + 3] & 0xFF) << 24);
            }

            return ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
        }

        private static boolean matchesMagic(byte[] header, int b0, int b1, int b2, int b3) {
            return (header[0] & 0xFF) == b0
                    && (header[1] & 0xFF) == b1
                    && (header[2] & 0xFF) == b2
                    && (header[3] & 0xFF) == b3;
        }
    }
}
