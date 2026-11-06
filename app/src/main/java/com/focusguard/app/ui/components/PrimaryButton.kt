package com.focusguard.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 主行动按钮（高定版）
 *
 * ── 设计要点 ────────────────────────────────────────────────
 * 1. **禁用态不是灰**：原生 Button 禁用时变成灰色块，
 *    视觉上像"坏了"。这里改为**低饱和度的暖色半透明**，
 *    保持品牌感的同时明确表达"暂不可用"。
 * 2. **按压反馈**：按下时轻微缩放（0.97）+ 阴影收紧，
 *    给到"按下去有实体"的触感，而不是纯色切换。
 * 3. **渐变**：主色到浅主色的斜向渐变，
 *    比纯色更有光照感（与液态玻璃的高光语言一致）。
 * 4. 圆角 28dp，与 App 的圆润调性统一。
 *
 * @param text 按钮文案
 * @param onClick 点击回调
 * @param modifier 布局修饰符
 * @param enabled 是否可用
 * @param loading 是否显示加载态
 * @param height 按钮高度
 * @param containerColor 容器色（默认主色）
 * @param contentColor 文字色
 * @param textStyle 文字样式（默认 labelLarge；需要更大字号时显式传入）
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // 按下时轻微缩小，制造"实体下沉"感
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "primary_btn_scale"
    )

    // 禁用态：低饱和暖色半透明，而非灰色
    val actualContainer = if (enabled) containerColor else containerColor.copy(alpha = 0.22f)
    val actualContent = if (enabled) contentColor else contentColor.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scale(pressScale)
            .clip(RoundedCornerShape(height / 2))
            .background(
                if (enabled) {
                    // 斜向渐变：模拟光源从左上打来
                    Brush.linearGradient(
                        listOf(
                            containerColor.lighten(0.06f),
                            containerColor,
                            containerColor.darken(0.04f)
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(actualContainer, actualContainer))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = actualContent,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                style = textStyle,
                color = actualContent
            )
        }
    }
}

/**
 * 浅色提升（在保持色相的前提下向白色靠）
 */
private fun Color.lighten(factor: Float): Color = Color(
    red = red + (1f - red) * factor,
    green = green + (1f - green) * factor,
    blue = blue + (1f - blue) * factor,
    alpha = alpha
)

/**
 * 浅色压暗（用于渐变末端，增加体积感）
 */
private fun Color.darken(factor: Float): Color = Color(
    red = red * (1f - factor),
    green = green * (1f - factor),
    blue = blue * (1f - factor),
    alpha = alpha
)
