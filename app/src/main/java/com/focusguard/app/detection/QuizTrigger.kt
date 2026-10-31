package com.focusguard.app.detection

/**
 * 拦截弹窗的触发原因
 *
 * 决定弹窗用哪套文案/图标展示：
 * - FIRST：首次打开被约束应用（用户还没做选择）
 * - RECURRING：持续使用达到设置里的「再次提醒」间隔
 * - EXPIRY：用户自己在问答页选定的「本次使用时长」已到点
 */
enum class QuizTrigger {
    /** 首次打开被约束应用 */
    FIRST,

    /** 持续使用再次提醒（recurring 间隔到期） */
    RECURRING,

    /** 选定的使用时长到期提醒 */
    EXPIRY;

    companion object {
        /** 解析字符串，非法值回退到 FIRST */
        fun fromName(value: String?): QuizTrigger =
            entries.firstOrNull { it.name == value } ?: FIRST
    }
}

/**
 * 「使用时长到期」事件
 *
 * @param durationMinutes 用户当初在问答页选定的使用时长（分钟）
 */
data class ExpiryReminder(
    val durationMinutes: Int
)
