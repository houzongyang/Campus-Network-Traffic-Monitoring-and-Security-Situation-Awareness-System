const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        Header, Footer, AlignmentType, LevelFormat, HeadingLevel,
        BorderStyle, WidthType, ShadingType, VerticalAlign, PageNumber,
        PageBreak, UnderlineType, convertInchesToTwip } = require('docx');
const fs = require('fs');
const path = require('path');

// A4尺寸 (单位: DXA, 1 inch = 1440 DXA, 1 cm = 567 DXA)
const PAGE_WIDTH = 11906;
const PAGE_HEIGHT = 16838;

// 页边距 (2cm = 1134 DXA, 装订线1cm = 567 DXA)
const MARGIN_TOP = 1134;
const MARGIN_BOTTOM = 1134;
const MARGIN_LEFT = 1134 + 567;  // 左2cm + 装订线1cm
const MARGIN_RIGHT = 1134;

// 计算内容宽度
const CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;  // 9071 DXA

// 标题层级样式
const headingStyles = [
  { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
    run: { size: 36, bold: true, font: "楷体", color: "1F497D" },
    paragraph: { spacing: { before: 480, after: 240 }, outlineLevel: 0 } },
  { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
    run: { size: 32, bold: true, font: "楷体", color: "17375E" },
    paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 1 } },
  { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
    run: { size: 28, bold: true, font: "楷体", color: "2E74B5" },
    paragraph: { spacing: { before: 280, after: 160 }, outlineLevel: 2 } },
];

// 小四号字 = 12pt = 24 half-points
const BODY_FONT_SIZE = 24;
const BODY_LINE_SPACING = 360;  // 单倍行距 (12pt * 1.2 ≈ 14.4pt 换算约360 DXA)

// 创建段落 helper
function createParagraph(text, options = {}) {
  const runs = [];
  if (options.bold) {
    runs.push(new TextRun({ text, bold: true, font: "楷体", size: BODY_FONT_SIZE }));
  } else if (options.italic) {
    runs.push(new TextRun({ text, italics: true, font: "楷体", size: BODY_FONT_SIZE }));
  } else {
    runs.push(new TextRun({ text, font: "楷体", size: BODY_FONT_SIZE }));
  }

  return new Paragraph({
    children: runs,
    alignment: options.alignment || AlignmentType.LEFT,
    spacing: {
      before: options.spaceBefore || 0,
      after: options.spaceAfter || 120,
      line: BODY_LINE_SPACING,
      lineRule: "exact"
    },
    indent: options.indent || undefined
  });
}

// 创建带超链接样式的段落
function createHeading1(text) {
  return new Paragraph({
    children: [new TextRun({ text, bold: true, font: "楷体", size: 36, color: "1F497D" })],
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 480, after: 240, line: BODY_LINE_SPACING, lineRule: "exact" },
    pageBreakBefore: false
  });
}

function createHeading2(text) {
  return new Paragraph({
    children: [new TextRun({ text, bold: true, font: "楷体", size: 32, color: "17375E" })],
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 360, after: 200, line: BODY_LINE_SPACING, lineRule: "exact" }
  });
}

function createHeading3(text) {
  return new Paragraph({
    children: [new TextRun({ text, bold: true, font: "楷体", size: 28, color: "2E74B5" })],
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 280, after: 160, line: BODY_LINE_SPACING, lineRule: "exact" }
  });
}

// 创建正文段落
function createBody(text, options = {}) {
  const runs = [];
  if (options.bold) {
    runs.push(new TextRun({ text, bold: true, font: "楷体", size: BODY_FONT_SIZE }));
  } else if (options.color) {
    runs.push(new TextRun({ text, font: "楷体", size: BODY_FONT_SIZE, color: options.color }));
  } else {
    runs.push(new TextRun({ text, font: "楷体", size: BODY_FONT_SIZE }));
  }

  return new Paragraph({
    children: runs,
    alignment: options.alignment || AlignmentType.LEFT,
    spacing: {
      before: options.spaceBefore || 60,
      after: options.spaceAfter || 60,
      line: BODY_LINE_SPACING,
      lineRule: "exact"
    },
    indent: options.indent || undefined
  });
}

// 创建带项目符号的列表项
function createBulletItem(text, indentLevel = 0) {
  return new Paragraph({
    children: [new TextRun({ text, font: "楷体", size: BODY_FONT_SIZE })],
    numbering: {
      reference: "bullets",
      level: indentLevel
    },
    spacing: {
      before: 60,
      after: 60,
      line: BODY_LINE_SPACING,
      lineRule: "exact"
    }
  });
}

// 创建表格
function createTable(headers, rows, colWidths) {
  const border = { style: BorderStyle.SINGLE, size: 1, color: "666666" };
  const borders = { top: border, bottom: border, left: border, right: border };

  const headerRow = new TableRow({
    children: headers.map((h, i) => new TableCell({
      borders,
      width: { size: colWidths[i], type: WidthType.DXA },
      shading: { fill: "D6DCE4", type: ShadingType.CLEAR },
      margins: { top: 80, bottom: 80, left: 120, right: 120 },
      verticalAlign: VerticalAlign.CENTER,
      children: [new Paragraph({
        children: [new TextRun({ text: h, bold: true, font: "楷体", size: BODY_FONT_SIZE })],
        alignment: AlignmentType.CENTER,
        spacing: { before: 60, after: 60, line: BODY_LINE_SPACING, lineRule: "exact" }
      })]
    }))
  });

  const dataRows = rows.map(row => new TableRow({
    children: row.map((cell, i) => new TableCell({
      borders,
      width: { size: colWidths[i], type: WidthType.DXA },
      margins: { top: 80, bottom: 80, left: 120, right: 120 },
      verticalAlign: VerticalAlign.CENTER,
      children: [new Paragraph({
        children: [new TextRun({ text: cell, font: "楷体", size: BODY_FONT_SIZE })],
        alignment: AlignmentType.LEFT,
        spacing: { before: 60, after: 60, line: BODY_LINE_SPACING, lineRule: "exact" }
      })]
    }))
  }));

  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [headerRow, ...dataRows]
  });
}

// 创建空行
function createEmptyLine(count = 1) {
  const paragraphs = [];
  for (let i = 0; i < count; i++) {
    paragraphs.push(new Paragraph({
      children: [new TextRun({ text: "", font: "楷体", size: BODY_FONT_SIZE })],
      spacing: { before: 60, after: 60, line: BODY_LINE_SPACING, lineRule: "exact" }
    }));
  }
  return paragraphs;
}

// 解析Markdown内容 - 提取表格
function parseMarkdownTable(mdText) {
  const lines = mdText.split('\n').map(l => l.trim()).filter(l => l.length > 0);
  const tableData = [];
  let headers = [];
  let inTable = false;

  for (const line of lines) {
    if (line.startsWith('|')) {
      if (!inTable) {
        inTable = true;
        headers = line.split('|').filter(c => c.trim()).map(c => c.trim());
      } else if (line.match(/^\|[\s\-:|]+\|$/)) {
        // 跳过分隔行
        continue;
      } else {
        const cells = line.split('|').filter(c => c.trim()).map(c => c.trim());
        tableData.push(cells);
      }
    } else {
      inTable = false;
    }
  }

  return { headers, tableData };
}

// 分割文本中包含表格的部分
function splitByTables(text) {
  const parts = [];
  const tableRegex = /(\|[^\n]+\|\n\|[\s\-:|]+\|\n(?:\|[^\n]+\|\n?)+)/g;
  let lastIndex = 0;
  let match;

  while ((match = tableRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ type: 'text', content: text.substring(lastIndex, match.index) });
    }
    parts.push({ type: 'table', content: match[0] });
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < text.length) {
    parts.push({ type: 'text', content: text.substring(lastIndex) });
  }

  return parts;
}

// 提取列表项
function extractListItems(text) {
  const lines = text.split('\n');
  const items = [];
  let currentListItems = [];

  for (const line of lines) {
    const listMatch = line.match(/^[-*]\s+(.+)$/);
    if (listMatch) {
      currentListItems.push(listMatch[1]);
    } else if (line.trim() === '') {
      if (currentListItems.length > 0) {
        items.push({ type: 'endlist', items: [...currentListItems] });
        currentListItems = [];
      }
    } else if (line.match(/^\d+\.\s+/)) {
      if (currentListItems.length > 0) {
        items.push({ type: 'endlist', items: [...currentListItems] });
        currentListItems = [];
      }
    } else if (!line.match(/^\|/) && !line.match(/^#{1,3}\s/) && line.trim()) {
      if (currentListItems.length > 0) {
        items.push({ type: 'endlist', items: [...currentListItems] });
        currentListItems = [];
      }
      items.push({ type: 'text', content: line.trim() });
    }
  }

  if (currentListItems.length > 0) {
    items.push({ type: 'endlist', items: [...currentListItems] });
  }

  return items;
}

// 简单的Markdown解析 - 生成文档内容
function parseMarkdownToContent(mdText) {
  const paragraphs = [];
  const lines = mdText.split('\n');
  let i = 0;
  let inTable = false;
  let currentTableLines = [];

  const processTextBlock = (text) => {
    if (!text || text.trim() === '') return;

    // 检查是否是标题
    if (text.match(/^#{1}\s+(.+)$/)) {
      paragraphs.push(createHeading1(text.replace(/^#{1}\s+/, '')));
    } else if (text.match(/^#{2}\s+(.+)$/)) {
      paragraphs.push(createHeading2(text.replace(/^#{2}\s+/, '')));
    } else if (text.match(/^#{3}\s+(.+)$/)) {
      paragraphs.push(createHeading3(text.replace(/^#{3}\s+/, '')));
    } else {
      // 提取粗体文本
      const cleanText = text.replace(/\*\*(.+?)\*\*/g, '$1');
      paragraphs.push(createBody(cleanText));
    }
  };

  while (i < lines.length) {
    const line = lines[i];

    // 标题处理
    if (line.match(/^#\s+/)) {
      if (inTable && currentTableLines.length > 0) {
        const { headers, tableData } = parseMarkdownTable(currentTableLines.join('\n'));
        if (headers.length > 0) {
          const colWidths = headers.map(() => Math.floor(CONTENT_WIDTH / headers.length));
          paragraphs.push(createTable(headers, tableData, colWidths));
        }
        inTable = false;
        currentTableLines = [];
      }
      processTextBlock(line);
    }
    // 表格处理
    else if (line.match(/^\|/)) {
      inTable = true;
      currentTableLines.push(line);
    }
    // 分隔线
    else if (line.match(/^---+$/)) {
      if (inTable && currentTableLines.length > 0) {
        const { headers, tableData } = parseMarkdownTable(currentTableLines.join('\n'));
        if (headers.length > 0) {
          const colWidths = headers.map(() => Math.floor(CONTENT_WIDTH / headers.length));
          paragraphs.push(createTable(headers, tableData, colWidths));
        }
        inTable = false;
        currentTableLines = [];
      }
      paragraphs.push(...createEmptyLine(1));
    }
    // 列表项
    else if (line.match(/^[-*]\s+(.+)$/)) {
      if (inTable && currentTableLines.length > 0) {
        const { headers, tableData } = parseMarkdownTable(currentTableLines.join('\n'));
        if (headers.length > 0) {
          const colWidths = headers.map(() => Math.floor(CONTENT_WIDTH / headers.length));
          paragraphs.push(createTable(headers, tableData, colWidths));
        }
        inTable = false;
        currentTableLines = [];
      }
      const match = line.match(/^[-*]\s+(.+)$/);
      paragraphs.push(createBulletItem(match[1]));
    }
    // 普通文本
    else if (line.trim()) {
      if (inTable && currentTableLines.length > 0) {
        const { headers, tableData } = parseMarkdownTable(currentTableLines.join('\n'));
        if (headers.length > 0) {
          const colWidths = headers.map(() => Math.floor(CONTENT_WIDTH / headers.length));
          paragraphs.push(createTable(headers, tableData, colWidths));
        }
        inTable = false;
        currentTableLines = [];
      }
      processTextBlock(line.trim());
    }
    // 空行
    else {
      if (inTable && currentTableLines.length > 0) {
        const { headers, tableData } = parseMarkdownTable(currentTableLines.join('\n'));
        if (headers.length > 0) {
          const colWidths = headers.map(() => Math.floor(CONTENT_WIDTH / headers.length));
          paragraphs.push(createTable(headers, tableData, colWidths));
        }
        inTable = false;
        currentTableLines = [];
      }
      paragraphs.push(...createEmptyLine(1));
    }

    i++;
  }

  // 处理末尾的表格
  if (inTable && currentTableLines.length > 0) {
    const { headers, tableData } = parseMarkdownTable(currentTableLines.join('\n'));
    if (headers.length > 0) {
      const colWidths = headers.map(() => Math.floor(CONTENT_WIDTH / headers.length));
      paragraphs.push(createTable(headers, tableData, colWidths));
    }
  }

  return paragraphs;
}

// 读取Markdown文件
const mdFilePath = path.join(__dirname, '项目详细方案.md');
const mdContent = fs.readFileSync(mdFilePath, 'utf-8');

// 创建文档
const doc = new Document({
  styles: {
    default: {
      document: {
        run: { font: "楷体", size: BODY_FONT_SIZE }
      }
    },
    paragraphStyles: headingStyles
  },
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [{
          level: 0,
          format: LevelFormat.BULLET,
          text: "\u2022",
          alignment: AlignmentType.LEFT,
          style: {
            paragraph: {
              indent: { left: 720, hanging: 360 }
            },
            run: { font: "楷体", size: BODY_FONT_SIZE }
          }
        }, {
          level: 1,
          format: LevelFormat.BULLET,
          text: "\u25E6",
          alignment: AlignmentType.LEFT,
          style: {
            paragraph: {
              indent: { left: 1080, hanging: 360 }
            },
            run: { font: "楷体", size: BODY_FONT_SIZE }
          }
        }]
      }
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: PAGE_WIDTH, height: PAGE_HEIGHT },
        margin: {
          top: MARGIN_TOP,
          bottom: MARGIN_BOTTOM,
          left: MARGIN_LEFT,
          right: MARGIN_RIGHT
        }
      }
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          children: [new TextRun({
            text: "面向智慧校园的细粒度网络流量监控与安全态势感知系统",
            font: "楷体",
            size: 18,
            color: "888888"
          })],
          alignment: AlignmentType.RIGHT,
          spacing: { before: 0, after: 0 }
        })]
      })
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          children: [
            new TextRun({ text: "第 ", font: "楷体", size: 18 }),
            new TextRun({ children: [PageNumber.CURRENT], font: "楷体", size: 18 }),
            new TextRun({ text: " 页", font: "楷体", size: 18 })
          ],
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 0 }
        })]
      })
    },
    children: parseMarkdownToContent(mdContent)
  }]
});

// 生成文档
const outputPath = path.join(__dirname, 'T2601487—无敌暴龙战队—A19智慧校园网络流量监控—项目详细方案.docx');
Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(outputPath, buffer);
  console.log('文档生成成功: ' + outputPath);
}).catch(err => {
  console.error('生成失败:', err);
});
