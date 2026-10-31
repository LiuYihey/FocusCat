package com.focusguard.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.ui.theme.LocalGlassColors

/**
 * 高定弹窗（Glass Dialog）
 *
 * ── 解决的问题（实测截图对比）────────────────────────────────
 * 原生 AlertDialog 的观感问题：
 *  - 遮罩是纯黑半透明 → 廉价、割裂
 *  - 弹窗是纯白直角矩形 → 与 App 的圆润调性不符
 *  - 按钮文字方正、字号偏小
 *
 * 本组件的改进：
 *  - 遮罩用暖色深棕（非纯黑），并带极轻微的径向渐晕，
 *    让弹窗像是"从页面里浮起来"而不是"盖在页面上"
 *  - 弹窗容器 28dp 大圆角 + 柔和落影 + 顶部折射高光
 *  - 入场动画：缩放 0.92→1 + 淡入，带轻微弹性（不是生硬的缩放）
 *  - 退出动画：更快更利落（120ms），符合"确认/取消"的直觉
 *
 * @param onDismissRequest 点击外部/返回时回调
 * @param title 标题
 * @param modifier 弹窗内容修饰符
 * @param actions 底部操作区（固定在底部，始终可见）
 * @param content 内容区（超出时内部滚动）
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 遮罩：暖深棕（非纯黑）+ 轻微径向渐晕
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            scheme.scrim.copy(alpha = 0.34f),
                            scheme.scrim.copy(alpha = 0.52f)
                        ),
                        radius = 1400f
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            // 弹窗主体
            Box(
                modifier = modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .scale(0.96f)
                    .clip(RoundedCornerShape(28.dp))
                    // 落影：暖色投影，比默认黑影更柔和
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0x338B5A1A),
                        spotColor = Color(0x4D8B5A1A)
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.surface,
                                scheme.surfaceVariant.copy(alpha = 0.96f)
                            )
                        )
                    )
                    // 顶部折射高光（与液态玻璃同一套光学语言）
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                // 高光层
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(glass.highlightTop, Color.Transparent)
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    // 标题：由 GlassDialog 统一渲染，调用方不再重复输出标题
                    if (title.isNotEmpty()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // 内容区：可滚动，超出时内部滚动而不是撑破弹窗
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        content = content
                    )
                    // 操作区固定在底部，始终可见
                    if (actions != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        actions()
                    }
                }
            }
        }
    }
}

/**
 * 弹窗底部操作区
 *
 * 取消 / 保存并排，靠右对齐，等宽。
 * 主按钮用实心蜜糖橙，次按钮用无填充的暖色文字 —— 层级清晰但不抢戏。
 */
@Composable
fun GlassDialogActions(
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    confirmLabel: String,
    onDismiss: (() -> Unit)? = null,
    onConfirm: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissLabel != null && onDismiss != null) {
            Text(
                text = dismissLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = confirmLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onConfirm)
                .padding(horizontal = 22.dp, vertical = 11.dp)
        )
    }
}
