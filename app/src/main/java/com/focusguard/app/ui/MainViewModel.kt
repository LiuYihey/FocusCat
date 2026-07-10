package com.focusguard.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.repository.CatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主界面初始状态
 */
sealed class MainUiState {
    /** 加载中 */
    object Loading : MainUiState()
    /** 未选择猫咪，需显示选择页 */
    object NeedCatSelection : MainUiState()
    /** 已有猫咪，显示主界面 */
    object Ready : MainUiState()
}

/**
 * 主界面 ViewModel
 * 检查用户是否已选择猫咪，决定显示选择页还是主界面
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val catRepository: CatRepository
) : ViewModel() {

    private val _state = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        checkCatExistence()
    }

    /**
     * 检查用户是否已选择猫咪
     *
     * 首次启动时保证 Loading 界面至少展示 [MIN_LOADING_DURATION_MS]，
     * 让用户能看到"FocusCat 专注每一刻，陪伴每一天"的品牌标语，
     * 避免异步检查过快导致开屏一闪而过。
     * 用 async 并行执行猫咪检查 + delay，两者取最大值，不阻塞业务。
     * 后续由 CatSelectionScreen 创建猫咪后回调触发时，无需再等待。
     */
    fun checkCatExistence() {
        viewModelScope.launch {
            val isFirstCheck = _state.value is MainUiState.Loading
            if (!isFirstCheck) {
                _state.value = MainUiState.Loading
            }
            coroutineScope {
                val catCheck = async { catRepository.hasCat() }
                if (isFirstCheck) {
                    // 并行等待最小展示时长，与猫咪检查取最大值
                    delay(MIN_LOADING_DURATION_MS)
                }
                val hasCat = catCheck.await()
                _state.value =
                    if (hasCat) MainUiState.Ready else MainUiState.NeedCatSelection
            }
        }
    }

    companion object {
        /** 开屏 Loading 界面最小展示时长（毫秒） */
        private const val MIN_LOADING_DURATION_MS = 1000L
    }
}
