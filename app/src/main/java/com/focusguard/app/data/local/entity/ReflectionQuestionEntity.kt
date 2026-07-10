package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 反思问题实体
 * 存储用户进入被约束应用时需回答的开放式问题
 * 问题无标准答案，用于目标强化（goal reinforcement）
 */
@Entity(tableName = "reflection_questions")
data class ReflectionQuestionEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 问题文本 */
    val questionText: String,
    /** 显示顺序（1、2、3） */
    val order: Int,
    /** 是否启用 */
    val isActive: Boolean = true,
    /** 是否用户自定义（默认问题为 false） */
    val isCustom: Boolean = false,
    /** 输入框占位提示文本 */
    val placeholder: String = ""
)
