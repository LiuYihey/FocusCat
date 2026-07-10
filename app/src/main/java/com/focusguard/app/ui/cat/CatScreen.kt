package com.focusguard.app.ui.cat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.data.local.entity.FoodInventoryEntity
import com.focusguard.app.ui.components.AppCard
import com.focusguard.app.ui.components.AppCardTokens
import com.focusguard.app.ui.components.CatCanvas
import com.focusguard.app.ui.components.FoodCanvas
import com.focusguard.app.MainActivity

/**
 * 猫咪养成界面（默认首页）
 * 展示猫咪形象、好感度、食物库存、投喂交互、成就，并提供开始专注入口
 */
@Composable
fun CatScreen(
    onNavigateToFocus: () -> Unit,
    viewModel: CatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticFeedback = LocalHapticFeedback.current

    // 成就解锁 - 全屏庆祝弹窗
    if (state.newlyUnlockedAchievement != null) {
        AchievementCelebrationDialog(
            achievement = state.newlyUnlockedAchievement!!,
            onDismiss = { viewModel.clearAchievementPopup() }
        )
    }

    // 错误提示
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val userCat = state.userCat
        val breed = state.breed

        if (state.isLoading) {
            // 正在加载猫咪数据 - 在 Column 外层显示，确保居中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (userCat == null) {
            // 未选择猫咪 - 富空状态：沉睡猫咪插画 + 引导文案
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 沉睡猫咪矢量插画
                CatCanvas(
                    breedId = "orange",
                    state = "sleeping",
                    modifier = Modifier.size(160.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "还没有领养猫咪",
                    color = textColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "回到首页完成首次猫咪选择\n即可开启你的陪伴之旅",
                    color = dimColor,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // 保存滚动状态，便于"去投喂"跳转后自动滚动到食物库存
            val scrollState = rememberScrollState()
            // 记录食物库存标题的实际 Y 坐标，用于精确滚动定位
            var foodSectionY by remember { mutableStateOf(0) }
            // 监听 ConfirmActivity "去投喂" 一次性导航事件，自动滚动到食物库存区
            val navigateToCat by MainActivity.navigateToCatEvent.collectAsStateWithLifecycle()
            LaunchedEffect(navigateToCat) {
                if (navigateToCat) {
                    MainActivity.requestNavigateToCatConsumed()
                    // 滚动到食物库存标题的内容偏移位置
                    // foodSectionY 已存储「内容偏移量」（视觉位置 + 当时滚动偏移），可直接用作滚动目标
                    if (foodSectionY > 0) {
                        scrollState.animateScrollTo(foodSectionY)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 猫咪名字
                Text(
                    text = userCat.name,
                    color = textColor,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = breed?.displayName ?: "",
                    color = accentColor,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 开始专注入口：放在猫咪页顶部，打开 App 看到猫即可一键开始
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToFocus()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "开始专注",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 猫咪动画展示区 - 使用高质量矢量绘制
                Box(contentAlignment = Alignment.Center) {
                    CatAnimationArea(
                        animationPhase = state.animationPhase,
                        accentColor = accentColor,
                        breedId = userCat.breedId,
                        onFeedingComplete = { viewModel.onFeedingVideoCompleted() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 鼓励语（根据投喂进度变化，避免永远同一句）
                // 放置在猫咪展示区正下方、好感度卡片上方，让用户进页面立即看到陪伴感的鼓励语
                val encouragement = when {
                    userCat.totalFeedCount >= 100 -> "你已经和猫咪成为灵魂伴侣，它最懂你的每一次专注"
                    userCat.totalFeedCount >= 50 -> "猫咪已经完全信任你，每次投喂都是默契的确认"
                    userCat.totalFeedCount >= 10 -> "猫咪越来越喜欢你了，它看到你每天都在进步"
                    else -> "猫咪刚认识你，多投喂几次，它会越来越亲近你"
                }
                Text(
                    text = encouragement,
                    color = dimColor,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 好感度信息
                val title = viewModel.getAffinityTitle(userCat.totalFeedCount)
                val nextMilestone = viewModel.getNextMilestone(userCat.totalFeedCount)
                val isMax = viewModel.isMaxMilestone(userCat.totalFeedCount)
                val progress = if (nextMilestone > 0) {
                    (userCat.totalFeedCount.toFloat() / nextMilestone.toFloat()).coerceIn(0f, 1f)
                } else 1f

                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    radius = AppCardTokens.RadiusLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "好感度: ${userCat.affinityLevel}",
                                color = textColor,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "称号: $title",
                                color = accentColor,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // 进度条
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = accentColor,
                            trackColor = dimColor.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = dimColor.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isMax) {
                                "已达成最高成就，累计投喂 ${userCat.totalFeedCount} 次"
                            } else {
                                "投喂 ${userCat.totalFeedCount} / $nextMilestone 次"
                            },
                            color = dimColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 食物库存（带图标视觉锚点，便于滚动定位）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            // 修复 Bug #6：positionInRoot() 返回视觉坐标（随滚动变化），
                            // 而 animateScrollTo 期望内容偏移量。
                            // 加上当前滚动偏移得到稳定的内容偏移量（即未滚动时元素在 root 中的 Y）
                            foodSectionY = (coordinates.positionInRoot().y + scrollState.value).toInt()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pets,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "食物库存",
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (state.foodInventory.isEmpty()) {
                    // 空库存引导：直接作为圆角内容卡片，无外层灰色框，无内层白色直角框
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(AppCardTokens.RadiusLarge)
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "食物库存空空如也",
                            color = textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "添加约束应用 → 开启守护 → 完成反思\n选择退出即可获得食物奖励",
                            color = dimColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // 可操作按钮：直接跳转到约束应用 Tab
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                com.focusguard.app.MainActivity.requestNavigateToApps()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppCardTokens.RadiusLarge),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(
                                text = "去添加约束应用",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else {
                    // LazyRow 外层叠加右侧渐变遮罩，暗示"还有更多可滑动"
                    Box {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.foodInventory, key = { it.foodId }) { item ->
                                // 单次查找食物目录信息，避免重复遍历
                                val foodInfo = state.foodCatalog.find { it.foodId == item.foodId }
                                FoodItem(
                                    item = item,
                                    foodName = foodInfo?.displayName ?: item.foodId,
                                    affinityBonus = foodInfo?.affinityBonus ?: 1,
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    dimColor = dimColor,
                                    isFeeding = state.isFeeding,
                                    onFeed = { viewModel.feedCat(item.foodId) }
                                )
                            }
                        }
                        // 右侧边缘渐变遮罩：仅当食物数量 > 3 时显示，暗示可继续滑动
                        // 与食物卡片同高（130dp），叠加在 LazyRow 右侧
                        if (state.foodInventory.size > 3) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 24.dp, height = 130.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 成就列表
                Text(
                    text = "成就",
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                state.achievements.forEach { achievement ->
                    val isUnlocked = achievement.unlockedAt != null
                    // iOS 风格成就卡片：大圆角、微妙的色彩区分、解锁勾选徽章
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        containerColor = if (isUnlocked) accentColor.copy(alpha = 0.12f) else cardColor,
                        radius = 14.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 成就图标（矢量 Material Icon，替代 emoji）
                            Icon(
                                imageVector = if (isUnlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (isUnlocked) accentColor else dimColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = achievement.title,
                                    color = if (isUnlocked) accentColor else dimColor,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = achievement.description,
                                    color = if (isUnlocked) textColor else dimColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            // 解锁成就显示勾选徽章
                            if (isUnlocked) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 成就解锁全屏庆祝弹窗 - 带粒子效果和弹性动画
 */
@Composable
private fun AchievementCelebrationDialog(
    achievement: com.focusguard.app.data.local.entity.AffinityAchievementEntity,
    onDismiss: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surface
    val hapticFeedback = LocalHapticFeedback.current

    // 进场动画
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        kotlinx.coroutines.delay(100)
        visible = true
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            // 成就解锁是用户长期投入的高峰时刻，禁止点击 scrim 误关
            // 仅允许"太棒了！"按钮或返回键关闭，避免手抖错过庆祝
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 成就弹窗：不添加任何全屏背景层 / Lottie 粒子（用户硬约束）
            // 仅保留居中卡片浮于 Dialog 默认 scrim 之上，干净克制
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.fadeIn() +
                    androidx.compose.animation.scaleIn(
                        initialScale = 0.5f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                        )
                    )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 庆祝图标（矢量 Material Icon，替代 emoji）
                        Icon(
                            imageVector = Icons.Filled.Celebration,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "成就解锁！",
                            color = accentColor,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = achievement.title,
                            color = textColor,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = achievement.description,
                            color = dimColor,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(
                                text = "太棒了！",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 投喂成功后简洁的"+X 好感"反馈
 * 固定在动画区域右上角，淡入淡出，无抖动/飘升
 *
 * @param trigger 触发序号（每次投喂 +1）
 * @param affinityBonus 本次投喂获得的好感度（null 时不显示）
 * @param accentColor 主色
 */
@Composable
private fun AffinityFloatFeedback(
    trigger: Int,
    affinityBonus: Int?,
    accentColor: Color
) {
    // 控制动画可见性：trigger 变化时短暂显示
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && affinityBonus != null) {
            visible = true
            kotlinx.coroutines.delay(1200)
            visible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "affinity_float_alpha"
    )

    if (trigger > 0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = "好感度 +${affinityBonus ?: 1}",
                color = accentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .alpha(alpha)
            )
        }
    }
}

/**
 * 猫咪动画展示区
 * - eating + 视频可用：全屏播放进食视频，不叠加装饰（真实场景已足够丰富）
 * - 其他状态：图片 + 光晕/星星装饰
 * 不显示任何状态文字，仅通过右上角"好感度 +n"提示投喂结果
 */
@Composable
private fun CatAnimationArea(
    animationPhase: String,
    accentColor: Color,
    breedId: String,
    onFeedingComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 检测进食视频是否存在（同步检测，避免首次进入时异步检测导致 fallback）
    // openFd 探测比 available() 更可靠
    val eatVideoPath = "videos/eating/$breedId.mp4"
    val eatVideoExists = remember(breedId) {
        try {
            context.assets.openFd(eatVideoPath).close(); true
        } catch (e: Exception) { false }
    }
    // 检测待机视频是否存在（idle 视频模式下也不画装饰，保持白底沉浸感）
    val idleVideoPath = "videos/idle/$breedId.mp4"
    val idleVideoExists = remember(breedId) {
        try {
            context.assets.openFd(idleVideoPath).close(); true
        } catch (e: Exception) { false }
    }
    // eating 状态且视频可用 → 纯视频模式，不画装饰
    val isVideoEating = animationPhase == "eating" && eatVideoExists
    // 任何视频模式（eating 或 idle）都隐藏装饰层，避免光晕/星星叠加在纯白背景视频上破坏沉浸感
    val isVideoMode = isVideoEating || (animationPhase == "idle" && idleVideoExists)
    val hideDecoration = isVideoMode

    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    // 光晕呼吸效果（仅非视频模式使用）
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_twinkle"
    )

    // 容器：视频模式填满宽度（沉浸式，两侧无空隙），图片模式固定 280dp
    // 视频模式添加背景色：ExoPlayer 首次创建/prepare 期间避免透明区暴露底层内容，
    // 与主题背景同色，用户无感知地过渡到视频播放
    Box(
        modifier = if (isVideoMode) {
            Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.background)
        } else {
            Modifier.size(280.dp)
        },
        contentAlignment = Alignment.Center
    ) {
        // 装饰层用 AnimatedVisibility 平滑淡入淡出，避免进食瞬间硬切
        // 视频模式（eating/idle）下隐藏装饰，保持纯白背景沉浸感
        AnimatedVisibility(
            visible = !hideDecoration,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Neo-minimalism：仅保留 3 颗点缀星星（从 6 颗精简），更克制
                val starPositions = remember {
                    listOf(Offset(50f, 60f), Offset(230f, 70f), Offset(130f, 250f))
                }
                Canvas(modifier = Modifier.size(280.dp)) {
                    starPositions.forEachIndexed { index, pos ->
                        val alpha = (starTwinkle + index * 0.2f) % 1f
                        drawStar(Offset(pos.x, pos.y), 3.5f, com.focusguard.app.ui.theme.StarGold.copy(alpha = alpha * 0.5f))
                    }
                }
                // 外层光晕（呼吸效果）
                Box(
                    modifier = Modifier
                        .size((220 * glowScale).dp)
                        .background(color = accentColor.copy(alpha = glowAlpha), shape = CircleShape)
                )
                // 中层光晕
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(color = accentColor.copy(alpha = 0.08f), shape = CircleShape)
                )
            }
        }
        // 视频模式：填满容器；图片模式：固定尺寸 + 渐进过渡
        val targetCatSize = if (isVideoEating) 280.dp else 240.dp
        val catSize by animateDpAsState(
            targetValue = targetCatSize,
            animationSpec = tween(durationMillis = 400),
            label = "cat_size"
        )
        CatCanvas(
            breedId = breedId,
            state = animationPhase,
            modifier = if (isVideoMode) Modifier.fillMaxSize() else Modifier.size(catSize),
            interactive = !hideDecoration,  // 视频模式禁用触控倾斜
            useVideo = true,
            onVideoCompletion = if (animationPhase == "eating") onFeedingComplete else null
        )
    }
}

/**
 * 绘制小星星装饰
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        for (i in 0..4) {
            val angle = (i * 144 - 90).toDouble() * Math.PI / 180
            val x = center.x + (radius * Math.cos(angle)).toFloat()
            val y = center.y + (radius * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path = path, color = color)
}

/**
 * 食物卡片 - 带按压反馈
 * 使用奶油白(SurfaceVariant)背景 + 圆角裁剪食物图，避免"套娃"式嵌套框
 */
@Composable
private fun FoodItem(
    item: FoodInventoryEntity,
    foodName: String,
    affinityBonus: Int,
    accentColor: Color,
    textColor: Color,
    dimColor: Color,
    isFeeding: Boolean,
    onFeed: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.96f else 1f
    val isEmpty = item.count <= 0
    val hapticFeedback = LocalHapticFeedback.current
    // 奶油白卡片背景，与 App 其他卡片层级一致（SurfaceVariant = #FFF8E7）
    val foodCardColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .size(width = 110.dp, height = 130.dp)
            .scale(pressScale)
            .clickable(
                enabled = !isFeeding && !isEmpty,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onFeed()
                }
            ),
        shape = RoundedCornerShape(AppCardTokens.RadiusLarge),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty) foodCardColor.copy(alpha = 0.5f) else foodCardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AppCardTokens.ElevationSoft,
            pressedElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 食物图：FoodCanvas 内置径向渐变边缘柔化（与视频窗口一致），无需额外裁剪
            FoodCanvas(
                foodId = item.foodId,
                modifier = Modifier
                    .size(64.dp)
                    .scale(if (isPressed && !isEmpty) 1.15f else 1f),
                size = 64
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = foodName,
                color = if (isEmpty) dimColor else textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "x${item.count}",
                color = if (isEmpty) dimColor else accentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "+$affinityBonus 好感",
                color = dimColor,
                fontSize = 10.sp
            )
        }
    }
}
