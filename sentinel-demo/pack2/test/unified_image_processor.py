import cv2 as cv
import numpy as np
import os
from PIL import Image
from datetime import datetime


def preprocess_image(image):
    """统一的图像预处理函数
    
    Args:
        image: 输入图像，可以是文件路径或图像数组
        
    Returns:
        处理后的灰度图像
    """
    # 如果是字符串，假定为文件路径
    if isinstance(image, str):
        img = cv.imread(image, cv.IMREAD_GRAYSCALE)
    # 如果是mss截图对象
    elif hasattr(image, 'pixels'):
        arr = np.array(image.pixels, dtype=np.uint8)
        img = cv.cvtColor(arr, cv.COLOR_BGRA2GRAY)
    # 如果是numpy数组
    elif isinstance(image, np.ndarray):
        # 检查是否为彩色图像
        if len(image.shape) == 3:
            img = cv.cvtColor(image, cv.COLOR_BGR2GRAY)
        else:
            img = image
    else:
        raise ValueError("不支持的图像类型")
    
    return img


def process_image_regions(template, target, template_info=None):
    """统一的图像区域处理函数
    
    Args:
        template: 模板图像
        target: 目标图像
        template_info: 模板图像信息，包含区域黑色标记
        
    Returns:
        处理后的模板图像和目标图像
    """
    # 如果没有提供模板信息，则不进行区域处理
    if template_info is None:
        return template, target
    
    # 获取区域黑色标记
    left_side_black = template_info.get('left_side_black', False)
    middle_black = template_info.get('middle_black', False)
    
    # 根据黑色区域标记调整比对区域
    width = template.shape[1]
    if left_side_black and middle_black:
        # 如果左侧1/5和中间2/5都是黑色，只比对右侧2/5区域
        start = (width * 3) // 5
        template_cropped = template[:, start:]
        target_cropped = target[:, start:]
    elif left_side_black:
        # 如果只有左侧1/5是黑色，比对右侧4/5区域
        start = width // 5
        template_cropped = template[:, start:]
        target_cropped = target[:, start:]
    else:
        # 不需要裁剪
        template_cropped = template
        target_cropped = target
    
    return template_cropped, target_cropped


def calculate_image_similarity(template, target, method="template", template_info=None):
    """统一的图像相似度计算函数
    
    Args:
        template: 模板图像
        target: 目标图像
        method: 匹配方法，"template"使用模板匹配，"orb"使用特征点匹配
        template_info: 模板图像信息，包含区域黑色标记
        
    Returns:
        相似度分数
    """
    # 预处理图像
    template_gray = preprocess_image(template)
    target_gray = preprocess_image(target)
    
    # 调整大小
    if template_gray.shape != target_gray.shape:
        template_gray = cv.resize(template_gray, (target_gray.shape[1], target_gray.shape[0]))
    
    # 区域处理
    if template_info is not None:
        template_gray, target_gray = process_image_regions(template_gray, target_gray, template_info)
    
    # 根据方法选择不同的匹配算法
    if method == "template":
        try:
            result = cv.matchTemplate(target_gray, template_gray, cv.TM_CCOEFF_NORMED)
            _, max_val, _, _ = cv.minMaxLoc(result)
            return max_val * 100  # 转换为百分比
        except Exception as e:
            print(f"模板匹配出错: {e}")
            return 0
    elif method == "orb":
        try:
            # 创建ORB特征检测器
            orb = cv.ORB_create()
            
            # 检测关键点和计算描述符
            kp1, des1 = orb.detectAndCompute(template_gray, None)
            kp2, des2 = orb.detectAndCompute(target_gray, None)
            
            if des1 is None or des2 is None:
                print("未检测到特征点")
                return 0
            
            # 创建BF匹配器
            bf = cv.BFMatcher(cv.NORM_HAMMING, crossCheck=True)
            matches = bf.match(des1, des2)
            
            # 按距离排序
            matches = sorted(matches, key=lambda x: x.distance)
            
            # 计算好的匹配点数量
            good_matches = 0
            for m in matches:
                if m.distance <= 60:  # 阈值
                    good_matches += 1
            
            return good_matches
        except Exception as e:
            print(f"特征点匹配出错: {e}")
            return 0
    else:
        raise ValueError("不支持的匹配方法")


def compare_images(template_path, target_path_or_obj, template_info=None, debug=False):
    """比较两个图像的相似度
    
    Args:
        template_path: 模板图像路径
        target_path_or_obj: 目标图像路径或对象
        template_info: 模板图像信息，包含区域黑色标记
        debug: 是否输出调试信息
        
    Returns:
        dict: 包含不同方法的相似度结果
    """
    results = {}
    
    # 模板匹配
    template_similarity = calculate_image_similarity(
        template_path, 
        target_path_or_obj, 
        method="template",
        template_info=template_info
    )
    results["template"] = template_similarity
    
    # 特征点匹配
    orb_similarity = calculate_image_similarity(
        template_path, 
        target_path_or_obj, 
        method="orb",
        template_info=template_info
    )
    results["orb"] = orb_similarity
    
    if debug:
        print(f"模板匹配相似度: {template_similarity:.2f}%")
        print(f"特征点匹配相似度: {orb_similarity}")
    
    return results


# 测试函数
def test_image_matching(template_path, target_path):
    """测试图像匹配
    
    Args:
        template_path: 模板图像路径
        target_path: 目标图像路径
    """
    print(f"比较图像: {os.path.basename(template_path)} 和 {os.path.basename(target_path)}")
    
    # 直接从文件系统加载
    print("\n1. 直接从文件系统加载:")
    results1 = compare_images(template_path, target_path, debug=True)
    
    # 模拟截图流程
    print("\n2. 模拟截图流程:")
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
    
    print("\n比较结果:")
    print(f"文件系统加载 - 模板匹配: {results1['template']:.2f}%, 特征点匹配: {results1['orb']}")
    print(f"模拟截图流程 - 模板匹配: {results2['template']:.2f}%, 特征点匹配: {results2['orb']}")


if __name__ == "__main__":
    # 示例用法
    import sys
    
    if len(sys.argv) == 3:
        template_path = sys.argv[1]
        target_path = sys.argv[2]
        test_image_matching(template_path, target_path)
    else:
        print("用法: python unified_image_processor.py <模板图像路径> <目标图像路径>")