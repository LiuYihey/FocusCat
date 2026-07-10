package com.focusguard.app.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 猫咪视频播放组件
 *
 * 基于 ExoPlayer 播放 assets 中的猫咪视频（进食/活动）。
 *
 * 丝滑衔接设计：
 * 1. ExoPlayer 与组件生命周期绑定（不随 videoPath 变化重建），避免切换视频时释放/重建导致黑屏
 * 2. PlayerView.setKeepContentOnPlayerReset(true) —— 切换 MediaItem 时保持上一帧画面，
 *    新视频 prepare 完成后自动切换，用户无感知
 * 3. LifecycleObserver —— Activity 后台暂停 / 前台恢复，避免回来后黑屏
 *
 * 黑屏兜底设计（首帧占位 + 无缝切换）：
 * - 接收外部传入的 fallbackBitmap（猫咪 PNG = idle 视频首帧）
 * - isVideoHealthy 初始 false：首次加载时显示 fallbackBitmap（=视频首帧）
 * - STATE_READY 后切换到视频播放，因 PNG 就是首帧，视觉上完全无缝
 * - 仅在真正异常时显示 fallback：播放错误 / 超长缓冲(>5s) / loop模式异常ENDED
 * - videoPath 变化时不标记不健康，靠 setKeepContentOnPlayerReset 保持上一帧
 *
 * @param videoPath assets 中的视频路径，如 "videos/idle/ragdoll.mp4"
 * @param modifier 布局修饰符
 * @param loop 是否循环播放（默认 true，用于持续动画效果）
 * @param videoResizeMode 视频缩放模式（FIT 完整显示 / ZOOM 铺满背景）
 * @param edgeFade 是否启用边缘柔化过渡（径向渐变遮罩），让视频边缘融入背景，
 *        避免出现明显的"视频播放器"矩形边界感。默认 true。
 *        全屏背景视频（如专注模式 ZOOM 铺满）应设为 false。
 * @param onCompletion 单次播放结束回调（仅 loop=false 时生效，用于视频序列切换）
 * @param fallbackBitmap 首次加载及播放器异常时显示的兜底图（猫咪 PNG = idle 视频首帧），
 *        首次加载时作为首帧占位，STATE_READY 后无缝切换到视频播放
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun CatVideo(
    videoPath: String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    videoResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    edgeFade: Boolean = true,
    onCompletion: (() -> Unit)? = null,
    fallbackBitmap: Bitmap? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCompletion = rememberUpdatedState(onCompletion)
    val currentLoop = rememberUpdatedState(loop)

    // === 播放器健康状态 ===
    // true = 视频正在渲染，显示 PlayerView
    // false = 首次加载中 / 真正异常，显示 fallbackBitmap
    // 初始 false：首次加载时显示 fallbackBitmap（= idle 视频首帧 PNG），
    // STATE_READY 后切换到视频，因 PNG 就是首帧，视觉上完全无缝
    var isVideoHealthy by remember { mutableStateOf(false) }
    // 进入 BUFFERING 的时间戳，用于检测超长时间缓冲
    var bufferingSince by remember { mutableStateOf<Long?>(null) }

    // ExoPlayer 只创建一次，与组件生命周期绑定。
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f  // 静音
            playWhenReady = true
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    // videoPath 变化时切换 MediaItem（player 不重建）
    LaunchedEffect(videoPath) {
        // 正常切换时不标记不健康：setKeepContentOnPlayerReset(true) 会保持上一帧画面，
        // 新视频 STATE_READY 后自动切换，无需首帧兜底介入，避免亮度突变
        bufferingSince = null
        val assetUri = "asset:///$videoPath"
        exoPlayer.setMediaItem(MediaItem.fromUri(assetUri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // loop 参数变化时更新 repeatMode
    LaunchedEffect(loop) {
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // 监听播放状态 + 定时健康检测 + 生命周期管理
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // 视频准备好，标记健康，清空缓冲计时
                        isVideoHealthy = true
                        bufferingSince = null
                    }
                    Player.STATE_BUFFERING -> {
                        // 缓冲是正常过程，不标记不健康，等视频自行恢复
                        // 仅记录起始时间，供超长缓冲检测用
                        bufferingSince = System.currentTimeMillis()
                    }
                    Player.STATE_IDLE -> {
                        // IDLE 是播放器初始/重置后的正常过渡状态（prepare 后进入 BUFFERING → READY）
                        // 不标记不健康，避免首次加载时误触发 fallback
                        bufferingSince = null
                    }
                    Player.STATE_ENDED -> {
                        // loop=true 模式下出现 ENDED 是异常（应自动循环）→ 不健康
                        if (currentLoop.value) {
                            isVideoHealthy = false
                        }
                        // loop=false 的正常结束 → 触发回调
                        if (currentOnCompletion.value != null && !currentLoop.value) {
                            currentOnCompletion.value?.invoke()
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // 播放器出错 → 不健康，显示兜底图
                isVideoHealthy = false
                bufferingSince = null
            }
        }
        exoPlayer.addListener(listener)

        // 定时健康检测：BUFFERING 超过 5 秒才标记不健康（真正异常）
        // 正常缓冲（1-3 秒）不应触发 fallback，避免图片闪现破坏丝滑感
        val handler = Handler(Looper.getMainLooper())
        val healthCheckRunnable = object : Runnable {
            override fun run() {
                val since = bufferingSince
                if (since != null && System.currentTimeMillis() - since > 5000) {
                    // 超长时间缓冲 → 真正异常，显示兜底图
                    isVideoHealthy = false
                    bufferingSince = null
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(healthCheckRunnable, 500)

        // 生命周期观察：后台暂停 / 前台恢复，防止回来后黑屏
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // 回到前台：恢复播放；若状态异常则重新 prepare
                    // 不标记不健康，让 STATE_READY 自然恢复健康状态，避免 fallback 闪现
                    if (exoPlayer.playbackState == Player.STATE_IDLE ||
                        exoPlayer.playbackState == Player.STATE_ENDED
                    ) {
                        exoPlayer.prepare()
                    }
                    exoPlayer.playWhenReady = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            handler.removeCallbacks(healthCheckRunnable)
            exoPlayer.release()
        }
    }

    // ContentScale 与 resizeMode 对齐
    val contentScale = when (videoResizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> ContentScale.Fit
        else -> ContentScale.Fit
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = videoResizeMode
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // === 兜底静态展示 ===
        // 视频不健康（切换中/异常/长时间缓冲/首次加载）时，用外部传入的 fallbackBitmap（猫咪 PNG）
        // 覆盖 PlayerView，确保画面永不消失，杜绝黑屏
        if (!isVideoHealthy && fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!isVideoHealthy && fallbackBitmap == null) {
            // 兜底图尚未加载完成（首次进入或切换中），用背景色填充避免透明黑屏
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // 边缘柔化过渡：径向渐变遮罩
        if (edgeFade) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
    }
}
