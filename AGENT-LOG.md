# 接手必读（写给下一个改代码的 AI / 人）

> 这份文档的唯一目的：**让接手的人（或模型）在 10 分钟内知道现状、为什么这么做、哪些坑已经踩过。**
> 最后更新：2026-10-24，版本 v1.0.0

---

## 0. 一句话

Android 原生（无 AndroidX）的悬浮桌宠：一只蓝发鲸鱼娘，浮在手机桌面上，能播 105 段动作、对充电/耳机/通知/久坐做反应、长按弹出悬浮动作面板。

仓库：https://github.com/luoyushanghua/whale-pet
发布：https://github.com/luoyushanghua/whale-pet/releases

---

## 1. 环境（照抄即可，别自己找路径）

| 项 | 值 |
|---|---|
| JDK | `C:\Program Files\Zulu\zulu-21` |
| Android SDK | `D:\AndroidDev\toolchain\sdk` |
| Gradle | `D:\AndroidDev\toolchain\gradle-8.9` |
| Python（跑测试脚本） | `D:\ComfyUI\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\python\python.exe` |
| 环境变量脚本 | `. D:\AndroidDev\env.ps1`（设置 JAVA_HOME / ANDROID_HOME / GRADLE_HOME / adb） |
| 设备 | vivo V2408A，Android 16（SDK 36），1440×3168，序列号 `10AF2N1S4H004ZN` |
| 模拟器（备用） | MuMu，`127.0.0.1:16416`，720×1280 |
| 工作区 | `D:\WhalePet`（app / scripts / tools / docs / releases） |
| 工程真身 | `D:\AndroidDev\projects\whale-pet` |

构建：`gradle assembleDebug`（AGP 8.7.3，minSdk 26，targetSdk 35）

---

## 2. 代码地图

```
app/src/main/java/com/whale/pet/
├── MainActivity          主页：状态、召唤按钮、免责声明
├── PetService            核心。前台服务 + 悬浮窗 + 生命周期 + 所有反应逻辑
├── PetOverlayView        GLSurfaceView 子类；触摸（点/拖/长按/双击）
├── PetRenderer           GL 渲染 + MediaPlayer 播放 + 动画链调度
├── PetPanel              悬浮动作面板（WindowManager 直接加 View，不是 Activity）
├── ActionPanelActivity   旧的全屏面板（已不再调用，留着备查）
├── ActionMap             英文动作 id → 中文素材文件名（含 .mp4）
├── PetNotificationListener 通知使用权：把通知转成短句
├── NotificationActionReceiver / BootReceiver  通知按钮 / 开机自启
├── Prefs / Memory / Mind / Brain / Archive    偏好、长期记忆、台词、API、存档
├── ChatActivity / ChatSettingsActivity        聊天界面
├── Poster / WidgetProvider / Achv / WhaleBadge / WhaleScene / Search / Ui
└── CreditsActivity       鸣谢页
```

### 关键常量（改之前先看这里）

| 常量 | 位置 | 值 / 含义 |
|---|---|---|
| `VIDEO_W / VIDEO_H` | PetRenderer | 960 / 720（单侧画面比例，不是整条视频） |
| 窗口尺寸 | PetService.showPet | `min(screenW, screenH) * sizePct/100`，高度 `w * 720/960` |
| `sizePct` 默认 | Prefs | 55 |
| 贴底 y | PetService.resetPosition | `screenH - h - dp(10)` |
| 动画链权重 | PetRenderer | idle 10 / turn 5 / move 5 / 其余 80 |
| 哼气窗口 | PetService.onTap | 5 秒内连戳 5 次，冷却 60 秒 |
| 使用时长 | PetService.trackUsage | 同一 App 30 分钟，冷却 30 分钟 |

---

## 3. 渲染管线（最容易改坏的地方）

**视频格式**：单条 1920×720 的 H.264。
- 左半边 960×720 = 彩色
- 右半边 960×720 = 灰度 alpha 蒙版

**为什么要这么存**：Android 的硬件解码器不暴露 VP9 的 alpha 通道，只能把 alpha 自己塞进第二条流再拼起来。整条视频只解一次，性能最好。

**着色器输出必须是预乘 alpha**：
```glsl
gl_FragColor = vec4(c * a, a);
```
配合 `glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`。写错会变成黑底或灰影。

**letterbox**：`onSurfaceChanged` 里按 `VIDEO_W/VIDEO_H` 算视口，任何窗口比例都不拉伸。

**渲染模式**：`RENDERMODE_CONTINUOUSLY`。之前是 `WHEN_DIRTY`，只在视频来帧时画（24fps），导致压扁回弹动画一顿一顿。

**播放**：`MediaPlayer` + `SurfaceTexture`，`setVolume(0,0)`。

### 素材转码（重新生成时必须用这套）

```bash
ffmpeg -y -c:v libvpx-vp9 -alpha_mode 0 -i src.webm \
       -c:v libvpx-vp9 -i src.webm \
       -filter_complex "[0:v]crop=480:360:80:0,scale=960:720:flags=lanczos,format=rgb24[c];\
[1:v]crop=480:360:80:0,alphaextract,scale=960:720:flags=lanczos,format=gray[m];\
[c][m]hstack=inputs=2,format=yuv420p[o]" \
       -map "[o]" -c:v libx264 -crf 20 -preset veryfast -r 24 -an out.mp4
```

**必须双解码**：`-alpha_mode 0` 那次拿彩色，普通那次拿 alpha。
只用一次解码 → alpha 全是 255 → **黑底灰影**（这个坑踩过）。

源素材：`D:\AndroidDev\work\dsh-pet-src\dsh-pet\assets\webm\*.webm`（106 个，640×360 VP9 带 alpha）
坏素材备份：`D:\AndroidDev\work\actions_bad_backup\`

---

## 4. 窗口与交互

**窗口参数**：`TYPE_APPLICATION_OVERLAY` + `TRANSLUCENT` +
`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS`

**长按** → `PetPanel` 悬浮面板（WindowManager 加 View，**不启动 Activity**）。
面板 90%×48%（竖）/ 46%×80%（横），带分类横滑 + 两列网格 + 摸头/投喂/关掉 + 帧率切换。

**点击** → `renderer.bounce()` + 随机点击动作。bounce 是压扁回弹，用户喜欢。

**拖动** → `onDragBy` 移动窗口；松手速度 < 260 就原地待机浮动，不吸附边缘。

**待机浮动** → Choreographer 每帧 `lp.y = baseY + sin(t/477)*dp(5)`；`moving/flinging/dragging/隐藏` 时让位。

### ⚠️ 不要再加全屏触摸层
用户明确要求：**任何全屏 overlay 都会挡键盘**。
`enableTouchLayer()` 现在直接 return，只保留「点她本人」的交互。

---

## 5. 反应系统

### 环境广播（PetService.screenReceiver）
| 广播 | 反应 | 计数器 |
|---|---|---|
| POWER_CONNECTED | 伸懒腰 +「插上电啦…」 | react_power |
| POWER_DISCONNECTED | 吓一跳 +「拔啦？…」 | react_unplug |
| HEADSET_PLUG | 戴/摘耳机台词 | react_headset_in/out |
| AIRPLANE_MODE_CHANGED | 「要飞走啦？」 | — |
| BATTERY_LOW / OKAY | 低电提醒 | — |

**注册必须带 `Context.RECEIVER_NOT_EXPORTED`（API 33+）**，否则 Android 13+ 直接 `SecurityException` 崩服务 —— 这就是当初「召唤不出来」的根因。

### 使用情况（tickWatch 每 4 秒）
`trackUsage(pkg)`：同一 App 停留 ≥30 分钟 → 按分类说一句（game/social/video/shop/read/other）。

### 情绪
| 情绪 | 触发 | 状态 |
|---|---|---|
| 哼气 | 5 秒内连戳 5 次 | ✅ 已验证（interactions delta=4） |
| 开心 | 心情≥80 + 摸头 | ⚠️ **当前不可达**（长按改成开面板了），在 `onLongPressPetLegacy()` 里挂着 |
| 无聊 / 被冷落 | 5 分钟 / 30 分钟无互动 | ⚠️ 代码在 `upkeepLoneliness()`，未验证到气泡 |

### 通知播报
`PetNotificationListener` 把通知转短句：
- 含「@你/有人@我」→「X @你啦」
- 群消息 →「群里有人找你：X」
- 私聊 →「X 找你啦：正文前 18 字」

配提示音：`assets/sfx/chime_mention.mp3`（三连音，@你）/ `chime_notify.mp3`（两连音）。
**用户明确不要 TTS**（嫌机械），所以只有提示音 + 气泡文字。

### 已废弃
`enableTouchLayer()`（点哪跑哪）→ 全屏层会挡键盘，已停用。

---

## 6. 已修 bug 的根因（别重复踩）

| 现象 | 根因 | 修法 |
|---|---|---|
| 召唤没反应 | `registerReceiver` 缺 `RECEIVER_NOT_EXPORTED`，服务 onCreate 崩 | 加 flag，API33+ 判断 |
| 黑底灰影方块 | 素材 alpha 全 255（ffmpeg 默认丢 VP9 alpha） | 106 段全部双解码重转 |
| 进 App 她消失、回桌面不回来 | `PetApp` 用 `startService` 发 UI_CLOSE，Android 8+ 后台禁止 | 改 `sendBroadcast` |
| 窗口变 1×1 回不来 | 服务在跑（隐藏态）时点召唤只弹 Toast，恢复入口不可达 | running 时 `restartServiceIfRunning()` |
| 面板选动作没反应 | 面板关闭触发的 UI_CLOSE 让她重播 idle，把选中动作顶掉 | `postDelayed(playOnce(act), 400)` |
| 包体删了素材却不减小 | `mergeDebugAssets` 增量缓存 | 必须 `clean` 再 build |
| 点她抽搐 | 掉帧（24fps）+ 压扁动画 | 改 `RENDERMODE_CONTINUOUSLY` |
| 挡键盘 | 全屏触摸层 | 彻底移除（用户要求） |
| 横屏她巨大 | 尺寸按 `screenW` 算，横屏宽度翻倍 | 改用 `min(screenW, screenH)` |

---

## 7. 验证方法（不看截图也能判断）

**核心思路：别靠中文 OCR，adb 里中文是乱码。**

```powershell
$py = "D:\ComfyUI\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\python\python.exe"
& $py D:\WhalePet\tools\summon_measure.py    # 窗口 396x297 / y 浮动
& $py D:\WhalePet\tools\chain_test.py        # 连拍 6 帧，changed 4/5 以上
& $py D:\WhalePet\tools\stability.py         # hidden 0 of 20
```

**判断"有没有触发"用 prefs 计数器**：

```powershell
adb shell run-as com.whale.pet cat shared_prefs/pet.xml | findstr react_power
```

已有的计数器：`react_power` / `react_unplug` / `react_headset_in/out` / `usage_warn` / `interactions`。

**看播放状态**：代码里 `Log.d("whalepet", "play " + action + " file=" + file)`，用
`adb logcat -d -v brief | findstr whalepet` 看。

---

## 8. 已知问题 / 待办

1. **抛掷（fling）约 40fps，略顿**
   根因：每帧都要 `wm.updateViewLayout` 移动悬浮窗，单次 ~20ms。
   尝试过 Choreographer 驱动（略好）、fling 时停连续重绘（无效）。
   **方案没做完**：窗口加 padding + 顶点着色器 `uShift` 做亚像素偏移，窗口几帧挪一次。
   改到一半因为用 PowerShell 拼 Java 字符串把文件改坏，已回滚（备份在 `D:\AndroidDev\work\backup_render_173800`）。

2. **「开心」情绪不可达**：长按改成开面板后，原逻辑挂在 `onLongPressPetLegacy()`，没接线。

3. **无聊/被冷落未验证**：四种验证手段都没抓到气泡窗口，怀疑 `showBubble` 在该调用路径静默失败。

4. **充电/耳机反应未真机验证**：`dumpsys battery set ac 1` 不产生广播；`ACTION_HEADSET_PLUG` 没有 adb 伪造入口。

5. **通知权限默认拒绝**：用户需手动开（设置 → 应用 → 鲸鱼娘 → 通知）。

6. **横屏未真机验证**：vivo 拒绝 `adb shell wm user-rotation`，代码写了自适应但没实测截图。

---

## 9. 作业纪律（血泪）

1. **改 Java 用 `edit`/`write` 工具，别用 PowerShell 做多行字符串替换**
   - 单引号里的 `` `n `` 是字面量，写进 Java 会编译报"非法字符"
   - 出过 3 次事故，最严重一次把 `Prefs.java` 写成 0 字节
2. **containing 中文的脚本一律 UTF-8 保存**，别用默认 ASCII
3. **`build` 成功 ≠ 手机上是新代码**：改完必须 `install -r`
4. **临时调过阈值的，必须改回并 clean 重装**
5. **删资源后必须 `clean`**，否则包体不会小
6. **别信单次 `dumpsys`**：窗口有启动瞬态，要「等 10 秒后连续采样 20 次」
7. **别在素材上做"提升清晰度"的幻想**：源素材就是 640×360，放大不会更清晰
8. **白色描边去不掉**：那是画进像素里的，不是 alpha 问题

---

## 10. 发版流程

```powershell
cd D:\WhalePet
.\scripts\build.ps1      # 编译
.\scripts\deploy.ps1     # 编译 + 装手机
.\scripts\publish.ps1    # 出包 + 更新桌面「鲸鱼娘-最新.apk」
```

GitHub Release 用 API（凭据在 Windows 凭据管理器里，`git credential fill` 能取到 token）：

```powershell
# 建 release + 传 APK（注意 curl 要加 --ssl-no-revoke，否则证书吊销检查失败）
curl.exe --ssl-no-revoke -X POST `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/vnd.android.package-archive" `
  --data-binary "@C:\Users\feather\Desktop\鲸鱼娘-最新.apk" `
  "https://uploads.github.com/repos/luoyushanghua/whale-pet/releases/<ID>/assets?name=whale-pet-x.y.z.apk"
```

⚠️ 素材文件名不要用中文传给 upload API，会被截断成 `-1.0.0.apk`。

---

## 11. 版权（必须保留）

- 代码：MIT
- 动作素材：PC2005-cloud/dsh-pet，**允许开源使用，禁止商用**
- 形象「溟月」：上善无形，**CC BY-NC-SA 4.0**
- 个人学习作品，**不商用**

详见仓库 `NOTICE`。
