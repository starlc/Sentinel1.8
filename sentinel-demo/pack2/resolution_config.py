import json
import os

class ResolutionConfig:
    """分辨率配置类，用于管理不同分辨率下的屏幕坐标和参数"""
    
    def __init__(self, resolution_name=None):
        # 默认配置
        self.configs = {
            "2560x1440": {
                "resource_dir": "resource//25601440",
                "ui_position": {"x": 1560, "y": 1370},
                "weapon_detection": {
                    "left": 1940,
                    "top": 1325,
                    "width": 195,
                    "height": 100,
                    "offset_for_second": 80
                },
                "posture_detection": {
                    "point1": {"left": 962, "top": 1308, "width": 5, "height": 5},
                    "point2": {"left": 960, "top": 1315, "width": 5, "height": 5}
                },
                "confidence_thresholds": {
                    "high": 45,
                    "low": 15
                }
            },
            "2313x1440": {
                "resource_dir": "resource//23131440",
                "ui_position": {"x": 1440, "y": 1370},
                "weapon_detection": {
                    "left": 1680,
                    "top": 1325,
                    "width": 210,
                    "height": 100,
                    "offset_for_second": 80
                },
                "posture_detection": {
                    "point1": {"left": 842, "top": 1308, "width": 5, "height": 5},
                    "point2": {"left": 832, "top": 1330, "width": 5, "height": 5}
                },
                "confidence_thresholds": {
                    "high": 40,
                    "low": 10
                }
            }
        }
        
        # 当前配置
        self.current_config = None
        
        # 如果指定了分辨率名称，则加载对应配置
        if resolution_name and resolution_name in self.configs:
            self.current_config = self.configs[resolution_name]
        else:
            # 尝试自动检测分辨率
            self._auto_detect_resolution()
    
    def _auto_detect_resolution(self):
        """自动检测分辨率并加载对应配置"""
        try:
            import ctypes
            user32 = ctypes.windll.user32
            screen_width = user32.GetSystemMetrics(0)
            screen_height = user32.GetSystemMetrics(1)
            resolution = f"{screen_width}x{screen_height}"
            
            # 检查是否有完全匹配的分辨率配置
            if resolution in self.configs:
                self.current_config = self.configs[resolution]
                print(f"已自动加载分辨率配置: {resolution}")
                return
            
            # 如果没有完全匹配的，检查宽度匹配的
            for config_name in self.configs:
                config_width = int(config_name.split('x')[0])
                if config_width == screen_width:
                    self.current_config = self.configs[config_name]
                    print(f"已加载相似分辨率配置: {config_name}")
                    return
            
            # 如果都没有匹配的，使用默认配置
            default_resolution = next(iter(self.configs))
            self.current_config = self.configs[default_resolution]
            print(f"未找到匹配的分辨率配置，使用默认配置: {default_resolution}")
        except Exception as e:
            print(f"自动检测分辨率失败: {e}，使用默认配置")
            default_resolution = next(iter(self.configs))
            self.current_config = self.configs[default_resolution]
    
    def get_resource_dir(self):
        """获取资源目录"""
        return self.current_config.get("resource_dir", "resource//25601440")
    
    def get_ui_position(self):
        """获取UI位置"""
        return self.current_config.get("ui_position", {"x": 1560, "y": 1370})
    
    def get_weapon_detection_params(self):
        """获取武器检测参数"""
        return self.current_config.get("weapon_detection", {
            "left": 1940,
            "top": 1325,
            "width": 195,
            "height": 100,
            "offset_for_second": 80
        })
    
    def get_posture_detection_params(self):
        """获取姿势检测参数"""
        return self.current_config.get("posture_detection", {
            "point1": {"left": 962, "top": 1308, "width": 5, "height": 5},
            "point2": {"left": 960, "top": 1315, "width": 5, "height": 5}
        })
    
    def get_confidence_thresholds(self):
        """获取置信度阈值"""
        return self.current_config.get("confidence_thresholds", {
            "high": 35,
            "low": 10
        })
    
    def save_custom_config(self, resolution_name, config):
        """保存自定义配置"""
        self.configs[resolution_name] = config
        self.current_config = config
        
        # 保存到文件
        try:
            config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "resource", "config", "resolution_config.json")
            os.makedirs(os.path.dirname(config_path), exist_ok=True)
            
            with open(config_path, 'w', encoding='utf-8') as f:
                json.dump(self.configs, f, indent=4, ensure_ascii=False)
            
            print(f"已保存自定义配置: {resolution_name}")
            return True
        except Exception as e:
            print(f"保存自定义配置失败: {e}")
            return False
    
    def load_configs_from_file(self):
        """从文件加载配置"""
        try:
            config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "resource", "config", "resolution_config.json")
            if os.path.exists(config_path):
                with open(config_path, 'r', encoding='utf-8') as f:
                    loaded_configs = json.load(f)
                    self.configs.update(loaded_configs)
                print("已从文件加载分辨率配置")
                return True
        except Exception as e:
            print(f"从文件加载配置失败: {e}")
        return False