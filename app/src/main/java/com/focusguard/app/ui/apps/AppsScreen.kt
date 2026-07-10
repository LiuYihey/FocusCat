package com.focusguard.app.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.ui.components.pressScale
import com.focusguard.app.ui.theme.Success
import com.focusguard.app.ui.theme.Warning
import com.focusguard.app.util.AppInfo
import com.focusguard.app.util.PermissionHelper
import com.focusguard.app.util.toImageBitmap

/**
 * 应用管理界面
 * 显示守护状态、链路诊断、已添加的保护应用列表，支持添加和删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: AppsViewModel = hiltViewModel()
) {
    // 观察已拦截应用列表和 UI 状态
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccess()
        }
    }

    // 守护错误提示
    LaunchedEffect(uiState.guardErrorMessage) {
        uiState.guardErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearGuardError()
        }
    }

    // 页面回到前台时刷新权限与守护状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 是否所有链路就绪
    val isGuardReady = uiState.isProtectionEnabled &&
        uiState.permissionStatus.allGranted &&
        uiState.isAccessibilityRunning &&
        blockedApps.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "约束应用",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.showDialog()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加约束应用",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // === 守护状态卡片 ===
            GuardStatusCard(
                isProtectionEnabled = uiState.isProtectionEnabled,
                isReady = isGuardReady,
                onToggle = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleProtection()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === 链路诊断 ===
            GuardDiagnosticCard(
                isProtectionEnabled = uiState.isProtectionEnabled,
                permissionStatus = uiState.permissionStatus,
                isAccessibilityRunning = uiState.isAccessibilityRunning,
                isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                blockedAppsCount = blockedApps.size,
                onRequestPermission = { permission ->
                    when (permission) {
                        GuardDiagnosticItem.USAGE_STATS ->
                            PermissionHelper.requestUsageStatsPermission(context)
                        GuardDiagnosticItem.OVERLAY ->
                            PermissionHelper.requestOverlayPermission(context)
                        GuardDiagnosticItem.ACCESSIBILITY ->
                            PermissionHelper.requestAccessibilityPermission(context)
                        GuardDiagnosticItem.BATTERY ->
                            PermissionHelper.requestBatteryOptimization(context)
                        GuardDiagnosticItem.APPS ->
                            viewModel.showDialog()
                        else -> { /* 其他项无跳转 */ }
                    }
                    viewModel.checkPermissions()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (blockedApps.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "还没有添加约束应用",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "把容易让你分心的应用加进来\n每次打开都会先弹反思问答",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.showDialog()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "添加约束应用",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // 已拦截应用列表 - iOS 风格分组列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已保护的 App",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${blockedApps.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 分组列表
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        blockedApps.forEachIndexed { index, app ->
                            BlockedAppItem(
                                appName = app.appName,
                                packageName = app.packageName,
                                blockCount = app.blockCount,
                                iconBytes = app.iconBytes,
                                isActive = app.isActive,
                                onToggleActive = {
                                    viewModel.setAppActive(app.packageName, it)
                                },
                                onDelete = {
                                    viewModel.removeApp(app.packageName)
                                },
                                isLast = index == blockedApps.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加应用对话框（使用 ModalBottomSheet）
    if (uiState.showAddDialog) {
        val sheetState = rememberModalBottomSheetState()
        var searchQuery by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { viewModel.hideDialog() },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "选择应用",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 搜索框 - iOS 风格
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = {
                        Text(
                            "搜索应用",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    maxLines = 1,
                    minLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // 已安装应用列表（过滤掉已添加的应用 + 按搜索关键词过滤）
                    val existingPackages = remember(blockedApps) {
                        blockedApps.map { it.packageName }.toSet()
                    }
                    val availableApps = remember(searchQuery, existingPackages, uiState.installedApps) {
                        uiState.installedApps.filter { appInfo ->
                            appInfo.packageName !in existingPackages &&
                                (searchQuery.isBlank() ||
                                    appInfo.appName.contains(searchQuery, ignoreCase = true) ||
                                    appInfo.packageName.contains(searchQuery, ignoreCase = true))
                        }
                    }

                    if (availableApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "未找到匹配的应用" else "没有可添加的应用",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableApps.forEach { appInfo ->
                                AppSelectionItem(
                                    appInfo = appInfo,
                                    onClick = { viewModel.addApp(appInfo) }
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
 * 守护状态卡片
 */
@Composable
private fun GuardStatusCard(
    isProtectionEnabled: Boolean,
    isReady: Boolean,
    onToggle: () -> Unit
) {
    val statusColor = if (isReady) Success else Warning
    val statusIcon = if (isReady) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val statusText = when {
        isReady -> "守护中"
        !isProtectionEnabled -> "守护已关闭"
        else -> "守护未就绪"
    }
    val statusDesc = when {
        isReady -> "正在保护你的专注时间"
        !isProtectionEnabled -> "开启后才会拦截约束应用"
        else -> "请按下方诊断完成必要设置"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isProtectionEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

/**
 * 链路诊断项类型
 */
enum class GuardDiagnosticItem {
    PROTECTION,
    USAGE_STATS,
    OVERLAY,
    ACCESSIBILITY,
    BATTERY,
    APPS
}

/**
 * 链路诊断卡片
 * @param isAccessibilityEnabled 系统设置中无障碍是否已启用
 * @param isAccessibilityRunning 无障碍服务是否真正运行中（onServiceConnected 已回调）
 */
@Composable
private fun GuardDiagnosticCard(
    isProtectionEnabled: Boolean,
    permissionStatus: GuardPermissionStatus,
    isAccessibilityRunning: Boolean,
    isAccessibilityEnabled: Boolean,
    blockedAppsCount: Int,
    onRequestPermission: (GuardDiagnosticItem) -> Unit
) {
    val items = listOf(
        GuardDiagnosticItem.PROTECTION to isProtectionEnabled,
        GuardDiagnosticItem.USAGE_STATS to permissionStatus.usageStatsGranted,
        GuardDiagnosticItem.OVERLAY to permissionStatus.overlayGranted,
        GuardDiagnosticItem.ACCESSIBILITY to (permissionStatus.accessibilityGranted && isAccessibilityRunning),
        GuardDiagnosticItem.BATTERY to permissionStatus.batteryOptimizationGranted,
        GuardDiagnosticItem.APPS to (blockedAppsCount > 0)
    )
    val allReady = items.all { it.second }

    // 无障碍"已启用但未运行"的特殊状态：需要引导用户重启服务
    val accessibilityEnabledNotRunning = isAccessibilityEnabled && !isAccessibilityRunning

    if (allReady) {
        // 全部就绪：绿色药丸提示
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Success.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "守护链路已就绪，正在保护 $blockedAppsCount 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = Success,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        // 有未就绪项：列出问题
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "链路诊断",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                items.forEach { (item, ok) ->
                    DiagnosticRow(
                        item = item,
                        isOk = ok,
                        onClick = { onRequestPermission(item) },
                        accessibilityEnabledNotRunning = accessibilityEnabledNotRunning
                    )
                }
                // 提示用户：授权后返回本页会自动检测
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "提示：在系统设置中授权后返回本页，状态会自动更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }

        // 无障碍"已启用但未运行"的特殊提示卡片
        // 服务可能因进程被杀、ROM 限制等原因崩溃，系统设置仍显示"已启用"但服务实际不工作
        // 引导用户到无障碍设置页关闭再开启，触发系统重新绑定服务
        if (accessibilityEnabledNotRunning) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Warning.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "无障碍服务需要重启",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "服务可能在退出应用后被系统暂停，需要在设置中关闭再重新开启即可恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = { onRequestPermission(GuardDiagnosticItem.ACCESSIBILITY) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Warning
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "去重启无障碍服务",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单条诊断项
 * @param accessibilityEnabledNotRunning 仅对 ACCESSIBILITY 项有效：true=已启用但未运行，false=未启用
 */
@Composable
private fun DiagnosticRow(
    item: GuardDiagnosticItem,
    isOk: Boolean,
    onClick: () -> Unit,
    accessibilityEnabledNotRunning: Boolean = false
) {
    val (label, actionLabel) = when (item) {
        GuardDiagnosticItem.PROTECTION -> "守护总开关" to "开启"
        GuardDiagnosticItem.USAGE_STATS -> "使用情况访问权限" to "去授权"
        GuardDiagnosticItem.OVERLAY -> "悬浮窗权限" to "去授权"
        GuardDiagnosticItem.ACCESSIBILITY -> if (accessibilityEnabledNotRunning) {
            "无障碍服务已启用但未运行" to "去重启"
        } else {
            "无障碍服务运行中" to "去授权"
        }
        GuardDiagnosticItem.BATTERY -> "电池优化白名单" to "去设置"
        GuardDiagnosticItem.APPS -> "已添加约束应用" to "去添加"
    }
    val icon = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val tint = if (isOk) Success else Warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (!isOk) {
            TextButton(
                onClick = onClick,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 已拦截应用列表项 - iOS 风格分组行
 */
@Composable
private fun BlockedAppItem(
    appName: String,
    packageName: String,
    blockCount: Int,
    iconBytes: ByteArray?,
    isActive: Boolean,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    isLast: Boolean = false
) {
    val iconBitmap = remember(packageName) { iconBytes.toImageBitmap() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap,
                    contentDescription = appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isActive) 1f else 0.4f
                    )
                )
                // 仅显示拦截次数，不再显示包名（避免长包名换行挤压数据）
                if (blockCount > 0) {
                    Text(
                        text = "拦截 $blockCount 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isActive,
                onCheckedChange = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleActive(it)
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showDeleteDialog = true
            }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 70.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("移除约束") },
            text = { Text("确定要移除「$appName」的约束吗？") },
            confirmButton = {
                TextButton(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 应用选择列表项 - iOS 风格
 */
@Composable
private fun AppSelectionItem(
    appInfo: AppInfo,
    onClick: () -> Unit
) {
    val iconBitmap = remember(appInfo.packageName) {
        appInfo.icon?.toImageBitmap()
    }
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap,
                contentDescription = appInfo.appName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
