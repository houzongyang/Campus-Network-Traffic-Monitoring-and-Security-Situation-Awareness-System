# UI 迁移说明

## 项目信息
- 技术栈：React 18 + Vite 5 + ECharts 5 + ECharts-GL + Zustand + Axios
- 风格：深色科技大屏，主色调深海蓝 + 蓝绿荧光（`#5dd6ff`）

---

## 文件清单（ui-extract.zip 内容）

```
src/
├── main.jsx                    # React 挂载入口
├── App.jsx                     # 根组件 + 顶部导航
├── App.css                     # 导航栏样式（毛玻璃效果）
├── styles/
│   └── global.css              # ★ 全局设计系统（CSS变量/按钮/表格/栅格）
├── api/
│   └── client.js               # Axios HTTP 客户端 + 所有 API 封装
├── store/
│   └── index.js                # Zustand 全局状态（含 WebSocket 自动重连）
├── components/
│   ├── ChartCard.jsx/.css      # ECharts 图表容器（通用）
│   ├── MetricsCard.jsx/.css    # 数字指标卡（5种 accent 颜色）
│   ├── TopFlowsTable.jsx/.css  # Top-K 大流表格
│   ├── GlobeChart.jsx/.css     # 3D 地球攻击态势图（echarts-gl）
│   ├── projection.js           # 经纬度投影工具
│   └── world.js                # 世界地图 GeoJSON 数据（~987KB）
└── pages/
    ├── Dashboard.jsx/.css      # 全景画像页（含下钻柱状图 / 热力图）
    ├── SecurityCenter.jsx/.css # 安全态势感知页（含 3D 地球）
    ├── FlowSearch.jsx/.css     # 微观流级检索页
    └── IpDetails.jsx/.css      # 单 IP 详情页
index.html                      # HTML 入口
package.json                    # 依赖声明
vite.config.js                  # 构建 + 代理配置
```

---

## 核心依赖（需在目标项目安装）

```bash
npm install react react-dom axios echarts echarts-gl zustand
npm install -D vite @vitejs/plugin-react
```

---

## 迁移到其他项目的步骤

### 1. 只迁移视觉样式（CSS 设计系统）

如果只想复用颜色/圆角/布局体系：
- 复制 `src/styles/global.css` 到目标项目
- 引入即可获得完整 CSS 变量和通用组件类

### 2. 迁移单个组件

每个组件是**独立的**，可按需拷贝：

| 组件 | 需要的文件 | 外部依赖 |
|------|-----------|---------|
| MetricsCard | `MetricsCard.jsx` + `MetricsCard.css` | 无 |
| ChartCard | `ChartCard.jsx` + `ChartCard.css` | `echarts` |
| TopFlowsTable | `TopFlowsTable.jsx` + `TopFlowsTable.css` | 无 |
| GlobeChart | `GlobeChart.jsx` + `GlobeChart.css` + `world.js` + `projection.js` | `echarts` + `echarts-gl` |

> **注意**：所有组件都依赖 `global.css` 中的 CSS 变量（`--teal`, `--panel`, `--line` 等）。  
> 迁移组件时**必须同时引入** `global.css`，否则样式会缺失。

### 3. 迁移整个前端项目

1. 将 `ui-extract.zip` 解压到目标目录
2. 执行 `npm install`
3. 修改 `src/api/client.js` 中的接口地址（或通过 `.env` 配置 `VITE_API_URL`）
4. 修改 `src/store/index.js` 中的数据结构（对齐新后端的 API 响应格式）
5. 修改 `App.jsx` 中的标题和导航文案
6. 执行 `npm run dev` 启动开发服务器

---

## API 对接说明

修改 `src/api/client.js`，对齐目标后端接口。目前封装了三组 API：

```js
// 仪表盘
dashboardAPI.getOverview()        // GET /api/dashboard/overview
dashboardAPI.getTopFlows()        // GET /api/dashboard/top-flows
dashboardAPI.getRegionHierarchy() // GET /api/dashboard/region-hierarchy
dashboardAPI.getThroughputTrend() // GET /api/dashboard/throughput-trend

// 流检索
flowAPI.search(params)            // POST /api/flows/search
flowAPI.getIpProfile(ip)          // GET /api/flows/ip-profile/{ip}

// 安全告警
securityAPI.getAlerts(params)     // GET /api/security/alerts
securityAPI.getAlertStatistics()  // GET /api/security/alert-statistics
securityAPI.getGeoDistribution()  // GET /api/security/geo-distribution
```

WebSocket 实时推送：`ws://{host}/ws/dashboard/metrics`  
消息格式：`{ type: "dashboard_metrics", metrics: { throughputMbps, pps, activeIps, ... } }`

---

## 全局 CSS 变量速查

```css
--bg: #07111f          /* 页面背景 */
--panel: rgba(...)     /* 卡片面板 */
--teal: #5dd6ff        /* 主题蓝绿色 */
--amber: #ffb454       /* 警告橙色 */
--red: #ff6b6b         /* 危险红色 */
--green: #59d47c       /* 成功绿色 */
--violet: #9e8cff      /* 紫色 */
--text: #ecf2ff        /* 正文颜色 */
--muted: #9fb0d9       /* 次要文字 */
--radius-lg: 28px      /* 大圆角 */
--radius-md: 18px
--radius-sm: 12px
```

---

## 注意事项

1. **world.js 很大（~987KB）**，GlobeChart 强依赖它。如果不用 3D 地球，可删除 `GlobeChart.jsx` 和 `world.js`。
2. **echarts-gl** 约 1.5MB，仅 GlobeChart 使用。不需要 3D 地球时去掉此依赖。
3. SecurityCenter 会请求外网 `https://echarts.apache.org/examples/data-gl/asset/data/flights.json`（航线背景），内网环境请求会失败但组件做了容错处理不影响功能。
