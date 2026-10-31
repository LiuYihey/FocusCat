package com.focusguard.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用主题颜色常量
 *
 * 设计语言：Warm Healing · Honey & Cream（温暖治愈 · 蜜糖奶油风）
 *
 * ── 分层原则（严格执行）─────────────────────────────────────
 * 1. 禁黑框与 #000：全 App 不出现纯黑（#000000）与黑色描边，
 *    所有"框线"一律用暖色调 + 明度分层表达，避免生硬割裂。
 * 2. 1-3% 微明度 4 层背景分层：背景不是"白 / 灰"两档，而是
 *    canvas → surface → surfaceRaised → surfaceOverlay 四层，
 *    每层相对下层只提亮 1-3% 明度，靠极微弱色差建立空间纵深，
 *    视觉上"平"但不空，达到 Apple / Linear 式的克制质感。
 * 3. 暗色字重全降 40：深色主题下所有文字字重相对浅色下调一档
 *    （如 SemiBold → Medium，Bold → SemiBold），因为深底本身
 *    已经提供了足够的视觉重量，字重再高会显得刺眼、发闷。
 * 4. 暖色偏移：所有中性色都带 3-6% 的暖偏移（红/黄向），
 *    避免纯灰带来的"工具感"，与猫咪陪伴的产品调性一致。
 */

// ========== 主色 - 蜜糖橙（Honey Orange）==========
// 温暖、治愈、明亮，呼应橘猫形象，把"克制分心"变成"收获陪伴"的温暖感
val Primary = Color(0xFFFF9F43)
val PrimaryLight = Color(0xFFFFB76B)
val PrimaryDark = Color(0xFFE8852A)
// 浅蜜橙：用于彩色背景（天空/视频）上的次级强调文字，
// 比 Primary 更柔和浅淡，不会与白色计时数字抢视觉重心
val PrimarySoft = Color(0xFFFFCC8C)

// ========== 强调色 - 浅蜜橙（同色系延伸） ==========
val Accent = Color(0xFFFFB74D)

// ========== 浅色主题 · 4 层微明度背景分层 ==========
// 起点仍是纯白（与 AI 生成的纯白底猫咪图无缝融合），
// 但向下每一层只降 1-3% 明度，形成 canvas → surface → raised → overlay
val Background = Color(0xFFFFFFFF)        // L0 canvas：纯白底
val Surface = Color(0xFFFDFBF7)           // L1 surface：比 canvas 暗 1%
val SurfaceVariant = Color(0xFFF7F1E7)    // L2 raised：比 L1 暗 2.4%
val SurfaceOverlay = Color(0xFFFBF5EC)    // L3 overlay：悬浮层，比 L2 亮 1%（抬升感）

// ========== 深色主题 · 4 层微明度背景分层 ==========
// 底色绝不用 #000：以暖深棕为起点，逐层提亮 1-3%
val DarkBackground = Color(0xFF221D19)      // L0 canvas：暖深棕，非纯黑
val DarkSurface = Color(0xFF272220)         // L1 surface：+1.2% 明度
val DarkSurfaceVariant = Color(0xFF2E2825)  // L2 raised：+2.9% 明度
val DarkSurfaceOverlay = Color(0xFF332C28)  // L3 overlay：+4.6% 明度（悬浮抬升）

// ========== 文本色（暖深棕，非纯黑） ==========
val TextPrimary = Color(0xFF3D2E1F)      // 暖深棕
val TextSecondary = Color(0xFF8C7B6B)    // 暖灰棕
val DarkTextPrimary = Color(0xFFF0E6DA)  // 暖米白（非纯白，降低眩光）
val DarkTextSecondary = Color(0xFFB8A897)

// ========== 状态色（暖调，去"吓人感"，用户偏好非红） ==========
// 成功 - 柔和抹茶绿，温暖治愈
val Success = Color(0xFF7CB342)
// 错误 - 柔暖橙粉，提示但不刺眼（仅用于图标/边框/容器底色，不用红色）
val Error = Color(0xFFE8A87C)
// 错误正文色 - 加深暖棕，bodySmall/14sp 在白底达 AA 4.5:1
val ErrorText = Color(0xFFA0522D)
// 待处理/警告 - 暖琥珀（warm amber #F59E0B，硬约束：权限提示使用此色）
val Warning = Color(0xFFF59E0B)

val OnPrimaryDark = Color(0xFFFFF8E7)

// ========== 装饰色（暖调，与蜜糖橙呼应） ==========
val StarGold = Color(0xFFE8B547)      // 暖金
val RarityCommon = Color(0xFFB0A899)  // 暖灰
val RarityRare = Color(0xFF6BA3C4)    // 雾蓝（稀有，冷色点缀平衡）
val RarityEpic = Color(0xFFC17767)    // 暖红棕（史诗，珍贵感）

// ========== 液态玻璃（Liquid Glass）专用色 ==========
// 苹果液态玻璃的核心是"折射"：边缘因折射而提亮、中心因厚度而加深。
// 这组颜色用于底部导航的玻璃层，保证在任意背景上都有可读的层次。
/** 玻璃层中心色（带背景色 8% 混入，形成"厚度"感） */
val GlassTint = Color(0x14FF9F43)

/** 玻璃上边缘高光（模拟顶光折射，最亮的一条细线） */
val GlassHighlightTop = Color(0x66FFFFFF)

/** 玻璃下边缘折射光（模拟底部反射环境光，暖调） */
val GlassHighlightBottom = Color(0x33FFD9A0)

/** 玻璃层内侧柔和阴影（让玻璃"浮"起来，而非平贴） */
val GlassInnerShadow = Color(0x14000000)

/** 深色主题下的玻璃高光（深色中玻璃更靠"发光边"表达） */
val GlassHighlightTopDark = Color(0x40FFFFFF)
val GlassHighlightBottomDark = Color(0x26FFC98A)
