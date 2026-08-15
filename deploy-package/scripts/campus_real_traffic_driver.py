#!/usr/bin/env python3
"""
Linux online traffic driver for the deployed campus network monitor.

Main mode: send conservative, realistic HTTP/API/WebSocket-like access traffic
from the traffic source server (60.205.56.61) to the deployed frontend/backend:

  - Frontend dashboard: http://8.146.228.64:3000
  - Backend API:        http://8.146.228.64:8080

Optional mode: print or run a low-speed tcpreplay/tcprewrite command for
/opt/network-monitor/sample.pcap. PCAP replay is kept separate because it sends
raw packets to a network interface, while the deployed system is primarily a Web
application exposing HTTP/WebSocket/API entry points.
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import math
import os
import random
import shutil
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any


DEFAULT_HOST = "8.146.228.64"
DEFAULT_FRONTEND_PORT = 3000
DEFAULT_BACKEND_PORT = 8080
DEFAULT_SCHEME = "http"
DEFAULT_PCAP = Path("/opt/network-monitor/sample.pcap")
SCENARIOS = ("normal_week", "exam_week", "course_selection_week")
MODES = ("http", "pcap")
SAFE_MAX_QPS_WITHOUT_OVERRIDE = 10.0
SAFE_MAX_PCAP_MBPS_WITHOUT_OVERRIDE = 20.0


@dataclass(frozen=True)
class Endpoint:
    name: str
    target: str
    method: str
    path: str
    weight: float
    params: tuple[tuple[str, str], ...] = ()
    websocket_probe: bool = False


@dataclass
class MinuteStats:
    minute_start: datetime
    planned: int = 0
    success: int = 0
    failed: int = 0
    latency_ms_total: float = 0.0
    multiplier_total: float = 0.0
    multiplier_samples: int = 0

    def avg_latency_ms(self) -> float:
        completed = self.success + self.failed
        return self.latency_ms_total / completed if completed else 0.0

    def avg_multiplier(self) -> float:
        return self.multiplier_total / self.multiplier_samples if self.multiplier_samples else 0.0


ENDPOINTS: list[Endpoint] = [
    Endpoint("frontend_home", "frontend", "GET", "/", 0.16),
    Endpoint("dashboard_health", "backend", "GET", "/api/dashboard/health", 0.11),
    Endpoint("dashboard_metrics", "backend", "GET", "/api/dashboard/metrics", 0.13, (("minutesAgo", "-5"),)),
    Endpoint("dashboard_top_flows", "backend", "GET", "/api/dashboard/top-flows", 0.09, (("limit", "10"), ("minutesAgo", "-5"), ("metric", "bytes"))),
    Endpoint("dashboard_region_traffic", "backend", "GET", "/api/dashboard/region-traffic", 0.06, (("minutesAgo", "-30"),)),
    Endpoint("dashboard_trend", "backend", "GET", "/api/dashboard/throughput-trend", 0.10, (("minutesAgo", "-60"), ("bucketMinutes", "5"))),
    Endpoint("supported_protocols", "backend", "GET", "/api/dashboard/supported-protocols", 0.04),
    Endpoint("threat_statistics", "backend", "GET", "/api/dashboard/threat-statistics", 0.08, (("minutesAgo", "-60"),)),
    Endpoint("security_alerts_page", "backend", "GET", "/api/security/alerts", 0.10, (("minutesAgo", "-60"), ("page", "0"), ("size", "10"))),
    Endpoint("critical_alerts", "backend", "GET", "/api/security/critical-alerts", 0.04, (("minutesAgo", "-60"),)),
    Endpoint("alert_statistics", "backend", "GET", "/api/security/alert-statistics", 0.05, (("minutesAgo", "-60"),)),
    Endpoint("geo_distribution", "backend", "GET", "/api/security/geo-distribution", 0.05, (("minutesAgo", "-60"),)),
    Endpoint("flow_search_small", "backend", "POST", "/api/flows/search", 0.05, (("minutesAgo", "-30"), ("page", "0"), ("size", "10"))),
    Endpoint("dashboard_ws_probe", "backend", "GET", "/ws/dashboard/metrics", 0.06, websocket_probe=True),
]


shutdown_event = threading.Event()


def parse_duration(raw_value: str) -> int:
    value = raw_value.strip().lower()
    if not value:
        raise argparse.ArgumentTypeError("duration cannot be empty")
    try:
        if value.endswith("ms"):
            seconds = float(value[:-2]) / 1000.0
        elif value.endswith("s"):
            seconds = float(value[:-1])
        elif value.endswith("m"):
            seconds = float(value[:-1]) * 60.0
        elif value.endswith("h"):
            seconds = float(value[:-1]) * 3600.0
        else:
            seconds = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("duration must be a number with optional s/m/h suffix") from exc
    if seconds <= 0:
        raise argparse.ArgumentTypeError("duration must be > 0")
    return max(1, int(math.ceil(seconds)))


def parse_start_time(raw_value: str | None) -> datetime:
    if not raw_value:
        return datetime.now().replace(microsecond=0)
    try:
        return datetime.fromisoformat(raw_value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("start time must use ISO format, e.g. 2026-05-20T08:00:00") from exc


def default_log_path(prefix: str, suffix: str) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return Path(__file__).resolve().parent / "logs" / f"{prefix}_{timestamp}{suffix}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Conservatively send realistic HTTP/API/WebSocket traffic to the deployed network monitor."
    )
    parser.add_argument("--mode", choices=MODES, default="http", help="http = Web/API online traffic; pcap = optional raw PCAP replay helper.")
    parser.add_argument("--host", default=DEFAULT_HOST, help="Target deployment host/IP. Defaults to 8.146.228.64.")
    parser.add_argument("--frontend-port", type=int, default=DEFAULT_FRONTEND_PORT, help="Frontend dashboard port. Defaults to 3000.")
    parser.add_argument("--backend-port", type=int, default=DEFAULT_BACKEND_PORT, help="Backend API port. Defaults to 8080.")
    parser.add_argument("--scheme", choices=("http", "https"), default=DEFAULT_SCHEME, help="HTTP protocol for frontend/backend.")
    parser.add_argument("--scenario", choices=SCENARIOS, default="normal_week", help="Traffic fluctuation scenario.")
    parser.add_argument("--duration", type=parse_duration, default=parse_duration("10m"), help="Run duration, e.g. 30s, 10m, 2h.")
    parser.add_argument("--start-time", type=parse_start_time, default=None, help="Virtual scenario start time, ISO format.")
    parser.add_argument("--base-qps", type=float, default=0.3, help="Baseline requests per second before scenario multipliers.")
    parser.add_argument("--max-qps", type=float, default=1.5, help="Hard request-per-second cap after multipliers.")
    parser.add_argument("--seed", type=int, default=20260520, help="Random seed for reproducible fluctuation and endpoint choices.")
    parser.add_argument("--dry-run", action="store_true", help="Plan and log requests/commands without sending traffic.")
    parser.add_argument("--no-sleep", action="store_true", help="Do not wait between seconds; intended for HTTP dry-run validation only.")
    parser.add_argument("--log-path", type=Path, default=None, help="Text/JSONL request log path.")
    parser.add_argument("--summary-csv", type=Path, default=None, help="Per-minute summary CSV path.")
    parser.add_argument("--timeout", type=float, default=5.0, help="HTTP timeout seconds per request.")
    parser.add_argument("--max-concurrency", type=int, default=4, help="Maximum concurrent HTTP requests.")
    parser.add_argument("--stop-failure-rate", type=float, default=0.60, help="Stop when completed requests fail above this rate after warm-up.")
    parser.add_argument("--warmup-requests", type=int, default=20, help="Minimum completed requests before failure-rate breaker can stop the run.")
    parser.add_argument("--unsafe-allow-high-qps", action="store_true", help="Allow --max-qps above the built-in safety limit.")
    parser.add_argument("--pcap", type=Path, default=DEFAULT_PCAP, help="PCAP path for optional replay mode. Defaults to /opt/network-monitor/sample.pcap.")
    parser.add_argument("--interface", help="Network interface for optional PCAP replay, e.g. eth0.")
    parser.add_argument("--target-ip", default=DEFAULT_HOST, help="Destination IP used by optional tcprewrite/tcpreplay mode.")
    parser.add_argument("--rate-mbps", type=float, default=1.0, help="PCAP replay Mbps cap. Defaults to conservative 1 Mbps.")
    parser.add_argument("--multiplier", type=float, default=1.0, help="PCAP replay speed multiplier when --rate-mbps is not used by a custom command plan.")
    parser.add_argument("--unsafe-run-pcap", action="store_true", help="Actually run tcpreplay commands. Without this, PCAP mode prints dry-run commands.")
    parser.add_argument("--unsafe-allow-high-pcap-rate", action="store_true", help="Allow PCAP --rate-mbps above built-in safety limit.")
    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> None:
    for name in ("frontend_port", "backend_port"):
        value = getattr(args, name)
        if value < 1 or value > 65535:
            raise SystemExit(f"--{name.replace('_', '-')} must be between 1 and 65535")
    if args.base_qps <= 0:
        raise SystemExit("--base-qps must be > 0")
    if args.max_qps <= 0:
        raise SystemExit("--max-qps must be > 0")
    if args.max_qps < args.base_qps:
        raise SystemExit("--max-qps must be >= --base-qps")
    if args.max_qps > SAFE_MAX_QPS_WITHOUT_OVERRIDE and not args.unsafe_allow_high_qps:
        raise SystemExit(
            f"Refuse to run with --max-qps {args.max_qps}; use --unsafe-allow-high-qps only after confirming capacity."
        )
    if args.timeout <= 0:
        raise SystemExit("--timeout must be > 0")
    if args.max_concurrency < 1 or args.max_concurrency > 64:
        raise SystemExit("--max-concurrency must be between 1 and 64")
    if not 0 <= args.stop_failure_rate <= 1:
        raise SystemExit("--stop-failure-rate must be between 0 and 1")
    if args.no_sleep and (not args.dry_run or args.mode != "http"):
        raise SystemExit("--no-sleep is only allowed with --mode http --dry-run")
    if args.rate_mbps <= 0:
        raise SystemExit("--rate-mbps must be > 0")
    effective_pcap_rate = args.rate_mbps * args.multiplier
    if effective_pcap_rate > SAFE_MAX_PCAP_MBPS_WITHOUT_OVERRIDE and not args.unsafe_allow_high_pcap_rate:
        raise SystemExit(
            f"Refuse to run PCAP replay at {effective_pcap_rate} Mbps; use --unsafe-allow-high-pcap-rate only after confirming capacity."
        )
    if args.multiplier <= 0:
        raise SystemExit("--multiplier must be > 0")


def http_root(args: argparse.Namespace, target: str) -> str:
    port = args.frontend_port if target == "frontend" else args.backend_port
    default_port = 443 if args.scheme == "https" else 80
    if port == default_port:
        return f"{args.scheme}://{args.host}"
    return f"{args.scheme}://{args.host}:{port}"


def signal_handler(signum: int, _frame: Any) -> None:
    print(f"Received signal {signum}; stopping after in-flight work...", file=sys.stderr)
    shutdown_event.set()


def daily_profile(virtual_time: datetime) -> float:
    hour = virtual_time.hour + virtual_time.minute / 60.0 + virtual_time.second / 3600.0
    anchors = [
        (0.0, 0.15),
        (5.5, 0.13),
        (7.0, 0.45),
        (8.5, 1.02),
        (10.5, 1.15),
        (12.2, 0.76),
        (13.2, 0.96),
        (15.5, 1.10),
        (17.8, 0.72),
        (19.5, 0.92),
        (22.3, 0.50),
        (24.0, 0.15),
    ]
    for (left_hour, left_value), (right_hour, right_value) in zip(anchors, anchors[1:]):
        if left_hour <= hour <= right_hour:
            ratio = (hour - left_hour) / (right_hour - left_hour)
            return left_value + (right_value - left_value) * ratio
    return 0.15


def scenario_multiplier(scenario: str, virtual_time: datetime, rng: random.Random) -> float:
    value = daily_profile(virtual_time)
    if virtual_time.weekday() >= 5:
        value *= 0.72

    if scenario == "exam_week":
        value *= 1.08
        if 7 <= virtual_time.hour < 9:
            value *= 1.12
        elif 14 <= virtual_time.hour < 16:
            value *= 1.08
        elif 20 <= virtual_time.hour < 23:
            value *= 1.18
        elif 0 <= virtual_time.hour < 2:
            value *= 1.12
    elif scenario == "course_selection_week":
        value *= 1.03
        if virtual_time.weekday() in (0, 1, 2) and virtual_time.hour in (9, 14) and virtual_time.minute < 12:
            value *= 1.35 + 0.18 * math.exp(-((virtual_time.minute - 5) ** 2) / 24.0)
        elif 8 <= virtual_time.hour < 11 or 13 <= virtual_time.hour < 16:
            value *= 1.08

    if 11 <= virtual_time.hour < 13:
        value *= rng.uniform(0.92, 1.10)
    if 7 <= virtual_time.hour < 9 or 17 <= virtual_time.hour < 20:
        value *= rng.uniform(0.94, 1.08)
    value *= rng.lognormvariate(-0.5 * 0.08 * 0.08, 0.08)
    if rng.random() < 0.0015:
        value *= rng.uniform(1.18, 1.45)
    return max(0.05, value)


def poisson(lam: float, rng: random.Random) -> int:
    if lam <= 0:
        return 0
    if lam < 40:
        limit = math.exp(-lam)
        count = 0
        product = 1.0
        while product > limit:
            count += 1
            product *= rng.random()
        return count - 1
    return max(0, int(round(rng.gauss(lam, math.sqrt(lam)))))


def weighted_endpoint(rng: random.Random) -> Endpoint:
    total = sum(endpoint.weight for endpoint in ENDPOINTS)
    pick = rng.random() * total
    cursor = 0.0
    for endpoint in ENDPOINTS:
        cursor += endpoint.weight
        if pick <= cursor:
            return endpoint
    return ENDPOINTS[-1]


def jittered_endpoint(endpoint: Endpoint, rng: random.Random) -> Endpoint:
    params = dict(endpoint.params)
    if endpoint.name in {"dashboard_metrics", "dashboard_top_flows"}:
        params["minutesAgo"] = rng.choice(["-5", "-10", "-15"])
    elif endpoint.name in {"dashboard_region_traffic", "dashboard_trend", "threat_statistics", "security_alerts_page"}:
        params["minutesAgo"] = rng.choice(["-30", "-60", "-120"])
    if endpoint.name == "dashboard_top_flows":
        params["metric"] = rng.choice(["bytes", "packets", "flows"])
        params["limit"] = rng.choice(["5", "10", "15"])
    if endpoint.name == "flow_search_small":
        params["size"] = rng.choice(["5", "10", "20"])
        if rng.random() < 0.35:
            params["appProtocol"] = rng.choice(["HTTP", "HTTPS", "DNS", "SSH"])
    return Endpoint(endpoint.name, endpoint.target, endpoint.method, endpoint.path, endpoint.weight, tuple(sorted(params.items())), endpoint.websocket_probe)


def build_http_url(args: argparse.Namespace, endpoint: Endpoint) -> str:
    root = http_root(args, endpoint.target)
    query = urllib.parse.urlencode(endpoint.params)
    return f"{root}{endpoint.path}{'?' + query if query else ''}"


def build_ws_url(args: argparse.Namespace, endpoint: Endpoint) -> str:
    scheme = "wss" if args.scheme == "https" else "ws"
    port = args.backend_port
    default_port = 443 if scheme == "wss" else 80
    root = f"{scheme}://{args.host}" if port == default_port else f"{scheme}://{args.host}:{port}"
    return f"{root}{endpoint.path}"


def open_log(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    return path.open("a", encoding="utf-8", buffering=1)


def websocket_probe(url: str, timeout: float) -> tuple[bool, int | str, str]:
    parsed = urllib.parse.urlparse(url)
    host = parsed.hostname
    port = parsed.port or (443 if parsed.scheme == "wss" else 80)
    if parsed.scheme == "wss":
        return False, "UNSUPPORTED", "wss probe is not implemented with stdlib; use HTTP polling or install websocket-client"
    path = parsed.path or "/"
    if parsed.query:
        path += "?" + parsed.query
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    expected_accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode("ascii")
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {parsed.netloc}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "User-Agent: campus-realistic-traffic/1.0\r\n"
        "\r\n"
    ).encode("ascii")
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall(request)
        response = sock.recv(4096).decode("iso-8859-1", errors="replace")
    first_line = response.split("\r\n", 1)[0]
    ok = first_line.startswith("HTTP/1.1 101") and expected_accept in response
    return ok, 101 if ok else first_line[:80], ""


def request_once(
    args: argparse.Namespace,
    endpoint: Endpoint,
    dry_run: bool,
    log_handle,
    log_lock: threading.Lock,
) -> tuple[bool, float, int | str]:
    url = build_ws_url(args, endpoint) if endpoint.websocket_probe else build_http_url(args, endpoint)
    started = time.perf_counter()
    status: int | str = "DRY_RUN"
    ok = True
    error = ""
    if not dry_run:
        if endpoint.websocket_probe:
            try:
                ok, status, error = websocket_probe(url, args.timeout)
            except Exception as exc:  # noqa: BLE001
                ok = False
                status = type(exc).__name__
                error = str(exc)
        else:
            data = b"" if endpoint.method == "POST" else None
            request = urllib.request.Request(
                url,
                data=data,
                method=endpoint.method,
                headers={
                    "User-Agent": "campus-realistic-traffic/1.0",
                    "Accept": "application/json,text/html;q=0.8,*/*;q=0.5",
                    "Connection": "close",
                },
            )
            try:
                with urllib.request.urlopen(request, timeout=args.timeout) as response:
                    status = response.getcode()
                    response.read(4096)
                    ok = 200 <= int(status) < 400
            except urllib.error.HTTPError as exc:
                status = exc.code
                ok = 200 <= exc.code < 400
                error = str(exc)
            except Exception as exc:  # noqa: BLE001
                ok = False
                status = type(exc).__name__
                error = str(exc)
    elapsed_ms = (time.perf_counter() - started) * 1000.0
    event = {
        "time": datetime.now().isoformat(timespec="milliseconds"),
        "endpoint": endpoint.name,
        "target": endpoint.target,
        "method": endpoint.method,
        "url": url,
        "status": status,
        "ok": ok,
        "latencyMs": round(elapsed_ms, 2),
    }
    if error:
        event["error"] = error[:300]
    with log_lock:
        log_handle.write(json.dumps(event, ensure_ascii=False) + "\n")
    return ok, elapsed_ms, status


def update_stats(
    stats: dict[str, MinuteStats],
    key: str,
    minute_start: datetime,
    planned_delta: int = 0,
    success_delta: int = 0,
    failed_delta: int = 0,
    latency_ms: float = 0.0,
    multiplier: float | None = None,
) -> None:
    item = stats.setdefault(key, MinuteStats(minute_start=minute_start))
    item.planned += planned_delta
    item.success += success_delta
    item.failed += failed_delta
    item.latency_ms_total += latency_ms
    if multiplier is not None:
        item.multiplier_total += multiplier
        item.multiplier_samples += 1


def write_summary(path: Path, stats: dict[str, MinuteStats], scenario: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output)
        writer.writerow(["minute", "scenario", "avg_multiplier", "planned_requests", "success", "failed", "avg_latency_ms"])
        for key in sorted(stats):
            item = stats[key]
            writer.writerow([
                item.minute_start.isoformat(timespec="seconds"),
                scenario,
                f"{item.avg_multiplier():.4f}",
                item.planned,
                item.success,
                item.failed,
                f"{item.avg_latency_ms():.2f}",
            ])


def should_stop_for_failures(stats: dict[str, MinuteStats], stop_failure_rate: float, warmup_requests: int) -> bool:
    completed = sum(item.success + item.failed for item in stats.values())
    failed = sum(item.failed for item in stats.values())
    if completed < warmup_requests:
        return False
    return completed > 0 and failed / completed > stop_failure_rate


def run_http_mode(args: argparse.Namespace) -> int:
    rng = random.Random(args.seed)
    virtual_start = args.start_time or datetime.now().replace(microsecond=0)
    log_path = args.log_path or default_log_path("online_traffic", ".jsonl")
    summary_path = args.summary_csv or default_log_path("online_traffic_summary", ".csv")
    stats: dict[str, MinuteStats] = {}
    stats_lock = threading.Lock()
    log_lock = threading.Lock()
    total_scheduled = 0
    total_completed = 0

    print(
        f"mode=http frontend={http_root(args, 'frontend')} backend={http_root(args, 'backend')} "
        f"scenario={args.scenario} duration={args.duration}s base_qps={args.base_qps} max_qps={args.max_qps} dry_run={args.dry_run}"
    )
    print(f"log={log_path.resolve()}")
    print(f"summary={summary_path.resolve()}")

    def record_result(future, minute_key: str, minute_start: datetime) -> None:
        nonlocal total_completed
        ok, latency_ms, _status = future.result()
        with stats_lock:
            update_stats(stats, minute_key, minute_start, success_delta=1 if ok else 0, failed_delta=0 if ok else 1, latency_ms=latency_ms)
            total_completed += 1

    with open_log(log_path) as log_handle, ThreadPoolExecutor(max_workers=args.max_concurrency) as executor:
        futures = []
        started_at = time.monotonic()
        for second_index in range(args.duration):
            if shutdown_event.is_set():
                break
            virtual_time = virtual_start + timedelta(seconds=second_index)
            minute_start = virtual_time.replace(second=0, microsecond=0)
            minute_key = minute_start.isoformat(timespec="minutes")
            multiplier = scenario_multiplier(args.scenario, virtual_time, rng)
            current_qps = min(args.max_qps, args.base_qps * multiplier)
            requests_this_second = poisson(current_qps, rng)
            if current_qps > 0 and requests_this_second == 0 and rng.random() < current_qps:
                requests_this_second = 1
            with stats_lock:
                update_stats(stats, minute_key, minute_start, planned_delta=requests_this_second, multiplier=multiplier)
            for _ in range(requests_this_second):
                endpoint = jittered_endpoint(weighted_endpoint(rng), rng)
                future = executor.submit(request_once, args, endpoint, args.dry_run, log_handle, log_lock)
                future.add_done_callback(lambda f, k=minute_key, m=minute_start: record_result(f, k, m))
                futures.append(future)
                total_scheduled += 1
            if second_index % 15 == 0:
                with stats_lock:
                    write_summary(summary_path, dict(stats), args.scenario)
                    should_stop = should_stop_for_failures(stats, args.stop_failure_rate, args.warmup_requests)
                if should_stop:
                    print("failure-rate breaker triggered; stopping safely", file=sys.stderr)
                    shutdown_event.set()
                    break
            if not args.no_sleep:
                next_tick = started_at + second_index + 1
                time.sleep(max(0.0, next_tick - time.monotonic()))

        for future in futures:
            try:
                future.result()
            except Exception as exc:  # noqa: BLE001
                with log_lock:
                    log_handle.write(json.dumps({"time": datetime.now().isoformat(), "ok": False, "error": str(exc)}) + "\n")
        with stats_lock:
            write_summary(summary_path, dict(stats), args.scenario)

    total_success = sum(item.success for item in stats.values())
    total_failed = sum(item.failed for item in stats.values())
    print(f"scheduled={total_scheduled} completed={total_completed} success={total_success} failed={total_failed}")
    return 0 if total_failed == 0 or args.dry_run else 2


def detect_default_interface() -> str | None:
    route_path = Path("/proc/net/route")
    if not route_path.exists():
        return None
    for line in route_path.read_text(encoding="utf-8", errors="ignore").splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "00000000":
            return fields[0]
    return None


def pcap_dependencies() -> dict[str, str | None]:
    return {"tcpreplay": shutil.which("tcpreplay"), "tcprewrite": shutil.which("tcprewrite")}


def build_pcap_commands(args: argparse.Namespace, interface_name: str) -> tuple[list[str], list[str] | None]:
    rewrite_path = args.pcap.with_suffix(".to-target.pcap")
    rewrite_cmd = None
    if shutil.which("tcprewrite"):
        rewrite_cmd = [
            "tcprewrite",
            "--infile",
            str(args.pcap),
            "--outfile",
            str(rewrite_path),
            "--dstipmap",
            f"0.0.0.0/0:{args.target_ip}",
            "--fixcsum",
        ]
        replay_input = rewrite_path
    else:
        replay_input = args.pcap

    effective_rate_mbps = args.rate_mbps * args.multiplier
    replay_cmd = ["tcpreplay", "--intf1", interface_name, "--mbps", f"{effective_rate_mbps:.3f}", str(replay_input)]
    return replay_cmd, rewrite_cmd


def run_pcap_mode(args: argparse.Namespace) -> int:
    interface_name = args.interface or detect_default_interface()
    deps = pcap_dependencies()
    print("mode=pcap")
    print(f"pcap={args.pcap}")
    print(f"target_ip={args.target_ip}")
    print(f"interface={interface_name or 'NOT_DETECTED'}")
    print(f"rate_mbps={args.rate_mbps} multiplier={args.multiplier} effective_rate_mbps={args.rate_mbps * args.multiplier:.3f}")
    print(f"tcpreplay={deps['tcpreplay'] or 'MISSING'} tcprewrite={deps['tcprewrite'] or 'MISSING'}")
    print("PCAP replay sends raw packets and usually requires root privileges or CAP_NET_RAW.")

    if not args.pcap.exists():
        print(f"PCAP file not found: {args.pcap}", file=sys.stderr)
        return 2
    if not interface_name:
        print("No interface detected; pass --interface eth0 explicitly.", file=sys.stderr)
        if args.dry_run or not args.unsafe_run_pcap:
            interface_name = "<interface>"
        else:
            return 2
    if not deps["tcpreplay"]:
        print("tcpreplay is not installed. Install tcpreplay first, e.g. apt/yum install tcpreplay.", file=sys.stderr)
        if not args.dry_run and args.unsafe_run_pcap:
            return 2

    replay_cmd, rewrite_cmd = build_pcap_commands(args, interface_name)
    if rewrite_cmd:
        print("rewrite_cmd=" + " ".join(rewrite_cmd))
    else:
        print("tcprewrite not found; replaying original sample.pcap without target-IP rewrite.")
    print("replay_cmd=" + " ".join(replay_cmd))

    if args.dry_run or not args.unsafe_run_pcap:
        print("dry-run only. Add --unsafe-run-pcap without --dry-run to execute replay.")
        return 0
    if os.geteuid() != 0:
        print("Refuse to run PCAP replay without root. Use sudo after confirming interface/rate.", file=sys.stderr)
        return 2
    if rewrite_cmd:
        subprocess.run(rewrite_cmd, check=True)
    subprocess.run(replay_cmd, check=True)
    return 0


def main() -> None:
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    args = parse_args()
    validate_args(args)
    if args.mode == "pcap":
        raise SystemExit(run_pcap_mode(args))
    raise SystemExit(run_http_mode(args))


if __name__ == "__main__":
    main()
