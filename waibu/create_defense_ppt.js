/**
 * 计算机设计大赛答辩演示PPT生成脚本
 * 面向智慧校园的细粒度网络流量监控与安全态势感知系统
 */

const pptxgen = require("pptxgenjs");

// ============ 配色方案 - 科技蓝色系 ============
const COLORS = {
  primary: "0F4C75",      // 深海蓝 - 主色
  secondary: "1B9AAA",    // 青色 - 辅助色
  accent: "00B4D8",       // 亮蓝 - 强调色
  dark: "0D1B2A",         // 深蓝黑 - 背景
  light: "F0F4F8",        // 浅灰白 - 浅背景
  white: "FFFFFF",
  text: "1B263B",         // 深色文字
  textLight: "778DA9",    // 浅色文字
  success: "2ECC71",      // 绿色
  warning: "F39C12",      // 橙色
  danger: "E74C3C",       // 红色
  gold: "D4AF37",         // 金色
};

// ============ 创建阴影的工厂函数 ============
const makeCardShadow = () => ({
  type: "outer", color: "000000", blur: 8, offset: 3, angle: 135, opacity: 0.12
});
const makeButtonShadow = () => ({
  type: "outer", color: "000000", blur: 4, offset: 2, angle: 135, opacity: 0.15
});

// ============ 主函数 ============
async function createPresentation() {
  let pres = new pptxgen();
  pres.layout = 'LAYOUT_16x9';
  pres.title = '面向智慧校园的细粒度网络流量监控与安全态势感知系统';
  pres.author = '苏州大学 无敌暴龙战队';
  pres.subject = '计算机设计大赛答辩演示';

  // ============ 幻灯片 1: 封面 ============
  let slide1 = pres.addSlide();
  slide1.background = { color: COLORS.dark };

  // 顶部装饰线
  slide1.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 0.08,
    fill: { color: COLORS.accent }
  });

  // 左侧装饰条
  slide1.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 1.5, w: 0.12, h: 2.5,
    fill: { color: COLORS.secondary }
  });

  // 参赛信息标签
  slide1.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 0.5, w: 2.4, h: 0.45,
    fill: { color: COLORS.secondary },
    shadow: makeButtonShadow()
  });
  slide1.addText("计算机设计大赛", {
    x: 0.5, y: 0.5, w: 2.4, h: 0.45,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  // 主标题
  slide1.addText("面向智慧校园的细粒度", {
    x: 0.5, y: 1.6, w: 9, h: 0.7,
    fontSize: 36, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white
  });
  slide1.addText("网络流量监控与安全态势感知系统", {
    x: 0.5, y: 2.2, w: 9, h: 0.8,
    fontSize: 40, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.accent
  });

  // 分隔线
  slide1.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 3.2, w: 3, h: 0.04,
    fill: { color: COLORS.secondary }
  });

  // 团队信息
  slide1.addText("无敌暴龙战队", {
    x: 0.5, y: 3.5, w: 5, h: 0.5,
    fontSize: 20, fontFace: "Microsoft YaHei",
    color: COLORS.textLight
  });
  slide1.addText("苏州大学", {
    x: 0.5, y: 4.0, w: 5, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei",
    color: COLORS.textLight
  });

  // 底部信息
  slide1.addText("2026年中国大学生计算机设计大赛", {
    x: 0.5, y: 5.1, w: 9, h: 0.35,
    fontSize: 14, fontFace: "Microsoft YaHei",
    color: COLORS.textLight
  });

  // 右侧装饰圆
  slide1.addShape(pres.shapes.OVAL, {
    x: 7.5, y: 2.8, w: 2.8, h: 2.8,
    fill: { color: COLORS.primary, transparency: 30 }
  });
  slide1.addShape(pres.shapes.OVAL, {
    x: 8.2, y: 3.5, w: 1.8, h: 1.8,
    fill: { color: COLORS.secondary, transparency: 40 }
  });

  // ============ 幻灯片 2: 目录 ============
  let slide2 = pres.addSlide();
  slide2.background = { color: COLORS.light };

  // 标题区
  slide2.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.2,
    fill: { color: COLORS.dark }
  });
  slide2.addText("答辩提纲", {
    x: 0.5, y: 0.35, w: 9, h: 0.6,
    fontSize: 32, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 目录项
  const tocItems = [
    { num: "01", title: "项目背景", desc: "高校网络运维的挑战与机遇" },
    { num: "02", title: "痛点分析", desc: "三大核心问题深度剖析" },
    { num: "03", title: "解决方案", desc: "四层架构全栈解决方案" },
    { num: "04", title: "核心功能", desc: "全景大屏 / 威胁感知 / 流检索" },
    { num: "05", title: "算法创新", desc: "四套原创算法技术突破" },
    { num: "06", title: "总结展望", desc: "应用价值与未来发展" },
  ];

  tocItems.forEach((item, i) => {
    const row = Math.floor(i / 3);
    const col = i % 3;
    const x = 0.5 + col * 3.1;
    const y = 1.6 + row * 1.8;

    // 卡片背景
    slide2.addShape(pres.shapes.RECTANGLE, {
      x: x, y: y, w: 2.9, h: 1.5,
      fill: { color: COLORS.white },
      shadow: makeCardShadow()
    });

    // 左侧强调条
    slide2.addShape(pres.shapes.RECTANGLE, {
      x: x, y: y, w: 0.08, h: 1.5,
      fill: { color: COLORS.secondary }
    });

    // 序号
    slide2.addText(item.num, {
      x: x + 0.2, y: y + 0.15, w: 0.8, h: 0.5,
      fontSize: 28, fontFace: "Arial", bold: true,
      color: COLORS.accent, margin: 0
    });

    // 标题
    slide2.addText(item.title, {
      x: x + 0.2, y: y + 0.65, w: 2.5, h: 0.4,
      fontSize: 18, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.text, margin: 0
    });

    // 描述
    slide2.addText(item.desc, {
      x: x + 0.2, y: y + 1.0, w: 2.5, h: 0.4,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, margin: 0
    });
  });

  // ============ 幻灯片 3: 项目背景 ============
  let slide3 = pres.addSlide();
  slide3.background = { color: COLORS.dark };

  // 顶部装饰
  slide3.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 0.15, h: 5.625,
    fill: { color: COLORS.secondary }
  });

  // 标题
  slide3.addText("01", {
    x: 0.4, y: 0.3, w: 1, h: 0.5,
    fontSize: 24, fontFace: "Arial", bold: true,
    color: COLORS.accent, margin: 0
  });
  slide3.addText("项目背景", {
    x: 0.4, y: 0.7, w: 3, h: 0.6,
    fontSize: 32, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 背景内容卡片
  slide3.addShape(pres.shapes.RECTANGLE, {
    x: 0.4, y: 1.5, w: 9.2, h: 3.8,
    fill: { color: COLORS.white, transparency: 95 },
    line: { color: COLORS.secondary, width: 1 }
  });

  // 背景描述
  slide3.addText("教育信息化2.0时代，高校校园网已成为教学、科研与管理的核心基础设施", {
    x: 0.6, y: 1.7, w: 8.8, h: 0.5,
    fontSize: 16, fontFace: "Microsoft YaHei",
    color: COLORS.white
  });

  // 三个特征
  const features = [
    { symbol: "[ 01 ]", title: "高并发", desc: "万人同时在线\n教学科研并行" },
    { symbol: "[ 02 ]", title: "高突发", desc: "考试/活动期间\n流量瞬时激增" },
    { symbol: "[ 03 ]", title: "应用复杂", desc: "HTTP/DNS/SSH等\n27+种协议" },
  ];

  features.forEach((f, i) => {
    const x = 0.8 + i * 3;
    slide3.addText(f.symbol, {
      x: x + 0.6, y: 2.4, w: 1.4, h: 0.4,
      fontSize: 14, fontFace: "Arial",
      color: COLORS.accent, align: "center"
    });
    slide3.addText(f.title, {
      x: x, y: 2.9, w: 2.6, h: 0.5,
      fontSize: 22, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.accent, align: "center", margin: 0
    });
    slide3.addText(f.desc, {
      x: x, y: 3.5, w: 2.6, h: 0.8,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, align: "center"
    });
  });

  // 底部强调
  slide3.addShape(pres.shapes.RECTANGLE, {
    x: 0.6, y: 4.6, w: 8.8, h: 0.5,
    fill: { color: COLORS.secondary, transparency: 70 }
  });
  slide3.addText("传统监控手段已无法满足精细化运维需求", {
    x: 0.6, y: 4.6, w: 8.8, h: 0.5,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  // ============ 幻灯片 4: 痛点分析 ============
  let slide4 = pres.addSlide();
  slide4.background = { color: COLORS.light };

  // 标题区
  slide4.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.1,
    fill: { color: COLORS.dark }
  });
  slide4.addText("02  痛点分析", {
    x: 0.5, y: 0.3, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 三大痛点
  const painPoints = [
    {
      symbol: "[ 01 ]",
      title: "看不清流量",
      problem: "SNMP 5分钟级采样",
      solution: "秒级高频采样",
      improvement: "精度提升 300 倍"
    },
    {
      symbol: "[ 02 ]",
      title: "查不到根因",
      problem: "缺乏流级检索能力",
      solution: "类Wireshark Web引擎",
      improvement: "多维精确查询"
    },
    {
      symbol: "[ 03 ]",
      title: "防不住威胁",
      problem: "规则匹配误报率高",
      solution: "规则+算法双层检测",
      improvement: "误报率降低 60%+"
    },
  ];

  painPoints.forEach((p, i) => {
    const x = 0.5 + i * 3.1;

    // 卡片背景
    slide4.addShape(pres.shapes.RECTANGLE, {
      x: x, y: 1.4, w: 2.9, h: 3.9,
      fill: { color: COLORS.white },
      shadow: makeCardShadow()
    });

    // 顶部强调条
    slide4.addShape(pres.shapes.RECTANGLE, {
      x: x, y: 1.4, w: 2.9, h: 0.1,
      fill: { color: COLORS.secondary }
    });

    // 序号
    slide4.addText(p.symbol, {
      x: x, y: 1.6, w: 2.9, h: 0.4,
      fontSize: 14, fontFace: "Arial",
      color: COLORS.accent, align: "center"
    });

    // 标题
    slide4.addText(p.title, {
      x: x, y: 2.05, w: 2.9, h: 0.5,
      fontSize: 20, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.text, align: "center", margin: 0
    });

    // 问题标签
    slide4.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.3, y: 2.7, w: 2.3, h: 0.4,
      fill: { color: COLORS.danger, transparency: 85 }
    });
    slide4.addText("X " + p.problem, {
      x: x + 0.3, y: 2.7, w: 2.3, h: 0.4,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.danger, align: "center", valign: "middle"
    });

    // 箭头
    slide4.addText(">>>", {
      x: x, y: 3.2, w: 2.9, h: 0.3,
      fontSize: 12, fontFace: "Arial",
      color: COLORS.textLight, align: "center"
    });

    // 解决方案标签
    slide4.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.3, y: 3.55, w: 2.3, h: 0.4,
      fill: { color: COLORS.success, transparency: 85 }
    });
    slide4.addText("V " + p.solution, {
      x: x + 0.3, y: 3.55, w: 2.3, h: 0.4,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.success, align: "center", valign: "middle"
    });

    // 提升效果
    slide4.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.3, y: 4.15, w: 2.3, h: 0.9,
      fill: { color: COLORS.accent, transparency: 90 }
    });
    slide4.addText(p.improvement, {
      x: x + 0.3, y: 4.15, w: 2.3, h: 0.9,
      fontSize: 16, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.primary, align: "center", valign: "middle"
    });
  });

  // ============ 幻灯片 5: 解决方案架构 ============
  let slide5 = pres.addSlide();
  slide5.background = { color: COLORS.dark };

  // 左侧装饰
  slide5.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 0.15, h: 5.625,
    fill: { color: COLORS.secondary }
  });

  // 标题
  slide5.addText("03", {
    x: 0.4, y: 0.3, w: 1, h: 0.5,
    fontSize: 24, fontFace: "Arial", bold: true,
    color: COLORS.accent, margin: 0
  });
  slide5.addText("解决方案 — 四层架构", {
    x: 0.4, y: 0.7, w: 5, h: 0.6,
    fontSize: 32, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 四层架构
  const layers = [
    { name: "采集层", desc: "NetFlow 秒级采集", color: COLORS.secondary },
    { name: "分析层", desc: "内存缓冲 + 聚合计算", color: COLORS.accent },
    { name: "检测层", desc: "规则匹配 + 算法分析", color: COLORS.primary },
    { name: "展示层", desc: "WebSocket 全双工推送", color: COLORS.secondary },
  ];

  layers.forEach((layer, i) => {
    const y = 1.6 + i * 0.95;

    // 连接线
    if (i < 3) {
      slide5.addShape(pres.shapes.RECTANGLE, {
        x: 1.3, y: y + 0.75, w: 0.04, h: 0.2,
        fill: { color: COLORS.textLight }
      });
    }

    // 层级卡片
    slide5.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: y, w: 2.2, h: 0.75,
      fill: { color: layer.color },
      shadow: makeButtonShadow()
    });
    slide5.addText(layer.name, {
      x: 0.5, y: y, w: 2.2, h: 0.75,
      fontSize: 18, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.white, align: "center", valign: "middle"
    });

    // 箭头
    slide5.addShape(pres.shapes.RECTANGLE, {
      x: 2.9, y: y + 0.3, w: 0.5, h: 0.15,
      fill: { color: COLORS.textLight }
    });

    // 描述
    slide5.addText(layer.desc, {
      x: 3.6, y: y, w: 3, h: 0.75,
      fontSize: 16, fontFace: "Microsoft YaHei",
      color: COLORS.white, valign: "middle"
    });
  });

  // 右侧数据流
  slide5.addShape(pres.shapes.RECTANGLE, {
    x: 7, y: 1.4, w: 2.6, h: 3.9,
    fill: { color: COLORS.white, transparency: 95 },
    line: { color: COLORS.secondary, width: 1 }
  });

  slide5.addText("数据流向", {
    x: 7, y: 1.55, w: 2.6, h: 0.4,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.accent, align: "center", margin: 0
  });

  const flowSteps = [
    "高吞吐链路",
    "秒级NetFlow采集",
    "内存缓冲",
    "实时聚合计算",
    "WebSocket推送",
    "PostgreSQL持久化"
  ];

  flowSteps.forEach((step, i) => {
    const y = 2.1 + i * 0.5;
    slide5.addShape(pres.shapes.OVAL, {
      x: 7.2, y: y + 0.08, w: 0.18, h: 0.18,
      fill: { color: COLORS.secondary }
    });
    slide5.addText(step, {
      x: 7.5, y: y, w: 2, h: 0.35,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.white, valign: "middle"
    });
    if (i < flowSteps.length - 1) {
      slide5.addShape(pres.shapes.RECTANGLE, {
        x: 7.27, y: y + 0.26, w: 0.04, h: 0.24,
        fill: { color: COLORS.textLight }
      });
    }
  });

  // ============ 幻灯片 6: 核心功能 - 全景大屏 ============
  let slide6 = pres.addSlide();
  slide6.background = { color: COLORS.light };

  // 标题区
  slide6.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.1,
    fill: { color: COLORS.dark }
  });
  slide6.addText("04  核心功能 — 实时全景大屏", {
    x: 0.5, y: 0.3, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 功能列表
  const dashboardFeatures = [
    { title: "核心指标卡片", desc: "实时吞吐量、PPS、活跃IP数、告警总数" },
    { title: "吞吐趋势图", desc: "时间桶对齐折线图，60fps流畅刷新" },
    { title: "协议分布饼图", desc: "HTTP/HTTPS/DNS等27种协议实时分析" },
    { title: "Top-K突发大流", desc: "按流量排序Top-10，点击直接下钻" },
    { title: "区域热力图", desc: "宿舍区/教学区/行政区/科研区分布" },
  ];

  dashboardFeatures.forEach((f, i) => {
    const y = 1.4 + i * 0.78;

    // 卡片
    slide6.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: y, w: 4.3, h: 0.7,
      fill: { color: COLORS.white },
      shadow: makeCardShadow()
    });

    // 左侧强调条
    slide6.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: y, w: 0.06, h: 0.7,
      fill: { color: COLORS.secondary }
    });

    // 标题
    slide6.addText(f.title, {
      x: 0.7, y: y + 0.1, w: 3.9, h: 0.3,
      fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.text, margin: 0
    });

    // 描述
    slide6.addText(f.desc, {
      x: 0.7, y: y + 0.38, w: 3.9, h: 0.28,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, margin: 0
    });
  });

  // 右侧截图区域
  slide6.addShape(pres.shapes.RECTANGLE, {
    x: 5.1, y: 1.4, w: 4.4, h: 3.9,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });
  slide6.addShape(pres.shapes.RECTANGLE, {
    x: 5.1, y: 1.4, w: 4.4, h: 0.45,
    fill: { color: COLORS.primary }
  });
  slide6.addText("Dashboard 实时预览", {
    x: 5.1, y: 1.4, w: 4.4, h: 0.45,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  // 模拟图表
  slide6.addShape(pres.shapes.RECTANGLE, {
    x: 5.3, y: 2.0, w: 2, h: 1.2,
    fill: { color: COLORS.accent, transparency: 90 }
  });
  slide6.addText("吞吐量趋势", {
    x: 5.3, y: 2.0, w: 2, h: 0.3,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: COLORS.textLight, align: "center"
  });

  slide6.addShape(pres.shapes.RECTANGLE, {
    x: 7.5, y: 2.0, w: 1.8, h: 1.2,
    fill: { color: COLORS.secondary, transparency: 90 }
  });
  slide6.addText("协议分布", {
    x: 7.5, y: 2.0, w: 1.8, h: 0.3,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: COLORS.textLight, align: "center"
  });

  // KPI卡片
  const kpis = [
    { value: "1.2", unit: "Gbps", label: "吞吐量" },
    { value: "58K", unit: "PPS", label: "每秒包数" },
    { value: "2,847", unit: "", label: "活跃IP" },
    { value: "23", unit: "", label: "告警数" },
  ];

  kpis.forEach((kpi, i) => {
    const x = 5.3 + i * 1.05;
    slide6.addShape(pres.shapes.RECTANGLE, {
      x: x, y: 3.4, w: 0.95, h: 1.0,
      fill: { color: COLORS.dark }
    });
    slide6.addText(kpi.value, {
      x: x, y: 3.5, w: 0.95, h: 0.4,
      fontSize: 16, fontFace: "Arial", bold: true,
      color: COLORS.accent, align: "center", margin: 0
    });
    slide6.addText(kpi.unit, {
      x: x, y: 3.85, w: 0.95, h: 0.2,
      fontSize: 9, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, align: "center", margin: 0
    });
    slide6.addText(kpi.label, {
      x: x, y: 4.05, w: 0.95, h: 0.25,
      fontSize: 10, fontFace: "Microsoft YaHei",
      color: COLORS.white, align: "center", margin: 0
    });
  });

  // ============ 幻灯片 7: 核心功能 - 安全威胁感知 ============
  let slide7 = pres.addSlide();
  slide7.background = { color: COLORS.light };

  // 标题区
  slide7.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.1,
    fill: { color: COLORS.dark }
  });
  slide7.addText("04  核心功能 — 安全威胁感知中心", {
    x: 0.5, y: 0.3, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 左侧：威胁类型
  slide7.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.4, w: 4.3, h: 3.9,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });

  slide7.addText("双层威胁检测体系", {
    x: 0.5, y: 1.55, w: 4.3, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.primary, align: "center", margin: 0
  });

  // 规则层
  slide7.addShape(pres.shapes.RECTANGLE, {
    x: 0.7, y: 2.1, w: 3.9, h: 0.35,
    fill: { color: COLORS.secondary }
  });
  slide7.addText("规则驱动层 — 6种典型威胁", {
    x: 0.7, y: 2.1, w: 3.9, h: 0.35,
    fontSize: 12, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  const ruleThreats = ["DDoS攻击", "端口扫描", "慢速扫描", "蠕虫传播", "钓鱼攻击", "数据外泄"];
  ruleThreats.forEach((t, i) => {
    const row = Math.floor(i / 2);
    const col = i % 2;
    const x = 0.8 + col * 1.9;
    const y = 2.6 + row * 0.45;
    slide7.addShape(pres.shapes.OVAL, {
      x: x, y: y + 0.08, w: 0.18, h: 0.18,
      fill: { color: COLORS.danger }
    });
    slide7.addText(t, {
      x: x + 0.25, y: y, w: 1.5, h: 0.35,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: COLORS.text, valign: "middle"
    });
  });

  // 算法层
  slide7.addShape(pres.shapes.RECTANGLE, {
    x: 0.7, y: 4.0, w: 3.9, h: 0.35,
    fill: { color: COLORS.accent }
  });
  slide7.addText("算法增强层 — 4套原创算法", {
    x: 0.7, y: 4.0, w: 3.9, h: 0.35,
    fontSize: 12, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  slide7.addText("EWMA-EAD | MDTCE | NBF-RS | TPM-PA", {
    x: 0.7, y: 4.45, w: 3.9, h: 0.4,
    fontSize: 11, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.primary, align: "center"
  });

  // 右侧：3D地球展示
  slide7.addShape(pres.shapes.RECTANGLE, {
    x: 5.1, y: 1.4, w: 4.4, h: 3.9,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });

  slide7.addShape(pres.shapes.RECTANGLE, {
    x: 5.1, y: 1.4, w: 4.4, h: 0.45,
    fill: { color: COLORS.primary }
  });
  slide7.addText("3D地球攻击溯源地图", {
    x: 5.1, y: 1.4, w: 4.4, h: 0.45,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  // 地球模拟
  slide7.addShape(pres.shapes.OVAL, {
    x: 6.3, y: 2.1, w: 2, h: 2,
    fill: { color: COLORS.dark }
  });
  slide7.addShape(pres.shapes.OVAL, {
    x: 6.4, y: 2.2, w: 1.8, h: 1.8,
    fill: { color: COLORS.primary, transparency: 50 }
  });

  // 攻击点
  const attackPoints = [
    { x: 6.6, y: 2.5 },
    { x: 7.2, y: 2.8 },
    { x: 6.9, y: 3.3 },
    { x: 7.5, y: 3.0 },
  ];
  attackPoints.forEach(p => {
    slide7.addShape(pres.shapes.OVAL, {
      x: p.x, y: p.y, w: 0.15, h: 0.15,
      fill: { color: COLORS.danger }
    });
  });

  // 图例
  slide7.addText("● 攻击源    ● 目标位置", {
    x: 5.3, y: 4.2, w: 4, h: 0.3,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: COLORS.textLight
  });

  slide7.addText("WebGL渲染 | Bloom泛光特效 | Jitter坐标抖动", {
    x: 5.3, y: 4.6, w: 4, h: 0.3,
    fontSize: 10, fontFace: "Microsoft YaHei",
    color: COLORS.accent
  });

  // ============ 幻灯片 8: 核心功能 - 流级检索 ============
  let slide8 = pres.addSlide();
  slide8.background = { color: COLORS.light };

  // 标题区
  slide8.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.1,
    fill: { color: COLORS.dark }
  });
  slide8.addText("04  核心功能 — 微观流级检索引擎", {
    x: 0.5, y: 0.3, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 检索能力
  slide8.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.4, w: 4.5, h: 3.9,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });

  slide8.addText("[ 检索 ]", {
    x: 0.5, y: 1.6, w: 4.5, h: 0.6,
    fontSize: 24, fontFace: "Arial", bold: true,
    color: COLORS.accent, align: "center"
  });

  slide8.addText("类Wireshark Web检索引擎", {
    x: 0.5, y: 2.3, w: 4.5, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.primary, align: "center", margin: 0
  });

  const searchFeatures = [
    "源/目IP精确 + CIDR通配检索",
    "端口范围、协议类型筛选",
    "应用协议、时间范围联合查询",
    "分页展示完整五元组",
    "CSV一键导出取证",
    "任意IP点击下钻画像"
  ];

  searchFeatures.forEach((f, i) => {
    slide8.addShape(pres.shapes.OVAL, {
      x: 0.8, y: 2.85 + i * 0.38, w: 0.15, h: 0.15,
      fill: { color: COLORS.success }
    });
    slide8.addText(f, {
      x: 1.05, y: 2.8 + i * 0.38, w: 3.8, h: 0.35,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: COLORS.text, valign: "middle"
    });
  });

  // 右侧：IP画像
  slide8.addShape(pres.shapes.RECTANGLE, {
    x: 5.2, y: 1.4, w: 4.3, h: 3.9,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });

  slide8.addShape(pres.shapes.RECTANGLE, {
    x: 5.2, y: 1.4, w: 4.3, h: 0.45,
    fill: { color: COLORS.secondary }
  });
  slide8.addText("单IP详情画像", {
    x: 5.2, y: 1.4, w: 4.3, h: 0.45,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center", valign: "middle"
  });

  const profileItems = [
    { label: "IP地址", value: "192.168.1.105" },
    { label: "总流量", value: "2.35 GB" },
    { label: "活跃时长", value: "4h 23m" },
    { label: "通信对端", value: "128 个" },
  ];

  profileItems.forEach((item, i) => {
    const y = 2.0 + i * 0.6;
    slide8.addShape(pres.shapes.RECTANGLE, {
      x: 5.4, y: y, w: 3.9, h: 0.5,
      fill: { color: COLORS.light }
    });
    slide8.addText(item.label, {
      x: 5.5, y: y, w: 1.5, h: 0.5,
      fontSize: 12, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, valign: "middle"
    });
    slide8.addText(item.value, {
      x: 7, y: y, w: 2.2, h: 0.5,
      fontSize: 14, fontFace: "Arial", bold: true,
      color: COLORS.primary, valign: "middle", align: "right"
    });
  });

  // 六层下钻
  slide8.addText("六层下钻路径", {
    x: 5.4, y: 4.45, w: 3.9, h: 0.3,
    fontSize: 11, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.accent, margin: 0
  });

  const drillLevels = ["全网", "区域", "楼宇", "交换机", "端口", "IP"];
  drillLevels.forEach((level, i) => {
    const x = 5.4 + i * 0.65;
    slide8.addShape(pres.shapes.RECTANGLE, {
      x: x, y: 4.8, w: 0.55, h: 0.35,
      fill: { color: i === 5 ? COLORS.accent : COLORS.primary }
    });
    slide8.addText(level, {
      x: x, y: 4.8, w: 0.55, h: 0.35,
      fontSize: 9, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.white, align: "center", valign: "middle"
    });
    if (i < 5) {
      slide8.addText(">", {
        x: x + 0.55, y: 4.8, w: 0.1, h: 0.35,
        fontSize: 10, color: COLORS.textLight, valign: "middle"
      });
    }
  });

  // ============ 幻灯片 9: 算法创新 ============
  let slide9 = pres.addSlide();
  slide9.background = { color: COLORS.dark };

  // 左侧装饰
  slide9.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 0.15, h: 5.625,
    fill: { color: COLORS.secondary }
  });

  // 标题
  slide9.addText("05", {
    x: 0.4, y: 0.3, w: 1, h: 0.5,
    fontSize: 24, fontFace: "Arial", bold: true,
    color: COLORS.accent, margin: 0
  });
  slide9.addText("算法创新 — 四套原创算法", {
    x: 0.4, y: 0.7, w: 6, h: 0.6,
    fontSize: 32, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 四大算法
  const algorithms = [
    {
      name: "EWMA-EAD",
      full: "指数加权熵异常检测",
      desc: "融合流量统计与协议Shannon熵",
      effect: "误报率降低60%+"
    },
    {
      name: "MDTCE",
      full: "多维威胁关联引擎",
      desc: "基于Kill Chain攻击阶段图建模",
      effect: "识别完整攻击链"
    },
    {
      name: "NBF-RS",
      full: "7维行为指纹评分",
      desc: "协议熵、对端多样性等7维特征",
      effect: "综合风险评估"
    },
    {
      name: "TPM-PA",
      full: "时序预测预警",
      desc: "Pearson跨信号相关+趋势预测",
      effect: "预防性告警"
    },
  ];

  algorithms.forEach((algo, i) => {
    const row = Math.floor(i / 2);
    const col = i % 2;
    const x = 0.5 + col * 4.7;
    const y = 1.5 + row * 1.95;

    // 卡片
    slide9.addShape(pres.shapes.RECTANGLE, {
      x: x, y: y, w: 4.4, h: 1.75,
      fill: { color: COLORS.white, transparency: 92 },
      line: { color: COLORS.secondary, width: 1 }
    });

    // 左侧强调
    slide9.addShape(pres.shapes.RECTANGLE, {
      x: x, y: y, w: 0.1, h: 1.75,
      fill: { color: COLORS.accent }
    });

    // 算法名
    slide9.addText(algo.name, {
      x: x + 0.25, y: y + 0.15, w: 1.8, h: 0.45,
      fontSize: 22, fontFace: "Arial", bold: true,
      color: COLORS.accent, margin: 0
    });

    // 全称
    slide9.addText(algo.full, {
      x: x + 0.25, y: y + 0.6, w: 4, h: 0.35,
      fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.white, margin: 0
    });

    // 描述
    slide9.addText(algo.desc, {
      x: x + 0.25, y: y + 0.95, w: 4, h: 0.35,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, margin: 0
    });

    // 效果标签
    slide9.addShape(pres.shapes.RECTANGLE, {
      x: x + 0.25, y: y + 1.3, w: 2.2, h: 0.35,
      fill: { color: COLORS.success, transparency: 70 }
    });
    slide9.addText("V " + algo.effect, {
      x: x + 0.25, y: y + 1.3, w: 2.2, h: 0.35,
      fontSize: 11, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.success, valign: "middle"
    });
  });

  // ============ 幻灯片 10: 技术亮点 ============
  let slide10 = pres.addSlide();
  slide10.background = { color: COLORS.light };

  // 标题区
  slide10.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.1,
    fill: { color: COLORS.dark }
  });
  slide10.addText("05  技术亮点", {
    x: 0.5, y: 0.3, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 技术亮点卡片
  const techHighlights = [
    {
      symbol: "[ G ]",
      title: "3D地球可视化",
      points: ["Canvas内存纹理生成", "Jitter坐标抖动算法", "WebGL Bloom泛光特效"]
    },
    {
      symbol: "[ D ]",
      title: "12Gbps智能演示数据",
      points: ["启动自检自动补充", "每分钟持续注入", "5分钟过期自动重建"]
    },
    {
      symbol: "[ R ]",
      title: "高性能实时处理",
      points: ["CompletableFuture并行", "WebSocket全双工推送", "JSONB威胁上下文存储"]
    },
    {
      symbol: "[ C ]",
      title: "Docker一键部署",
      points: ["容器化环境隔离", "一键启动完整系统", "评审可完整复现"]
    },
  ];

  techHighlights.forEach((h, i) => {
    const row = Math.floor(i / 2);
    const col = i % 2;
    const x = 0.5 + col * 4.7;
    const y = 1.4 + row * 2.0;

    // 卡片
    slide10.addShape(pres.shapes.RECTANGLE, {
      x: x, y: y, w: 4.4, h: 1.8,
      fill: { color: COLORS.white },
      shadow: makeCardShadow()
    });

    // 符号
    slide10.addText(h.symbol, {
      x: x + 0.15, y: y + 0.2, w: 0.6, h: 0.5,
      fontSize: 16, fontFace: "Arial", bold: true,
      color: COLORS.accent
    });

    // 标题
    slide10.addText(h.title, {
      x: x + 0.8, y: y + 0.25, w: 3.3, h: 0.4,
      fontSize: 16, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.text, margin: 0
    });

    // 要点
    h.points.forEach((p, j) => {
      slide10.addShape(pres.shapes.OVAL, {
        x: x + 0.25, y: y + 0.95 + j * 0.28, w: 0.12, h: 0.12,
        fill: { color: COLORS.secondary }
      });
      slide10.addText(p, {
        x: x + 0.45, y: y + 0.88 + j * 0.28, w: 3.7, h: 0.28,
        fontSize: 11, fontFace: "Microsoft YaHei",
        color: COLORS.textLight, margin: 0
      });
    });
  });

  // ============ 幻灯片 11: 技术栈 ============
  let slide11 = pres.addSlide();
  slide11.background = { color: COLORS.dark };

  // 左侧装饰
  slide11.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 0.15, h: 5.625,
    fill: { color: COLORS.secondary }
  });

  // 标题
  slide11.addText("技术栈", {
    x: 0.4, y: 0.3, w: 9, h: 0.6,
    fontSize: 32, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 技术栈表格
  const techStack = [
    { layer: "前端框架", tech: "React 18 + Vite 5", desc: "组件化开发，极速构建HMR" },
    { layer: "状态管理", tech: "Zustand", desc: "轻量无样板，跨组件共享" },
    { layer: "可视化", tech: "ECharts 5 + ECharts-GL", desc: "WebGL 3D地球渲染" },
    { layer: "后端框架", tech: "Spring Boot 3.x", desc: "生产级Java框架" },
    { layer: "数据访问", tech: "Spring Data JPA", desc: "Actuator监控开箱即用" },
    { layer: "数据库", tech: "PostgreSQL 15", desc: "JSONB列存储威胁上下文" },
    { layer: "实时通信", tech: "Spring WebSocket", desc: "原生低延迟全双工推送" },
    { layer: "容器化", tech: "Docker + Docker Compose", desc: "一键部署，环境隔离" },
  ];

  techStack.forEach((t, i) => {
    const y = 1.1 + i * 0.55;
    const isEven = i % 2 === 0;

    // 行背景
    slide11.addShape(pres.shapes.RECTANGLE, {
      x: 0.4, y: y, w: 9.2, h: 0.5,
      fill: { color: COLORS.white, transparency: isEven ? 95 : 92 }
    });

    // 层级标签
    slide11.addShape(pres.shapes.RECTANGLE, {
      x: 0.4, y: y, w: 1.3, h: 0.5,
      fill: { color: COLORS.secondary }
    });
    slide11.addText(t.layer, {
      x: 0.4, y: y, w: 1.3, h: 0.5,
      fontSize: 11, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.white, align: "center", valign: "middle"
    });

    // 技术
    slide11.addText(t.tech, {
      x: 1.85, y: y, w: 2.5, h: 0.5,
      fontSize: 12, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.accent, valign: "middle"
    });

    // 描述
    slide11.addText(t.desc, {
      x: 4.4, y: y, w: 5, h: 0.5,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.textLight, valign: "middle"
    });
  });

  // ============ 幻灯片 12: 部署方式 ============
  let slide12 = pres.addSlide();
  slide12.background = { color: COLORS.light };

  // 标题区
  slide12.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 1.1,
    fill: { color: COLORS.dark }
  });
  slide12.addText("部署与运行", {
    x: 0.5, y: 0.3, w: 9, h: 0.6,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 服务架构
  slide12.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 1.4, w: 4.4, h: 3.9,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });

  slide12.addText("系统服务架构", {
    x: 0.5, y: 1.55, w: 4.4, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.primary, align: "center", margin: 0
  });

  // 服务卡片
  const services = [
    { name: "Nginx", desc: "反向代理 + 静态托管", color: COLORS.secondary },
    { name: "Spring Boot", desc: "REST API + WebSocket", color: COLORS.primary },
    { name: "PostgreSQL", desc: "流量与告警存储", color: COLORS.accent },
  ];

  services.forEach((s, i) => {
    const y = 2.2 + i * 1.0;
    slide12.addShape(pres.shapes.RECTANGLE, {
      x: 0.8, y: y, w: 3.8, h: 0.8,
      fill: { color: s.color },
      shadow: makeButtonShadow()
    });
    slide12.addText(s.name, {
      x: 0.8, y: y + 0.1, w: 3.8, h: 0.35,
      fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.white, align: "center", margin: 0
    });
    slide12.addText(s.desc, {
      x: 0.8, y: y + 0.45, w: 3.8, h: 0.3,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.white, align: "center", margin: 0
    });
  });

  // 启动命令
  slide12.addShape(pres.shapes.RECTANGLE, {
    x: 5.2, y: 1.4, w: 4.3, h: 1.6,
    fill: { color: COLORS.dark }
  });
  slide12.addText("部署架构", {
    x: 5.2, y: 1.5, w: 4.3, h: 0.35,
    fontSize: 12, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.accent, align: "center", margin: 0
  });
  slide12.addText("宝塔面板 + Nginx + JDK 17", {
    x: 5.4, y: 1.9, w: 3.9, h: 0.35,
    fontSize: 13, fontFace: "Microsoft YaHei",
    color: COLORS.success
  });
  slide12.addText("Windows Server 2022", {
    x: 5.4, y: 2.3, w: 3.9, h: 0.35,
    fontSize: 11, fontFace: "Microsoft YaHei",
    color: COLORS.textLight
  });

  // 访问地址
  slide12.addShape(pres.shapes.RECTANGLE, {
    x: 5.2, y: 3.2, w: 4.3, h: 2.1,
    fill: { color: COLORS.white },
    shadow: makeCardShadow()
  });
  slide12.addText("公网访问地址", {
    x: 5.2, y: 3.35, w: 4.3, h: 0.35,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.primary, align: "center", margin: 0
  });

  const accessPoints = [
    { label: "前端大屏", url: "http://8.146.228.64:3000" },
    { label: "后端API", url: "http://8.146.228.64:8080" },
    { label: "API文档", url: "http://8.146.228.64:8080/swagger-ui" },
  ];

  accessPoints.forEach((a, i) => {
    const y = 3.8 + i * 0.5;
    slide12.addText(a.label + ":", {
      x: 5.4, y: y, w: 1.2, h: 0.4,
      fontSize: 11, fontFace: "Microsoft YaHei",
      color: COLORS.textLight
    });
    slide12.addText(a.url, {
      x: 6.6, y: y, w: 2.7, h: 0.4,
      fontSize: 11, fontFace: "Consolas",
      color: COLORS.accent
    });
  });

  // ============ 幻灯片 13: 总结展望 ============
  let slide13 = pres.addSlide();
  slide13.background = { color: COLORS.dark };

  // 顶部装饰
  slide13.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 10, h: 0.08,
    fill: { color: COLORS.accent }
  });

  // 左侧装饰
  slide13.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0.5, w: 0.15, h: 4.5,
    fill: { color: COLORS.secondary }
  });

  // 标题
  slide13.addText("06", {
    x: 0.4, y: 0.3, w: 1, h: 0.5,
    fontSize: 24, fontFace: "Arial", bold: true,
    color: COLORS.accent, margin: 0
  });
  slide13.addText("总结与展望", {
    x: 0.4, y: 0.7, w: 5, h: 0.6,
    fontSize: 36, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, margin: 0
  });

  // 三大价值
  slide13.addText("项目核心价值", {
    x: 0.5, y: 1.5, w: 9, h: 0.4,
    fontSize: 16, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.accent, margin: 0
  });

  const values = [
    { title: "技术创新", desc: "四套原创算法实现从「被动响应」到「主动预防」的能力跃升" },
    { title: "工程实践", desc: "Docker Compose一键部署，智能演示数据自维护确保高可用性" },
    { title: "用户体验", desc: "对标企业级NetFlow Analyzer产品，兼顾专业性与易用性" },
  ];

  values.forEach((v, i) => {
    const y = 2.0 + i * 0.7;
    slide13.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: y, w: 0.08, h: 0.55,
      fill: { color: COLORS.secondary }
    });
    slide13.addText(v.title, {
      x: 0.7, y: y, w: 1.5, h: 0.55,
      fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
      color: COLORS.accent, valign: "middle"
    });
    slide13.addText(v.desc, {
      x: 2.2, y: y, w: 7.3, h: 0.55,
      fontSize: 13, fontFace: "Microsoft YaHei",
      color: COLORS.white, valign: "middle"
    });
  });

  // 应用前景
  slide13.addShape(pres.shapes.RECTANGLE, {
    x: 0.5, y: 4.2, w: 9, h: 1.1,
    fill: { color: COLORS.white, transparency: 95 },
    line: { color: COLORS.secondary, width: 1 }
  });
  slide13.addText("应用前景", {
    x: 0.7, y: 4.35, w: 2, h: 0.35,
    fontSize: 14, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.accent, margin: 0
  });
  slide13.addText("系统架构具有良好的通用性与可扩展性，可推广至高校、企业园区网、政务专网、数据中心等场景，具有显著的应用价值与行业示范意义。", {
    x: 0.7, y: 4.7, w: 8.6, h: 0.5,
    fontSize: 12, fontFace: "Microsoft YaHei",
    color: COLORS.textLight
  });

  // ============ 幻灯片 14: 致谢 ============
  let slide14 = pres.addSlide();
  slide14.background = { color: COLORS.dark };

  // 装饰圆
  slide14.addShape(pres.shapes.OVAL, {
    x: -1, y: -1, w: 4, h: 4,
    fill: { color: COLORS.primary, transparency: 60 }
  });
  slide14.addShape(pres.shapes.OVAL, {
    x: 7, y: 3, w: 4, h: 4,
    fill: { color: COLORS.secondary, transparency: 70 }
  });

  // 致谢
  slide14.addText("感谢聆听", {
    x: 0, y: 1.8, w: 10, h: 1,
    fontSize: 56, fontFace: "Microsoft YaHei", bold: true,
    color: COLORS.white, align: "center"
  });

  slide14.addText("THANK YOU", {
    x: 0, y: 2.7, w: 10, h: 0.6,
    fontSize: 24, fontFace: "Arial",
    color: COLORS.accent, align: "center", charSpacing: 8
  });

  // 分隔线
  slide14.addShape(pres.shapes.RECTANGLE, {
    x: 3.5, y: 3.5, w: 3, h: 0.03,
    fill: { color: COLORS.secondary }
  });

  // 团队信息
  slide14.addText("苏州大学  无敌暴龙战队", {
    x: 0, y: 3.8, w: 10, h: 0.5,
    fontSize: 18, fontFace: "Microsoft YaHei",
    color: COLORS.textLight, align: "center"
  });
  // 底部信息
  slide14.addText("2026年中国大学生计算机设计大赛", {
    x: 0, y: 5.1, w: 10, h: 0.35,
    fontSize: 12, fontFace: "Microsoft YaHei",
    color: COLORS.textLight, align: "center"
  });

  // 保存文件
  const outputPath = "d:/ASUS/Documents/jiedan/waibao/计算机设计大赛答辩演示_无敌暴龙战队.pptx";
  await pres.writeFile({ fileName: outputPath });
  console.log(`Presentation saved to: ${outputPath}`);
}

createPresentation().catch(console.error);
