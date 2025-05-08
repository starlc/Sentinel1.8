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
        print(f"r: {r}, g: {g}, b: {b}")
        
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
            return save_path+'.png'
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
    
    def preprocess_image(self, image):
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
    
    def image_similarity_opencv(self, img1, img2):
        """对比图片特征点
        
        Args:
            img1: 第一张图片数据，可以是图像或包含图像和区域黑色标记的字典
            img2: 第二张图片，可以是图像、文件路径或截图对象
        
        Returns:
            int: 相似度分数
        """
        # 处理img1，可能是字典或直接是图像
        if isinstance(img1, dict) and 'image' in img1:
            template_img = img1['image']
            left_side_black = img1.get('left_side_black', False)
            middle_black = img1.get('middle_black', False)
        else:
            template_img = img1
            left_side_black = False
            middle_black = False
        
        # 预处理图像，统一格式
        try:
            # 处理模板图像
            if isinstance(template_img, np.ndarray):
                if len(template_img.shape) == 3:
                    template_gray = cv.cvtColor(template_img, cv.COLOR_BGR2GRAY)
                else:
                    template_gray = template_img
            else:
                template_gray = self.preprocess_image(template_img)
            
            # 处理目标图像
            if isinstance(img2, np.ndarray):
                if len(img2.shape) == 3:
                    target_gray = cv.cvtColor(img2, cv.COLOR_BGR2GRAY)
                else:
                    target_gray = img2
            else:
                target_gray = self.preprocess_image(img2)
            
            # 打印调试信息
            print(f"模板图像形状: {template_gray.shape}, 目标图像形状: {target_gray.shape}")
        except Exception as e:
            print(f"图像预处理失败: {e}")
            return 0
        
        # 根据黑色区域标记调整比对区域
        width = template_gray.shape[1]
        if left_side_black and middle_black:
            # 如果左侧1/5和中间2/5都是黑色，只比对右侧2/5区域
            start = (width * 3) // 5
            template_gray = template_gray[:, start:]
            target_gray = target_gray[:, start:]
            print(f"裁剪右侧2/5区域进行比对")
        elif left_side_black:
            # 如果只有左侧1/5是黑色，比对右侧4/5区域
            start = width // 5
            template_gray = template_gray[:, start:]
            target_gray = target_gray[:, start:]
            print(f"裁剪右侧4/5区域进行比对")
        
        # 根据当前设置的检测方法选择不同的算法
        if self.detection_method == 0:
            return self._image_similarity_original(template_gray, target_gray)
        else:
            return self._image_similarity_template(template_gray, target_gray)
    
    def _image_similarity_original(self, img1, img2):
        """原始的特征点匹配方法
        
        Args:
            img1: 第一张图片
            img2: 第二张图片
        
        Returns:
            int: 相似度分数
        """
        try:
            # 创建ORB特征检测器
            orb = cv.ORB_create()
            
            # 检测关键点和计算描述符
            kp1, des1 = orb.detectAndCompute(img1, None)
            kp2, des2 = orb.detectAndCompute(img2, None)
            
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
            
            print(f"特征点匹配结果: {good_matches} 个好的匹配点")
            return good_matches
        except Exception as e:
            print(f"图像相似度计算失败: {e}")
            return 0
    
    def _image_similarity_template(self, img1, img2):
        """模板匹配方法
        
        Args:
            img1: 第一张图片
            img2: 第二张图片
        
        Returns:
            float: 相似度分数（百分比）
        """
        # 调整大小以确保匹配
        if img1.shape != img2.shape:
            img1 = cv.resize(img1, (img2.shape[1], img2.shape[0]))
            
        # 使用OpenCV的模板匹配
        try:
            result = cv.matchTemplate(img2, img1, cv.TM_CCOEFF_NORMED)
            _, max_val, _, _ = cv.minMaxLoc(result)
            similarity = max_val * 100  # 转换为百分比
            print(f"模板匹配结果: {similarity:.2f}%")
            return similarity
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
            return image