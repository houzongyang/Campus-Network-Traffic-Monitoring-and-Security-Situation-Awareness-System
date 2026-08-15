# 校园网络监控系统 - 宝塔面板 (Windows Server) 部署手册

## 📋 部署环境概览
- **服务器**: Windows Server 2022 (2核 2GB)
- **管理面板**: 宝塔面板 (Windows版)
- **技术栈**: React 18 + Spring Boot 3.2 + PostgreSQL 15 + Nginx

---

## 🛠️ 第一步：服务器环境配置 (宝塔面板)

1. **安装软件**:
   进入宝塔“软件商店”，安装以下插件：
   - **Nginx** (建议 1.22+)
   - **PostgreSQL** (建议 15.x)
   - **Java 项目管理器** (确保已安装 JDK 17)

2. **数据库初始化**:
   - 在“数据库” -> “PostgreSQL” 中，点击“添加数据库”。
   - **数据库名**: `network_monitor`
   - **用户名**: `postgres` (或您自定义)
   - **密码**: `password` (需与代码一致，建议在 application.yml 中确认)

3. **开放防火墙端口**:
   - 在宝塔“安全”菜单中，放行以下端口：
     - `3000` (前端访问)
     - `8080` (后端 API)
     - `5432` (PostgreSQL，仅需远程管理时开放)

---

## 🏗️ 第二步：本地打包 (在您的 Windows 电脑执行)

由于服务器内存仅 2GB，**严禁**在服务器上进行编译。请在本地打包后再上传成品。

### 1. 前端打包
打开本地终端，进入 `network-monitor-frontend` 目录：
```powershell
# 安装依赖
npm install

# 编译打包
npm run build
```
编译完成后，会生成 **`dist`** 文件夹。请将其压缩为 `dist.zip`。

### 2. 后端打包
打开本地终端，进入 `network-monitor-backend` 目录：
```powershell
# 使用 Maven 打包
mvn clean package -DskipTests
```
打包完成后，在 `target` 目录下会生成 **`network-monitor-1.0.0.jar`**。

---

## 🚀 第三步：上传并部署

### 1. 部署后端 (Java 项目管理器)
1. 在服务器 `/www/wwwroot/` 下创建目录 `network-monitor/backend`。
2. 上传 `network-monitor-1.0.0.jar` 到该目录。
3. 打开宝塔“Java 项目管理器”，点击“添加项目”：
   - **项目 Jar 路径**: 选择该 `.jar` 文件。
   - **项目端口**: `8080`。
   - **项目执行参数**: (关键优化，针对 2GB 内存)
     `-Xms256m -Xmx512m`
   - **自动启动**: 勾选。
4. 点击提交，确认项目状态为“运行中”。

### 2. 部署前端 (Nginx)
1. 在服务器 `/www/wwwroot/` 下创建目录 `network-monitor/frontend`。
2. 上传并解压 `dist.zip`。
3. 在宝塔“网站”中，点击“添加站点”：
   - **域名**: `你的服务器公网IP`
   - **根目录**: 选择刚才解压的 `dist` 目录。
   - **备注**: 校园网络监控前端。
   - **端口**: `3000`。

### 3. 配置 Nginx 反向代理 (解决 API 跨域)
在刚才创建的站点设置中，找到“反向代理” -> “添加反向代理”：
- **代理名称**: `api`
- **目标 URL**: `http://127.0.0.1:8080`
- **发送域名**: `$host`
- 点击保存。

---

## ⚠️ 关键性能调优 (针对 2GB 内存)

1. **Windows 虚拟内存 (必做)**:
   由于 Windows Server 本身占用较大，2GB 物理内存极易溢出。
   - 右键“此电脑” -> 属性 -> 高级系统设置 -> 性能设置 -> 高级 -> 虚拟内存。
   - 设置“托管的系统大小”或手动设置 `4096MB` 以上。

2. **数据库内存优化**:
   在宝塔 PostgreSQL 设置中，将 `shared_buffers` 调小至 `128MB`。

---

## ✅ 验证访问
- **前端地址**: `http://你的服务器IP:3000`
- **API 健康检查**: `http://你的服务器IP:8080/api-docs` (Swagger 文档)

祝部署顺利！
