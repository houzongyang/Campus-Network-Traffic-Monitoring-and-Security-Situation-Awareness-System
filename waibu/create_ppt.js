const pptxgen = require("pptxgenjs");

// ============================================
// 配置
// ============================================
const OUTPUT_FILE = "T2601487—无敌暴龙战队—【A19】面向智慧校园的细粒度网络流量监控与安全态势感知系统【苏州大学】—项目简介PPT.pptx";
const SCHOOL_CN = "苏州大学";
const TEAM_NAME = "无敌暴龙战队";
const TEAM_ID = "T2601487";
const PROJECT_CODE = "A19";

// 配色方案 - Ocean Gradient + Teal (科技网络安全主题)
const COLORS = {
  primary: "0F2A4A",      // 深海蓝（主背景）
  secondary: "1A3A5C",    // 次深蓝
  accent: "00D4AA",       // 科技青绿（强调色）
  accent2: "4ECDC4",      // 浅青色
  white: "FFFFFF",
  lightGray: "E8EEF2",
  textGray: "B8C5D0",
  darkText: "2C3E50",
  danger: "E74C3C",       // 红色（告警）
  warning: "F39C12",      // 橙色（警告）
  success: "27AE60",      // 绿色
};

// 创建演示文稿
let pres = new pptxgen();
pres.layout = 'LAYOUT_16x9';
pres.author = TEAM_NAME;
pres.title = `项目简介PPT - ${PROJECT_CODE}`;
pres.subject = "面向智慧校园的细粒度网络流量监控与安全态势感知系统";

// ============================================
// 工具函数
// ============================================

// 绘制装饰性圆形
function addDecoCircle(slide, pres, x, y, size, color, transparency = 0) {
  slide.addShape(pres.shapes.OVAL, {
    x, y, w: size, h: size,
    fill: { color, transparency },
    line: { color: "FFFFFF", width: 0 }
  });
}

// 绘制矩形装饰条
function addAccentBar(slide, pres, x, y, w, h, color) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w, h,
    fill: { color },
    line: { width: 0 }
  });
}

// 添加页脚
function addFooter(slide, pres, pageNum, totalPages) {
  // 底部装饰线
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 5.4, w: 10, h: 0.02,
    fill: { color: COLORS.accent, transparency: 50 }
  });

  // 页码
  slide.addText(`${pageNum} / ${totalPages}`, {
    x: 9, y: 5.45, w: 0.8, h: 0.2,
    fontSize: 9, color: COLORS.textGray,
    align: "right", margin: 0
  });

  // 团队标识
  slide.addText(`${TEAM_NAME} | ${SCHOOL_CN}`, {
    x: 0.3, y: 5.45, w: 3, h: 0.2,
    fontSize: 9, color: COLORS.textGray,
    align: "left", margin: 0
  });
}

// 添加标题装饰
function addTitleDecor(slide, pres, x, y) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w: 0.08, h: 0.4,
    fill: { color: COLORS.accent }
  });
}

// ============================================
// 第1页：封面
// ============================================
function createCoverSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 装饰性圆形（右上角）
  addDecoCircle(slide, pres, 7.5, -1, 4, COLORS.accent, 85);
  addDecoCircle(slide, pres, 8.5, -0.5, 3, COLORS.accent2, 90);
  addDecoCircle(slide, pres, -1, 3.5, 3, COLORS.secondary, 50);

  // 左侧装饰线
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.5, w: 0.06, h: 2.8,
    fill: { color: COLORS.accent }
  });

  // 赛题编号
  slide.addText(`赛题编号：${PROJECT_CODE}`, {
    x: 0.8, y: 1.2, w: 3, h: 0.35,
    fontSize: 14, color: COLORS.accent, bold: true,
    margin: 0
  });

  // 主标题
  slide.addText("面向智慧校园的", {
    x: 0.8, y: 1.7, w: 8, h: 0.7,
    fontSize: 32, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("细粒度网络流量监控", {
    x: 0.8, y: 2.3, w: 8, h: 0.7,
    fontSize: 36, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText("与安全态势感知系统", {
    x: 0.8, y: 2.9, w: 8, h: 0.7,
    fontSize: 32, color: COLORS.white, bold: true,
    margin: 0
  });

  // 分隔线
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.8, y: 3.7, w: 4, h: 0.02,
    fill: { color: COLORS.accent, transparency: 50 }
  });

  // 团队信息
  slide.addText([
    { text: "参赛队伍：", options: { color: COLORS.textGray } },
    { text: TEAM_NAME, options: { color: COLORS.white, bold: true } }
  ], {
    x: 0.8, y: 4.0, w: 5, h: 0.35,
    fontSize: 16, margin: 0
  });

  slide.addText([
    { text: "参赛编号：", options: { color: COLORS.textGray } },
    { text: TEAM_ID, options: { color: COLORS.accent2 } }
  ], {
    x: 0.8, y: 4.4, w: 5, h: 0.35,
    fontSize: 14, margin: 0
  });

  slide.addText([
    { text: "命题单位：", options: { color: COLORS.textGray } },
    { text: SCHOOL_CN, options: { color: COLORS.white } }
  ], {
    x: 0.8, y: 4.8, w: 5, h: 0.35,
    fontSize: 14, margin: 0
  });

  // 右下角装饰文字
  slide.addText("项目简介", {
    x: 7.5, y: 4.8, w: 2, h: 0.4,
    fontSize: 18, color: COLORS.textGray,
    align: "right", margin: 0
  });
}

// ============================================
// 第2页：目录
// ============================================
function createTOCSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.5);
  slide.addText("目录", {
    x: 0.7, y: 0.45, w: 3, h: 0.5,
    fontSize: 28, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("CONTENTS", {
    x: 0.7, y: 0.9, w: 3, h: 0.3,
    fontSize: 12, color: COLORS.textGray,
    margin: 0
  });

  const tocItems = [
    { num: "01", title: "背景与问题分析" },
    { num: "02", title: "解决方案概览" },
    { num: "03", title: "系统架构设计" },
    { num: "04", title: "核心功能模块" },
    { num: "05", title: "核心算法设计" },
    { num: "06", title: "技术路线" },
    { num: "07", title: "业务模式与可行性" },
    { num: "08", title: "团队介绍" },
  ];

  // 左右两列布局
  const leftItems = tocItems.slice(0, 4);
  const rightItems = tocItems.slice(4, 8);

  leftItems.forEach((item, i) => {
    const y = 1.6 + i * 0.9;
    // 序号
    slide.addText(item.num, {
      x: 0.5, y, w: 0.8, h: 0.6,
      fontSize: 28, color: COLORS.accent, bold: true,
      margin: 0
    });
    // 标题
    slide.addText(item.title, {
      x: 1.3, y: y + 0.1, w: 3.5, h: 0.4,
      fontSize: 18, color: COLORS.white,
      margin: 0
    });
    // 分隔线
    slide.addShape(pres.shapes.RECTANGLE, {
      x: 1.3, y: y + 0.55, w: 3, h: 0.01,
      fill: { color: COLORS.textGray, transparency: 70 }
    });
  });

  rightItems.forEach((item, i) => {
    const y = 1.6 + i * 0.9;
    slide.addText(item.num, {
      x: 5.3, y, w: 0.8, h: 0.6,
      fontSize: 28, color: COLORS.accent, bold: true,
      margin: 0
    });
    slide.addText(item.title, {
      x: 6.1, y: y + 0.1, w: 3.5, h: 0.4,
      fontSize: 18, color: COLORS.white,
      margin: 0
    });
    slide.addShape(pres.shapes.RECTANGLE, {
      x: 6.1, y: y + 0.55, w: 3, h: 0.01,
      fill: { color: COLORS.textGray, transparency: 70 }
    });
  });

  addFooter(slide, pres, 2, 18);
}

// ============================================
// 第3页：背景与问题分析
// ============================================
function createProblemSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("背景与问题分析", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 背景说明
  slide.addText("随着教育信息化2.0深入推进，高校校园网已成为教学、科研与管理的核心基础设施", {
    x: 0.5, y: 1.0, w: 9, h: 0.4,
    fontSize: 13, color: COLORS.textGray,
    margin: 0
  });

  // 三大痛点
  const problems = [
    {
      icon: "👁️",
      title: "看不清流量",
      desc: "SNMP 5分钟级采样\n无法感知秒级波动",
      stat: "精度提升300倍",
      color: COLORS.danger
    },
    {
      icon: "🔍",
      title: "查不到根因",
      desc: "缺乏流级检索\n故障定位靠经验猜测",
      stat: "类Wireshark检索",
      color: COLORS.warning
    },
    {
      icon: "🛡️",
      title: "防不住威胁",
      desc: "蠕虫/慢速扫描\n隐蔽在正常流量中",
      stat: "误报率降低60%+",
      color: COLORS.accent
    }
  ];

  problems.forEach((p, i) => {
    const x = 0.6 + i * 3.1;

    // 卡片背景
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 1.6, w: 2.9, h: 2.8,
      fill: { color: COLORS.secondary },
      line: { color: p.color, width: 2 }
    });

    // 顶部色条
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 1.6, w: 2.9, h: 0.08,
      fill: { color: p.color }
    });

    // 图标
    slide.addText(p.icon, {
      x, y: 1.75, w: 2.9, h: 0.5,
      fontSize: 28, align: "center",
      margin: 0
    });

    // 标题
    slide.addText(p.title, {
      x: x + 0.2, y: 2.3, w: 2.5, h: 0.4,
      fontSize: 18, color: COLORS.white, bold: true,
      margin: 0
    });

    // 描述
    slide.addText(p.desc, {
      x: x + 0.2, y: 2.75, w: 2.5, h: 0.8,
      fontSize: 12, color: COLORS.textGray,
      margin: 0
    });

    // 解决指标
    slide.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.2, y: 3.6, w: 2.5, h: 0.5,
      fill: { color: p.color, transparency: 80 }
    });

    slide.addText(p.stat, {
      x: x + 0.2, y: 3.65, w: 2.5, h: 0.4,
      fontSize: 12, color: p.color, bold: true, align: "center",
      margin: 0
    });
  });

  // 底部总结
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 4.6, w: 9, h: 0.6,
    fill: { color: COLORS.accent, transparency: 85 }
  });

  slide.addText("本项目针对高校校园网「看不清、查不到、防不住」三大运维痛点", {
    x: 0.5, y: 4.7, w: 9, h: 0.4,
    fontSize: 14, color: COLORS.accent, align: "center",
    margin: 0
  });

  addFooter(slide, pres, 3, 18);
}

// ============================================
// 第4页：解决方案概览
// ============================================
function createSolutionOverviewSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("解决方案概览", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 核心思路
  slide.addText("系统采用「采集 → 分析 → 检测 → 展示」四层架构", {
    x: 0.5, y: 1.0, w: 9, h: 0.35,
    fontSize: 14, color: COLORS.accent,
    margin: 0
  });

  // 流程图
  const flowSteps = [
    { name: "高吞吐链路", desc: "NetFlow采集" },
    { name: "秒级采样", desc: "精度1秒" },
    { name: "内存缓冲", desc: "实时聚合" },
    { name: "WebSocket", desc: "全双工推送" }
  ];

  flowSteps.forEach((step, i) => {
    const x = 0.8 + i * 2.3;

    // 圆形节点
    slide.addShape(pres.shapes.OVAL, {
      x: x, y: 1.6, w: 1.8, h: 0.9,
      fill: { color: COLORS.accent },
      line: { width: 0 }
    });

    slide.addText(step.name, {
      x, y: 1.7, w: 1.8, h: 0.4,
      fontSize: 13, color: COLORS.primary, bold: true, align: "center",
      margin: 0
    });

    slide.addText(step.desc, {
      x, y: 2.05, w: 1.8, h: 0.3,
      fontSize: 10, color: COLORS.primary, align: "center",
      margin: 0
    });

    // 箭头
    if (i < 3) {
      slide.addText("→", {
        x: x + 1.8, y: 1.8, w: 0.5, h: 0.5,
        fontSize: 24, color: COLORS.accent2, align: "center",
        margin: 0
      });
    }
  });

  // 分支流程
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.5, y: 2.7, w: 0.02, h: 0.5,
    fill: { color: COLORS.textGray, transparency: 50 }
  });

  // 五大核心能力
  const capabilities = [
    { icon: "📊", name: "实时全景大屏", desc: "四维KPI + 趋势图 + 协议分布" },
    { icon: "🛡️", name: "安全威胁感知", desc: "6种规则 + 4套算法双重检测" },
    { icon: "🔎", name: "流级检索引擎", desc: "类Wireshark Web版精确检索" },
    { icon: "👤", name: "IP详情画像", desc: "单IP行为分析与威胁评估" },
    { icon: "🔽", name: "六层下钻交互", desc: "全景→区域→楼宇→端口" }
  ];

  capabilities.forEach((cap, i) => {
    const row = Math.floor(i / 3);
    const col = i % 3;
    const x = 0.6 + col * 3.1;
    const y = 3.4 + row * 1.0;

    // 小卡片
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 2.9, h: 0.85,
      fill: { color: COLORS.secondary }
    });

    // 左侧色条
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 0.06, h: 0.85,
      fill: { color: COLORS.accent }
    });

    slide.addText(cap.icon, {
      x: x + 0.15, y: y + 0.15, w: 0.5, h: 0.5,
      fontSize: 20, margin: 0
    });

    slide.addText(cap.name, {
      x: x + 0.6, y: y + 0.1, w: 2.2, h: 0.35,
      fontSize: 13, color: COLORS.white, bold: true,
      margin: 0
    });

    slide.addText(cap.desc, {
      x: x + 0.6, y: y + 0.45, w: 2.2, h: 0.3,
      fontSize: 10, color: COLORS.textGray,
      margin: 0
    });
  });

  addFooter(slide, pres, 4, 18);
}

// ============================================
// 第5页：系统架构设计
// ============================================
function createArchitectureSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("系统架构设计", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 三层架构
  const layers = [
    {
      name: "前端展示层",
      tech: "React 18 + Vite 5 + ECharts 5 + ECharts-GL",
      components: "Dashboard | SecurityCenter | FlowSearch | IpDetails",
      color: COLORS.accent,
      y: 1.1
    },
    {
      name: "后端服务层",
      tech: "Spring Boot 3.x + Spring Data JPA",
      components: "流量分析 | 威胁检测 | 算法引擎 | WebSocket推送",
      color: COLORS.accent2,
      y: 2.6
    },
    {
      name: "数据存储层",
      tech: "PostgreSQL 15",
      components: "流量记录 + 安全告警 + 地理信息",
      color: "3498DB",
      y: 4.1
    }
  ];

  layers.forEach((layer, i) => {
    // 层级容器
    slide.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: layer.y, w: 9, h: 1.3,
      fill: { color: COLORS.secondary },
      line: { color: layer.color, width: 1 }
    });

    // 左侧色块
    slide.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: layer.y, w: 0.15, h: 1.3,
      fill: { color: layer.color }
    });

    // 层级名称
    slide.addText(layer.name, {
      x: 0.8, y: layer.y + 0.15, w: 2, h: 0.4,
      fontSize: 16, color: layer.color, bold: true,
      margin: 0
    });

    // 技术栈
    slide.addText(layer.tech, {
      x: 0.8, y: layer.y + 0.55, w: 5, h: 0.3,
      fontSize: 12, color: COLORS.white,
      margin: 0
    });

    // 组件
    slide.addText(layer.components, {
      x: 0.8, y: layer.y + 0.85, w: 8, h: 0.3,
      fontSize: 11, color: COLORS.textGray,
      margin: 0
    });

    // 连接箭头
    if (i < 2) {
      slide.addText("⬇", {
        x: 4.8, y: layer.y + 1.3, w: 0.4, h: 0.3,
        fontSize: 16, color: layer.color, align: "center",
        margin: 0
      });
    }
  });

  // 右侧通信说明
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 6.2, y: 1.1, w: 3.3, h: 1.3,
    fill: { color: COLORS.secondary }
  });

  slide.addText("HTTP REST + WebSocket", {
    x: 6.4, y: 1.3, w: 3, h: 0.3,
    fontSize: 13, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText("实时全双工推送\n≤1秒数据刷新延迟", {
    x: 6.4, y: 1.65, w: 3, h: 0.6,
    fontSize: 11, color: COLORS.textGray,
    margin: 0
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 6.2, y: 2.6, w: 3.3, h: 1.3,
    fill: { color: COLORS.secondary }
  });

  slide.addText("JPA / SQL", {
    x: 6.4, y: 2.8, w: 3, h: 0.3,
    fontSize: 13, color: COLORS.accent2, bold: true,
    margin: 0
  });

  slide.addText("JSONB威胁上下文\nGIN索引加速检索", {
    x: 6.4, y: 3.15, w: 3, h: 0.6,
    fontSize: 11, color: COLORS.textGray,
    margin: 0
  });

  addFooter(slide, pres, 5, 18);
}

// ============================================
// 第6页：核心功能 - 实时全景大屏
// ============================================
function createDashboardSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("核心功能：实时全景大屏", {
    x: 0.7, y: 0.35, w: 6, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 页面标识
  slide.addText("Dashboard", {
    x: 8.5, y: 0.4, w: 1.2, h: 0.4,
    fontSize: 12, color: COLORS.accent, align: "right",
    margin: 0
  });

  // 四大KPI卡片
  const kpis = [
    { label: "实时吞吐量", value: "3.2", unit: "Gbps", icon: "📈" },
    { label: "每秒包数", value: "892K", unit: "PPS", icon: "📦" },
    { label: "活跃IP数", value: "15,623", unit: "个", icon: "👥" },
    { label: "告警总数", value: "47", unit: "条", icon: "🚨" }
  ];

  kpis.forEach((kpi, i) => {
    const x = 0.5 + i * 2.35;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 1.0, w: 2.2, h: 1.1,
      fill: { color: COLORS.secondary }
    });

    slide.addText(kpi.icon, {
      x, y: 1.1, w: 0.6, h: 0.4,
      fontSize: 18, margin: 0
    });

    slide.addText(kpi.value, {
      x: x + 0.5, y: 1.05, w: 1.5, h: 0.5,
      fontSize: 22, color: COLORS.accent, bold: true,
      margin: 0
    });

    slide.addText(kpi.unit, {
      x: x + 1.5, y: 1.35, w: 0.6, h: 0.25,
      fontSize: 10, color: COLORS.textGray,
      margin: 0
    });

    slide.addText(kpi.label, {
      x: x + 0.1, y: 1.7, w: 2, h: 0.3,
      fontSize: 11, color: COLORS.textGray,
      margin: 0
    });
  });

  // 功能特性
  const features = [
    { title: "吞吐趋势图", desc: "时间桶对齐折线图\n支持Bytes/Packets双维切换\n60fps流畅刷新" },
    { title: "协议分布饼图", desc: "覆盖27种应用协议\n实时占比分析\n支持维度切换" },
    { title: "Top-K突发大流", desc: "按流量排序Top-10\n点击直接下钻\n至单IP详情" },
    { title: "区域热力图", desc: "按宿舍区/教学区\n行政区/科研区展示\n流量差异分布" }
  ];

  features.forEach((f, i) => {
    const row = Math.floor(i / 2);
    const col = i % 2;
    const x = 0.5 + col * 4.7;
    const y = 2.3 + row * 1.4;

    // 卡片
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 4.5, h: 1.25,
      fill: { color: COLORS.secondary }
    });

    // 左侧装饰
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 0.08, h: 1.25,
      fill: { color: COLORS.accent }
    });

    slide.addText(f.title, {
      x: x + 0.2, y: y + 0.1, w: 4, h: 0.35,
      fontSize: 14, color: COLORS.white, bold: true,
      margin: 0
    });

    slide.addText(f.desc, {
      x: x + 0.2, y: y + 0.5, w: 4, h: 0.7,
      fontSize: 11, color: COLORS.textGray,
      margin: 0
    });
  });

  addFooter(slide, pres, 6, 18);
}

// ============================================
// 第7页：核心功能 - 安全威胁感知中心
// ============================================
function createSecuritySlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("核心功能：安全威胁感知中心", {
    x: 0.7, y: 0.35, w: 6, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("SecurityCenter", {
    x: 8.3, y: 0.4, w: 1.4, h: 0.4,
    fontSize: 12, color: COLORS.accent, align: "right",
    margin: 0
  });

  // 双层检测体系
  slide.addText("双层威胁检测体系", {
    x: 0.5, y: 1.0, w: 4, h: 0.35,
    fontSize: 16, color: COLORS.accent, bold: true,
    margin: 0
  });

  // 规则驱动层
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.4, w: 4.3, h: 1.8,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.4, w: 4.3, h: 0.4,
    fill: { color: COLORS.danger }
  });

  slide.addText("规则驱动层", {
    x: 0.7, y: 1.45, w: 4, h: 0.35,
    fontSize: 13, color: COLORS.white, bold: true,
    margin: 0
  });

  const ruleThreats = ["DDoS攻击", "端口扫描", "慢速扫描", "蠕虫传播", "钓鱼攻击", "数据外泄"];
  ruleThreats.forEach((t, i) => {
    const row = Math.floor(i / 3);
    const col = i % 3;
    const x = 0.7 + col * 1.35;
    const y = 1.95 + row * 0.55;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 1.2, h: 0.4,
      fill: { color: COLORS.danger, transparency: 70 }
    });

    slide.addText(t, {
      x, y: y + 0.05, w: 1.2, h: 0.3,
      fontSize: 10, color: COLORS.white, align: "center",
      margin: 0
    });
  });

  // 算法增强层
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 3.35, w: 4.3, h: 1.0,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 3.35, w: 4.3, h: 0.4,
    fill: { color: COLORS.accent }
  });

  slide.addText("算法增强层（4套原创算法）", {
    x: 0.7, y: 3.4, w: 4, h: 0.35,
    fontSize: 13, color: COLORS.primary, bold: true,
    margin: 0
  });

  slide.addText("EWMA-EAD | MDTCE | NBF-RS | TPM-PA", {
    x: 0.7, y: 3.85, w: 4, h: 0.4,
    fontSize: 12, color: COLORS.white,
    margin: 0
  });

  // 右侧3D地球与告警列表
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 1.4, w: 4.5, h: 1.8,
    fill: { color: COLORS.secondary }
  });

  slide.addText("🌍  3D地球攻击溯源地图", {
    x: 5.2, y: 1.55, w: 4, h: 0.35,
    fontSize: 14, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText([
    { text: "• WebGL渲染全球攻击源分布", options: { breakLine: true } },
    { text: "• 支持旋转/缩放/点击查看", options: { breakLine: true } },
    { text: "• Jitter坐标抖动防重叠", options: { breakLine: true } },
    { text: "• Bloom泛光特效科技感" }
  ], {
    x: 5.2, y: 2.0, w: 4, h: 1.1,
    fontSize: 11, color: COLORS.textGray,
    margin: 0
  });

  // 告警列表
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 3.35, w: 4.5, h: 1.0,
    fill: { color: COLORS.secondary }
  });

  slide.addText("📋  告警列表", {
    x: 5.2, y: 3.5, w: 4, h: 0.35,
    fontSize: 14, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText("多维筛选 | CSV导出取证 | IP下钻", {
    x: 5.2, y: 3.9, w: 4, h: 0.35,
    fontSize: 11, color: COLORS.textGray,
    margin: 0
  });

  addFooter(slide, pres, 7, 18);
}

// ============================================
// 第8页：核心功能 - 流级检索引擎
// ============================================
function createFlowSearchSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("核心功能：流级检索引擎", {
    x: 0.7, y: 0.35, w: 6, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("FlowSearch", {
    x: 8.3, y: 0.4, w: 1.4, h: 0.4,
    fontSize: 12, color: COLORS.accent, align: "right",
    margin: 0
  });

  // 核心特性
  slide.addText("类Wireshark Web版检索，精准故障取证", {
    x: 0.5, y: 1.0, w: 9, h: 0.35,
    fontSize: 14, color: COLORS.accent,
    margin: 0
  });

  // 检索条件
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.5, w: 4.3, h: 2.4,
    fill: { color: COLORS.secondary }
  });

  slide.addText("多维组合检索", {
    x: 0.7, y: 1.6, w: 4, h: 0.4,
    fontSize: 15, color: COLORS.white, bold: true,
    margin: 0
  });

  const searchFields = [
    { field: "源/目IP", format: "精确IP 或 CIDR通配", example: "192.168.1.1 / 10.0.0.0/8" },
    { field: "端口", format: "精确端口 或 范围", example: "80 / 1024-65535" },
    { field: "协议类型", format: "TCP / UDP / ICMP", example: "TCP" },
    { field: "应用协议", format: "27种协议覆盖", example: "HTTPS / DNS / HTTP" },
    { field: "时间范围", format: "起止时间 或 最近N分钟", example: "minutesAgo=30" }
  ];

  searchFields.forEach((f, i) => {
    const y = 2.1 + i * 0.35;

    slide.addText(f.field, {
      x: 0.7, y, w: 1.0, h: 0.3,
      fontSize: 10, color: COLORS.accent, bold: true,
      margin: 0
    });

    slide.addText(f.format, {
      x: 1.7, y, w: 1.5, h: 0.3,
      fontSize: 10, color: COLORS.white,
      margin: 0
    });

    slide.addText(f.example, {
      x: 3.2, y, w: 1.5, h: 0.3,
      fontSize: 9, color: COLORS.textGray,
      margin: 0
    });
  });

  // 结果展示
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 1.5, w: 4.5, h: 2.4,
    fill: { color: COLORS.secondary }
  });

  slide.addText("分页展示与导出", {
    x: 5.2, y: 1.6, w: 4, h: 0.4,
    fontSize: 15, color: COLORS.white, bold: true,
    margin: 0
  });

  // 表格示意
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.2, y: 2.1, w: 4.1, h: 1.6,
    fill: { color: COLORS.primary }
  });

  // 表头
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.2, y: 2.1, w: 4.1, h: 0.3,
    fill: { color: COLORS.accent, transparency: 70 }
  });

  const headers = ["源IP", "目的IP", "端口", "协议"];
  headers.forEach((h, i) => {
    slide.addText(h, {
      x: 5.2 + i * 1.0, y: 2.15, w: 1.0, h: 0.25,
      fontSize: 9, color: COLORS.white, bold: true, align: "center",
      margin: 0
    });
  });

  // 数据行示例
  const sampleRows = [
    ["192.168.1.105", "8.8.8.8", "443", "TCP"],
    ["10.0.2.15", "114.114.114.114", "53", "UDP"],
    ["172.16.0.50", "139.xxx.xx", "445", "TCP"]
  ];

  sampleRows.forEach((row, i) => {
    row.forEach((cell, j) => {
      slide.addText(cell, {
        x: 5.2 + j * 1.0, y: 2.45 + i * 0.35, w: 1.0, h: 0.3,
        fontSize: 8, color: COLORS.textGray, align: "center",
        margin: 0
      });
    });
  });

  // 功能特性
  slide.addText("📤 CSV导出  |  🔍 IP下钻  |  📄 分页浏览（默认20条/页）", {
    x: 0.5, y: 4.1, w: 9, h: 0.4,
    fontSize: 12, color: COLORS.accent2,
    margin: 0
  });

  // IP下钻说明
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 4.6, w: 9, h: 0.6,
    fill: { color: COLORS.secondary }
  });

  slide.addText("💡 任意IP字段均可点击跳转至 IpDetails 单IP详情页，实现无缝下钻分析", {
    x: 0.7, y: 4.7, w: 8.5, h: 0.4,
    fontSize: 12, color: COLORS.white,
    margin: 0
  });

  addFooter(slide, pres, 8, 18);
}

// ============================================
// 第9页：核心功能 - 六层下钻交互
// ============================================
function createDrilldownSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("核心功能：六层下钻交互", {
    x: 0.7, y: 0.35, w: 6, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("IpDetails", {
    x: 8.3, y: 0.4, w: 1.4, h: 0.4,
    fontSize: 12, color: COLORS.accent, align: "right",
    margin: 0
  });

  // 下钻路径
  slide.addText("对标企业级NetFlow Analyzer产品，实现从宏观到微观的无缝逐级下钻", {
    x: 0.5, y: 1.0, w: 9, h: 0.35,
    fontSize: 13, color: COLORS.textGray,
    margin: 0
  });

  // 六层结构
  const levels = [
    { level: 1, name: "全网全景", desc: "全网总览", icon: "🌐", color: COLORS.accent },
    { level: 2, name: "网络区域", desc: "宿舍区/教学区/行政区/科研区", icon: "🏫", color: COLORS.accent2 },
    { level: 3, name: "楼宇", desc: "宿舍A栋/教学楼/行政楼...", icon: "🏢", color: "3498DB" },
    { level: 4, name: "交换机", desc: "SW-001 / SW-002 ...", icon: "🔌", color: "9B59B6" },
    { level: 5, name: "端口", desc: "GE0/0/1 ~ GE0/0/48", icon: "🔹", color: "E67E22" },
    { level: 6, name: "单IP", desc: "IP详情画像分析", icon: "👤", color: COLORS.danger }
  ];

  // 主下钻流程
  levels.forEach((l, i) => {
    const x = 0.6 + i * 1.55;
    const y = 1.7;

    // 层级卡片
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 1.4, h: 2.0,
      fill: { color: COLORS.secondary },
      line: { color: l.color, width: 2 }
    });

    // 层级编号
    slide.addShape(pres.shapes.OVAL, {
      x: x + 0.45, y: y + 0.1, w: 0.5, h: 0.5,
      fill: { color: l.color }
    });

    slide.addText(`${l.level}`, {
      x: x + 0.45, y: y + 0.15, w: 0.5, h: 0.4,
      fontSize: 14, color: COLORS.white, bold: true, align: "center",
      margin: 0
    });

    // 图标
    slide.addText(l.icon, {
      x, y: y + 0.7, w: 1.4, h: 0.5,
      fontSize: 24, align: "center",
      margin: 0
    });

    // 名称
    slide.addText(l.name, {
      x, y: y + 1.25, w: 1.4, h: 0.3,
      fontSize: 12, color: COLORS.white, bold: true, align: "center",
      margin: 0
    });

    // 描述
    slide.addText(l.desc, {
      x: x + 0.05, y: y + 1.55, w: 1.3, h: 0.4,
      fontSize: 8, color: COLORS.textGray, align: "center",
      margin: 0
    });

    // 连接箭头
    if (i < 5) {
      slide.addText("→", {
        x: x + 1.4, y: y + 0.8, w: 0.15, h: 0.4,
        fontSize: 16, color: l.color,
        margin: 0
      });
    }
  });

  // 单IP详情页功能
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 4.0, w: 9, h: 1.2,
    fill: { color: COLORS.secondary }
  });

  slide.addText("单IP详情画像功能", {
    x: 0.7, y: 4.1, w: 4, h: 0.35,
    fontSize: 14, color: COLORS.accent, bold: true,
    margin: 0
  });

  const ipFeatures = [
    "入/出流量趋势时序图",
    "通信对端TOP-10",
    "协议分布饼图",
    "端口使用分析"
  ];

  ipFeatures.forEach((f, i) => {
    const x = 0.7 + i * 2.2;

    slide.addShape(pres.shapes.OVAL, {
      x, y: 4.6, w: 0.25, h: 0.25,
      fill: { color: COLORS.accent }
    });

    slide.addText(f, {
      x: x + 0.35, y: 4.55, w: 1.8, h: 0.35,
      fontSize: 11, color: COLORS.white,
      margin: 0
    });
  });

  addFooter(slide, pres, 9, 18);
}

// ============================================
// 第10页：核心算法概览
// ============================================
function createAlgoOverviewSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("核心算法设计", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("四套原创算法，超越规则驱动", {
    x: 0.5, y: 1.0, w: 9, h: 0.35,
    fontSize: 14, color: COLORS.accent,
    margin: 0
  });

  // 四套算法卡片
  const algorithms = [
    {
      name: "EWMA-EAD",
      full: "指数加权熵异常检测",
      desc: "融合流量统计与协议熵\n区分正常突发与攻击",
      innovation: "误报率降低60%+",
      color: COLORS.accent
    },
    {
      name: "MDTCE",
      full: "多维威胁关联引擎",
      desc: "基于Kill Chain建模\n识别完整攻击链",
      innovation: "填补校园网安全空白",
      color: COLORS.accent2
    },
    {
      name: "NBF-RS",
      full: "7维行为指纹评分",
      desc: "协议熵/对端多样性\n流量非对称性等综合评估",
      innovation: "7维特征向量画像",
      color: "3498DB"
    },
    {
      name: "TPM-PA",
      full: "时序预测预警",
      desc: "Pearson跨信号相关\n+趋势预测",
      innovation: "预防性告警",
      color: "9B59B6"
    }
  ];

  algorithms.forEach((algo, i) => {
    const row = Math.floor(i / 2);
    const col = i % 2;
    const x = 0.5 + col * 4.7;
    const y = 1.5 + row * 1.85;

    // 卡片背景
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 4.5, h: 1.7,
      fill: { color: COLORS.secondary }
    });

    // 顶部色条
    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 4.5, h: 0.1,
      fill: { color: algo.color }
    });

    // 算法名称
    slide.addText(algo.name, {
      x: x + 0.2, y: y + 0.2, w: 1.5, h: 0.4,
      fontSize: 18, color: algo.color, bold: true,
      margin: 0
    });

    // 全称
    slide.addText(algo.full, {
      x: x + 1.7, y: y + 0.25, w: 2.5, h: 0.35,
      fontSize: 12, color: COLORS.textGray,
      margin: 0
    });

    // 描述
    slide.addText(algo.desc, {
      x: x + 0.2, y: y + 0.7, w: 2.8, h: 0.6,
      fontSize: 11, color: COLORS.white,
      margin: 0
    });

    // 创新点
    slide.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.2, y: y + 1.3, w: 4.1, h: 0.3,
      fill: { color: algo.color, transparency: 80 }
    });

    slide.addText("✨ " + algo.innovation, {
      x: x + 0.3, y: y + 1.32, w: 4, h: 0.25,
      fontSize: 10, color: algo.color,
      margin: 0
    });
  });

  // 底部总结
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 5.1, w: 9, h: 0.35,
    fill: { color: COLORS.accent, transparency: 85 }
  });

  slide.addText("从「被动响应」到「主动预防」的能力跃升", {
    x: 0.5, y: 5.15, w: 9, h: 0.3,
    fontSize: 12, color: COLORS.accent, align: "center",
    margin: 0
  });

  addFooter(slide, pres, 10, 18);
}

// ============================================
// 第11页：算法详解
// ============================================
function createAlgoDetailSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("算法详解", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // EWMA-EAD
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 4.3, h: 2.3,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 0.1, h: 2.3,
    fill: { color: COLORS.accent }
  });

  slide.addText("EWMA-EAD 指数加权熵异常检测", {
    x: 0.7, y: 1.1, w: 4, h: 0.35,
    fontSize: 13, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText([
    { text: "核心思路：", options: { bold: true, breakLine: true } },
    { text: "1. EWMA平滑：抑制流量噪声", options: { breakLine: true } },
    { text: "   S_t = α×X_t + (1-α)×S_{t-1}", options: { breakLine: true } },
    { text: "2. Shannon熵：协议分布分析", options: { breakLine: true } },
    { text: "   H = -Σ p_i × log₂(p_i)", options: { breakLine: true } },
    { text: "3. 融合判断：流量超3σ 且 熵低于阈值", options: { breakLine: true } },
    { text: "", options: { breakLine: true } },
    { text: "优势：融合熵分析后可区分正常突发", options: { color: COLORS.accent } },
    { text: "（协议多样）与攻击（协议单一）" }
  ], {
    x: 0.7, y: 1.5, w: 4, h: 1.7,
    fontSize: 10, color: COLORS.textGray,
    margin: 0
  });

  // MDTCE
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 1.0, w: 4.5, h: 2.3,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 1.0, w: 0.1, h: 2.3,
    fill: { color: COLORS.accent2 }
  });

  slide.addText("MDTCE 多维威胁关联引擎", {
    x: 5.2, y: 1.1, w: 4, h: 0.35,
    fontSize: 13, color: COLORS.accent2, bold: true,
    margin: 0
  });

  slide.addText([
    { text: "核心思路：", options: { bold: true, breakLine: true } },
    { text: "基于网络杀伤链（Kill Chain）模型", options: { breakLine: true } },
    { text: "", options: { breakLine: true } },
    { text: "攻击阶段：", options: { bold: true, breakLine: true } },
    { text: "侦察 → 武器化 → 投递 → 利用", options: { breakLine: true } },
    { text: "→ 持久化 → 横向移动", options: { breakLine: true } },
    { text: "", options: { breakLine: true } },
    { text: "关联规则：同源IP在时间窗内出现≥2个", options: { breakLine: true } },
    { text: "不同攻击阶段事件，触发高威胁关联告警" }
  ], {
    x: 5.2, y: 1.5, w: 4.2, h: 1.7,
    fontSize: 10, color: COLORS.textGray,
    margin: 0
  });

  // NBF-RS & TPM-PA
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 3.5, w: 4.3, h: 1.7,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 3.5, w: 0.1, h: 1.7,
    fill: { color: "3498DB" }
  });

  slide.addText("NBF-RS 7维行为指纹评分", {
    x: 0.7, y: 3.6, w: 4, h: 0.35,
    fontSize: 13, color: "3498DB", bold: true,
    margin: 0
  });

  slide.addText([
    { text: "7个维度：", options: { bold: true } },
    { text: "协议熵 | 对端多样性 | 流量非对称性", options: { breakLine: true } },
    { text: "端口分散度 | 时序集中度 | 连接强度", options: { breakLine: true } },
    { text: "包大小偏差", options: { breakLine: true } },
    { text: "", options: { breakLine: true } },
    { text: "综合评分：7维度加权求和，超过阈值触发风险告警" }
  ], {
    x: 0.7, y: 4.0, w: 4, h: 1.1,
    fontSize: 10, color: COLORS.textGray,
    margin: 0
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 3.5, w: 4.5, h: 1.7,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 5.0, y: 3.5, w: 0.1, h: 1.7,
    fill: { color: "9B59B6" }
  });

  slide.addText("TPM-PA 时序预测预警", {
    x: 5.2, y: 3.6, w: 4, h: 0.35,
    fontSize: 13, color: "9B59B6", bold: true,
    margin: 0
  });

  slide.addText([
    { text: "核心思路：", options: { bold: true, breakLine: true } },
    { text: "1. 线性趋势分解：提取异常残差", options: { breakLine: true } },
    { text: "2. Pearson跨信号相关分析", options: { breakLine: true } },
    { text: "   r = Σ(X_i-X̄)(Y_i-Ȳ) / ...", options: { breakLine: true } },
    { text: "3. 多区域异常相关+残差超阈值，提前预警", options: { breakLine: true } },
    { text: "", options: { breakLine: true } },
    { text: "实现「预防性告警」而非事后发现" }
  ], {
    x: 5.2, y: 4.0, w: 4.2, h: 1.1,
    fontSize: 10, color: COLORS.textGray,
    margin: 0
  });

  addFooter(slide, pres, 11, 18);
}

// ============================================
// 第12页：技术路线
// ============================================
function createTechRouteSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("技术路线", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 技术栈表格
  const techStack = [
    { layer: "前端框架", tech: "React 18 + Vite 5", reason: "组件化开发，Vite极速HMR" },
    { layer: "状态管理", tech: "Zustand", reason: "轻量无样板，跨组件共享状态" },
    { layer: "可视化", tech: "ECharts 5 + ECharts-GL", reason: "WebGL 3D地球渲染，Bloom特效" },
    { layer: "后端框架", tech: "Spring Boot 3.x", reason: "生产级Java框架，Actuator监控" },
    { layer: "ORM", tech: "Spring Data JPA", reason: "简化数据访问，支持复杂动态查询" },
    { layer: "数据库", tech: "PostgreSQL 15", reason: "强类型、JSONB列、GIN索引" },
    { layer: "实时通信", tech: "Spring WebSocket", reason: "原生集成STOMP，低延迟推送" },
    { layer: "容器化", tech: "Docker + Docker Compose", reason: "一键部署，环境隔离" }
  ];

  // 表头
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 9, h: 0.4,
    fill: { color: COLORS.accent }
  });

  slide.addText("层次", {
    x: 0.6, y: 1.05, w: 1.2, h: 0.3,
    fontSize: 12, color: COLORS.primary, bold: true,
    margin: 0
  });

  slide.addText("技术选型", {
    x: 1.8, y: 1.05, w: 2.5, h: 0.3,
    fontSize: 12, color: COLORS.primary, bold: true,
    margin: 0
  });

  slide.addText("选型理由", {
    x: 4.3, y: 1.05, w: 5, h: 0.3,
    fontSize: 12, color: COLORS.primary, bold: true,
    margin: 0
  });

  techStack.forEach((t, i) => {
    const y = 1.45 + i * 0.45;
    const bgColor = i % 2 === 0 ? COLORS.secondary : COLORS.primary;

    slide.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y, w: 9, h: 0.45,
      fill: { color: bgColor }
    });

    slide.addText(t.layer, {
      x: 0.6, y: y + 0.08, w: 1.2, h: 0.3,
      fontSize: 11, color: COLORS.accent, bold: true,
      margin: 0
    });

    slide.addText(t.tech, {
      x: 1.8, y: y + 0.08, w: 2.5, h: 0.3,
      fontSize: 11, color: COLORS.white,
      margin: 0
    });

    slide.addText(t.reason, {
      x: 4.3, y: y + 0.08, w: 5, h: 0.3,
      fontSize: 10, color: COLORS.textGray,
      margin: 0
    });
  });

  // 底部强调
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 5.0, w: 9, h: 0.45,
    fill: { color: COLORS.accent, transparency: 85 }
  });

  slide.addText("跨平台支持：Linux / Windows | API文档：springdoc-openapi (Swagger UI)", {
    x: 0.5, y: 5.05, w: 9, h: 0.35,
    fontSize: 11, color: COLORS.accent, align: "center",
    margin: 0
  });

  addFooter(slide, pres, 12, 18);
}

// ============================================
// 第13页：业务模式与可行性分析
// ============================================
function createBusinessSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("业务模式与可行性分析", {
    x: 0.7, y: 0.35, w: 6, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 应用对象
  slide.addText("应用对象", {
    x: 0.5, y: 1.0, w: 4, h: 0.35,
    fontSize: 15, color: COLORS.accent, bold: true,
    margin: 0
  });

  const users = [
    { role: "网络运维工程师", use: "实时监控、快速定位故障根因、处理安全告警" },
    { role: "网络安全管理员", use: "威胁态势感知、攻击溯源分析、应急响应" },
    { role: "信息化管理部门", use: "全校网络运行态势总览、辅助决策" },
    { role: "竞赛评审专家", use: "一键部署快速复现、体验完整功能" }
  ];

  users.forEach((u, i) => {
    const x = 0.5 + (i % 2) * 4.7;
    const y = 1.4 + Math.floor(i / 2) * 0.7;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 4.5, h: 0.6,
      fill: { color: COLORS.secondary }
    });

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 0.08, h: 0.6,
      fill: { color: COLORS.accent }
    });

    slide.addText(u.role, {
      x: x + 0.2, y: y + 0.05, w: 1.8, h: 0.25,
      fontSize: 11, color: COLORS.accent, bold: true,
      margin: 0
    });

    slide.addText(u.use, {
      x: x + 0.2, y: y + 0.3, w: 4.2, h: 0.25,
      fontSize: 9, color: COLORS.textGray,
      margin: 0
    });
  });

  // 可推广场景
  slide.addText("可推广场景", {
    x: 0.5, y: 2.9, w: 4, h: 0.35,
    fontSize: 15, color: COLORS.accent, bold: true,
    margin: 0
  });

  const scenarios = ["企业园区网", "政务专网", "数据中心"];

  scenarios.forEach((s, i) => {
    const x = 0.5 + i * 3.1;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 3.3, w: 2.8, h: 0.5,
      fill: { color: COLORS.secondary }
    });

    slide.addText(s, {
      x, y: 3.35, w: 2.8, h: 0.4,
      fontSize: 13, color: COLORS.white, align: "center",
      margin: 0
    });
  });

  // 可行性分析
  slide.addText("可行性分析", {
    x: 0.5, y: 4.0, w: 4, h: 0.35,
    fontSize: 15, color: COLORS.accent, bold: true,
    margin: 0
  });

  const feasibilities = [
    { title: "技术可行性", desc: "主流技术栈，组件成熟", icon: "✅" },
    { title: "经济可行性", desc: "开源组件，低部署成本", icon: "💰" },
    { title: "操作可行性", desc: "Docker一键部署，即开即用", icon: "🚀" }
  ];

  feasibilities.forEach((f, i) => {
    const x = 0.5 + i * 3.1;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 4.4, w: 2.9, h: 0.85,
      fill: { color: COLORS.secondary }
    });

    slide.addText(f.icon, {
      x: x + 0.1, y: 4.5, w: 0.5, h: 0.3,
      fontSize: 16, margin: 0
    });

    slide.addText(f.title, {
      x: x + 0.5, y: 4.5, w: 2.3, h: 0.3,
      fontSize: 12, color: COLORS.white, bold: true,
      margin: 0
    });

    slide.addText(f.desc, {
      x: x + 0.1, y: 4.85, w: 2.7, h: 0.3,
      fontSize: 10, color: COLORS.textGray,
      margin: 0
    });
  });

  addFooter(slide, pres, 13, 18);
}

// ============================================
// 第14页：团队介绍
// ============================================
function createTeamSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("团队介绍", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 团队名称卡片
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 9, h: 1.2,
    fill: { color: COLORS.secondary }
  });

  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 0.15, h: 1.2,
    fill: { color: COLORS.accent }
  });

  slide.addText(TEAM_NAME, {
    x: 0.8, y: 1.1, w: 4, h: 0.5,
    fontSize: 28, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText("参赛编号：" + TEAM_ID + "  |  命题单位：" + SCHOOL_CN, {
    x: 0.8, y: 1.65, w: 6, h: 0.35,
    fontSize: 14, color: COLORS.textGray,
    margin: 0
  });

  // 成员信息（假设3-4人）
  slide.addText("核心成员", {
    x: 0.5, y: 2.4, w: 4, h: 0.35,
    fontSize: 15, color: COLORS.accent, bold: true,
    margin: 0
  });

  const members = [
    { name: "队长 / 开发", role: "系统架构设计与核心算法实现", skill: "Java / Spring Boot / 数据库" },
    { name: "前端开发", role: "React组件开发与可视化实现", skill: "React / ECharts / WebSocket" },
    { name: "算法工程师", role: "威胁检测算法设计与优化", skill: "Python / 数据分析 / 机器学习" },
    { name: "测试运维", role: "Docker部署与系统测试", skill: "Docker / Linux / 性能调优" }
  ];

  members.forEach((m, i) => {
    const x = 0.5 + (i % 2) * 4.7;
    const y = 2.85 + Math.floor(i / 2) * 1.1;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 4.5, h: 0.95,
      fill: { color: COLORS.secondary }
    });

    // 头像占位
    slide.addShape(pres.shapes.OVAL, {
      x: x + 0.15, y: y + 0.2, w: 0.55, h: 0.55,
      fill: { color: COLORS.accent, transparency: 50 }
    });

    slide.addText(m.name.charAt(0), {
      x: x + 0.15, y: y + 0.28, w: 0.55, h: 0.4,
      fontSize: 14, color: COLORS.white, bold: true, align: "center",
      margin: 0
    });

    slide.addText(m.name, {
      x: x + 0.85, y: y + 0.15, w: 3.5, h: 0.3,
      fontSize: 13, color: COLORS.white, bold: true,
      margin: 0
    });

    slide.addText(m.role, {
      x: x + 0.85, y: y + 0.45, w: 3.5, h: 0.25,
      fontSize: 10, color: COLORS.textGray,
      margin: 0
    });

    slide.addText(m.skill, {
      x: x + 0.85, y: y + 0.7, w: 3.5, h: 0.2,
      fontSize: 9, color: COLORS.accent2,
      margin: 0
    });
  });

  addFooter(slide, pres, 14, 18);
}

// ============================================
// 第15页：部署方案
// ============================================
function createDeploySlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("部署方案", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 服务器信息标签
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.0, w: 9, h: 0.35,
    fill: { color: COLORS.secondary }
  });
  slide.addText("🏠 部署服务器: 8.146.228.64  |  📡 测试流量源: 60.205.56.61", {
    x: 0.7, y: 1.05, w: 8.6, h: 0.25,
    fontSize: 12, color: COLORS.accent,
    margin: 0
  });

  // 架构图 - 三层架构
  const archNodes = [
    { name: "测试流量源", sub: "60.205.56.61", x: 0.8, color: "F39C12", icon: "📡" },
    { name: "前端大屏", sub: "8.146.228.64:3000", x: 3.8, color: COLORS.accent, icon: "🖥️" },
    { name: "后端服务", sub: "8.146.228.64:8080", x: 6.8, color: COLORS.accent2, icon: "⚙️" }
  ];

  archNodes.forEach((n, i) => {
    // 节点方框
    slide.addShape(pres.shapes.RECTANGLE, {
      x: n.x, y: 1.6, w: 2.4, h: 1.3,
      fill: { color: COLORS.secondary }
    });
    // 顶部强调条
    slide.addShape(pres.shapes.RECTANGLE, {
      x: n.x, y: 1.6, w: 2.4, h: 0.08,
      fill: { color: n.color }
    });
    // 图标+名称
    slide.addText(n.icon + " " + n.name, {
      x: n.x, y: 1.75, w: 2.4, h: 0.4,
      fontSize: 13, color: n.color, bold: true, align: "center",
      margin: 0
    });
    // 子标题
    slide.addText(n.sub, {
      x: n.x, y: 2.2, w: 2.4, h: 0.3,
      fontSize: 10, color: COLORS.textGray, align: "center",
      margin: 0
    });
    // 箭头
    if (i < 2) {
      slide.addText("→", {
        x: n.x + 2.4, y: 2.0, w: 0.4, h: 0.4,
        fontSize: 22, color: COLORS.textGray, align: "center",
        margin: 0
      });
    }
  });

  // 数据流向标注
  slide.addText("↓ 模拟真实网络流量", {
    x: 0.8, y: 2.95, w: 2.4, h: 0.25,
    fontSize: 10, color: "F39C12", align: "center",
    margin: 0
  });
  slide.addText("↑ HTTP/WebSocket 数据", {
    x: 3.8, y: 2.95, w: 2.4, h: 0.25,
    fontSize: 10, color: COLORS.accent, align: "center",
    margin: 0
  });

  // 访问地址区
  slide.addText("🌐 在线访问地址", {
    x: 0.5, y: 3.4, w: 4, h: 0.3,
    fontSize: 14, color: COLORS.accent,
    margin: 0
  });

  const endpoints = [
    { name: "前端大屏", url: "http://8.146.228.64:3000" },
    { name: "后端API", url: "http://8.146.228.64:8080" },
    { name: "Swagger文档", url: "http://8.146.228.64:8080/swagger-ui.html" }
  ];

  endpoints.forEach((e, i) => {
    const x = 0.5 + i * 3.1;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 3.75, w: 2.9, h: 0.7,
      fill: { color: COLORS.secondary }
    });

    slide.addText(e.name, {
      x: x + 0.1, y: 3.8, w: 2.7, h: 0.25,
      fontSize: 10, color: COLORS.textGray,
      margin: 0
    });

    slide.addText(e.url, {
      x: x + 0.1, y: 4.1, w: 2.7, h: 0.3,
      fontSize: 10, color: COLORS.accent, bold: true,
      margin: 0
    });
  });

  // 底部说明
  slide.addText("Docker Compose 一键容器化部署 · PostgreSQL 15 数据库 · 前后端分离架构", {
    x: 0.5, y: 4.7, w: 9, h: 0.3,
    fontSize: 11, color: COLORS.textGray, align: "center",
    margin: 0
  });
  
  addFooter(slide, pres, 15, 18);
}

// ============================================
// 第16页：创新亮点总结
// ============================================
function createHighlightSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("创新亮点总结", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  const highlights = [
    {
      num: "01",
      title: "四套原创算法",
      desc: "EWMA-EAD、MDTCE、NBF-RS、TPM-PA\n实现从「被动响应」到「主动预防」",
      color: COLORS.accent
    },
    {
      num: "02",
      title: "6级下钻交互",
      desc: "全景→区域→楼宇→交换机→端口→单IP\n对标企业级NetFlow Analyzer产品",
      color: COLORS.accent2
    },
    {
      num: "03",
      title: "3D地球攻击溯源",
      desc: "纯前端WebGL渲染\nCanvas纹理+Jitter+Bloom特效",
      color: "3498DB"
    },
    {
      num: "04",
      title: "类Wireshark检索",
      desc: "CIDR通配+端口范围+协议组合检索\nCSV导出精准取证",
      color: "9B59B6"
    },
    {
      num: "05",
      title: "演示数据智能自维护",
      desc: "12Gbps吞吐量自动续流\n评审全程即开即用",
      color: "E67E22"
    },
    {
      num: "06",
      title: "高性能实时架构",
      desc: "CompletableFuture并行查询\nWebSocket≤1秒推送",
      color: COLORS.danger
    }
  ];

  highlights.forEach((h, i) => {
    const row = Math.floor(i / 3);
    const col = i % 3;
    const x = 0.5 + col * 3.1;
    const y = 1.0 + row * 2.2;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y, w: 2.9, h: 2.0,
      fill: { color: COLORS.secondary }
    });

    // 编号
    slide.addText(h.num, {
      x, y: y + 0.1, w: 2.9, h: 0.5,
      fontSize: 28, color: h.color, bold: true, align: "center",
      margin: 0
    });

    // 标题
    slide.addText(h.title, {
      x: x + 0.1, y: y + 0.65, w: 2.7, h: 0.35,
      fontSize: 13, color: COLORS.white, bold: true, align: "center",
      margin: 0
    });

    // 分隔线
    slide.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.8, y: y + 1.05, w: 1.3, h: 0.02,
      fill: { color: h.color }
    });

    // 描述
    slide.addText(h.desc, {
      x: x + 0.1, y: y + 1.15, w: 2.7, h: 0.75,
      fontSize: 10, color: COLORS.textGray, align: "center",
      margin: 0
    });
  });

  addFooter(slide, pres, 16, 18);
}

// ============================================
// 第17页：未来展望
// ============================================
function createFutureSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 标题
  addTitleDecor(slide, pres, 0.5, 0.4);
  slide.addText("未来展望", {
    x: 0.7, y: 0.35, w: 5, h: 0.5,
    fontSize: 26, color: COLORS.white, bold: true,
    margin: 0
  });

  // 发展方向
  const futureItems = [
    {
      title: "算法深化",
      points: ["引入深度学习模型提升检测精度", "基于时序网络的异常检测", "用户行为分析(UEBA)集成"],
      color: COLORS.accent
    },
    {
      title: "场景拓展",
      points: ["支持5G/IoT流量监控", "多校区集中管控", "云原生架构升级"],
      color: COLORS.accent2
    },
    {
      title: "生态建设",
      points: ["开放API供第三方集成", "威胁情报共享联盟", "行业标准化输出"],
      color: "3498DB"
    }
  ];

  futureItems.forEach((item, i) => {
    const x = 0.5 + i * 3.1;

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 1.0, w: 2.9, h: 2.8,
      fill: { color: COLORS.secondary }
    });

    slide.addShape(pres.shapes.RECTANGLE, {
      x, y: 1.0, w: 2.9, h: 0.08,
      fill: { color: item.color }
    });

    slide.addText(item.title, {
      x, y: 1.2, w: 2.9, h: 0.4,
      fontSize: 15, color: item.color, bold: true, align: "center",
      margin: 0
    });

    item.points.forEach((p, j) => {
      slide.addText("• " + p, {
        x: x + 0.2, y: 1.7 + j * 0.6, w: 2.5, h: 0.5,
        fontSize: 11, color: COLORS.textGray,
        margin: 0
      });
    });
  });

  // 愿景
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 4.0, w: 9, h: 1.0,
    fill: { color: COLORS.accent, transparency: 85 }
  });

  slide.addText("愿景", {
    x: 0.7, y: 4.1, w: 1, h: 0.3,
    fontSize: 14, color: COLORS.accent, bold: true,
    margin: 0
  });

  slide.addText("为高校网络安全治理提供可复制、可推广的标准化参考范式，成为智慧校园网络运维的标杆解决方案", {
    x: 0.7, y: 4.45, w: 8.5, h: 0.4,
    fontSize: 13, color: COLORS.white,
    margin: 0
  });

  addFooter(slide, pres, 17, 18);
}

// ============================================
// 第18页：结束页
// ============================================
function createEndSlide(pres) {
  let slide = pres.addSlide();
  slide.background = { color: COLORS.primary };

  // 装饰性圆形
  addDecoCircle(slide, pres, -1, -1, 4, COLORS.accent, 90);
  addDecoCircle(slide, pres, 7, 3.5, 5, COLORS.secondary, 50);
  addDecoCircle(slide, pres, 8, 4, 3, COLORS.accent2, 85);

  // 左侧装饰线
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.8, w: 0.06, h: 2.0,
    fill: { color: COLORS.accent }
  });

  // 致谢
  slide.addText("感谢聆听", {
    x: 0.8, y: 1.8, w: 8, h: 0.8,
    fontSize: 44, color: COLORS.white, bold: true,
    margin: 0
  });

  slide.addText("THANK YOU", {
    x: 0.8, y: 2.6, w: 8, h: 0.5,
    fontSize: 20, color: COLORS.accent,
    charSpacing: 8,
    margin: 0
  });

  // 分隔线
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0.8, y: 3.3, w: 4, h: 0.02,
    fill: { color: COLORS.accent, transparency: 50 }
  });

  // 项目信息
  slide.addText("面向智慧校园的细粒度网络流量监控与安全态势感知系统", {
    x: 0.8, y: 3.5, w: 8, h: 0.4,
    fontSize: 14, color: COLORS.textGray,
    margin: 0
  });

  // 团队信息
  slide.addText([
    { text: TEAM_NAME, options: { color: COLORS.accent, bold: true } },
    { text: "  |  " + TEAM_ID, options: { color: COLORS.textGray } },
    { text: "  |  " + SCHOOL_CN, options: { color: COLORS.textGray } }
  ], {
    x: 0.8, y: 4.0, w: 8, h: 0.35,
    fontSize: 13,
    margin: 0
  });

  // 赛题编号
  slide.addText("赛题编号：" + PROJECT_CODE, {
    x: 0.8, y: 4.4, w: 4, h: 0.3,
    fontSize: 12, color: COLORS.accent2,
    margin: 0
  });

  // 右下角装饰
  slide.addText("智慧校园 · 安全先行", {
    x: 6.5, y: 5.0, w: 3, h: 0.4,
    fontSize: 12, color: COLORS.textGray, align: "right",
    margin: 0
  });
}

// ============================================
// 生成所有幻灯片
// ============================================
async function createPresentation() {
  console.log("开始生成PPT...");

  // 封面
  createCoverSlide(pres);
  console.log("✓ 第1页：封面");

  // 目录
  createTOCSlide(pres);
  console.log("✓ 第2页：目录");

  // 背景与问题分析
  createProblemSlide(pres);
  console.log("✓ 第3页：背景与问题分析");

  // 解决方案概览
  createSolutionOverviewSlide(pres);
  console.log("✓ 第4页：解决方案概览");

  // 系统架构设计
  createArchitectureSlide(pres);
  console.log("✓ 第5页：系统架构设计");

  // 核心功能 - 实时全景大屏
  createDashboardSlide(pres);
  console.log("✓ 第6页：核心功能-实时全景大屏");

  // 核心功能 - 安全威胁感知中心
  createSecuritySlide(pres);
  console.log("✓ 第7页：核心功能-安全威胁感知中心");

  // 核心功能 - 流级检索引擎
  createFlowSearchSlide(pres);
  console.log("✓ 第8页：核心功能-流级检索引擎");

  // 核心功能 - 六层下钻交互
  createDrilldownSlide(pres);
  console.log("✓ 第9页：核心功能-六层下钻交互");

  // 核心算法概览
  createAlgoOverviewSlide(pres);
  console.log("✓ 第10页：核心算法概览");

  // 算法详解
  createAlgoDetailSlide(pres);
  console.log("✓ 第11页：算法详解");

  // 技术路线
  createTechRouteSlide(pres);
  console.log("✓ 第12页：技术路线");

  // 业务模式与可行性分析
  createBusinessSlide(pres);
  console.log("✓ 第13页：业务模式与可行性分析");

  // 团队介绍
  createTeamSlide(pres);
  console.log("✓ 第14页：团队介绍");

  // 部署方案
  createDeploySlide(pres);
  console.log("✓ 第15页：部署方案");

  // 创新亮点总结
  createHighlightSlide(pres);
  console.log("✓ 第16页：创新亮点总结");

  // 未来展望
  createFutureSlide(pres);
  console.log("✓ 第17页：未来展望");

  // 结束页
  createEndSlide(pres);
  console.log("✓ 第18页：结束页");

  // 保存文件
  await pres.writeFile({ fileName: OUTPUT_FILE });
  console.log(`\n✅ PPT生成完成！`);
  console.log(`📄 文件名：${OUTPUT_FILE}`);
  console.log(`📊 共18页`);
}

createPresentation().catch(console.error);
