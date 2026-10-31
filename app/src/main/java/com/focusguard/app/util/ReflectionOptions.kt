package com.focusguard.app.util

import org.json.JSONArray
import kotlin.math.ceil

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

    /** 时长上限：24 小时，防止用户误填导致无限免打扰 */
    const val MAX_DURATION_MINUTES = 24 * 60

    /** 时长下限：1 分钟，0 表示无法识别 */
    const val MIN_DURATION_MINUTES = 1

    /**
     * 数字（+ 可选单位）的匹配模式
     *
     * 单位按「长优先」排列，保证 "分钟" 不会被 "分" 抢走、
     * "minutes" 不会被 "min"/"m" 抢走。
     *
     * 单位是可选的：允许用户只写数字（"45" → 45 分钟），
     * 这是设置页提示里明确推荐的写法之一。
     */
    private val DURATION_PATTERN = Regex(
        """(\d+(?:\.\d+)?)\s*(小时|小時|时|時|hours|hour|hrs|hr|h|分钟|分鐘|分|minutes|minute|mins|min|m|秒钟|秒鐘|秒|seconds|second|secs|sec|s)?""",
        RegexOption.IGNORE_CASE
    )

    /** 单位 → 分钟的换算系数 */
    private val UNIT_MINUTES = mapOf(
        "小时" to 60.0, "小時" to 60.0, "时" to 60.0, "時" to 60.0,
        "hours" to 60.0, "hour" to 60.0, "hrs" to 60.0, "hr" to 60.0, "h" to 60.0,
        "分钟" to 1.0, "分鐘" to 1.0, "分" to 1.0,
        "minutes" to 1.0, "minute" to 1.0, "mins" to 1.0, "min" to 1.0, "m" to 1.0,
        "秒钟" to 1.0 / 60.0, "秒鐘" to 1.0 / 60.0, "秒" to 1.0 / 60.0,
        "seconds" to 1.0 / 60.0, "second" to 1.0 / 60.0, "secs" to 1.0 / 60.0, "sec" to 1.0 / 60.0, "s" to 1.0 / 60.0
    )

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
     *
     * 解析规则：
     * 1. 文本中出现「半小时 / 半天」等口语写法时优先按固定值处理
     * 2. 逐个匹配「数字 + 单位」，把各段累加成分钟
     *    - "15 分钟" → 15
     *    - "1 小时" → 60
     *    - "1.5 小时" → 90
     *    - "1 小时 30 分钟" → 90
     *    - "90 秒" → 2（向上取整，不允许 0 分钟）
     * 3. 只要有一处带单位，就只累加带单位的部分，
     *    避免 "Q1 15 分钟" 里的 "1" 被当成 1 分钟
     * 4. 全部是裸数字时，整段按分钟解释："45" → 45
     * 5. 结果向上取整并限制在 [1, 1440] 分钟；无法识别返回 0
     */
    fun parseDurationMinutes(optionText: String): Int {
        val text = optionText.trim()
        if (text.isEmpty()) return 0

        // 口语写法：半小时 / 半天
        if (text.contains("半")) {
            val halfMinutes = when {
                text.contains("天") -> 12 * 60
                text.contains("小时") || text.contains("小時") || text.contains("时") ||
                    text.contains("時") || text.contains("hour") || text.contains("hr") -> 30
                text.contains("分钟") || text.contains("分鐘") || text.contains("分") ||
                    text.contains("min") -> 1
                else -> 0
            }
            if (halfMinutes > 0) return halfMinutes.coerceAtMost(MAX_DURATION_MINUTES)
        }

        val matches = DURATION_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return 0

        // 有单位时只累加带单位的段；否则把裸数字当分钟
        val withUnit = matches.filter { it.groupValues[2].isNotEmpty() }
        val parts = if (withUnit.isNotEmpty()) withUnit else matches

        var totalMinutes = 0.0
        for (match in parts) {
            val value = match.groupValues[1].toDoubleOrNull() ?: continue
            val unit = match.groupValues[2].lowercase()
            totalMinutes += value * (UNIT_MINUTES[unit] ?: 1.0)
        }
        if (totalMinutes <= 0.0) return 0

        val rounded = ceil(totalMinutes).toInt()
        return rounded.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
    }

    /**
     * 把分钟数格式化为「N 分钟 / N 小时」的可读文案
     * 用于设置页的实时解析预览
     */
    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return ""
        return if (minutes % 60 == 0) {
            "${minutes / 60} 小时"
        } else {
            "$minutes 分钟"
        }
    }
}
