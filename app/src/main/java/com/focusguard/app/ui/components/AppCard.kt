package com.focusguard.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Neo-minimalism 卡片设计 token
 *
 * 分层元素（Layered Elements）通过圆角 + 柔和阴影 + 明度差实现层级感：
 * - CardRadiusLarge：主要内容卡片（首页主卡、专注入口）— 16dp，全 App 卡片统一口径
 * - CardRadiusMedium：常规卡片（统计、权限项）— 12dp
 * - CardRadiusSmall：紧凑卡片（开关、列表项）— 10dp
 * - ElevationSoft：第一层（白卡浮于纸张色背景）
 * - ElevationFloat：第二层（悬浮按钮、强调卡）
 */
object AppCardTokens {
    val RadiusLarge = 16.dp
    val RadiusMedium = 12.dp
    val RadiusSmall = 10.dp
    val ElevationSoft = 1.dp
    val ElevationFloat = 3.dp
}

/**
 * 按压缩放修饰符 — 统一全 App 可点卡片的按压反馈
 *
 * 与 FoodItem 的 0.96 缩放一致，轻微下陷、克制但明确，避免"泡沫感"。
 * 需与 clickable 共享同一 interactionSource 才能驱动按压状态。
 *
 * 用法：
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Card(
 *     modifier = Modifier
 *         .pressScale(interactionSource)
 *         .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { ... }
 * )
 * ```
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    return this.scale(if (isPressed) pressedScale else 1f)
}

/**
 * 统一卡片样式 - Neo-minimalism Layered Card
 *
 * 设计要点：
 * 1. 白色表面浮于暖白纸张色背景，形成"第一层"
 * 2. 柔和低海拔阴影（1dp），不刺眼但能感知层级
 * 3. 圆角 14dp（中等），平衡"亲和"与"精致"
 * 4. 无强边框，靠阴影和明度差区分
 *
 * @param modifier 修饰符
 * @param containerColor 卡片背景色，默认为 surface（白色）
 * @param elevation 阴影海拔，默认 ElevationSoft（1dp）
 * @param radius 圆角，默认 RadiusMedium（14dp）
 * @param content 卡片内容
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    elevation: androidx.compose.ui.unit.Dp = AppCardTokens.ElevationSoft,
    radius: androidx.compose.ui.unit.Dp = AppCardTokens.RadiusMedium,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        content()
    }
}
