#!/usr/bin/env python3
"""
Generate realistic campus traffic PCAP files for the network monitor importer.

The backend currently imports PCAP files through /api/admin/import/pcap-path and
/api/admin/import/pcap-upload. This script writes Ethernet + IPv4 TCP/UDP packets
with timestamps and ports that the Java PcapImportService can parse into
NetworkFlow entities.
"""

from __future__ import annotations

import argparse
import csv
import ipaddress
import math
import random
import socket
import struct
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable


SCENARIOS = ("normal_week", "exam_week", "course_selection_week")
DEFAULT_OUTPUT = Path("realistic_campus_traffic.pcap")
ETHERNET_LINKTYPE = 1


@dataclass(frozen=True)
class TrafficProfile:
    name: str
    dst_port: int
    protocol: str
    weight: float
    avg_packet_bytes: int
    jitter: int
    internal_service: bool = False


@dataclass(frozen=True)
class PacketPlan:
    timestamp: datetime
    src_ip: str
    dst_ip: str
    src_port: int
    dst_port: int
    protocol: str
    payload_size: int


@dataclass(frozen=True)
class MinuteSummary:
    minute: datetime
    scenario: str
    multiplier: float
    packets: int
    bytes_total: int
    anomaly: str


BASE_PROFILES = [
    TrafficProfile("HTTPS", 443, "TCP", 0.34, 720, 260),
    TrafficProfile("HTTP", 80, "TCP", 0.12, 620, 220),
    TrafficProfile("DNS", 53, "UDP", 0.10, 140, 50),
    TrafficProfile("HTTP-Alt", 8080, "TCP", 0.14, 680, 240, True),
    TrafficProfile("SSH", 22, "TCP", 0.04, 360, 120, True),
    TrafficProfile("MySQL", 3306, "TCP", 0.03, 520, 180, True),
    TrafficProfile("NTP", 123, "UDP", 0.03, 120, 40),
    TrafficProfile("Campus-App", 8443, "TCP", 0.20, 760, 260, True),
]

EXAM_PROFILE_WEIGHTS = {
    "HTTPS": 0.36,
    "HTTP": 0.08,
    "DNS": 0.11,
    "HTTP-Alt": 0.18,
    "SSH": 0.03,
    "MySQL": 0.04,
    "NTP": 0.02,
    "Campus-App": 0.18,
}

COURSE_SELECTION_PROFILE_WEIGHTS = {
    "HTTPS": 0.33,
    "HTTP": 0.06,
    "DNS": 0.08,
    "HTTP-Alt": 0.22,
    "SSH": 0.02,
    "MySQL": 0.09,
    "NTP": 0.01,
    "Campus-App": 0.19,
}

STUDENT_SUBNETS = [
    ipaddress.ip_network("10.10.0.0/20"),
    ipaddress.ip_network("10.20.0.0/20"),
    ipaddress.ip_network("10.30.0.0/21"),
]
TEACHING_SUBNETS = [ipaddress.ip_network("10.40.0.0/22"), ipaddress.ip_network("10.50.0.0/22")]
SERVER_SUBNETS = [ipaddress.ip_network("10.60.0.0/24"), ipaddress.ip_network("10.70.0.0/24")]
PUBLIC_SUBNETS = [ipaddress.ip_network("101.33.0.0/19"), ipaddress.ip_network("120.48.0.0/16")]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate realistic campus traffic PCAP with teaching/exam/course-selection fluctuation patterns."
    )
    parser.add_argument("--scenario", choices=SCENARIOS, default="normal_week", help="Traffic scenario to simulate.")
    parser.add_argument(
        "--start",
        default=datetime.now().replace(hour=8, minute=0, second=0, microsecond=0).isoformat(timespec="seconds"),
        help="Start time, e.g. 2026-05-20T08:00:00. Defaults to today 08:00 local time.",
    )
    parser.add_argument("--duration-hours", type=float, default=24.0, help="Duration to generate in hours.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Output PCAP path.")
    parser.add_argument("--summary-csv", type=Path, help="Optional per-minute summary CSV path.")
    parser.add_argument("--seed", type=int, default=20260520, help="Random seed for reproducible output.")
    parser.add_argument(
        "--base-qps",
        type=float,
        default=0.35,
        help="Baseline logical packets per second before scenario multipliers. Keep small for lightweight demo PCAPs.",
    )
    parser.add_argument(
        "--base-mbps",
        type=float,
        default=None,
        help="Optional baseline bandwidth in Mbps. When set, packet sizes are adjusted toward this bandwidth.",
    )
    parser.add_argument(
        "--max-packets",
        type=int,
        default=250_000,
        help="Safety cap for generated packets. Set 0 to disable.",
    )
    parser.add_argument(
        "--anomaly-rate",
        type=float,
        default=0.003,
        help="Probability per minute of a short, mild anomaly spike.",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Only print final result line.",
    )
    return parser.parse_args()


def parse_start_time(raw_value: str) -> datetime:
    try:
        return datetime.fromisoformat(raw_value)
    except ValueError as exc:
        raise SystemExit(f"Invalid --start value {raw_value!r}; use ISO format like 2026-05-20T08:00:00") from exc


def validate_args(args: argparse.Namespace) -> None:
    if args.duration_hours <= 0:
        raise SystemExit("--duration-hours must be > 0")
    if args.base_qps <= 0:
        raise SystemExit("--base-qps must be > 0")
    if args.base_mbps is not None and args.base_mbps <= 0:
        raise SystemExit("--base-mbps must be > 0 when provided")
    if args.max_packets < 0:
        raise SystemExit("--max-packets must be >= 0")
    if not 0 <= args.anomaly_rate <= 0.05:
        raise SystemExit("--anomaly-rate must be between 0 and 0.05")


def build_profiles(scenario: str) -> list[TrafficProfile]:
    if scenario == "exam_week":
        weights = EXAM_PROFILE_WEIGHTS
    elif scenario == "course_selection_week":
        weights = COURSE_SELECTION_PROFILE_WEIGHTS
    else:
        weights = {profile.name: profile.weight for profile in BASE_PROFILES}

    return [
        TrafficProfile(
            profile.name,
            profile.dst_port,
            profile.protocol,
            weights.get(profile.name, profile.weight),
            profile.avg_packet_bytes,
            profile.jitter,
            profile.internal_service,
        )
        for profile in BASE_PROFILES
    ]


def minute_count(duration_hours: float) -> int:
    return max(1, int(math.ceil(duration_hours * 60)))


def daily_profile(dt: datetime) -> float:
    hour = dt.hour + dt.minute / 60.0
    anchors = [
        (0.0, 0.18),
        (5.5, 0.16),
        (7.0, 0.48),
        (8.5, 1.04),
        (10.5, 1.18),
        (12.0, 0.78),
        (13.0, 0.96),
        (15.5, 1.12),
        (17.5, 0.74),
        (19.5, 0.92),
        (22.0, 0.56),
        (24.0, 0.18),
    ]
    for (left_hour, left_value), (right_hour, right_value) in zip(anchors, anchors[1:]):
        if left_hour <= hour <= right_hour:
            ratio = (hour - left_hour) / (right_hour - left_hour)
            return left_value + (right_value - left_value) * ratio
    return 0.18


def scenario_multiplier(scenario: str, dt: datetime, rng: random.Random) -> tuple[float, str]:
    value = daily_profile(dt)
    note = ""

    if dt.weekday() >= 5:
        value *= 0.72

    if scenario == "exam_week":
        value *= 1.08
        if 7 <= dt.hour < 9:
            value *= 1.10
            note = "exam_arrival_peak"
        elif 20 <= dt.hour < 23:
            value *= 1.16
            note = "evening_review_peak"
        elif 0 <= dt.hour < 2:
            value *= 1.18
            note = "late_review_tail"
    elif scenario == "course_selection_week":
        value *= 1.03
        if dt.weekday() in (0, 1, 2) and (9 <= dt.hour < 10 or 14 <= dt.hour < 15):
            minutes_from_start = dt.minute
            burst_shape = 1.0 + 0.42 * math.exp(-((minutes_from_start - 10) ** 2) / 260.0)
            value *= burst_shape
            note = "course_selection_burst"
        elif 8 <= dt.hour < 11 or 13 <= dt.hour < 16:
            value *= 1.08

    lunch_wobble = 1.0
    if 11 <= dt.hour < 13:
        lunch_wobble += rng.uniform(-0.10, 0.12)
        note = note or "lunch_wobble"

    morning_evening_wobble = 1.0
    if 7 <= dt.hour < 9 or 17 <= dt.hour < 20:
        morning_evening_wobble += rng.uniform(-0.08, 0.10)
        note = note or "commute_peak_wobble"

    background_noise = rng.lognormvariate(-0.5 * 0.08 * 0.08, 0.08)
    return max(0.05, value * lunch_wobble * morning_evening_wobble * background_noise), note


def anomaly_windows(start: datetime, minutes: int, scenario: str, anomaly_rate: float, rng: random.Random) -> dict[int, tuple[float, str]]:
    windows: dict[int, tuple[float, str]] = {}
    cursor = 0
    while cursor < minutes:
        if rng.random() < anomaly_rate:
            duration = rng.randint(2, 5)
            lift = rng.uniform(1.22, 1.58)
            label = rng.choice(["dns_retry_spike", "short_video_cluster", "lab_download_spike", "scanner_noise"])
            for offset in range(duration):
                if cursor + offset < minutes:
                    windows[cursor + offset] = (lift, label)
            cursor += duration
        cursor += 1

    if scenario == "course_selection_week":
        for index in range(minutes):
            dt = start + timedelta(minutes=index)
            if dt.weekday() in (0, 1, 2) and dt.hour in (9, 14) and 0 <= dt.minute < 12:
                windows[index] = (max(windows.get(index, (1.0, ""))[0], 1.35), "course_selection_login_cluster")
    return windows


def weighted_choice(profiles: list[TrafficProfile], rng: random.Random) -> TrafficProfile:
    total = sum(profile.weight for profile in profiles)
    pick = rng.random() * total
    cursor = 0.0
    for profile in profiles:
        cursor += profile.weight
        if pick <= cursor:
            return profile
    return profiles[-1]


def random_ip(networks: Iterable[ipaddress.IPv4Network], rng: random.Random) -> str:
    network = rng.choice(list(networks))
    first = int(network.network_address) + 1
    last = int(network.broadcast_address) - 1
    return str(ipaddress.ip_address(rng.randint(first, last)))


def choose_hosts(profile: TrafficProfile, rng: random.Random) -> tuple[str, str]:
    internal_pool = STUDENT_SUBNETS + TEACHING_SUBNETS
    src_ip = random_ip(internal_pool, rng)
    if profile.internal_service or rng.random() < 0.35:
        dst_ip = random_ip(SERVER_SUBNETS, rng)
    else:
        dst_ip = random_ip(PUBLIC_SUBNETS, rng)
    return src_ip, dst_ip


def poisson(lam: float, rng: random.Random) -> int:
    if lam <= 0:
        return 0
    if lam < 45:
        limit = math.exp(-lam)
        count = 0
        product = 1.0
        while product > limit:
            count += 1
            product *= rng.random()
        return count - 1
    return max(0, int(round(rng.gauss(lam, math.sqrt(lam)))))


def estimate_packet_size(profile: TrafficProfile, target_size: int | None, rng: random.Random) -> int:
    base = target_size if target_size is not None else profile.avg_packet_bytes
    jitter = max(profile.jitter, int(base * 0.18))
    size = int(rng.gauss(base, jitter / 2.8))
    return max(54, min(1400, size))


def build_minute_packets(
    minute_start: datetime,
    packets_target: int,
    target_packet_size: int | None,
    profiles: list[TrafficProfile],
    rng: random.Random,
) -> list[PacketPlan]:
    packets: list[PacketPlan] = []
    remaining = packets_target
    while remaining > 0:
        profile = weighted_choice(profiles, rng)
        src_ip, dst_ip = choose_hosts(profile, rng)
        src_port = rng.randint(20_000, 60_000)
        dst_port = profile.dst_port
        session_packets = min(remaining, rng.choices([1, 2, 3, 4, 5, 6, 8], [18, 24, 20, 16, 10, 8, 4])[0])
        first_offset = rng.uniform(0, 58.5)
        for seq in range(session_packets):
            timestamp = minute_start + timedelta(seconds=min(59.95, first_offset + seq * rng.uniform(0.03, 1.8)))
            packets.append(
                PacketPlan(
                    timestamp=timestamp,
                    src_ip=src_ip,
                    dst_ip=dst_ip,
                    src_port=src_port,
                    dst_port=dst_port,
                    protocol=profile.protocol,
                    payload_size=estimate_packet_size(profile, target_packet_size, rng),
                )
            )
        remaining -= session_packets
    packets.sort(key=lambda packet: packet.timestamp)
    return packets


def generate_packet_plans(args: argparse.Namespace) -> tuple[list[PacketPlan], list[MinuteSummary]]:
    rng = random.Random(args.seed)
    start = parse_start_time(args.start)
    minutes = minute_count(args.duration_hours)
    profiles = build_profiles(args.scenario)
    anomalies = anomaly_windows(start, minutes, args.scenario, args.anomaly_rate, rng)
    packets: list[PacketPlan] = []
    summaries: list[MinuteSummary] = []
    max_packets = args.max_packets if args.max_packets > 0 else None

    day_factors: dict[datetime.date, float] = {}
    for index in range(minutes):
        minute_start = start + timedelta(minutes=index)
        day_factors.setdefault(minute_start.date(), rng.uniform(0.93, 1.07))
        multiplier, note = scenario_multiplier(args.scenario, minute_start, rng)
        multiplier *= day_factors[minute_start.date()]
        if index in anomalies:
            lift, label = anomalies[index]
            multiplier *= lift
            note = label

        expected_packets = args.base_qps * 60.0 * multiplier
        packets_target = poisson(expected_packets, rng)
        target_packet_size = None
        if args.base_mbps is not None and packets_target > 0:
            target_bytes_per_minute = args.base_mbps * 125_000.0 * 60.0 * multiplier
            target_packet_size = int(target_bytes_per_minute / max(1, packets_target))

        if max_packets is not None and len(packets) + packets_target > max_packets:
            packets_target = max(0, max_packets - len(packets))

        minute_packets = build_minute_packets(minute_start, packets_target, target_packet_size, profiles, rng)
        minute_bytes = sum(packet.payload_size + 54 for packet in minute_packets)
        packets.extend(minute_packets)
        summaries.append(
            MinuteSummary(
                minute=minute_start,
                scenario=args.scenario,
                multiplier=multiplier,
                packets=len(minute_packets),
                bytes_total=minute_bytes,
                anomaly=note,
            )
        )
        if max_packets is not None and len(packets) >= max_packets:
            break
    return packets, summaries


def checksum(data: bytes) -> int:
    if len(data) % 2:
        data += b"\x00"
    total = 0
    for offset in range(0, len(data), 2):
        total += (data[offset] << 8) + data[offset + 1]
        total = (total & 0xFFFF) + (total >> 16)
    return (~total) & 0xFFFF


def ip_bytes(ip: str) -> bytes:
    return socket.inet_aton(ip)


def build_ipv4_header(src_ip: str, dst_ip: str, protocol_number: int, total_length: int, packet_id: int) -> bytes:
    version_ihl = 0x45
    dscp_ecn = 0
    flags_fragment = 0x4000
    ttl = 64
    header = struct.pack(
        "!BBHHHBBH4s4s",
        version_ihl,
        dscp_ecn,
        total_length,
        packet_id & 0xFFFF,
        flags_fragment,
        ttl,
        protocol_number,
        0,
        ip_bytes(src_ip),
        ip_bytes(dst_ip),
    )
    return header[:10] + struct.pack("!H", checksum(header)) + header[12:]


def build_tcp_segment(plan: PacketPlan, payload: bytes, sequence: int) -> bytes:
    data_offset_reserved = 5 << 4
    flags = 0x18
    window = 8192
    urgent = 0
    header = struct.pack(
        "!HHLLBBHHH",
        plan.src_port,
        plan.dst_port,
        sequence & 0xFFFFFFFF,
        0,
        data_offset_reserved,
        flags,
        window,
        0,
        urgent,
    )
    pseudo_header = ip_bytes(plan.src_ip) + ip_bytes(plan.dst_ip) + struct.pack("!BBH", 0, 6, len(header) + len(payload))
    tcp_checksum = checksum(pseudo_header + header + payload)
    return header[:16] + struct.pack("!H", tcp_checksum) + header[18:] + payload


def build_udp_datagram(plan: PacketPlan, payload: bytes) -> bytes:
    length = 8 + len(payload)
    header = struct.pack("!HHHH", plan.src_port, plan.dst_port, length, 0)
    pseudo_header = ip_bytes(plan.src_ip) + ip_bytes(plan.dst_ip) + struct.pack("!BBH", 0, 17, length)
    udp_checksum = checksum(pseudo_header + header + payload)
    if udp_checksum == 0:
        udp_checksum = 0xFFFF
    return struct.pack("!HHHH", plan.src_port, plan.dst_port, length, udp_checksum) + payload


def build_ethernet_frame(plan: PacketPlan, packet_index: int) -> bytes:
    dst_mac = b"\x02\x42\xac\x10\x00\x01"
    src_mac = b"\x02\x42\xac\x10\x00\x02"
    ether_type_ipv4 = b"\x08\x00"
    payload_length = max(1, plan.payload_size)
    payload = bytes(((packet_index + offset) % 251) + 1 for offset in range(payload_length))

    if plan.protocol == "UDP":
        transport = build_udp_datagram(plan, payload)
        protocol_number = 17
    else:
        transport = build_tcp_segment(plan, payload, sequence=packet_index * 97)
        protocol_number = 6

    total_length = 20 + len(transport)
    ip_header = build_ipv4_header(plan.src_ip, plan.dst_ip, protocol_number, total_length, packet_index)
    return dst_mac + src_mac + ether_type_ipv4 + ip_header + transport


def write_pcap(path: Path, packets: list[PacketPlan]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as output:
        output.write(struct.pack("<IHHIIII", 0xA1B2C3D4, 2, 4, 0, 0, 65535, ETHERNET_LINKTYPE))
        for index, plan in enumerate(packets, start=1):
            frame = build_ethernet_frame(plan, index)
            timestamp = plan.timestamp.timestamp()
            seconds = int(timestamp)
            micros = int((timestamp - seconds) * 1_000_000)
            output.write(struct.pack("<IIII", seconds, micros, len(frame), len(frame)))
            output.write(frame)


def write_summary_csv(path: Path, summaries: list[MinuteSummary]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.writer(output)
        writer.writerow(["minute", "scenario", "multiplier", "packets", "bytes_total", "anomaly_note"])
        for summary in summaries:
            writer.writerow(
                [
                    summary.minute.isoformat(timespec="seconds"),
                    summary.scenario,
                    f"{summary.multiplier:.4f}",
                    summary.packets,
                    summary.bytes_total,
                    summary.anomaly,
                ]
            )


def print_report(args: argparse.Namespace, packets: list[PacketPlan], summaries: list[MinuteSummary], summary_path: Path | None) -> None:
    if not packets:
        print("Generated 0 packets. Increase --base-qps or --duration-hours.")
        return
    total_bytes = sum(summary.bytes_total for summary in summaries)
    first_time = packets[0].timestamp.isoformat(timespec="seconds")
    last_time = packets[-1].timestamp.isoformat(timespec="seconds")
    anomalies = sum(1 for summary in summaries if summary.anomaly)
    if not args.quiet:
        print(f"Scenario: {args.scenario}")
        print(f"Time range: {first_time} -> {last_time}")
        print(f"Packets: {len(packets)}")
        print(f"Approx bytes on wire: {total_bytes}")
        print(f"Minutes with named fluctuation/anomaly: {anomalies}")
        print(f"PCAP: {args.output.resolve()}")
        if summary_path:
            print(f"Summary CSV: {summary_path.resolve()}")
    else:
        print(f"pcap={args.output.resolve()} packets={len(packets)} bytes={total_bytes}")


def main() -> None:
    args = parse_args()
    validate_args(args)
    summary_path = args.summary_csv
    if summary_path is None:
        summary_path = args.output.with_suffix(".summary.csv")
    packets, summaries = generate_packet_plans(args)
    write_pcap(args.output, packets)
    write_summary_csv(summary_path, summaries)
    print_report(args, packets, summaries, summary_path)


if __name__ == "__main__":
    main()
