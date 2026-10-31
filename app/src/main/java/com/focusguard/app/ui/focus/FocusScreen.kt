package com.focusguard.app.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.focusguard.app.MainActivity
import com.focusguard.app.ui.components.CatVideo
import com.focusguard.app.ui.components.GlassDialog
import com.focusguard.app.ui.components.GlassDialogActions
import com.focusguard.app.ui.components.RunningCatProgress
import com.focusguard.app.ui.theme.TimerTextStyle

/**
 * 专注模式全屏页面（v3 · 沉浸视频版）
 *
 * 设计要点：
 * - 全屏视频作背景，ZOOM 铺满，猫咪在画面中央，天空在上方
 * - 计时器直接浮在天空区域，无遮罩，保持画面沉浸
 * - 左上角返回按钮，右上角状态栏避让
 * - 保留退出确认对话框、奖励动画覆盖层
 * - 移除：音乐功能、白色半透明遮罩、"猫咪陪伴"文字
 *
 * 防分心机制：FocusSessionState 标志位 + FocusGuardService 轮询检测切屏 → 拉回本页
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun FocusScreen(
    onExit: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    // 进入页面自动开始计时
    LaunchedEffect(Unit) {
        viewModel.startFocus()
    }

    // 拦截系统返回键：与退出按钮走同一确认链路，避免误按丢失进度
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.requestExit()
    }

    // 收集 FocusGuardService 的拉回事件（用户切屏时被拉回）
    val focusRecall by MainActivity.focusRecallEvent.collectAsStateWithLifecycle()
    LaunchedEffect(focusRecall) {
        if (focusRecall) {
            MainActivity.requestFocusRecallConsumed()
        }
    }

    // 全屏沉浸式背景 + 计时器
    // 注意：根容器不加 systemBarsPadding —— 视频必须铺满到屏幕底部导航栏区域，
    // 否则底部会露出一条背景色白边（破坏沉浸感）。内容元素各自用 statusBarsPadding 避让。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 视频全屏背景：focus 类别 9:16 竖屏，ZOOM 铺满
        val userCat = uiState.userCat
        if (userCat != null) {
            CatVideo(
                videoPath = "videos/focus/${userCat.breedId}.mp4",
                modifier = Modifier.fillMaxSize(),
                loop = true,
                videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                edgeFade = false,  // 全屏背景视频无需边缘柔化
                // 需求 2：进入专注页从视频中段随机时刻开始播放，每次进入都是新的画面
                randomStart = true,
                // 需求 2：换猫时以品种 ID 标识内容，遮住上一只猫的残留画面
                contentKey = userCat.breedId
            )
        }

        // === 左上角返回按钮 ===
        // 半透明玻璃圆钮 + 白色描边，浮在视频之上；不再是突兀的白色实心圆。
        IconButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.requestExit()
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 18.dp, top = 10.dp)
                .align(Alignment.TopStart)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.32f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "退出专注",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(18.dp)
            )
        }

        // === 计时器 ===
        // 圆润数字（Baloo 2）+ 自动缩放字号保证单行 + 下方细进度条
        // 不用圆环：圆环容易被误读成 24 小时时钟表盘
        FocusTimer(
            elapsedMillis = uiState.elapsedMillis,
            millisToNextReward = uiState.millisToNextReward,
            rewardIntervalMs = FocusUiState.REWARD_INTERVAL_MS,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 56.dp)
                .align(Alignment.TopCenter)
        )

        // === 奖励动画覆盖层（底部横幅）===
        // 放在底部而不是顶部：顶部被左上角返回按钮和计时器占据，
        // 横幅滑入会盖住返回按钮，导致按钮在该几秒内点不到。
        // 底部是 Toast/Snackbar 的惯例位置，也不会遮挡画面主体。
        AnimatedVisibility(
            visible = uiState.showRewardAnimation,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400)),
            exit = fadeOut(tween(600)) + scaleOut(targetScale = 0.8f, animationSpec = tween(600)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            RewardBannerOverlay(
                foodName = uiState.lastReward?.displayName ?: "猫粮"
            )
        }
    }

    // === 退出确认对话框 ===
    if (uiState.showExitConfirm) {
        ExitConfirmDialog(
            millisToNextReward = uiState.millisToNextReward,
            rewardsEarned = uiState.rewardsEarned,
            onConfirm = {
                viewModel.confirmExit()
                onExit()
            },
            onDismiss = { viewModel.cancelExit() }
        )
    }
}

/**
 * 专注计时器
 *
 * ── 设计意图 ────────────────────────────────────────────────
 *  - **不要进度圈**：外圈圆环会被误读成 24 小时时钟表盘，
 *    且在明亮天空上留下一圈生硬描边。改为纯文字 + 下方细进度条。
 *  - **圆润数字**：使用 Baloo 2 圆体（`TimerTextStyle`），
 *    字符本身有柔和的圆弧收笔，比系统黑体更有"可爱"感。
 *    该字体**只作用于计时数字**，不进入全局 Typography。
 *  - **绝不分行**：时长超过 1 小时后格式从 `MM:SS` 变成 `HH:MM:SS`，
 *    字符数从 5 变 8，固定字号会把数字挤到两行。用
 *    `BoxWithConstraints` + 文本测量，按可用宽度自动缩放字号，
 *    保证任何时长都是单行。
 *  - **不抖动的秒数**：字体自带 tnum（tabular figures），
 *    每个数字占位相同，跳秒时整串宽度不变。
 */
@Composable
private fun FocusTimer(
    elapsedMillis: Long,
    millisToNextReward: Long,
    rewardIntervalMs: Long,
    modifier: Modifier = Modifier
) {
    val timeText = formatElapsedTime(elapsedMillis)

    // 进度：已过的奖励周期比例（0f ~ 1f），以细进度条呈现
    val progress = if (rewardIntervalMs > 0) {
        val elapsedInCycle = rewardIntervalMs - millisToNextReward
        (elapsedInCycle.toFloat() / rewardIntervalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "focus_timer_progress"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val baseStyle = TimerTextStyle.copy(
            color = Color.White,
            shadow = Shadow(
                color = Color(0xFF6B4423).copy(alpha = 0.42f),
                offset = Offset(0f, 2f),
                blurRadius = 10f
            )
        )
        // 量出当前字符串在基准字号下的宽度，再按可用宽度反推能单行放下的字号
        val textMeasurer = rememberTextMeasurer()
        val estimatedWidthPx = remember(timeText) {
            textMeasurer.measure(
                text = timeText,
                style = baseStyle,
                constraints = Constraints()
            ).size.width
        }
        val availableWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val fittedFontSize = remember(estimatedWidthPx, availableWidthPx) {
            if (estimatedWidthPx <= 0f || availableWidthPx <= 0f) {
                baseStyle.fontSize
            } else {
                // 留 12% 余量，避免数字贴边
                val ratio = (availableWidthPx * 0.88f) / estimatedWidthPx
                (baseStyle.fontSize.value * ratio).coerceIn(26f, baseStyle.fontSize.value).sp
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = timeText,
                style = baseStyle.copy(
                    fontSize = fittedFontSize,
                    lineHeight = fittedFontSize * 1.16f,
                    letterSpacing = (fittedFontSize.value * 0.02f).sp
                ),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )

            // 小猫奔跑进度：猫沿轨道朝猫粮碗跑，位置 = 奖励进度
            Spacer(modifier = Modifier.height(12.dp))
            RunningCatProgress(
                progress = animatedProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
        }
    }
}

/**
 * Apple 风格奖励横幅通知
 * 底部浮出的细长横幅，带脉冲动画
 */
@Composable
private fun RewardBannerOverlay(foodName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "reward_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulse)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "为猫咪赢得 $foodName x1",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * 退出确认对话框
 */
@Composable
private fun ExitConfirmDialog(
    millisToNextReward: Long,
    rewardsEarned: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val intervalMs = FocusUiState.REWARD_INTERVAL_MS
    val minutesLeft = (millisToNextReward / 60000).toInt()

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "确定要退出专注吗？",
        actions = {
            GlassDialogActions(
                dismissLabel = "退出",
                confirmLabel = "继续专注",
                onDismiss = onConfirm,
                onConfirm = onDismiss
            )
        }
    ) {
        val msg = if (millisToNextReward >= intervalMs) {
            "本次专注已为猫咪赢得 $rewardsEarned 份猫粮，确定退出吗？"
        } else if (minutesLeft > 0) {
            "还有 $minutesLeft 分钟就可以为猫咪获得下一份猫粮奖励，确定要退出吗？"
        } else {
            "马上就能为猫咪获得下一份猫粮奖励，再坚持一下！确定要退出吗？"
        }
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 格式化已专注时长为 HH:MM:SS
 *
 * millis 可能为负（系统时间被往回调），此时钳制为 0，
 * 否则会显示成 "-2:-37" 这种负数时间。
 */
private fun formatElapsedTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
