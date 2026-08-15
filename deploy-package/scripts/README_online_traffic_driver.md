# Linux 真实在线打流脚本

`campus_real_traffic_driver.py` 是面向本项目部署拓扑的真实在线打流脚本。它运行在测试流量源 Linux 服务器 `60.205.56.61`，向部署服务器 `8.146.228.64` 的前端大屏和后端 API 发送保守、可控、带真实场景波动的访问流量。

默认目标与截图一致：

- 前端大屏：`http://8.146.228.64:3000`
- 后端 API：`http://8.146.228.64:8080`
- WebSocket 路径：`ws://8.146.228.64:8080/ws/dashboard/metrics`
- Swagger：`http://8.146.228.64:8080/swagger-ui.html`，脚本默认不打 Swagger，避免无意义文档流量

## 两种模式

### 主线：HTTP/API/WebSocket 在线打流

这是默认模式，也是本次主要产物。脚本模拟用户访问前端大屏、前端轮询后端 Dashboard/Security/Flow 只读接口，以及少量 WebSocket 握手连接尝试。

访问池包含：

- `GET http://8.146.228.64:3000/`
- `GET /api/dashboard/health`
- `GET /api/dashboard/metrics`
- `GET /api/dashboard/top-flows`
- `GET /api/dashboard/region-traffic`
- `GET /api/dashboard/throughput-trend`
- `GET /api/dashboard/supported-protocols`
- `GET /api/dashboard/threat-statistics`
- `GET /api/security/alerts`
- `GET /api/security/critical-alerts`
- `GET /api/security/alert-statistics`
- `GET /api/security/geo-distribution`
- 小页 `POST /api/flows/search`
- 少量 `ws://8.146.228.64:8080/ws/dashboard/metrics` WebSocket 握手探测

脚本刻意不调用：

- `/api/admin/import/*`
- `/api/security/run-detection`
- `/api/advanced-analysis/*` 的重分析 POST
- CSV 导出接口
- 高成本大页查询

### 可选：PCAP 回放模式

用户打流端 `/opt/network-monitor` 下已有 `sample.pcap`。脚本提供 `--mode pcap` 辅助生成/执行低速 `tcpreplay`/`tcprewrite` 命令：

- 默认 PCAP：`/opt/network-monitor/sample.pcap`
- 默认目标 IP：`8.146.228.64`
- 默认低速：`--rate-mbps 1.0`

注意：PCAP 回放是原始包级别流量，需要明确目标网卡和 root 权限；如果线上系统主要是 Web 应用，推荐优先用 HTTP/API 在线打流模式。

## 依赖

HTTP/API 模式：

- Linux + Python 3.9+
- 仅使用 Python 标准库，无需 `curl`、`ab`、`wrk`、`hey`

PCAP 模式可选依赖：

- `tcpreplay`
- `tcprewrite`，可选，用于将 PCAP 目的 IP 改写为 `8.146.228.64`

安装示例：

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y python3 tcpreplay

# CentOS/RHEL/Alibaba Cloud Linux，包名可能随源不同而变化
sudo yum install -y python3 tcpreplay
```

## 上传到测试流量源 60.205.56.61

在本机项目目录执行：

```bash
scp network-monitor-backend/scripts/campus_real_traffic_driver.py root@60.205.56.61:/opt/network-monitor/campus_real_traffic_driver.py
ssh root@60.205.56.61
chmod +x /opt/network-monitor/campus_real_traffic_driver.py
mkdir -p /opt/network-monitor/logs
```

如不使用 root 登录，请把 `root@60.205.56.61` 替换为实际用户，并确保用户有写 `/opt/network-monitor/logs` 的权限。

## dry-run 验证

dry-run 不会发真实请求，只会按场景规划请求并生成 JSONL/CSV 日志：

```bash
/opt/network-monitor/campus_real_traffic_driver.py \
  --dry-run \
  --no-sleep \
  --scenario course_selection_week \
  --start-time 2026-05-20T09:00:00 \
  --duration 30s \
  --base-qps 0.5 \
  --max-qps 2.5 \
  --seed 42 \
  --log-path /opt/network-monitor/logs/dry-run.jsonl \
  --summary-csv /opt/network-monitor/logs/dry-run-summary.csv
```

查看摘要：

```bash
head /opt/network-monitor/logs/dry-run-summary.csv
head /opt/network-monitor/logs/dry-run.jsonl
```

## 正式 HTTP/API 在线打流

先用默认保守速率跑 10~30 分钟观察系统状态。

普通教学周：

```bash
/opt/network-monitor/campus_real_traffic_driver.py \
  --host 8.146.228.64 \
  --frontend-port 3000 \
  --backend-port 8080 \
  --scenario normal_week \
  --duration 30m \
  --base-qps 0.3 \
  --max-qps 1.5 \
  --seed 20260520 \
  --log-path /opt/network-monitor/logs/normal-week.jsonl \
  --summary-csv /opt/network-monitor/logs/normal-week-summary.csv
```

考试周，晚间复习波动：

```bash
/opt/network-monitor/campus_real_traffic_driver.py \
  --scenario exam_week \
  --start-time 2026-05-20T20:00:00 \
  --duration 1h \
  --base-qps 0.4 \
  --max-qps 2.0 \
  --log-path /opt/network-monitor/logs/exam-week.jsonl \
  --summary-csv /opt/network-monitor/logs/exam-week-summary.csv
```

选课周，短时尖峰：

```bash
/opt/network-monitor/campus_real_traffic_driver.py \
  --scenario course_selection_week \
  --start-time 2026-05-20T09:00:00 \
  --duration 20m \
  --base-qps 0.5 \
  --max-qps 2.5 \
  --seed 42 \
  --log-path /opt/network-monitor/logs/course-selection.jsonl \
  --summary-csv /opt/network-monitor/logs/course-selection-summary.csv
```

## 后台运行与停止

后台运行：

```bash
nohup /opt/network-monitor/campus_real_traffic_driver.py \
  --scenario normal_week \
  --duration 2h \
  --base-qps 0.3 \
  --max-qps 1.5 \
  --log-path /opt/network-monitor/logs/normal-2h.jsonl \
  --summary-csv /opt/network-monitor/logs/normal-2h-summary.csv \
  > /opt/network-monitor/logs/normal-2h.out 2>&1 &
echo $! > /opt/network-monitor/logs/traffic-driver.pid
```

停止：

```bash
kill -TERM $(cat /opt/network-monitor/logs/traffic-driver.pid)
```

脚本收到 `SIGINT` 或 `SIGTERM` 后会停止继续调度，并等待已发出的请求完成。

## 输出文件

JSONL 请求日志，每行一条：

```json
{"time":"2026-05-20T09:00:01.123","endpoint":"dashboard_metrics","target":"backend","method":"GET","url":"http://8.146.228.64:8080/api/dashboard/metrics?minutesAgo=-5","status":200,"ok":true,"latencyMs":35.42}
```

CSV 分钟摘要：

```csv
minute,scenario,avg_multiplier,planned_requests,success,failed,avg_latency_ms
2026-05-20T09:00:00,course_selection_week,1.4720,38,38,0,42.31
```

## 可选 PCAP 回放：sample.pcap 主线说明

查看样例文件：

```bash
cd /opt/network-monitor
find . -type f -name "*.pcap"
# 预期输出：./sample.pcap
```

查看网卡：

```bash
ip route get 8.146.228.64
ip -br addr
```

PCAP dry-run，默认不会发包：

```bash
/opt/network-monitor/campus_real_traffic_driver.py \
  --mode pcap \
  --pcap /opt/network-monitor/sample.pcap \
  --target-ip 8.146.228.64 \
  --interface eth0 \
  --rate-mbps 1.0 \
  --dry-run
```

正式低速回放，需要 root，并需确认目标系统/网络允许接收该类原始包流量：

```bash
sudo /opt/network-monitor/campus_real_traffic_driver.py \
  --mode pcap \
  --pcap /opt/network-monitor/sample.pcap \
  --target-ip 8.146.228.64 \
  --interface eth0 \
  --rate-mbps 1.0 \
  --unsafe-run-pcap
```

安全提醒：PCAP 回放会发原始网络包，可能触发安全组/防火墙/入侵检测，必须先 dry-run，确认网卡和速率后再低速执行。

## 安全保护

- 默认低速：`base-qps=0.3`，`max-qps=1.5`。
- 默认拒绝 `max-qps > 10`，除非显式加 `--unsafe-allow-high-qps`。
- 默认并发：`--max-concurrency 4`。
- 失败率熔断：完成 warm-up 后失败率超过 `--stop-failure-rate`，默认 `0.60`，自动停止。
- 接口安全：只访问只读页面/接口，避免写入、导入、重检测、大导出。
- WebSocket：使用标准库做轻量握手探测；如环境不支持，会记录失败，不影响 HTTP 主流量。
- PCAP：默认 dry-run/低速；正式回放需要 `--unsafe-run-pcap` 和 root 权限。

## 参数速查

```bash
/opt/network-monitor/campus_real_traffic_driver.py --help
```

主线 HTTP/API 参数：

- `--host`：默认 `8.146.228.64`
- `--frontend-port`：默认 `3000`
- `--backend-port`：默认 `8080`
- `--scenario`：`normal_week`、`exam_week`、`course_selection_week`
- `--duration`：如 `30s`、`10m`、`2h`
- `--start-time`：虚拟场景时间，决定早晚高峰、夜间低谷、选课尖峰位置
- `--base-qps` / `--max-qps`
- `--seed`
- `--dry-run`
- `--log-path` / `--summary-csv`
- `--timeout`
- `--max-concurrency`

PCAP 参数：

- `--mode pcap`
- `--pcap /opt/network-monitor/sample.pcap`
- `--interface eth0`
- `--target-ip 8.146.228.64`
- `--rate-mbps 1.0`
- `--unsafe-run-pcap`

## 与现场 play_traffic.sh 的关系

现场 `/opt/network-monitor` 已有：

- `docker-compose.yml`
- `network-monitor-backend/`
- `network-monitor-frontend/`
- `play_traffic.sh`
- `play_traffic.log`
- `sample.pcap`

本脚本命名为 `campus_real_traffic_driver.py`，上传后可与原 `play_traffic.sh` 并存，不会覆盖原脚本。重新制作的原因是原 `play_traffic.sh` 容易让流量长时间保持高位，看起来不真实；新脚本会按时间段动态调节强度：夜间低谷、教学时段中等、午间/晚间小高峰、考试周复习波动、选课窗口短时尖峰，然后自然回落。

最贴近现场的 2 小时选课周运行示例：

```bash
cd /opt/network-monitor
chmod +x campus_real_traffic_driver.py
./campus_real_traffic_driver.py \
  --scenario course_selection_week \
  --start-time 2026-05-20T09:00:00 \
  --duration 2h \
  --base-qps 0.5 \
  --max-qps 2.5 \
  --log-path /opt/network-monitor/logs/course-selection-2h.jsonl \
  --summary-csv /opt/network-monitor/logs/course-selection-2h-summary.csv
```

如果仍要沿用 `online_traffic_driver.py` 名称，仓库内提供了一个兼容 wrapper；正式上传建议上传 `campus_real_traffic_driver.py`，避免与历史脚本混淆。
