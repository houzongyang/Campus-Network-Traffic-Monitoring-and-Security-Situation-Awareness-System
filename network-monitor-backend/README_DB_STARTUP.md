# 后端数据库启动配置说明

后端启动时如果出现 `Unable to open JDBC Connection for DDL execution`，并伴随
`org.postgresql.util.PSQLException: 用户 "postgres" Password 认证失败`，根因通常是
Spring Boot 读取到的 PostgreSQL 用户名/密码与本机或 Docker 中数据库实际密码不一致。

## 本地直接启动后端

不要把真实密码写入 `application.yml`。请在当前终端设置环境变量后启动：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/network_monitor"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="password"
mvn spring-boot:run
```

如果使用根目录 `docker-compose.yml` 单独提供 PostgreSQL，默认宿主机端口是 `55432`：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/network_monitor"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="你的POSTGRES_PASSWORD"
mvn spring-boot:run
```

## Docker Compose 启动整套系统

在项目根目录创建不提交 Git 的 `.env` 文件：

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=请替换为你自己的数据库密码
POSTGRES_DB=network_monitor
POSTGRES_HOST_PORT=55432
```

然后执行：

```powershell
docker compose up --build
```

## 与算法包迁移的关系

四套算法迁移到 `com.campus.network.algorithm` 只影响 Java 包路径和 Spring Bean 扫描；
该包位于 `com.campus.network` 根包下，仍会被 `@SpringBootApplication` 自动扫描。
上述 PostgreSQL 认证失败发生在 `entityManagerFactory` 初始化数据库连接阶段，
与算法包迁移无直接关系。
