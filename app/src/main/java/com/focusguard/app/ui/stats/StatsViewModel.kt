package com.focusguard.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.BlockedAppEntity
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.data.repository.QuizRepositoryImpl
import com.focusguard.app.data.repository.StatsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * 统计周期枚举
 * 注意：WEEK/MONTH 使用滚动窗口（近 7 天 / 近 30 天），非自然周/自然月
 */
enum class StatsPeriod(val title: String) {
    /** 今天 */
    TODAY("今天"),
    /** 近 7 天（滚动窗口） */
    WEEK("近 7 天"),
    /** 近 30 天（滚动窗口） */
    MONTH("近 30 天")
}

/**
 * 趋势数据项
 * @property label 日期标签
 * @property value 数值（毫秒）
 */
data class TrendItem(
    val label: String,
    val value: Long
)

/**
 * 统计 UI 状态
 */
data class StatsUiState(
    /** 当前选中的统计周期 */
    val selectedPeriod: StatsPeriod = StatsPeriod.TODAY,
    /** 总专注时长（毫秒）- 全部历史累计 */
    val totalFocusDuration: Long = 0L,
    /** 总拦截次数 - 全部历史累计 */
    val totalBlockCount: Int = 0,
    /** 当前周期内的专注时长（毫秒）：今天=今日，本周=本周累计，本月=本月累计 */
    val periodFocusDuration: Long = 0L,
    /** 当前周期内的拦截次数 */
    val periodBlockCount: Int = 0,
    /** 趋势数据：今天/本周=7 天，本月=30 天 */
    val trendData: List<TrendItem> = emptyList()
)

/**
 * 统计 ViewModel
 * 负责加载统计数据和趋势数据
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepositoryImpl,
    private val quizRepository: QuizRepositoryImpl,
    private val appRepository: AppRepositoryImpl
) : ViewModel() {

    /** 已拦截应用列表（用于显示拦截次数排行） */
    val blockedApps: StateFlow<List<BlockedAppEntity>> = appRepository.getAllBlockedApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 统计 UI 状态 */
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /** 数据收集 Job 引用，用于在重新刷新前取消旧的收集器，避免重复收集 */
    private var dataCollectionJob: Job? = null

    /** 趋势数据加载 Job 引用，独立管理避免快速切换周期时并发竞态 */
    private var trendDataJob: Job? = null

    init {
        refreshData()
    }

    /**
     * 选择统计周期
     * @param period 统计周期
     */
    fun selectPeriod(period: StatsPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        refreshData()
    }

    /**
     * 刷新统计数据
     * 使用 Job 引用管理，调用前取消旧 Job 避免重复收集 Flow
     *
     * 同时加载「全部历史累计」与「当前周期」两套数据：
     * - 总专注时长 / 总拦截次数：全历史累计（不随周期变化）
     * - 周期内专注时长 / 拦截次数：随 selectedPeriod 切换
     */
    fun refreshData() {
        // 取消旧的收集器，避免多次调用导致重复收集
        dataCollectionJob?.cancel()
        dataCollectionJob = viewModelScope.launch {
            val period = _uiState.value.selectedPeriod
            val periodStart = getStartTimestamp(period)
            val now = System.currentTimeMillis()

            // 周期内专注时长：根据周期过滤聚合
            launch {
                try {
                    val sessions = statsRepository.getSessionsByDateRange(periodStart, now)
                    val periodDurationMs = sessions.sumOf { it.durationSeconds * 1000L }
                    _uiState.update { it.copy(periodFocusDuration = periodDurationMs) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单项查询失败不影响其他统计项
            }
            }

            // 周期内拦截次数
            launch {
                try {
                    val history = quizRepository.getQuizHistoryByDateRange(periodStart, now)
                    _uiState.update { it.copy(periodBlockCount = history.size) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单项查询失败不影响其他统计项
            }
            }

            // 全历史累计专注时长
            launch {
                try {
                    val totalFocusSeconds = statsRepository.getTotalStats()
                    _uiState.update { it.copy(totalFocusDuration = totalFocusSeconds * 1000L) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单项查询失败不影响其他统计项
            }
            }

            // 全历史累计拦截次数（所有被约束 APP 的 blockCount 之和）
            launch {
                try {
                    val totalBlocks = appRepository.getAllBlockedApps()
                        .first()
                        .sumOf { it.blockCount }
                    _uiState.update { it.copy(totalBlockCount = totalBlocks) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单项查询失败不影响其他统计项
            }
            }
        }

        // 加载趋势数据（周期决定天数）
        loadTrendData()
    }

    /**
     * 获取今日 00:00 时间戳
     */
    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 加载专注时长趋势数据
     * 周期决定趋势天数：今天/本周=7 天，本月=30 天
     * 一次性查询范围内所有专注会话，按日聚合，避免多次数据库查询
     * 使用独立 Job 管理，快速切换周期时取消旧查询避免竞态
     */
    private fun loadTrendData() {
        trendDataJob?.cancel()
        trendDataJob = viewModelScope.launch {
            try {
                val period = _uiState.value.selectedPeriod
                // 周期对应的天数：今天/本周看 7 天趋势，本月看 30 天趋势
                val days = when (period) {
                    StatsPeriod.TODAY -> 7
                    StatsPeriod.WEEK -> 7
                    StatsPeriod.MONTH -> 30
                }
                val trendList = mutableListOf<TrendItem>()
                val now = Calendar.getInstance()
                // N-1 天前 00:00 作为查询起点
                val startCalendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -(days - 1))
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // 一次性查询范围内所有会话
                val allSessions = statsRepository.getSessionsByDateRange(
                    start = startCalendar.timeInMillis,
                    end = now.timeInMillis
                )
                // 按天分组聚合
                for (i in (days - 1) downTo 0) {
                    val dayCalendar = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val dayStart = dayCalendar.timeInMillis
                    dayCalendar.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = dayCalendar.timeInMillis
                    val label = dayStart.formatTrendLabel()
                    val value = allSessions
                        .filter { it.startTime in dayStart until dayEnd }
                        .sumOf { it.durationSeconds * 1000L }
                    trendList.add(TrendItem(label = label, value = value))
                }
                _uiState.update { it.copy(trendData = trendList) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 查询失败时保留空列表，避免崩溃
                _uiState.update { it.copy(trendData = emptyList()) }
            }
        }
    }

    /**
     * 获取统计周期的起始时间戳
     * - TODAY：今日 00:00
     * - WEEK：6 天前 00:00（含今日共 7 天，与趋势图 7 天对齐）
     * - MONTH：29 天前 00:00（含今日共 30 天，与趋势图 30 天对齐）
     * @param period 统计周期，默认取当前选中周期
     * @return 起始时间戳（毫秒）
     */
    private fun getStartTimestamp(period: StatsPeriod = _uiState.value.selectedPeriod): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return when (period) {
            StatsPeriod.TODAY -> calendar.timeInMillis
            StatsPeriod.WEEK -> {
                // -6 = 含今日共 7 天，与 loadTrendData 的 days-1=6 对齐
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.timeInMillis
            }
            StatsPeriod.MONTH -> {
                // -29 = 含今日共 30 天，与 loadTrendData 的 days-1=29 对齐
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.timeInMillis
            }
        }
    }

    /**
     * 格式化时间戳为趋势标签（MM-dd）
     */
    private fun Long.formatTrendLabel(): String {
        val dateFormat = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(this))
    }
}
