package com.focusguard.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * AI 生成图片猫咪展示组件
 *
 * 使用 Seedream 生成的真实感猫咪 PNG。
 * - eating 状态：优先播放进食视频（基于该图片首帧生成）
 * - 其他状态：图片 + 缩放/位移/旋转动画
 *
 * @param breedId 品种 ID（ragdoll/orange）
 * @param state 动画状态（idle/eating/happy/sleeping）
 * @param modifier 布局修饰符
 * @param interactive 是否启用触控视差倾斜
 * @param useVideo 是否启用视频模式（eating 时播放进食视频）
 * @param edgeFade 是否启用视频边缘柔化过渡，透传给 CatVideo
 */
@Composable
fun CatCanvas(
    breedId: String,
    state: String = "idle",
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    useVideo: Boolean = true,
    edgeFade: Boolean = true,
    /** 视频（单次）播放完成回调，用于 eating 等单次播放场景 */
    onVideoCompletion: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // 预加载静态图片 bitmap（同步），作为视频播放器的 fallbackBitmap
    // 同步加载：确保首次渲染时 bitmap 就绪，作为视频首帧占位
    // cats/$breedId.png 就是 idle 视频的首帧，视觉上"图片→视频"完全无缝
    val assetPath = remember(breedId) { "cats/$breedId.png" }
    val bitmap = remember(breedId) { loadAssetBitmap(context, assetPath) }

    // === 统一视频检测（同步）===
    // 所有视频路径只与 breedId 相关，不随 state 变化重新检测
    // 同步检测：首次组合时立即得到结果，避免异步 LaunchedEffect 导致首次进入时
    // 视频检测为 null → 走静态图分支 → 检测完成后才切视频的"fallback"现象
    val eatVideoPath = "videos/eating/$breedId.mp4"
    val idleVideoPath = "videos/idle/$breedId.mp4"
    val eatVideoExists = remember(breedId) { assetExists(context, eatVideoPath) }
    val idleVideoExists = remember(breedId) { assetExists(context, idleVideoPath) }
    val availableBranches = remember(breedId) {
        listOf("踩奶", "伸懒腰", "蝴蝶").mapNotNull { name ->
            val path = "videos/idle/${breedId}_$name.mp4"
            if (assetExists(context, path)) path else null
        }
    }

    // === 统一视频播放（eating + idle 序列共用同一个 CatVideo 实例）===
    // 关键设计：eating 和 idle 视频在同一个 if 分支中，复用同一个 ExoPlayer。
    // 切换状态时只改变 videoPath，由 setKeepContentOnPlayerReset(true) 平滑过渡，
    // 避免 ExoPlayer 重建导致的黑屏/亮度闪烁。
    val isEating = useVideo && state == "eating" && eatVideoExists
    val isIdleVideo = useVideo && state in listOf("idle", "sleeping") && idleVideoExists

    if (isEating || isIdleVideo) {
        // idle 序列索引：0=idle, 1=分支1, 2=idle, 3=分支2, ...
        var sequenceIndex by remember(breedId) { mutableStateOf(0) }

        // 当前应播放的视频路径与回调
        val currentVideoPath: String
        val currentOnCompletion: (() -> Unit)?

        if (isEating) {
            // eating 状态：播放进食视频，完成后回调 onVideoCompletion
            currentVideoPath = eatVideoPath
            currentOnCompletion = onVideoCompletion
        } else {
            // idle 序列：偶数索引=idle 视频，奇数索引=分支视频
            currentVideoPath = if (sequenceIndex % 2 == 0) {
                idleVideoPath
            } else {
                val branchIdx = (sequenceIndex - 1) / 2
                availableBranches.getOrNull(branchIdx) ?: idleVideoPath
            }
            currentOnCompletion = {
                val sequenceLength = 2 * availableBranches.size
                if (availableBranches.isEmpty()) {
                    sequenceIndex = 0
                } else {
                    sequenceIndex = (sequenceIndex + 1) % sequenceLength
                    if (sequenceIndex % 2 == 1) {
                        val branchIdx = (sequenceIndex - 1) / 2
                        if (branchIdx >= availableBranches.size) {
                            sequenceIndex = 0
                        }
                    }
                }
            }
        }

        CatVideo(
            videoPath = currentVideoPath,
            modifier = modifier,
            loop = false,
            edgeFade = edgeFade,
            onCompletion = currentOnCompletion,
            fallbackBitmap = bitmap
        )
        return
    }
    // 视频不存在 → 继续走图片分支显示静态占位

    // 其他状态：图片 + 动画
    // bitmap 已在顶部预加载，此处直接使用
    val infiniteTransition = rememberInfiniteTransition(label = "cat_img")

    val (targetScaleX, targetScaleY, targetTranslateY, targetRotation) = when (state) {
        "idle" -> Quad(
            scaleX = infiniteTransition.animateFloat(
                initialValue = 0.98f, targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = LinearEasing), RepeatMode.Reverse
                ), label = "idle_scale"
            ).value,
            scaleY = infiniteTransition.animateFloat(
                initialValue = 0.98f, targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = LinearEasing), RepeatMode.Reverse
                ), label = "idle_scale_y"
            ).value,
            translateY = 0f,
            rotation = 0f
        )
        "eating" -> Quad(
            scaleX = 1f,
            scaleY = infiniteTransition.animateFloat(
                initialValue = 0.96f, targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    tween(400, easing = LinearEasing), RepeatMode.Reverse
                ), label = "eat_scale"
            ).value,
            translateY = infiniteTransition.animateFloat(
                initialValue = -2f, targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    tween(300, easing = LinearEasing), RepeatMode.Reverse
                ), label = "eat_y"
            ).value,
            rotation = 0f
        )
        "happy" -> Quad(
            scaleX = 1f,
            scaleY = 1f,
            translateY = infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = -16f,
                animationSpec = infiniteRepeatable(
                    tween(600, easing = LinearEasing), RepeatMode.Reverse
                ), label = "happy_y"
            ).value,
            rotation = infiniteTransition.animateFloat(
                initialValue = -3f, targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    tween(400, easing = LinearEasing), RepeatMode.Reverse
                ), label = "happy_rot"
            ).value
        )
        "sleeping" -> Quad(
            scaleX = infiniteTransition.animateFloat(
                initialValue = 0.99f, targetValue = 1.01f,
                animationSpec = infiniteRepeatable(
                    tween(3000, easing = LinearEasing), RepeatMode.Reverse
                ), label = "sleep_scale"
            ).value,
            scaleY = infiniteTransition.animateFloat(
                initialValue = 0.99f, targetValue = 1.01f,
                animationSpec = infiniteRepeatable(
                    tween(3000, easing = LinearEasing), RepeatMode.Reverse
                ), label = "sleep_scale_y"
            ).value,
            translateY = 0f,
            rotation = 0f
        )
        else -> Quad(1f, 1f, 0f, 0f)
    }

    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    val animatedTiltX by animateFloatAsState(targetValue = tiltX, label = "tilt_x")
    val animatedTiltY by animateFloatAsState(targetValue = tiltY, label = "tilt_y")

    Box(
        modifier = modifier
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull()?.let { change ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    tiltX = ((change.position.y - cy) / cy) * 8f
                                    tiltY = ((change.position.x - cx) / cx) * 8f
                                }
                            }
                        }
                    }
                } else Modifier
            )
            .graphicsLayer {
                scaleX = targetScaleX
                scaleY = targetScaleY
                translationY = targetTranslateY
                rotationZ = targetRotation
                rotationX = animatedTiltX
                rotationY = animatedTiltY
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "猫咪 $breedId",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Color(0xFFE0E0E0),
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

private data class Quad(
    val scaleX: Float,
    val scaleY: Float,
    val translateY: Float,
    val rotation: Float
)

/**
 * 从 assets 加载 Bitmap，失败返回 null
 */
private fun loadAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        context.assets.open(path).use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 检测 assets 中文件是否存在
 * 修复 B6：用 openFd 替代 available()，后者在部分 AssetInputStream 实现上不可靠
 */
private fun assetExists(context: Context, path: String): Boolean {
    return try {
        context.assets.openFd(path).close()
        true
    } catch (e: Exception) {
        false
    }
}
