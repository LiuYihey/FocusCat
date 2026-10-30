package com.focusguard.app.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.ReflectionAnswerEntity
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity
import com.focusguard.app.data.repository.ReflectionRepository
import com.focusguard.app.util.ReflectionOptions
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
 *
 * 需求 3：选择题点击即答 —— selectedOptionIndex 记录选中的选项下标；
 * 开放问答（历史数据）仍用 answer 文本。
 */
data class ReflectionItemState(
    val question: ReflectionQuestionEntity,
    /** 开放问答的文本回答 */
    val answer: String = "",
    /** 选择题选中的选项下标，-1 表示未选择 */
    val selectedOptionIndex: Int = -1,
    /** 解析后的选项列表（选择题/使用时长题） */
    val options: List<String> = emptyList()
) {
    /**
     * 是否为点击即答的题型
     * 选项为空时降级为开放问答，避免出现「无选项可选」的僵死状态
     */
    val isChoice: Boolean
        get() = question.questionType != ReflectionQuestionEntity.TYPE_OPEN && options.isNotEmpty()

    /** 该题是否已完成作答 */
    val isAnswered: Boolean
        get() = if (isChoice) {
            selectedOptionIndex in options.indices
        } else {
            answer.isNotBlank()
        }

    /** 当前作答文本（选择题返回选中选项文案） */
    val answerText: String
        get() = if (isChoice) {
            options.getOrNull(selectedOptionIndex) ?: ""
        } else {
            answer
        }
}

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
    val errorMessage: String? = null,
    /** 用户选中的使用时长（分钟），0 表示未选择或无时长题 */
    val selectedDurationMinutes: Int = 0
)

/**
 * 反思问答 ViewModel
 * 管理反思问题的加载和用户回答的提交
 *
 * 需求 3：题目改为选择题，点击选项即完成作答；
 * 「使用时长」题的选中结果解析为分钟数，供确认页写入临时豁免窗口。
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
                questions = emptyList(),
                selectedDurationMinutes = 0
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val questions = reflectionRepository.getActiveQuestions()
                val items = questions.map { entity ->
                    ReflectionItemState(
                        question = entity,
                        options = ReflectionOptions.parse(entity.optionsJson)
                    )
                }
                // 需求 3：必须存在一道「使用时长」题，否则无法确定免打扰时长。
                // 用户删光题目或自建问题时，用内置默认时长题兜底，保证功能始终可用。
                // 顺序保持 DB 的 order 字段：默认种子下时长题正是第 2 题。
                val withDuration = if (items.any { it.question.questionType == ReflectionQuestionEntity.TYPE_DURATION }) {
                    items
                } else {
                    items + ReflectionItemState(
                        question = DEFAULT_DURATION_QUESTION,
                        options = ReflectionOptions.parse(DEFAULT_DURATION_QUESTION.optionsJson)
                    )
                }
                _state.update {
                    it.copy(
                        questions = withDuration,
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
     * 更新某个问题的回答（开放问答）
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
     * 选择题：选中某个选项（点击即答）
     */
    fun selectOption(index: Int, optionIndex: Int) {
        _state.update { state ->
            val newQuestions = state.questions.toMutableList()
            if (index !in newQuestions.indices) return@update state
            val item = newQuestions[index]
            if (optionIndex !in item.options.indices) return@update state
            newQuestions[index] = item.copy(selectedOptionIndex = optionIndex)
            val updated = state.copy(questions = newQuestions, errorMessage = null)
            // 重新计算选中时长：使用时长题每次改选都要同步
            updated.copy(selectedDurationMinutes = resolveDurationMinutes(updated.questions))
        }
    }

    /**
     * 提交回答
     *
     * 设计：用户必须回答所有问题，不允许跳过。
     * 全部回答后保存到数据库并进入确认页。
     * 通过 isSubmitting 状态防止双击重复保存。
     * @return true 表示提交成功可进入确认页，false 表示有未回答的问题或正在提交中
     */
    fun submitAnswers(): Boolean {
        val currentState = _state.value
        // 正在提交中，阻止并发触发
        if (currentState.isSubmitting) return false

        val allAnswered = currentState.questions.isNotEmpty() &&
            currentState.questions.all { it.isAnswered }

        if (!allAnswered) {
            _state.update { it.copy(errorMessage = "请先回答所有反思问题") }
            return false
        }

        _state.update { it.copy(isSubmitting = true) }
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val answers = currentState.questions
                    // 跳过兜底的时长题（id < 0），它没有对应的 DB 记录
                    .filter { it.question.id > 0 }
                    .map { item ->
                        ReflectionAnswerEntity(
                            questionId = item.question.id,
                            answerText = item.answerText.trim(),
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

    /**
     * 从题目列表中解析「使用时长」题的选中分钟数
     * 找不到时长题或未选择时返回 0
     */
    private fun resolveDurationMinutes(items: List<ReflectionItemState>): Int {
        val durationItem = items.firstOrNull {
            it.question.questionType == ReflectionQuestionEntity.TYPE_DURATION
        } ?: return 0
        val option = durationItem.options.getOrNull(durationItem.selectedOptionIndex) ?: return 0
        return ReflectionOptions.parseDurationMinutes(option)
    }

    companion object {
        /**
         * 内置的默认使用时长题
         * 当用户删除全部反思问题或未配置时长题时作为兜底，保证时长选择功能始终可用
         */
        val DEFAULT_DURATION_QUESTION = ReflectionQuestionEntity(
            id = -1,
            questionText = "这次打算使用多久？",
            order = Int.MAX_VALUE,
            isActive = true,
            isCustom = false,
            placeholder = "",
            questionType = ReflectionQuestionEntity.TYPE_DURATION,
            optionsJson = """["5 分钟","15 分钟","30 分钟","1 小时"]"""
        )
    }
}
