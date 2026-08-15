# 系统架构设计文档

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│         React 前端大屏 (http://localhost:3000)      │
├─────────────────────────────────────────────────────┤
│  浏览器与WebSocket实时通信                           │
├─────────────────────────────────────────────────────┤
│  Spring Boot REST API (http://localhost:8080)       │
│  ├─ DashboardController (仪表板数据)                │
│  ├─ SecurityAlertsController (安全告警)             │
│  ├─ FlowSearchController (流检索)                   │
│  └─ RegionController (区域分析)                     │
├─────────────────────────────────────────────────────┤
│  业务逻辑服务层                                      │
│  ├─ FlowAnalysisService (流量分析)                  │
│  ├─ ThreatDetectionService (威胁检测)               │
│  ├─ AppIdentificationService (应用识别)             │
│  └─ DataImportService (数据导入)                    │
├─────────────────────────────────────────────────────┤
│  数据访问层 (JPA Repository)                        │
│  ├─ NetworkFlowRepository                           │
│  ├─ SecurityAlertRepository                         │
│  └─ MetricsRepository (可选)                        │
├─────────────────────────────────────────────────────┤
│  数据存储层                                          │
│  ├─ PostgreSQL (流量数据、告警日志)                  │
│  ├─ InfluxDB (时序指标数据)                         │
│  └─ Redis (实时缓存、会话)                          │
├─────────────────────────────────────────────────────┤
│  流量采集与预处理                                    │
│  ├─ PCAP 文件导入                                   │
│  ├─ ntopng / Zeek / Suricata (可选)                 │
│  └─ 模拟数据生成                                     │
└─────────────────────────────────────────────────────┘
```

## 核心模块详解

### 1. 前端展示层 (React)

**位置**: `network-monitor-frontend/src`

```
pages/
├─ Dashboard.jsx      # 全景仪表板（首页）
├─ SecurityCenter.jsx # 安全威胁感知页面
├─ FlowSearch.jsx     # 流级检索页面
└─ IpDetails.jsx      # IP详情页面

components/
├─ MetricsCard.jsx    # 指标卡片组件
├─ ChartCard.jsx      # 图表组件（ECharts）
└─ TopFlowsTable.jsx  # 流量表格

store/
└─ index.js           # Zustand 状态管理

api/
└─ client.js          # Axios API 客户端
```

**技术特点**：
- 使用 Zustand 进行状态管理（轻量级）
- ECharts 用于数据可视化
- WebSocket 实时数据推送
- 响应式设计，支持大屏和移动设备

### 2. REST API 层 (Spring Boot)

**位置**: `network-monitor-backend/src/main/java/com/campus/network/controller`

#### 接口设计

```
Dashboard API
├─ GET /metrics          # 实时指标（吞吐、PPS、活跃IP、协议分布）
├─ GET /top-flows        # Top-K 大流
├─ GET /region-traffic   # 区域流量分布
└─ GET /threat-statistics# 威胁统计

Security API
├─ GET /alerts           # 告警列表
├─ GET /critical-alerts  # 严重告警
├─ POST /run-detection   # 手动运行检测
└─ GET /geo-distribution # 地理分布

Flow API
├─ POST /search          # 多条件搜索
├─ GET /{flowId}         # 流详情
├─ GET /by-ip/{ip}       # 按IP查询
└─ GET /by-protocol/{p}  # 按协议查询
```

#### 请求/响应示例

**获取实时指标**：
```bash
GET /api/dashboard/metrics?minutesAgo=-5

Response:
{
  "status": "success",
  "throughputMbps": "256.45",
  "pps": "15000.00",
  "activeIps": 2500,
  "appDistribution": {
    "HTTP": 1200,
    "HTTPS": 1000,
    "DNS": 800,
    "SSH": 250
  },
  "timestamp": "2026-03-28T16:30:00"
}
```

### 3. 业务逻辑层 (Services)

#### FlowAnalysisService
```
功能：
├─ calculateThroughputMbps()    # 吞吐量计算
├─ calculatePPS()               # 包速率计算
├─ countActiveIps()             # 活跃IP统计
├─ getAppProtocolDistribution() # 协议分布
├─ getTopFlows()                # Top-K流
└─ getRegionTraffic()           # 区域流量统计
```

#### ThreatDetectionService
```
功能：
├─ detectDDosAttacks()          # DDoS检测
├─ detectPortScanAttacks()      # 端口扫描检测
├─ detectDataExfiltration()     # 数据外泄检测
├─ detectMalware()              # 恶意软件检测
├─ runFullThreatDetection()     # 完整检测流程
└─ getThreatStatistics()        # 威胁统计
```

#### AppIdentificationService
```
功能：
├─ identifyByPort()             # 基于端口识别
├─ identifyProtocol()           # 综合识别协议
├─ isEncrypted()                # 检查是否加密
└─ getSupportedProtocols()      # 获取支持的协议列表

支持的协议（17+种）：
HTTP, HTTPS, SSH, DNS, SMTP, POP3S, IMAPS,
IMAP, FTP, MySQL, PostgreSQL, RDP, NTP, DHCP,
SNMP, POP3, Telnet, VPN
```

### 4. 数据访问层 (Repository)

使用 Spring Data JPA 进行数据持久化：

```java
NetworkFlowRepository
├─ findBySrcIpOrDstIp()        # 按IP查询
├─ findByTimestampBetween()    # 按时间查询
├─ findByAppProtocol()         # 按协议查询
├─ searchFlows()               # 多条件搜索
└─ findTopFlowsByBytes()       # Top-K查询

SecurityAlertRepository
├─ findByDetectedTimeBetween() # 按时间查询告警
├─ findByAlertType()           # 按告警类型查询
├─ findBySrcIp()               # 按源IP查询
├─ countByAlertType()          # 告警类型统计
└─ findCriticalAlerts()        # 查询严重告警
```

### 5. 数据模型

#### NetworkFlow (网络流)
```java
@Entity
public class NetworkFlow {
    Long id;
    String srcIp;           // 源IP
    String dstIp;           // 目的IP
    Integer srcPort;        // 源端口
    Integer dstPort;        // 目的端口
    String protocol;        // TCP/UDP/ICMP
    Long bytesSent;         // 发送字节数
    Long bytesRecv;         // 接收字节数
    Long packetsSent;       // 发送包数
    Long packetsRecv;       // 接收包数
    String appProtocol;     // HTTP, HTTPS, DNS...
    LocalDateTime startTime;// 流开始时间
    LocalDateTime endTime;  // 流结束时间
    String region;          // 来自哪个区域/交换机
    String direction;       // 入/出
}
```

#### SecurityAlert (安全告警)
```java
@Entity
public class SecurityAlert {
    Long id;
    String alertType;       // DDoS, PortScan, Malware, Phishing
    String severity;        // low, medium, high, critical
    String srcIp;           // 源IP
    String dstIp;           // 目的IP
    LocalDateTime detectedTime;
    String description;     // 告警描述
    String country;         // 国家（地理位置）
    String city;            // 城市
    Double latitude;        // 纬度
    Double longitude;       // 经度
    String threatDetails;   // JSON格式的详细信息
    Boolean confirmed;      // 是否已确认
}
```

### 6. 数据库设计

#### PostgreSQL 表结构

```sql
-- 网络流表
CREATE TABLE network_flows (
    id SERIAL PRIMARY KEY,
    src_ip VARCHAR(45) NOT NULL,
    dst_ip VARCHAR(45) NOT NULL,
    src_port INTEGER NOT NULL,
    dst_port INTEGER NOT NULL,
    protocol VARCHAR(20),
    bytes_sent BIGINT NOT NULL,
    bytes_recv BIGINT NOT NULL,
    packets_sent BIGINT NOT NULL,
    packets_recv BIGINT NOT NULL,
    app_protocol VARCHAR(50),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    region VARCHAR(100) NOT NULL,
    direction VARCHAR(10)
);

-- 创建索引加速查询
CREATE INDEX idx_src_ip ON network_flows(src_ip);
CREATE INDEX idx_dst_ip ON network_flows(dst_ip);
CREATE INDEX idx_timestamp ON network_flows(timestamp);
CREATE INDEX idx_app_protocol ON network_flows(app_protocol);

-- 安全告警表
CREATE TABLE security_alerts (
    id SERIAL PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20),
    src_ip VARCHAR(45) NOT NULL,
    dst_ip VARCHAR(45),
    detected_time TIMESTAMP NOT NULL,
    description VARCHAR(500),
    country VARCHAR(100),
    city VARCHAR(100),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    threat_details TEXT,
    confirmed BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_alert_time ON security_alerts(detected_time);
CREATE INDEX idx_alert_type ON security_alerts(alert_type);
```

#### InfluxDB 时序数据

```
Measurement: network_metrics
Tags: [time, region, app_type, direction]
Fields: [throughput_mbps, pps, active_flows, unique_src_ips, unique_dst_ips]

Example:
network_metrics,region=dormitory_a,app_type=HTTP,direction=in
    throughput_mbps=100.5,pps=5000,active_flows=250 1640000000000000000
```

## 数据流

### 1. 仪表板数据流

```
User访问首页
    ↓
前端加载 Dashboard.jsx
    ↓
调用 dashboardAPI.getMetrics()
    ↓
Axios 发送 GET /api/dashboard/metrics
    ↓
Spring Boot DashboardController.getMetrics()
    ↓
FlowAnalysisService 计算指标
    ↓
查询 PostgreSQL 获取流数据
    ↓
返回 JSON 数据
    ↓
前端使用 Zustand 存储状态
    ↓
React 组件渲染图表
    ↓
ECharts 绘制可视化
```

### 2. 威胁检测数据流

```
定时任务（可选）或手动触发
    ↓
ThreatDetectionService.runFullThreatDetection()
    ↓
检测DDoS：groupBy(srcIp) 统计流量
检测端口扫描：groupBy(srcIp, dstIp) 统计连接失败
检测异常流量：检查包大小
检测恶意IP：查询黑名单
    ↓
生成 SecurityAlert 对象
    ↓
保存到 PostgreSQL security_alerts表
    ↓
前端定期刷新 SecurityCenter.jsx
    ↓
调用 securityAPI.getAlerts()
    ↓
展示告警列表和地图
```

### 3. 流级检索数据流

```
用户输入搜索条件
    ↓
FlowSearch.jsx 收集参数
    ↓
调用 flowAPI.search(params)
    ↓
POST /api/flows/search?srcIp=xx&dstPort=xx...
    ↓
FlowSearchController.searchFlows()
    ↓
NetworkFlowRepository.searchFlows() 执行JPQL查询
    ↓
返回符合条件的流记录列表
    ↓
前端展示在表格中
    ↓
支持点击查看流详情
```

## 关键算法

### 威胁检测算法

#### DDoS 检测
```
算法：异常统计检测
1. 按源IP分组，统计总流量（Bytes）
2. 如果流量 > THRESHOLD (1GB/30s)，标记为DDoS
3. 计算平均流量大小，检查是否异常突增
4. 返回告警：[IP, 时间, 流量大小]
```

#### 端口扫描检测
```
算法：连接失败率检测
1. 按(srcIp, dstIp)分组
2. 统计不同目的端口数量
3. 如果端口数 > THRESHOLD (50个)，标记为端口扫描
4. 返回告警：[源IP, 目的IP, 扫描端口数]
```

#### 数据外泄检测
```
算法：异常包大小检测
1. 遍历所有流记录
2. 检查单个流的总字节数
3. 如果流 > THRESHOLD (10MB/packet)，标记为异常
4. 返回告警：[源IP, 目的IP, 包大小]
```

### 应用识别算法

```
优先级识别：
1. 检查目的端口（准确性最高）
   - 80 → HTTP
   - 443 → HTTPS
   - 22 → SSH
   - 53 → DNS
   - ...

2. 如果无法识别，检查源端口

3. 根据包大小和流持续时间推断应用类型
   - 小包、高频率 → DNS或控制协议
   - 大包、长连接 → 数据传输（HTTP、FTP等）
   - 加密包 → HTTPS、SSH等

4. 支持集成 DPI 库（nDPI、libprotoident）进行深包检测
```

## 性能优化

### 1. 数据库优化
- 在 `src_ip`, `dst_ip`, `timestamp`, `app_protocol` 上建立索引
- 分区存储（按时间分区）
- 定期清理过期数据

### 2. 缓存策略
- Redis 缓存热点数据（Top-K流、区域统计）
- 缓存更新周期：5秒

### 3. 前端优化
- 虚拟滚动（长列表）
- 图表分批渲染
- WebSocket 推送替代定时轮询
- 使用 Web Worker 处理大数据

### 4. 查询优化
- 使用 JPQL 的 `@Query` 注解，而不是遍历
- 批量操作而非逐条处理
- 使用分页查询限制数据量

## 扩展性

### 1. 分布式部署

```
多个 Backend 实例 + Nginx 负载均衡
    ↓
使用 Redis 作为分布式缓存
    ↓
PostgreSQL 主从复制
    ↓
InfluxDB 集群
```

### 2. 实时性增强

```
使用 Kafka 处理流量事件
    ↓
Flink 实时处理和聚合
    ↓
WebSocket 推送实时告警
```

### 3. 智能化增强

```
集成 ML 模型进行异常检测
    ↓
使用 Prophet 进行流量趋势预测
    ↓
实现基于学习的应用识别
```

---

**版本**: 1.0.0
**最后更新**: 2026-03-28
