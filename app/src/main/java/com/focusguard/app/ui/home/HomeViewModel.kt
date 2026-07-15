package com.focusguard.app.ui.home

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.CatCatalogEntity
import com.focusguard.app.data.local.entity.UserCatEntity
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.data.repository.CatRepository
import com.focusguard.app.data.repository.QuizRepositoryImpl
import com.focusguard.app.data.repository.StatsRepositoryImpl
import com.focusguard.app.detection.AppDetectionManager
import com.focusguard.app.service.FocusGuardService
import com.focusguard.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Calendar
import javax.inject.Inject

/**
 * 权限状态数据类
 */
data class PermissionStatus(
    /** 使用情况访问权限是否已授权 */
    val usageStatsGranted: Boolean = false,
    /** 悬浮窗权限是否已授权 */
    val overlayGranted: Boolean = false,
    /** 无障碍服务权限是否已授权 */
    val accessibilityGranted: Boolean = false,
    /** 电池优化白名单是否已授权（避免系统杀死无障碍服务） */
    val batteryOptimizationGranted: Boolean = false
) {
    /** 是否所有权限都已授权 */
    val allGranted: Boolean
        get() = usageStatsGranted && overlayGranted && accessibilityGranted && batteryOptimizationGranted
}

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    /** 今日专注时长（毫秒） */
    val todayFocusDuration: Long = 0L,
    /** 今日拦截次数（一次拦截即一次反思） */
    val todayBlockCount: Int = 0,
    /** 已保护应用数 */
    val protectedAppsCount: Int = 0,
    /** 守护功能是否开启 */
    val isProtectionEnabled: Boolean = false,
    /** 权限状态 */
    val permissionStatus: PermissionStatus = PermissionStatus(),
    /** 无障碍服务是否真正运行中（区别于"是否在设置中启用"），默认 false 避免误导用户以为服务已运行 */
    val isAccessibilityRunning: Boolean = false,
    /** 用户猫咪（null 表示尚未选择或正在加载） */
    val userCat: UserCatEntity? = null,
    /** 猫咪品种信息（用于首页预览的品种配色和名称） */
    val breed: CatCatalogEntity? = null,
    /** 一次性错误消息（用于 Snackbar 提示） */
    val errorMessage: String? = null,
    /** 猫咪数据是否正在加载（区分"加载中"与"真空态"，避免已选猫用户被"还没有猫咪"误吓） */
    val isCatLoading: Boolean = true
)

/**
 * 首页 ViewModel
 * 负责加载首页所需的统计数据和权限状态
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appRepository: AppRepositoryImpl,
    private val quizRepository: QuizRepositoryImpl,
    private val statsRepository: StatsRepositoryImpl,
    private val catRepository: CatRepository,
    application: Application
) : AndroidViewModel(application) {

    /** 首页 UI 状态 */
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 数据收集 Job 引用，用于在重新刷新前取消旧的收集器，避免重复收集 */
    private var dataCollectionJob: Job? = null

    init {
        // 初始化时加载守护开关状态
        _uiState.update { it.copy(isProtectionEnabled = AppDetectionManager.isProtectionEnabled) }
        // 监听无障碍服务运行状态，服务崩溃/断开时 UI 能立即感知
        viewModelScope.launch {
            AppDetectionManager.isAccessibilityConnected.collect { running ->
                _uiState.update { it.copy(isAccessibilityRunning = running) }
            }
        }
        // 刷新数据
        refreshData()
        // 检查权限
        checkPermissions()
    }

    /**
     * 切换守护开关
     * 同时启动或停止前台保活服务，确保开关状态与服务运行状态一致
     * 启动失败时回滚开关状态并通过 errorMessage 提示用户
     */
    fun toggleProtection() {
        val newEnabled = !_uiState.value.isProtectionEnabled
        AppDetectionManager.setProtectionEnabled(newEnabled)
        _uiState.update { it.copy(isProtectionEnabled = newEnabled) }

        // 根据开关状态启动或停止前台服务
        val context = getApplication<Application>()
        val intent = Intent(context, FocusGuardService::class.java)
        if (newEnabled) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+ 后台启动前台服务受限，回滚开关状态并提示用户
                AppDetectionManager.setProtectionEnabled(false)
                _uiState.update {
                    it.copy(
                        isProtectionEnabled = false,
                        errorMessage = "守护服务启动失败，请稍后重试或从手机设置中允许后台启动"
                    )
                }
            }
        } else {
            context.stopService(intent)
        }
    }

    /** 清除一次性错误消息 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 刷新首页数据
     * 加载今日专注时长、拦截次数、答题正确率、已保护应用数
     * 使用 Job 引用管理，调用前取消旧 Job 避免重复收集 Flow
     */
    fun refreshData() {
        // 取消旧的收集器，避免多次调用导致重复收集
        dataCollectionJob?.cancel()
        dataCollectionJob = viewModelScope.launch {
            val startOfDay = getStartOfDay()

            // 修复迭代7 Bug #2：使用 supervisorScope 隔离子协程
            // 原代码 4 个子 launch 共享普通 Job 父，任一抛异常会取消父 Job，
            // 导致其他兄弟协程（猫咪数据、专注统计等）全部被取消，首页数据同时失效。
            // supervisorScope 下子协程异常独立传播，不影响兄弟协程。
            supervisorScope {
                // 收集今日专注会话数据
                launch {
                    try {
                        statsRepository.getTodayStats(startOfDay).collect { sessions ->
                            val totalDurationMs = sessions.sumOf { it.durationSeconds * 1000L }
                            _uiState.update { it.copy(todayFocusDuration = totalDurationMs) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 单项数据收集失败不影响其他数据
                    }
                }

                // 收集今日答题历史，统计拦截次数
                launch {
                    try {
                        quizRepository.getTodayQuizHistory(startOfDay).collect { history ->
                            _uiState.update { it.copy(todayBlockCount = history.size) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 单项数据收集失败不影响其他数据
                    }
                }

                // 获取已保护应用数
                launch {
                    try {
                        val activeApps = appRepository.getActiveBlockedApps()
                        _uiState.update { it.copy(protectedAppsCount = activeApps.size) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 单项查询失败不影响其他数据
                    }
                }

                // 观察用户猫咪（用于首页预览）
                launch {
                    try {
                        catRepository.observeUserCat().collect { cat ->
                            val breed = cat?.let { catRepository.getBreedById(it.breedId) }
                            // 加载完成后置 isCatLoading=false，区分"加载中"与"真空态"
                            _uiState.update { it.copy(userCat = cat, breed = breed, isCatLoading = false) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 猫咪数据加载失败，重置加载状态避免永久转圈
                        _uiState.update { it.copy(isCatLoading = false) }
                    }
                }
            }
        }
    }

    /**
     * 检查权限状态
     *
     * 修复 Bug 2：原代码在"无障碍已授权 + 守护未开启"时无条件调用 autoEnableProtection()，
     * 导致用户手动关闭守护后，每次进入首页或从设置返回时守护被自动重新打开。
     *
     * 新逻辑：尊重用户的手动选择，不再自动开启守护开关。
     * 仅在守护已开启时确保前台服务运行（进程被杀重启后服务可能未运行）。
     * 首次安装的自动开启由 AppDetectionManager.setAccessibilityConnected() 内的
     * hasUserOperated 检查负责（仅当用户从未操作过守护开关时自动开启）。
     */
    fun checkPermissions() {
        val context = getApplication<Application>()
        val accessibilityGranted = PermissionHelper.checkAccessibilityPermission(context)
        val status = PermissionStatus(
            usageStatsGranted = PermissionHelper.checkUsageStatsPermission(context),
            overlayGranted = PermissionHelper.checkOverlayPermission(context),
            accessibilityGranted = accessibilityGranted,
            batteryOptimizationGranted = PermissionHelper.checkBatteryOptimization(context)
        )
        _uiState.update { it.copy(permissionStatus = status) }

        // FIX #2: 系统设置中无障碍已启用 → 主动同步连接状态
        // 解决进程被杀重启后 isAccessibilityConnected 误显示为 false 的问题
        if (accessibilityGranted) {
            AppDetectionManager.setAccessibilityConnected(true)
        }

        // 修复 Bug 2：移除"无障碍已授权 + 守护未开启 → 自动开启守护"逻辑
        // 用户手动关闭守护后不再自动重开，仅当守护已开启时确保前台服务运行
        if (AppDetectionManager.isProtectionEnabled) {
            // FIX #2: 守护已开启但前台保活服务可能未运行（进程被杀重启后）
            // 重新启动前台服务确保轮询引擎运行
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
     * 重新检查权限状态并刷新数据，确保用户从设置页授权后状态能及时更新
     */
    fun onResume() {
        checkPermissions()
        refreshData()
    }

    /**
     * 获取今日起始时间戳（毫秒）
     * @return 今日 00:00:00 的时间戳
     */
    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
