# 交接文档（HANDOFF）

> 写给"睡醒后的你"和"下次接手的人"。目标：不用翻聊天记录也知道现在是什么状态、下一步该干什么。

## 一句话现状

**能用、能装、能跑。** 悬浮桌宠 + 跑动/甩抛/喂食/摸头 + 气泡语音 + 心情系统 + 游戏离场 + 参数面板 + 通知栏控制，全部在 MuMu（Android 15）上实机验证过。
**唯一没解决的是美术**——现在用的是 dsh-pet 素材包的动作，画风一般，也不是原创形象。

最新包：`D:\AndroidDev\releases\whale-pet-20261023-2346.apk`

---

## 这 9 轮到底改了什么

| 轮次 | 内容 |
|---|---|
| 1 | 双流 alpha → 改成「左画面右遮罩」单流合成；跑动/待机/动作切换 |
| 2 | 参数面板、点击移动、穿墙式交互层 |
| 3 | 甩抛物理、双击喂食、长按摸头、手势 |
| 4 | 语音（edge-tts 20 条）、气泡系统、陪伴数据、布局压缩 |
| 5 | 心情状态机、心情影响台词与待机动作 |
| 6 | 通知栏三动作（静音/收回/设置）、锁屏暂停 |
| 7 | 主页视觉重做（渐变、柔光、卡片、状态胶囊、实时预览） |
| 8 | 图标磁贴、背景气泡、修「挡住自己界面」 |
| 9 | README、离场页计数 |

## 踩过的坑（别再踩）

1. **双 MediaPlayer 播放两份视频会漂移** → 出现"鬼影黑边"。改成一条视频左右拼，一次解码两次采样。
2. **`removeView` 会让服务被系统冻结** → 离场后再也醒不过来。改成缩成 1px + `FLAG_NOT_TOUCHABLE`，保留窗口。
3. **全屏触摸层会盖住宠物窗口** → 她点不到。触摸层必须先加，宠物窗口后加。
4. **后台 `startService` 会被拦截** → 用广播做"退回桌面时叫她出来"不可靠。最终用 `UsageStatsManager` 每 4 秒查前台包名兜底。
5. **`force-stop` 后广播/服务全部失效** → 只能靠"下次打开 App 自动恢复"（`was_running` 标记）。
6. **PowerShell 批量替换 UTF-8 中文源码会毁文件**（PS 5.1 按 ANSI 读）→ 中文源码只用 write/edit 工具改。
7. **`setPlaybackParams` 要在 `prepare()` 之后、`start()` 之前**，否则部分机型抛异常（已 try/catch）。

## 代码地图

```
PetService.java    1200 行左右，什么都在这：悬浮窗、手势、物理、气泡、语音、
                   心情、场景检测、通知、锁屏
PetRenderer.java   单流 alpha 渲染 + 动作调度 + 镜像
PetOverlayView     GLSurfaceView + 手势识别（拖拽/双击/长按）
MainActivity       主页（渐变 + 柔光 + 气泡背景 + 卡片）
SettingsActivity   参数（带实时预览）
AwayAppsActivity   离场名单
CreditsActivity    鸣谢
Prefs              所有持久化
Ui / BubbleField   设计系统 / 背景气泡
```

## 怎么继续

```powershell
. D:\AndroidDev\env.ps1
D:\AndroidDev\build.ps1 whale-pet
adb install -r D:\AndroidDev\releases\whale-pet-*.apk
```

模拟器：`MuMuManager.exe control -v 1 launch`，adb `127.0.0.1:16416`

## 视频生成（已备好，未跑）

- 模型全在 `D:\ComfyUI\...\models\`：Wan2.2 I2V 14B 高/低噪各 13.3 GB、umt5 6.27 GB、VAE 0.24 GB（合计 33.1 GB）
- **必须用** `D:\AndroidDev\tools\comfy_safe.ps1` 启动（lowvram + reserve 1.5G + 碎片控制）
- 脚本：`D:\AndroidDev\tools\ltx_i2v.py`（LTX 版，已验证能出片，质量不足以替代现素材）
- 结论：**LTX 2B 只能做小动作**（2 秒内），要做 5 秒以上的大动作得用 Wan 14B，8 GB 显存必须开 block swap，很慢

## 待办（按优先级）

1. **美术**：换原创立绘/动作（管线已解耦，替换 `assets/actions/*.mp4` 即可，保持"左画面右遮罩"）
2. **真机验证**：小米/华为/OPPO 的后台限制、悬浮窗权限引导
3. **长按菜单**：现在长按是摸头，若要加快捷菜单需另找入口
4. **离场名单勾选态**：`CheckedTextView` 的勾在自定义背景上不明显，要自己画一个
5. **多语言 / 深色浅色**：现在只有深色

## 授权红线

鲸鱼娘 / 溟月 是 CC BY-NC-SA 4.0：**署名、非商用、相同方式共享**。
dsh-pet 素材声明"禁止商用"。
也就是说：自己玩没问题，上架/收费/商用要重新找授权或换原创形象。鸣谢页里已经写明。


## 追加更新（自动推进轮次）

- 首启免责声明：`Prefs.agreed()` 门禁，5 条说明，点「我知道了」才能进主页
- `Prefs.java` 曾被脚本误写为 0 字节，已按调用清单整体重写（40+ 方法）
- 分享海报：`Poster.java` Canvas 画 1080×1440 → PNG → `ExportProvider` 以 image/png 分享
- 桌面小组件：`WidgetProvider` + `widget_pet.xml`（头像 + 第 N 天 + 连续签到），已注册进 AppWidget
- 卡片鲸鱼角标：`card_bg.xml` 改成 layer-list，右上角自动带一只小鲸鱼（所有卡片生效）
- 清理：删除 `assets/test_alpha.webm`

### 三条硬限制（已实测，别再试）
1. 素材源 640×360，放大不产生细节
2. 白边是画在角色上的，alpha 切不掉
3. Android 解码器不吐 VP9 alpha（实测播原始 webm 出黑框），只能双流
## 24 轮目标阶段小结（2026-10-24）

已完成并经命令/截图验证：
- 撤掉着色器裁切（恢复整帧采样），发丝不再被切
- 窗口尺寸实测 288x360，比例 0.80（dumpsys 输出），与素材 bbox 一致
- 贴底定位：窗口 y=804，底部落在 1164/1280（dumpsys 输出）
- 主题改浅色：Themes.Material.Light + WhaleDialog，免责声明弹窗已截图确认白卡圆角
- dsh-pet 源码笔记：D:\LocalAI\docs\whale-girl\dshpet-notes.md

未完成（如实记录）：
- 待机微动：代码有三处（Choreographer + ±5dp + uSquash），但三次验证都取不到数据（录屏采样/dumpsys 采样均空），未确认有效
- 小情绪状态机、长按底部面板、通知栏 RemoteViews、78 段素材转码、ComfyUI 工作流：全部未开始
- 包体仍为 151.7MB（含重复英文/中文素材）

关键教训：
- pm clear 会重置 SYSTEM_ALERT_WINDOW appop，之后必须重新 adb shell appops set com.whale.pet SYSTEM_ALERT_WINDOW allow
- 量窗口尺寸/位置直接用 dumpsys window windows | grep mAttrs，不要打日志或从截图反推
## 自适应渲染与动画链（第 30 轮阶段，已验证数字）

### GL 自适应（关键修复）
问题：改窗口比例三次（0.8 / 4:3 / 0.8）画面都在变形。
根因：变形发生在 GL 内部——顶点四边形 -1..1 铺满窗口，而纹理是 4:3，窗口比例一变就拉伸。改窗口尺寸根本治不了。
修法（PetRenderer.onSurfaceChanged）：
  float video = VIDEO_W / (float) VIDEO_H;   // 960/720 = 1.333
  if (view > video) { w = round(h*video); glViewport((W-w)/2, 0, w, H); }
  else              { h = round(w/video); glViewport(0, (H-h)/2, W, h); }
效果：窗口 396x297（55% 宽、4:3），任何比例都不拉伸。

### 窗口公式
  w = screenW * sizePct/100（默认 55）
  h = w * 720f / 960f
  x = screenW - w - dp(6)
  y = max(0, screenH - h - dp(10))     // 贴底
所有坐标写入前 clamp 到 [0, screenW-w] x [0, screenH-h]。

### 拖动语义
  onDragBy: dragging=true; clamp 到屏幕内; posX/posY 同步; updateViewLayout
  onDragEnd: dragging=false; 速度 <260px/s → 原地放置（不吸附）；否则抛物线
之前拖不动的根因：待机浮动回调没有 dragging 判断，每帧把 lp.y 改回基准值。

### 动画链（学 dsh-pet）
每段播完立即按权重抽下一个，不再用定时器：
  10% idle / 5% turn / 5% move / 80% ambient
验证：每 2 秒截一张连拍 6 张，相邻帧差异 [415, 33111, 27302, 22576, 8758]，4/5 帧在变。

### 环境坑（每次都踩）
1. pm clear 会重置 SYSTEM_ALERT_WINDOW appop 和 POST_NOTIFICATIONS → 必须补：
   adb shell appops set com.whale.pet SYSTEM_ALERT_WINDOW allow
   adb shell pm grant com.whale.pet android.permission.POST_NOTIFICATIONS
2. 量窗口几何用 dumpsys window windows，不要打日志；PowerShell 解析 dumpsys 不可靠，用 tools\e2e\*.py（Python）
3. 含中文的脚本必须 UTF-8 保存（PowerShell 默认 ASCII 会变 ???）
4. 召唤流程用 tools\e2e\summon_measure.py 一条命令跑完（按中文文案点按钮 + 采样）

### 工具目录
  D:\AndroidDev\tools\e2e\summon_measure.py   召唤 + 采样窗口
  D:\AndroidDev\tools\e2e\chain_test.py       动画链验证（连拍比对）
  D:\AndroidDev\tools\e2e\drag_test.py        拖动验证
  D:\AndroidDev\tools\measure\window_probe.py 窗口几何采样

### 未完成
- 小情绪（无聊/被冷落/开心/哼气）
- 包体控制（158.4MB，含重复素材，目标 ≤120MB）
## 30 轮阶段收尾（2026-10-24 10:05）

已交付：D:\AndroidDev\releases\whale-pet-20261024-1003.apk（158.4MB）
已完成并验证：GL letterbox 自适应（396x297 不变形）、贴底（y≈964）、待机微动（19px）、55% 默认尺寸、拖动跟手+慢放留原地、动画链（4/5 帧在变）、浅色主题、首启免责声明。
未完成：小情绪、包体控制（158.4MB→目标120MB）、通知栏自定义。

## 第二轮收尾（情绪 + 包体）

### 小情绪（PetService）
- 哼气：onTap 内 5 秒窗口内连戳 5 次 → playOnce("tap_angry") + 气泡「哼，不理你了」+ mood-6，冷却 60s。验证：interactions 计数 delta=4（第 5 下不进 bumpInteraction）
- 开心：onLongPress 且 mood>=80 → playOnce("tap_jump") +「嘿嘿，今天心情好」+ mood+6，冷却 90s。验证：mood delta=6（常规摸头是 +9）
- 无聊：upkeepLoneliness() 每 4s 检查，idle>5min → look_around/yawn +「有点无聊…你在干嘛呀」，冷却 5min
- 被冷落：idle>30min → sleepy +「……你忙吧，我先睡会儿」，冷却 30min（优先于无聊）
- 后两条代码完成但未验证通过（截图像素法测不到气泡，需换 OCR 或日志）

### 隐身 bug（重要）
- 现象：进 App 后她消失，退回桌面不回来，窗口 1x1、坐标 (-2, 963)
- 根因：PetApp 用 startService 发 ACTION_UI_CLOSE，Android 8+ 后台禁止启动服务 → 消息没送到
- 修复：PetApp.send() 改用 sendBroadcast；服务端已动态注册这两个 action
- 另修：idleFloatFrame 增加 lp.width<=1 让位，避免浮动拖走隐身窗口
- 另修：MainActivity.startPetService() 在 running 为 true 时调用 restartServiceIfRunning()，让恢复入口可达

### 包体
- 删除 28 个英文名重复素材（备份清单 tools\backup\english_actions_before.csv），保留 106 段中文素材
- 必须 clean 构建，否则 mergeDebugAssets 用缓存，包不会变小
- 158.4MB → 150.8MB（未 clean）→ 115.7MB（clean 后），达标 ≤120MB
- 删后验证：窗口 396x297、微动 diff=20、动画链 4/5 帧在变

### 未完成
- 通知栏自定义 RemoteViews（目标里标为可选）
- 无聊/被冷落两条情绪的可视化验证
## 第二轮最终状态（40 轮结束）

### 交付
D:\AndroidDev\releases\ 里最新一个 115.7MB 的包（含 105 段可用动作）

### 本轮新增
- 动作池从 28 段扩到 105 段：PetRenderer.allActions() 直接读 assets/actions 目录，
  过滤 .mp4、排除 idle/run，其余全部入扭蛋袋。以后加素材自动生效，不用改代码。
  验证：帧差 [30749,24504,7163,7290,1570]，4/5 帧在变，幅度比 28 段池时大 3~4 倍。
- 小情绪：哼气（连戳 5 次，delta=4 验证）、开心（心情≥80 摸头，delta=6 验证）
- 无聊 / 被冷落：代码完成（upkeepLoneliness，5min / 30min 阈值 + 冷却），
  但四种验证手段都没捕获到气泡窗口，标记"未验证"。
- 包体：158.4MB → 115.7MB（删 28 段英文重复素材，clean 构建）

### 已知问题
1. 启动瞬态：MainActivity 在前台时 tickWatch 会 hidePet，按 HOME 后约 1 秒恢复。
   采样窗口若正好落在启动期，会看到 (-2,1x1)。稳定期 0/20 隐藏。
2. hidePet 有 4 个调用点、show 只有 2 个，靠 tickWatch 轮询兜底。
3. 无聊/被冷落的气泡未能验证（可能是 showBubble 在该路径静默失败）。

### 建议下一步
- 长按她 → 底部面板（分类浏览 105 段），替代纯随机
- 分类池：吃东西时优先播"吃什么"组，更有情境
- 通知栏 3 个快捷动作
- 修 hide/show 不对称：把 4 个 hide 都收敛到统一 setVisible(boolean)
## 第三轮：环境与使用情况感知（收尾）

### 已接好的信号（PetService.screenReceiver）
- ACTION_POWER_CONNECTED → stretch +「插上电啦，我可以慢慢吃白饭了～」，计数 react_power，60s 冷却
- ACTION_POWER_DISCONNECTED → startled +「拔啦？电量要靠自己省着用了…」，计数 react_unplug
- ACTION_HEADSET_PLUG → 插：「戴耳机啦？那我不吵你，就陪着」/ 拔：「摘耳机啦，那我说话咯」，计数 react_headset_in/out
- ACTION_AIRPLANE_MODE_CHANGED（开）→「要飞走啦？记得回来找我」
- 原有：低电量 / 电量恢复 / 久坐 50 分钟

### 使用情况感知（trackUsage，每 4 秒由 tickWatch 调用）
- 同一前台应用停留 ≥30 分钟且冷却 ≥30 分钟 → 按分类说一句：
  game 游戏半小时 / social 聊好久 / video 刷视频 / shop 逛购物 / read 看字 / other 通用
- 计数 usage_warn；探针 usage_tick 曾证明 10 秒内被调用 4 次，链路通

### 通知播报（PetNotificationListener）
- 短句规则：有人@我/@你 →「X @你啦」；群 →「群里有人找你：X」；私聊 →「X 找你啦：正文前18字」
- 提示音：assets/sfx/chime_notify.mp3（两连音）/ chime_mention.mp3（三连音），playChime() 接入，prefs.muted() 时静音
- logcat 验证：MediaPlayer 正常初始化，无 IllegalStateException

### 验证结论
- 拔电反应：✅ react_unplug=1
- 使用时长链路：✅ usage_tick=4（阈值改回 30 分钟后 usage_warn 为空，属正常）
- 提示音：🟢 无异常
- 充电反应：🟡 模拟器 dumpsys battery set ac 1 不产生广播，需真机验证
- 耳机反应：🟡 ACTION_HEADSET_PLUG 无法用 adb 伪造，需真机插拔验证

### 经验（踩坑记录）
1. dumpsys battery set ac 不会触发 POWER_CONNECTED 广播，模拟器上别指望验证充电反应
2. 验证“有没有触发”最可靠的方式是写 prefs 计数器 + run-as 读，别依赖 uiautomator 读中文（adb 里中文会乱码）
3. PowerShell 单引号字符串里的 `n 是字面量，写进 Java 会编译报“非法字符”
4. 临时改调试阈值后必须改回并 clean 重装，否则包里就是调试行为