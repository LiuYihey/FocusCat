package com.focusguard.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.data.repository.QuizRepositoryImpl
import com.focusguard.app.data.repository.StatsRepositoryImpl
import com.focusguard.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 权限状态数据类
 */
data class SettingsPermissionStatus(
    /** 使用情况访问权限是否已授权 */
    val usageStatsGranted: Boolean = false,
    /** 悬浮窗权限是否已授权 */
    val overlayGranted: Boolean = false,
    /** 无障碍服务权限是否已授权 */
    val accessibilityGranted: Boolean = false
)

/**
 * 设置界面 UI 状态
 */
data class SettingsUiState(
    /** 权限状态 */
    val permissionStatus: SettingsPermissionStatus = SettingsPermissionStatus(),
    /** 开机自启动是否开启 */
    val bootStartup: Boolean = true,
    /** 守护通知是否开启 */
    val notificationEnabled: Boolean = true,
    /** 「持续使用再次提醒」间隔（分钟），0=关闭。默认 30 */
    val recurringIntervalMin: Int = 30,
    /** 导出的数据文件路径 */
    val exportedFilePath: String? = null,
    /** 错误提示（导出失败等），UI 层据此展示反馈 */
    val errorMessage: String? = null,
    /** 一次性成功提示（导出成功等），UI 层据此展示 Snackbar */
    val successMessage: String? = null
)

/**
 * 设置 ViewModel
 * 负责管理权限状态、开关设置和数据导出
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appRepository: AppRepositoryImpl,
    private val quizRepository: QuizRepositoryImpl,
    private val statsRepository: StatsRepositoryImpl,
    application: Application
) : AndroidViewModel(application) {

    /** SharedPreferences 用于持久化设置 */
    private val sharedPreferences = application.getSharedPreferences("focus_guard_settings", android.content.Context.MODE_PRIVATE)
    /** 与 AppDetectionManager 共用的 prefs，用于读写「再次提醒间隔」 */
    private val guardPrefs = application.getSharedPreferences("focus_guard_prefs", android.content.Context.MODE_PRIVATE)

    /** 设置 UI 状态 */
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 加载持久化的设置
        val savedInterval = guardPrefs.getInt(KEY_RECURRING_INTERVAL_MIN, DEFAULT_RECURRING_INTERVAL_MIN)
        _uiState.update {
            it.copy(
                bootStartup = sharedPreferences.getBoolean(KEY_BOOT_STARTUP, true),
                notificationEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATION_ENABLED, true),
                recurringIntervalMin = savedInterval
            )
        }
        // 同步到 AppDetectionManager（防止其尚未 init 时内存值不一致）
        com.focusguard.app.detection.AppDetectionManager.setRecurringInterval(savedInterval)
        // 检查权限
        checkPermissions()
    }

    /**
     * 检查权限状态
     */
    fun checkPermissions() {
        val context = getApplication<Application>()
        val status = SettingsPermissionStatus(
            usageStatsGranted = PermissionHelper.checkUsageStatsPermission(context),
            overlayGranted = PermissionHelper.checkOverlayPermission(context),
            accessibilityGranted = PermissionHelper.checkAccessibilityPermission(context)
        )
        _uiState.update { it.copy(permissionStatus = status) }
    }

    /**
     * 页面回到前台时调用
     * 重新检查权限状态，确保用户从系统设置授权后状态能及时更新
     */
    fun onResume() {
        checkPermissions()
    }

    /**
     * 切换开机自启动开关
     */
    fun toggleBootStartup() {
        val newValue = !_uiState.value.bootStartup
        sharedPreferences.edit().putBoolean(KEY_BOOT_STARTUP, newValue).apply()
        _uiState.update { it.copy(bootStartup = newValue) }
    }

    /**
     * 切换守护通知开关
     */
    fun toggleNotification() {
        val newValue = !_uiState.value.notificationEnabled
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, newValue).apply()
        _uiState.update { it.copy(notificationEnabled = newValue) }
    }

    /**
     * 切换「持续使用再次提醒」开关
     * - 开启时恢复到上次保存的非零间隔（若无则默认 30 分钟）
     * - 关闭时置 0，但保留上次非零值到独立 key，便于下次开启恢复
     *
     * 修复 P1-1：原实现关闭再开启会丢失用户自定义值，违背"暂停而非重置"语义
     */
    fun toggleRecurringReminder() {
        val current = _uiState.value.recurringIntervalMin
        if (current > 0) {
            // 关闭前记住当前值
            guardPrefs.edit().putInt(KEY_LAST_RECURRING_INTERVAL_MIN, current).apply()
            setRecurringInterval(0)
        } else {
            // 开启：恢复上次非零值，无则用默认
            val restored = guardPrefs.getInt(KEY_LAST_RECURRING_INTERVAL_MIN, DEFAULT_RECURRING_INTERVAL_MIN)
            setRecurringInterval(restored)
        }
    }

    /**
     * 设置「持续使用再次提醒」间隔
     * 持久化到 focus_guard_prefs 并同步到 AppDetectionManager
     * @param minutes 间隔分钟数，0=关闭，范围 [0, 120]
     *
     * 非零值同时写入 KEY_LAST_RECURRING_INTERVAL_MIN，保证关闭再开启时能恢复用户期望值。
     */
    fun setRecurringInterval(minutes: Int) {
        val safe = minutes.coerceIn(0, 120)
        com.focusguard.app.detection.AppDetectionManager.setRecurringInterval(safe)
        if (safe > 0) {
            guardPrefs.edit().putInt(KEY_LAST_RECURRING_INTERVAL_MIN, safe).apply()
        }
        _uiState.update { it.copy(recurringIntervalMin = safe) }
    }

    /**
     * 导出数据为 JSON 格式
     * 将拦截应用、答题历史、专注会话数据导出为 JSON 文件
     */
    fun exportData() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val exportData = JSONObject()

                // 导出拦截应用列表
                val blockedApps = appRepository.getActiveBlockedApps()
                val appsArray = JSONArray()
                blockedApps.forEach { app ->
                    val appJson = JSONObject()
                    appJson.put("packageName", app.packageName)
                    appJson.put("appName", app.appName)
                    appJson.put("addedAt", app.addedAt)
                    appJson.put("isActive", app.isActive)
                    appJson.put("blockCount", app.blockCount)
                    appsArray.put(appJson)
                }
                exportData.put("blockedApps", appsArray)

                // 导出答题统计
                val quizStats = quizRepository.getQuizStats()
                val quizJson = JSONObject()
                quizJson.put("correctCount", quizStats.correctCount)
                quizJson.put("totalCount", quizStats.totalCount)
                exportData.put("quizStats", quizJson)

                // 导出专注统计
                val totalFocusTime = statsRepository.getTotalStats()
                val statsJson = JSONObject()
                statsJson.put("totalFocusTimeSeconds", totalFocusTime)
                exportData.put("focusStats", statsJson)

                // 写入文件
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val exportFile = File(exportDir, "focusguard_export_${System.currentTimeMillis()}.json")
                FileOutputStream(exportFile).use { fos ->
                    fos.write(exportData.toString(2).toByteArray())
                }

                _uiState.update {
                    it.copy(
                        exportedFilePath = exportFile.absolutePath
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程取消必须重新抛出，保证结构化并发正确传播
                // （ViewModel 销毁时不应走错误处理路径）
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "导出失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    /**
     * 清除导出文件路径
     */
    fun clearExportedFile() {
        _uiState.update { it.copy(exportedFilePath = null) }
    }

    /**
     * 清除错误提示
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 清除一次性成功提示（Snackbar 展示后调用）
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    companion object {
        /** 开机自启动设置键 */
        private const val KEY_BOOT_STARTUP = "boot_startup"
        /** 守护通知设置键 */
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        /** 「再次提醒间隔」设置键（与 AppDetectionManager 共用 focus_guard_prefs） */
        private const val KEY_RECURRING_INTERVAL_MIN = "recurring_interval_min"
        /** 上次非零间隔（用于关闭再开启时恢复用户自定义值，修复 P1-1） */
        private const val KEY_LAST_RECURRING_INTERVAL_MIN = "last_recurring_interval_min"
        /** 「再次提醒间隔」默认值（分钟） */
        private const val DEFAULT_RECURRING_INTERVAL_MIN = 30
    }
}
