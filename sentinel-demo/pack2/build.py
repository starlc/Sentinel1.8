import os
import sys

def main():
    # 打包命令参数说明
    # -F: 创建单个可执行文件
    # -w: 不显示控制台窗口
    # -n: 指定输出文件名
    # -i: 指定图标文件
    # --add-data: 添加资源文件，格式为 源路径;目标路径
    
    # 检查图标文件是否存在
    icon_file = "2313.ico"
    #icon_file = "sjt.ico"
    if not os.path.exists(icon_file):
        print(f"错误: 图标文件 {icon_file} 不存在")
        return 1
    
    # 检查主程序文件是否存在
    main_file = "main.py"
    if not os.path.exists(main_file):
        print(f"错误: 主程序文件 {main_file} 不存在")
        return 1
    
    # 定义需要打包的资源目录
    resource_dirs = [
        'resource/25601440',  # 枪械图像目录
        'resource/23131440',  # 枪械图像目录
        'resource/dict'      # 枪械配置文件目录
    ]
    
    # 构建资源文件参数
    resource_params = ""
    for res_dir in resource_dirs:
        # 检查目录是否存在
        if os.path.exists(res_dir):
            # 在Windows上，PyInstaller使用分号分隔源路径和目标路径
            resource_params += f" --add-data={res_dir};{res_dir}"
    
    # 构建打包命令
    cmd = f"pyinstaller -F -w -n auto -i {icon_file} {resource_params} {main_file}"
    #cmd = f"pyinstaller -F  -n auto -i {icon_file} {resource_params} {main_file}"
    
    print("开始打包程序...")
    print(f"执行命令: {cmd}")
    
    # 执行打包命令
    result = os.system(cmd)
    
    if result == 0:
        print("打包成功! 可执行文件位于 dist 目录中")
        print("\n资源文件已经被打包到可执行文件中，不再需要单独复制resource目录")
        print("直接运行exe文件即可，无需额外的依赖文件")
    else:
        print(f"打包失败，错误代码: {result}")
    
    return result

if __name__ == "__main__":
    sys.exit(main())