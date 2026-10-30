package com.focusguard.app.util

import org.json.JSONArray

/**
 * 反思题选项 JSON 序列化工具（需求 3）
 *
 * 选项以 JSON 数组字符串存储在 ReflectionQuestionEntity.optionsJson 中，
 * 格式与既有的 QuizQuestionEntity.optionsJson 保持一致。
 *
 * 题目类型说明：
 * - open     开放问答（兼容历史数据），UI 渲染文本输入框
 * - choice   选择题，UI 渲染可点击的选项卡片，点击即选中
 * - duration 使用时长选择，选项文本中的数字即分钟数（如 "15 分钟" → 15）
 */
object ReflectionOptions {

    /** 解析选项 JSON 数组，非法或空内容返回空列表 */
    fun parse(optionsJson: String?): List<String> {
        if (optionsJson.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(optionsJson)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 序列化为 JSON 数组字符串，空列表返回空串 */
    fun encode(options: List<String>): String {
        if (options.isEmpty()) return ""
        val array = JSONArray()
        options.forEach { array.put(it) }
        return array.toString()
    }

    /**
     * 从选项文本中解析分钟数（用于 duration 类型）
     * "15 分钟" → 15；"30" → 30；"1 小时" → 60；无法解析返回 0
     */
    fun parseDurationMinutes(optionText: String): Int {
        val digits = optionText.filter { it.isDigit() }
        val value = digits.toIntOrNull() ?: return 0
        // 含「小时 / h / H」按小时换算为分钟，避免 "1 小时" 被解析成 1 分钟
        val lower = optionText.lowercase()
        val isHour = lower.contains("小时") || lower.contains("hour") ||
            lower.contains("h") && !lower.contains("min")
        return if (isHour) value * 60 else value
    }
}
