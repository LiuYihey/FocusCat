package com.focusguard.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.BuildConfig
import java.io.File

/**
 * 设置界面 — iOS Settings 风格
 *
 * 设计语言：
 * - 分组列表（Grouped Inset List），每组用 Surface(surfaceVariant) + 14dp 圆角
 * - 组内行高 56dp，水平 padding 16dp
 * - 组间 24dp 间距
 * - 无阴影（iOS Settings 风格）
 * - 开关/箭头右对齐
 */
@Composable
fun SettingsScreen(
    onNavigateToReflectionQuestions: () -> Unit = {},
    onNavigateToCatSwitch: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // 监听生命周期，ON_RESUME 时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // 导出成功的正向反馈（一次性 Snackbar）
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccess()
        }
    }

    // 当导出文件路径变化时，触发分享；成功后才提示用户
    LaunchedEffect(uiState.exportedFilePath) {
        uiState.exportedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "导出数据"))
                    snackbarHostState.showSnackbar("已打开分享窗口，请选择保存位置")
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "未找到可分享的应用", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "分享失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
            }
            viewModel.clearExportedFile()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── 页面标题（headlineSmall，更宽裕的上边距） ──
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.size(24.dp))

            // ── 错误提示 ──
            uiState.errorMessage?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearError() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(24.dp))
            }

            // ── 后台保活引导 ──
            KeepAliveGuideCard()

            Spacer(modifier = Modifier.size(24.dp))

            // ── 通用设置 ──
            SectionHeader(title = "通用设置")

            IosSettingsGroup {
                // 切换猫咪（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.Pets,
                    title = "切换猫咪",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToCatSwitch()
                    }
                )
                IosDivider()
                // 开机自启动
                IosSwitchRow(
                    icon = Icons.Filled.PowerSettingsNew,
                    title = "开机自启动",
                    checked = uiState.bootStartup,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleBootStartup()
                    }
                )
                IosDivider()
                // 守护通知
                IosSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "守护通知",
                    checked = uiState.notificationEnabled,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleNotification()
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // ── 持续使用再次提醒（独立分组） ──
            RecurringReminderGroup(
                intervalMin = uiState.recurringIntervalMin,
                onToggle = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleRecurringReminder()
                },
                onIntervalChange = { viewModel.setRecurringInterval(it) }
            )

            Spacer(modifier = Modifier.size(24.dp))

            // ── 自定义与数据 ──
            SectionHeader(title = "自定义与数据")

            IosSettingsGroup {
                // 反思问题管理（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.Edit,
                    title = "反思问题管理",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToReflectionQuestions()
                    }
                )
                IosDivider()
                // 导出数据（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.Upload,
                    title = "导出数据",
                    onClick = { viewModel.exportData() }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // ── 关于（居中 App 信息） ──
            AboutSection()

            Spacer(modifier = Modifier.size(48.dp))
        }

        // 导出成功/失败的一次性反馈，置于页面底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ──────────────────────────────────────────────
// iOS Style Shared Components
// ──────────────────────────────────────────────

/**
 * iOS Settings 分组标题（titleSmall，灰字）
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * iOS Settings 分组容器
 * - surfaceVariant 背景
 * - 14dp 圆角
 * - 无阴影
 */
@Composable
private fun IosSettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        // Surface 内部为 Box 布局，多个子项会堆叠；用 Column 包裹确保垂直排列
        Column {
            content()
        }
    }
}

/**
 * iOS 风格分割线 — 0.5dp，alpha 0.1
 */
@Composable
private fun IosDivider() {
    HorizontalDivider(
        modifier = Modifier.alpha(0.1f),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * iOS 导航行
 * - 左侧图标 (20dp, primary alpha 0.7)
 * - 中间 bodyLarge 标题
 * - 右侧 chevron (14dp, onSurfaceVariant)
 * - 行高 56dp，水平 padding 16dp
 */
@Composable
private fun IosNavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * iOS 开关行
 * - 左侧图标 (20dp, primary alpha 0.7)
 * - 中间 bodyLarge 标题
 * - 右侧 Material3 Switch
 * - 行高 56dp，水平 padding 16dp
 */
@Composable
private fun IosSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange()
            }
        )
    }
}

/**
 * 持续使用再次提醒 — 独立 iOS 分组
 * 包含开关行 + 展开的间隔输入框
 */
@Composable
private fun RecurringReminderGroup(
    intervalMin: Int,
    onToggle: () -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    val enabled = intervalMin > 0
    val accentColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hapticFeedback = LocalHapticFeedback.current

    // 输入框文本：开启时显示当前间隔，关闭时为空
    var inputText by remember(intervalMin) {
        mutableStateOf(if (enabled) intervalMin.toString() else "")
    }
    var inputError by remember { mutableStateOf(false) }

    IosSettingsGroup {
        // 主行：图标 + 标题 + Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "持续使用再次提醒",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Switch(
                checked = enabled,
                onCheckedChange = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }
            )
        }

        // 展开的输入区域
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + androidx.compose.animation.expandVertically(),
            exit = fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "再次提醒间隔（5-120 分钟）",
                    style = MaterialTheme.typography.bodySmall,
                    color = dimColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { text ->
                            // 只允许数字
                            val filtered = text.filter { it.isDigit() }
                            inputText = filtered
                            val num = filtered.toIntOrNull()
                            if (num != null && num in 5..120) {
                                inputError = false
                                if (num != intervalMin) {
                                    onIntervalChange(num)
                                }
                            } else if (filtered.isNotEmpty()) {
                                inputError = true
                            } else {
                                inputError = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = inputError,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        supportingText = {
                            if (inputError) {
                                Text(
                                    text = "请输入 5-120 之间的数字",
                                    color = com.focusguard.app.ui.theme.ErrorText
                                )
                            } else {
                                Text(text = "范围 5-120 分钟，0 = 关闭")
                            }
                        },
                        trailingIcon = {
                            Text(
                                text = "分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = dimColor
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * 后台保活引导 — 可折叠卡片
 * 指导用户完成电池优化白名单、自启动、锁定后台等设置，确保守护服务稳定运行
 */
@Composable
private fun KeepAliveGuideCard() {
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val accentColor = Color(0xFFF59E0B)
    val guideSteps = listOf(
        "电池优化白名单" to "在系统设置中将「专注猫」加入电池优化白名单，避免省电策略杀死后台服务。",
        "允许自启动" to "部分厂商需要单独开启「自启动」权限，否则手机重启后守护不会自动恢复。",
        "锁定后台任务" to "进入多任务界面，下拉或长按专注猫卡片，选择「锁定」，防止被一键清理。",
        "关闭深度休眠" to "在设置 - 电池 - 应用耗电管理中，关闭「深度休眠」或「睡眠模式」。",
        "保持通知开启" to "开启「守护通知」开关，利用前台服务通知提升系统保活优先级。"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                expanded = !expanded
            }
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryFull,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "后台保活引导",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "守护服务被杀？点这里查看设置方法",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.size(16.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    guideSteps.forEachIndexed { index, (title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accentColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.size(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 关于区域 — 居中 App 信息
 * - App 图标 48dp（使用 Pets 图标占位）
 * - App 名称 titleMedium
 * - 版本号 bodySmall alpha 0.5
 * - 版权 labelSmall alpha 0.5
 */
@Composable
private fun AboutSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App 图标
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        // App 名称
        Text(
            text = "专注猫",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.size(4.dp))

        // 版本号
        Text(
            text = "版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}${if (BuildConfig.DEBUG) " · debug" else ""})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.size(16.dp))

        // 版权/致谢
        Text(
            text = "用温暖守护你的专注",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
