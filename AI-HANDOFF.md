# 给下一个 AI 的交接（2026-10-25）

> 你要做的是：把东西上传到用户的 GitHub 仓库。
> 先读完这份，再动手。**别猜路径、别重建工程。**

---

## 1. 一句话现状

Android 悬浮桌宠「鲸鱼娘」，代码在 `D:\AndroidDev\projects\whale-pet`，
本地仓库已关联 GitHub，**推送凭据已经存在 Windows 凭据管理器里**，能直接 push。

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/luoyushanghua/whale-pet （Public） |
| 当前版本 | `versionName = 1.0.1`，`versionCode = 2` |
| 最新提交 | `834ce4c`（在 main 上，已推送） |
| Release | `v1.0.0`、`v1.0.1`，各带一个 175 MB 的 APK |
| 桌面安装包 | `C:\Users\feather\Desktop\鲸鱼娘-最新.apk` |
| 工作区 | `D:\WhalePet`（app / scripts / tools / docs / releases） |

---

## 2. 怎么认证（关键）

**不需要向用户要 token**，本机已有凭据：

```powershell
$cred = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$token = ($cred | Select-String "^password=" | ForEach-Object { $_.Line -replace "^password=","" })
```

拿到 `$token` 后可调 GitHub API：

```powershell
$h = @{ Authorization="Bearer $token"; "User-Agent"="ai-agent"; Accept="application/vnd.github+json" }
```

⚠️ **不要打印 token**，用完别写进文件。

### 中文编码坑（踩过）

发 JSON 时中文会变 `???`，必须转字节：

```powershell
$bytes = [Text.Encoding]::UTF8.GetBytes($body)
Invoke-RestMethod -Uri $url -Method Post -Headers $h -Body $bytes `
  -ContentType "application/json; charset=utf-8"
```

### 上传大文件（APK）的坑

`curl.exe` 必须加 `--ssl-no-revoke`，否则报 `CRYPT_E_NO_REVOCATION_CHECK`：

```powershell
curl.exe -sS --ssl-no-revoke -X POST `
  -H "Authorization: Bearer $token" -H "User-Agent: ai-agent" `
  -H "Content-Type: application/vnd.android.package-archive" `
  --data-binary "@C:\path\to.apk" `
  "https://uploads.github.com/repos/luoyushanghua/whale-pet/releases/<RELEASE_ID>/assets?name=whale-pet-1.0.2.apk"
```

素材文件名别用中文，会被截断成 `-1.0.2.apk`。

---

## 3. 上传东西的三种方式

### A. 普通文件（代码/文档）→ git push

```powershell
cd D:\AndroidDev\projects\whale-pet
git add .
git commit -m "说明这次改了什么"
git push origin main
```

**动手前先看 `.gitignore`**——`app/src/main/assets/actions/` 被排除，
那是 106 段动作素材（171 MB），**版权归 dsh-pet，不能进仓库**。

### B. 图片/素材 → 可以放 `docs/` 或 Releases

仓库里可以放**图标、截图、封面**（体积小、自己有版权）。
动作素材不行。

### C. 新版本 APK → 走发版脚本（别手动上传）

```powershell
cd D:\WhalePet
.\scripts\release.ps1              # 升 patch，自动：编译→提交→推送→建 Release→上传 APK
.\scripts\release.ps1 -Minor       # 升 minor
.\scripts\release.ps1 -NoUpload    # 只编译+更新桌面，不发 GitHub
.\scripts\release.ps1 -Notes "修了什么"
```

**每次发版必须同时升 `versionCode` 和 `versionName`**，否则 App 里的「检查更新」不生效，
部分机型还会拒绝覆盖安装。

---

## 4. 用户的数据不会丢

| 操作 | 设置/聊天记忆 |
|---|---|
| `install -r` 覆盖安装 | 保留 |
| 应用商店更新 | 保留 |
| 卸载再装 | 清空 |

所以升级时**不要让用户卸载**。

---

## 5. 现在的已知问题（别以为都修好了）

| 问题 | 状态 |
|---|---|
| 离场名单勾选状态不对 | 已修（`CHOICE_MODE_MULTIPLE` 双写冲突） |
| 横屏面板方向错乱 | 已修（旋转时收起面板），**没真机验证** |
| 召唤后应居中 | 已修，MuMu 上验证过 |
| **连续操作后动作卡住** | 已加固（失败重试/去抖/暂停不释放），**用户仍在测，可能没完全修好** |
| 抛掷约 40fps 略顿 | 未修（需 GPU 偏移方案，改到一半回滚了） |
| 充电/耳机反应 | 代码在，模拟器不可验 |
| 视频比例 | 与源素材一致（0.788→0.790），不要"调瘦"，那是画风 |

---

## 6. 环境（照抄，别自己找）

```powershell
. D:\AndroidDev\env.ps1       # 设好 JAVA_HOME / ANDROID_HOME / GRADLE_HOME
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
```

| 项 | 路径 |
|---|---|
| JDK 21 | `C:\Program Files\Zulu\zulu-21` |
| SDK | `D:\AndroidDev\toolchain\sdk` |
| Gradle | `D:\AndroidDev\toolchain\gradle-8.9` |
| 编译 | `gradle assembleDebug`（在工程根目录） |
| 手机 | vivo V2408A / Android 16 / `10AF2N1S4H004ZN` |
| 模拟器 | MuMu，`127.0.0.1:16416`（不稳，常掉线，用 `adb connect` 重连） |

---

## 7. 作业纪律（血泪，务必遵守）

1. **改 Java 用 edit/write 工具，不要用 PowerShell 做多行字符串替换**
   - 单引号里的 `` `n `` 是字面量，写进 Java 会编译报"非法字符"
   - 已因此把文件改坏过 3 次
2. 含中文的脚本一律 **UTF-8 保存**
3. `build` 成功 ≠ 手机上跑的是新代码，**必须 `install -r`**
4. 临时改的调试阈值必须改回并 clean 重装
5. 删资源后必须 `clean`，否则包体不会变小
6. 别只 `dumpsys` 一次就下结论，窗口有启动瞬态
7. 验证优先用：
   - `adb shell dumpsys window windows | findstr mAttrs`（看窗口）
   - `adb shell run-as com.whale.pet cat shared_prefs/pet.xml`（看状态）
   - `adb logcat -d -v brief | findstr whalepet`（看播放日志）
   - **`uiautomator dump` 会偶发卡死，少用**

---

## 8. 版权（写文档、发 Release 时必须带上）

```
形象原型：溟月（上善无形，CC BY-NC-SA 4.0）
动作素材：PC2005-cloud/dsh-pet（允许开源使用，禁止商用）
本项目：个人学习作品，不商用；代码 MIT
```

---

## 9. 你想问用户什么

只问必要的：

- "要发新版吗？"（是的话跑 `release.ps1`）
- "上传的是哪类东西？代码 / 图片 / 新 APK？"
- 需要真机验证时："手机插上了吗？"

别问"工程在哪""用哪个 SDK"——本文档里都有。
