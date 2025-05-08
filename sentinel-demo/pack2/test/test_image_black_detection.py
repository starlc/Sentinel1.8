import os
import sys
import cv2 as cv
import numpy as np
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from image_processor import ImageProcessor
import argparse

def test_black_detection(img_dir):
    processor = ImageProcessor()
    gun_list, gun_img_dict = processor.load_gun_images(img_dir)
    if not gun_list:
        print(f"未找到图片目录或目录为空: {img_dir}")
        return
    
    # 按照武器ID的数字值排序（自然排序）
    sorted_gun_list = sorted(gun_list, key=lambda x: int(x))
    
    print(f"{'武器ID':<10} {'左侧1/5全黑':<12} {'中间2/5全黑':<12}")
    print("-" * 35)
    
    for gun_id in sorted_gun_list:
        info = gun_img_dict[gun_id]
        
        # 处理不同的数据结构
        left_black = False
        middle_black = False
        
        if isinstance(info, dict):
            # 如果info是字典，尝试获取black信息
            left_black = info.get('left_side_black', False)
            middle_black = info.get('middle_black', False)
        
        print(f"{gun_id:<10} {str(left_black):<12} {str(middle_black):<12}")
    
    print(f"\n共加载 {len(gun_list)} 个模板图片")

def test_template_matching(template_dir, temp_dir, target_file=None):
    """测试临时目录中的截图与模板的匹配度
    
    Args:
        template_dir: 模板图片所在目录
        temp_dir: 临时截图所在目录
        target_file: 可选，指定只匹配特定文件名
    """
    processor = ImageProcessor()
    # 加载模板图片
    gun_list, gun_img_dict = processor.load_gun_images(template_dir)
    if not gun_list:
        print(f"未找到模板目录或目录为空: {template_dir}")
        return
    
def calculate_similarity(template, target):
    """计算两个图像的相似度(模板匹配方法)
    
    Args:
        template: 模板图像(可以是文件路径或图像数组)
        target: 目标图像(可以是文件路径或图像数组)
        
    Returns:
        float: 相似度得分(0-100)
    
    Raises:
        Exception: 当模板匹配过程中出现错误时抛出
    """
    # 确保两个图像是灰度图
    if len(template.shape) > 2:
        template = cv.cvtColor(template, cv.COLOR_BGR2GRAY)
    if len(target.shape) > 2:
        target = cv.cvtColor(target, cv.COLOR_BGR2GRAY)
        
    # 调整模板大小以匹配目标图像
    if template.shape != target.shape:
        template = cv.resize(template, (target.shape[1], target.shape[0]))
        
    # 使用OpenCV的模板匹配
    try:
        result = cv.matchTemplate(target, template, cv.TM_CCOEFF_NORMED)
        _, max_val, _, _ = cv.minMaxLoc(result)
        return max_val * 100  # 转换为百分比
    except Exception as e:
        print(f"模板匹配出错: {e}")
        return 0
    
    

def save_matching_results_to_file(template_dir, temp_dir, output_file="matching_results.txt"):
    """将模板匹配结果保存到文件
    
    Args:
        template_dir: 模板图片所在目录
        temp_dir: 临时截图所在目录
        output_file: 输出结果文件名，默认为'matching_results.txt'
    
    Returns:
        bool: 是否成功保存结果
    """
    processor = ImageProcessor()
    gun_list, gun_img_dict = processor.load_gun_images(template_dir)
    if not gun_list:
        print(f"未找到模板目录或目录为空: {template_dir}")
        return False
    
    # 获取临时目录中的所有图片
    temp_files = []
    for root, dirs, files in os.walk(temp_dir):
        for file in files:
            if file.lower().endswith(('.png', '.jpg', '.jpeg')):
                temp_files.append(os.path.join(root, file))
    
    if not temp_files:
        print(f"临时目录中没有找到图片: {temp_dir}")
        return False
    
    results = {}
    for temp_file in temp_files:
        img = cv.imread(temp_file)
        if img is None:
            continue
            
        gray_img = cv.cvtColor(img, cv.COLOR_BGR2GRAY) if len(img.shape) == 3 else img
        
        # 检测并处理黑色区域
       
        
        # 计算与每个模板的匹配度
        file_results = []
        for gun_id in gun_list:
            template_data = gun_img_dict[gun_id]
            if isinstance(template_data, dict) and 'image' in template_data:
                template_img = template_data['image']
            elif hasattr(template_data, 'shape'):
                template_img = template_data
            else:
                continue
                
            similarity = calculate_similarity(template_img, gray_img)
            file_results.append((gun_id, similarity))
        
        # 按匹配度排序并保存最佳结果
        file_results.sort(key=lambda x: x[1], reverse=True)
        results[os.path.basename(temp_file)] = file_results[0] if file_results else None
    
    # 将结果写入文件
    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write("测试图片,最佳匹配武器ID,匹配度\n")
            for filename, result in results.items():
                if result:
                    f.write(f"{filename},{result[0]},{result[1]:.2f}\n")
        print(f"匹配结果已保存到: {output_file}")
        return True
    except Exception as e:
        print(f"保存结果到文件时出错: {e}")
        return False


def test_comparison_matching(template_dir, temp_dir, target_file=None):
    """比较不同匹配方法的效果
    
    Args:
        template_dir: 模板图片所在目录
        temp_dir: 临时截图所在目录
        target_file: 可选，指定只匹配特定文件名
    """
    # 导入主程序类和其他所需的模块
    from main import PUBGAssistant
    
    # 初始化图像处理器和主程序实例
    processor = ImageProcessor()
    assistant = PUBGAssistant()
    
    # 加载模板图片
    gun_list, gun_img_dict = processor.load_gun_images(template_dir)
    if not gun_list:
        print(f"未找到模板目录或目录为空: {template_dir}")
        return
    
    # 辅助函数：计算两个图像的相似度 (模板匹配方法)
    def calculate_similarity(template, target):
        """计算两个图像的相似度
        
        Args:
            template: 模板图像
            target: 目标图像
            
        Returns:
            float: 相似度得分
        """
        # 确保两个图像是灰度图
        if len(template.shape) > 2:
            template = cv.cvtColor(template, cv.COLOR_BGR2GRAY)
        if len(target.shape) > 2:
            target = cv.cvtColor(target, cv.COLOR_BGR2GRAY)
            
        # 调整模板大小以匹配目标图像
        if template.shape != target.shape:
            template = cv.resize(template, (target.shape[1], target.shape[0]))
            
        # 使用OpenCV的模板匹配
        try:
            result = cv.matchTemplate(target, template, cv.TM_CCOEFF_NORMED)
            _, max_val, _, _ = cv.minMaxLoc(result)
            return max_val * 100  # 转换为百分比
        except Exception as e:
            print(f"模板匹配出错: {e}")
            return 0
    
    # 新增辅助函数：使用ORB特征点匹配
    def calculate_orb_similarity(template, target):
        """使用ORB特征点匹配计算相似度（模拟主程序的方法）
        
        Args:
            template: 模板图像
            target: 目标图像
            
        Returns:
            int: 特征点匹配数量
        """
        # 确保两个图像是灰度图
        if len(template.shape) > 2:
            template = cv.cvtColor(template, cv.COLOR_BGR2GRAY)
        if len(target.shape) > 2:
            target = cv.cvtColor(target, cv.COLOR_BGR2GRAY)
            
        # 调整模板大小以匹配目标图像
        if template.shape != target.shape:
            template = cv.resize(template, (target.shape[1], target.shape[0]))
        
        try:
            # 创建ORB特征检测器
            orb = cv.ORB_create()
            
            # 检测关键点和计算描述符
            kp1, des1 = orb.detectAndCompute(template, None)
            kp2, des2 = orb.detectAndCompute(target, None)
            
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
                if m.distance <= 60:  # 原始阈值
                    good_matches += 1
                    
            # 输出调试信息
            print(f"ORB匹配 - 关键点: {len(kp1)}/{len(kp2)}, 匹配点: {len(matches)}, 好的匹配点: {good_matches}")
            
            return good_matches
        except Exception as e:
            print(f"ORB特征点匹配出错: {e}")
            return 0
    
    # 获取临时目录中的所有图片
    temp_files = []
    for root, dirs, files in os.walk(temp_dir):
        for file in files:
            if file.lower().endswith(('.png', '.jpg', '.jpeg')):
                # 如果指定了目标文件名，则只处理该文件
                if target_file and file != target_file:
                    continue
                temp_files.append(os.path.join(root, file))
    
    if not temp_files:
        if target_file:
            print(f"在临时目录中未找到指定文件: {target_file}")
        else:
            print(f"临时目录中没有找到图片: {temp_dir}")
        return
    
    print(f"\n找到临时图片 {len(temp_files)} 个{f'(目标文件: {target_file})' if target_file else ''}，开始比较测试...")
    
    # 对每个临时图片进行测试
    for temp_file in temp_files:
        print(f"\n测试图片: {os.path.basename(temp_file)}")
        
        # 读取临时图片
        img = cv.imread(temp_file)
        if img is None:
            print(f"无法读取图片: {temp_file}")
            continue
        
        # 转换为numpy数组（模拟main.py中的处理方式）
        arr = np.array(img, dtype=np.uint8)
        
        print("=== 方法比较测试 ===")
        
        # 转换为灰度图
        if len(img.shape) == 3:
            gray_img = cv.cvtColor(img, cv.COLOR_BGR2GRAY)
        else:
            gray_img = img
        
        
        
        # 测试与每个模板的匹配度（使用三种不同的方法）
        print("\n1. 使用calculate_similarity方法（模板匹配）")
        results_calc = []
        for gun_id in gun_list:
            # 获取模板图像
            template_data = gun_img_dict[gun_id]
            if isinstance(template_data, dict) and 'image' in template_data:
                template_img = template_data['image']
            elif hasattr(template_data, 'shape'):
                template_img = template_data
            else:
                print(f"警告: 无法解析武器ID {gun_id} 的模板数据，跳过此模板")
                continue
            
            try:
                similarity = calculate_similarity(template_img, gray_img)
                results_calc.append((gun_id, similarity))
            except Exception as e:
                print(f"计算武器ID {gun_id} 的相似度时出错: {e}")
                continue
        
        # 按匹配度从高到低排序
        results_calc.sort(key=lambda x: x[1], reverse=True)
        
        # 输出匹配结果（按匹配度排序）
        print(f"{'武器ID':<10} {'模板匹配相似度':<25}")
        print("-" * 35)
        for gun_id, similarity in results_calc[:5]:  # 只显示前5个匹配结果
            print(f"{gun_id:<10} {similarity:.2f}")
        
        # 测试ORB特征点匹配
        print("\n2. 使用ORB特征点匹配（模拟主程序方法）")
        results_orb = []
        for gun_id in gun_list:
            # 获取模板图像
            template_data = gun_img_dict[gun_id]
            if isinstance(template_data, dict) and 'image' in template_data:
                template_img = template_data['image']
            elif hasattr(template_data, 'shape'):
                template_img = template_data
            else:
                print(f"警告: 无法解析武器ID {gun_id} 的模板数据，跳过此模板")
                continue
            
            try:
                similarity = calculate_orb_similarity(template_img, gray_img)
                results_orb.append((gun_id, similarity))
            except Exception as e:
                print(f"计算武器ID {gun_id} 的相似度时出错: {e}")
                continue
        
        # 按匹配度从高到低排序
        results_orb.sort(key=lambda x: x[1], reverse=True)
        
        # 输出匹配结果（按匹配度排序）
        print(f"{'武器ID':<10} {'ORB特征点匹配数':<25}")
        print("-" * 35)
        for gun_id, similarity in results_orb[:5]:  # 只显示前5个匹配结果
            print(f"{gun_id:<10} {similarity}")
            
        # 对比两种方法的排序结果
        print("\n=== 模板匹配与ORB特征点匹配排序对比 ===")
        print("模板匹配排序前5名:")
        for i, (gun_id, similarity) in enumerate(results_calc[:5]):
            print(f"{i+1}. 武器ID {gun_id}, 相似度 {similarity:.2f}")
            
        print("\nORB特征点匹配排序前5名:")
        for i, (gun_id, similarity) in enumerate(results_orb[:5]):
            print(f"{i+1}. 武器ID {gun_id}, 特征点数 {similarity}")
            
        # 检查两种方法的TOP1是否一致
        if results_calc and results_orb:
            if results_calc[0][0] == results_orb[0][0]:
                print("\n两种方法的最佳匹配结果一致，都是武器ID", results_calc[0][0])
            else:
                print("\n两种方法的最佳匹配结果不一致:")
                print(f"模板匹配方法: 武器ID {results_calc[0][0]}, 相似度 {results_calc[0][1]:.2f}")
                print(f"ORB特征点方法: 武器ID {results_orb[0][0]}, 特征点数 {results_orb[0][1]}")
        
        print("\n3. 使用主程序的_check_similarity方法（两种算法对比）")
        
        # 准备好主程序的gun_list和gun_img_dict
        assistant.gun_list = gun_list
        assistant.gun_img_dict = gun_img_dict
        
        # 输出主程序检测参数信息
        print("\n=== 使用特征点匹配 ===")
        confidence_thresholds = assistant.resolution_config.get_confidence_thresholds()
        print(f"置信度阈值: 高={confidence_thresholds['high']}, 低={confidence_thresholds['low']}")
        
        # 调用_check_similarity (特征点匹配)
        result_orb = assistant._check_similarity(arr, 1, False)
        print(f"特征点匹配结果: {result_orb}")
        
        # 调用_check_similarity (模板匹配)
        print("\n=== 使用模板匹配 ===")
        print(f"置信度阈值: 高=75%, 低=50%")
        result_template = assistant._check_similarity(arr, 1, True)
        print(f"模板匹配结果: {result_template}")
        
        # 综合分析
        print("\n=== 综合分析 ===")
        print("1. 算法差异:")
        print("   - 模板匹配(calculate_similarity)使用的是全局图像相似度计算")
        print("   - ORB特征点匹配关注的是图像中的特征点和它们的匹配情况")
        
        print("\n2. 阈值差异:")
        print(f"   - 特征点匹配使用高置信度阈值 {confidence_thresholds['high']} 和低置信度阈值 {confidence_thresholds['low']}")
        print("   - 模板匹配使用高置信度阈值 75% 和低置信度阈值 50%")
        
        # 推荐方案
        print("\n=== 推荐方案 ===")
        print("1. 为不同的武器选择适合的匹配算法:")
        print("   - 一些武器可能更适合使用模板匹配，如形状独特的武器")
        print("   - 另一些可能更适合使用特征点匹配，如有明显纹理特征的武器")
        print("2. 在UI中实时显示匹配度和匹配算法，方便用户选择")
        print("3. 保存识别成功的图像，建立特征库，进一步提高识别准确率")
        
        # 最终推荐
        print("\n=== 最终推荐 ===")
        if result_template and not result_orb:
            print("推荐使用模板匹配算法，可以识别出此武器")
        elif result_orb and not result_template:
            print("推荐使用特征点匹配算法，可以识别出此武器")
        elif result_orb and result_template:
            # 比较两种方法的最高匹配
            if results_calc[0][1] >= 70:  # 模板匹配度超过70%
                print("两种算法都能识别，但模板匹配准确度可能更高")
            else:
                print("两种算法都能识别，使用默认的特征点匹配即可")
        else:
            print("两种算法都无法可靠识别当前图像，建议更新模板或调整阈值")

if __name__ == "__main__":
    try:
        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        
        # 解析命令行参数
        parser = argparse.ArgumentParser(description='测试图像处理功能')
        parser.add_argument('--file', type=str, help='指定要匹配的文件名')
        parser.add_argument('--only-match', action='store_true', help='只运行模板匹配测试')
        parser.add_argument('--compare', action='store_true', help='运行比较测试')
        parser.add_argument('--save-results', action='store_true', help='将匹配结果保存到文件')
        args = parser.parse_args()
        
        if args.file is None:
            args.file = '202505040048180001.png'  # 设置默认值
        
        # 设置目录路径
        img_dir = os.path.join(base_dir, 'resource', '25601440')
        template_dir = os.path.join(base_dir, 'resource', '25601440')
        temp_dir = os.path.join(base_dir, 'resource', 'temp2313')
        
        # 根据参数决定运行哪些测试
        if args.compare:
            # 只运行比较测试
            print("=== 运行匹配方法比较测试 ===")
            test_comparison_matching(template_dir, temp_dir, args.file)
        else:
            # 测试黑色检测
            if not args.only_match:
                print("=== 测试模板图片的黑色区域检测 ===")
                test_black_detection(img_dir)
            
            # 测试模板匹配
            print("\n=== 测试临时截图与模板的匹配度 ===")
            test_template_matching(template_dir, temp_dir, args.file)
            
            # 如果需要保存结果
            if args.save_results:
                print("\n=== 保存匹配结果到文件 ===")
                save_matching_results_to_file(template_dir, temp_dir)
        
    except Exception as e:
        import traceback
        print(f"程序执行出错: {e}")
        print(traceback.format_exc())
