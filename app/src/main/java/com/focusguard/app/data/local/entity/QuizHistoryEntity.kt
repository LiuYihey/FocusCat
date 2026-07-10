package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 测验历史记录实体类
 * 用于存储用户每次答题的记录
 */
@Entity(tableName = "quiz_history")
data class QuizHistoryEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 关联的题目 ID */
    val questionId: Long,
    /** 题目文本（冗余存储，便于历史记录展示） */
    val questionText: String,
    /** 用户选择的答案索引 */
    val selectedAnswer: Int,
    /** 是否回答正确 */
    val isCorrect: Boolean,
    /** 触发测验的目标应用包名（可为空） */
    val targetApp: String?,
    /** 答题时间戳（毫秒） */
    val timestamp: Long
)
