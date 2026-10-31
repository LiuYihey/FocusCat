# 小猫奔跑精灵图生成工具

专注页「小猫奔跑进度」动画的素材生成脚本。
用 **three.js** 离线渲染 3D 橘猫的奔跑循环，打包成横向精灵图。

> 构建期一次性生成，**App 运行时不含 WebView，也不依赖 three.js**。

## 生成产物

`app/src/main/res/drawable-nodpi/cat_run_sheet.png`
横向 12 帧，每帧 175×182 px（已统一裁剪到并集包围盒，播放时猫身不抖动）。

## 用法

```powershell
# 1. 安装 three.js（渲染页面通过 importmap 引用 node_modules）
npm install three@0.160.0

# 2. 渲染 12 帧 PNG（需要 Playwright 的 chromium）
pip install playwright
python render.py

# 3. 打包成精灵图
python pack.py
```

## 关键点

- **正面朝向镜头**：侧身剪影读不出"可爱"，双眼中高光 + 腮红 + 奶白肚皮才有 Q 版感
- **腿要探出身体轮廓**：`HIP` 里腿的 z 值推到 ±0.34（身体 z 向半径约 0.41），
  否则腿被躯干挡住，小尺寸下看起来像"站着"而不是"在跑"
- **对角腿同相**：步态用 `phase` 偏移 0 / 0.5，形成小跑的节奏
- **统一裁剪**：所有帧用「并集包围盒」裁同一个矩形，否则逐帧裁剪框不同会抖动
- **相机自动取景**：`fitCamera()` 按模型包围盒拟合正交相机，保证全身在画面内
- **WebGL 参数**：headless Chromium 需要 `--use-angle=swiftshader --enable-unsafe-swiftshader`，
  否则会 CONTEXT_LOST 渲染出空白帧
