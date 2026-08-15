const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        AlignmentType, BorderStyle, WidthType, ShadingType, VerticalAlign } = require('docx');
const fs = require('fs');

const PAGE_WIDTH = 11906;
const PAGE_HEIGHT = 16838;
const MARGIN = 567;
const BINDING = 567;
const CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2 - BINDING;

const FONT_KAITI = '楷体';
const FONT_HEITI = '黑体';
const FONT_SIZE_BODY = 21;
const FONT_SIZE_H1 = 32;
const FONT_SIZE_H2 = 28;
const FONT_SIZE_H3 = 24;

const border = { style: BorderStyle.SINGLE, size: 1, color: "000000" };
const borders = { top: border, bottom: border, left: border, right: border };

function heading2(text) {
    return new Paragraph({
        children: [new TextRun({ text: text, font: FONT_HEITI, size: FONT_SIZE_H2, bold: true })],
        spacing: { before: 300, after: 180 },
    });
}

function heading3(text) {
    return new Paragraph({
        children: [new TextRun({ text: text, font: FONT_HEITI, size: FONT_SIZE_H3, bold: true })],
        spacing: { before: 240, after: 120 },
    });
}

function bodyPara(text) {
    return new Paragraph({
        children: [new TextRun({ text: text, font: FONT_KAITI, size: FONT_SIZE_BODY })],
        spacing: { line: 360, lineRule: "atLeast" },
        alignment: AlignmentType.JUSTIFIED,
        indent: { firstLine: 420 },
    });
}

function bulletPara(text) {
    return new Paragraph({
        children: [new TextRun({ text: text, font: FONT_KAITI, size: FONT_SIZE_BODY })],
        spacing: { line: 360, lineRule: "atLeast" },
        alignment: AlignmentType.JUSTIFIED,
    });
}

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
                alignment: AlignmentType.LEFT,
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
            // 标题
            new Paragraph({
                children: [
                    new TextRun({ text: "第六章", font: FONT_HEITI, size: FONT_SIZE_H1, bold: true }),
                    new TextRun({ text: " 项目总结", font: FONT_HEITI, size: FONT_SIZE_H1, bold: true }),
                ],
                spacing: { before: 300, after: 400 },
                alignment: AlignmentType.CENTER,
            }),

            // 6.1 项目协调与任务分解
            heading2("6.1  项目协调与任务分解"),
            bodyPara("本项目由苏州大学无敌暴龙战队（T2601487）成员协作完成，团队成员涵盖计算机科学与技术、软件工程等专业背景。在项目启动初期，团队通过需求分析会议明确了系统定位和技术路线，将整体工作划分为前端开发、后端开发、算法设计、测试验证四个核心模块。"),
            bodyPara('任务分解采用"主-辅"协作模式：前端开发人员主导页面交互设计和可视化实现，后端开发人员负责数据接口和业务逻辑，算法设计人员专注于四个创新算法的理论推导与代码实现，测试人员负责功能验证和性能调优。各模块通过GitHub代码仓库进行版本管理和代码审查，确保代码质量和进度可控。'),

            // 6.2 技术挑战与克服
            heading2("6.2  技术挑战与克服"),
            heading3("6.2.1  异构跨云架构部署"),
            bodyPara("系统需要同时运行在Windows Server和Linux两个不同操作系统的服务器上，并确保前后端服务在公网环境下的稳定通信。团队通过宝塔面板统一管理Nginx反向代理，配置跨平台兼容的API接口规范，解决了因系统差异导致的网络通信问题。"),

            heading3("6.2.2  实时流量可视化性能"),
            bodyPara("3D地球可视化需要同时渲染数千条攻击弧线，对前端渲染性能提出严峻挑战。团队采用WebGL绘制方案替代传统Canvas，结合ECharts的增量渲染机制，优化了地理坐标映射算法，最终实现了60fps的流畅渲染效果。"),

            heading3("6.2.3  算法工程化落地"),
            bodyPara("四个创新算法（EWMA-EAD、NBF-RS、MDTCE、TPM-PA）的理论模型需要转化为可运行的代码模块。团队通过Python脚本验证算法公式正确性后，使用Java重写了核心逻辑，并针对Spring Boot框架进行了性能优化，确保算法在生产环境下的运行效率。"),

            // 6.3 水平提升
            heading2("6.3  水平提升"),
            bodyPara("通过本项目的开发，团队成员在以下方面获得了显著提升："),
            createTable(
                ["能力维度", "提升内容"],
                [
                    ["系统架构设计", "掌握了异构系统集成和微服务化部署的实践经验"],
                    ["前端工程化", "熟练运用React组件化开发和ECharts可视化技术"],
                    ["后端性能优化", "深入理解了数据库索引优化和缓存策略的应用"],
                    ["算法研发能力", "学会了从学术论文到工程实现的完整转化流程"],
                    ["团队协作", "提升了需求分析、任务分解和进度管控能力"],
                ],
                [3000, 5600]
            ),

            // 6.4 升级演进
            heading2("6.4  升级演进"),
            bodyPara("当前系统已具备基础的流量监控和安全态势感知能力，未来可从以下方向进行升级："),
            new Paragraph({
                children: [new TextRun({ text: "短期升级（1-3个月）", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("集成实时网络抓包模块，实现流量在线采集"),
            bulletPara("引入机器学习模型，提升异常检测准确率"),
            bulletPara("开发移动端适配页面，支持移动办公场景"),
            new Paragraph({
                children: [new TextRun({ text: "中期升级（3-6个月）", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("构建分布式集群架构，支持更大规模的流量处理"),
            bulletPara("集成自动化响应模块，实现威胁自动处置"),
            bulletPara("开发API开放平台，支持第三方安全设备对接"),
            new Paragraph({
                children: [new TextRun({ text: "长期演进（6-12个月）", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("结合威胁情报服务，构建主动防御体系"),
            bulletPara("探索零信任网络安全架构"),
            bulletPara("开发SaaS化服务模式，支持多租户管理"),

            // 6.5 商业推广
            heading2("6.5  商业推广"),
            bodyPara("本系统可面向以下场景进行商业推广："),
            createTable(
                ["应用场景", "目标客户", "价值主张"],
                [
                    ["高校校园网", "高校信息化部门", "全方位安全态势感知，满足等保合规要求"],
                    ["中小企业", "IT管理部门", "低成本高效益的安全监控解决方案"],
                    ["数据中心", "运维服务商", "实时流量分析，提升运维效率"],
                    ["政府机构", "政务网络管理中心", "国产化替代方案，保障网络安全"],
                ],
                [2400, 2400, 3800]
            ),
            new Paragraph({
                children: [new TextRun({ text: "推广策略", font: FONT_KAITI, size: FONT_SIZE_BODY, bold: true })],
                spacing: { before: 200, line: 360, lineRule: "atLeast" },
            }),
            bulletPara("系统采用模块化设计，可根据客户需求灵活组合功能模块"),
            bulletPara("支持定制化开发和私有化部署"),
            bulletPara("计划通过免费试用+增值服务的商业模式逐步打开市场"),
        ]
    }]
});

Packer.toBuffer(doc).then(buffer => {
    fs.writeFileSync("D:/ASUS/Documents/jiedan/waibao/waibu/第六章_项目总结_v2.docx", buffer);
    console.log("文档已生成：第六章_项目总结_v2.docx");
});
