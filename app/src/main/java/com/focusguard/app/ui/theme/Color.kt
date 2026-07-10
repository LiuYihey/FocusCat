package com.focusguard.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用主题颜色常量
 *
 * 设计语言：Warm Healing · Honey & Cream（温暖治愈 · 蜜糖奶油风）
 * 设计哲学：可爱毛茸茸的陪伴感（approachable warmth）
 *
 * 配色原则：
 * 1. 纯白背景（#FFFFFF）与 AI 生成的纯白底猫咪图无缝融合，让猫咪像"住"在 app 里
 * 2. 蜜糖橙主色（#FF9F43）呼应猫咪主题，温暖明亮治愈
 * 3. 奶油白卡片层（#FFF8E7）通过暖色明度差分层，而非冷灰
 * 4. 错误/警告色用暖橙金调，去"吓人感"（用户偏好暖色调而非红色）
 * 5. 文本用暖深棕而非纯黑，更柔和治愈
 */

// ========== 主色 - 蜜糖橙（Honey Orange）==========
// 温暖、治愈、明亮，呼应橘猫形象，把"克制分心"变成"收获陪伴"的温暖感
val Primary = Color(0xFFFF9F43)
val PrimaryLight = Color(0xFFFFB76B)
val PrimaryDark = Color(0xFFE8852A)

// ========== 强调色 - 浅蜜橙（同色系延伸） ==========
val Accent = Color(0xFFFFB74D)

// ========== 背景层 - 纯白 + 奶油白 ==========
// 纯白背景让 AI 生成的白底猫咪图无缝融入，沉浸感最佳
val Background = Color(0xFFFFFFFF)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFFFF8E7) // 奶油白，卡片分层

// ========== 深色主题（跟随系统，暖色调暗色） ==========
val DarkBackground = Color(0xFF2A2420) // 暖深棕，非冷墨蓝
val DarkBackgroundDeep = Color(0xFF1F1A16)
val DarkSurface = Color(0xFF352D27)
val DarkSurfaceVariant = Color(0xFF443A32)

// ========== 文本色（暖深棕，非纯黑） ==========
val TextPrimary = Color(0xFF3D2E1F) // 暖深棕
val TextSecondary = Color(0xFF8C7B6B) // 暖灰棕
val DarkTextPrimary = Color(0xFFF5EBE0) // 暖米白
val DarkTextSecondary = Color(0xFFC4B5A4)

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
val StarGold = Color(0xFFE8B547) // 暖金
val RarityCommon = Color(0xFFB0A899) // 暖灰
val RarityRare = Color(0xFF6BA3C4) // 雾蓝（稀有，冷色点缀平衡）
val RarityEpic = Color(0xFFC17767) // 暖红棕（史诗，珍贵感）
