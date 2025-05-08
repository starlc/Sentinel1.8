import os
import json

class ConfigManager:
    """配置文件管理类，负责加载和保存配置"""
    
    def __init__(self, base_dir="D:\\pubg\\"):
        self.base_dir = base_dir
        self.gun_dict = {}
        self.gun_list_name = []
    
    def load_gun_configs(self, get_resource_path_func=None):
        """加载枪械配置文件
        
        Args:
            get_resource_path_func: 获取资源路径的函数，用于兼容打包环境
        """
        try:
            # 确定资源路径
            if get_resource_path_func:
                gun_dict_path = get_resource_path_func('resource//dict//gun_dict.json')
                gun_arr_path = get_resource_path_func('resource//dict//gun_arr.json')
            else:
                gun_dict_path = r'resource//dict//gun_dict.json'
                gun_arr_path = r'resource//dict//gun_arr.json'
            
            with open(gun_dict_path, 'r') as f:
                self.gun_dict = json.load(f)
            
            with open(gun_arr_path, 'r') as f:
                self.gun_list_name = json.load(f)
            
            return True, self.gun_dict, self.gun_list_name
        except Exception as e:
            print(f"加载配置文件失败: {e}")
            return False, {}, []
    
    def save_config(self, title, content):
        """保存配置到lua文件
        
        Args:
            title: 配置文件名称
            content: 配置内容
        
        Returns:
            bool: 保存是否成功
        """
        file_path = os.path.join(self.base_dir, f"{title}.lua")
        field = title
        if title == "gun":
            field = "weaponNo"
        
        try:
            with open(file_path, "w+") as file:
                #print(f"{field} = {content}")
                file.write(f"{field}={content}")
                file.flush()
                os.fsync(file.fileno())
            # 强制刷新文件系统缓存
            # if os.name == 'nt':
            #     os.system(f'fsutil file seteof {file_path} {os.path.getsize(file_path)}')
            return True
        except Exception as e:
            print(f"保存配置失败: {e}")
            return False
    
    def get_gun_name(self, gun_index):
        """获取枪械名称
        
        Args:
            gun_index: 枪械索引
        
        Returns:
            str: 枪械名称
        """
        if not self.gun_list_name or gun_index <= 0 or gun_index > len(self.gun_list_name):
            return str(gun_index)
        return self.gun_list_name[gun_index - 1] or str(gun_index)
    
    def get_gun_config_name(self, gun_name):
        """枪名取对应的lua配置名
        
        Args:
            gun_name: 枪械名称
        
        Returns:
            str: 配置名称
        """
        return self.gun_dict.get(gun_name, gun_name)
        
    def save_detection_method(self, method):
        """保存检测方法配置
        
        Args:
            method: 检测方法 (0: 原始方法, 1: 增强方法)
            
        Returns:
            bool: 保存是否成功
        """
        return self.save_config("detection_method", str(method))
        
    def load_detection_method(self):
        """加载检测方法配置
        
        Returns:
            int: 检测方法 (0: 原始方法, 1: 增强方法)
        """
        try:
            file_path = os.path.join(self.base_dir, "detection_method.lua")
            if os.path.exists(file_path):
                with open(file_path, "r") as file:
                    content = file.read().strip()
                    if content.startswith("detection_method="):
                        method = int(content.split("=")[1])
                        return method if method in [0, 1] else 0
            return 0  # 默认使用原始方法
        except Exception as e:
            print(f"加载检测方法配置失败: {e}")
            return 0