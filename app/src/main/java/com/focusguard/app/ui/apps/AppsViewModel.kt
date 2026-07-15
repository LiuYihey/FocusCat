package com.focusguard.app.ui.apps

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.BlockedAppEntity
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.detection.AppDetectionManager
import com.focusguard.app.service.FocusGuardService
import com.focusguard.app.util.AppInfo
import com.focusguard.app.util.PermissionHelper
import com.focusguard.app.util.getInstalledApps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * 守护权限状态
 */
data class GuardPermissionStatus(
    /** 使用情况访问权限是否已授权 */
    val usageStatsGranted: Boolean = false,
    /** 悬浮窗权限是否已授权 */
    val overlayGranted: Boolean = false,
    /** 无障碍服务权限是否已授权 */
    val accessibilityGranted: Boolean = false,
    /** 电池优化白名单是否已授权 */
    val batteryOptimizationGranted: Boolean = false
) {
    /** 是否所有权限都已授权 */
    val allGranted: Boolean
        get() = usageStatsGranted && overlayGranted && accessibilityGranted && batteryOptimizationGranted
}

/**
 * 应用管理界面 UI 状态
 */
data class AppsUiState(
    /** 已安装应用列表 */
    val installedApps: List<AppInfo> = emptyList(),
    /** 是否显示添加应用对话框 */
    val showAddDialog: Boolean = false,
    /** 是否正在加载已安装应用列表 */
    val isLoading: Boolean = false,
    /** 一次性成功提示（添加/删除约束应用后触发 Snackbar） */
    val successMessage: String? = null,
    /** 守护功能是否开启 */
    val isProtectionEnabled: Boolean = false,
    /** 守护权限状态 */
    val permissionStatus: GuardPermissionStatus = GuardPermissionStatus(),
    /** 无障碍服务是否真正运行中（onServiceConnected 已回调） */
    val isAccessibilityRunning: Boolean = false,
    /** 无障碍服务在系统设置中是否已启用（但可能未运行） */
    val isAccessibilityEnabled: Boolean = false,
    /** 守护相关一次性错误消息 */
    val guardErrorMessage: String? = null
)

/**
 * 应用管理 ViewModel
 * 负责加载已拦截应用列表和已安装应用列表
 */
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val appRepository: AppRepositoryImpl,
    application: Application
) : AndroidViewModel(application) {

    /** 已拦截应用列表（Flow 形式，自动响应数据库变化） */
    val blockedApps: StateFlow<List<BlockedAppEntity>> = appRepository.getAllBlockedApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 应用管理 UI 状态 */
    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        // 初始化时加载已安装应用列表
        loadInstalledApps()

        // 初始化守护开关状态
        _uiState.update { it.copy(isProtectionEnabled = AppDetectionManager.isProtectionEnabled) }

        // 监听无障碍服务运行状态，服务崩溃/断开时 UI 能立即感知
        viewModelScope.launch {
            AppDetectionManager.isAccessibilityConnected.collect { running ->
                _uiState.update { it.copy(isAccessibilityRunning = running) }
            }
        }

        // 检查权限与守护状态
        checkPermissions()
    }

    /**
     * 切换守护开关
     * 同时启动或停止前台保活服务，确保开关状态与服务运行状态一致
     */
    fun toggleProtection() {
        val newEnabled = !_uiState.value.isProtectionEnabled
        AppDetectionManager.setProtectionEnabled(newEnabled)
        _uiState.update { it.copy(isProtectionEnabled = newEnabled) }

        val context = getApplication<Application>()
        val intent = Intent(context, FocusGuardService::class.java)
        if (newEnabled) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AppDetectionManager.setProtectionEnabled(false)
                _uiState.update {
                    it.copy(
                        isProtectionEnabled = false,
                        guardErrorMessage = "守护服务启动失败，请稍后重试或从手机设置中允许后台启动"
                    )
                }
            }
        } else {
            context.stopService(intent)
        }
    }

    /**
     * 检查权限状态
     *
     * 修复 Bug 2：原代码在"无障碍已授权 + 守护未开启"时无条件调用 autoEnableProtection()，
     * 导致用户手动关闭守护后，每次进入应用管理页或从设置返回时守护被自动重新打开。
     *
     * 新逻辑：尊重用户的手动选择，不再自动开启守护开关。
     * 仅在守护已开启时确保前台服务运行（进程被杀重启后服务可能未运行）。
     *
     * 注意：系统设置中无障碍已启用 ≠ 服务真正运行中
     * 服务可能因进程被杀、ROM 限制等原因崩溃，此时设置仍显示"已启用"但 onServiceConnected 未回调
     * isAccessibilityRunning 反映真实运行状态，isAccessibilityEnabled 反映系统设置状态
     *
     * 不再盲目调用 setAccessibilityConnected(true)：
     * isAccessibilityConnected 应只由 AppMonitorService.onServiceConnected/onUnbind 驱动，
     * 否则会掩盖"已启用但未运行"的真实故障，导致 UI 误显示"服务运行中"
     */
    fun checkPermissions() {
        val context = getApplication<Application>()
        val accessibilityGranted = PermissionHelper.checkAccessibilityPermission(context)
        val status = GuardPermissionStatus(
            usageStatsGranted = PermissionHelper.checkUsageStatsPermission(context),
            overlayGranted = PermissionHelper.checkOverlayPermission(context),
            accessibilityGranted = accessibilityGranted,
            batteryOptimizationGranted = PermissionHelper.checkBatteryOptimization(context)
        )
        _uiState.update {
            it.copy(
                permissionStatus = status,
                isAccessibilityEnabled = accessibilityGranted
            )
        }

        // 修复 Bug 2：移除"无障碍已授权 + 守护未开启 → 自动开启守护"逻辑
        // 用户手动关闭守护后不再自动重开，仅当守护已开启时确保前台服务运行
        if (AppDetectionManager.isProtectionEnabled) {
            try {
                val intent = Intent(context, FocusGuardService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // 启动失败不影响守护开关状态
            }
        }
    }

    /**
     * 页面回到前台时调用
     */
    fun onResume() {
        checkPermissions()
    }

    /**
     * 清除守护相关一次性错误消息
     */
    fun clearGuardError() {
        _uiState.update { it.copy(guardErrorMessage = null) }
    }

    /**
     * 加载已安装应用列表
     * getInstalledApps 内部已切换到 IO 调度器（P1-7），无需外层再切
     *
     * 修复迭代7 Bug #3：原代码无 try-finally，PackageManager 抛异常时 isLoading 永远不会被重置，
     * 用户看到永久转圈 loading 无法重试。改为 try-finally 保证 isLoading 在任何路径下都被重置。
     */
    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val apps = getApplication<Application>().getInstalledApps()
                _uiState.update {
                    it.copy(
                        installedApps = apps
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 某些 ROM 上 PackageManager 可能抛 SecurityException/DeadObjectException
                // 保留空列表，仅重置 loading 状态，用户可下拉重试
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 添加保护应用
     * 将应用添加到数据库，并更新拦截列表
     * @param appInfo 应用信息
     *
     * 修复 P1-1：PNG 编码（drawableToBytes）+ Room 写入是 CPU/IO 密集型操作，
     * 原实现在 viewModelScope.launch 默认 Main 调度器执行，连续添加多个应用会卡 UI 甚至 ANR。
     * 改为 withContext(Dispatchers.IO) 切到 IO 线程，仅在更新 UI 状态时回 Main。
     */
    fun addApp(appInfo: AppInfo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 将图标转换为字节数组（PNG 编码，CPU 密集）
                val iconBytes = drawableToBytes(appInfo.icon)
                // 添加到数据库（IO 密集）
                appRepository.addBlockedApp(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    iconBytes = iconBytes
                )
                // 更新拦截列表
                updateBlockedAppsInManager()
            }
            // 以下操作回 Main 线程：关闭对话框 + 提示
            hideDialog()
            // 正向反馈：与 app 的"正向反馈"理念一致
            _uiState.update { it.copy(successMessage = "已加入「${appInfo.appName}」到守护名单") }
        }
    }

    /**
     * 移除保护应用
     * @param packageName 应用包名
     */
    fun removeApp(packageName: String) {
        viewModelScope.launch {
            // 从数据库中查找对应实体并删除
            val entity = blockedApps.value.find { it.packageName == packageName }
            val removedName = entity?.appName ?: packageName
            entity?.let {
                appRepository.removeBlockedApp(it)
            }
            // 更新拦截列表
            updateBlockedAppsInManager()
            // 正向反馈：让用户确认删除已生效
            _uiState.update { it.copy(successMessage = "已移除「$removedName」的约束") }
        }
    }

    /**
     * 设置应用的启用/禁用状态（暂停拦截但保留记录）
     * 修复 B2：原 UI Switch 只改本地 state 不持久化，导致 UI 与底层拦截行为相反。
     * 现在持久化到数据库并同步到 AppDetectionManager。
     * @param packageName 应用包名
     * @param active true=启用拦截，false=暂停拦截
     */
    fun setAppActive(packageName: String, active: Boolean) {
        viewModelScope.launch {
            appRepository.setAppActive(packageName, active)
            // 同步更新拦截管理器（禁用后该包不再被拦截）
            updateBlockedAppsInManager()
            _uiState.update {
                it.copy(successMessage = if (active) "已恢复拦截" else "已暂停拦截")
            }
        }
    }

    /**
     * 显示添加应用对话框
     */
    fun showDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    /**
     * 隐藏添加应用对话框
     */
    fun hideDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    /**
     * 清除一次性成功提示（Snackbar 展示后调用）
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * 更新 AppDetectionManager 中的拦截列表
     */
    private suspend fun updateBlockedAppsInManager() {
        val activeApps = appRepository.getActiveBlockedApps()
        val packages = activeApps.map { it.packageName }.toSet()
        AppDetectionManager.updateBlockedApps(packages)
    }

    /**
     * 将 Drawable 转换为字节数组（PNG 格式）
     * @param drawable Drawable 对象
     * @return 字节数组，若 drawable 为空则返回 null
     */
    private fun drawableToBytes(drawable: Drawable?): ByteArray? {
        if (drawable == null) return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        // 关闭流并回收 Bitmap，避免内存泄漏
        stream.close()
        bitmap.recycle()
        return bytes
    }
}
