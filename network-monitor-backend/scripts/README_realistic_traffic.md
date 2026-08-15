# 真实场景波动打流数据生成脚本

`generate_realistic_traffic_pcap.py` 用于生成可被本项目后端直接导入的校园网络流量 PCAP。脚本只依赖 Python 标准库，适合上传到打流服务器或数据准备服务器运行。

## 输出格式

- 主输出：标准 `pcap` 文件，链路类型为 Ethernet，包内容为 IPv4 + TCP/UDP。
- 项目导入：后端 `AdminImportController` 当前支持 `/api/admin/import/pcap-path` 与 `/api/admin/import/pcap-upload`，因此脚本默认生成 `.pcap`。
- 辅助输出：同名 `.summary.csv`，按分钟记录场景系数、包数、字节数和波动/异常说明，便于核验曲线是否自然。

PCAP 被导入后会映射到 `NetworkFlow` 模型字段：`srcIp`、`dstIp`、`srcPort`、`dstPort`、`protocol`、`bytesSent`、`packetsSent`、`appProtocol`、`timestamp`、`region`、`direction` 等。

## 场景说明

- `normal_week`：普通教学周，包含夜间低谷、上午/下午教学高峰、午间波动、晚间回落。
- `exam_week`：考试周，早晨入场、晚间复习和深夜尾流略高，但整体不过度夸张。
- `course_selection_week`：选课周，在上午/下午选课开放窗口出现短时登录聚集，其余时间保持普通校园波动。
- 所有场景都叠加轻微随机噪声和少量短时异常尖峰，例如 DNS 重试、实验室下载、短视频聚集或扫描噪声。

## 常用参数

```bash
python generate_realistic_traffic_pcap.py \
  --scenario normal_week \
  --start 2026-05-20T08:00:00 \
  --duration-hours 24 \
  --base-qps 0.5 \
  --seed 20260520 \
  --output out/normal_week_24h.pcap
```

参数含义：

- `--scenario`：`normal_week`、`exam_week`、`course_selection_week`。
- `--start`：开始时间，ISO 格式，例如 `2026-05-20T08:00:00`。
- `--duration-hours`：生成时长，单位小时，支持小数。
- `--output`：输出 PCAP 路径。
- `--summary-csv`：可选，指定分钟级摘要 CSV；不指定时生成同名 `.summary.csv`。
- `--seed`：随机种子，用于复现同一批数据。
- `--base-qps`：基础逻辑包速率，脚本会按场景/日周期乘以波动系数。
- `--base-mbps`：可选基础带宽规模；设置后会尽量调整包大小贴近目标带宽。
- `--max-packets`：安全上限，避免误生成超大文件；`0` 表示禁用上限。
- `--anomaly-rate`：每分钟短时异常尖峰概率，默认 `0.003`，建议不要设置过高。

## 上传到打流服务器后运行

Linux/macOS：

```bash
mkdir -p ~/campus-traffic/out
scp generate_realistic_traffic_pcap.py user@traffic-server:~/campus-traffic/
ssh user@traffic-server
cd ~/campus-traffic
python3 generate_realistic_traffic_pcap.py \
  --scenario course_selection_week \
  --start 2026-05-20T08:00:00 \
  --duration-hours 48 \
  --base-qps 1.2 \
  --seed 42 \
  --output out/course_selection_48h.pcap
```

Windows PowerShell：

```powershell
python .\generate_realistic_traffic_pcap.py `
  --scenario exam_week `
  --start 2026-05-20T08:00:00 `
  --duration-hours 12 `
  --base-qps 0.8 `
  --seed 42 `
  --output .\out\exam_week_12h.pcap
```

## 导入到本项目后端

路径导入示例：

```bash
curl -X POST "http://localhost:8080/api/admin/import/pcap-path" \
  --data-urlencode "path=/absolute/path/to/course_selection_48h.pcap" \
  --data-urlencode "replaceExisting=true" \
  --data-urlencode "rebaseTimestamps=false" \
  --data-urlencode "runThreatDetection=true" \
  --data-urlencode "inactivitySeconds=60"
```

文件上传示例：

```bash
curl -X POST "http://localhost:8080/api/admin/import/pcap-upload" \
  -F "file=@out/course_selection_48h.pcap" \
  -F "replaceExisting=true" \
  -F "rebaseTimestamps=false" \
  -F "runThreatDetection=true" \
  -F "inactivitySeconds=60"
```

说明：如果希望数据时间自动平移到当前时间附近，可将 `rebaseTimestamps=true`；如果要保留脚本生成的考试周/选课周绝对时间，请使用 `false`。

## 注意事项

- 该脚本用于项目演示和评测数据准备，不声称为真实线上采样。
- 默认 `base-qps` 较小，适合快速生成和导入测试；正式演示可逐步增加到 `1~5`，避免一次生成过大 PCAP。
- `course_selection_week` 的尖峰是短时温和放大，不会制造长期夸张流量。
- 摘要 CSV 只是核验辅助文件，后端导入使用 PCAP。
