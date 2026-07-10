package com.focusguard.app.ui.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.CatCatalogEntity
import com.focusguard.app.data.local.entity.FoodCatalogEntity
import com.focusguard.app.data.local.entity.UserCatEntity
import com.focusguard.app.data.repository.CatRepository
import com.focusguard.app.data.repository.FoodRepository
import com.focusguard.app.data.repository.StatsRepositoryImpl
import com.focusguard.app.util.FocusSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 专注会话 UI 状态
 */
data class FocusUiState(
    /** 计时是否正在运行 */
    val isRunning: Boolean = false,
    /** 已专注时长（毫秒） */
    val elapsedMillis: Long = 0L,
    /** 距离下次猫粮奖励的剩余毫秒（0 表示已达成待领取） */
    val millisToNextReward: Long = REWARD_INTERVAL_MS,
    /** 本次专注会话已获得的猫粮数量 */
    val rewardsEarned: Int = 0,
    /** 最近一次获得的猫粮（用于奖励动画），null 表示无新奖励 */
    val lastReward: FoodCatalogEntity? = null,
    /** 是否显示退出确认对话框 */
    val showExitConfirm: Boolean = false,
    /** 是否显示奖励动画 */
    val showRewardAnimation: Boolean = false,
    /** 用户猫咪（用于陪伴展示） */
    val userCat: UserCatEntity? = null,
    /** 猫咪品种信息 */
    val breed: CatCatalogEntity? = null,
    /** 错误提示 */
    val errorMessage: String? = null
) {
    companion object {
        /** 每专注 30 分钟获得一份猫粮 */
        const val REWARD_INTERVAL_MS = 30 * 60 * 1000L
    }
}

/**
 * 专注模式 ViewModel
 *
 * 核心职责：
 * 1. 启动/停止正计时（每秒更新 elapsedMillis）
 * 2. 每满 30 分钟自动调用 FoodRepository.grantRandomFood() 发放一份猫粮，触发奖励动画
 * 3. 调用 StatsRepositoryImpl 持久化 FocusSession（开始时 recordFocusSession，结束时 updateFocusSession）
 * 4. 通过 FocusSessionState 全局标志通知 FocusGuardService 进行防分心检测
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val catRepository: CatRepository,
    private val statsRepository: StatsRepositoryImpl,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    /** 计时循环 Job */
    private var timerJob: Job? = null
    /** 奖励动画关闭协程引用，新奖励触发前取消旧的，避免旧协程提前关闭新动画 */
    private var rewardAnimationJob: Job? = null
    /** recordFocusSession 协程引用，stopFocus 时 join 等待 sessionId 写入，避免孤儿记录 */
    private var recordSessionJob: Job? = null

    /** 当前专注会话的数据库 ID（持久化用） */
    private var sessionId: Long = -1L

    /** 本次会话开始时间戳 */
    private var sessionStartTime: Long = 0L

    init {
        // 加载用户猫咪用于陪伴展示
        viewModelScope.launch {
            catRepository.observeUserCat().collect { cat ->
                val breed = cat?.let { catRepository.getBreedById(it.breedId) }
                _uiState.update { it.copy(userCat = cat, breed = breed) }
            }
        }
    }

    /**
     * 开始专注计时
     * - 持久化一条 FocusSession 到数据库
     * - 设置 FocusSessionState 全局标志（FocusGuardService 据此检测切屏）
     * - 启动每秒计时循环
     */
    fun startFocus() {
        if (_uiState.value.isRunning) return

        sessionStartTime = System.currentTimeMillis()
        FocusSessionState.start()

        // 持久化 FocusSession 到数据库（保存 Job 引用，stopFocus 时 join 等待写入完成）
        recordSessionJob = viewModelScope.launch {
            try {
                sessionId = statsRepository.recordFocusSession(
                    targetApp = "focus_mode", // 专注模式标记，区别于被拦截 APP
                    startTime = sessionStartTime,
                    endTime = null,
                    quizPassed = false,
                    durationSeconds = 0
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程取消必须重新抛出，保证结构化并发正确传播
                throw e
            } catch (e: Exception) {
                // 持久化失败不影响计时功能
            }
        }

        _uiState.update { it.copy(isRunning = true) }
        startTimerLoop()
    }

    /**
     * 计时循环：每秒更新 elapsedMillis 和 millisToNextReward
     * 每满 30 分钟自动发放一份猫粮
     */
    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.isRunning) {
                val elapsed = System.currentTimeMillis() - sessionStartTime

                // 每秒更新计时显示和累计时长（奖励发放时也不中断计时更新，避免 1 秒冻结）
                _uiState.update {
                    it.copy(
                        elapsedMillis = elapsed,
                        millisToNextReward = FocusUiState.REWARD_INTERVAL_MS -
                            (elapsed % FocusUiState.REWARD_INTERVAL_MS)
                    )
                }
                FocusSessionState.updateAccumulated(elapsed)

                // 检查是否达成新奖励（每 30 分钟一次）
                val totalRewardsShouldHave = (elapsed / FocusUiState.REWARD_INTERVAL_MS).toInt()
                if (totalRewardsShouldHave > _uiState.value.rewardsEarned) {
                    grantReward()
                }

                delay(1000L)
            }
        }
    }

    /**
     * 发放一份随机猫粮并触发奖励动画
     */
    private suspend fun grantReward() {
        try {
            val food = foodRepository.grantRandomFood()
            _uiState.update {
                it.copy(
                    rewardsEarned = it.rewardsEarned + 1,
                    lastReward = food,
                    showRewardAnimation = true
                )
            }
            // 3 秒后关闭奖励动画
            // 修复 B7：保存 Job 引用，新奖励触发前取消旧的，避免旧协程提前关闭新动画
            rewardAnimationJob?.cancel()
            rewardAnimationJob = viewModelScope.launch {
                delay(3000L)
                _uiState.update { it.copy(showRewardAnimation = false) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消必须重新抛出，保证结构化并发正确传播
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "猫粮发放失败：${e.message}") }
        }
    }

    /**
     * 用户点击退出按钮 → 显示退出确认对话框
     * 对话框中显示距离下一份猫粮的剩余分钟数
     */
    fun requestExit() {
        _uiState.update { it.copy(showExitConfirm = true) }
    }

    /**
     * 取消退出，继续专注
     */
    fun cancelExit() {
        _uiState.update { it.copy(showExitConfirm = false) }
    }

    /**
     * 确认退出，结束专注会话
     * - 停止计时
     * - 更新数据库 FocusSession 的结束时间和时长
     * - 清除 FocusSessionState 标志
     */
    fun confirmExit() {
        stopFocus()
        _uiState.update { it.copy(showExitConfirm = false) }
    }

    /**
     * 停止专注计时（内部调用）
     */
    private fun stopFocus() {
        timerJob?.cancel()
        timerJob = null
        FocusSessionState.stop()

        val endTime = System.currentTimeMillis()
        val durationSeconds = ((endTime - sessionStartTime) / 1000).toInt()

        // 更新数据库 FocusSession
        // 修复 FV-2：原代码在协程内才重置 sessionId，若用户快速"退出→再开始"，
        // startFocus 的协程会先设置新 sessionId，然后本协程的 sessionId=-1L 会覆盖它，
        // 导致新会话无法被关闭更新，统计数据丢失。改为立即重置成员变量。
        //
        // 修复迭代5 Bug #5：原代码若用户在 recordFocusSession 协程完成前调用 stopFocus，
        // sessionId 仍是 -1L，updateFocusSession 不会被调用，但 recordSessionJob 随后
        // 完成会写入新 sessionId，形成孤儿记录（endTime=null, duration=0）。
        // 改为 join 等待 recordSessionJob 完成后再判断 sessionId。
        val pendingJob = recordSessionJob
        recordSessionJob = null
        viewModelScope.launch {
            // 等待 recordFocusSession 完成，确保 sessionId 已写入（或失败保持 -1L）
            pendingJob?.join()
            if (sessionId > 0) {
                val sessionIdToClose = sessionId
                sessionId = -1L  // 立即重置，防止被 startFocus 的新值覆盖
                try {
                    statsRepository.updateFocusSession(
                        id = sessionIdToClose,
                        endTime = endTime,
                        duration = durationSeconds
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 协程取消必须重新抛出，保证结构化并发正确传播
                    throw e
                } catch (e: Exception) {
                    // 持久化失败不影响 UI
                }
            }
        }

        _uiState.update {
            it.copy(
                isRunning = false,
                elapsedMillis = 0L,
                millisToNextReward = FocusUiState.REWARD_INTERVAL_MS,
                rewardsEarned = 0
            )
        }
    }

    /**
     * 清除错误提示
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * ViewModel 销毁时确保停止计时和清除全局标志
     * 防止用户直接杀进程导致 FocusSessionState 残留 true
     */
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        rewardAnimationJob?.cancel()
        FocusSessionState.stop()
    }
}
