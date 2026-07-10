package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 测验题目实体类
 * 用于存储拦截应用时弹出的测验题目
 */
@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 题目内容 */
    val question: String,
    /** 选项的 JSON 数组字符串 */
    val optionsJson: String,
    /** 正确答案的索引（从 0 开始） */
    val correctIndex: Int,
    /** 题目分类 */
    val category: String,
    /** 难度等级，默认为 1 */
    val difficulty: Int = 1,
    /** 累计展示次数，默认为 0 */
    val timesShown: Int = 0,
    /** 累计答对次数，默认为 0 */
    val timesCorrect: Int = 0
)
