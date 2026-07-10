package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 专注会话实体类
 * 用于记录用户每次专注会话的统计信息
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 目标应用包名 */
    val targetApp: String,
    /** 会话开始时间戳（毫秒） */
    val startTime: Long,
    /** 会话结束时间戳（毫秒），可为空表示会话未结束 */
    val endTime: Long?,
    /** 是否通过测验 */
    val quizPassed: Boolean,
    /** 会话时长（秒），默认为 0 */
    val durationSeconds: Int = 0
)
