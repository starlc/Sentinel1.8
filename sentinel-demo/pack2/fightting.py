import os
import threading
import time
import json
import pynput
import ctypes
import cv2 as cv
import numpy as np
import pyttsx3 as pytts
import tkinter as tk

from mss import mss
from queue import Queue
from pynput.mouse import Button
from pynput import keyboard
from PIL import Image
from datetime import datetime

q = Queue()

current_gun = {1: "", 2: ""}  # 当前的武器名

player_posture = 1  # 姿势 1为站 2为蹲 3为趴(暂时不用) 默认1
player_gun = 0
player_bullets = 1
gun_list = []

gun_img_dict = dict()

gun_dict = {}

global_seq = 1
## 定义枪械的裁剪
gun_list_name = []

job = None
#武器锁定 0 初始化 1锁定
gun_lock = 0

# 枪械是否满配 false 满配  true 裸配
player_gun_config = True
# 创建和运行显示字符的应用程序
def is_numlock_on():
    # 使用 ctypes 调用 Windows API 获取键盘状态
    hllDll = ctypes.WinDLL("User32.dll")
    VK_NUMLOCK = 0x90
    return hllDll.GetKeyState(VK_NUMLOCK) & 1

# 获取 Caps Lock 状态
def is_caps_lock_on():
    return ctypes.WinDLL("User32.dll").GetKeyState(0x14) & 1
class Job(threading.Thread):

    def __init__(self, *args, **kwargs):
        super(Job, self).__init__(*args, **kwargs)
        self.__flag = threading.Event()     # 用于暂停线程的标识
        # self.__flag.set()       # 设置为True
        self.__running = threading.Event()      # 用于停止线程的标识
        self.__running.set()      # 将running设置为True

    def run(self):
        while self.__running.isSet():
            self.__flag.wait()      # 为True时立即返回, 为False时阻塞直到内部的标识位为True后返回
            posture()  # 姿势判断
           #bullets()  # 子弹数量判断

    def pause(self):
        self.__flag.clear()     # 设置为False, 让线程阻塞

    def resume(self):
        self.__flag.set()    # 设置为True, 让线程停止阻塞

    def stop(self):
        self.__flag.set()       # 将线程从暂停状态恢复, 如何已经暂停的话
        self.__running.clear()        # 设置为False


class Action(object):
    # action_type 定义: True 为切换武器  False 为切换姿势
    def __init__(self, action_type, param):
        self.action_type = action_type
        self.param = param

    def get_type(self):
        return self.action_type

    def get_param(self):
        return self.param

# 播放声音

class CharacterDisplayApp:
    def __init__(self, root, initial_character, x, y):
        self.root = root
        self.root.title("Character Display")
        
        # 去掉窗口边框
        self.root.overrideredirect(True)

        # 设置窗口透明度为70%
        self.root.attributes("-alpha", 0.5)
        
        # 设置窗口置顶
        self.root.attributes("-topmost", True)
        
        # # 创建标签并设置背景为浅灰色，字体颜色为深蓝色
        # self.label = tk.Label(root, text=initial_character, font=("Microsoft YaHei", 14), fg="#1e3a8a", bg="#d1d5db")
        # self.label.pack()
         # 创建标签并设置背景为浅灰色，字体颜色为深蓝色
        self.label = tk.Label(root, text=initial_character, font=("Microsoft YaHei", 12), fg="#1e3a8a", bg="#d1d5db", anchor="w", justify="left")
        self.label.pack(fill="both", expand=True)

        # 设置窗口位置
        self.root.geometry(f"+{x}+{y}")

    def update_character(self, new_character):
        self.label.config(text=new_character)

def create_display_app(initial_character, x, y):
    root = tk.Tk()
    app = CharacterDisplayApp(root, initial_character, x, y)
    return app

def run_display_app(app):
    app.root.mainloop()

def updateDisplay():
    gun_status = "锁" if gun_lock == 1 else "解"
    gun_name = get_gun_name(int(player_gun))
    posture_status = "站" if player_posture == 1 else "蹲"
    full_status = "满" if player_gun_config  else "裸"
    new_character = f"{gun_status}|{full_status}|{posture_status}|{gun_name}"
    app.update_character(new_character)

def play_sound(content):
    engine = pytts.Engine()
    engine.setProperty('rate', 220)  # 语速
    engine.setProperty('volume', 0.35)  # 音量
    engine.say(content)
    engine.runAndWait()
    engine.stop()
 
def getSequence():
    global global_seq
    now = datetime.now()
    strDate = now.strftime('%Y%m%d%H%M%S')
    srtSeq = f"{global_seq:04d}"
    global_seq = global_seq+1
    return f"{strDate}{srtSeq}"


# 枪名取对应的lua配置名


def get_gun_config_name(gun_name):
    # return gun_dict.get(gun_name) or gun_name
    return gun_name

def get_gun_name(gun):
    return gun_list_name[gun-1] or gun
# 枪名取对应的
# 保存数据


def save_config(title, content):
    file = "D:\\pubg\\"+title+".lua"
    field = title
    if title == "gun":
        field = "weaponNo"
    with open(file, "w+") as file:
        print(field+" = "+content)
        file.write(field+"="+content)

# 对比图片特征点


def image_similarity_opencv(img1, img2):
    image1 = img1
    image2 = cv.cvtColor(img2, cv.IMREAD_GRAYSCALE)

    orb = cv.ORB_create()
    kp1, des1 = orb.detectAndCompute(image1, None)
    kp2, des2 = orb.detectAndCompute(image2, None)
    bf = cv.BFMatcher(cv.NORM_HAMMING, crossCheck=True)
    if des1 is None or des2 is None:
        return 0
    matches = bf.match(des1, des2)
    matches = sorted(matches, key=lambda x: x.distance)
    goodMatches = 0
    for m in matches:
        if m.distance <= 60:
            goodMatches = goodMatches+1
    #good_matches = sum(1 for m in matches if m.distance <= 60)
    return goodMatches


#相似度检测
def similarity(im, gun_pos):
    similarity = {}
    global player_gun
    for gun_name in gun_list:
        result = image_similarity_opencv(gun_img_dict[gun_name],im)
        similarity[gun_name] = result
        if result >= 40:
            # 如果当前持有枪械和检测到的枪械不同 切换武器
            savePlayGunAndSound(gun_name,gun_pos)
            print("切换武器:" + get_gun_name(int(gun_name))+"  当前武器栏:" + str(gun_pos) +
                  "  当前姿势:" + ("站" if player_posture == 1 else "蹲")+" 识别度："+str(result))
            return True
        
    m = max(similarity.items(), key=lambda x: x[1])
    print("本轮相似度最大值为武器:",get_gun_name(int(m[0])),"相似度：",m[1])
    if m[1] >= 10:
        savePlayGunAndSound(str(m[0]),gun_pos)
        print("最大相似度切换武器:" + get_gun_name(int(m[0]))+"  当前武器栏:" + str(gun_pos) +
                "  当前姿势:" + ("站" if player_posture == 1 else "蹲"))
        return True
    return False

# 保存武器到配置文件并且播报
# gunName 武器编号 1~N
# gunPos 当前持有武器位置
def savePlayGunAndSound(gunName,gunPos):
    global player_gun
    if player_gun != gunName and gunName != '' :
        player_gun = gunName
        current_gun[gunPos] = gunName  # 避免重复操作
        save_config("gun", str(gunName))
        updateDisplay()
        #play_sound(get_gun_name(int(gunName)))

def getRGB(box):
    img = screenshot(box)
    saveTempPic(img,'resource/posturetemp/',False)
    r, g, b = img.pixel(3, 3)
    print("r:",r,"g:",g,"b:",b)
    if r > 190 and g > 190 and b > 190:
        return True
    return False

# 判断姿势 
def posture():
    global player_posture
    saveFlag = False
    left = 962
    top = 1308
    width = 5
    height = 5
    #检测点1
    time.sleep(0.05)
    box = (left, top, left + width, top + height)
    result1 = getRGB(box)

    left2 = 960
    top2 = 1315
    #检测点2
    box2 = (left2, top2, left2 + width, top2 + height)
    result2 = getRGB(box2)
    
    if  result1 and  result2:
        if player_posture == 99:
            player_posture =1
            saveFlag = True
    else:
        if player_posture == 1:
            player_posture =99
            saveFlag = True
    if saveFlag:
        save_config("posture", str(player_posture))
        updateDisplay()
    time.sleep(1)
#锁定武器栏
def lockWeaponBar():
    global gun_lock
    gun_lock = 1 if gun_lock== 0 else 0
    updateDisplay()

#变更武器满配、裸配
def changeWeaponConfigState():
    global player_gun_config
    player_gun_config = not player_gun_config
    updateDisplay()
# 变更武器
def responeKeyboard(pressKey):
    # 锁定武器栏
    if pressKey == 4 :
        lockWeaponBar()
        return True
    
    # 临时关闭宏
    elif pressKey == 0:
        closeWeapon(pressKey)
        return True
    
    # 检测NumLock状态
    elif pressKey == keyboard.Key.num_lock:
        changeWeaponConfigState()
        return True
    else:
        #对应1,2切换武器或者识别
        if gun_lock == 0:
            screen(pressKey)
        else:
            savePlayGunAndSound(current_gun[pressKey],pressKey)

#关闭宏 只能在手持某武器之后按
def closeWeapon(pressKey):
    save_config("gun", "0")
    play_sound("close")

# 截屏
def screen(gun_pos):
    left = 1940
    top = 1325
    width = 195
    height = 100
    if gun_pos == 2:
        top = top - 80
        pass
    box = (left, top, left + width, top + height)
    n = 0
    time.sleep(0.35)
    while True:
        img = screenshot(box)
        arr = np.array(img.pixels, dtype=np.uint8)
        #arr = extract_gun(arr)
        saveTempPic(img,'resource/temp2313/',False)
        if similarity(arr, gun_pos):  # 如果返回True 退出循环
            break
        n = n + 1
        if n >= 2:  # 如果5次还没有识别出来 退出循环1
            print("检测失败")
            break
        time.sleep(1)

def extract_gun(image):
    """提取图片中的枪(预处理图片)"""
    image[image <= 200] = 0 
    return image

def saveTempPic(img,path,isSave):
    if isSave != True:
        return True
    img = Image.frombytes("RGB", img.size, img.bgra, "raw", "BGRX")
    savepath = os.path.abspath(path+getSequence())
    img.save(savepath+'.png', format='PNG')

# 消费者


def consumer():
    while True:
        action = q.get()
        if action.get_type():
            responeKeyboard(action.get_param())
        else:
            # 鼠标右键按下时 检测是蹲还是站
            posture(action.get_param())
        q.task_done()


# 监听键盘输入
def on_release(key):
    try:
        if key == keyboard.Key.f1: #按下F1 将武器设值为0
            q.queue.clear()  # 每次按下 1 或者 2  清空掉之前的队列
            action = Action(True, 0)
            q.put(action)
            return True
        
        if key == keyboard.Key.num_lock: #按下numlock 变更numLock状态
            q.queue.clear()  # 每次按下 
            action = Action(True, key)
            q.put(action)
            return True
        
        if str(key.char) == '`': #按下~ 锁定武器栏
            print(key.char)
            q.queue.clear()  # 
            action = Action(True, 4)
            q.put(action)
            return True
        
        key = int(key.char)
        if key == 1 or key == 2 or key == 4 :
            q.queue.clear()  # 每次按下 1 或者 2  清空掉之前的队列
            action = Action(True, key)
            q.put(action)
            return True
    finally:
        return True

# 监听键盘输入


def keyboard_listener():
    with pynput.keyboard.Listener(
            on_release=on_release) as listener:
        listener.join()


# 监听鼠标按键
def on_click(x, y, button, pressed):
    global mouse_down_time,executed
    if Button.x2 == button:
        if pressed:
            # q.queue.clear()  # 每次按下 1 或者 2  清空掉之前的队列
            # action = Action(False, 0.5)
            # q.put(action)
            job.resume()  # 开始检测
        else:
            job.pause()  # 停止检测


# 监听鼠标按键
def mouse_listener():
    with pynput.mouse.Listener(
            on_click=on_click) as listener:
        listener.join()


# 初始化配置
def initialize(img_dir):
    for files in os.listdir(img_dir):
        if os.path.splitext(files)[1] == '.png':
            gun_list.append(os.path.splitext(files)[0])
            gun_file = os.path.join(img_dir, files)
            gun_img_dict[os.path.splitext(files)[0]]= cv.imread(gun_file,cv.IMREAD_GRAYSCALE)
    global gun_dict
    # 枪械名字对应的配置 lua存在的不在这里写 没有枪械配置的先套用其他枪的配置
    with open(r'resource//dict//gun_dict.json', 'r') as f:
        gun_dict = json.load(f)
    
    global gun_list_name
    with open(r'resource//dict//gun_arr.json', 'r') as f:
        gun_list_name = json.load(f)
    


def screenshot(box):
    with mss() as sct:
        shot = sct.grab(box)
        return shot


# 程序入口
def main():
    global job
    os.system("title Main")
    os.system("mode con cols=50 lines=30")

    initialize("resource//25601440")

    job = Job()
    global player_gun_config
    player_gun_config = not is_numlock_on()
    threads = [threading.Thread(target=consumer), threading.Thread(
        target=keyboard_listener), threading.Thread(target=mouse_listener), job]
    for t in threads:
        t.start()

   

if __name__ == '__main__':
    main()# 创建和运行显示字符的应用程序
app = create_display_app("启动", 1560, 1370)
 # 开始 Tkinter 主循环
run_display_app(app)
