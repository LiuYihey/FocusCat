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
    }

    companion object {
        /** 闹钟间隔（毫秒）- 5 分钟，AlarmManager 最小重复间隔建议 5 分钟 */
        private const val ALARM_INTERVAL_MS = 5 * 60 * 1000L

        /** PendingIntent 请求码 */
        private const val REQUEST_CODE = 2001

        /**
         * 注册定时闹钟（守护开启时调用）
         * 使用 setInexactRepeating：系统合并多应用闹钟更省电，且不受 Android 12+ 精确闹钟限制
         */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val intent = Intent(context, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 先取消已有闹钟，避免重复注册导致多次触发
            alarmManager.cancel(pendingIntent)
            // setInexactRepeating：系统会合并多个应用的闹钟，更省电
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
                ALARM_INTERVAL_MS,
                pendingIntent
            )
        }

        /**
         * 取消定时闹钟（守护关闭时调用）
         */
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val intent = Intent(context, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
