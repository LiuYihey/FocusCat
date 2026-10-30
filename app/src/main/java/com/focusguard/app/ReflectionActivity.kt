package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.focusguard.app.detection.AppDetectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.focusguard.app.ui.reflection.ReflectionUiState
import com.focusguard.app.ui.reflection.ReflectionViewModel
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.util.getAppName
import dagger.hilt.android.AndroidEntryPoint

/**
 * 反思拦截 Activity
 * 当用户打开被约束的应用时弹出，填写反思问题后方可进入确认页
 *
 * 核心理念：Goal Reinforcement（目标强化）
 * 问题无标准答案，仅唤醒用户对当下意图的觉察
 *
 * 流程：显示 3 个开放式问题 → 用户填写 → 点击「进入应用」→ 跳转 ConfirmActivity
 */
@AndroidEntryPoint
class ReflectionActivity : ComponentActivity() {

    private val viewModel: ReflectionViewModel by viewModels()

    /**
     * 标记是否正在跳转到 ConfirmActivity
     * 用于 onDestroy 时决定是否调用 onQuizDismissed：
     * - true：跳转中，onDestroy 不调用 onQuizDismissed（由 ConfirmActivity 管理 isQuizShowing）
     * - false：用户直接关闭，onDestroy 调用 onQuizDismissed 释放拦截状态
     * 避免竞态条件：ReflectionActivity.onDestroy 与 ConfirmActivity.onCreate 时序不保证
     */
    private var isTransitioningToConfirm = false

    // 以下 4 个状态提升为类成员属性，以便 onNewIntent 能访问并更新
    // （singleInstance 模式下新拦截会走 onNewIntent 而非重建 Activity）
    // 使用 by mutableStateOf 委托：在 Composable 中读取会被 Compose snapshot 观察，
    // 修改时自动触发重组；在非 Composable 中（如 onNewIntent）赋值也安全。
    private var currentTargetPackage: String by mutableStateOf("")
    private var displayAppName: String by mutableStateOf("")
    private var isRecurring: Boolean by mutableStateOf(false)
    private var elapsedMin: Int by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸式显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""

        if (targetPackage.isBlank()) {
            finish()
            return
        }

        // 初始化成员状态（onCreate 首次进入）
        currentTargetPackage = targetPackage
        displayAppName = getAppName(targetPackage)
        isRecurring = intent.getBooleanExtra(EXTRA_IS_RECURRING, false)
        elapsedMin = intent.getIntExtra(EXTRA_ELAPSED_MIN, 0)

        // 标记拦截页已显示
        AppDetectionManager.onQuizShown(targetPackage)
        // 加载反思问题
        viewModel.loadQuestions(targetPackage)

        // 拦截返回键 - 返回即放弃进入目标应用（符合专注守护理念）
        // Bug 5：弹窗解耦 - 回退直接关闭弹窗 + 退出约束的娱乐 app，不影响 FocusCat 主界面
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 用户选择不进入目标应用，跳桌面关闭弹窗和被约束的娱乐 app
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(homeIntent)
                } catch (e: Exception) {
                    // 无桌面 launcher 时降级到 finish
                }
                finish()
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

                    // 提交成功后跳转确认页
                    LaunchedEffect(state.isSubmitted) {
                        if (state.isSubmitted) {
                            try {
                                val confirmIntent = Intent(this@ReflectionActivity, ConfirmActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra(EXTRA_TARGET_PACKAGE, currentTargetPackage)
                                    // 需求 3：把用户选中的「使用时长」透传给确认页，
                                    // 确认进入后据此写入临时豁免窗口
                                    putExtra(EXTRA_SELECTED_DURATION_MIN, state.selectedDurationMinutes)
                                }
                                // 标记跳转中，onDestroy 不调用 onQuizDismissed
                                // 改由 ConfirmActivity 管理 isQuizShowing 状态
                                isTransitioningToConfirm = true
                                startActivity(confirmIntent)
                                // 只在跳转成功后 finish，失败时保留页面让用户重试
                                finish()
                            } catch (e: Exception) {
                                // 跳转失败时重置状态，允许用户重试
                                isTransitioningToConfirm = false
                                viewModel.resetSubmitted()
                            }
                        }
                    }

                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        state.questions.isEmpty() -> {
                            // 反思问题被清空：不再静默跳转，给出友好提示 + 返回 FocusCat 入口
                            EmptyQuestionsContent(
                                onGoToSettings = {
                                    // 返回 FocusCat 主界面，用户可到「设置 → 反思问题管理」添加问题
                                    try {
                                        val mainIntent = Intent(
                                            this@ReflectionActivity,
                                            MainActivity::class.java
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        startActivity(mainIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@ReflectionActivity,
                                            "请到 FocusCat 设置中添加反思问题",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    finish()
                                },
                                onClose = { finish() }
                            )
                        }

                        else -> {
                            ReflectionContent(
                                state = state,
                                displayAppName = displayAppName,
                                isRecurring = isRecurring,
                                elapsedMin = elapsedMin,
                                onAnswerChange = { index, answer ->
                                    viewModel.updateAnswer(index, answer)
                                },
                                onOptionSelect = { index, optionIndex ->
                                    viewModel.selectOption(index, optionIndex)
                                },
                                onSubmit = {
                                    viewModel.submitAnswers()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        if (targetPackage.isNotBlank()) {
            AppDetectionManager.onQuizShown(targetPackage)
            viewModel.loadQuestions(targetPackage)
            // 同步更新全部状态，确保跳转 ConfirmActivity 时传递正确包名、UI 显示正确应用名
            currentTargetPackage = targetPackage
            displayAppName = getAppName(targetPackage)
            isRecurring = intent.getBooleanExtra(EXTRA_IS_RECURRING, false)
            elapsedMin = intent.getIntExtra(EXTRA_ELAPSED_MIN, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台回到前台时重新标记拦截页显示
        // onStop 时已释放 isQuizShowing，此处恢复，防止轮询在后台期间重复弹窗
        if (currentTargetPackage.isNotBlank()) {
            AppDetectionManager.onQuizShown(currentTargetPackage)
        }
    }

    override fun onStop() {
        super.onStop()
        // Bug 5：弹窗解耦 - Activity 进入后台时立即销毁，避免用户打开 FocusCat 时
        // 仍显示拦截页（singleInstance 模式下 Activity 会残留在独立 task 中）
        // 跳转 ConfirmActivity 时不销毁（由 ConfirmActivity 接管 isQuizShowing）
        if (!isTransitioningToConfirm) {
            AppDetectionManager.onQuizDismissed()
            // 用户没有通过"选择进入"路径离开，清空进入会话
            // 防止 lastAppEnterPkg 残留导致再次打开时不弹窗
            AppDetectionManager.clearAppEnterSession()
            // 解耦：弹窗进入后台即销毁，确保不影响 FocusCat 主界面
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 仅在未跳转到 ConfirmActivity 时清除拦截状态
        // 跳转中时由 ConfirmActivity 管理 isQuizShowing，避免竞态条件
        // 注意：onStop 通常已先调用过 onQuizDismissed，此处为兜底（如进程被杀时 onStop 可能不保证执行）
        if (!isTransitioningToConfirm) {
            AppDetectionManager.onQuizDismissed()
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        /** 是否为「持续使用再次提醒」（recurring）。默认 false=首次拦截 */
        const val EXTRA_IS_RECURRING = "extra_is_recurring"
        /** recurring 场景下已使用的分钟数，用于文案展示 */
        const val EXTRA_ELAPSED_MIN = "extra_elapsed_min"
        /** 用户在「使用时长」题中选择的分钟数，透传给确认页写入临时豁免窗口 */
        const val EXTRA_SELECTED_DURATION_MIN = "extra_selected_duration_min"
    }
}

/**
 * 反思问题为空时的友好提示页
 * 避免用户看到"一闪而过突然确认页"的困惑体验
 */
@Composable
private fun EmptyQuestionsContent(
    onGoToSettings: () -> Unit,
    onClose: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SelfImprovement,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "还没有反思问题",
            color = textColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "到 FocusCat 设置 → 反思问题管理 中添加\n即可继续你的专注守护之旅",
            color = dimColor,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onGoToSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Text(
                text = "返回 FocusCat",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "稍后再说",
                color = dimColor
            )
        }
    }
}

/**
 * 反思问答界面主体内容 - 逐步展示问题，带进度指示
 * 颜色统一引用 MaterialTheme.colorScheme
 *
 * 需求 3：题目改为选择题，点击选项即完成作答；
 * 「使用时长」题以选项形式呈现（5/15/30/60 分钟），选中后进入下一步。
 */
@Composable
private fun ReflectionContent(
    state: ReflectionUiState,
    displayAppName: String,
    isRecurring: Boolean,
    elapsedMin: Int,
    onAnswerChange: (Int, String) -> Unit,
    onOptionSelect: (Int, Int) -> Unit,
    onSubmit: () -> Unit
) {
    // 当前展示到第几个问题（逐步揭示）
    // 使用 state.targetApp 作为 key：onNewIntent 加载新问题时 currentStep 自动重置为 0
    // 避免新 quiz 沿用旧 quiz 的步骤（如旧 quiz 第3题 → 新 quiz 只有2题导致越界）
    var currentStep by remember(state.targetApp) { mutableStateOf(0) }
    val totalQuestions = state.questions.size
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // 主题色引用
    val textColor = MaterialTheme.colorScheme.onBackground
    val cardColor = MaterialTheme.colorScheme.surface
    val accentColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 进度条平滑过渡：切换问题时进度从旧值动画到新值，避免蹦变
    val targetProgress = (currentStep + 1f) / totalQuestions.coerceAtLeast(1)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 400,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "reflection_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // recurring 场景小标签：让用户瞬间识别"这是再次提醒"，不必读标题
        // 修复 P1-2：原仅靠标题文字区分，缺乏瞬间识别
        if (isRecurring) {
            Text(
                text = "再次提醒",
                color = accentColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 顶部图标 - 矢量图标替代 emoji
        // 首次拦截：SelfImprovement（打坐，专注觉醒）
        // recurring：Schedule（时钟，时间到了再想想）
        Icon(
            imageVector = if (isRecurring) Icons.Filled.Schedule else Icons.Filled.SelfImprovement,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRecurring) "已经用了一阵子" else "专注时刻",
            color = textColor,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isRecurring) {
                "继续之前，再花一分钟确认下当下的状态"
            } else {
                "想清楚再进入，也是一种温柔"
            },
            color = dimColor,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 进度指示器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${minOf(currentStep + 1, totalQuestions)} / $totalQuestions",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = accentColor,
            trackColor = dimColor.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 逐步展示问题
        state.questions.forEachIndexed { index, item ->
            // 只显示到当前步骤的问题
            if (index <= currentStep) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = androidx.compose.animation.core.tween(400)
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Q${index + 1}. ${item.question.questionText}",
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (item.isChoice) {
                                // 需求 3：选择题 —— 点击即答，无需输入文字
                                item.options.forEachIndexed { optionIndex, optionText ->
                                    ChoiceOptionRow(
                                        text = optionText,
                                        selected = item.selectedOptionIndex == optionIndex,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                            onOptionSelect(index, optionIndex)
                                            // 点击后自动推进到下一题（最后题停留，等待提交）
                                            if (index < totalQuestions - 1) {
                                                coroutineScope.launch {
                                                    delay(220)
                                                    if (currentStep == index) currentStep = index + 1
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                                // 使用时长题的补充说明
                                if (item.question.questionType ==
                                    com.focusguard.app.data.local.entity.ReflectionQuestionEntity.TYPE_DURATION
                                ) {
                                    Text(
                                        text = "这段时间内不会再打扰你，时间到了会再提醒",
                                        color = dimColor,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                    )
                                }
                            } else {
                                // 开放问答（兼容历史自定义问题）
                                OutlinedTextField(
                                    value = item.answer,
                                    onValueChange = { onAnswerChange(index, it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // 用 heightIn 而非 height：supportingText 会被算入容器高度，
                                        // height 会挤压输入区；heightIn(min) 让 supportingText 在容器外渲染
                                        .heightIn(min = 100.dp),
                                    placeholder = {
                                        Text(
                                            text = item.question.placeholder,
                                            color = dimColor,
                                            fontSize = 14.sp
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentColor,
                                        unfocusedBorderColor = dimColor.copy(alpha = 0.5f),
                                        cursorColor = accentColor,
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor,
                                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
                                    ),
                                    supportingText = {
                                        Text(
                                            text = "${item.answer.length} 字",
                                            color = dimColor,
                                            fontSize = 11.sp,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                )
                            }

                            // 上一题/下一题按钮组（当前题时显示）
                            if (index == currentStep) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 上一题按钮：currentStep > 0 时显示，回退一步保留已填答案
                                    if (currentStep > 0) {
                                        TextButton(
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentStep--
                                            },
                                            // 触控目标 ≥44dp（WCAG 2.5.5），高频操作防误触
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("← 上一题", fontSize = 13.sp, color = dimColor)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(1.dp))
                                    }
                                    // 下一题按钮：已回答当前题且不是最后一题时显示
                                    // 选择题会自动推进，此按钮仅在开放问答或未作答时出现
                                    if (index < totalQuestions - 1 && item.isAnswered && !item.isChoice) {
                                        TextButton(
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (currentStep < totalQuestions - 1) {
                                                    currentStep++
                                                }
                                            },
                                            // 触控目标 ≥44dp（WCAG 2.5.5），高频操作防误触
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("下一题 →", fontSize = 13.sp, color = dimColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 错误提示 - 用 ErrorText (#6B5B3E) 替代 error (#9C7A82)：14sp 在白底达 AA 4.5:1
        state.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = com.focusguard.app.ui.theme.ErrorText,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // 进入应用按钮（必须回答所有问题后才能提交）
        val allAnswered = state.questions.isNotEmpty() && state.questions.all { it.isAnswered }
        val canSubmit = allAnswered
        // 禁用→启用 scale 平滑过渡，避免 0.95↔1.0 瞬间跳变的「啪」感
        val submitScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (canSubmit) 1f else 0.95f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 250,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            label = "submit_scale"
        )
        Button(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onSubmit()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(submitScale),
            shape = RoundedCornerShape(12.dp),
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canSubmit) accentColor else accentColor.copy(alpha = 0.5f),
                disabledContainerColor = accentColor.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "完成反思，去确认 →",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        // 按钮禁用时的引导提示
        if (!canSubmit) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请先回答所有反思问题",
                color = dimColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 底部提示
        // 首次拦截：告诉用户是哪个应用触发（信息价值高）
        // recurring：去掉冗余应用名，仅显示真实使用时长（修复 P0-2 + P2-2）
        Text(
            text = if (isRecurring && elapsedMin > 0) {
                "已使用 $elapsedMin 分钟"
            } else {
                "触发应用：$displayAppName"
            },
            color = dimColor,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 选择题选项行（需求 3）
 *
 * 点击整行即选中，选中态用蜜糖橙描边 + 淡橙底 + 对勾图标，
 * 触控目标 ≥52dp，符合移动端可点性要求。
 */
@Composable
private fun ChoiceOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(
                color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accentColor else dimColor.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = if (selected) textColor else dimColor,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选中",
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
