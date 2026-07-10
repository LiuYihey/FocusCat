package com.focusguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浅色主题配色方案
 *
 * 设计语言：Warm Healing · Honey & Cream（温暖治愈 · 蜜糖奶油风）
 * - 纯白背景（#FFFFFF）与 AI 生成的纯白底猫咪图无缝融合，让猫咪像"住"在 app 里
 * - 蜜糖橙主色（#FF9F43），温暖治愈，呼应猫咪主题
 * - 奶油白卡片层（#FFF8E7）通过暖色明度差分层
 * - 错误色为柔暖橙粉，提示但不"吓人"（用户偏好非红）
 *
 * 适用：可爱毛茸茸的陪伴场景，温暖明亮不疲劳
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF3E0), // 淡奶油橙
    onPrimaryContainer = Color(0xFF8B5A1A), // 深焦糖
    secondary = Accent,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFF8E7), // 奶油白
    onSecondaryContainer = Color(0xFF5D4037), // 暖深棕
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = Color(0xFFFFFFFF),
    // 错误容器用暖橙淡色，避免回落 M3 默认粉红/红色（用户偏好非红）
    errorContainer = Color(0xFFFFF3E0),
    onErrorContainer = Color(0xFFA0522D),
    outline = Color(0xFFE8DCC8) // 暖米色描边
)

/**
 * 深色主题配色方案
 *
 * 跟随系统暗色模式自动切换。延续 Warm Healing 设计语言：
 * - 深背景采用暖深棕（非冷墨蓝），减少夜间使用时的冷感
 * - 主色沿用蜜糖橙，保持品牌温暖一致性
 * - 文本与表面分层通过暖色明度差实现，与浅色主题对称
 *
 * 仍以浅色为主设计，深色作为系统级适配。
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFF2A2420),
    primaryContainer = Color(0xFF8B5A1A),
    onPrimaryContainer = Color(0xFFFFF3E0),
    secondary = Accent,
    onSecondary = Color(0xFF2A2420),
    secondaryContainer = Color(0xFF443A32),
    onSecondaryContainer = Color(0xFFF5EBE0),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = Error,
    onError = Color(0xFF2A2420),
    errorContainer = Color(0xFF5D3520),
    onErrorContainer = Color(0xFFF5DCC8),
    outline = Color(0xFF5D4E42)
)

/**
 * 应用主题 Composable
 *
 * FocusCat 统一使用 Warm Healing 主题：
 * - 浅色模式：纯白背景与 AI 生成的纯白底猫咪图无缝融合，沉浸感最佳
 * - 深色模式：跟随系统设置，暖深棕背景，夜间使用不冷峻
 * - 蜜糖橙主色，温暖治愈，符合"可爱毛茸茸的陪伴感"
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
