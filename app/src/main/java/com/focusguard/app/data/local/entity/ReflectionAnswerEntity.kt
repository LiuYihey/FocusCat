package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 反思回答记录实体
 * 存储用户在反思页填写的回答历史
 */
@Entity(tableName = "reflection_answers")
data class ReflectionAnswerEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 关联的问题 ID */
    val questionId: Long,
    /** 用户填写的回答文本 */
    val answerText: String,
    /** 触发应用包名（可为空） */
    val targetApp: String?,
    /** 回答时间戳（毫秒） */
    val answeredAt: Long = System.currentTimeMillis()
)
