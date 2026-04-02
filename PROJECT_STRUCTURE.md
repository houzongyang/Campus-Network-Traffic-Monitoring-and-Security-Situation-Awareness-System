# 项目结构

```
network-monitor/                               # 项目根目录
│
├── docker-compose.yml                         # Docker容器编排文件
├── README.md                                  # 项目主文档
├── DEPLOYMENT.md                              # 部署指南
├── ARCHITECTURE.md                            # 架构设计文档
├── .gitignore                                 # Git忽略文件配置
│
├── network-monitor-backend/                   # 后端项目
│   ├── pom.xml                                # Maven配置
│   ├── Dockerfile                             # Docker镜像配置
│   │
│   └── src/main/
│       ├── java/com/campus/network/
│       │   ├── NetworkMonitorApplication.java # Spring Boot主类
│       │   │
│       │   ├── controller/                    # REST API 控制层
│       │   │   ├── DashboardController.java   # 仪表板API
│       │   │   ├── FlowSearchController.java  # 流检索API
│       │   │   └── SecurityAlertsController.java # 安全告警API
│       │   │
│       │   ├── service/                       # 业务逻辑层
│       │   │   ├── FlowAnalysisService.java   # 流量分析
│       │   │   ├── ThreatDetectionService.java# 威胁检测
│       │   │   ├── AppIdentificationService.java # 应用识别
│       │   │   └── DataImportService.java     # 数据导入
│       │   │
│       │   ├── repository/                    # 数据访问层
│       │   │   ├── NetworkFlowRepository.java # 流数据DAO
│       │   │   └── SecurityAlertRepository.java # 告警数据DAO
│       │   │
│       │   ├── model/                         # 数据模型
│       │   │   ├── NetworkFlow.java           # 流记录实体
│       │   │   └── SecurityAlert.java         # 告警实体
│       │   │
│       │   └── config/                        # 配置文件
│       │       └── ApplicationStartupInitializer.java # 启动初始化器
│       │
│       └── resources/
│           └── application.yml                # Spring Boot配置
│
├── network-monitor-frontend/                  # 前端项目
│   ├── package.json                           # NPM配置
│   ├── vite.config.js                         # Vite构建配置
│   ├── index.html                             # 主HTML文件
│   ├── Dockerfile                             # Docker镜像配置
│   ├── nginx.conf                             # Nginx配置
│   │
│   └── src/
│       ├── main.jsx                           # React入口
│       ├── App.jsx                            # 主应用组件
│       ├── App.css                            # 应用样式
│       │
│       ├── styles/
│       │   └── global.css                     # 全局样式
│       │
│       ├── pages/                             # 页面组件
│       │   ├── Dashboard.jsx                  # 全景仪表板
│       │   ├── Dashboard.css
│       │   ├── SecurityCenter.jsx             # 安全中心
│       │   ├── SecurityCenter.css
│       │   ├── FlowSearch.jsx                 # 流检索页面
│       │   ├── FlowSearch.css
│       │   ├── IpDetails.jsx                  # IP详情页面
│       │   └── IpDetails.css
│       │
│       ├── components/                        # 可复用组件
│       │   ├── MetricsCard.jsx                # 指标卡片
│       │   ├── MetricsCard.css
│       │   ├── ChartCard.jsx                  # 图表卡片
│       │   ├── ChartCard.css
│       │   ├── TopFlowsTable.jsx              # 流量表格
│       │   └── TopFlowsTable.css
│       │
│       ├── api/
│       │   └── client.js                      # API客户端
│       │
│       └── store/
│           └── index.js                       # Zustand状态管理
│
└── docs/                                       # 文档（可选）
    ├── API.md                                 # API文档
    ├── FAQ.md                                 # 常见问题
    └── CONTRIBUTING.md                        # 贡献指南
```

## 代码统计

### 后端代码量
- Java源代码：约 2,500 行
- SQL脚本：约 200 行
- 配置文件：约 100 行
- 总计：约 2,800 行

### 前端代码量
- React JSX：约 1,500 行
- CSS样式：约 800 行
- JavaScript工具：约 300 行
- 总计：约 2,600 行

### 总代码量：约 5,400 行

## 关键文件速查

| 文件 | 位置 | 用途 |
|-----|------|------|
| pom.xml | backend/ | Maven依赖管理 |
| application.yml | backend/src/main/resources/ | Spring Boot配置 |
| NetworkFlow.java | backend/.../model/ | 流数据模型 |
| FlowAnalysisService.java | backend/.../service/ | 流量分析逻辑 |
| DashboardController.java | backend/.../controller/ | API端点 |
| App.jsx | frontend/src/ | React主应用 |
| Dashboard.jsx | frontend/src/pages/ | 仪表板页面 |
| client.js | frontend/src/api/ | API调用 |
| index.js | frontend/src/store/ | 状态管理 |
| docker-compose.yml | 项目根目录 | 容器编排 |
| README.md | 项目根目录 | 项目说明 |

