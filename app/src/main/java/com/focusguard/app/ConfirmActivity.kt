package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.focusguard.app.detection.AppDetectionManager
import com.focusguard.app.ui.components.CelebrationParticles
import com.focusguard.app.ui.components.LottieCelebration
import com.focusguard.app.ui.reflection.ConfirmViewModel
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.util.getAppName
import dagger.hilt.android.AndroidEntryPoint

/**
 * 确认/格言奖励 Activity
 * 反思问答提交后的二次抉择页
 *
 * 设计理念：Positive Reward（正向奖励）
 * 用户选择退出即可获得一句随机小猫格言，把「克制」变成「收获」
 *
 * 两个选项：
 * 1. 确认进入 → 启动目标应用
 * 2. 退出获格言 → 随机抽取一句小猫格言 + 显示揭晓动效
 */

/**
 * 确认页阶段
 */
private enum class ConfirmPhase {
    /** 选择页（确认进入 / 退出获格言） */
    CHOICE,
    /** 获得格言 */
    OBTAINED,
    /** 获取失败 */
    FAILED
}

@AndroidEntryPoint
class ConfirmActivity : ComponentActivity() {

    private val viewModel: ConfirmViewModel by viewModels()

    // 提升为类成员属性，以便 onNewIntent 能访问并更新
    // （singleInstance 模式下新拦截会走 onNewIntent 而非重建 Activity）
    // 使用 by mutableStateOf 委托：在 Composable 中读取会被 Compose snapshot 观察，
    // 修改时自动触发重组；在非 Composable 中（如 onNewIntent）赋值也安全。
    private var targetPackage: String by mutableStateOf("")
    private var displayAppName: String by mutableStateOf("")

    /**
     * 标记用户是否选择了"还是进入"（onAppEntered 已调用）
     * onStop 时据此判断是否需要清空 lastAppEnterPkg：
     * - true：用户选择了进入应用，lastAppEnterPkg 保留用于 recurring 计时
     * - false：用户按 home 键/返回键/退出获格言，清空 lastAppEnterPkg 避免残留
     */
    private var hasUserEnteredApp: Boolean = false

    /**
     * 用户在反思页选中的「使用时长」（分钟），需求 3
     *
     * >0 时，确认进入后写入临时豁免窗口：该时长内再次进入该应用不再弹窗，
     * 时间到后自动恢复拦截并提醒。
     */
    private var selectedDurationMin: Int by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸式显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val pkg = intent.getStringExtra(ReflectionActivity.EXTRA_TARGET_PACKAGE) ?: ""

        if (pkg.isBlank()) {
            finish()
            return
        }

        // 初始化成员状态（onCreate 首次进入）
        targetPackage = pkg
        // 解析应用名（用户可读）
        displayAppName = getAppName(pkg)
        // 需求 3：读取反思页选中的使用时长
        selectedDurationMin = intent.getIntExtra(ReflectionActivity.EXTRA_SELECTED_DURATION_MIN, 0)

        viewModel.setTargetApp(pkg)

        // 标记拦截页仍在显示（防止 polling 在 ConfirmActivity 期间重复弹窗）
        // ReflectionActivity.onDestroy 已调用 onQuizDismissed，需在此重新标记
        AppDetectionManager.onQuizShown(pkg)

        // 拦截返回键
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = viewModel.state.value
                when {
                    // 已进入"获得格言"页或获取失败页，返回键直接关闭
                    current.isExiting -> finish()
                    // 选择页阶段，返回键 = 放弃进入目标应用，回到桌面
                    // 与「退出获格言」按钮区分：返回键不发放格言，仅克制地离开
                    // 修复 P0-1：原逻辑返回键 = exitWithFood() 违背直觉，用户未主动选择却被动获得食物
                    else -> {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                }
            }
        })

        setContent {
            // 统一使用应用主题，颜色引用 MaterialTheme.colorScheme
            FocusGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsStateWithLifecycle()

                    // 用阶段枚举驱动 AnimatedContent，让反思→确认→揭晓→收获的情感高潮丝滑过渡
                    val phase = when {
                        state.isExiting && state.grantedQuote != null -> ConfirmPhase.OBTAINED
                        state.isExiting -> ConfirmPhase.FAILED
                        else -> ConfirmPhase.CHOICE
                    }

                    AnimatedContent(
                        targetState = phase,
                        transitionSpec = {
                            (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300))) togetherWith
                                fadeOut(tween(200))
                        },
                        contentAlignment = Alignment.Center,
                        label = "confirm_phase"
                    ) { p ->
                        when (p) {
                            ConfirmPhase.OBTAINED -> {
                                QuoteObtainedContent(
                                    quote = state.grantedQuote ?: "",
                                    onClose = {
                                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                            addCategory(Intent.CATEGORY_HOME)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(homeIntent)
                                        finish()
                                    }
                                )
                            }

                            ConfirmPhase.FAILED -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .safeDrawingPadding()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // 失败图标：用"难过"表情替代猫爪，与正面场景视觉区分
                                    // 修复 P2-4：原用 Pets（猫爪）= 全 App 正面图标，失败时易误读为成功
                                    Icon(
                                        imageVector = Icons.Filled.SentimentDissatisfied,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "格言暂时没准备好，稍后再试一次吧",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.exitWithQuote() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = "重试",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { finish() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "关闭",
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            ConfirmPhase.CHOICE -> {
                                ConfirmChoiceContent(
                                    targetAppName = displayAppName,
                                    durationMin = selectedDurationMin,
                                    onConfirmEnter = {
                                        // 标记用户选择了进入应用，onStop 时保留 lastAppEnterPkg
                                        hasUserEnteredApp = true
                                        // 记录「进入应用」会话：作为 recurring quiz 计时起点，
                                        // 同时让 checkAndTrigger 在此期间对此包返回 false（防进入后立即重复弹窗）
                                        // 需求 3：传入用户选中的使用时长，
                                        // 该时长内再次进入此应用不再弹窗，到期后自动恢复拦截提醒
                                        AppDetectionManager.onAppEntered(targetPackage, selectedDurationMin)
                                        // 「时长到期」提醒已送达（用户在本次弹窗做出了新选择），
                                        // 清除该事件，避免它被用于后续的弹窗
                                        AppDetectionManager.consumePendingExpiryReminder(targetPackage)
                                        // 仅 finish() 即可：拦截页（ReflectionActivity → ConfirmActivity）
                                        // 以 NEW_TASK 启动于独立 task，用户原本打开的目标应用仍留在背后的 task。
                                        // ConfirmActivity finish 后系统自动将该 task 拉回前台，目标应用自然显现。
                                        // 不再显式 startActivity 启动目标应用：显式启动会引入 task 切换过渡，
                                        // 轮询引擎可能在过渡瞬间检测到桌面等非约束应用而调用 clearAppEnterSession()
                                        // 清空 lastAppEnterPkg，导致目标应用回前台时被 checkAndTrigger 误判为
                                        // "首次打开"再次弹窗，用户卡在问答页无法进入应用。
                                        finish()
                                    },
                                    onExitWithQuote = {
                                        // 用户选择退出获格言：结束本次「进入应用」会话，
                                        // 确保下次再打开该应用能被正常拦截为新会话
                                        AppDetectionManager.clearAppEnterSession()
                                        viewModel.exitWithQuote()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleInstance 模式下，新拦截触发不同应用时系统复用现有实例
        // 默认 onNewIntent 什么也不做，会导致 viewModel 仍持有旧目标包名、UI 显示旧应用名
        val pkg = intent.getStringExtra(ReflectionActivity.EXTRA_TARGET_PACKAGE) ?: ""
        if (pkg.isNotBlank()) {
            targetPackage = pkg
            displayAppName = getAppName(pkg)
            // 需求 3：新拦截周期重置选中的使用时长，避免沿用上一次的豁免时长
            selectedDurationMin = intent.getIntExtra(ReflectionActivity.EXTRA_SELECTED_DURATION_MIN, 0)
            // 重置进入标志：新拦截周期开始
            hasUserEnteredApp = false
            // 重置全部状态：取消旧获取协程，清空 isExiting/grantedQuote
            // 否则若上次拦截已到 OBTAINED/FAILED 阶段，新拦截仍显示旧的格言结果
            viewModel.resetForNewInterception(pkg)
            // 重新标记拦截页显示（防止 polling 在 ConfirmActivity 期间重复弹窗）
            AppDetectionManager.onQuizShown(pkg)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台回到前台时重新标记拦截页显示
        // onStop 时已释放 isQuizShowing，此处恢复
        if (targetPackage.isNotBlank()) {
            AppDetectionManager.onQuizShown(targetPackage)
        }
    }

    override fun onStop() {
        super.onStop()
        // Bug 5：弹窗解耦 - Activity 进入后台时立即销毁，避免用户打开 FocusCat 时
        // 仍显示确认页（singleInstance 模式下 Activity 会残留在独立 task 中）
        // 如果用户没有选择"还是进入"，清空 lastAppEnterPkg 防止残留
        if (!hasUserEnteredApp) {
            AppDetectionManager.clearAppEnterSession()
        }
        AppDetectionManager.onQuizDismissed()
        // 解耦：弹窗进入后台即销毁，确保不影响 FocusCat 主界面
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ConfirmActivity 关闭时清除拦截页显示标记
        // 注意：onStop 通常已先调用过 onQuizDismissed，此处为兜底
        if (!hasUserEnteredApp) {
            AppDetectionManager.clearAppEnterSession()
        }
        AppDetectionManager.onQuizDismissed()
    }

}

/**
 * 确认/退出选择页
 * 颜色统一引用 MaterialTheme.colorScheme
 *
 * @param durationMin 用户在反思页选中的使用时长（分钟），>0 时展示免打扰提示
 */
@Composable
private fun ConfirmChoiceContent(
    targetAppName: String,
    durationMin: Int = 0,
    onConfirmEnter: () -> Unit,
    onExitWithQuote: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 矢量猫爪图标（替代 emoji）
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "确认进入吗？",
            color = textColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 把目标应用名作为副标题放在主标题下方，让用户一眼知道在决定进入哪个应用
        // 修复 P2-8：原底部脚注「触发应用：X」与按钮文案重复且位置太靠后
        Text(
            text = "你正要打开 $targetAppName",
            color = dimColor,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "退出可获得一句小猫格言",
            color = dimColor,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        // 需求 3：告知用户本次免打扰时长，到期后会再提醒
        if (durationMin > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "接下来 $durationMin 分钟不会再打扰你",
                color = accentColor.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 退出获格言按钮 - 主按钮（正向行为，用品牌 primary 色保持全 App 一致，对比度 16.5:1）
        Button(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onExitWithQuote()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "退出，收获一句格言",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 确认进入按钮 - 次要按钮（弱化，引导用户优先选择退出获格言）
        OutlinedButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onConfirmEnter()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "还是进入 $targetAppName",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = dimColor
            )
        }
    }
}

/**
 * 获得格言的展示页（需求 1）
 *
 * 保留原「获得食物」的揭晓仪式感，但主体改为一句小猫格言：
 * 1. 标题：柔和缩放淡入（EaseOutCubic，无旋转/无过冲）
 * 2. 光晕：双层呼吸式径向渐变（持续脉动，模拟柔和发光）
 * 3. 漂浮粒子：Canvas 绘制的微光点缓慢上浮
 * 4. 格言卡片：引号装饰 + 居中文字，分阶段淡入
 * 5. 文字：分阶段淡入（标题→卡片内容→按钮），克制留白
 *
 * 同时保留原有的 LottieCelebration + CelebrationParticles 庆祝效果
 */
@Composable
private fun QuoteObtainedContent(
    quote: String,
    onClose: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    val hapticFeedback = LocalHapticFeedback.current

    // === 出现阶段 ===
    // 0: 初始  1: 标题浮现  2: 格言淡入  3: 按钮淡入
    var appearPhase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        appearPhase = 1
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(320)
        appearPhase = 2
        delay(200)
        appearPhase = 3
    }

    // 标题：柔和缩放（0.85→1）+ 淡入，EaseOutCubic 缓动
    val titleScale by animateFloatAsState(
        targetValue = if (appearPhase >= 1) 1f else 0.85f,
        animationSpec = tween(560, easing = EaseOutCubic),
        label = "quote_title_scale"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (appearPhase >= 1) 1f else 0f,
        animationSpec = tween(480, easing = EaseOutCubic),
        label = "quote_title_alpha"
    )

    // 光晕：初始扩散 + 持续呼吸（双层）
    val glowAlpha by animateFloatAsState(
        targetValue = if (appearPhase >= 1) 0.22f else 0f,
        animationSpec = tween(700, easing = EaseOutCubic),
        label = "quote_glow_alpha"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "quote_glow_breath")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "quote_breath"
    )

    // 格言淡入
    val quoteAlpha by animateFloatAsState(
        targetValue = if (appearPhase >= 2) 1f else 0f,
        animationSpec = tween(420, easing = EaseOutCubic),
        label = "quote_text_alpha"
    )

    // 按钮淡入
    val buttonAlpha by animateFloatAsState(
        targetValue = if (appearPhase >= 3) 1f else 0f,
        animationSpec = tween(400, easing = EaseOutCubic),
        label = "quote_button_alpha"
    )

    // 漂浮粒子（Canvas，缓慢上浮的微光点）
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "quote_particle_progress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LottieCelebration(
            modifier = Modifier.fillMaxSize(),
            iterations = 3
        )
        CelebrationParticles(
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 标题（淡入）
            Box(
                modifier = Modifier
                    .scale(titleScale)
                    .alpha(titleAlpha)
            ) {
                Text(
                    text = "小猫想对你说",
                    color = accentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 格言卡片（含光晕、粒子、缩放淡入）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    // 顶部渐变细线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // 漂浮粒子层
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val particleCount = 14
                        for (i in 0 until particleCount) {
                            val seed = i * 0.137f
                            val x = ((seed * 7.3f) % 1f) * w
                            val startY = h + 10f
                            val endY = h * 0.15f
                            val y = startY + (endY - startY) * ((particleProgress + seed) % 1f)
                            val lifeAlpha = 1f - ((particleProgress + seed) % 1f)
                            val radius = 1.5f + (seed * 2f)
                            val color = accentColor.copy(alpha = lifeAlpha * 0.5f)
                            drawCircle(color = color, radius = radius, center = Offset(x, y))
                        }
                    }

                    // 内容居中
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 双层呼吸光晕
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(150.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((130.dp * breath))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                accentColor.copy(alpha = glowAlpha * 0.6f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size((100.dp * (2f - breath)))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                accentColor.copy(alpha = glowAlpha * 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            // 引号装饰
                            Icon(
                                imageVector = Icons.Filled.FormatQuote,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 格言正文
                        Text(
                            text = quote,
                            color = textColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 34.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(quoteAlpha)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 副标题（淡入）
            Text(
                text = "放下，是为了更好地拿起",
                color = dimColor,
                fontSize = 15.sp,
                modifier = Modifier.alpha(quoteAlpha)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 按钮（淡入）
            Column(
                modifier = Modifier.alpha(buttonAlpha)
            ) {
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(
                        text = "我记住了",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "返回桌面", color = dimColor)
                }
            }
        }
    }
}
