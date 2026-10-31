package com.focusguard.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.detection.AppDetectionManager

/**
 * 前台服务第三方守护者（修复 C）
 *
 * 根本问题：双引擎（无障碍服务 + 前台服务）运行在同一进程，ROM 杀进程时一起死，
 * 无障碍服务内的看门狗也随之死亡，无法互相拉起。导致：
 * - 前台服务被杀后轮询停摆，recurring 永不触发
 * - 无障碍服务检测到事件后 postPendingTrigger，但消费者（前台服务）已死，问答页不弹出
 * - 用户必须手动点 FocusCat 才能恢复
 *
 * 解决方案：AlarmManager 作为独立于 app 进程的第三方守护者。
 * 系统闹钟服务由系统进程托管，app 被杀后闹钟仍会触发。
 * 每 5 分钟检查守护开关状态，若开启则拉起前台服务，确保：
 * 1. 轮询引擎持续运行，recurring 按时触发
 * 2. pending trigger 有消费者，问答页能正常弹出
 * 3. 即使无障碍服务被杀，前台服务也能独立兜底检测
 *
 * 调度时机：
 * - setProtectionEnabled(true) 时注册闹钟
 * - setProtectionEnabled(false) 时取消闹钟
 * - BootReceiver 开机时恢复闹钟
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // 恢复守护开关状态（进程被杀后单例状态丢失，需从 prefs 恢复）
        AppDetectionManager.init(context)
        // 仅在守护开启时拉起前台服务
        if (!AppDetectionManager.isProtectionEnabled) return

        val serviceIntent = Intent(context, FocusGuardService::class.java)
        try {
            // startForegroundService 由系统调度，即使 app 在后台也能启动
            // FocusGuardService.onStartCommand 是幂等的，重复调用安全
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            // Android 12+ 极端后台场景可能受限，忽略避免崩溃
        }
        // 需求 1+4：闹钟触发时顺带做一次无障碍健康自检
        // 若系统开关被关闭则发通知引导用户重新开启；
        // 若开关还在只是进程被杀，此刻进程已被系统重建，系统会自动重绑无障碍服务。
        KeepAliveManager.checkAccessibilityHealth(context)

        // 需求 1+4（实测修复）：三种广播各自续自己的链，互不推后对方的排期。
        // 2 分钟看门狗广播不能碰 KeepAliveManager 的精确闹钟排期 ——
        // 看门狗每 2 分钟触发一次，若每次都把精确闹钟重排到"当前时刻 + 3 分钟"，
        // 精确闹钟会被无限推后，永远等不到触发点（实测 dumpsys 中该闹钟被反复取消重排）。
        when (intent?.action) {
            KeepAliveManager.ACTION_KEEPALIVE_ALARM -> {
                // 精确闹钟链：续上下一次
                KeepAliveManager.continueAlarmChain(context)
            }
            ACTION_WATCHDOG -> {
                // 看门狗链（链式精确闹钟）：续上下一次
                schedule(context)
            }
            else -> {
                // 一次性服务重启广播：不需要续链（它本身就是一次性的）
            }
        }
    }

    companion object {
        /** 看门狗闹钟间隔（毫秒）- 2 分钟
         *  修复 Bug 3：原 5 分钟间隔过长，服务被杀后最多需等 5 分钟才能恢复，
         *  缩短到 2 分钟提升恢复及时性 */
        private const val ALARM_INTERVAL_MS = 2 * 60 * 1000L

        /** 看门狗闹钟的 PendingIntent 请求码 */
        private const val REQUEST_CODE = 2001

        /** 一次性重启闹钟的 PendingIntent 请求码（FocusGuardService.onDestroy/onTaskRemoved 调用） */
        const val REQUEST_CODE_RESTART = 2002

        /** 看门狗广播的 action（与 KeepAliveManager 的精确闹钟区分） */
        private const val ACTION_WATCHDOG = "com.focusguard.app.action.SERVICE_WATCHDOG"

        /** 服务重启广播的 action（FocusGuardService.onDestroy/onTaskRemoved 触发） */
        const val ACTION_RESTART = "com.focusguard.app.action.SERVICE_RESTART"

        /**
         * 注册看门狗闹钟（守护开启时调用）
         *
         * 改用链式精确闹钟（原为 setInexactRepeating）：
         * setInexactRepeating 在 Doze / 深度休眠下会被系统大幅延迟甚至丢弃，
         * 实测 dumpsys 中该闹钟带 window=+1m30s0ms 的不精确窗口，
         * 这正是"退出后台后无障碍服务迟迟不恢复"的直接原因之一。
         *
         * 链式模型：每次触发时在 onReceive 里续上下一次（见 KeepAliveManager 的闹钟链）。
         * 无精确闹钟权限时降级为 setAndAllowWhileIdle（Doze 下仍可唤醒）。
         */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val pendingIntent = buildPendingIntent(context)
            // 先取消已有闹钟，避免重复注册导致多次触发
            alarmManager.cancel(pendingIntent)
            val triggerAt = android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS
            if (KeepAliveManager.canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }

        /**
         * 取消定时闹钟（守护关闭时调用）
         */
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            alarmManager.cancel(buildPendingIntent(context))
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                action = ACTION_WATCHDOG
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
