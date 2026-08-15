# 构建产物部署说明（JAR + 前端 dist）

本目录是“构建产物包”，不是源码包。目录结构：

```text
deploy-package/
├─ backend/network-monitor.jar
├─ frontend/dist/
├─ scripts/campus_real_traffic_driver.py
├─ scripts/online_traffic_driver.py
└─ scripts/README_online_traffic_driver.md
```

## 一、系统服务器 8.146.228.64（宝塔）

服务器项目目录建议：`/opt/network-monitor`。

### 1. 上传

方式 A：宝塔文件管理上传整个 `deploy-package` 或压缩包到 `/opt/network-monitor`。

方式 B：只上传两个核心产物：

- `backend/network-monitor.jar` → `/opt/network-monitor/backend/network-monitor.jar`
- `frontend/dist/` → `/opt/network-monitor/frontend/dist/`

### 2. 备份旧产物

```bash
cd /opt/network-monitor
mkdir -p backup/$(date +%Y%m%d-%H%M%S)
cp -f backend/network-monitor.jar backup/$(date +%Y%m%d-%H%M%S)/network-monitor.jar 2>/dev/null || true
cp -a frontend/dist backup/$(date +%Y%m%d-%H%M%S)/frontend-dist 2>/dev/null || true
```

### 3. 覆盖后端 JAR

```bash
cd /opt/network-monitor
mkdir -p backend logs
cp -f deploy-package/backend/network-monitor.jar backend/network-monitor.jar
```

如果你通过宝塔直接上传到目标路径，可跳过 `cp`。

### 4. 覆盖前端 dist

```bash
cd /opt/network-monitor
mkdir -p frontend
rm -rf frontend/dist
cp -a deploy-package/frontend/dist frontend/dist
```

宝塔 Nginx 站点根目录应指向：

```text
/opt/network-monitor/frontend/dist
```

前端 API 反向代理保持 `/api` → `http://127.0.0.1:8080/api`，WebSocket 保持 `/ws` → `http://127.0.0.1:8080/ws`。

### 5. 检查宝塔 `.env` 环境变量文件

本包已提供宝塔部署版环境变量模板：

- `deploy-package/.env`
- `deploy-package/.env.baota`

可上传到服务器项目目录：`C:/wwwroot/network-monitor/.env`（或 Linux 路径 `/opt/network-monitor/.env`）。

模板内容使用 PostgreSQL 本机默认端口 `5432`：

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/network_monitor
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=请替换为你的PostgreSQL密码
APP_DEMO_AUTO_INIT=false
SERVER_PORT=8080
```

上传后必须把 `SPRING_DATASOURCE_PASSWORD` 改成服务器 PostgreSQL 真实密码；不要把真实密码写入 JAR、Git 或部署说明。

如果用命令行启动，可先加载 `.env`：

```bash
set -a
source .env
set +a
```

### 6. 启动或重启后端

简单 nohup 方式：

```bash
cd /opt/network-monitor
pkill -f 'network-monitor.jar' || true
nohup java -jar backend/network-monitor.jar --server.port=8080 > logs/backend.log 2>&1 &
```

如果宝塔 Java 项目管理器已配置该 JAR，请在宝塔里重启 Java 项目即可。

### 7. 验证

```bash
curl -f http://127.0.0.1:8080/api/dashboard/health
curl -f 'http://127.0.0.1:8080/api/dashboard/period-analysis?minutesAgo=-60&bucketMinutes=10'
```

浏览器打开前端页面，在“全景画像”中拖动“链路吞吐变化”图底部时间缩放条，查看图下方区间摘要是否更新。

## 二、打流服务器 60.205.56.61（无宝塔，仅 SSH/SCP）

### 1. 上传脚本

```bash
scp deploy-package/scripts/campus_real_traffic_driver.py root@60.205.56.61:/opt/network-monitor/
scp deploy-package/scripts/online_traffic_driver.py root@60.205.56.61:/opt/network-monitor/
scp deploy-package/scripts/README_online_traffic_driver.md root@60.205.56.61:/opt/network-monitor/
```

### 2. 赋权并 dry-run

```bash
ssh root@60.205.56.61
cd /opt/network-monitor
chmod +x campus_real_traffic_driver.py online_traffic_driver.py
python3 campus_real_traffic_driver.py --target-url http://8.146.228.64:8080 --dry-run
```

### 3. 正式运行

先阅读 `README_online_traffic_driver.md` 参数说明，再执行正式打流，例如：

```bash
python3 campus_real_traffic_driver.py --target-url http://8.146.228.64:8080 --duration-minutes 30
```

`online_traffic_driver.py` 是兼容 wrapper，推荐优先使用 `campus_real_traffic_driver.py`。

## 三、校验

本包提供 SHA256 校验文件：

- `SHA256SUMS.txt`：目录内关键产物校验
- `*.zip.sha256`：最终压缩包校验（如果上传 zip）
