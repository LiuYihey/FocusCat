# FocusCat（专注猫）APP 设计文档与实施方案

> 版本：v2.0（FocusGuard → FocusCat 演进）
> 日期：2026-06-23
> 目标：从「选择题拦截」演进为「目标反思 + 猫咪养成」的可用级落地应用，支持 APK 分发与手机实装

---

## 一、产品愿景与设计理念

### 1.1 一句话定位
FocusCat 是一款**目标强化型专注工具**：在用户打开娱乐/分心应用前，通过「无标准答案的反思问答」唤醒用户当下意图，并用「养成一只因你进步而开心的猫」作为正向反馈，把「克制分心」从痛苦约束变成温柔陪伴。

### 1.2 核心设计理念
| 理念 | 落地方式 |
|------|----------|
| **Goal Reinforcement（目标强化）** | 进入分心应用前先回答「你进来做什么」，防止进入后迷失 |
| **No Right Answer（无对错）** | 反思问答为开放式文本输入，不评判对错，只唤醒觉察 |
| **Positive Reward（正向奖励）** | 选择退出即获得随机猫粮食物，把「克制」变成「收获」 |
| **Companion Growth（陪伴成长）** | 投喂猫咪 → 好感度升级 → 成就解锁，长期养成正反馈 |
| **Simple & Calm（简洁克制）** | 仿照现有专注软件的极简风格，深色沉浸式拦截页 |

### 1.3 与现有 FocusGuard 的差异
| 维度 | FocusGuard（现状） | FocusCat（目标） |
|------|---------------------|------------------|
| 问答类型 | 选择题，有对错 | 开放式文本，无对错 |
| 问答内容 | 常识/数学题 | 目标反思 3 问（可自定义） |
| 通过后行为 | 直接进入目标应用 | 进入「确认/退出获食物」二次抉择页 |
| 退出激励 | 无 | 随机食物奖励（猫粮/鸡胸肉/冻干/酸奶） |
| 养成系统 | 无 | 选猫 → 投喂 → 动画 → 好感度 → 成就 |
| 主界面 | 数据统计为主 | 猫咪形象 + 食物库存 + 数据统计 |
| 品牌名 | 专注守护 | 专注猫 FocusCat |

---

## 二、用户流程

### 2.1 首次启动流程
```
启动 App → 权限引导（无障碍/使用情况/悬浮窗）
         → 猫咪选择页（选择品种：橘猫/英短/布偶/黑猫/暹罗...）
         → 命名猫咪
         → 进入主界面（猫咪展示 + 引导添加约束应用）
```

### 2.2 拦截反思流程（核心）
```
用户点击被约束应用（如抖音）
  ↓ AccessibilityService 检测
  ↓ 启动 ReflectionActivity（沉浸式深色页）
  ↓ 显示 3 个反思问题（文本输入框）：
      Q1. 你进入这个 app 是来做什么的？
      Q2. 你今天的 todo list 还有哪些？
      Q3. 你这段时间的长期目标是什么？
  ↓ 用户填写完成 → 点击「进入应用」
  ↓ 实际跳转到 ConfirmActivity（二次抉择页）
      ├─ 「确认进入 [抖音]」→ 启动目标应用
      └─ 「退出，收获一份食物」→ 关闭页面 + 随机发放食物 + 食物获得动画
```

### 2.3 养成投喂流程
```
主界面 → 看到自己的猫（3D 卡通形象）
       → 点击「投喂」→ 选择食物（猫粮/鸡胸肉/冻干/酸奶）
       → 猫咪进食动画（2s）
       → 猫咪开心动画（1.5s）
       → 好感度 +N，进度条增长
       → 达成 10/50/100 次投喂 → 解锁阶段成就（猫咪看到你每天进步很开心）
```

### 2.4 问题自定义流程
```
设置 → 反思问题管理 → 查看 3 个默认问题
                   → 编辑问题文本
                   → 启用/禁用某个问题
                   → 重置为默认
```

---

## 三、功能规格

### 3.1 功能模块清单
| 模块 | 功能点 | 优先级 |
|------|--------|--------|
| **猫咪选择** | 首次启动选品种、命名 | P0 |
| **猫咪展示** | 主界面猫咪形象、好感度进度条 | P0 |
| **投喂系统** | 食物库存、投喂动画、好感度增长 | P0 |
| **成就系统** | 10/50/100 投喂里程碑、阶段称号 | P0 |
| **反思问答** | 3 问开放式输入、沉浸式拦截 | P0 |
| **确认/食物页** | 二次抉择、随机食物奖励 | P0 |
| **问题自定义** | 编辑/启用/禁用/重置问题 | P1 |
| **约束应用管理** | 选择应用加入约束名单 | P0（已有） |
| **数据统计** | 拦截次数、克制次数、投喂次数 | P1 |
| **设置** | 问题管理、数据导出、关于 | P1 |
| **APK 分发** | 签名构建、版本管理 | P0 |

### 3.2 猫咪品种定义
| 品种 ID | 名称 | 描述 | 形象资源 |
|---------|------|------|----------|
| `orange` | 橘猫 | 憨厚贪吃，亲和力满分 | Lottie/SVG |
| `british` | 英短 | 圆脸沉稳，安静陪伴 | Lottie/SVG |
| `ragdoll` | 布偶 | 蓝眼温柔，粘人治愈 | Lottie/SVG |
| `black` | 黑猫 | 神秘优雅，独立灵动 | Lottie/SVG |
| `siamese` | 暹罗 | 聪明话痨，活力十足 | Lottie/SVG |

> 注：3D 卡通形象资源采用 Lottie 动画 JSON 或 SVG 矢量图，避免引入重型 3D 引擎，保证 APK 体积可控。

### 3.3 食物类型定义
| 食物 ID | 名称 | 稀有度 | 好感度加成 | 获取概率 |
|---------|------|--------|-----------|---------|
| `cat_food` | 猫粮 | 普通 | +1 | 50% |
| `chicken` | 鸡胸肉 | 普通 | +2 | 25% |
| `freeze_dried` | 冻干 | 稀有 | +3 | 15% |
| `yogurt` | 酸奶 | 稀有 | +3 | 8% |
| `canned` | 罐头 | 史诗 | +5 | 2% |

### 3.4 好感度阶段成就
| 投喂次数 | 阶段称号 | 解锁内容 |
|---------|---------|---------|
| 10 | 初识之友 | 猫咪开心动画升级 |
| 50 | 默契伙伴 | 猫咪新姿势/装饰 |
| 100 | 灵魂伴侣 | 专属称号 + 猫咪特效 |

---

## 四、技术架构

### 4.1 技术栈（沿用现有）
- **语言**：Kotlin
- **UI**：Jetpack Compose + Material3
- **DI**：Hilt
- **DB**：Room
- **异步**：Coroutines + Flow
- **导航**：Navigation Compose
- **最低 SDK**：26（Android 8.0）
- **目标 SDK**：34（Android 14）

### 4.2 架构分层
```
┌─────────────────────────────────────────┐
│  UI Layer (Compose)                     │
│  ├─ ReflectionActivity（反思拦截页）     │
│  ├─ ConfirmActivity（确认/食物页）       │
│  ├─ HomeScreen（猫咪展示）              │
│  ├─ CatScreen（投喂/养成）              │
│  ├─ AppsScreen（约束应用管理）          │
│  ├─ SettingsScreen（设置/问题管理）     │
│  └─ StatsScreen（统计）                 │
├─────────────────────────────────────────┤
│  ViewModel Layer                        │
│  ├─ ReflectionViewModel                 │
│  ├─ ConfirmViewModel                    │
│  ├─ CatViewModel                        │
│  └─ ...                                 │
├─────────────────────────────────────────┤
│  Domain/Repository Layer                │
│  ├─ CatRepository                       │
│  ├─ FoodRepository                      │
│  ├─ ReflectionRepository                │
│  ├─ AppRepository（已有）               │
│  └─ StatsRepository（已有）             │
├─────────────────────────────────────────┤
│  Data Layer (Room)                      │
│  ├─ cat_catalog / user_cat              │
│  ├─ food_inventory / food_catalog       │
│  ├─ reflection_questions / answers      │
│  ├─ blocked_apps（已有）                │
│  ├─ quiz_history（保留兼容）            │
│  └─ focus_sessions（已有）              │
├─────────────────────────────────────────┤
│  Service Layer                          │
│  ├─ AppMonitorService（无障碍，已有）   │
│  └─ FocusGuardService（前台保活，已有） │
└─────────────────────────────────────────┘
```

### 4.3 关键设计决策
1. **包名保持 `com.focusguard.app`**：避免大规模重命名风险，applicationId 不影响用户感知品牌
2. **用户品牌为「专注猫 FocusCat」**：通过 strings.xml 与 UI 文案统一呈现
3. **数据库版本升级 v1→v2**：新增表，保留旧表兼容，使用 Migration 而非破坏性重建
4. **猫咪形象用 Lottie/SVG**：3D 引擎体积过大，Lottie 动画即可实现「3D 卡通感」且体积小
5. **反思问答无对错**：移除 correctIndex 判定逻辑，改为文本输入 + 必填校验

---

## 五、数据模型设计

### 5.1 新增实体

#### 5.1.1 猫咪品种目录 `cat_catalog`
```kotlin
@Entity(tableName = "cat_catalog")
data class CatCatalogEntity(
    @PrimaryKey val breedId: String,      // "orange", "british"...
    val displayName: String,              // "橘猫"
    val description: String,              // 品种描述
    val iconAsset: String,                // 图标资源名
    val idleAnimAsset: String,            // 待机动画资源
    val eatAnimAsset: String,             // 进食动画资源
    val happyAnimAsset: String            // 开心动画资源
)
```

#### 5.1.2 用户猫咪 `user_cat`
```kotlin
@Entity(tableName = "user_cat")
data class UserCatEntity(
    @PrimaryKey val id: Int = 1,          // 单例，固定 id=1
    val breedId: String,                  // 关联 cat_catalog
    val name: String,                     // 用户命名的猫名
    val affinityLevel: Int = 0,           // 好感度数值
    val totalFeedCount: Int = 0,          // 累计投喂次数
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 5.1.3 食物目录 `food_catalog`
```kotlin
@Entity(tableName = "food_catalog")
data class FoodCatalogEntity(
    @PrimaryKey val foodId: String,       // "cat_food", "chicken"...
    val displayName: String,              // "猫粮"
    val rarity: String,                   // "common", "rare", "epic"
    val affinityBonus: Int,               // 好感度加成
    val dropRate: Double,                 // 获取概率 0.0-1.0
    val iconAsset: String                 // 图标资源
)
```

#### 5.1.4 食物库存 `food_inventory`
```kotlin
@Entity(tableName = "food_inventory")
data class FoodInventoryEntity(
    @PrimaryKey val foodId: String,       // 关联 food_catalog
    val count: Int = 0,                   // 拥有数量
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### 5.1.5 反思问题 `reflection_questions`
```kotlin
@Entity(tableName = "reflection_questions")
data class ReflectionQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionText: String,             // 问题文本
    val order: Int,                       // 显示顺序 1/2/3
    val isActive: Boolean = true,         // 是否启用
    val isCustom: Boolean = false,        // 是否用户自定义
    val placeholder: String = ""          // 输入框提示
)
```

#### 5.1.6 反思回答记录 `reflection_answers`
```kotlin
@Entity(tableName = "reflection_answers")
data class ReflectionAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,                 // 关联 reflection_questions
    val answerText: String,               // 用户回答
    val targetApp: String?,               // 触发应用包名
    val answeredAt: Long = System.currentTimeMillis()
)
```

#### 5.1.7 好感度成就 `affinity_achievements`
```kotlin
@Entity(tableName = "affinity_achievements")
data class AffinityAchievementEntity(
    @PrimaryKey val milestone: Int,       // 10, 50, 100
    val title: String,                    // "初识之友"
    val description: String,
    val unlockedAt: Long? = null          // 解锁时间，null=未解锁
)
```

### 5.2 数据库迁移 v1 → v2
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建新表
        database.execSQL("""CREATE TABLE IF NOT EXISTS cat_catalog (...)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS user_cat (...)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS food_catalog (...)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS food_inventory (...)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS reflection_questions (...)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS reflection_answers (...)""")
        database.execSQL("""CREATE TABLE IF NOT EXISTS affinity_achievements (...)""")
        // 插入种子数据
        // ...
    }
}
```

---

## 六、UI/UX 设计

### 6.1 配色方案（温暖治愈风）
| 色彩 | 用途 | 色值 |
|------|------|------|
| 主色 | 猫咪橙 | `#FF9F43` |
| 辅色 | 奶油白 | `#FFF8E7` |
| 背景 | 暖深色 | `#2C2A37` |
| 卡片 | 暖灰 | `#3D3A4A` |
| 成功 | 抹茶绿 | `#16A34A` |
| 强调 | 莓果粉 | `#FF6B9D` |

### 6.2 主界面布局
```
┌─────────────────────────────┐
│  专注猫  [🐱 猫名]           │  顶部栏
├─────────────────────────────┤
│                             │
│      [猫咪 Lottie 动画]      │  猫咪展示区
│                             │
│   好感度: Lv.3  ████░ 45/50  │  好感度进度
│   投喂次数: 45  称号:默契伙伴  │
│                             │
├─────────────────────────────┤
│  食物库存                    │
│  [猫粮x12] [鸡胸x5] [冻干x2] │  食物网格
├─────────────────────────────┤
│  [投喂猫咪]                  │  主操作按钮
├─────────────────────────────┤
│  今日数据                    │
│  拦截 3 次 | 克制 2 次        │
├─────────────────────────────┤
│ [首页][应用][统计][设置]      │  底部导航
└─────────────────────────────┘
```

### 6.3 反思拦截页
```
┌─────────────────────────────┐  沉浸式深色
│                             │
│      🐱 专注时刻             │
│   想清楚再进入，也是一种温柔  │
│                             │
│  ┌─────────────────────────┐│
│  │ Q1. 你进入这个 app       ││
│  │     是来做什么的？       ││
│  │ ┌─────────────────────┐ ││
│  │ │ 输入你的回答...      │ ││  文本输入
│  │ └─────────────────────┘ ││
│  └─────────────────────────┘│
│                             │
│  ┌─────────────────────────┐│
│  │ Q2. 你今天的 todo list  ││
│  │     还有哪些？           ││
│  │ ┌─────────────────────┐ ││
│  │ │                     │ ││
│  │ └─────────────────────┘ ││
│  └─────────────────────────┘│
│                             │
│  ┌─────────────────────────┐│
│  │ Q3. 你这段时间的长期    ││
│  │     目标是什么？         ││
│  │ ┌─────────────────────┐ ││
│  │ │                     │ ││
│  │ └─────────────────────┘ ││
│  └─────────────────────────┘│
│                             │
│  [    进入应用    ]          │  主按钮
│                             │
│  触发应用：抖音              │
└─────────────────────────────┘
```

### 6.4 确认/食物奖励页
```
┌─────────────────────────────┐
│                             │
│        🐾                   │
│                             │
│   确认进入「抖音」吗？        │
│                             │
│   退出可获得一份随机食物      │
│                             │
│  ┌──────────┐ ┌──────────┐  │
│  │  确认进入 │ │ 退出获食物│  │
│  └──────────┘ └──────────┘  │
│                             │
└─────────────────────────────┘

退出后弹出食物获得动画：
┌─────────────────────────────┐
│                             │
│      ✨ 获得食物 ✨          │
│                             │
│      [🍗 鸡胸肉 x1]          │
│                             │
│   猫咪会很开心哦~            │
│                             │
│      [ 去投喂 ]              │
└─────────────────────────────┘
```

---

## 七、实施方案与阶段划分

### Phase 1：数据层演进（P0）
- 新增 6 个实体 + DAO
- 数据库 v1→v2 Migration
- 种子数据（猫咪品种、食物目录、默认问题、成就里程碑）
- Hilt DI 模块更新

### Phase 2：问答流程重设计（P0）
- 新建 `ReflectionActivity` 替代 `QuizActivity`
- 3 个开放式文本输入，必填校验
- 新建 `ConfirmActivity` 二次抉择页
- 随机食物发放逻辑
- `AppMonitorService` 改为启动 `ReflectionActivity`

### Phase 3：猫咪养成系统（P0）
- 首次启动猫咪选择页 `CatSelectionScreen`
- 主界面猫咪展示区 + 好感度进度
- 投喂交互 + Lottie 动画播放
- 成就解锁逻辑与提示

### Phase 4：主界面重构与品牌升级（P1）
- HomeScreen 改为猫咪为中心
- 新增 CatScreen 投喂页
- strings.xml 品牌统一为「专注猫」
- 配色方案更新为温暖治愈风
- 问题自定义管理页

### Phase 5：构建配置与 APK 分发（P0）
- 版本号升级 versionCode=2, versionName="2.0.0"
- ProGuard 混淆配置启用
- Release 签名配置
- APK 构建命令与分发说明

---

## 八、APK 分发方案

### 8.1 构建命令
```bash
# Debug APK（快速测试）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release APK（分发用，需签名）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

### 8.2 签名配置
在 `~/.gradle/gradle.properties` 或项目 `gradle.properties` 中配置：
```
FOCUSCAT_STORE_FILE=focuscat.jks
FOCUSCAT_STORE_PASSWORD=*****
FOCUSCAT_KEY_ALIAS=focuscat
FOCUSCAT_KEY_PASSWORD=*****
```
`build.gradle.kts` 中引用，签名文件放 `app/` 目录。

### 8.3 分发渠道
1. **APK 直传**：构建 Release APK → 发到手机安装（最快捷）
2. **GitHub Release**：上传 APK 附带版本说明
3. **应用商店**：酷安（个人开发者友好）、F-Droid（开源）

### 8.4 权限说明（商店上架需声明）
| 权限 | 用途说明 |
|------|---------|
| `PACKAGE_USAGE_STATS` | 检测用户打开的应用 |
| `SYSTEM_ALERT_WINDOW` | 显示拦截页面 |
| `BIND_ACCESSIBILITY_SERVICE` | 监听应用切换事件 |
| `QUERY_ALL_PACKAGES` | 列出可选约束应用 |
| `FOREGROUND_SERVICE` | 保持守护服务运行 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启动 |

---

## 九、风险与对策

| 风险 | 对策 |
|------|------|
| 无障碍服务被系统杀死 | 前台保活服务 + 用户引导加白名单 |
| Lottie 动画资源体积大 | 使用 SVG 矢量图 + 简单补间动画兜底 |
| 数据库迁移失败 | Migration + fallback 策略，保留旧表 |
| 应用商店审核（无障碍权限） | 提供清晰权限说明视频 |
| 猫咪形象版权 | 使用开源 CC0 素材或自绘 |

---

## 十、验收标准

- [ ] 首次启动可选猫并命名
- [ ] 添加约束应用后，打开该应用触发反思页
- [ ] 反思页 3 个问题必填，填写后可进入确认页
- [ ] 确认页可选择「进入」或「退出获食物」
- [ ] 退出后获得随机食物并入库
- [ ] 主界面可投喂猫咪，播放进食+开心动画
- [ ] 好感度与投喂次数正确累计
- [ ] 10/50/100 次投喂触发成就
- [ ] 可自定义反思问题
- [ ] Release APK 可签名构建并在手机安装运行
