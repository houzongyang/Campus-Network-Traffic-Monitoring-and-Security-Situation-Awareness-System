const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  WidthType, BorderStyle, AlignmentType, HeadingLevel, convertInchesToTwip
} = require('docx');
const fs = require('fs');

const outputPath = 'D:/ASUS/Documents/jiedan/waibao/waibu/第六章_项目总结_v3.docx';

// 格式设置
const FONT = '楷体';
const FONT_SIZE = 12; // 小四
const MARGIN = {
  top: convertInchesToTwip(0.8),
  bottom: convertInchesToTwip(0.8),
  left: convertInchesToTwip(0.8),
  right: convertInchesToTwip(0.8),
};

function createPara(text, options = {}) {
  return new Paragraph({
    children: [new TextRun({ text, font: FONT, size: FONT_SIZE * 2, ...options })],
    spacing: { line: 360 },
    margin: { left: convertInchesToTwip(0.2) },
  });
}

function createBullet(text, bold = false) {
  return new Paragraph({
    children: [new TextRun({ text: '• ' + text, font: FONT, size: FONT_SIZE * 2, bold })],
    spacing: { line: 300 },
    margin: { left: convertInchesToTwip(0.4) },
  });
}

function createSubTitle(text) {
  return new Paragraph({
    children: [new TextRun({ text, font: '黑体', size: FONT_SIZE * 2, bold: true })],
    spacing: { before: 120, after: 60 },
    margin: { left: convertInchesToTwip(0.1) },
  });
}

const doc = new Document({
  sections: [{
    properties: {
      page: {
        margin: MARGIN,
        size: { width: convertInchesToTwip(8.27), height: convertInchesToTwip(11.69) },
      },
    },
    children: [
      // 标题
      new Paragraph({
        children: [new TextRun({ text: '第六章 项目总结', font: '黑体', size: FONT_SIZE * 2.2, bold: true })],
        alignment: AlignmentType.CENTER,
        spacing: { after: 200 },
      }),

      // 6.1 创新点
      createSubTitle('6.1 创新点'),
      createBullet('EWMA-EAD算法：融合EWMA自适应基线追踪与Shannon熵分析，三维度综合评分检测行为异常'),
      createBullet('NBF-RS算法：提出7维行为指纹向量，采用异常敏感归一化策略降低误报率'),
      createBullet('MDTCE算法：基于Cyber Kill Chain模型构建多维威胁关联图，检测多阶段攻击链'),
      createBullet('TPM-PA算法：多信号线性回归趋势分解实现预测性预警，交叉信号放大提升准确性'),

      // 6.2 项目协调与任务分解
      createSubTitle('6.2 项目协调与任务分解'),
      createBullet('采用"主-辅"协作模式：前端主导页面交互与可视化，后端负责数据接口与业务逻辑，算法人员专注四个创新算法的理论推导与实现'),
      createBullet('通过GitHub仓库进行版本管理，模块化开发确保代码质量和进度可控'),

      // 6.3 克服的困难
      createSubTitle('6.3 克服的困难'),
      createBullet('异构跨云双机部署：解决Windows Server与Linux服务器间的网络通信和数据同步问题'),
      createBullet('3D地球可视化性能：优化WebGL渲染管线，确保大流量数据下流畅展示'),
      createBullet('算法工程化落地：将学术算法转化为高性能生产级代码，实现毫秒级实时检测'),

      // 6.4 能力提升
      createSubTitle('6.4 能力提升'),
      new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        rows: [
          new TableRow({
            children: ['维度', '提升内容'].map(t => new TableCell({
              children: [new Paragraph({
                children: [new TextRun({ text: t, font: FONT, size: FONT_SIZE * 2, bold: true })],
                alignment: AlignmentType.CENTER,
              })],
              shading: { fill: 'E8E8E8' },
              margins: { top: 40, bottom: 40, left: 60, right: 60 },
            })),
          }),
          new TableRow({
            children: ['系统架构', '分布式系统设计、异构跨云部署'].map(t => new TableCell({
              children: [new Paragraph({
                children: [new TextRun({ text: t, font: FONT, size: FONT_SIZE * 2 })],
                alignment: AlignmentType.CENTER,
              })],
              margins: { top: 40, bottom: 40, left: 60, right: 60 },
            })),
          }),
          new TableRow({
            children: ['前端开发', 'React组件化开发、ECharts数据可视化、WebGL 3D渲染'].map(t => new TableCell({
              children: [new Paragraph({
                children: [new TextRun({ text: t, font: FONT, size: FONT_SIZE * 2 })],
                alignment: AlignmentType.CENTER,
              })],
              margins: { top: 40, bottom: 40, left: 60, right: 60 },
            })),
          }),
          new TableRow({
            children: ['后端技术', 'Spring Boot微服务、PostgreSQL数据库、Nginx反向代理'].map(t => new TableCell({
              children: [new Paragraph({
                children: [new TextRun({ text: t, font: FONT, size: FONT_SIZE * 2 })],
                alignment: AlignmentType.CENTER,
              })],
              margins: { top: 40, bottom: 40, left: 60, right: 60 },
            })),
          }),
          new TableRow({
            children: ['算法能力', '网络安全异常检测算法设计、时序数据分析、威胁建模'].map(t => new TableCell({
              children: [new Paragraph({
                children: [new TextRun({ text: t, font: FONT, size: FONT_SIZE * 2 })],
                alignment: AlignmentType.CENTER,
              })],
              margins: { top: 40, bottom: 40, left: 60, right: 60 },
            })),
          }),
          new TableRow({
            children: ['团队协作', 'Git版本管理、代码评审、敏捷迭代开发'].map(t => new TableCell({
              children: [new Paragraph({
                children: [new TextRun({ text: t, font: FONT, size: FONT_SIZE * 2 })],
                alignment: AlignmentType.CENTER,
              })],
              margins: { top: 40, bottom: 40, left: 60, right: 60 },
            })),
          }),
        ],
      }),

      // 6.5 升级演进与商业推广（合并精简）
      createSubTitle('6.5 升级演进与商业推广'),
      createBullet('短期升级：集成实时抓包模块、引入机器学习模型提升检测准确率'),
      createBullet('中期升级：引入Kafka消息队列，实现集群化架构提升可扩展性'),
      createBullet('长期规划：集成自动化响应模块，开放API供第三方集成，构建智慧校园网络安全生态'),
      createBullet('商业推广：面向高校和中小企业，提供SaaS化网络安全监测服务'),
    ],
  }],
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(outputPath, buffer);
  console.log('Generated:', outputPath);
});
