# 部署和运维指南

## 🎯 快速开始

### 最小化部署（推荐用于演示）

```bash
# 1. 进入项目目录
cd /path/to/network-monitor

# 2. 启动 Docker 容器
docker-compose up -d

# 3. 检查服务状态
docker-compose ps

# 4. 查看启动日志
docker-compose logs -f backend

# 5. 等待数据初始化（约1-2分钟）
# 看到"初始化完成！访问 http://localhost:3000"表示准备就绪

# 6. 访问系统
- 前端大屏: http://localhost:3000
- API文档: http://localhost:8080/swagger-ui.html
```

## 🔧 配置调优

### PostgreSQL 性能优化

编辑 `docker-compose.yml`：

```yaml
postgres:
  environment:
    POSTGRES_INITDB_ARGS: "-c max_connections=200 -c shared_buffers=256MB"
```

### InfluxDB 保留策略

```bash
docker-compose exec influxdb influx v1 shell

# 在 InfluxDB 命令行
CREATE RETENTION POLICY "30d" ON "network_monitor" DURATION 30d REPLICATION 1 DEFAULT
```

### Redis 内存优化

```yaml
redis:
  command: redis-server --maxmemory 2gb --maxmemory-policy allkeys-lru
```

## 📊 监控和日志

### 查看日志

```bash
# 后端日志
docker-compose logs -f backend

# PostgreSQL日志
docker-compose logs -f postgres

# 前端日志（如果运行在开发模式）
docker-compose logs -f frontend
```

### 性能监控

```bash
# 进入后端容器
docker-compose exec backend bash

# 查看Java进程
jps -v
```

## 🔄 数据导入

### 导入PCAP文件

在后端添加接口（可选）：

```java
@PostMapping("/api/pcap/upload")
public ResponseEntity<Map<String, Object>> uploadPcap(@RequestParam("file") MultipartFile file) {
    // 实现PCAP导入逻辑
    dataImportService.importFromPcapFile(file.getOriginalFilename());
    return ResponseEntity.ok(Collections.singletonMap("status", "success"));
}
```

### 批量生成测试数据

```bash
# 进入Spring Boot容器
docker-compose exec backend bash

# 使用Java进入应用上下文
# 调用 DataImportService.generateSampleFlows(100000)
```

## 🔐 安全加固

### 启用HTTPS

生成自签名证书：

```bash
keytool -genkey -alias tomcat -keyalg RSA -keystore keystore.p12 -storetype PKCS12
```

在 `application.yml` 中配置：

```yaml
server:
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: your-password
    key-store-type: PKCS12
  port: 8443
```

### 数据库备份

```bash
# PostgreSQL 备份
docker-compose exec postgres pg_dump -U postgres network_monitor > backup.sql

# 恢复
docker-compose exec postgres psql -U postgres network_monitor < backup.sql
```

## 🚨 常见问题

### 问题1：PostgreSQL 连接超时

**原因**：数据库还未完全启动

**解决**：
```bash
docker-compose restart postgres
docker-compose up -d backend --force-recreate
```

### 问题2：内存溢出 (Out of Memory)

**原因**：流量数据过多，JVM内存不足

**解决**：
```bash
# 修改 docker-compose.yml
backend:
  environment:
    JAVA_OPTS: "-Xmx4g -Xms2g"
```

### 问题3：前端无法连接API

**原因**：CORS或代理配置问题

**解决**：检查 `nginx.conf` 中的代理配置

### 问题4：告警数据不更新

**原因**：威胁检测未运行或有数据库连接问题

**解决**：
```bash
# 查看后端日志
docker-compose logs backend | grep -i error

# 手动触发检测
curl -X POST http://localhost:8080/api/security/run-detection
```

## 📈 扩展和自定义

### 添加新的威胁检测规则

1. 编辑 `ThreatDetectionService.java`
2. 实现新的检测方法
3. 在 `runFullThreatDetection()` 中调用

### 集成第三方威胁情报库

```java
// 在 ThreatDetectionService 中积分
@Autowired
private ThreatIntelligenceClient threatIntel;

// 查询恶意IP
boolean isMalicious = threatIntel.checkIp(srcIp);
```

### 自定义大屏样式

编辑 `frontend/src/styles/global.css`：

```css
:root {
  --color-primary: #your-color;
  --color-bg-dark: #your-bg;
  /* 更多自定义... */
}
```

## 📊 生产环境部署

### 使用 Kubernetes

创建 `k8s-deployment.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: network-monitor
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: backend
        image: network-monitor-backend:latest
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
```

### 负载均衡

使用 Nginx 进行反向代理和负载均衡：

```nginx
upstream backend {
    server backend-1:8080;
    server backend-2:8080;
    server backend-3:8080;
}

server {
    listen 80;
    location /api {
        proxy_pass http://backend;
    }
}
```

## 🔍 监控指标

### 关键指标

- **API 响应时间**：应< 500ms
- **数据库查询时间**：应< 300ms
- **WebSocket 延迟**：应< 100ms
- **内存占用**：应< 4GB（生产）
- **CPU 使用率**：应< 80%

### 告警阈值

```
DDoS: 单IP流量 > 1GB/30s
端口扫描: 单IP连接失败 > 50
异常包: 单个包大小 > 10MB
```

## 🆘 支持和联系

- 问题报告: GitHub Issues
- 技术支持: 开发团队文档
- 功能建议: Pull Request

---

**最后更新**: 2026-03-28
**版本**: 1.0.0
