package com.focusguard.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.focusguard.app.R

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

/**
 * 计时器专用圆体数字字体族（Baloo 2）
 *
 * 只用于专注页的计时数字 —— 不进入全局 Typography，
 * 避免影响正文/按钮的字形与行高（之前全量替换字体导致
 * 「部分文字变了、部分没变、数字也没变」的不一致问题）。
 *
 * 字体本身自带 tnum（tabular figures）特性，秒数跳动时数字等宽不抖动。
 */
val TimerNumberFont = FontFamily(
    Font(R.font.baloo2, weight = FontWeight.Bold)
)

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
 * 使用 Baloo 2 圆体字（圆润可爱），配合 tnum 等宽特性，
 * 跳秒时数字宽度不变 → 视觉稳定不抖动。
 */
val TimerTextStyle = TextStyle(
    fontFamily = TimerNumberFont,
    fontWeight = FontWeight.Bold,
    fontSize = 76.sp,
    lineHeight = 84.sp,
    letterSpacing = 1.sp,
    fontFeatureSettings = "tnum"
)

/**
 * 可爱圆体字族（Baloo 2 + 站酷快乐体回退链）
 *
 * 只用于专注页那一句「XX已获得 N 份猫粮」——
 * 这是全 App 唯一一处需要"活泼可爱"气质的中文，
 * 所以**不进入全局 Typography**，只在此处按需引用。
 *
 * 字体回退链的作用：
 *  - 数字与拉丁字母 → Baloo 2（圆润、tnum 等宽，跳秒不抖）
 *  - 中日韩汉字 → 站酷快乐体（手绘活泼感）
 * 两种字体都是圆润手绘风，混排不突兀。
 */
val CuteTextFont = FontFamily(
    Font(R.font.baloo2_bold, weight = FontWeight.Bold),
    Font(R.font.zcool_kuaile, weight = FontWeight.Normal)
)

/**
 * 站酷快乐体（中文手绘活泼风）
 *
 * 供需要逐字分派字体的场景使用（见 FocusScreen 的猫粮计数文案）：
 * 汉字走这个，数字/拉丁字母走 Baloo 2。
 */
val CuteChineseFont = FontFamily(
    Font(R.font.zcool_kuaile, weight = FontWeight.Normal)
)

/**
 * 猫粮计数文案样式（专注页计时器下方）
 *
 * 蜜糖橙，与背景天空形成暖色对比。
 */
val CuteRewardTextStyle = TextStyle(
    fontFamily = CuteTextFont,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp
)
