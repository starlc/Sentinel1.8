import cv2 as cv
import numpy as np
import os
from PIL import Image
from datetime import datetime
from mss import mss

class ImageProcessor:
    """图像处理类，负责截图和图像识别"""
    
    def __init__(self):
        self.global_seq = 1
        self.detection_method = 0  # 0: 原始方法, 1: 增强方法
    
    def screenshot(self, box):
        """截取屏幕指定区域
        
        Args:
            box: 截图区域 (left, top, right, bottom)
        
        Returns:
            截图对象
        """
        with mss() as sct:
            return sct.grab(box)
    
    def get_rgb(self, box, temp_path='resource/posturetemp/'):
        """获取指定区域的RGB值
        
        Args:
            box: 截图区域 (left, top, right, bottom)
            temp_path: 临时图片保存路径
        
        Returns:
            bool: 是否符合条件（RGB值都大于190）
        """
        img = self.screenshot(box)
        self.save_temp_pic(img, temp_path, False)
        r, g, b = img.pixel(3, 3)
        #print(f"r: {r}, g: {g}, b: {b}")
        
        return r > 190 and g > 190 and b > 190
    
    def save_temp_pic(self, img, path, is_save):
        """保存临时图片
        
        Args:
            img: 图片对象
            path: 保存路径
            is_save: 是否保存
        
        Returns:
            bool: 是否成功
        """
        if not is_save:
            return True
        
        try:
            # 确保目录存在
            os.makedirs(os.path.dirname(path), exist_ok=True)
            
            img_pil = Image.frombytes("RGB", img.size, img.bgra, "raw", "BGRX")
            save_path = os.path.abspath(f"{path}{self.get_sequence()}")
            img_pil.save(f"{save_path}.png", format='PNG')
            return True
        except Exception as e:
            print(f"保存图片失败: {e}")
            return False
    
    def get_sequence(self):
        """获取序列号
        
        Returns:
            str: 序列号
        """
        now = datetime.now()
        str_date = now.strftime('%Y%m%d%H%M%S')
        str_seq = f"{self.global_seq:04d}"
        self.global_seq = self.global_seq + 1
        return f"{str_date}{str_seq}"
    
    def load_gun_images(self, img_dir):
        """加载枪械图像
        
        Args:
            img_dir: 图像目录
        
        Returns:
            gun_list: 枪械列表
            gun_img_dict: 枪械图像字典，每个元素包含图像和左侧是否全黑的标记
        """
        # 确保目录存在
        if not os.path.exists(img_dir):
            print(f"警告: 图像目录 {img_dir} 不存在")
            return [], {}
        gun_list = []
        gun_img_dict = {}
        
        try:
            for files in os.listdir(img_dir):
                if os.path.splitext(files)[1] == '.png':
                    gun_id = os.path.splitext(files)[0]
                    gun_list.append(gun_id)
                    gun_file = os.path.join(img_dir, files)
                    img = cv.imread(gun_file, cv.IMREAD_GRAYSCALE)
                    
                    # 检查图像左侧1/5和中间3/5区域是否全黑
                    left_side_black = False
                    middle_black = False
                    if img is not None:
                        width = img.shape[1]
                        # 获取图像左侧1/5区域
                        left_region = img[:, :width//5]
                        # 获取图像中间2/5区域
                        middle_start = width//5
                        middle_end = middle_start + (width * 2)//5
                        middle_region = img[:, middle_start:middle_end]
                        
                        # 如果区域的所有像素值都小于10（接近黑色），则标记为全黑
                        if np.all(left_region < 10):
                            left_side_black = True
                            print(f"检测到武器图片 {files} 左侧1/5全黑")
                        if np.all(middle_region < 10):
                            middle_black = True
                            print(f"检测到武器图片 {files} 中间2/5全黑")
                    
                    gun_img_dict[gun_id] = {
                        'image': img,
                        'left_side_black': left_side_black,
                        'middle_black': middle_black
                    }
            
            return gun_list, gun_img_dict
        except Exception as e:
            print(f"加载枪械图像失败: {e}")
            return [], {}
    
    def image_similarity_opencv(self, img1_data, img2):
        """对比图片特征点
        
        Args:
            img1_data: 第一张图片的数据，包含图像和区域黑色标记
            img2: 第二张图片
        
        Returns:
            int: 相似度分数
        """
        # 获取图像和区域黑色标记
        img1 = img1_data['image']
        left_side_black = img1_data['left_side_black']
        middle_black = img1_data['middle_black']
        
        # 根据黑色区域标记调整比对区域
        width = img1.shape[1]
        if left_side_black and middle_black:
            # 如果左侧1/5和中间2/5都是黑色，只比对右侧2/5区域
            start = (width * 3) // 5
            img1 = img1[:, start:]
            img2 = img2[:, start:]
        elif left_side_black:
            # 如果只有左侧1/5是黑色，比对右侧4/5区域
            start = width // 5
            img1 = img1[:, start:]
            img2 = img2[:, start:]
        elif middle_black:
            # 如果只有中间3/5是黑色，比对两侧1/5区域
            left_part1 = img1[:, :width//5]
            left_part2 = img2[:, :width//5]
            right_part1 = img1[:, (width*4)//5:]
            right_part2 = img2[:, (width*4)//5:]
            img1 = np.hstack((left_part1, right_part1))
            img2 = np.hstack((left_part2, right_part2))
        
        # 根据当前设置的检测方法选择不同的算法
        if self.detection_method == 0:
            return self._image_similarity_original(img1, img2)
        else:
            return self._image_similarity_template(img1, img2)
    
    def _image_similarity_original(self, img1, img2):
        """原始的特征点匹配方法
        
        Args:
            img1: 第一张图片
            img2: 第二张图片
        
        Returns:
            int: 相似度分数
        """
        try:
            image1 = img1
            # 确保图像转换正确
            if len(img2.shape) == 3:
                image2 = cv.cvtColor(img2, cv.COLOR_BGR2GRAY)
            else:
                image2 = img2
            
            # 创建ORB特征检测器
            orb = cv.ORB_create()
            
            # 检测关键点和计算描述符
            kp1, des1 = orb.detectAndCompute(image1, None)
            kp2, des2 = orb.detectAndCompute(image2, None)
            
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
            
            return good_matches
        except Exception as e:
            print(f"图像相似度计算失败: {e}")
            return 0
    
    def _image_similarity_template(self, img1, img2):
        """改进的模板匹配方法
        
        Args:
            img1: 第一张图片（模板图像）
            img2: 第二张图片（目标图像）
        
        Returns:
            float: 相似度分数（百分比）
        """
        try:
            # 确保两个图像都是灰度图
            if len(img1.shape) == 3:
                img1 = cv.cvtColor(img1, cv.COLOR_BGR2GRAY)
            if len(img2.shape) == 3:
                img2 = cv.cvtColor(img2, cv.COLOR_BGR2GRAY)
            
            # 确定哪个是模板，哪个是目标图像
            # 通常模板应该小于或等于目标图像
            if img1.shape[0] > img2.shape[0] or img1.shape[1] > img2.shape[1]:
                # 如果img1更大，则将其调整为与img2相同大小
                template = cv.resize(img1, (img2.shape[1], img2.shape[0]))
                target = img2
                print("调整模板大小以匹配目标图像")
            else:
                template = img1
                target = img2
            
            # 应用直方图均衡化以增强对比度
            template = cv.equalizeHist(template)
            target = cv.equalizeHist(target)
            
            # 应用高斯模糊减少噪声
            template = cv.GaussianBlur(template, (3, 3), 0)
            target = cv.GaussianBlur(target, (3, 3), 0)
            
            # 应用自适应阈值处理增强边缘特征
            template = cv.adaptiveThreshold(template, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 11, 2)
            target = cv.adaptiveThreshold(target, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 11, 2)
            
            # 使用多种模板匹配方法并取加权平均结果
            methods = [
                (cv.TM_CCOEFF_NORMED, 0.2),  # 相关系数匹配，权重0.6
                (cv.TM_CCORR_NORMED, 0.7),   # 相关匹配，权重0.3
                (cv.TM_SQDIFF_NORMED, 0.1)   # 平方差匹配，权重0.1
            ]
            
            weighted_similarity = 0
            
            for method, weight in methods:
                # 如果模板大于目标，需要调整大小
                if template.shape[0] > target.shape[0] or template.shape[1] > target.shape[1]:
                    template = cv.resize(template, (target.shape[1], target.shape[0]))
                
                # 执行模板匹配
                result = cv.matchTemplate(target, template, method)
                
                # 对于TM_SQDIFF和TM_SQDIFF_NORMED，值越小越好
                if method == cv.TM_SQDIFF_NORMED:
                    min_val, _, _, _ = cv.minMaxLoc(result)
                    # 转换为相似度分数（1-距离）
                    similarity = (1 - min_val) * 100
                else:
                    _, max_val, _, _ = cv.minMaxLoc(result)
                    similarity = max_val * 100
                
                # 累加加权相似度
                weighted_similarity += similarity * weight
                print(f"方法 {method} 相似度: {similarity:.2f}%, 权重: {weight}")
            
            # 计算结构相似性指数(SSIM)作为附加指标
            try:
                from skimage.metrics import structural_similarity as ssim
                s_score = ssim(template, target)
                ssim_score = s_score * 100
                print(f"SSIM相似度: {ssim_score:.2f}%")
                
                # 将SSIM分数与加权模板匹配分数结合
                final_similarity = weighted_similarity * 0.7 + ssim_score * 0.3
            except ImportError:
                # 如果没有skimage库，只使用模板匹配分数
                final_similarity = weighted_similarity
                print("未安装skimage库，无法计算SSIM相似度")
            
            print(f"模板匹配最终结果: {final_similarity:.2f}%")
            return final_similarity
        except Exception as e:
            print(f"模板匹配出错: {e}")
            return 0
            
    def set_detection_method(self, method):
        """设置检测方法
        
        Args:
            method: 检测方法 (0: 原始方法, 1: 增强方法)
        """
        self.detection_method = method
        method_name = "原始" if method == 0 else "增强"
        print(f"已切换到{method_name}检测方法")
        return method_name
        
    def toggle_detection_method(self):
        """切换检测方法
        
        Returns:
            str: 当前检测方法名称
        """
        self.detection_method = 1 - self.detection_method  # 在0和1之间切换
        method_name = "原始" if self.detection_method == 0 else "增强"
        print(f"已切换到{method_name}检测方法")
        return method_name
    
    def extract_gun(self, image):
        """提取图片中的枪(预处理图片)
        
        Args:
            image: 原始图像
        
        Returns:
            处理后的图像
        """
        try:
            # 创建图像副本以避免修改原始图像
            processed_image = image.copy()
            
            # 阈值处理，将低于200的像素值设为0
            processed_image[processed_image <= 200] = 0
            
            return processed_image
        except Exception as e:
            print(f"图像预处理失败: {e}")