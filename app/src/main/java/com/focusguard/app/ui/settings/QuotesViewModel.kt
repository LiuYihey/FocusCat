package com.focusguard.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.QuoteEntity
import com.focusguard.app.data.repository.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 小猫格言管理页状态
 *
 * @param quotes 全部格言（含停用）
 * @param editing 正在编辑的格言（null = 无编辑弹窗）
 * @param inputText 编辑框内容
 * @param errorMessage 一次性错误提示
 */
data class QuotesUiState(
    /** 全部格言（含停用），按 order 升序 */
    val quotes: List<QuoteEntity> = emptyList(),
    /** 是否展示添加/编辑弹窗 */
    val showDialog: Boolean = false,
    /** 正在编辑的格言（null = 新增模式） */
    val editing: QuoteEntity? = null,
    /** 编辑框内容 */
    val inputText: String = "",
    /** 一次性错误消息 */
    val errorMessage: String? = null
)

/**
 * 小猫格言管理 ViewModel
 *
 * 需求 1：退出被约束应用时不再发放猫粮，改为随机展示一句小猫格言；
 * 格言内容由用户自行编写，可在设置中增删改。
 */
@HiltViewModel
class QuotesViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuotesUiState())
    val uiState: StateFlow<QuotesUiState> = _uiState.asStateFlow()

    init {
        loadQuotes()
    }

    /** 加载全部格言 */
    fun loadQuotes() {
        viewModelScope.launch {
            try {
                val quotes = quoteRepository.getAllQuotes()
                _uiState.update { it.copy(quotes = quotes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "加载格言失败：${e.message}") }
            }
        }
    }

    /** 打开新增弹窗 */
    fun showAddDialog() {
        _uiState.update { it.copy(showDialog = true, editing = null, inputText = "") }
    }

    /** 打开编辑弹窗 */
    fun showEditDialog(quote: QuoteEntity) {
        _uiState.update { it.copy(showDialog = true, editing = quote, inputText = quote.text) }
    }

    /** 关闭弹窗 */
    fun dismissDialog() {
        _uiState.update { it.copy(showDialog = false, editing = null, inputText = "") }
    }

    /** 更新编辑框内容 */
    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * 保存格言（新增或更新）
     *
     * 新增：order 取当前最大值 + 1，保证追加到列表末尾
     * 更新：保留原有 order
     */
    fun saveQuote() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "格言内容不能为空") }
            return
        }
        val editing = state.editing
        viewModelScope.launch {
            try {
                if (editing == null) {
                    val order = (quoteRepository.getMaxOrder()) + 1
                    quoteRepository.addQuote(
                        QuoteEntity(
                            text = text,
                            order = order,
                            isActive = true,
                            isCustom = true
                        )
                    )
                } else {
                    quoteRepository.updateQuote(editing.copy(text = text))
                }
                _uiState.update { it.copy(showDialog = false, editing = null, inputText = "") }
                loadQuotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "保存格言失败：${e.message}") }
            }
        }
    }

    /** 删除格言 */
    fun deleteQuote(quote: QuoteEntity) {
        viewModelScope.launch {
            try {
                quoteRepository.deleteQuote(quote)
                loadQuotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "删除格言失败：${e.message}") }
            }
        }
    }

    /** 启用/停用格言（停用的格言不参与随机抽取） */
    fun toggleActive(quote: QuoteEntity) {
        viewModelScope.launch {
            try {
                quoteRepository.updateQuote(quote.copy(isActive = !quote.isActive))
                loadQuotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "更新格言状态失败：${e.message}") }
            }
        }
    }

    /** 消费一次性错误消息 */
    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
