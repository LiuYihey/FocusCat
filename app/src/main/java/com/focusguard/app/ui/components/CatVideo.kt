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
import kotlin.random.Random

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
 * 随机起点设计（需求 2）：
 * - randomStart=true 时，每次加载新 MediaItem 都会在视频中段随机取一个时间点开始播放，
 *   每次进入页面 / 切换猫咪看到的内容都不同，带来新鲜感
 * - 随机位置在视频时长的 15%~85% 区间内取，避免一开始或结尾的突兀感
 * - 换猫时（contentKey 变化）会重新随机取点，且在新的首帧渲染前用新猫的静态图遮住
 *   上一只猫的残留画面，保证不会显示错误的猫
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
 * @param randomStart 是否从视频中段的随机时间点开始播放（需求 2）
 * @param contentKey 内容标识（如猫咪品种 ID）。用于检测"换了一只猫"，
 *        换猫时先遮住旧画面再播放新猫的视频，避免显示错误的猫
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
    fallbackBitmap: Bitmap? = null,
    randomStart: Boolean = false,
    contentKey: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCompletion = rememberUpdatedState(onCompletion)
    val currentLoop = rememberUpdatedState(loop)
    val currentContentKey = rememberUpdatedState(contentKey)

    // === MediaItem 代次计数器（需求 2 修复）===
    //
    // 背景：在同一个 ExoPlayer 实例上重复调用 setMediaItem 时，ExoPlayer 可能把
    // 上一个 MediaItem 的 STATE_ENDED 残留事件投递给监听器。该事件如果被误当作
    // "当前视频播放完毕"消费，会立刻触发 onCompletion → 上层切换到下一个视频
    // → 随机起跳标记 pendingRandomSeek 被覆盖为 false → 随机起点彻底失效。
    //
    // 解决：每次 setMediaItem 自增 generation，事件回调只接受"当前代次"的事件，
    // 旧代次的残留事件直接丢弃。
    var mediaItemGeneration by remember { mutableStateOf(0) }

    // 已确认 READY 的代次：只有该代次的视频才算"真正开始播放"，
    // 其 ENDED 事件才是有效的"播放完毕"信号
    var readyGeneration by remember { mutableStateOf(0) }

    // === 播放器健康状态 ===
    // true = 视频正在渲染，显示 PlayerView
    // false = 首次加载中 / 真正异常，显示 fallbackBitmap
    // 初始 false：首次加载时显示 fallbackBitmap（= idle 视频首帧 PNG），
    // STATE_READY 后切换到视频，因 PNG 就是首帧，视觉上完全无缝
    var isVideoHealthy by remember { mutableStateOf(false) }
    // 进入 BUFFERING 的时间戳，用于检测超长时间缓冲
    var bufferingSince by remember { mutableStateOf<Long?>(null) }
    // 已渲染首帧的内容标识，用于判断"当前画面属于哪只猫"
    var renderedContentKey by remember { mutableStateOf<String?>(null) }
    // 是否还需要执行一次随机起跳（在下一个 READY 时消费）
    var pendingRandomSeek by remember { mutableStateOf(false) }

    // 已消耗随机起跳的内容标识（需求 2）
    // 同一只猫的同一段视频只随机一次：进入页面 / 换猫时随机，
    // 待机动画自身循环切换（idle → 动作 → idle）不再跳位，避免动作中途突兀。
    var randomConsumedKey by remember { mutableStateOf<String?>(null) }

    // ExoPlayer 只创建一次，与组件生命周期绑定。
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f  // 静音
            playWhenReady = true
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    // videoPath 变化时切换 MediaItem（player 不重建）
    LaunchedEffect(videoPath, contentKey) {
        // 正常切换时不标记不健康：setKeepContentOnPlayerReset(true) 会保持上一帧画面，
        // 新视频 STATE_READY 后自动切换，无需首帧兜底介入，避免亮度突变
        bufferingSince = null
        // 标记需要随机起跳：仅当当前内容尚未随机过（进入页面 / 换猫）时才跳
        // videoPath 单独变化（待机动画序列切换）不重复跳位
        pendingRandomSeek = randomStart && randomConsumedKey != contentKey
        val assetUri = "asset:///$videoPath"
        // 代次自增：新 MediaItem 尚未 READY，先把 readyGeneration 置为 0，
        // 确保旧代次的 READY 记录不会让新代次的 ENDED 被误判为有效
        mediaItemGeneration += 1
        readyGeneration = 0
        exoPlayer.setMediaItem(MediaItem.fromUri(assetUri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // 换猫检测（需求 2）：在组合阶段直接判定，避免"重组 → effect 执行"之间
    // 闪现上一只猫的旧帧。renderedContentKey 只在新视频 READY 后才更新，
    // 因此只要内容标识与已渲染内容不一致，就必须用静态图遮住画面。
    val contentMismatch = contentKey != null && contentKey != renderedContentKey
    // 显示兜底图的条件：播放器不健康、或当前渲染的内容不是本次要展示的内容
    val shouldShowFallback = !isVideoHealthy || contentMismatch

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
                        renderedContentKey = currentContentKey.value
                        // 记录当前代次已就绪：此代次后续的 ENDED 才是有效的"播放完毕"
                        readyGeneration = mediaItemGeneration
                        // 随机起跳：READY 后才知道视频时长，此刻取随机位置并 seek
                        if (pendingRandomSeek) {
                            pendingRandomSeek = false
                            seekToRandomPosition(exoPlayer)
                            // 标记该内容已随机过：换猫时会再次随机
                            randomConsumedKey = currentContentKey.value
                        }
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
                        // 有效性校验（需求 2 修复）：
                        // 播放器在 setMediaItem 之前可能处于初始 ENDED 状态，
                        // 该残留事件绝不能被当作"当前视频播放完毕"，
                        // 否则会立刻触发 onCompletion 切走视频，随机起点随之失效。
                        // 只有"当前代次已 READY 过"的 ENDED 才是真正的播放结束。
                        val isCurrentItemEnded =
                            readyGeneration == mediaItemGeneration && mediaItemGeneration > 0

                        // loop=true 模式下出现 ENDED 是异常（应自动循环）→ 不健康
                        if (currentLoop.value) {
                            isVideoHealthy = false
                        }
                        // loop=false 的正常结束 → 触发回调
                        if (isCurrentItemEnded &&
                            currentOnCompletion.value != null &&
                            !currentLoop.value
                        ) {
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
        // 视频不健康（切换中/异常/长时间缓冲/首次加载）或换猫中（内容标识不匹配）时，
        // 用外部传入的 fallbackBitmap（猫咪 PNG）覆盖 PlayerView，
        // 确保画面永不消失、且绝不会显示上一只猫的错误画面
        if (shouldShowFallback && fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (shouldShowFallback && fallbackBitmap == null) {
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

/**
 * 从视频中段随机取一个时间点开始播放（需求 2）
 *
 * 取值区间为视频时长的 10%~70%：
 * - 起点太靠前会与"从头播放"观感雷同，失去新鲜感
 * - 终点太靠后会立刻播完结束，动画显得突兀
 *
 * 必须在 STATE_READY 之后调用，此时 ExoPlayer 才拿到准确的时长。
 */
private fun seekToRandomPosition(player: ExoPlayer) {
    val duration = player.duration
    // duration 未就绪（TIME_UNSET / 0）时不做处理，保持从头播放
    if (duration <= 0L || duration == androidx.media3.common.C.TIME_UNSET) return
    val min = (duration * 0.10f).toLong()
    val max = (duration * 0.70f).toLong()
    if (max <= min) return
    val target = min + Random.nextLong(max - min)
    player.seekTo(target)
}
