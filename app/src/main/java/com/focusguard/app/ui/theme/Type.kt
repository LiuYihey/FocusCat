package com.focusguard.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 应用文字样式定义
 *
 * ── 排版理念 ────────────────────────────────────────────────
 * 1. **回归系统字体**：中文界面用系统字体（思源黑体 / MiSans 等）最稳妥，
 *    嵌入第三方字体会带来字形不统一、行高错位、包体膨胀等问题。
 *    之前引入的站酷快乐体 / Baloo 2 已全部移除。
 * 2. **数字用等宽数字（tabular）**：计时器、统计数值用
 *    `FontFeatureSettings("tnum")`，保证数字等宽，
 *    秒数跳动时不会左右抖动 —— 这是"设计感"的真正来源，
 *    而不是换一个花体。
 * 3. **正文开启 text-wrap: pretty**（Compose 1.7 的 LineBreak.Paragraph）：
 *    自动优化断行，避免末行只剩一两个字的"孤字行"，行末更自然。
 * 4. **暗色字重全降 40**：深色主题下字重整体下调一档
 *    （SemiBold→Medium、Bold→SemiBold），因为深底本身已提供视觉重量，
 *    字重过高会显得刺眼发闷。
 * 5. **层级靠字重梯度而非字号堆叠**：SemiBold → Medium → Normal，
 *    避免 Bold 造成的生硬观感。
 */

/** 系统默认字体族（保持平台原生观感） */
private val SystemFont = FontFamily.Default

/**
 * 等宽数字字体族
 * 通过 FontFeatureSettings 开启 tabular figures，
 * 让"00:12"这类计时数字等宽对齐，跳秒时不抖动。
 */
private val TabularNumberFont = FontFamily.Default

/** 浅色主题排版 */
val Typography = Typography(
    // 正文大号：开启 LineBreak.Paragraph 等价 text-wrap: pretty
    bodyLarge = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.3.sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 页面主标题
    headlineLarge = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 次级页面标题
    headlineMedium = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 区块标题
    titleLarge = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 按钮文字
    labelLarge = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp
    ),
    // 详情卡片、次要按钮
    labelMedium = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp
    ),
    // 辅助标注、底部导航
    labelSmall = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    // 正文中号
    bodyMedium = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 卡片标题
    titleMedium = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 辅助文本
    bodySmall = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.3.sp,
        lineBreak = LineBreak.Paragraph
    ),
    // 卡片副标题
    titleSmall = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        lineBreak = LineBreak.Paragraph
    )
)

/**
 * 深色主题排版 —— 字重全降 40
 *
 * 浅色 → 深色对应关系：
 *   Normal → Normal（不变）
 *   Medium → Normal（降 40）
 *   SemiBold → Medium（降 40）
 *   Bold → SemiBold（降 40）
 *
 * 深底已经自带视觉重量，字重再高会让文字"发闷"且边界生硬。
 */
val DarkTypography = Typography(
    bodyLarge = Typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
    headlineLarge = Typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
    titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.Medium),
    labelLarge = Typography.labelLarge.copy(fontWeight = FontWeight.Normal),
    labelMedium = Typography.labelMedium.copy(fontWeight = FontWeight.Normal),
    labelSmall = Typography.labelSmall.copy(fontWeight = FontWeight.Normal),
    bodyMedium = Typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
    titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.Normal),
    bodySmall = Typography.bodySmall.copy(fontWeight = FontWeight.Normal),
    titleSmall = Typography.titleSmall.copy(fontWeight = FontWeight.Normal)
)

/**
 * 计时器专用样式
 *
 * 数字用等宽（tabular）特性，跳秒时数字宽度不变 → 视觉稳定不抖动。
 * 字重 SemiBold + 紧凑字距，既有存在感又不笨重。
 * 这是"设计感"的来源：数字对齐 + 节奏，而非花体。
 */
val TimerTextStyle = TextStyle(
    fontFamily = TabularNumberFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 76.sp,
    lineHeight = 84.sp,
    letterSpacing = 2.sp,
    fontFeatureSettings = "tnum"
)
