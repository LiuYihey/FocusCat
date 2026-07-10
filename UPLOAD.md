# FocusCat 开源上传指南

本文档提供将 FocusCat 上传到 GitHub 的完整命令。请在本机终端中**逐步执行**。

> 仓库地址：https://github.com/LiuYihey/FocusCat  
> SSH 远程：`git@github.com:LiuYihey/FocusCat.git`

---

## 上传前检查清单

确认以下敏感文件**不会**被提交（已在 `.gitignore` 中排除）：

| 文件 | 说明 |
|------|------|
| `focuscat-release.jks` | 签名密钥库 |
| `gradle.properties` | 含签名密码 |
| `local.properties` | 本地 SDK 路径 |
| `old/` | 归档的日志与开发迭代文件 |
| `app/build/` | 构建产物 |
| `.gradle/` | Gradle 缓存 |

验证命令（在 `FocusCat` 目录下执行）：

```powershell
cd d:\___Desktop___\MiniMax\FocusCat

# 确认敏感文件存在但被忽略
git check-ignore -v focuscat-release.jks gradle.properties local.properties old/
```

---

## 第一步：初始化 Git 并推送代码

```powershell
cd d:\___Desktop___\MiniMax\FocusCat

# 初始化仓库
git init

# 添加远程（SSH）
git remote add origin git@github.com:LiuYihey/FocusCat.git

# 查看将要提交的文件（确认无敏感文件）
git status

# 添加所有文件
git add .

# 再次确认暂存区不含敏感文件
git status
git diff --cached --name-only | Select-String -Pattern "jks|gradle\.properties$|local\.properties"

# 首次提交
git commit -m "Initial open source release: FocusCat v1.0.0"

# 推送到 GitHub（主分支）
git branch -M main
git push -u origin main
```

如果 `git diff --cached` 中出现 `jks`、`gradle.properties` 或 `local.properties`，请**不要推送**，先检查 `.gitignore`。

---

## 第二步：创建 GitHub Release 并上传 APK

APK 路径：

```
d:\___Desktop___\MiniMax\FocusCat\app\build\outputs\apk\release\app-release.apk
```

### 方式 A：使用 gh CLI（推荐）

先确认 gh 已登录：

```powershell
gh auth login
gh auth status
```

创建 Release 并上传 APK：

```powershell
cd d:\___Desktop___\MiniMax\FocusCat

gh release create v1.0.0 `
  "app\build\outputs\apk\release\app-release.apk" `
  --repo LiuYihey/FocusCat `
  --title "FocusCat v1.0.0" `
  --notes "## FocusCat v1.0.0 正式发布

- 应用拦截 + 反思问答
- 猫咪养成与好感度系统
- 专注锁机模式
- 数据统计

### 安装说明
1. 下载 app-release.apk 到手机
2. 允许安装未知来源应用
3. 按引导开启无障碍、使用情况、悬浮窗等权限"
```

### 方式 B：GitHub 网页手动上传

1. 打开 https://github.com/LiuYihey/FocusCat/releases/new
2. Tag：`v1.0.0`
3. Release title：`FocusCat v1.0.0`
4. 上传 `app-release.apk`
5. 点击 **Publish release**

---

## 本地签名配置（后续自行构建 Release 时）

```powershell
# 复制模板
cp gradle.properties.example gradle.properties

# 编辑 gradle.properties，填入签名信息
# 或将以下属性写入 ~/.gradle/gradle.properties：
# FOCUSCAT_STORE_FILE=../focuscat-release.jks
# FOCUSCAT_STORE_PASSWORD=<你的密码>
# FOCUSCAT_KEY_ALIAS=focuscat
# FOCUSCAT_KEY_PASSWORD=<你的密码>

# 构建 Release APK
.\gradlew.bat assembleRelease
```

---

## 已归档到 old/ 的文件（不上传）

以下文件已移至 `old/` 目录并被 `.gitignore` 忽略：

- `old/logs/` — JVM 崩溃日志（hs_err_pid*.log、replay_pid*.log）
- `old/loops/` — 开发迭代记录（21 个子目录）
- `old/.uploads/` — 本地上传缓存
- `old/FOCUSCAT_UI_OPTIMIZATION.md` — UI 优化设计笔记
- `old/app_icon_generated.png` — 生成的图标草稿
- `old/图库视频素材/` — 设计素材文件夹

如需恢复，从 `old/` 目录取回即可，不影响应用源码。
