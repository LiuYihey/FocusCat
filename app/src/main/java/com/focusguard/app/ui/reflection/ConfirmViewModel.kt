package com.focusguard.app.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.FoodCatalogEntity
import com.focusguard.app.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 确认/食物奖励页的 UI 状态
 */
data class ConfirmUiState(
    /** 触发应用包名 */
    val targetApp: String? = null,
    /** 是否已选择退出并获得食物 */
    val isExiting: Boolean = false,
    /** 获得的食物（退出后设置） */
    val grantedFood: FoodCatalogEntity? = null
)

/**
 * 确认页 ViewModel
 * 管理二次抉择逻辑：确认进入应用 或 退出获得随机食物
 */
@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConfirmUiState())
    val state: StateFlow<ConfirmUiState> = _state.asStateFlow()

    /** 当前发放食物的协程，用于防止并发触发 */
    private var grantJob: Job? = null

    /**
     * 设置目标应用
     */
    fun setTargetApp(packageName: String) {
        _state.update { it.copy(targetApp = packageName) }
    }

    /**
     * 为新拦截重置全部状态（singleInstance 模式下 onNewIntent 使用）
     * 取消正在执行的发放协程，清空 isExiting/grantedFood，避免新拦截显示旧的食物发放结果
     */
    fun resetForNewInterception(packageName: String) {
        grantJob?.cancel()
        grantJob = null
        _state.value = ConfirmUiState(targetApp = packageName)
    }
    /**
     * 退出并获得随机食物
     * 调用 FoodRepository 加权随机发放一份食物
     *
     * 直接进入 OBTAINED 阶段（不再有独立揭晓动画阶段），
     * 揭晓动效集成在 FoodObtainedContent 中播放
     *
     * 防重复策略（三层守门）：
     * 1. 若已成功获得食物（grantedFood != null），不再重复发放（防双击/多击发多份）
     * 2. 若发放协程正在执行（grantJob.isActive），阻止新触发（防并发）
     * 3. 若处于 FAILED 状态（isExiting && grantedFood == null && 协程已结束），允许重试
     */
    fun exitWithFood() {
        val current = _state.value
        if (current.grantedFood != null) return
        if (grantJob?.isActive == true) return

        grantJob = viewModelScope.launch {
            try {
                val food = foodRepository.grantRandomFood()
                // 直接进入 OBTAINED 阶段，揭晓动效由 FoodObtainedContent 内部播放
                _state.update { it.copy(isExiting = true, grantedFood = food) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isExiting = true, grantedFood = null)
                }
            }
        }
    }
}
