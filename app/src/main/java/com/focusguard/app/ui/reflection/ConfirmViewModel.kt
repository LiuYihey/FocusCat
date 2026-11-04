package com.focusguard.app.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.repository.QuoteRepository
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
 * 确认/格言页的 UI 状态
 */
data class ConfirmUiState(
    /** 触发应用包名 */
    val targetApp: String? = null,
    /** 是否已选择退出 */
    val isExiting: Boolean = false,
    /** 退出后随机得到的小猫格言（null = 尚未获取） */
    val grantedQuote: String? = null
)

/**
 * 确认页 ViewModel
 * 管理二次抉择逻辑：确认进入应用 或 退出获得随机小猫格言
 *
 * 需求 1：退出被约束应用不再发放猫粮奖励，改为随机展示一句小猫格言。
 * 猫粮奖励仅来自专注时长（专注模式计时）。
 */
@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConfirmUiState())
    val state: StateFlow<ConfirmUiState> = _state.asStateFlow()

    /** 当前获取格言的协程，用于防止并发触发 */
    private var grantJob: Job? = null

    /**
     * 设置目标应用
     */
    fun setTargetApp(packageName: String) {
        _state.update { it.copy(targetApp = packageName) }
    }

    /**
     * 为新拦截重置全部状态（singleInstance 模式下 onNewIntent 使用）
     * 取消正在执行的获取协程，清空 isExiting/grantedQuote，避免新拦截显示旧的格言
     */
    fun resetForNewInterception(packageName: String) {
        grantJob?.cancel()
        grantJob = null
        _state.value = ConfirmUiState(targetApp = packageName)
    }

    /**
     * 退出并获得一句随机小猫格言
     * 调用 QuoteRepository 从启用中的格言里随机抽取一条
     *
     * 防重复策略（两层守门）：
     * 1. 若已成功获得格言（grantedQuote != null），不再重复获取（防双击/多击）
     * 2. 若获取协程正在执行（grantJob.isActive），阻止新触发（防并发）
     */
    fun exitWithQuote() {
        val current = _state.value
        if (current.grantedQuote != null) return
        if (grantJob?.isActive == true) return

        grantJob = viewModelScope.launch {
            try {
                val quote = quoteRepository.getRandomQuote()?.text
                _state.update { it.copy(isExiting = true, grantedQuote = quote) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isExiting = true, grantedQuote = null)
                }
            }
        }
    }
}
