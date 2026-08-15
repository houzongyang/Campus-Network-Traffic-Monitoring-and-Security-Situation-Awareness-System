const {
  Document, Packer, Paragraph, TextRun,
  ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel,
  BorderStyle, TableLayoutType, ShadingType
} = require('docx');
const fs = require('fs');
const path = require('path');

const imgDir = path.resolve(__dirname);

const commonFont = 'KaiTi';
const commonSize = 24; // 小四 = 12pt = 24 half-pt

function heading1(text) {
  return new Paragraph({
    spacing: { after: 120, before: 240 },
    children: [new TextRun({ text, bold: true, font: commonFont, size: 30 })],
  });
}

function heading2(text) {
  return new Paragraph({
    spacing: { after: 80, before: 160 },
    children: [new TextRun({ text, bold: true, font: commonFont, size: commonSize })],
  });
}

function para(text) {
  return new Paragraph({
    spacing: { after: 60, line: 240 },
    indent: { firstLine: 480 },
    children: [new TextRun({ text, font: commonFont, size: commonSize })],
  });
}

function caption(text) {
  return new Paragraph({
    spacing: { after: 120, before: 60 },
    alignment: AlignmentType.CENTER,
    children: [new TextRun({ text, bold: true, font: commonFont, size: commonSize })],
  });
}

function imgParagraph(file) {
  const buf = fs.readFileSync(path.join(imgDir, file));
  const size = buf.length;
  const w = 560; // docx units, roughly 15cm
  const ratio = 0.5625; // assume 16:9 screenshots
  const h = Math.round(w * ratio);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 40 },
    children: [new ImageRun({ data: buf, transformation: { width: w, height: h }, type: 'png' })],
  });
}

async function main() {
  const children = [];

  // 3.1 界面设计
  children.push(heading1('3.1  界面设计'));

  children.push(para(
    '系统前端基于React 18构建单页应用（SPA），采用深色主题以匹配网络安全态势感知场景。整体布局为顶部导航栏 + 下方内容区，共包含4个核心页面：全景画像、安全态势、微观流检索、单IP详情，通过状态管理实现无刷新页面切换。'
  ));

  // 全景画像
  children.push(heading2('（1）全景画像'));
  children.push(para(
    '全景画像是系统首页，提供全网流量的实时可视化总览。页面顶部为四个核心指标卡（实时吞吐量、包速率、活跃IP数、监控状态），通过WebSocket实现秒级推送；中部为四个图表区域：链路吞吐变化折线图（同时展示Mbps与PPS）、应用协议分布饼图、区域/楼宇透视柱状图（支持区域-楼宇-交换机多层级下钻）、区域流量热力图；底部为Top大流表格，支持按字节或包数切换视图。'
  ));
  children.push(imgParagraph('screenshot_dashboard.png'));
  children.push(caption('图3-1  全景画像界面'));

  // 安全态势
  children.push(heading2('（2）安全态势'));
  children.push(para(
    '安全态势页面集中展示安全威胁检测与全球攻击态势。页面顶部为多条件筛选表单，支持按威胁类型、严重级别、源/目标IP、关键词等组合查询，筛选条件自动持久化；中部为四个指标卡（告警总数、高危/严重数、威胁类型数、地理点位数）及三个图表：威胁类型占比饼图、严重级别分布柱状图、基于ECharts GL的三维地球可视化（展示攻击源地理分布及攻击路径，地图审图号：GS(2021)648号）；底部为安全日志分页表格，支持排序与CSV导出。'
  ));
  children.push(imgParagraph('screenshot_security.png'));
  children.push(caption('图3-2  安全态势界面'));

  // 微观流检索
  children.push(heading2('（3）微观流检索'));
  children.push(para(
    '微观流检索页面提供网络流量的细粒度查询能力。页面顶部为多条件过滤表单，支持按时间范围、源/目标IP、协议类型、端口等条件组合检索；下方为检索结果表格，展示流记录详情，每行包含NBF-RS行为指纹风险评分；支持分页浏览与结果导出，帮助安全运维人员快速定位可疑流量。'
  ));
  children.push(imgParagraph('screenshot_flows.png'));
  children.push(caption('图3-3  微观流检索界面'));

  // 单IP详情
  children.push(heading2('（4）单IP详情'));
  children.push(para(
    '单IP详情页对单个IP地址进行深度画像分析。页面顶部为IP基本信息卡片（IP地址、区域、风险等级等）；中部为流量趋势时间线图，展示该IP的历史流量变化；下方左侧为协议分布饼图，右侧为通信对端柱状图，直观呈现该IP的通信行为特征。页面提供返回按钮，支持从微观流检索等入口快速跳转进入。'
  ));
  children.push(imgParagraph('screenshot_ipdetail.png'));
  children.push(caption('图3-4  单IP详情界面'));

  const doc = new Document({
    sections: [{ children }],
  });

  const buf = await Packer.toBuffer(doc);
  fs.writeFileSync(path.join(imgDir, '第三章_界面设计_v2.docx'), buf);
  console.log('OK, size:', buf.length);
}

main().catch(e => { console.error(e); process.exit(1); });
