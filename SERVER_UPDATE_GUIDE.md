# Network Monitor 服务器更新包部署说明

本压缩包用于更新系统服务器 `8.146.228.64` 的 `/opt/network-monitor` 项目，以及将在线打流脚本同步到打流服务器 `60.205.56.61`。

## 一、系统服务器 `8.146.228.64`（有宝塔）

### 1. 上传与备份

1. 在宝塔文件管理中进入 `/opt/network-monitor`。
2. 上传 `network-monitor-update-*.zip` 到 `/opt/network-monitor`。
3. 在宝塔终端或 SSH 中执行备份：

```bash
cd /opt
sudo tar -czf network-monitor-backup-$(date +%Y%m%d-%H%M%S).tar.gz network-monitor
```

### 2. 解压覆盖

```bash
cd /opt/network-monitor
unzip -o network-monitor-update-*.zip
```

压缩包根目录包含 `docker-compose.yml`、`network-monitor-backend/`、`network-monitor-frontend/` 和本说明文档。

### 3. 检查 `.env`

不要用压缩包覆盖服务器真实密码。确认 `/opt/network-monitor/.env` 至少包含：

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=你的服务器PostgreSQL密码
POSTGRES_DB=network_monitor
POSTGRES_HOST_PORT=55432
APP_DEMO_AUTO_INIT=false
```

如果 `.env` 不存在，请新建；如果已存在，只检查变量是否正确，不要把真实密码写入 Git 或文档。

### 4. 构建并更新容器

```bash
cd /opt/network-monitor
docker compose build backend frontend
docker compose up -d backend frontend
```

如需要同时启动数据库，可执行：

```bash
docker compose up -d postgres backend frontend
```

### 5. 验证系统

```bash
docker compose ps
curl -f http://127.0.0.1:8080/api/dashboard/health
curl -f "http://127.0.0.1:8080/api/dashboard/period-analysis?minutesAgo=-60&bucketMinutes=10"
```

浏览器访问前端域名或 `http://8.146.228.64:3000`，在“全景画像”页面拖动“链路吞吐变化”图底部时间缩放条，检查图下方轻量区间摘要是否更新。

## 二、打流服务器 `60.205.56.61`（无宝塔，仅 SSH/SCP）

### 1. 从更新包取脚本

在本地或系统服务器解压后，取以下两个文件：

- `network-monitor-backend/scripts/online_traffic_driver.py`
- `network-monitor-backend/scripts/README_online_traffic_driver.md`

### 2. 上传到打流服务器

```bash
scp network-monitor-backend/scripts/online_traffic_driver.py root@60.205.56.61:/opt/network-monitor/
scp network-monitor-backend/scripts/README_online_traffic_driver.md root@60.205.56.61:/opt/network-monitor/
```

如使用非 root 用户，请替换用户名和目标目录。

### 3. 赋权与 dry-run

```bash
ssh root@60.205.56.61
cd /opt/network-monitor
chmod +x online_traffic_driver.py
python3 online_traffic_driver.py --target-url http://8.146.228.64:8080 --dry-run
```

### 4. 正式运行

请先阅读 `README_online_traffic_driver.md` 中的参数说明。确认目标 URL、速率、时长后再执行正式打流，例如：

```bash
python3 online_traffic_driver.py --target-url http://8.146.228.64:8080 --duration-minutes 30
```

运行时可在系统服务器前端观察实时指标和“链路吞吐变化”图。

## 三、回滚建议

如果更新后异常，可在系统服务器执行：

```bash
cd /opt
sudo rm -rf network-monitor
sudo tar -xzf network-monitor-backup-YYYYMMDD-HHMMSS.tar.gz
cd /opt/network-monitor
docker compose up -d --build
```

请将备份文件名替换为实际生成的文件名。
