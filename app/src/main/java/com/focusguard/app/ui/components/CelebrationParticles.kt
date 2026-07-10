package com.focusguard.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.focusguard.app.ui.theme.Accent
import com.focusguard.app.ui.theme.Primary
import com.focusguard.app.ui.theme.RarityRare
import com.focusguard.app.ui.theme.StarGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * 庆祝粒子效果
 * 用于食物获取、成就解锁等高光时刻
 * 包含：彩色纸屑下落 + 爱心上浮 + 星星闪烁
 */
@Composable
fun CelebrationParticles(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Primary,
        Accent,
        StarGold,
        RarityRare,
        Color(0xFF34D399)
    )
) {
    val infiniteTransition = rememberInfiniteTransition(label = "celebration")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 彩色纸屑（从顶部下落）
            val confettiCount = 20
            for (i in 0 until confettiCount) {
                val seed = i * 0.1f
                val x = ((seed * 7.3f) % 1f) * w
                val startY = -50f
                val endY = h + 50f
                val y = startY + (endY - startY) * ((progress + seed) % 1f)
                val color = colors[i % colors.size]
                val drift = sin((progress + seed) * 6f) * 30f
                val size = 8f + (seed * 6f)

                rotate(degrees = rotation + i * 30f, pivot = Offset(x + drift, y)) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x + drift - size / 2, y - size / 2),
                        size = androidx.compose.ui.geometry.Size(size, size * 0.6f)
                    )
                }
            }

            // 爱心上浮（从底部上升）
            val heartCount = 6
            for (i in 0 until heartCount) {
                val seed = i * 0.17f
                val x = ((seed * 5.7f) % 1f) * w
                val startY = h + 30f
                val endY = -50f
                val y = startY + (endY - startY) * ((progress + seed) % 1f)
                val alpha = 1f - ((progress + seed) % 1f)
                val color = colors[i % colors.size]
                val drift = cos((progress + seed) * 4f) * 20f

                drawHeart(Offset(x + drift, y), 12f, color.copy(alpha = alpha * 0.7f))
            }

            // 星星闪烁（随机位置）
            val starCount = 8
            for (i in 0 until starCount) {
                val seed = i * 0.23f
                val x = ((seed * 11.3f) % 1f) * w
                val y = ((seed * 8.7f) % 1f) * h * 0.6f
                val twinkle = (sin((progress + seed) * 8f) + 1f) / 2f
                val color = colors[i % colors.size]
                val size = 4f + twinkle * 4f

                drawStar(Offset(x, y), size, color.copy(alpha = twinkle * 0.8f))
            }
        }
    }
}

/**
 * 绘制爱心
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + size / 4)
        cubicTo(
            center.x - size / 2, center.y - size / 4,
            center.x - size, center.y,
            center.x, center.y + size
        )
        cubicTo(
            center.x + size, center.y,
            center.x + size / 2, center.y - size / 4,
            center.x, center.y + size / 4
        )
    }
    drawPath(path = path, color = color)
}

/**
 * 绘制星星
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        for (i in 0..4) {
            val angle = (i * 144 - 90).toDouble() * Math.PI / 180
            val x = center.x + (radius * cos(angle)).toFloat()
            val y = center.y + (radius * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path = path, color = color)
}
