package com.focusguard.app.util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 专注会话全局状态（进程内单例）
 *
 * 用于跨组件通信：FocusScreen 进入专注模式时置位，FocusGuardService 检测到
 * 用户切换到其他应用时若发现此标志为 true，则将 FocusCat 拉回前台（覆盖其他应用）。
 *
 * 仅内存态：进程被杀后状态丢失，专注会话也随之结束（符合用户预期：退出即结束）。
 */
object FocusSessionState {
    /** 专注模式是否激活 */
    private val active = AtomicBoolean(false)

    /** 专注开始时间戳（毫秒），用于服务端校验和恢复 UI */
    private val startedAt = AtomicLong(0L)

    /** 当前专注会话已积累的毫秒数（用于服务端推送奖励时判断是否满 30 分钟） */
    private val accumulatedMs = AtomicLong(0L)

    val isActive: Boolean get() = active.get()
    val startedAtMillis: Long get() = startedAt.get()
    val accumulatedMillis: Long get() = accumulatedMs.get()

    /** 进入专注模式 */
    fun start() {
        active.set(true)
        startedAt.set(System.currentTimeMillis())
        accumulatedMs.set(0L)
    }

    /** 更新累计时长（FocusScreen 每秒调用一次） */
    fun updateAccumulated(ms: Long) {
        if (active.get()) accumulatedMs.set(ms)
    }

    /** 退出专注模式 */
    fun stop() {
        active.set(false)
        startedAt.set(0L)
        accumulatedMs.set(0L)
    }
}
