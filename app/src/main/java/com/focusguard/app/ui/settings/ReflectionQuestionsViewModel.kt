package com.focusguard.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity
import com.focusguard.app.data.repository.ReflectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 反思问题管理 UI 状态
 */
data class ReflectionQuestionsUiState(
    /** 所有反思问题列表 */
    val questions: List<ReflectionQuestionEntity> = emptyList(),
    /** 是否正在加载 */
    val isLoading: Boolean = true,
    /** 是否显示添加/编辑对话框 */
    val showEditDialog: Boolean = false,
    /** 当前编辑的问题（null 表示新增） */
    val editingQuestion: ReflectionQuestionEntity? = null,
    /** 错误提示 */
    val errorMessage: String? = null
)

/**
 * 反思问题管理 ViewModel
 * 允许用户查看、添加、编辑、启用/禁用、删除反思问题
 */
@HiltViewModel
class ReflectionQuestionsViewModel @Inject constructor(
    private val reflectionRepository: ReflectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReflectionQuestionsUiState())
    val state: StateFlow<ReflectionQuestionsUiState> = _state.asStateFlow()

    init {
        loadQuestions()
    }

    /** 加载所有反思问题 */
    fun loadQuestions() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val questions = reflectionRepository.getAllQuestions()
                _state.update { it.copy(questions = questions, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "加载失败") }
            }
        }
    }

    /** 显示新增问题对话框 */
    fun showAddDialog() {
        _state.update { it.copy(showEditDialog = true, editingQuestion = null) }
    }

    /** 显示编辑问题对话框 */
    fun showEditDialog(question: ReflectionQuestionEntity) {
        _state.update { it.copy(showEditDialog = true, editingQuestion = question) }
    }

    /** 关闭对话框 */
    fun dismissDialog() {
        _state.update { it.copy(showEditDialog = false, editingQuestion = null) }
    }

    /**
     * 保存问题（新增或更新）
     * @param questionText 问题文本
     * @param placeholder 输入框占位提示
     */
    fun saveQuestion(questionText: String, placeholder: String) {
        val editing = _state.value.editingQuestion
        if (questionText.isBlank()) {
            _state.update { it.copy(errorMessage = "问题不能为空") }
            return
        }

        viewModelScope.launch {
            try {
                if (editing != null) {
                    // 更新现有问题
                    reflectionRepository.updateQuestion(
                        editing.copy(
                            questionText = questionText.trim(),
                            placeholder = placeholder.trim()
                        )
                    )
                } else {
                    // 新增问题：从 DB 查询 maxOrder，避免 state 异步刷新未完成时 order 重复
                    val maxOrder = reflectionRepository.getMaxOrder()
                    reflectionRepository.addQuestion(
                        ReflectionQuestionEntity(
                            questionText = questionText.trim(),
                            order = maxOrder + 1,
                            isActive = true,
                            isCustom = true,
                            placeholder = placeholder.trim()
                        )
                    )
                }
                _state.update { it.copy(showEditDialog = false, editingQuestion = null) }
                loadQuestions()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "保存失败") }
            }
        }
    }

    /** 切换问题启用状态 */
    fun toggleActive(question: ReflectionQuestionEntity) {
        viewModelScope.launch {
            try {
                reflectionRepository.updateQuestion(question.copy(isActive = !question.isActive))
                loadQuestions()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "操作失败") }
            }
        }
    }

    /** 删除问题 */
    fun deleteQuestion(question: ReflectionQuestionEntity) {
        viewModelScope.launch {
            try {
                reflectionRepository.deleteQuestion(question)
                loadQuestions()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "删除失败") }
            }
        }
    }

    /** 清除错误提示 */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
