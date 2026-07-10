package com.focusguard.app.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.focusguard.app.ui.components.AppCard
import com.focusguard.app.ui.components.AppCardTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.ui.components.StatCard
import com.focusguard.app.util.formatDuration

/**
 * 统计界面
 * 显示专注时长、拦截次数、答题数据等统计信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateToFocus: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 页面标题（置于 Tab 之上，建立清晰层级）
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "数据统计",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 顶部周期选择 Tab
        PrimaryTabRow(
            selectedTabIndex = uiState.selectedPeriod.ordinal
        ) {
            StatsPeriod.entries.forEach { period ->
                Tab(
                    selected = uiState.selectedPeriod == period,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectPeriod(period)
                    },
                    text = { Text(text = period.title) }
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 空状态提示
            val isEmpty = uiState.totalFocusDuration == 0L &&
                uiState.totalBlockCount == 0 &&
                uiState.periodBlockCount == 0 &&
                uiState.periodFocusDuration == 0L
            if (isEmpty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.size(20.dp))
                    Text(
                        text = "还没有统计数据",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "开启守护并完成一次反思后\n这里会展示你的专注数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(24.dp))
                    TextButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToFocus()
                        }
                    ) {
                        Text(
                            text = "开始专注",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {

            // 周期内统计卡片：标题随周期动态变化，与 Tab 标签保持一致
            val periodTitle = when (uiState.selectedPeriod) {
                StatsPeriod.TODAY -> "今日"
                StatsPeriod.WEEK -> "近 7 天"
                StatsPeriod.MONTH -> "近 30 天"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "${periodTitle}专注",
                    value = uiState.periodFocusDuration.formatDuration(),
                    icon = Icons.Filled.Timer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "${periodTitle}拦截",
                    value = uiState.periodBlockCount.toString(),
                    icon = Icons.Filled.Block,
                    modifier = Modifier.weight(1f)
                )
            }
            // 全历史累计卡片（不随周期变化）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "总专注时长",
                    value = uiState.totalFocusDuration.formatDuration(),
                    icon = Icons.Filled.Timer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "总拦截次数",
                    value = uiState.totalBlockCount.toString(),
                    icon = Icons.Filled.Block,
                    modifier = Modifier.weight(1f)
                )
            }

            // 专注时长趋势图：仅在周/月视图展示
            // 今日视图不显示多日趋势，避免与"今日"语义矛盾
            if (uiState.selectedPeriod != StatsPeriod.TODAY) {
                val trendDaysLabel = when (uiState.selectedPeriod) {
                    StatsPeriod.WEEK -> "近 7 天"
                    StatsPeriod.MONTH -> "近 30 天"
                    StatsPeriod.TODAY -> "近 7 天"
                }
                Column(
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "$trendDaysLabel 专注时长",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    radius = AppCardTokens.RadiusLarge
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 使用 Canvas 绘制柱状图
                        TrendChart(
                            trendData = uiState.trendData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
                } // end Column wrapper for section header + card
            }

            // 各保护应用拦截次数排行（过滤 0 次应用，避免冗余条目）
            val rankedApps = remember(blockedApps) {
                blockedApps
                    .filter { it.blockCount > 0 }
                    .sortedByDescending { it.blockCount }
            }
            Column(
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "应用拦截排行",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                radius = AppCardTokens.RadiusLarge
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (rankedApps.isEmpty()) {
                        Text(
                            text = "完成反思后这里会显示各应用的拦截次数",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        rankedApps.forEachIndexed { index, app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${app.blockCount} 次",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            } // end Column wrapper for ranking section
            } // end else（非空态才展示周期卡片、趋势图与排行）
        }
    }
}

/**
 * 趋势柱状图
 * 使用 Canvas 绘制柱状图，带网格线、圆角柱子和入场动画
 * 支持点击柱子查看详细数据（日期 + 时长 + 拦截次数）
 *
 * @param trendData 趋势数据列表
 * @param modifier 修饰符
 */
@Composable
private fun TrendChart(
    trendData: List<TrendItem>,
    modifier: Modifier = Modifier
) {
    if (trendData.isEmpty()) {
        // 空数据占位：显示提示文案而非空白
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无趋势数据\n完成反思后这里会展示你的专注趋势",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val maxValue = trendData.maxOfOrNull { it.value } ?: 0L
    val safeMaxValue = if (maxValue == 0L) 1L else maxValue
    val barColor = MaterialTheme.colorScheme.primary
    val dimBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val placeholderColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val barLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 入场动画：柱子从底部生长
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationPlayed = true
    }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "trend_chart_entrance"
    )

    // 当前选中的柱子索引（点击查看详情）
    var selectedBar by remember { mutableStateOf<Int?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    // 修复 Bug #8：切换周期或数据源变化时重置选中柱子，
    // 避免旧索引在新数据上误高亮或越界导致详情卡不显示
    LaunchedEffect(trendData) {
        selectedBar = null
    }

    // 用于在 Canvas 上绘制柱顶数值
    val textMeasurer = rememberTextMeasurer()
    val barLabelStyle = TextStyle(
        fontSize = 9.sp,
        color = barLabelColor,
        fontWeight = FontWeight.Medium
    )

    Column(modifier = modifier) {
        // Y 轴最大值标注：与柱顶 label 格式统一为 "Xh Ym"，移除"单位:分钟"避免自相矛盾
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最大值: ${maxValue.formatDuration()}",
                style = MaterialTheme.typography.labelSmall,
                color = maxLabelColor
            )
            Text(
                text = "专注时长",
                style = MaterialTheme.typography.labelSmall,
                color = maxLabelColor.copy(alpha = 0.7f)
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(trendData) {
                    // 点击柱子查看详情：命中检测扩大到整个 slot（含间距），更易点中
                    detectTapGestures(
                        onTap = { offset ->
                            val barCount = trendData.size
                            if (barCount == 0) return@detectTapGestures
                            val canvasWidth = size.width.toFloat()
                            val totalSpacing = canvasWidth * 0.1f
                            val totalBarWidth = canvasWidth - totalSpacing
                            val barWidth = totalBarWidth / barCount * 0.7f
                            val spacing = (canvasWidth - barWidth * barCount) / (barCount + 1)
                            val slotWidth = barWidth + spacing

                            val slotIndex = ((offset.x - spacing / 2f) / slotWidth)
                                .toInt()
                                .coerceIn(0, barCount - 1)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedBar = if (selectedBar == slotIndex) null else slotIndex
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barAreaHeight = canvasHeight * 0.85f

            // 绘制水平网格线（4 条）
            for (i in 0..3) {
                val y = barAreaHeight * (1f - i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
            }

            val barCount = trendData.size
            val totalSpacing = canvasWidth * 0.1f
            val totalBarWidth = canvasWidth - totalSpacing
            val barWidth = totalBarWidth / barCount * 0.7f
            val spacing = (canvasWidth - barWidth * barCount) / (barCount + 1)

            // 绘制每个柱子
            trendData.forEachIndexed { index, item ->
                val barHeight = if (item.value > 0) {
                    (item.value.toFloat() / safeMaxValue.toFloat()) * barAreaHeight * animationProgress
                } else {
                    0f
                }

                val x = spacing + index * (barWidth + spacing)
                val y = barAreaHeight - barHeight

                if (barHeight > 0) {
                    // 选中的柱子用完整 primary 突出，未选中用半透明
                    val color = if (selectedBar == index) barColor else dimBarColor
                    // 圆角柱子
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 4f, barWidth / 4f)
                    )
                    // 柱顶数值标签：动画接近完成时显示，避免与生长动画打架
                    // 格式与最大值标注统一：>=1h 显示 "Xh Ym"，整点显示 "Xh"，<1h 显示 "Xm"
                    if (animationProgress > 0.6f) {
                        val totalMinutes = item.value / 60000
                        val label = when {
                            totalMinutes >= 60 -> {
                                val h = totalMinutes / 60
                                val m = totalMinutes % 60
                                if (m == 0L) "${h}h" else "${h}h${m}m"
                            }
                            totalMinutes > 0 -> "${totalMinutes}m"
                            else -> "0"
                        }
                        val measured = textMeasurer.measure(label, style = barLabelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x + barWidth / 2f - measured.size.width / 2f,
                                (y - measured.size.height - 2f).coerceAtLeast(0f)
                            )
                        )
                    }
                } else {
                    // 无数据时显示占位线
                    drawRect(
                        color = placeholderColor,
                        topLeft = Offset(x, barAreaHeight - 2f),
                        size = Size(barWidth, 2f)
                    )
                }
            }
        }

        // 显示日期标签：数据量大时（如 30 天）仅显示每 5 个，避免重叠
        val labelStride = if (trendData.size > 14) 5 else 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            trendData.forEachIndexed { index, item ->
                val showLabel = index % labelStride == 0 ||
                    index == trendData.lastIndex ||
                    selectedBar == index
                Text(
                    text = if (showLabel) item.label else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedBar == index)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedBar == index) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // 选中柱子的详情卡片（带入场/出场动画）
        AnimatedVisibility(
            visible = selectedBar != null && selectedBar in trendData.indices,
            enter = fadeIn(tween(200)) + expandVertically(tween(250)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
        ) {
            val index = selectedBar
            if (index != null && index in trendData.indices) {
                val item = trendData[index]
                Column {
                    Spacer(modifier = Modifier.size(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "专注 ${item.value.formatDuration()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "再次点击柱子可取消选中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
