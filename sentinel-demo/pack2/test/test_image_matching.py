import os
import sys
import cv2 as cv
import numpy as np
from PIL import Image
from mss import mss

# 导入统一图像处理模块
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from unified_image_processor import (
    preprocess_image,
    process_image_regions,
    calculate_image_similarity,
    compare_images
)


def test_with_real_screenshot(template_path, screenshot_region=None):
    """使用真实截图测试图像匹配
    
    Args:
        template_path: 模板图像路径
        screenshot_region: 截图区域 (left, top, right, bottom)
    """
    print(f"使用模板: {os.path.basename(template_path)}")
    
    # 如果没有指定截图区域，使用默认值
    if screenshot_region is None:
        # 默认截取屏幕中央区域
        import ctypes
        user32 = ctypes.windll.user32
        screen_width = user32.GetSystemMetrics(0)
        screen_height = user32.GetSystemMetrics(1)
        left = screen_width // 4
        top = screen_height // 4
        width = screen_width // 2
        height = screen_height // 2
        screenshot_region = (left, top, left + width, top + height)
    
    print(f"截图区域: {screenshot_region}")
    
    # 1. 直接从文件系统加载模板
    template_img = cv.imread(template_path, cv.IMREAD_GRAYSCALE)
    
    # 2. 截图
    with mss() as sct:
        screenshot = sct.grab(screenshot_region)
    
    # 3. 保存截图为临时文件
    temp_path = "temp_screenshot.png"
    img_pil = Image.frombytes("RGB", screenshot.size, screenshot.bgra, "raw", "BGRX")
    img_pil.save(temp_path, format='PNG')
    
    # 4. 重新加载截图
    screenshot_img = cv.imread(temp_path, cv.IMREAD_GRAYSCALE)
    
    # 5. 直接处理截图对象
    print("\n方法1: 直接处理截图对象")
    similarity1 = calculate_image_similarity(template_img, screenshot, method="template")
    print(f"模板匹配相似度: {similarity1:.2f}%")
    
    # 6. 处理保存后的截图文件
    print("\n方法2: 处理保存后的截图文件")
    similarity2 = calculate_image_similarity(template_img, temp_path, method="template")
    print(f"模板匹配相似度: {similarity2:.2f}%")
    
    # 7. 使用统一预处理
    print("\n方法3: 使用统一预处理")
    template_processed = preprocess_image(template_path)
    screenshot_processed1 = preprocess_image(screenshot)
    screenshot_processed2 = preprocess_image(temp_path)
    
    # 调整大小
    if template_processed.shape != screenshot_processed1.shape:
        template_resized = cv.resize(template_processed, 
                                    (screenshot_processed1.shape[1], screenshot_processed1.shape[0]))
    else:
        template_resized = template_processed
    
    # 使用模板匹配
    result1 = cv.matchTemplate(screenshot_processed1, template_resized, cv.TM_CCOEFF_NORMED)
    _, max_val1, _, _ = cv.minMaxLoc(result1)
    similarity3 = max_val1 * 100
    
    result2 = cv.matchTemplate(screenshot_processed2, template_resized, cv.TM_CCOEFF_NORMED)
    _, max_val2, _, _ = cv.minMaxLoc(result2)
    similarity4 = max_val2 * 100
    
    print(f"直接处理截图对象相似度: {similarity3:.2f}%")
    print(f"处理保存后的截图文件相似度: {similarity4:.2f}%")
    
    # 清理临时文件
    if os.path.exists(temp_path):
        os.remove(temp_path)
    
    return {
        "direct_screenshot": similarity1,
        "saved_screenshot": similarity2,
        "unified_direct": similarity3,
        "unified_saved": similarity4
    }


def test_with_sample_images(template_dir, target_dir):
    """使用样本图像测试图像匹配
    
    Args:
        template_dir: 模板图像目录
        target_dir: 目标图像目录
    """
    # 获取所有模板图像
    template_files = []
    for root, dirs, files in os.walk(template_dir):
        for file in files:
            if file.lower().endswith(('.png', '.jpg', '.jpeg')):
                template_files.append(os.path.join(root, file))
    
    # 获取所有目标图像
    target_files = []
    for root, dirs, files in os.walk(target_dir):
        for file in files:
            if file.lower().endswith(('.png', '.jpg', '.jpeg')):
                target_files.append(os.path.join(root, file))
    
    if not template_files or not target_files:
        print("未找到图像文件")
        return
    
    print(f"找到 {len(template_files)} 个模板图像和 {len(target_files)} 个目标图像")
    
    # 测试每个模板与每个目标的匹配度
    for template_path in template_files[:3]:  # 限制测试数量
        template_name = os.path.basename(template_path)
        print(f"\n测试模板: {template_name}")
        
        for target_path in target_files[:3]:  # 限制测试数量
            target_name = os.path.basename(target_path)
            print(f"\n  与目标 {target_name} 比较:")
            
            # 使用统一图像处理模块比较
            results = compare_images(template_path, target_path, debug=True)
            
            # 模拟截图流程
            print("\n  模拟截图流程:")
            # 读取图像
            target_img = cv.imread(target_path)
            # 转换为BGR格式
            target_bgr = cv.cvtColor(target_img, cv.COLOR_BGR2RGB)
            # 保存为临时文件
            temp_path = "temp_screenshot.png"
            cv.imwrite(temp_path, target_bgr)
            # 重新读取
            results2 = compare_images(template_path, temp_path, debug=True)
            
            # 清理临时文件
            if os.path.exists(temp_path):
                os.remove(temp_path)


def main():
    """主函数"""
    print("PUBG图像匹配测试工具")
    print("====================")
    
    # 解析命令行参数
    if len(sys.argv) >= 2:
        command = sys.argv[1]
        
        if command == "screenshot" and len(sys.argv) >= 3:
            # 测试真实截图
            template_path = sys.argv[2]
            if len(sys.argv) >= 7:
                # 指定截图区域
                left = int(sys.argv[3])
                top = int(sys.argv[4])
                width = int(sys.argv[5])
                height = int(sys.argv[6])
                screenshot_region = (left, top, left + width, top + height)
                test_with_real_screenshot(template_path, screenshot_region)
            else:
                test_with_real_screenshot(template_path)
        
        elif command == "sample" and len(sys.argv) >= 4:
            # 测试样本图像
            template_dir = sys.argv[2]
            target_dir = sys.argv[3]
            test_with_sample_images(template_dir, target_dir)
        
        else:
            print_usage()
    else:
        print_usage()


def print_usage():
    """打印使用说明"""
    print("用法:")
    print("  1. 测试真实截图:")
    print("     python test_image_matching.py screenshot <模板图像路径> [<左> <上> <宽> <高>]")
    print("  2. 测试样本图像:")
    print("     python test_image_matching.py sample <模板图像目录> <目标图像目录>")
    # python test_image_matching.py sample ../resource/25601440 ../resource/temp2313

if __name__ == "__main__":
    main()