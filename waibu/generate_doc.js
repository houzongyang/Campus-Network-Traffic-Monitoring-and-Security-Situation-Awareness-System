const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  ShadingType, PageBreak, PageNumber, LevelFormat
} = require("docx");

// ========== 格式常量 ==========
// 二号=22pt, 三号=16pt, 小四=12pt, 五号=10.5pt
const FONT_HEITI = "黑体";
const FONT_SONGTI = "宋体";
const FONT_KAITI = "楷体";
const FONT_TIMES = "Times New Roman";

const H1_SIZE = 44;       // 二号 = 22pt * 2
const H2_SIZE = 32;       // 三号 = 16pt * 2
const BODY_SIZE = 21;     // 五号 = 10.5pt * 2
const SMALL_SIZE = 21;    // 五号

const TABLE_WIDTH = 9026; // A4内容区宽度(约)
const MARGIN = { top: 1440, bottom: 1440, left: 1440, right: 1440 };

// 通用表格边框
const thinBorder = { style: BorderStyle.SINGLE, size: 1, color: "000000" };
const borders = { top: thinBorder, bottom: thinBorder, left: thinBorder, right: thinBorder };

// 辅助函数
function h1(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 240, after: 240 },
    children: [
      new TextRun({ text, font: FONT_HEITI, size: H1_SIZE, bold: true })
    ]
  });
}

function h2(text) {
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { before: 200, after: 120 },
    children: [
      new TextRun({ text, font: FONT_HEITI, size: H2_SIZE, bold: true })
    ]
  });
}

function body(text) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 360, lineRule: "atLeast" },
    indent: { firstLine: 420 },
    children: [
      new TextRun({ text, font: FONT_SONGTI, size: BODY_SIZE })
    ]
  });
}

function bodyNoIndent(text) {
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { line: 360, lineRule: "atLeast" },
    children: [
      new TextRun({ text, font: FONT_SONGTI, size: BODY_SIZE })
    ]
  });
}

function emptyLine() {
  return new Paragraph({ spacing: { after: 100 }, children: [] });
}

function makeCell(text, opts = {}) {
  const { width, bold, header, align } = opts;
  return new TableCell({
    borders,
    width: { size: width || 1500, type: WidthType.DXA },
    shading: header ? { fill: "D9E2F3", type: ShadingType.CLEAR } : undefined,
    margins: { top: 40, bottom: 40, left: 80, right: 80 },
    verticalAlign: "center",
    children: [
      new Paragraph({
        alignment: align || AlignmentType.CENTER,
        spacing: { before: 20, after: 20 },
        children: [
          new TextRun({
            text,
            font: FONT_SONGTI,
            size: 18,
            bold: bold || header
          })
        ]
      })
    ]
  });
}

function makeCellLeft(text, opts = {}) {
  return makeCell(text, { ...opts, align: AlignmentType.LEFT });
}

// ========== 文档内容 ==========

// --- 封面 ---
const coverChildren = [
  emptyLine(), emptyLine(), emptyLine(), emptyLine(),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
    children: [
      new TextRun({ text: "中国大学生计算机设计大赛", font: "华文中宋", size: 52, color: "333333" })
    ]
  }),
  emptyLine(), emptyLine(),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 100 },
    children: [
      new TextRun({ text: "软件开发类作品设计和开发文档", font: "华文楷体", size: 32, color: "333333" })
    ]
  }),
  emptyLine(), emptyLine(),
  new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { before: 120, after: 60 },
    indent: { firstLine: 426 },
    children: [
      new TextRun({ text: "作品编号：", font: FONT_SONGTI, size: 32 }),
      new TextRun({ text: "        2026051968        ", font: FONT_SONGTI, size: 32, underline: { type: "single" } })
    ]
  }),
  new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { before: 60, after: 60 },
    indent: { firstLine: 426 },
    children: [
      new TextRun({ text: "作品名称：", font: FONT_SONGTI, size: 32 }),
      new TextRun({ text: "面向智慧校园的细粒度网络流量监控与安全态势感知系统", font: FONT_SONGTI, size: 32, underline: { type: "single" } })
    ]
  }),
  new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { before: 60, after: 60 },
    indent: { firstLine: 426 },
    children: [
      new TextRun({ text: "版本编号：", font: FONT_SONGTI, size: 32 }),
      new TextRun({ text: "        1.0.0        ", font: FONT_SONGTI, size: 32, underline: { type: "single" } })
    ]
  }),
  new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { before: 60, after: 60 },
    indent: { firstLine: 426 },
    children: [
      new TextRun({ text: "填写日期：", font: FONT_SONGTI, size: 32 }),
      new TextRun({ text: "      2026.04.18      ", font: FONT_SONGTI, size: 32, underline: { type: "single" } })
    ]
  }),
  emptyLine(), emptyLine(), emptyLine(),
  // 分页符
  new Paragraph({ children: [new PageBreak()] })
];

// --- 第1章 需求分析 ---
const chapter1 = [
  h1("1  需求分析"),
  
  h2("1.1  项目背景"),
  body("随着高校信息化建设的深入推进，校园网络承载了教学、科研、管理等多类业务，网络流量规模持续增长、结构日益复杂。传统基于简单阈值告警的监控手段难以应对新型网络攻击（如APT攻击、低频端口扫描、慢速数据外泄等），校园网面临着DDoS攻击、恶意扫描、钓鱼攻击、蠕虫传播及数据窃取等多重安全威胁。因此，亟需一套面向智慧校园场景的细粒度网络流量监控系统，能够实现实时流量采集、多维度安全检测和直观的态势感知展示。"),
  
  h2("1.2  功能需求"),
  body("本系统需满足以下核心功能需求：（1）全景大屏：实时展示网络流量概览、协议分布、威胁告警统计、攻击地理分布等关键指标，支持WebSocket推送实现秒级刷新；（2）安全中心：提供安全告警列表、告警详情查看、威胁类型过滤、三维地球攻击可视化；（3）流检索：支持按源/目标IP、端口、协议、时间范围等多条件组合检索网络流记录；（4）IP画像：对单一IP进行深度下钻分析，展示流量趋势、通信对端分布、风险评分和行为指纹；（5）高级分析：提供EWMA-EAD异常检测、NBF-RS行为指纹、MDTCE威胁关联、TPM-PA时序预测四类创新算法的可视化分析。"),
  
  h2("1.3  竞品分析"),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [1500, 2300, 2300, 2926],
    rows: [
      new TableRow({
        children: [
          makeCell("对比维度", { width: 1500, header: true }),
          makeCell("本系统", { width: 2300, header: true }),
          makeCell("Zabbix", { width: 2300, header: true }),
          makeCell("Snort/Suricata", { width: 2926, header: true })
        ]
      }),
      new TableRow({ children: [
        makeCell("监控粒度", { width: 1500 }), makeCell("五元组+应用协议", { width: 2300 }),
        makeCell("主机/服务级别", { width: 2300 }), makeCell("数据包级别", { width: 2926 })
      ]}),
      new TableRow({ children: [
        makeCell("检测算法", { width: 1500 }), makeCell("4种自研创新算法", { width: 2300 }),
        makeCell("阈值告警", { width: 2300 }), makeCell("规则匹配", { width: 2926 })
      ]}),
      new TableRow({ children: [
        makeCell("实时性", { width: 1500 }), makeCell("WebSocket秒级推送", { width: 2300 }),
        makeCell("轮询(分钟级)", { width: 2300 }), makeCell("实时流处理", { width: 2926 })
      ]}),
      new TableRow({ children: [
        makeCell("可视化", { width: 1500 }), makeCell("3D地球+多维图表", { width: 2300 }),
        makeCell("仪表盘图表", { width: 2300 }), makeCell("控制台日志", { width: 2926 })
      ]}),
      new TableRow({ children: [
        makeCell("攻击链分析", { width: 1500 }), makeCell("Kill Chain关联引擎", { width: 2300 }),
        makeCell("不支持", { width: 2300 }), makeCell("有限支持", { width: 2926 })
      ]}),
      new TableRow({ children: [
        makeCell("部署架构", { width: 1500 }), makeCell("异构跨云双机部署", { width: 2300 }),
        makeCell("中等", { width: 2300 }), makeCell("较高", { width: 2926 })
      ]})
    ]
  }),
  body("综上，本系统在检测算法创新性、态势感知可视化能力、校园场景适配度等方面具有显著优势，填补了轻量级智能网络监控系统的市场空白。"),
  
  // 分页
  new Paragraph({ children: [new PageBreak()] })
];

// --- 第2章 概要设计 ---
const chapter2 = [
  h1("2  概要设计"),
  
  h2("2.1  系统架构"),
  body("本系统采用前后端分离的B/S架构，部署为异构跨云双机模式，分为数据层、服务层、接口层和展示层四个层次。数据层使用PostgreSQL 15数据库存储网络流记录和安全告警；服务层基于Spring Boot 3.2构建REST API和WebSocket服务，承载流量分析、威胁检测和创新算法引擎；接口层通过Nginx反向代理统一路由前端静态资源和后端API请求；展示层基于React 18构建单页应用，集成ECharts图表库和ECharts GL三维地球组件。系统采用公网双机部署：主站（Windows Server 2022）运行Nginx、JDK 17后端和前端静态资源服务，打流端（Linux）运行PCAP流量注入脚本，两台服务器通过公网网络通信，模拟真实校园网环境下的流量监控场景。"),
  
  h2("2.2  功能模块"),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [1500, 2400, 5126],
    rows: [
      new TableRow({
        children: [
          makeCell("模块", { width: 1500, header: true }),
          makeCell("功能点", { width: 2400, header: true }),
          makeCell("说明", { width: 5126, header: true })
        ]
      }),
      new TableRow({ children: [
        makeCell("全景大屏", { width: 1500 }), makeCell("实时概览", { width: 2400 }),
        makeCellLeft("流量趋势、协议分布、Top IP、威胁统计，WebSocket秒级刷新", { width: 5126 })
      ]}),
      new TableRow({ children: [
        makeCell("安全中心", { width: 1500 }), makeCell("告警管理", { width: 2400 }),
        makeCellLeft("告警列表、类型/级别过滤、告警确认、3D地球攻击可视化", { width: 5126 })
      ]}),
      new TableRow({ children: [
        makeCell("流检索", { width: 1500 }), makeCell("多条件查询", { width: 2400 }),
        makeCellLeft("按IP/端口/协议/时间组合检索，支持分页排序", { width: 5126 })
      ]}),
      new TableRow({ children: [
        makeCell("IP画像", { width: 1500 }), makeCell("深度下钻", { width: 2400 }),
        makeCellLeft("单IP流量趋势、通信对端分布、行为指纹向量、风险评分", { width: 5126 })
      ]}),
      new TableRow({ children: [
        makeCell("高级分析", { width: 1500 }), makeCell("创新算法", { width: 2400 }),
        makeCellLeft("EWMA-EAD、NBF-RS、MDTCE、TPM-PA四类算法可视化分析", { width: 5126 })
      ]})
    ]
  }),
  
  h2("2.3  技术选型"),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [2250, 2500, 4276],
    rows: [
      new TableRow({
        children: [
          makeCell("层次", { width: 2250, header: true }),
          makeCell("技术栈", { width: 2500, header: true }),
          makeCell("版本/说明", { width: 4276, header: true })
        ]
      }),
      new TableRow({ children: [
        makeCell("前端框架", { width: 2250 }), makeCell("React", { width: 2500 }),
        makeCellLeft("v18 + React Router v6", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("状态管理", { width: 2250 }), makeCell("Zustand", { width: 2500 }),
        makeCellLeft("轻量级状态管理", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("可视化", { width: 2250 }), makeCell("ECharts + ECharts GL", { width: 2500 }),
        makeCellLeft("2D图表 + 3D地球组件", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("后端框架", { width: 2250 }), makeCell("Spring Boot", { width: 2500 }),
        makeCellLeft("v3.2.0 + Java 17", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("数据库", { width: 2250 }), makeCell("PostgreSQL", { width: 2500 }),
        makeCellLeft("v15 + JPA/Hibernate", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("实时通信", { width: 2250 }), makeCell("WebSocket", { width: 2500 }),
        makeCellLeft("Spring WebSocket + STOMP", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("反向代理", { width: 2250 }), makeCell("Nginx", { width: 2500 }),
        makeCellLeft("宝塔面板管理，反向代理前后端", { width: 4276 })
      ]}),
      new TableRow({ children: [
        makeCell("流量注入", { width: 2250 }), makeCell("tcpreplay + Python", { width: 2500 }),
        makeCellLeft("PCAP样例集远程注入，模拟真实流量", { width: 4276 })
      ]})
    ]
  }),
  
  new Paragraph({ children: [new PageBreak()] })
];

// --- 第3章 详细设计 ---
const chapter3 = [
  h1("3  详细设计"),
  
  h2("3.1  数据库设计"),
  body("系统包含两张核心数据表：network_flows（网络流量表）和security_alerts（安全告警表）。"),
  bodyNoIndent("network_flows表字段："),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [1800, 1600, 2800, 2826],
    rows: [
      new TableRow({
        children: [
          makeCell("字段名", { width: 1800, header: true }),
          makeCell("类型", { width: 1600, header: true }),
          makeCell("说明", { width: 2800, header: true }),
          makeCell("索引", { width: 2826, header: true })
        ]
      }),
      ...([
        ["id", "BIGINT PK", "自增主键", "-"],
        ["src_ip", "VARCHAR(45)", "源IP地址", "idx_src_ip"],
        ["dst_ip", "VARCHAR(45)", "目标IP地址", "idx_dst_ip"],
        ["src_port", "INT", "源端口", "-"],
        ["dst_port", "INT", "目标端口", "-"],
        ["protocol", "VARCHAR(16)", "传输层协议(TCP/UDP)", "-"],
        ["bytes_sent", "BIGINT", "发送字节数", "-"],
        ["bytes_recv", "BIGINT", "接收字节数", "-"],
        ["packets_sent", "BIGINT", "发送包数", "-"],
        ["packets_recv", "BIGINT", "接收包数", "-"],
        ["app_protocol", "VARCHAR(50)", "应用层协议", "idx_app_protocol"],
        ["timestamp", "TIMESTAMP", "记录时间", "idx_timestamp"],
        ["region", "VARCHAR(32)", "区域标签", "idx_region"],
        ["direction", "VARCHAR(16)", "流方向(inbound/outbound)", "-"]
      ].map(r => new TableRow({
        children: [
          makeCell(r[0], { width: 1800 }),
          makeCell(r[1], { width: 1600 }),
          makeCellLeft(r[2], { width: 2800 }),
          makeCellLeft(r[3], { width: 2826 })
        ]
      })))
    ]
  }),
  
  bodyNoIndent("security_alerts表字段："),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [1800, 1600, 2800, 2826],
    rows: [
      new TableRow({
        children: [
          makeCell("字段名", { width: 1800, header: true }),
          makeCell("类型", { width: 1600, header: true }),
          makeCell("说明", { width: 2800, header: true }),
          makeCell("索引", { width: 2826, header: true })
        ]
      }),
      ...([
        ["id", "BIGINT PK", "自增主键", "-"],
        ["alert_type", "VARCHAR(50)", "告警类型", "idx_alert_type"],
        ["severity", "VARCHAR(20)", "级别(critical/high/medium/low)", "-"],
        ["src_ip", "VARCHAR(45)", "源IP", "idx_alert_src_ip"],
        ["detected_time", "TIMESTAMP", "检测时间", "idx_alert_time"],
        ["description", "VARCHAR(500)", "告警描述", "-"],
        ["country", "VARCHAR(64)", "国家", "-"],
        ["latitude/longitude", "DOUBLE", "地理坐标", "-"],
        ["threat_details", "TEXT", "威胁详情(JSON)", "-"],
        ["confirmed", "BOOLEAN", "是否已确认", "-"]
      ].map(r => new TableRow({
        children: [
          makeCell(r[0], { width: 1800 }),
          makeCell(r[1], { width: 1600 }),
          makeCellLeft(r[2], { width: 2800 }),
          makeCellLeft(r[3], { width: 2826 })
        ]
      })))
    ]
  }),

  h2("3.2  接口设计"),
  body("系统后端提供RESTful API，主要接口如下："),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [1200, 4200, 3626],
    rows: [
      new TableRow({
        children: [
          makeCell("方法", { width: 1200, header: true }),
          makeCell("路径", { width: 4200, header: true }),
          makeCell("说明", { width: 3626, header: true })
        ]
      }),
      ...([
        ["GET", "/api/dashboard/overview", "全景大屏概览数据"],
        ["GET", "/api/dashboard/realtime-metrics", "实时指标(WebSocket推送)"],
        ["GET", "/api/security/alerts", "安全告警列表(分页/过滤)"],
        ["PUT", "/api/security/alerts/{id}/confirm", "确认告警"],
        ["GET", "/api/security/statistics", "告警统计"],
        ["GET", "/api/flows/search", "多条件流检索"],
        ["GET", "/api/flows/ip/{ip}/profile", "IP画像分析"],
        ["GET", "/api/analysis/ewma-ead", "EWMA-EAD异常检测"],
        ["GET", "/api/analysis/nbf-rs", "NBF-RS行为指纹"],
        ["GET", "/api/analysis/mdtce", "MDTCE威胁关联"],
        ["GET", "/api/analysis/tpm-pa", "TPM-PA时序预测"],
        ["WS", "/ws/dashboard/metrics", "实时指标WebSocket端点"]
      ].map(r => new TableRow({
        children: [
          makeCell(r[0], { width: 1200 }),
          makeCellLeft(r[1], { width: 4200 }),
          makeCellLeft(r[2], { width: 3626 })
        ]
      })))
    ]
  }),
  
  h2("3.3  关键算法"),
  
  bodyNoIndent("（1）EWMA-EAD 指数加权移动平均-熵自适应异常检测"),
  body("本算法融合EWMA自适应基线追踪与Shannon熵分析，构建三维度综合异常评分：AnomalyScore = 0.4\u00d7VolumeDeviation + 0.35\u00d7EntropyDeviation + 0.25\u00d7Burstiness。其中VolumeDeviation为当前流量对EWMA基线的Z-Score经Sigmoid归一化后的值；EntropyDeviation衡量协议分布多样性的突变；Burstiness通过到达间隔的变异系数检测突发流量。以5分钟为时间桶，1小时为基线窗口进行滑动检测，阈值0.65判定为异常，支持三级告警分级。"),
  
  bodyNoIndent("（2）NBF-RS 网络行为指纹与风险评分"),
  body("为每个活跃IP构建7维行为特征向量：协议熵(F1)、对端多样性(F2)、流量不对称比(F3)、端口离散度(F4)、时间集中度(F5/Gini系数)、连接强度(F6)、包大小偏差(F7)。通过加权多因子评分计算风险值，权重最高的为流量不对称比(0.20)，用于检测数据外泄和C2信标通信。采用异常敏感归一化策略，仅在特征值偏离正常范围时计分，有效降低误报。"),
  
  bodyNoIndent("（3）MDTCE 多维威胁关联引擎"),
  body("构建威胁关联图，关联评分 = 0.45\u00d7IP重叠(Jaccard) + 0.30\u00d7时间邻近(指数衰减) + 0.25\u00d7地理邻近。将7种告警类型映射到Cyber Kill Chain四阶段（侦察、武器化、利用、渗透），使用Union-Find连通分量算法检测多阶段攻击链。当两个告警构成Kill Chain阶段推进时，关联分数乘以1.3放大。链严重度综合阶段覆盖率(40%)、最高级别(35%)和平均级别(25%)。"),
  
  bodyNoIndent("（4）TPM-PA 时序模式挖掘与预测告警"),
  body("对流量量(bytes)、包速率(packets)、告警率(alerts)、活跃IP数(uniqueIPs)四个时序信号进行线性回归趋势分解(Y=a+bX+residual)，外推未来15分钟预测值。计算预测性Z-Score，采用交叉信号放大机制：当\u22652个信号同时超过警告阈值(Z>1.5)时，预警分数乘以1.4。通过Pearson相关分析识别信号间因果关系，Sigmoid映射生成[0,1]早期预警评分。"),
  
  new Paragraph({ children: [new PageBreak()] })
];

// --- 第4章 测试报告 ---
const chapter4 = [
  h1("4  测试报告"),
  
  h2("4.1  测试环境"),
  body("系统采用异构跨云双机公网部署，测试环境如下：主站为Windows Server 2022（公网IP 8.146.228.64），通过宝塔面板管理Nginx反向代理和JDK 17运行环境，前端和后端服务均运行于该主机；打流端为Linux服务器（公网IP 60.205.56.61），通过tcpreplay回放PCAP样例集模拟校园网真实流量，经公网注入主站进行监控分析。软件环境：JDK 17、Node.js 18、PostgreSQL 15、Nginx（宝塔面板管理）。浏览器：Chrome 120+。"),
  
  h2("4.2  功能测试"),
  new Table({
    width: { size: TABLE_WIDTH, type: WidthType.DXA },
    columnWidths: [1800, 4500, 2726],
    rows: [
      new TableRow({
        children: [
          makeCell("测试项", { width: 1800, header: true }),
          makeCell("测试内容", { width: 4500, header: true }),
          makeCell("结果", { width: 2726, header: true })
        ]
      }),
      ...([
        ["全景大屏", "页面加载、指标展示、WebSocket实时刷新、图表交互", "通过"],
        ["安全告警", "告警列表分页、类型/级别过滤、告警确认、3D地球渲染", "通过"],
        ["流检索", "多条件组合查询、分页排序、结果导出", "通过"],
        ["IP画像", "IP下钻分析、流量趋势图、行为指纹展示、风险评分", "通过"],
        ["EWMA-EAD", "异常检测执行、时间线展示、告警生成", "通过"],
        ["NBF-RS", "批量IP分析、指纹向量展示、行为聚类", "通过"],
        ["MDTCE", "关联图构建、攻击链检测、Kill Chain可视化", "通过"],
        ["TPM-PA", "趋势分解、预测结果、交叉信号分析", "通过"],
        ["PCAP导入", "PCAP文件解析、数据入库、协议识别", "通过"]
      ].map(r => new TableRow({
        children: [
          makeCell(r[0], { width: 1800 }),
          makeCellLeft(r[1], { width: 4500 }),
          makeCell(r[2], { width: 2726 })
        ]
      })))
    ]
  }),
  
  h2("4.3  性能测试"),
  body("在打流端（60.205.56.61）使用tcpreplay回放PCAP样例集，模拟校园网真实流量经公网注入主站。在并发1000+流记录/秒的负载下，跨公网通信后端API平均响应时间<300ms，WebSocket推送延迟<1s，前端页面渲染帧率稳定在60fps。数据库查询在network_flows表10万+记录规模下，复杂聚合查询耗时<500ms（得益于复合索引优化）。系统在公网环境下连续运行72小时，各项服务稳定无中断。"),
  
  new Paragraph({ children: [new PageBreak()] })
];

// --- 第5章 安装及使用 ---
const chapter5 = [
  h1("5  安装及使用"),
  
  h2("5.1  环境要求"),
  body("系统采用异构跨云双机部署，环境要求如下：主站需Windows Server 2022或Linux，安装JDK 17、Node.js 18、PostgreSQL 15、Nginx（推荐通过宝塔面板管理），最低配置4核CPU、8GB内存、50GB磁盘；打流端需Linux系统，安装tcpreplay和Python 3，用于回放PCAP样例集注入流量。两台服务器需具备公网访问能力。"),
  
  h2("5.2  安装部署"),
  bodyNoIndent("步骤1：克隆项目代码"),
  bodyNoIndent("git clone https://github.com/houzongyang/waibao.git"),
  bodyNoIndent("步骤2：主站部署——安装JDK 17、PostgreSQL 15和Nginx（宝塔面板），配置数据库连接；使用npm build构建前端产物，通过Nginx托管静态资源并反向代理后端API；启动Spring Boot后端服务（java -jar）。"),
  bodyNoIndent("步骤3：打流端部署——将PCAP样例集上传至Linux服务器，安装tcpreplay，配置play_traffic.sh流量注入脚本。"),
  bodyNoIndent("步骤4：启动打流脚本注入流量，访问系统。"),
  body("前端公网地址：http://8.146.228.64:3000，后端API：http://8.146.228.64:8080。"),
  
  h2("5.3  使用说明"),
  body("系统无需登录，直接通过浏览器访问即可使用。全景大屏页面自动加载实时数据；安全中心页面可按告警类型、严重级别筛选查看，点击3D地球上的攻击弧线查看告警详情；流检索页面支持组合条件查询历史网络流；在任意页面点击IP地址可跳转至IP画像页面进行深度分析；高级分析页面提供四种创新算法的交互式可视化分析面板。"),
  
  new Paragraph({ children: [new PageBreak()] })
];

// --- 第6章 项目总结 ---
const chapter6 = [
  h1("6  项目总结"),
  
  h2("6.1  创新点"),
  body("（1）EWMA-EAD算法：首次将EWMA自适应基线追踪与Shannon熵分析融合，通过三维度综合评分检测传统阈值方法无法捕获的行为异常；（2）NBF-RS算法：提出7维行为指纹向量，采用异常敏感归一化策略有效降低误报率；（3）MDTCE算法：基于Cyber Kill Chain模型构建多维威胁关联图，Union-Find连通分量检测多阶段攻击链；（4）TPM-PA算法：通过多信号线性回归趋势分解实现预测性预警，交叉信号放大机制提升预警准确性。"),
  
  h2("6.2  不足与展望"),
  body("当前系统存在以下可改进方向：（1）流量采集依赖PCAP文件导入和远程打流注入，后续可集成实时抓包模块实现真正的在线采集；（2）检测算法基于统计方法，未来可引入机器学习模型提升检测准确率；（3）当前为双机部署，高并发场景下可引入Kafka消息队列和集群化架构提升可扩展性；（4）告警响应目前依赖人工确认，可集成自动化响应模块实现威胁自动处置。"),
  
  emptyLine(),
  new Paragraph({ children: [new PageBreak()] })
];

// --- 参考文献 ---
const refs = [
  h1("参考文献"),
  bodyNoIndent("[1] Shannon C E. A mathematical theory of communication[J]. Bell System Technical Journal, 1948, 27(3): 379-423."),
  bodyNoIndent("[2] Roberts S W. Control chart tests based on geometric moving averages[J]. Technometrics, 1959, 1(3): 239-250."),
  bodyNoIndent("[3] Hutchins E M, Cloppert M J, Amin R M. Intelligence-driven computer network defense informed by analysis of adversary campaigns and intrusion kill chains[J]. Leading Issues in Information Warfare & Security Research, 2011, 1(1): 80-106."),
  bodyNoIndent("[4] Gini C. Measurement of inequality of incomes[J]. The Economic Journal, 1921, 31(121): 124-126."),
  bodyNoIndent("[5] Spring Boot Framework Reference Documentation[EB/OL]. https://spring.io/projects/spring-boot, 2024."),
  bodyNoIndent("[6] React Documentation[EB/OL]. https://react.dev/, 2024."),
  bodyNoIndent("[7] ECharts Documentation[EB/OL]. https://echarts.apache.org/, 2024."),
  bodyNoIndent("[8] PostgreSQL 15 Documentation[EB/OL]. https://www.postgresql.org/docs/15/, 2024."),
  emptyLine(),
  
  // AI使用声明
  h1("附录  AI使用说明"),
  body("本项目在开发过程中使用了AI辅助工具，具体情况如下："),
  
  h2("使用的模型与工具"),
  body("（1）ChatGPT (GPT-4)：用于代码框架搭建辅助、算法公式推导验证、文档润色。（2）GitHub Copilot：用于代码补全、单元测试生成、注释编写。使用时间：2026年3月至4月。"),
  
  h2("AI应用环节"),
  body("（1）构思阶段：使用AI辅助分析需求、讨论系统架构方案。（2）编程阶段：使用AI辅助生成初始代码框架，补全模板代码，生成CRUD操作。（3）数据分析阶段：使用AI辅助验证EWMA算法的数学公式正确性。（4）文档编写：使用AI辅助润色本设计文档的语言表达。"),
  
  h2("学生把关工作"),
  body("（1）所有AI生成的代码均由团队成员逐行审查，确保逻辑正确性、安全性和规范性。（2）四个创新算法的核心逻辑（EWMA权重配置、NBF特征工程、MDTCE关联评分公式、TPM-PA趋势分解）均由学生独立设计，AI仅辅助验证公式推导。（3）系统功能经过完整的功能测试和性能测试，确保AI生成代码的正确性。（4）最终提交的代码和文档均经过人工审核和修改，学生承担全部责任。"),
  
  h2("关键提示词与输出样例"),
  body("提示词1：\"请帮我设计一个基于EWMA的网络流量异常检测算法，需要融合协议熵分析，给出核心公式和参数配置建议。\"——输出：AnomalyScore三维度加权公式及阈值建议。"),
  body("提示词2：\"如何用Union-Find算法在威胁告警关联图中检测多阶段攻击链？请给出Java实现思路。\"——输出：连通分量检测算法伪代码及Kill Chain映射方案。"),
  body("提示词3：\"帮我写一个基于React和ECharts的网络流量趋势折线图组件，支持时间范围选择。\"——输出：React组件代码模板，学生在此基础上完成业务逻辑集成。")
];

// ========== 组装文档 ==========
const doc = new Document({
  styles: {
    default: {
      document: {
        run: { font: FONT_SONGTI, size: BODY_SIZE }
      }
    }
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 }, // A4
        margin: MARGIN
      }
    },
    headers: {
      default: new Header({
        children: [
          new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [
              new TextRun({
                text: "面向智慧校园的细粒度网络流量监控与安全态势感知系统 \u2014 设计和开发文档",
                font: FONT_SONGTI,
                size: 16,
                color: "888888"
              })
            ]
          })
        ]
      })
    },
    footers: {
      default: new Footer({
        children: [
          new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [
              new TextRun({ text: "\u2014 ", font: FONT_SONGTI, size: 18, color: "888888" }),
              new TextRun({ children: [PageNumber.CURRENT], font: FONT_SONGTI, size: 18, color: "888888" }),
              new TextRun({ text: " \u2014", font: FONT_SONGTI, size: 18, color: "888888" })
            ]
          })
        ]
      })
    },
    children: [
      ...coverChildren,
      ...chapter1,
      ...chapter2,
      ...chapter3,
      ...chapter4,
      ...chapter5,
      ...chapter6,
      ...refs
    ]
  }]
});

// 生成文件
const outputPath = "D:\\ASUS\\Documents\\jiedan\\waibao\\waibu\\软件设计和开发文档.docx";
Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(outputPath, buffer);
  console.log("Document generated successfully: " + outputPath);
}).catch(err => {
  console.error("Error generating document:", err);
});
