const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        AlignmentType, HeadingLevel, BorderStyle, WidthType, ShadingType,
        VerticalAlign, PageBreak } = require('docx');
const fs = require('fs');

// A4尺寸 (210mm x 297mm)，边距上下左右2cm，装订线1cm
// A4 = 11906 x 16838 DXA (1cm = 567 DXA)
const PAGE_WIDTH = 11906;
const PAGE_HEIGHT = 16838;
const MARGIN = 567; // 2cm
const BINDING = 567; // 1cm装订线
const CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2 - BINDING; // 装订线在左边

// 字体设置 - 小四楷体
const FONT_KAITI = '楷体';
const FONT_HEITI = '黑体';
const FONT_SIZE_BODY = 21; // 小四 = 14pt = 21 half-points
const FONT_SIZE_H1 = 32;   // 一级标题
const FONT_SIZE_H2 = 28;   // 二级标题

const border = { style: BorderStyle.SINGLE, size: 1, color: "000000" };
const borders = { top: border, bottom: border, left: border, right: border };

// 创建标题段落
function heading1(text) {
    return new Paragraph({
        children: [
            new TextRun({
                text: text,
                font: FONT_HEITI,
                size: FONT_SIZE_H1,
                bold: true,
            })
        ],
        spacing: { before: 400, after: 240 },
        alignment: AlignmentType.LEFT,
    });
}

function heading2(text) {
    return new Paragraph({
        children: [
            new TextRun({
                text: text,
                font: FONT_HEITI,
                size: FONT_SIZE_H2,
                bold: true,
            })
        ],
        spacing: { before: 300, after: 180 },
        alignment: AlignmentType.LEFT,
    });
}

function bodyPara(text, indent = true) {
    return new Paragraph({
        children: [
            new TextRun({
                text: text,
                font: FONT_KAITI,
                size: FONT_SIZE_BODY,
            })
        ],
        spacing: { line: 360, lineRule: "atLeast" },
        alignment: AlignmentType.JUSTIFIED,
        indent: indent ? { firstLine: 420 } : undefined,
    });
}

function bulletPara(text) {
    return new Paragraph({
        children: [
            new TextRun({
                text: text,
                font: FONT_KAITI,
                size: FONT_SIZE_BODY,
            })
        ],
        spacing: { line: 360, lineRule: "atLeast" },
        alignment: AlignmentType.JUSTIFIED,
    });
}

// 创建简单表格
function createTable(headers, rows, widths) {
    const headerRow = new TableRow({
        children: headers.map((h, i) => new TableCell({
            borders,
            width: { size: widths[i], type: WidthType.DXA },
            shading: { fill: "D9E2F3", type: ShadingType.CLEAR },
            margins: { top: 80, bottom: 80, left: 120, right: 120 },
            verticalAlign: VerticalAlign.CENTER,
            children: [new Paragraph({
                alignment: AlignmentType.CENTER,
                children: [new TextRun({ text: h, font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })]
            })]
        }))
    });

    const dataRows = rows.map(row => new TableRow({
        children: row.map((cell, i) => new TableCell({
            borders,
            width: { size: widths[i], type: WidthType.DXA },
            margins: { top: 80, bottom: 80, left: 120, right: 120 },
            children: [new Paragraph({
                alignment: AlignmentType.CENTER,
                children: [new TextRun({ text: cell, font: FONT_KAITI, size: FONT_SIZE_BODY })]
            })]
        }))
    }));

    return new Table({
        width: { size: CONTENT_WIDTH, type: WidthType.DXA },
        columnWidths: widths,
        rows: [headerRow, ...dataRows]
    });
}

const doc = new Document({
    sections: [{
        properties: {
            page: {
                size: { width: PAGE_WIDTH, height: PAGE_HEIGHT },
                margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN + BINDING }
            }
        },
        children: [
            // 第五章 标题
            new Paragraph({
                children: [
                    new TextRun({ text: "第五章", font: FONT_HEITI, size: FONT_SIZE_H1, bold: true }),
                    new TextRun({ text: " 安装及使用", font: FONT_HEITI, size: FONT_SIZE_H1, bold: true }),
                ],
                spacing: { before: 300, after: 400 },
                alignment: AlignmentType.CENTER,
            }),

            // 5.1 环境要求
            heading2("5.1  环境要求"),

            heading3("5.1.1  运行环境"),
            bodyPara("系统为Web应用，用户端仅需一台能上网的电脑和一个现代浏览器即可访问使用。"),
            createTable(
                ["项目", "要求"],
                [
                    ["浏览器", "Chrome 100+、Firefox 100+、Edge 100+、Safari 15+"],
                    ["网络", "能访问公网地址即可"],
                    ["分辨率", "推荐1920×1080及以上"],
                ],
                [2400, 6200]
            ),
            new Paragraph({ spacing: { before: 200 } }),

            heading3("5.1.2  服务器环境（运维人员参考）"),
            bodyPara("如需自行部署系统，服务器需满足以下要求："),
            createTable(
                ["项目", "最低配置", "推荐配置"],
                [
                    ["CPU", "4核", "8核"],
                    ["内存", "8GB", "16GB"],
                    ["磁盘", "50GB", "100GB"],
                    ["操作系统", "Windows Server 2019 / Ubuntu 20.04 / CentOS 8", "Windows Server 2022 / Ubuntu 22.04"],
                    ["JDK", "OpenJDK 17", "OpenJDK 17"],
                    ["Node.js", "16.x", "18.x"],
                    ["数据库", "PostgreSQL 13", "PostgreSQL 15"],
                    ["Web服务器", "Nginx", "Nginx（宝塔面板管理）"],
                ],
                [2000, 2800, 2800]
            ),
            new Paragraph({ spacing: { before: 200 } }),

            // 5.2 安装部署
            heading2("5.2  安装部署"),

            heading3("5.2.1  默认安装（使用已部署系统）"),
            bodyPara("本系统已在云端服务器完成部署，用户可直接使用，无需安装任何软件。"),
            new Paragraph({
                children: [
                    new TextRun({ text: "访问地址：", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true }),
                    new TextRun({ text: "http://8.146.228.64:3000", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true }),
                ],
                spacing: { line: 360, lineRule: "atLeast" },
                alignment: AlignmentType.CENTER,
            }),
            bodyPara("打开浏览器，输入上述地址即可进入系统。"),

            heading3("5.2.2  自行部署（运维人员参考）"),
            bodyPara("如需在其他服务器上部署本系统，按以下步骤操作："),
            new Paragraph({
                children: [new TextRun({ text: "步骤1：准备环境", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { line: 360, lineRule: "atLeast" },
            }),
            bulletPara("安装JDK 17"),
            bulletPara("安装Node.js 18"),
            bulletPara("安装PostgreSQL 15"),
            bulletPara("安装Nginx（可选，推荐使用宝塔面板简化配置）"),
            new Paragraph({
                children: [new TextRun({ text: "步骤2：配置数据库", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { line: 360, lineRule: "atLeast" },
            }),
            bulletPara("创建数据库 network_monitor"),
            bulletPara("配置数据库连接信息（地址、端口、用户名、密码）"),
            new Paragraph({
                children: [new TextRun({ text: "步骤3：启动服务", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { line: 360, lineRule: "atLeast" },
            }),
            bulletPara("构建前端：npm install && npm run build"),
            bulletPara("启动后端：执行 java -jar network-monitor-backend.jar"),
            bulletPara("配置Nginx反向代理前后端服务"),
            new Paragraph({
                children: [new TextRun({ text: "步骤4：验证部署", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { line: 360, lineRule: "atLeast" },
            }),
            bulletPara("访问前端地址，确认页面正常加载"),
            bulletPara("检查各功能模块是否可正常使用"),

            // 5.3 主要流程
            heading2("5.3  主要流程"),

            heading3("5.3.1  系统首页（全景大屏）"),
            bodyPara("登录系统后，默认进入全景大屏页面。该页面实时展示校园网整体安全态势："),
            createTable(
                ["区域", "说明"],
                [
                    ["顶部统计卡片", "展示今日流量总数、活跃IP数、告警数量、异常检测数"],
                    ["中部地图", "3D地球可视化，展示全球攻击源分布和攻击弧线"],
                    ["底部图表", "流量趋势图、协议分布饼图、告警趋势折线图"],
                ],
                [2400, 6200]
            ),
            new Paragraph({
                children: [new TextRun({ text: "操作提示：", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("页面数据每5秒自动刷新"),
            bulletPara("点击地图上的攻击弧线，可查看攻击详情"),
            bulletPara("点击统计卡片，可跳转至对应详情页面"),

            heading3("5.3.2  安全中心"),
            bodyPara("查看和管理安全告警："),
            new Paragraph({
                children: [new TextRun({ text: "查看告警列表", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara('点击左侧菜单"安全中心"'),
            bulletPara("系统展示所有告警记录，支持分页浏览"),
            bulletPara("可按告警类型、严重级别进行筛选"),
            new Paragraph({
                children: [new TextRun({ text: "告警类型说明", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            createTable(
                ["类型", "说明"],
                [
                    ["流量异常", "检测到异常流量模式（突发、低熵等）"],
                    ["行为异常", "主机行为偏离正常基线"],
                    ["攻击链", "检测到多阶段攻击关联序列"],
                    ["趋势预警", "基于历史数据预测的潜在风险"],
                ],
                [2000, 6600]
            ),
            new Paragraph({
                children: [new TextRun({ text: "告警级别说明", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            createTable(
                ["级别", "颜色标识", "说明"],
                [
                    ["高危", "红色", "确认的攻击行为，需立即处理"],
                    ["中危", "橙色", "可疑行为，建议关注"],
                    ["低危", "黄色", "轻微异常，可记录观察"],
                ],
                [1800, 2000, 4800]
            ),
            new Paragraph({
                children: [new TextRun({ text: "确认告警", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara('点击告警列表中的"确认"按钮'),
            bulletPara("告警状态更新为已确认"),

            heading3("5.3.3  流检索"),
            bodyPara("检索和分析历史网络流量："),
            bulletPara('点击左侧菜单"流检索"'),
            bulletPara("输入查询条件："),
            bulletPara("  源IP/目的IP：输入IP地址精确或模糊匹配"),
            bulletPara("  端口：输入端口号"),
            bulletPara("  协议：选择TCP/UDP/ICMP/HTTP/HTTPS等"),
            bulletPara("  时间范围：选择查询的时间段"),
            bulletPara('点击"查询"按钮查看结果'),
            bulletPara("支持导出CSV格式"),
            new Paragraph({
                children: [new TextRun({ text: "组合查询示例：", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("查看某IP的所有流量：源IP = 192.168.1.100"),
            bulletPara("查看HTTP流量：协议 = HTTP"),
            bulletPara("查看某时段高危端口流量：目的端口 = 22 OR 3389 AND 时间范围 = 最近1小时"),

            heading3("5.3.4  IP画像"),
            bodyPara("对特定IP地址进行深度分析："),
            bulletPara("在任意页面点击IP地址（如告警列表中的源IP）"),
            bulletPara("系统跳转至IP画像页面，展示该IP的完整分析报告"),
            new Paragraph({
                children: [new TextRun({ text: "画像内容包括：", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            createTable(
                ["模块", "说明"],
                [
                    ["基本信息", "IP地址、地理位置、活跃时间"],
                    ["行为分析", "7维行为指纹向量图"],
                    ["流量统计", "流量大小、包数量、会话数统计"],
                    ["历史活动", "该IP的历史告警和流量记录"],
                ],
                [2400, 6200]
            ),

            heading3("5.3.5  高级分析"),
            bodyPara("使用创新算法进行深度安全分析："),
            bulletPara('点击左侧菜单"高级分析"'),
            bulletPara("选择分析算法和参数"),
            bulletPara('点击"分析"查看结果'),
            new Paragraph({
                children: [new TextRun({ text: "可用算法：", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            createTable(
                ["算法", "功能"],
                [
                    ["EWMA-EAD", "自适应异常检测，综合评分识别异常行为"],
                    ["NBF-RS", "行为指纹分析，7维向量刻画主机特征"],
                    ["MDTCE", "攻击链关联，还原完整攻击路径"],
                    ["TPM-PA", "趋势预测预警，提前感知潜在风险"],
                ],
                [2400, 6200]
            ),
            new Paragraph({
                children: [new TextRun({ text: "典型使用场景：", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("日常巡检：使用EWMA-EAD快速扫描异常"),
            bulletPara("事件调查：使用NBF-RS分析可疑IP行为特征"),
            bulletPara("溯源分析：使用MDTCE还原攻击链"),
            bulletPara("主动防御：使用TPM-PA设置预警规则"),

            // 5.4 常见问题
            heading2("5.4  常见问题"),
            createTable(
                ["问题", "解决方案"],
                [
                    ["页面无法加载", "检查网络连接，确认能访问公网"],
                    ["数据不更新", "点击页面刷新按钮，或等待自动刷新"],
                    ["查询无结果", "调整查询条件，扩大时间范围"],
                    ['IP画像显示"无数据"', "该IP可能无历史活动记录"],
                    ["3D地球加载缓慢", "首次加载需下载地图资源，后续会缓存加速"],
                ],
                [3000, 5600]
            ),

            // 5.5 联系方式
            heading2("5.5  联系方式"),
            bodyPara("如在使用过程中遇到问题，请联系系统管理员。"),
        ]
    }]
});

// 标题3辅助函数
function heading3(text) {
    return new Paragraph({
        children: [
            new TextRun({
                text: text,
                font: FONT_HEITI,
                size: FONT_SIZE_BODY,
                bold: true,
            })
        ],
        spacing: { before: 240, after: 120 },
        alignment: AlignmentType.LEFT,
    });
}

// 保存文档
Packer.toBuffer(doc).then(buffer => {
    fs.writeFileSync("D:/ASUS/Documents/jiedan/waibao/waibu/第五章_安装及使用.docx", buffer);
    console.log("文档已生成：第五章_安装及使用.docx");
});
