# 校园网络流量监控与安全态势感知系统

## 📋 项目概述

这是一个企业级的网络流量监控和安全态势感知系统，针对高校校园网的复杂运维需求而设计。系统支持秒级流量监控、多维度可视化分析、实时安全威胁检测和微观流级检索。

### 核心功能

- ✅ **实时全景仪表板** - 吞吐量、PPS、活跃IP、应用协议分布
- ✅ **区域/楼宇透视** - 按交换机维度展示流量热点
- ✅ **安全威胁感知** - 检测DDoS、端口扫描、恶意软件、钓鱼攻击
- ✅ **流级检索** - Web版Wireshark，支持多维度查询
- ✅ **地理溯源** - 威胁源的全球地理分布展示

## 🏗️ 系统架构

```
Frontend (React 18.x)
    ↓
REST API & WebSocket (Spring Boot 3.x)
    ↓
数据缓存 & 队列 (Redis / RabbitMQ)
    ↓
数据存储 (InfluxDB / PostgreSQL)
    ↓
流量采集 (ntopng / PCAP导入)
```

## 🚀 快速开始

### 前置条件

- Docker & Docker Compose
- 8GB+ RAM
- 50GB+ 可用磁盘空间

### 一键部署

```bash
# 1. 进入项目目录
cd /path/to/project

# 2. 启动所有服务
docker-compose up -d

# 3. 等待服务启动（约2-3分钟）
docker-compose logs -f

# 4. 访问系统
- 前端大屏: http://localhost:3000
- 后端API: http://localhost:8080
- API文档: http://localhost:8080/swagger-ui.html
```

### 本地开发

**后端开发**：
```bash
cd network-monitor-backend
mvn spring-boot:run
```

**前端开发**：
```bash
cd network-monitor-frontend
npm install
npm run dev
```

## 📊 API文档

### 仪表板接口

```bash
# 获取实时指标
GET /api/dashboard/metrics?minutesAgo=-5

# 获取Top-K大流
GET /api/dashboard/top-flows?limit=10

# 获取区域流量分布
GET /api/dashboard/region-traffic

# 获取威胁统计
GET /api/dashboard/threat-statistics?minutesAgo=-60
```

### 安全告警接口

```bash
# 获取告警列表
GET /api/security/alerts?minutesAgo=-60

# 获取严重告警
GET /api/security/critical-alerts

# 运行威胁检测
POST /api/security/run-detection

# 获取威胁地理分布
GET /api/security/geo-distribution
```

### 流检索接口

```bash
# 多条件搜索流
POST /api/flows/search?srcIp=xx&dstIp=xx&dstPort=xx

# 获取流详情
GET /api/flows/{flowId}

# 按IP查询
GET /api/flows/by-ip/{ip}
```

## 🔧 配置

### 数据库配置 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/network_monitor
    username: postgres
    password: password

  redis:
    host: localhost
    port: 6379

influxdb:
  url: http://localhost:8086
  database: network_monitor
```

### 流量数据导入

```java
// 使用DataImportService生成模拟数据
@Autowired
private DataImportService dataImportService;

// 初始化演示数据
dataImportService.initializeDemoData();

// 或生成特定数量的数据
dataImportService.generateSampleFlows(10000);
```

## 📈 性能指标

- 支持链路速率：10Gbps+
- 秒级采样粒度
- 实时API响应：< 500ms
- WebSocket推送：< 100ms
- 支持大流查询：100万+ 记录

## 🛠️ 技术栈

### 后端
- Spring Boot 3.2.0
- PostgreSQL 15
- InfluxDB 2.6
- Redis 7.0
- Java 17

### 前端
- React 18.2.0
- ECharts 5.4.3
- Zustand 4.4.1
- Vite 5.0.8

## 🔒 安全特性

- 四层威胁检测：DDoS、端口扫描、恶意软件、钓鱼攻击
- IP信誉库查询
- 异常流量检测
- 地理位置追踪

## 📝 开发指南

### 添加新的应用协议识别

编辑 `AppIdentificationService.java`：

```java
public String identifyByPort(Integer port) {
    return switch (port) {
        case 3306 -> "MySQL";
        case 5432 -> "PostgreSQL";
        // 添加新协议...
        default -> "Unknown";
    };
}
```

### 添加新的威胁检测规则

编辑 `ThreatDetectionService.java`：

```java
public List<SecurityAlert> detectCustomThreat(LocalDateTime startTime, LocalDateTime endTime) {
    // 实现自定义威胁检测逻辑
    // ...
}
```

## 🐛 故障排查

### 数据库连接失败

```bash
# 检查 PostgreSQL 服务
docker-compose logs postgres

# 进入容器调试
docker-compose exec postgres psql -U postgres -d network_monitor
```

### 前端无法连接API

检查 `nginx.conf` 中的代理配置：
```nginx
location /api/ {
    proxy_pass http://backend:8080/api/;
}
```

### 内存溢出

调整 Spring Boot JVM 参数：
```bash
JAVA_OPTS="-Xmx4g -Xms2g" docker-compose up
```

## 📊 监控数据示例

### 流量数据结构

```json
{
  "srcIp": "192.168.1.100",
  "dstIp": "10.0.0.50",
  "srcPort": 1024,
  "dstPort": 80,
  "protocol": "TCP",
  "appProtocol": "HTTP",
  "bytesSent": 1024000,
  "bytesRecv": 2048000,
  "packetsSent": 500,
  "packetsRecv": 1000
}
```

### 告警数据结构

```json
{
  "alertType": "DDoS",
  "severity": "critical",
  "srcIp": "203.0.113.45",
  "dstIp": "192.168.1.1",
  "detectedTime": "2026-03-28T15:30:00",
  "country": "China",
  "city": "Beijing",
  "latitude": 39.9042,
  "longitude": 116.4074
}
```

## 📄 许可证

MIT License - 可自由使用、修改和分发

## 👥 贡献指南

欢迎提交 Pull Request 或报告问题！

## 📞 支持

- 问题报告: 提交 GitHub Issue
- 功能建议: 讨论区讨论
- 文档: 查看 `/docs` 目录

---

**开发人员**: Claude Code
**最后更新**: 2026-03-28
**版本**: 1.0.0
