# 🚀 快速启动指南

## ⚡ 30秒快速开始

```bash
# 1. 进入项目目录
cd /d/ASUS/Documents/接单/外包

# 2. 启动Docker容器
docker-compose up -d

# 3. 等待2-3分钟，自动初始化数据

# 4. 访问系统
- 前端大屏: http://localhost:3000
- 后端API: http://localhost:8080
- API文档: http://localhost:8080/swagger-ui.html
```

## 📋 系统要求

✅ Docker Desktop 20.10+ （包含 Docker Compose）
✅ 8GB+ 内存
✅ 20GB+ 磁盘空间
✅ 支持平台：Windows + WSL2, MacOS, Linux

## 🎯 默认登录信息

| 服务 | 用户名 | 密码 |
|-----|--------|------|
| PostgreSQL | postgres | password |
| InfluxDB | admin | password |
| Redis | (无认证) | - |

## 📊 演示数据

系统启动时自动生成：
- 10,000 条网络流记录
- 100+ 条安全告警
- 5 个区域的流量分布数据
- 多种应用协议混合

## 🔍 功能演示流程

### 1. 访问全景仪表板（首页）

**地址**: http://localhost:3000

**看到的内容**：
- 📈 实时吞吐量（Mbps）
- 📦 包速率（PPS）
- 🌐 活跃IP数量
- 📊 应用协议分布饼图
- 📋 Top-10 流量排行表格

**可进行的操作**：
- 🔄 点击"刷新"按钮更新数据
- ☑️ 开启"自动刷新"（5秒更新一次）
- 🖱️ 点击图表元素查看详情

### 2. 浏览安全威胁页面

**导航**: 点击导航栏 "🛡️ 安全威胁"

**看到的内容**：
- ⚠️ 告警统计（总数、关键数量）
- 📋 最近告警列表
  - 告警类型（DDoS、端口扫描等）
  - 源IP和目的IP
  - 检测时间和严重级别

**可进行的操作**：
- 🔍 运行威胁检测（重新扫描）
- 📈 查看告警时间走势
- 🗺️ 查看威胁源的地理分布

### 3. 进行流级检索

**导航**: 点击导航栏 "🔍 流搜索"

**使用方式**：
```
1. 输入搜索条件（可选）：
   - 源IP: 192.168.1.100
   - 目的IP: 10.0.0.50
   - 目的端口: 80
   - 应用协议: HTTP
   - 时间范围: 30分钟内

2. 点击"搜索"按钮

3. 查看匹配的流记录：
   - 5元组信息
   - 字节数和包数
   - 协议类型
```

**搜索示例**：
- 🔎 搜索所有HTTP流量
- 🔎 搜索特定IP的所有通信
- 🔎 搜索特定端口的连接
- 🔎 搜索特定时间段的流量

### 4. 查看IP详情

**方式1**：在仪表板中找到感兴趣的流，点击源IP
**方式2**：在流搜索中选择一条记录

**查看内容**：
- 📊 通信对端排行（通信最频繁的IP）
- 📋 完整的流记录列表
- 📈 流量趋势分析

## 🛠️ 常用命令

### Docker命令

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend    # 查看后端日志
docker-compose logs -f frontend   # 查看前端日志
docker-compose logs -f postgres   # 查看数据库日志

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 清理所有数据（谨慎！）
docker-compose down -v
```

### 数据库操作

```bash
# 进入PostgreSQL
docker-compose exec postgres psql -U postgres -d network_monitor

# 查看表
\dt

# 查看数据
SELECT * FROM network_flows LIMIT 10;

# 退出
\q
```

## 🔧 配置修改

### 修改刷新频率

编辑 `frontend/src/pages/Dashboard.jsx`:
```javascript
// 改变自动刷新周期（单位：毫秒）
const interval = setInterval(() => {
  fetchMetrics()
  fetchTopFlows(10)
}, 3000)  // 改这里，从5000改为3000表示3秒刷新一次
```

### 修改威胁检测阈值

编辑 `backend/.../service/ThreatDetectionService.java`:
```java
// 修改DDoS阈值
private static final long DDOS_THRESHOLD_BYTES = 1024 * 1024 * 1024; // 改这里

// 修改端口扫描阈值
private static final int PORT_SCAN_THRESHOLD = 50; // 改这里
```

### 修改演示数据量

编辑 `backend/.../config/ApplicationStartupInitializer.java`:
```java
// 修改生成的演示数据数量
dataImportService.generateSampleFlows(10000);  // 改这个数字
```

## 📚 API调用示例

### 获取实时指标

```bash
curl http://localhost:8080/api/dashboard/metrics?minutesAgo=-5

# 返回:
{
  "status": "success",
  "throughputMbps": "256.45",
  "pps": "15000.00",
  "activeIps": 2500,
  "appDistribution": {
    "HTTP": 1200,
    "HTTPS": 1000,
    ...
  }
}
```

### 搜索网络流

```bash
curl -X POST 'http://localhost:8080/api/flows/search?srcIp=192.168.1.100&dstPort=80'

# 返回匹配的流记录列表
```

### 获取告警列表

```bash
curl http://localhost:8080/api/security/alerts?minutesAgo=-60

# 返回最近1小时的告警
```

## 🐛 排查问题

### 问题1：Docker不能启动

**症状**：`docker-compose up -d` 失败

**解决**：
```bash
# 1. 确保Docker已启动
docker --version

# 2. 检查Docker Compose版本
docker-compose --version

# 3. 查看错误日志
docker-compose logs

# 4. 如果是端口占用，修改docker-compose.yml中的端口
```

### 问题2：浏览器无法访问http://localhost:3000

**症状**：`Connection refused`

**解决**：
```bash
# 1. 检查容器是否运行
docker-compose ps

# 2. 查看前端日志
docker-compose logs frontend

# 3. 等待2-3分钟，让服务完全启动

# 4. 检查防火墙是否阻止
```

### 问题3：后端API返回错误

**症状**：API调用返回 500 错误

**解决**：
```bash
# 1. 查看后端日志
docker-compose logs backend

# 2. 检查数据库是否正常
docker-compose exec postgres pg_isready

# 3. 如果是连接失败，重启后端
docker-compose restart backend
```

### 问题4：页面加载缓慢

**症状**：访问前端页面响应慢

**解决**：
```bash
# 1. 检查系统内存使用
docker stats

# 2. 增加Docker内存限制
# 编辑 docker-compose.yml，在 backend 或 frontend 服务下添加:
# mem_limit: 4g

# 3. 清理Docker缓存
docker system prune
```

## 📖 详细文档

进一步了解系统，请查看：

1. **README.md** - 项目概述和快速开始
2. **ARCHITECTURE.md** - 系统架构和设计
3. **DEPLOYMENT.md** - 部署和运维指南
4. **IMPLEMENTATION_SUMMARY.md** - 实现总结和后续建议
5. **PROJECT_STRUCTURE.md** - 项目结构和文件清单

## 💻 本地开发

### 后端开发

```bash
cd network-monitor-backend

# 必须先启动数据库
docker-compose up -d postgres redis influxdb

# 使用IDE运行Spring Boot
# 或命令行运行:
mvn spring-boot:run
```

### 前端开发

```bash
cd network-monitor-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 📱 访问地址速查表

| 服务 | URL | 说明 |
|-----|-----|------|
| 前端大屏 | http://localhost:3000 | React应用 |
| 后端API | http://localhost:8080 | Spring Boot |
| API文档 | http://localhost:8080/swagger-ui.html | Swagger UI |
| 健康检查 | http://localhost:8080/api/dashboard/health | 系统状态 |
| PostgreSQL | localhost:5432 | 关系型数据库 |
| Redis | localhost:6379 | 缓存数据库 |
| InfluxDB Web | http://localhost:8086 | 时序数据库 |

## 🎓 学习建议

**初级**（了解系统功能）：
1. 启动系统
2. 浏览各个页面
3. 尝试搜索和筛选

**中级**（理解架构）：
1. 阅读 ARCHITECTURE.md
2. 查看 API文档
3. 追踪几个API调用的完整流程

**高级**（修改代码）：
1. 修改演示数据生成逻辑
2. 添加新的威胁检测规则
3. 自定义前端样式
4. 集成新的数据源

## 💡 下一步建议

系统开箱即用，但如果想进一步优化，可以尝试：

1. **集成地理地图** - 在安全告警页面显示威胁源位置
2. **启用WebSocket** - 实现实时数据推送
3. **添加缓存** - 用Redis缓存热点数据
4. **性能优化** - 优化数据库查询和前端渲染
5. **功能扩展** - 添加新的威胁检测算法

详见 IMPLEMENTATION_SUMMARY.md 中的"后续优化建议"

## 🚀 生产部署

当系统准备投入生产使用时：

1. **修改默认密码** - 在 docker-compose.yml 中
2. **启用HTTPS** - 配置SSL证书
3. **设置负载均衡** - 使用Nginx进行反向代理
4. **部署监控** - 集成Prometheus和Grafana
5. **备份策略** - 定期备份数据库
6. **容器编排** - 考虑使用Kubernetes

详见 DEPLOYMENT.md 中的"生产环境部署"部分

---

**祝您使用愉快！有任何问题，请参考详细文档或查看日志。** 🎉

