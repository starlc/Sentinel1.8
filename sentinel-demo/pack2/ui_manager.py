import tkinter as tk
import pyttsx3 as pytts
import threading

class UIManager:
    """UI管理类，负责界面显示和声音播放"""
    
    def __init__(self):
        self.app = None
    
    def create_display_app(self, initial_character, x, y):
        """创建显示应用
        
        Args:
            initial_character: 初始显示文本
            x: 窗口x坐标
            y: 窗口y坐标
        
        Returns:
            CharacterDisplayApp: 显示应用实例
        """
        root = tk.Tk()
        self.app = CharacterDisplayApp(root, initial_character, x, y)
        return self.app
    
    def run_display_app(self):
        """运行显示应用"""
        if self.app:
            self.app.root.mainloop()
    
    def update_display(self, gun_lock, player_gun, player_posture, player_gun_config, detection_method, get_gun_name_func, logging_enabled=False):
        """更新显示内容
        
        Args:
            gun_lock: 武器锁定状态
            player_gun: 玩家当前武器
            player_posture: 玩家姿势
            player_gun_config: 武器配置状态
            detection_method: 图像识别方法
            get_gun_name_func: 获取武器名称的函数
            logging_enabled: 日志开关状态
        """
        if not self.app:
            return
            
        gun_status = "锁" if gun_lock == 1 else "解"
        gun_name = get_gun_name_func(int(player_gun)) if player_gun else ""
        posture_status = "站" if player_posture == 1 else "蹲"
        full_status = "满" if player_gun_config else "裸"
        method_status = "" if detection_method == 0 else "|"
        new_character = f"{gun_status}|{full_status}|{posture_status}{method_status}|{gun_name}"
        self.app.update_character(new_character)
    
    def play_sound(self, content):
        """播放声音
        
        Args:
            content: 要播放的文本内容
        """
        # 在单独的线程中播放声音，避免阻塞主线程
        sound_thread = threading.Thread(target=self._play_sound_thread, args=(content,))
        sound_thread.daemon = True
        sound_thread.start()
    
    def _play_sound_thread(self, content):
        """声音播放线程
        
        Args:
            content: 要播放的文本内容
        """
        try:
            engine = pytts.Engine()
            engine.setProperty('rate', 220)  # 语速
            engine.setProperty('volume', 0.35)  # 音量
            engine.say(content)
            engine.runAndWait()
            engine.stop()
        except Exception as e:
            print(f"播放声音失败: {e}")


class CharacterDisplayApp:
    """字符显示应用类"""
    def __init__(self, root, initial_character, x, y):
        self.root = root
        self.root.title("PUBG Assistant")
        
        # 去掉窗口边框
        self.root.overrideredirect(True)
        
        # 设置窗口透明度为50%
        self.root.attributes("-alpha", 0.5)
        
        # 设置窗口置顶
        self.root.attributes("-topmost", True)
        
        # 创建标签并设置背景为浅灰色，字体颜色为深蓝色
        self.label = tk.Label(root, text=initial_character, font=("Microsoft YaHei", 12),
                            fg="#1e3a8a", bg="#d1d5db", anchor="w", justify="left")
        self.label.pack(fill="both", expand=True)
        
        # 设置窗口位置
        self.root.geometry(f"+{x}+{y}")
    
    def update_character(self, new_character):
        """更新显示内容
        
        Args:
            new_character: 新的显示内容
        """
        self.label.config(text=new_character)