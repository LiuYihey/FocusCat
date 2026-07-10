package com.focusguard.app.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.focusguard.app.MainActivity
import com.focusguard.app.ui.components.CatVideo

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // 视频全屏背景：focus 类别 9:16 竖屏，ZOOM 铺满
        val userCat = uiState.userCat
        if (userCat != null) {
            CatVideo(
                videoPath = "videos/focus/${userCat.breedId}.mp4",
                modifier = Modifier.fillMaxSize(),
                loop = true,
                videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                edgeFade = false  // 全屏背景视频无需边缘柔化
            )
        }

        // === 左上角浮动返回按钮 ===
        IconButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.requestExit()
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .align(Alignment.TopStart)
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "退出专注",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // === 天空区域计时器 ===
        // 放在画面上方 1/3 处，白色大字体 + 轻微阴影，在蓝天背景上清晰可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 96.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = formatElapsedTime(uiState.elapsedMillis),
                    style = TextStyle(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 4.sp,
                        // 轻微阴影提升在明亮天空背景上的可读性
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.25f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                            blurRadius = 16f
                        )
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // === 奖励动画覆盖层（顶部滑入横幅）===
        AnimatedVisibility(
            visible = uiState.showRewardAnimation,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400)),
            exit = fadeOut(tween(600)) + scaleOut(targetScale = 0.8f, animationSpec = tween(600)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
 * Apple 风格奖励横幅通知
 * 从顶部滑入的细长横幅，带脉冲动画
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确定要退出专注吗？",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val msg = if (millisToNextReward >= intervalMs) {
                "本次专注已为猫咪赢得 $rewardsEarned 份猫粮，确定退出吗？"
            } else if (minutesLeft > 0) {
                "还有 $minutesLeft 分钟就可以为猫咪获得下一份猫粮奖励，确定要退出吗？"
            } else {
                "马上就能为猫咪获得下一份猫粮奖励，再坚持一下！确定要退出吗？"
            }
            Text(text = msg)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("退出", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("继续专注", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    )
}

/**
 * 格式化已专注时长为 HH:MM:SS
 */
private fun formatElapsedTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
