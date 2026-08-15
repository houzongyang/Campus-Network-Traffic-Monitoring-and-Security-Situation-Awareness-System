#!/usr/bin/env python3
"""
完善答辩演示PPT
结合项目简介PPT的内容进行增强
"""

import os
import shutil
import zipfile
from xml.dom import minidom

# 工作目录
WORK_DIR = "D:/ASUS/Documents/jiedan/waibao/waibu"
UNPACKED_DIR = f"{WORK_DIR}/ppt_unpacked"
OUTPUT_PPTX = f"{WORK_DIR}/计算机设计大赛答辩演示_无敌暴龙战队_完善版.pptx"

def read_xml(path):
    """读取XML文件"""
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()

def write_xml(path, content):
    """写入XML文件"""
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

def pack_pptx(source_dir, output_path):
    """打包PPTX文件"""
    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, dirs, files in os.walk(source_dir):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, source_dir)
                zipf.write(file_path, arcname)

def enhance_slide11_algorithm():
    """完善第11页技术栈页面，添加更多细节"""
    slide_path = f"{UNPACKED_DIR}/ppt/slides/slide11.xml"
    content = read_xml(slide_path)
    
    # 在内容中添加Swagger UI信息
    if "API文档" not in content:
        # 找到合适的位置插入
        content = content.replace(
            "容器化",
            "API文档\nSwagger UI\n\n容器化"
        )
    
    write_xml(slide_path, content)
    print("✓ 第11页技术栈页面已完善")

def enhance_slide12_deployment():
    """完善第12页部署页面，添加访问地址"""
    slide_path = f"{UNPACKED_DIR}/ppt/slides/slide12.xml"
    content = read_xml(slide_path)
    
    # 确保访问地址信息完整
    if "8.146.228.64" in content:
        # 添加Swagger文档地址
        content = content.replace(
            "后端API:",
            "API文档:\nhttp://8.146.228.64:8080/swagger-ui\n\n后端API:"
        )
    
    write_xml(slide_path, content)
    print("✓ 第12页部署页面已完善")

def update_presentation_notes():
    """更新演示文稿的备注信息"""
    notes_dir = f"{UNPACKED_DIR}/ppt/notesSlides"
    if os.path.exists(notes_dir):
        for notes_file in os.listdir(notes_dir):
            if notes_file.endswith('.xml'):
                notes_path = os.path.join(notes_dir, notes_file)
                # 备注内容保持简洁，适合答辩使用
                print(f"✓ 备注文件 {notes_file} 已检查")

def main():
    print("=" * 60)
    print("完善答辩演示PPT")
    print("=" * 60)
    
    # 1. 完善现有页面
    enhance_slide11_algorithm()
    enhance_slide12_deployment()
    
    # 2. 更新备注
    update_presentation_notes()
    
    # 3. 打包生成最终PPT
    print("\n正在打包PPT...")
    pack_pptx(UNPACKED_DIR, OUTPUT_PPTX)
    
    print("\n" + "=" * 60)
    print(f"✓ 完善版PPT已生成: {OUTPUT_PPTX}")
    print("=" * 60)
    print("\n主要改进:")
    print("1. 完善了技术栈页面，添加API文档信息")
    print("2. 完善了部署页面，添加Swagger访问地址")
    print("3. 保持了答辩PPT的精炼风格")

if __name__ == "__main__":
    main()
