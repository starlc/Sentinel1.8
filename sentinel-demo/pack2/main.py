import os
import sys
import threading
import time
import pynput
import cv2 as cv
from pynput.mouse import Button
from pynput import keyboard
from queue import Queue
import numpy as np

from config_manager import ConfigManager
from image_processor import ImageProcessor
from ui_manager import UIManager
from resolution_config import ResolutionConfig


class PUBGAssistant:
    """PUBG辅助程序主类，整合所有功能模块"""
    
    def __init__(self):
        # 初始化全局变量
        self.action_queue = Queue()
        self.current_gun = {1: "", 2: ""}  # 当前的武器名
        self.player_posture = 1  # 姿势 1为站 2为蹲 3为趴(暂时不用) 默认1
        self.player_gun = 0
        self.player_bullets = 1
        self.gun_list = []
        self.gun_img_dict = {}
        self.gun_lock = 0  # 武器锁定 0 初始化 1锁定
        self.player_gun_config = True  # 枪械是否满配 false 满配 true 裸配
        self.detection_method = 0  # 图像识别方法 0为原始方法 1为增强方法
        self.check_timers = []  # 存储检测定时器
        self.logging_enabled = False  # 日志总开关，默认关闭
        
        # 初始化组件
        self.config_manager = ConfigManager()
        self.image_processor = ImageProcessor()
        self.ui_manager = UIManager()
        self.resolution_config = ResolutionConfig()
        self.job = None
        
        # 初始化配置
        self._initialize_resources()
    
    def _get_resource_path(self, relative_path):
        """获取资源文件的绝对路径，兼容开发环境和PyInstaller打包后的环境"""
        try:
            # PyInstaller创建临时文件夹，将路径存储在_MEIPASS中
            base_path = sys._MEIPASS
        except Exception:
            # 如果不是打包环境，则使用当前目录
            base_path = os.path.abspath(".")
        
        return os.path.join(base_path, relative_path)
    
    def _initialize_resources(self):
        """初始化资源和配置"""
        # 获取资源路径
        resource_dir = self.resolution_config.get_resource_dir()
        resource_path = self._get_resource_path(resource_dir)
        
        # 加载枪械配置
        success, self.gun_dict, self.gun_list_name = self.config_manager.load_gun_configs(self._get_resource_path)
        
        # 加载枪械图像
        self.gun_list, self.gun_img_dict = self.image_processor.load_gun_images(resource_path)
        
        # 初始化键盘状态
        self.player_gun_config = not self.is_numlock_on()
    
    def start(self):
        """启动程序"""
        # 创建UI显示并启用双缓冲
        ui_position = self.resolution_config.get_ui_position()
        app = self.ui_manager.create_display_app("启动", ui_position["x"], ui_position["y"])
        app.doubleBuffered = True
        
        # 初始化线程
        self._initialize_threads()
        
        # 显示欢迎信息
        os.system("title PUBG Assistant")
        os.system("mode con cols=50 lines=30")
        print("按F2键切换日志开关，当前日志状态：关闭")
        
        # 启动所有线程
        for t in self.threads:
            t.start()
        
        # 启动UI
        self.ui_manager.run_display_app()
    
    def _initialize_threads(self):
        """初始化并启动线程"""
        self.job = self.PostureMonitor(self)
        
        # 创建线程
        self.threads = [
            threading.Thread(target=self._consumer),
            threading.Thread(target=self._keyboard_listener),
            threading.Thread(target=self._mouse_listener),
            self.job
        ]
    
    def _consumer(self):
        """消费者线程，处理队列中的动作"""
        while True:
            action = self.action_queue.get()
            if action.get_type():
                self._handle_keyboard_action(action.get_param())
            else:
                # 鼠标右键按下时 检测是蹲还是站
                self._check_posture(action.get_param())
            self.action_queue.task_done()
    
    def _handle_keyboard_action(self, key):
        """处理键盘动作"""
        # 锁定武器栏
        if key == 4:
            self._lock_weapon_bar()
            return True
        
        # 临时关闭宏
        elif key == 0:
            self._close_weapon(key)
            return True
        
        # 检测NumLock状态
        elif key == keyboard.Key.num_lock:
            self._change_weapon_config_state()
            return True
        
        # 切换图像识别方法
        elif key == "f8":
            self._toggle_detection_method()
            return True
        else:
            # 对应1,2切换武器或者识别
            if self.gun_lock == 0:
                self._screen(key)
            else:
                self._save_play_gun_and_sound(self.current_gun[key], key)
    
    def _keyboard_listener(self):
        """键盘监听线程"""
        with pynput.keyboard.Listener(on_release=self._on_key_release) as listener:
            listener.join()
    
    def _on_key_release(self, key):
        """键盘按键释放回调"""
        try:
            if key == keyboard.Key.f9:  # 按下F9退出程序
                os._exit(0)
                return True
            
            if key == keyboard.Key.f8:  # 按下F8切换图像识别方法
                self.action_queue.queue.clear()
                action = self.Action(True, "f8")
                self.action_queue.put(action)
                return True
            
            if key == keyboard.Key.f1:  # 按下F1 将武器设值为0
                self.action_queue.queue.clear()  # 清空队列
                action = self.Action(True, 0)
                self.action_queue.put(action)
                return True
            
            if key == keyboard.Key.f2:  # 按下F2切换日志开关
                self.logging_enabled = not self.logging_enabled
                self.log(f"日志状态: {'开启' if self.logging_enabled else '关闭'}")
                return True
            
            if key == keyboard.Key.num_lock:  # 按下numlock 变更numLock状态
                self.action_queue.queue.clear()
                action = self.Action(True, key)
                self.action_queue.put(action)
                return True
            
            if hasattr(key, 'char') and str(key.char) == '`':  # 按下~ 锁定武器栏
                self.action_queue.queue.clear()
                action = self.Action(True, 4)
                self.action_queue.put(action)
                return True
            
            if hasattr(key, 'char') and str(key.char) in ['1', '2', '4']:
                key_int = int(key.char)
                self.action_queue.queue.clear()  # 每次按下 1 或者 2 清空掉之前的队列
                action = self.Action(True, key_int)
                self.action_queue.put(action)
                return True
        except Exception as e:
            self.log(f"键盘事件处理错误: {e}")
        return True
    
    def _mouse_listener(self):
        """鼠标监听线程"""
        with pynput.mouse.Listener(on_click=self._on_mouse_click) as listener:
            listener.join()
    
    def _on_mouse_click(self, x, y, button, pressed):
        """鼠标点击回调"""
        if Button.x2 == button:
            if pressed:
                # 启动姿势监控线程
                self.job.resume()
                
                # 取消之前的所有定时器（如果有）
                if hasattr(self, 'check_timers') and self.check_timers:
                    for timer in self.check_timers:
                        if timer and timer.is_alive():
                            timer.cancel()
                
                # 初始化定时器列表
                self.check_timers = []
                
                # 立即开始一次检测
                self._check_posture()
                
                # 创建按照1,2,4,8秒间隔的检测任务
                intervals = [1, 2, 4, 8]
                for interval in intervals:
                    timer = threading.Timer(interval, self._check_posture)
                    timer.daemon = True
                    timer.start()
                    self.check_timers.append(timer)
            else:
                # 暂停姿势监控线程
                self.job.pause()
                
                # 释放按键时，取消所有定时检测
                if hasattr(self, 'check_timers'):
                    for timer in self.check_timers:
                        if timer and timer.is_alive():
                            timer.cancel()
                    self.check_timers = []
    
    def _lock_weapon_bar(self):
        """锁定武器栏"""
        self.gun_lock = 1 if self.gun_lock == 0 else 0
        self._update_display()
    
    def _change_weapon_config_state(self):
        """变更武器满配、裸配"""
        self.player_gun_config = not self.player_gun_config
        self._update_display()
    
    def _close_weapon(self, key):
        """关闭宏"""
        self.config_manager.save_config("gun", "0")
        self.ui_manager.play_sound("close")
    
    def _screen(self, gun_pos):
        """截屏检测武器"""
        # 获取武器检测参数
        weapon_params = self.resolution_config.get_weapon_detection_params()
        left = weapon_params["left"]
        top = weapon_params["top"]
        width = weapon_params["width"]
        height = weapon_params["height"]
        
        if gun_pos == 2:
            top = top - weapon_params["offset_for_second"]
        
        box = (left, top, left + width, top + height)
        n = 0
        time.sleep(0.35)  # 等待UI稳定
        
        self.log(f"开始识别武器槽 {gun_pos} 的武器，截图区域: {box}")
        
        while True:
            # 截图并转换为numpy数组
            img = self.image_processor.screenshot(box)
            arr = np.array(img.pixels, dtype=np.uint8)
            
            # 保存截图用于调试
            temp_path = self._get_resource_path('resource/temp2313/')
            self.image_processor.save_temp_pic(img, temp_path, False)
            self.log(f"第 {n+1} 次尝试识别，已保存截图")
            
            # 尝试识别武器
            if self._check_similarity(arr, gun_pos):  # 如果返回True 退出循环
                self.log(f"武器识别成功，退出识别循环")
                break
            
            n = n + 1
            if n >= 2:  # 如果2次还没有识别出来 退出循环
                self.log(f"经过 {n} 次尝试，武器识别失败")
                break
            
            self.log(f"等待1秒后进行下一次识别尝试")
            time.sleep(1)
    
    def _check_similarity(self, im, gun_pos):
        """相似度检测"""
        similarity_dict = {}
        # 获取置信度阈值
        confidence_thresholds = self.resolution_config.get_confidence_thresholds()
        high_confidence = confidence_thresholds["high"]  # 高置信度阈值
        low_confidence = confidence_thresholds["low"]   # 低置信度阈值
        
        # 预处理输入图像
        try:
            # 转换为灰度图
            if len(im.shape) == 3:
                gray_im = cv.cvtColor(im, cv.COLOR_BGR2GRAY)
            else:
                gray_im = im
            
            # 打印调试信息
            self.log(f"输入图像形状: {im.shape}, 处理后形状: {gray_im.shape}")
        except Exception as e:
            self.log(f"图像预处理失败: {e}")
            gray_im = im
        
        # 对每个武器模板进行匹配
        for gun_name in self.gun_list:
            result = self.image_processor.image_similarity_opencv(self.gun_img_dict[gun_name], gray_im)
            similarity_dict[gun_name] = result
            
            # 高置信度匹配
            if result >= high_confidence:
                # 如果当前持有枪械和检测到的枪械不同 切换武器
                self._save_play_gun_and_sound(gun_name, gun_pos)
                self.log(f"高置信度切换武器: {self._get_gun_name(int(gun_name))}  当前武器栏: {gun_pos}识别度: {result}")
                return True
        
        # 如果没有高相似度匹配，找出最高相似度的武器
        if similarity_dict:
            # 按相似度排序
            sorted_matches = sorted(similarity_dict.items(), key=lambda x: x[1], reverse=True)
            best_match = sorted_matches[0]
            
            self.log(f"本轮相似度最大值为武器: {self._get_gun_name(int(best_match[0]))}, 相似度: {best_match[1]}")
            
            # 如果有第二高的匹配，检查差距
            if len(sorted_matches) > 1:
                second_match = sorted_matches[1]
                diff = best_match[1] - second_match[1]
                self.log(f"第二高相似度武器: {self._get_gun_name(int(second_match[0]))}, 相似度: {second_match[1]}, 差距: {diff}")
                
                # 如果最高相似度显著高于第二高的，增加可信度
                if diff > 5 and best_match[1] >= low_confidence:
                    self._save_play_gun_and_sound(str(best_match[0]), gun_pos)
                    self.log(f"差距显著切换武器: {self._get_gun_name(int(best_match[0]))}  当前武器栏: {gun_pos} "
                           f"当前姿势: {'站' if self.player_posture == 1 else '蹲'}")
                    return True
            
            # 如果只有一个匹配或者没有显著差距，使用原始低置信度阈值
            if best_match[1] >= low_confidence:
                self._save_play_gun_and_sound(str(best_match[0]), gun_pos)
                self.log(f"低置信度切换武器: {self._get_gun_name(int(best_match[0]))}  当前武器栏: {gun_pos} "
                       f"当前姿势: {'站' if self.player_posture == 1 else '蹲'}")
                return True
        
        return False
    
    def _check_posture(self, param=None):
        """判断姿势"""
        save_flag = False
        
        # 获取姿势检测参数
        posture_params = self.resolution_config.get_posture_detection_params()
        point1 = posture_params["point1"]
        point2 = posture_params["point2"]
        
        # 检测点1
        time.sleep(0.05)  # 短暂延时以确保截图稳定
        box = (point1["left"], point1["top"], point1["left"] + point1["width"], point1["top"] + point1["height"])
        posture_path = self._get_resource_path('resource/posturetemp/')
        result1 = self.image_processor.get_rgb(box, posture_path)
        
        # 检测点2
        box2 = (point2["left"], point2["top"], point2["left"] + point2["width"], point2["top"] + point2["height"])
        result2 = self.image_processor.get_rgb(box2, posture_path)
        
        self.log(f"姿势检测结果: 点1={result1}, 点2={result2}")
        
        if result1 and result2:
            if self.player_posture == 99:
                self.player_posture = 1
                save_flag = True
        else:
            if self.player_posture == 1:
                self.player_posture = 99
                save_flag = True
        
        if save_flag:
            self.config_manager.save_config("posture", str(self.player_posture))
            self._update_display()
        
        # 不再需要固定延时，由定时器控制调用频率
    
    def _save_play_gun_and_sound(self, gun_name, gun_pos):
        """保存武器到配置文件并且播报"""
        if self.player_gun != gun_name and gun_name != '':
            self.player_gun = gun_name
            self.current_gun[gun_pos] = gun_name  # 避免重复操作
            self.config_manager.save_config("gun", str(gun_name))
            self._update_display()
            # self.ui_manager.play_sound(self._get_gun_name(int(gun_name)))
    
    def _update_display(self):
        """更新显示"""
        self.ui_manager.update_display(
            gun_lock=self.gun_lock,
            player_gun=self.player_gun,
            player_posture=self.player_posture,
            player_gun_config=self.player_gun_config,
            detection_method=self.detection_method,
            logging_enabled=self.logging_enabled,
            get_gun_name_func=self._get_gun_name
        )
        
    def _toggle_detection_method(self):
        """切换图像识别方法"""
        self.detection_method = 1 - self.detection_method  # 在0和1之间切换
        method_name = "原始" if self.detection_method == 0 else "增强"
        self.log(f"已切换到{method_name}检测方法")
        self._update_display()
    
    def _get_gun_name(self, gun):
        """获取枪械名称"""
        return self.config_manager.get_gun_name(gun)
    
    def is_numlock_on(self):
        """检查NumLock状态"""
        import ctypes
        hll_dll = ctypes.WinDLL("User32.dll")
        VK_NUMLOCK = 0x90
        return hll_dll.GetKeyState(VK_NUMLOCK) & 1
    
    def log(self, message):
        """日志输出方法，根据总开关决定是否打印日志"""
        if self.logging_enabled:
            print(message)
    
    class Action:
        """动作类，用于队列中的动作处理"""
        # action_type 定义: True 为切换武器  False 为切换姿势
        def __init__(self, action_type, param):
            self.action_type = action_type
            self.param = param
        
        def get_type(self):
            return self.action_type
        
        def get_param(self):
            return self.param
    
    class PostureMonitor(threading.Thread):
        """姿势监控线程类"""
        def __init__(self, parent):
            super().__init__()
            self.parent = parent
            self.__flag = threading.Event()  # 用于暂停线程的标识
            self.__running = threading.Event()  # 用于停止线程的标识
            self.__running.set()  # 将running设置为True
        
        def run(self):
            # 这个线程现在仅作为后备监控，侧键检测主要由定时器控制
            while self.__running.is_set():
                self.__flag.wait()  # 为True时立即返回, 为False时阻塞直到内部的标识位为True后返回
                # 暂停线程的具体检测逻辑，由定时器直接调用_check_posture方法
                time.sleep(0.5)  # 降低CPU使用率
        
        def pause(self):
            self.__flag.clear()  # 设置为False, 让线程阻塞
        
        def resume(self):
            self.__flag.set()  # 设置为True, 让线程停止阻塞
        
        def stop(self):
            self.__flag.set()  # 将线程从暂停状态恢复, 如果已经暂停的话
            self.__running.clear()  # 设置为False


# 程序入口
if __name__ == '__main__':
    assistant = PUBGAssistant()
    assistant.start()