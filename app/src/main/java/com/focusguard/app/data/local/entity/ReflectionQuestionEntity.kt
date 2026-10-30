package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 反思问题实体
 * 存储用户进入被约束应用时需回答的问题
 *
 * 需求 3 起反思页改为「点击即答」的选择题：
 * - [questionType] 为 [TYPE_CHOICE] 时渲染可点击的选项卡片
 * - [questionType] 为 [TYPE_DURATION] 时第二个问题为「使用时长」选择
 * - [questionType] 为 [TYPE_OPEN] 时保留旧版开放式问答（兼容历史数据）
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
    /** 输入框占位提示文本（仅 TYPE_OPEN 使用） */
    val placeholder: String = "",
    /** 题目类型：open 开放问答 / choice 选择题 / duration 使用时长选择 */
    val questionType: String = TYPE_OPEN,
    /** 选项的 JSON 数组字符串，如 ["消遣娱乐","完成工作"] */
    val optionsJson: String = ""
) {
    /**
     * 解析后的选项列表（计算属性，非数据库字段）
     * 选择题与使用时长题使用；开放问答为空列表
     */
    val options: List<String>
        get() = com.focusguard.app.util.ReflectionOptions.parse(optionsJson)

    companion object {
        /** 开放问答（旧版题型，保留兼容） */
        const val TYPE_OPEN = "open"

        /** 选择题：点击选项即完成回答 */
        const val TYPE_CHOICE = "choice"

        /** 使用时长选择：选项中的数字代表分钟数 */
        const val TYPE_DURATION = "duration"
    }
}
