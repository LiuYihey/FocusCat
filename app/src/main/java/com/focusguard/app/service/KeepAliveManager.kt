package com.focusguard.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusguard.app.detection.AppDetectionManager
import com.focusguard.app.util.PermissionHelper

/**
 * 后台保活总管（需求 1 + 4 的核心）
 *
 * 背景：OPPO / 一加 / realme 的 ColorOS 会在应用退出后台一段时间后
 * 把应用进程冻结甚至杀掉，并连带关闭本应用的无障碍服务。
 * 表现为「无障碍开关自己变灰 / 守护失效」，这是本 App 在国产 ROM 上最大的使用障碍。
 *
 * 核心策略（多路冗余，互为兜底）：
 *
 * 1. 【前台服务】FocusGuardService 常驻通知，进程存活 → 无障碍服务不被解绑。
 *    这是最可靠的一层，ColorOS 的"允许后台运行"白名单直接决定这一层是否生效。
 *
 * 2. 【JobScheduler 守护】setPersisted(true) 的周期任务，由系统调度，
 *    即便 App 进程被完全杀死，系统仍会拉起进程执行任务 → 进程一旦重建，
 *    系统会自动把仍处于"开启"状态的无障碍服务重新绑定回来。
 *    这是绕过 ColorOS 杀后台限制最有效的手段（AlarmManager 在深度休眠下会被大幅延迟，
 *    JobScheduler 则由系统保证执行）。
 *
 * 3. 【AlarmManager 守护】精确闹钟（无精确闹钟权限时降级为 setAndAllowWhileIdle /
 *    setInexactRepeating），与 JobScheduler 交错执行，避免单一机制被 ROM 掐断。
 *
 * 4. 【自检与引导】每次守护心跳都会检查无障碍服务是否仍处于系统"开启"状态：
 *    - 系统设置里仍是开启、但当前进程没有连上 → 说明被 ROM 杀进程后未重绑，
 *      通过重建进程（JobScheduler 拉起）自动恢复；
 *    - 系统设置里已被关闭 → 说明是用户或系统彻底关掉了开关，
 *      此时任何代码都无法自动恢复（Android 不允许 App 自行开启无障碍服务），
 *      只能发通知引导用户手动重新开启。
 */
object KeepAliveManager {

    /** JobScheduler 任务 ID */
    private const val JOB_ID = 42001

    /** AlarmManager 守护闹钟的 PendingIntent 请求码 */
    private const val ALARM_REQUEST_CODE = 42002

    /** JobScheduler 周期（毫秒）——系统最小周期为 15 分钟 */
    private const val JOB_INTERVAL_MS = 15 * 60 * 1000L

    /** 精确闹钟可用时的守护间隔（毫秒） */
    private const val ALARM_INTERVAL_EXACT_MS = 3 * 60 * 1000L

    /** 精确闹钟不可用时的降级守护间隔（毫秒） */
    private const val ALARM_INTERVAL_INEXACT_MS = 5 * 60 * 1000L

    /** 两次"无障碍被关闭"通知的最小间隔（毫秒），避免刷屏 */
    private const val NOTIFY_THROTTLE_MS = 10 * 60 * 1000L

    /** 上次发送"无障碍被关闭"通知的时间戳 */
    @Volatile
    private var lastDisabledNotifyAt = 0L

    /**
     * 启动全部保活机制（守护开启时调用）
     */
    fun schedule(context: Context) {
        scheduleJob(context)
        scheduleAlarm(context)
    }

    /**
     * 停止全部保活机制（守护关闭时调用）
     */
    fun cancel(context: Context) {
        cancelJob(context)
        cancelAlarm(context)
    }

    // ────────────────────────── JobScheduler ──────────────────────────

    /**
     * 注册 JobScheduler 周期任务
     *
     * setPersisted(true)：设备重启后任务依然保留（需 RECEIVE_BOOT_COMPLETED 权限，已在清单声明）。
     * 这是"进程被 ROM 杀掉后仍能自动复活"的关键。
     */
    private fun scheduleJob(context: Context) {
        try {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, KeepAliveJobService::class.java)
            )
                .setPeriodic(JOB_INTERVAL_MS)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            jobScheduler.schedule(jobInfo)
        } catch (e: Exception) {
            // 调度失败不影响其它保活机制
        }
    }

    private fun cancelJob(context: Context) {
        try {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return
            jobScheduler.cancel(JOB_ID)
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ────────────────────────── AlarmManager ──────────────────────────

    /**
     * 注册 AlarmManager 守护闹钟
     *
     * Android 12+ 精确闹钟需要 SCHEDULE_EXACT_ALARM 权限且用户可能已撤销，
     * 因此必须先 canScheduleExactAlarms() 判断，失败则降级为
     * setAndAllowWhileIdle（Doze 下可唤醒，仅时间不精确）。
     */
    private fun scheduleAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val pendingIntent = buildAlarmPendingIntent(context)
            // 先取消旧的，避免重复注册
            alarmManager.cancel(pendingIntent)
            val triggerAt = android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_EXACT_MS
            if (canScheduleExactAlarms(context)) {
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
        } catch (e: Exception) {
            // 降级到不精确重复闹钟
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_INEXACT_MS,
                    ALARM_INTERVAL_INEXACT_MS,
                    buildAlarmPendingIntent(context)
                )
            } catch (e2: Exception) {
                // 忽略
            }
        }
    }

    private fun cancelAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            alarmManager.cancel(buildAlarmPendingIntent(context))
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ServiceRestartReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 是否可以调度精确闹钟
     * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限（已声明），但用户可随时撤销。
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false
            alarmManager.canScheduleExactAlarms()
        } catch (e: Exception) {
            false
        }
    }

    // ────────────────────────── 运行时动作 ──────────────────────────

    /**
     * 确保前台保活服务处于运行状态
     * 幂等：服务已运行时重复调用只是多一次 onStartCommand，无副作用。
     */
    fun ensureForegroundService(context: Context) {
        if (!AppDetectionManager.isProtectionEnabled) return
        try {
            val intent = Intent(context, FocusGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限，忽略等待下一轮守护
        }
    }

    /**
     * 无障碍服务健康自检
     *
     * 两种失效场景分别处理：
     * A. 系统设置里仍是"开启"，但当前进程没连上 → 被 ROM 杀进程后未重绑。
     *    重建进程（保活任务本身就在新进程中执行）→ 系统自动重绑，无需打扰用户。
     * B. 系统设置里已被关闭 → Android 不允许 App 自行开启无障碍服务，
     *    只能发通知引导用户手动重新开启。
     */
    fun checkAccessibilityHealth(context: Context) {
        val enabledInSettings = PermissionHelper.checkAccessibilityPermission(context)
        if (enabledInSettings) {
            // 场景 A：开关还在，只是当前进程没连上。
            // 保持前台服务与守护闹钟存活，进程重建后系统会自动重绑。
            ensureForegroundService(context)
        } else {
            // 场景 B：开关被关闭 —— 引导用户手动重新开启
            AppDetectionManager.markAccessibilityDisabled()
            notifyAccessibilityDisabled(context)
        }
    }

    /**
     * 发送"无障碍服务已被关闭"的引导通知
     * 点击后直达系统无障碍设置页。
     */
    private fun notifyAccessibilityDisabled(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastDisabledNotifyAt < NOTIFY_THROTTLE_MS) return
        lastDisabledNotifyAt = now
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? android.app.NotificationManager ?: return
            val settingsIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = PendingIntent.getActivity(
                context,
                43001,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(
                context,
                com.focusguard.app.FocusGuardApp.NOTIFICATION_CHANNEL_ALERT
            )
                .setContentTitle("守护已暂停")
                .setContentText("系统关闭了无障碍服务，点击重新开启守护")
                .setSmallIcon(com.focusguard.app.R.drawable.ic_shield)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(ACCESSIBILITY_DISABLED_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // 通知失败不影响主流程
        }
    }

    /** "无障碍被关闭"引导通知 ID */
    const val ACCESSIBILITY_DISABLED_NOTIFICATION_ID = 1002
}
