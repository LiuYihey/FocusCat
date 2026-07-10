package com.focusguard.app.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.ReflectionAnswerEntity
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity
import com.focusguard.app.data.repository.ReflectionRepository
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
 * 单个反思问题的 UI 状态
 */
data class ReflectionItemState(
    val question: ReflectionQuestionEntity,
    val answer: String = ""
)

/**
 * 反思问答界面的 UI 状态
 */
data class ReflectionUiState(
    /** 反思问题列表 */
    val questions: List<ReflectionItemState> = emptyList(),
    /** 触发应用包名 */
    val targetApp: String? = null,
    /** 是否正在加载 */
    val isLoading: Boolean = true,
    /** 是否已提交（用于跳转确认页） */
    val isSubmitted: Boolean = false,
    /** 是否正在提交中（防双击重复保存） */
    val isSubmitting: Boolean = false,
    /** 错误提示 */
    val errorMessage: String? = null
)

/**
 * 反思问答 ViewModel
 * 管理反思问题的加载和用户回答的提交
 * 问题无标准答案，仅做必填校验
 */
@HiltViewModel
class ReflectionViewModel @Inject constructor(
    private val reflectionRepository: ReflectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReflectionUiState())
    val state: StateFlow<ReflectionUiState> = _state.asStateFlow()

    /** 当前提交协程引用，用于在新问题加载时取消未完成的旧提交 */
    private var submitJob: Job? = null

    /** 当前加载协程引用，用于在 onNewIntent 快速触发时取消未完成的旧加载，防止旧数据覆盖新数据竞态 */
    private var loadJob: Job? = null

    /**
     * 加载启用的反思问题
     * @param targetPackage 触发应用包名
     *
     * 注意：onNewIntent 触发新拦截时会调用本方法。必须取消正在进行的旧 submitAnswers 协程，
     * 否则旧协程完成后会设置 isSubmitted=true，错误地让新问题页面瞬间跳转 ConfirmActivity。
     * 同时重置 isSubmitting，避免新问题页面的提交被旧状态阻塞。
     *
     * 必须取消正在进行的旧加载协程：否则旧 loadQuestions 的 DB 读取若慢于新调用，
     * 旧 questions 会后完成并覆盖新 questions（stale data race），导致 UI 显示错误应用的问题。
     */
    fun loadQuestions(targetPackage: String) {
        submitJob?.cancel()
        submitJob = null
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = true,
                targetApp = targetPackage,
                isSubmitted = false,
                isSubmitting = false,
                errorMessage = null,
                questions = emptyList()
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val questions = reflectionRepository.getActiveQuestions()
                val items = questions.map { ReflectionItemState(question = it) }
                _state.update {
                    it.copy(
                        questions = items,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                // 协程被取消（onNewIntent 加载新问题）：不修改状态，交由新 loadQuestions 重置
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "加载问题失败"
                    )
                }
            }
        }
    }

    /**
     * 更新某个问题的回答
     */
    fun updateAnswer(index: Int, answer: String) {
        _state.update { state ->
            val newQuestions = state.questions.toMutableList()
            if (index in newQuestions.indices) {
                newQuestions[index] = newQuestions[index].copy(answer = answer)
            }
            state.copy(questions = newQuestions, errorMessage = null)
        }
    }

    /**
     * 提交回答
     *
     * 设计：用户必须回答所有问题，不允许跳过。
     * 全部回答非空后保存到数据库并进入确认页。
     * 通过 isSubmitting 状态防止双击重复保存。
     * @return true 表示提交成功可进入确认页，false 表示有未回答的问题或正在提交中
     */
    fun submitAnswers(): Boolean {
        val currentState = _state.value
        // 正在提交中，阻止并发触发
        if (currentState.isSubmitting) return false

        val allAnswered = currentState.questions.all { it.answer.isNotBlank() }

        if (!allAnswered) {
            _state.update { it.copy(errorMessage = "请先回答所有反思问题") }
            return false
        }

        _state.update { it.copy(isSubmitting = true) }
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val answers = currentState.questions.map { item ->
                    ReflectionAnswerEntity(
                        questionId = item.question.id,
                        answerText = item.answer.trim(),
                        targetApp = currentState.targetApp,
                        answeredAt = now
                    )
                }
                reflectionRepository.saveAnswers(answers)
                _state.update { it.copy(isSubmitted = true, isSubmitting = false) }
            } catch (e: CancellationException) {
                // 协程被取消（onNewIntent 加载新问题）：不修改状态，交由 loadQuestions 重置
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "保存失败，请重试", isSubmitting = false) }
            }
        }
        return true
    }

    /**
     * 重置已提交状态（跳转确认页失败时调用，允许用户重试）
     */
    fun resetSubmitted() {
        _state.update { it.copy(isSubmitted = false, isSubmitting = false) }
    }
}
