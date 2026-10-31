package com.focusguard.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.focusguard.app.R

/**
 * 小猫奔跑进度指示器
 *
 * ── 资产来源 ────────────────────────────────────────────────
 * 猫的动画帧是用 **three.js** 离线渲染出来的 3D 素材（构建期一次性生成，
 * 不是运行时跑 WebView）：
 *  · 12 帧奔跑循环，Q 版橘猫（球体 / 胶囊 / 圆锥拼装，toon 材质）
 *  · 对角腿同相抬起 + 身体弹跳 + 尾巴逐节延迟摆动
 *  · 渲染后统一裁剪到「并集包围盒」，保证播放时猫身不左右抖动
 *  · 打包为横向精灵图 `cat_run_sheet.png`（12 帧 × 175px）
 *
 * ── 运行时行为 ──────────────────────────────────────────────
 *  - 猫沿轨道朝右奔跑，**位置 = 当前奖励进度**：跑到终点就吃到猫粮
 *  - 腿部动画持续循环（原地小跑 + 缓慢前进），像一只在追目标的猫
 *  - 轨道已完成的部分用蜜糖橙渐变填充，未完成部分是淡白
 */
@Composable
fun RunningCatProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.24f),
    accentStart: Color = Color(0xFFFF9F43),
    accentEnd: Color = Color(0xFFFFD08A),
    catColor: Color = Color(0xFFF5A45F)
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 精灵图解码（只做一次）
    val sheet = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.cat_run_sheet)
    }

    // 奔跑动画：12 帧 / 1 秒一轮
    val transition = rememberInfiniteTransition(label = "cat_run")
    val frameFloat by transition.animateFloat(
        initialValue = 0f,
        targetValue = FRAME_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cat_frame"
    )
    val frameIndex = (frameFloat.toInt()) % FRAME_COUNT

    val frameWidth = sheet.width / FRAME_COUNT
    val frameHeight = sheet.height

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        // 猫的显示尺寸（dp）：约 58dp 高，宽度按精灵比例
        val catHeightPx = with(density) { 58.dp.toPx() }
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val catWidthPx = catHeightPx * (frameWidth.toFloat() / frameHeight.toFloat())

        // 猫在轨道上的 x 坐标：起点留半个猫身，终点同样留白
        val usable = (trackWidthPx - catWidthPx).coerceAtLeast(0f)
        val clamped = progress.coerceIn(0f, 1f)
        val catX = usable * clamped

        // 轨道
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
        ) {
            val r = size.height / 2f
            // 未完成轨道
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(r, r)
            )
            // 已完成轨道
            val done = usable * clamped
            if (done > 1f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accentStart, accentEnd),
                        startX = 0f,
                        endX = done
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(done, size.height),
                    cornerRadius = CornerRadius(r, r)
                )
            }
        }

        // 终点：猫粮碗
        Canvas(
            modifier = Modifier
                .size(width = 30.dp, height = 22.dp)
                .align(Alignment.BottomEnd)
                .offset(y = (-3).dp)
        ) {
            drawFoodBowl(catColor)
        }

        // 小猫
        Canvas(
            modifier = Modifier
                .size(
                    width = with(density) { catWidthPx.toDp() },
                    height = with(density) { catHeightPx.toDp() }
                )
                .offset {
                    IntOffset(
                        x = catX.toInt(),
                        y = 0
                    )
                }
        ) {
            val image = sheet.asImageBitmap()
            drawImage(
                image = image,
                srcOffset = IntOffset(frameIndex * frameWidth, 0),
                srcSize = IntSize(frameWidth, frameHeight),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                alpha = 1f
            )
        }
    }
}

/** 精灵图帧数（须与 cat_run_sheet.png 的横向帧数一致） */
private const val FRAME_COUNT = 12

/** 画一个猫粮碗：小半圆碗 + 一条边沿 + 几粒粮 */
private fun DrawScope.drawFoodBowl(color: Color) {
    val w = size.width
    val h = size.height
    val rimY = h * 0.30f

    // 碗身
    val bowl = Path().apply {
        moveTo(w * 0.10f, rimY)
        lineTo(w * 0.90f, rimY)
        quadraticTo(w * 0.5f, h * 1.18f, w * 0.10f, rimY)
        close()
    }
    drawPath(bowl, color = color.copy(alpha = 0.55f))

    // 碗里的猫粮
    val grain = color.copy(alpha = 0.95f)
    drawCircle(grain, radius = h * 0.11f, center = Offset(w * 0.36f, rimY - h * 0.08f))
    drawCircle(grain, radius = h * 0.10f, center = Offset(w * 0.56f, rimY - h * 0.12f))
    drawCircle(grain, radius = h * 0.09f, center = Offset(w * 0.72f, rimY - h * 0.06f))

    // 碗沿
    drawLine(
        color = color,
        start = Offset(w * 0.04f, rimY),
        end = Offset(w * 0.96f, rimY),
        strokeWidth = h * 0.16f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
}
