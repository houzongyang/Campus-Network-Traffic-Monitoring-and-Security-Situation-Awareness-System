#!/usr/bin/env python3
"""
为PPT添加动态效果（动画）
直接操作PPTX XML结构添加动画
"""

import os
import sys
import shutil
import re
from lxml import etree
from defusedxml import minidom

# 命名空间
NSMAP = {
    'a': 'http://schemas.openxmlformats.org/drawingml/2006/main',
    'r': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships',
    'p': 'http://schemas.openxmlformats.org/presentationml/2006/main',
    'p14': 'http://schemas.microsoft.com/office/powerpoint/2010/main',
}

# 注册命名空间
for prefix, uri in NSMAP.items():
    etree.register_namespace(prefix, uri)


def qn(tag):
    """转换为带命名空间前缀的标签"""
    prefix, local = tag.split(':')
    return '{%s}%s' % (NSMAP[prefix], local)


def create_timing_node(animations):
    """
    创建动画时间线节点

    animations: list of dict with keys:
        - shape_id: shape ID
        - type: animation type (fade, fly, zoom, wipe)
        - delay: delay in seconds
        - duration: duration in seconds
    """
    timing = etree.Element(qn('p:timing'))

    # 时间线列表
    tnLst = etree.SubElement(timing, qn('p:tnLst'))

    # 根节点
    par_root = etree.SubElement(tnLst, qn('p:par'))
    cTn_root = etree.SubElement(par_root, qn('p:cTn'))
    cTn_root.set('id', '1')
    cTn_root.set('dur', 'indefinite')
    cTn_root.set('restart', 'whenNotActive')
    cTn_root.set('nodeType', 'tmRoot')

    # 子节点列表
    childTnLst = etree.SubElement(cTn_root, qn('p:childTnLst'))

    anim_id = 2
    prev_tn_id = 1

    for anim in animations:
        shape_id = anim['shape_id']
        anim_type = anim.get('type', 'fade')
        delay = anim.get('delay', 0)
        dur = anim.get('duration', 0.5)

        # 创建动画par
        par = etree.SubElement(childTnLst, qn('p:par'))

        # cTn
        cTn = etree.SubElement(par, qn('p:cTn'))
        cTn.set('id', str(anim_id))
        cTn.set('dur', str(int(dur * 1000)))
        cTn.set('fill', 'hold')
        cTn.set('nodeType', 'clickEffect')

        # 开始条件
        stCondLst = etree.SubElement(cTn, qn('p:stCondLst'))
        cond = etree.SubElement(stCondLst, qn('p:cond'))
        if delay > 0:
            cond.set('delay', str(int(delay * 1000)))
        else:
            cond.set('delay', 'indefinite')

        # 引用前一个动画
        tn = etree.SubElement(cond, qn('p:tn'))
        tn.set('val', str(prev_tn_id))

        # 动画效果
        anim_elem = etree.SubElement(cTn, qn('p:anim'))

        if anim_type == 'fade':
            anim_elem.set('presetClass', 'entr')
            anim_elem.set('presetSubtype', '0')
        elif anim_type == 'zoom':
            anim_elem.set('presetClass', 'entr')
            anim_elem.set('presetSubtype', '4')
        elif anim_type == 'fly':
            anim_elem.set('presetClass', 'entr')
            anim_elem.set('presetSubtype', '1')  # 从左飞入
        elif anim_type == 'wipe':
            anim_elem.set('presetClass', 'entr')
            anim_elem.set('presetSubtype', '2')
        elif anim_type == 'spin':
            anim_elem.set('presetClass', 'entr')
            anim_elem.set('presetSubtype', '8')

        anim_elem.set('fill', 'hold')

        # 动画值
        cBhvr = etree.SubElement(anim_elem, qn('p:cBhvr'))
        cBhvr.set('addToSldr', '1')

        # 开始/结束属性
        stSel = etree.SubElement(cBhvr, qn('p:stSel'))
        stSel.set('val', 'sBegin')

        endSel = etree.SubElement(cBhvr, qn('p:endSel'))
        endSel.set('val', 'sEnd')

        # 目标
        tgtEl = etree.SubElement(cBhvr, qn('p:tgtEl'))
        spTgt = etree.SubElement(tgtEl, qn('p:spTgt'))
        spTgt.set('spid', str(shape_id))

        # 形状属性
        attList = etree.SubElement(anim_elem, qn('p:attList'))

        # 属性：透明度的变化（用于fade）
        if anim_type == 'fade':
            attr = etree.SubElement(attList, qn('p:attr'))
            attr.set('name', 'style.opacity')
            attr.set('fmla', 'val 0')
            attr.set('to', '100%')

        anim_id += 1
        prev_tn_id = anim_id - 1

    return timing


def add_animations_to_slide(slide_path, animations):
    """为幻灯片添加动画"""
    # 读取XML
    with open(slide_path, 'rb') as f:
        tree = etree.parse(f)

    root = tree.getroot()

    # 移除旧的timing节点（如果存在）
    old_timing = root.find(qn('p:timing'))
    if old_timing is not None:
        root.remove(old_timing)

    # 创建新的timing节点
    timing = create_timing_node(animations)

    # 添加到slide
    root.append(timing)

    # 保存
    with open(slide_path, 'wb') as f:
        tree.write(f, xml_declaration=True, encoding='utf-8', pretty_print=True)


def main():
    unpacked_dir = r"D:\ASUS\Documents\jiedan\waibao\unpacked_pptx"

    # 定义每张幻灯片的动画
    slide_animations = {
        # Slide 1: 标题页 - 依次淡入
        'slide1.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 500},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 500},
            {'shape_id': 4, 'type': 'fade', 'delay': 300, 'duration': 500},
            {'shape_id': 5, 'type': 'fly', 'delay': 400, 'duration': 500},
            {'shape_id': 6, 'type': 'fade', 'delay': 600, 'duration': 500},
            {'shape_id': 7, 'type': 'fade', 'delay': 800, 'duration': 500},
            {'shape_id': 8, 'type': 'fade', 'delay': 1000, 'duration': 500},
            {'shape_id': 9, 'type': 'fade', 'delay': 1200, 'duration': 500},
            {'shape_id': 10, 'type': 'wipe', 'delay': 1500, 'duration': 500},
            {'shape_id': 11, 'type': 'fade', 'delay': 1600, 'duration': 500},
            {'shape_id': 12, 'type': 'fade', 'delay': 1700, 'duration': 500},
            {'shape_id': 13, 'type': 'fade', 'delay': 1800, 'duration': 500},
            {'shape_id': 14, 'type': 'fade', 'delay': 1900, 'duration': 500},
        ],

        # Slide 2: 目录 - 依次出现
        'slide2.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 500},
            {'shape_id': 3, 'type': 'zoom', 'delay': 300, 'duration': 500},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 500},
            {'shape_id': 5, 'type': 'zoom', 'delay': 700, 'duration': 500},
            {'shape_id': 6, 'type': 'zoom', 'delay': 900, 'duration': 500},
            {'shape_id': 7, 'type': 'zoom', 'delay': 1100, 'duration': 500},
            {'shape_id': 8, 'type': 'zoom', 'delay': 1300, 'duration': 500},
            {'shape_id': 9, 'type': 'zoom', 'delay': 1500, 'duration': 500},
        ],

        # Slide 3: 背景与问题分析 - 卡片依次出现
        'slide3.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'fade', 'delay': 400, 'duration': 500},
            {'shape_id': 5, 'type': 'fly', 'delay': 600, 'duration': 500},
            {'shape_id': 6, 'type': 'fly', 'delay': 700, 'duration': 300},
            {'shape_id': 7, 'type': 'fly', 'delay': 800, 'duration': 300},
            {'shape_id': 8, 'type': 'fly', 'delay': 900, 'duration': 300},
            {'shape_id': 9, 'type': 'fly', 'delay': 1000, 'duration': 300},
            {'shape_id': 10, 'type': 'fly', 'delay': 1100, 'duration': 300},
            {'shape_id': 11, 'type': 'fade', 'delay': 1300, 'duration': 300},
        ],

        # Slide 4: 解决方案概览 - 流程依次出现
        'slide4.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 400, 'duration': 500},
            {'shape_id': 5, 'type': 'zoom', 'delay': 600, 'duration': 500},
            {'shape_id': 6, 'type': 'zoom', 'delay': 800, 'duration': 500},
            {'shape_id': 7, 'type': 'zoom', 'delay': 1000, 'duration': 500},
            {'shape_id': 8, 'type': 'zoom', 'delay': 1200, 'duration': 500},
            {'shape_id': 9, 'type': 'zoom', 'delay': 1400, 'duration': 500},
            {'shape_id': 10, 'type': 'zoom', 'delay': 1600, 'duration': 500},
        ],

        # Slide 5: 系统架构设计 - 层次依次出现
        'slide5.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
            {'shape_id': 6, 'type': 'zoom', 'delay': 1100, 'duration': 600},
        ],

        # Slide 6: 全景大屏 - 数据依次出现
        'slide6.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'zoom', 'delay': 300, 'duration': 500},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 500},
            {'shape_id': 5, 'type': 'zoom', 'delay': 700, 'duration': 500},
            {'shape_id': 6, 'type': 'zoom', 'delay': 900, 'duration': 500},
            {'shape_id': 7, 'type': 'zoom', 'delay': 1100, 'duration': 500},
        ],

        # Slide 7: 安全威胁感知 - 依次出现
        'slide7.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
        ],

        # Slide 8: 流级检索 - 依次出现
        'slide8.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 500},
            {'shape_id': 5, 'type': 'zoom', 'delay': 700, 'duration': 500},
            {'shape_id': 6, 'type': 'zoom', 'delay': 900, 'duration': 500},
            {'shape_id': 7, 'type': 'zoom', 'delay': 1100, 'duration': 500},
        ],

        # Slide 9: 六层下钻 - 依次出现
        'slide9.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'fly', 'delay': 500, 'duration': 500},
            {'shape_id': 5, 'type': 'fly', 'delay': 700, 'duration': 500},
            {'shape_id': 6, 'type': 'fly', 'delay': 900, 'duration': 500},
            {'shape_id': 7, 'type': 'fly', 'delay': 1100, 'duration': 500},
            {'shape_id': 8, 'type': 'fly', 'delay': 1300, 'duration': 500},
            {'shape_id': 9, 'type': 'fly', 'delay': 1500, 'duration': 500},
        ],

        # Slide 10: 核心算法设计
        'slide10.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
            {'shape_id': 6, 'type': 'zoom', 'delay': 1100, 'duration': 600},
            {'shape_id': 7, 'type': 'zoom', 'delay': 1400, 'duration': 600},
        ],

        # Slide 11: 算法详解
        'slide11.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
        ],

        # Slide 12: 技术路线
        'slide12.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 500},
            {'shape_id': 5, 'type': 'zoom', 'delay': 700, 'duration': 500},
            {'shape_id': 6, 'type': 'zoom', 'delay': 900, 'duration': 500},
        ],

        # Slide 13: 业务模式与可行性
        'slide13.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
            {'shape_id': 6, 'type': 'zoom', 'delay': 1100, 'duration': 600},
        ],

        # Slide 14: 团队介绍
        'slide14.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
        ],

        # Slide 15: 部署方案
        'slide15.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
        ],

        # Slide 16: 创新亮点总结
        'slide16.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 500},
            {'shape_id': 5, 'type': 'zoom', 'delay': 700, 'duration': 500},
            {'shape_id': 6, 'type': 'zoom', 'delay': 900, 'duration': 500},
            {'shape_id': 7, 'type': 'zoom', 'delay': 1100, 'duration': 500},
            {'shape_id': 8, 'type': 'zoom', 'delay': 1300, 'duration': 500},
            {'shape_id': 9, 'type': 'zoom', 'delay': 1500, 'duration': 500},
        ],

        # Slide 17: 未来展望
        'slide17.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 300},
            {'shape_id': 3, 'type': 'fade', 'delay': 200, 'duration': 300},
            {'shape_id': 4, 'type': 'zoom', 'delay': 500, 'duration': 600},
            {'shape_id': 5, 'type': 'zoom', 'delay': 800, 'duration': 600},
            {'shape_id': 6, 'type': 'zoom', 'delay': 1100, 'duration': 600},
        ],

        # Slide 18: 感谢页
        'slide18.xml': [
            {'shape_id': 2, 'type': 'fade', 'delay': 0, 'duration': 500},
            {'shape_id': 3, 'type': 'zoom', 'delay': 300, 'duration': 800},
            {'shape_id': 4, 'type': 'fade', 'delay': 800, 'duration': 500},
            {'shape_id': 5, 'type': 'fade', 'delay': 1000, 'duration': 500},
            {'shape_id': 6, 'type': 'fade', 'delay': 1200, 'duration': 500},
        ],
    }

    # 为每张幻灯片添加动画
    slides_dir = os.path.join(unpacked_dir, 'ppt', 'slides')

    for slide_file, animations in slide_animations.items():
        slide_path = os.path.join(slides_dir, slide_file)
        if os.path.exists(slide_path):
            print(f"为 {slide_file} 添加动画...")
            try:
                add_animations_to_slide(slide_path, animations)
                print(f"  完成")
            except Exception as e:
                print(f"  错误: {e}")
        else:
            print(f"  文件不存在: {slide_path}")

    print("\n动画添加完成！")
    print("下一步：重新打包PPT文件")


if __name__ == "__main__":
    main()
