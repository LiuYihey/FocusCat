package com.focusguard.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Lottie 庆祝动画组件
 * 用于成就解锁、获得食物等庆祝场景
 *
 * @param modifier 布局修饰符
 * @param rawRes Lottie JSON 资源（默认使用庆祝动画）
 * @param iterations 重复次数；默认无限循环。传 1 即单次播放，3 即播放 3 次后停止以省电
 */
@Composable
fun LottieCelebration(
    modifier: Modifier = Modifier,
    rawRes: Int = com.focusguard.app.R.raw.lottie_celebration,
    iterations: Int = LottieConstants.IterateForever
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

/**
 * Lottie 单次播放动画组件
 * 用于一次性庆祝效果（如成就解锁）
 */
@Composable
fun LottieOnce(
    modifier: Modifier = Modifier,
    rawRes: Int = com.focusguard.app.R.raw.lottie_celebration,
    onFinished: () -> Unit = {}
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
    // 进度达到 1f 时回调
    // 修复 B9：progress 在 0.99-1.0 间可能抖动导致 onFinished 多次触发，
    // 用 remember 标志位保证只回调一次
    // 修复迭代7 Bug #4：将 rawRes 加入 remember key，避免同一实例切换 rawRes 时
    // hasFinished 仍为 true 导致新动画 onFinished 永不触发，界面卡住。
    var hasFinished by remember(rawRes) { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (!hasFinished && progress >= 0.99f) {
            hasFinished = true
            onFinished()
        }
    }
}
