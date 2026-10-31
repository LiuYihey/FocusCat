package com.focusguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 浅色主题配色方案
 *
 * 设计语言：Warm Healing · Honey & Cream（温暖治愈 · 蜜糖奶油风）
 * - 纯白 canvas 与 AI 生成的纯白底猫咪图无缝融合，让猫咪像"住"在 app 里
 * - 4 层微明度背景分层（canvas → surface → raised → overlay），每层 1-3% 明度差
 * - 蜜糖橙主色（#FF9F43），温暖治愈，呼应猫咪主题
 * - 错误色为柔暖橙粉，提示但不"吓人"（用户偏好非红）
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF3E0), // 淡奶油橙
    onPrimaryContainer = Color(0xFF8B5A1A), // 深焦糖
    secondary = Accent,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SurfaceVariant, // L2 奶油白
    onSecondaryContainer = Color(0xFF5D4037), // 暖深棕
    tertiary = RarityRare,
    onTertiary = Color(0xFFFFFFFF),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface, // L1
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant, // L2
    onSurfaceVariant = TextSecondary,
    surfaceContainerHighest = SurfaceOverlay, // L3
    error = Error,
    onError = Color(0xFFFFFFFF),
    // 错误容器用暖橙淡色，避免回落 M3 默认粉红/红色（用户偏好非红）
    errorContainer = Color(0xFFFFF3E0),
    onErrorContainer = Color(0xFFA0522D),
    // 描边用暖米色且明度克制（禁黑框）
    outline = Color(0xFFE8DCC8),
    outlineVariant = Color(0xFFF0E7D9)
)

/**
 * 深色主题配色方案
 *
 * 延续 Warm Healing 设计语言：
 * - 深背景采用暖深棕（非 #000、非冷墨蓝），逐层提亮 1-3%
 * - 主色沿用蜜糖橙，保持品牌温暖一致性
 * - 文本与表面分层通过暖色明度差实现，与浅色主题对称
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFF2A2420),
    primaryContainer = Color(0xFF6B4413),
    onPrimaryContainer = Color(0xFFFFE9CE),
    secondary = Accent,
    onSecondary = Color(0xFF2A2420),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFF5EBE0),
    tertiary = RarityRare,
    onTertiary = Color(0xFF1F2A31),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerHighest = DarkSurfaceOverlay,
    error = Error,
    onError = Color(0xFF2A2420),
    errorContainer = Color(0xFF5D3520),
    onErrorContainer = Color(0xFFF5DCC8),
    outline = Color(0xFF5D4E42),
    outlineVariant = Color(0xFF4A3E35)
)

/**
 * 应用形状系统 —— 大圆角 + 柔和
 *
 * 圆润是"可爱感"的几何基础：圆角越大，亲和力越强。
 * 底部导航用 Capsule（胶囊），卡片用 20-24dp，弹窗用 28dp。
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 液态玻璃配色 —— 随主题切换的高光参数
 *
 * 苹果液态玻璃的视觉本质是"边缘折射提亮 + 中心厚度加深"，
 * 所以高光颜色必须随明暗主题切换，否则深色下会变成刺眼白边。
 */
data class GlassColors(
    val highlightTop: Color,
    val highlightBottom: Color,
    val innerShadow: Color,
    val tint: Color,
    val indicatorActive: Color,
    val indicatorInactive: Color
)

private val LightGlassColors = GlassColors(
    highlightTop = GlassHighlightTop,
    highlightBottom = GlassHighlightBottom,
    innerShadow = GlassInnerShadow,
    tint = GlassTint,
    indicatorActive = Primary,
    indicatorInactive = Color(0xFF8C7B6B)
)

private val DarkGlassColors = GlassColors(
    highlightTop = GlassHighlightTopDark,
    highlightBottom = GlassHighlightBottomDark,
    innerShadow = Color(0x1A000000),
    tint = Color(0x1AFF9F43),
    indicatorActive = PrimaryLight,
    indicatorInactive = DarkTextSecondary
)

val LocalGlassColors = staticCompositionLocalOf { LightGlassColors }

/**
 * 应用主题 Composable
 *
 * FocusCat 统一使用 Warm Healing 主题：
 * - 浅色模式：纯白 canvas 与 AI 生成的纯白底猫咪图无缝融合
 * - 深色模式：跟随系统设置，暖深棕 4 层背景，夜间使用不冷峻
 * - 站酷快乐体 + Baloo 2 圆润数字字体，可爱但不幼稚
 *
 * @param darkTheme 是否使用深色主题，默认跟随系统
 * @param content 子内容
 */
@Composable
fun FocusGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = if (darkTheme) DarkTypography else Typography
    val glassColors = if (darkTheme) DarkGlassColors else LightGlassColors

    CompositionLocalProvider(LocalGlassColors provides glassColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
