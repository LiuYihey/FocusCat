package com.focusguard.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.MainActivity
import com.focusguard.app.R
import com.focusguard.app.ReflectionActivity
import com.focusguard.app.data.local.AppDatabase
import com.focusguard.app.detection.AppDetectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 前台保活 + 轮询检测双引擎服务
 *
 * 核心作用（分发级可靠性）：
 * 国产 ROM（MIUI/EMUI/ColorOS/vivo 等）会丢弃或延迟无障碍事件，
 * 单靠无障碍服务检测被约束 APP 不可靠。
 * 本服务作为第二引擎，每 1.5 秒通过 UsageStatsManager 主动轮询前台应用，
 * 一旦发现被约束 APP 在前台即触发反思页，与无障碍服务互补。
 *
 * 两引擎均调用 AppDetectionManager.checkAndTrigger()，去重逻辑统一，不会重复弹窗。
 *
 * 前台服务拥有 BAL（后台 Activity 启动）权限，可在任意时刻启动 ReflectionActivity。
 */
class FocusGuardService : android.app.Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    /**
     * 上次检测到的前台包名缓存（修复 C）
     *
     * 问题：getForegroundPackage() 在用户停留同一应用 >5s 后，
     * queryEvents 无新事件、queryUsageStats 的 lastTimeUsed 可能不刷新，
     * 两个策略都返回 null，导致整个检测块被跳过——recurring 检测永远不执行。
     *
     * 解决：每次成功获取前台包名时缓存，getForegroundPackage() 返回 null 时
     * 回退使用缓存值（CACHE_VALIDITY_MS 内有效）。
     * 用户没切换应用 = 仍在缓存的应用里，用缓存值驱动 checkRecurringTrigger 是正确的。
     */
    private var lastKnownForeground: String? = null
    private var lastKnownForegroundTime: Long = 0L

    /**
     * 连续检测到非拦截应用的次数（修复 B）
     *
     * 问题：clearAppEnterSession() 在 fg 为任何非拦截应用时立即调用，
     * 但任务切换瞬间（ConfirmActivity finish → 目标应用上台的过渡帧）
     * fg 可能短暂为桌面/输入法/系统组件 → 立即清空 lastAppEnterPkg →
     * recurring 计时器还没开始走就被重置，永远到不了间隔。
     *
     * 解决：连续 CLEAR_SESSION_THRESHOLD 次检测到非拦截应用才清空会话，
     * 过滤掉 <4.5s 的瞬态切换。
     */
    private var nonBlockedForegroundCount = 0

    override fun onCreate() {
        super.onCreate()
        // 修复 F：确保守护开关和 recurring 会话状态已从 SharedPreferences 恢复
        // 正常流程下 FocusGuardApp.onCreate 已调用 init，但本服务可能被
        // AlarmManager / BootReceiver 独立拉起（进程刚重建），双重保险确保状态就绪
        AppDetectionManager.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        // 主动从数据库加载拦截列表，确保 polling 引擎启动时拦截列表已就绪
        // 否则 blockedPackages 为空，shouldBlock 全返回 false，开机后到无障碍服务
        // 连接前存在拦截失效窗口（无障碍服务可能延迟绑定，不能依赖它加载列表）
        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(this@FocusGuardService)
                val activeApps = db.blockedAppDao().getActiveBlockedApps()
                val packages = activeApps.map { it.packageName }.toSet()
                AppDetectionManager.updateBlockedApps(packages)
            } catch (e: Exception) {
                // 加载失败忽略，无障碍服务连接时会再次加载
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动 / 重启轮询任务
        startPollingIfNeeded()
        // 返回 START_STICKY，服务被杀后系统会尝试重建
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 启动前台应用轮询任务（幂等，重复调用安全）
     * 每 POLL_INTERVAL_MS 毫秒检测一次前台应用：
     * 1. 优先处理挂起的拦截请求（无障碍服务提交的，绕过BAL限制）
     * 2. 心跳检测无障碍服务连接状态，断开时尝试触发重连
     * 3. 若专注模式激活且用户切到其他应用 → 拉回 FocusCat（专注防分心）
     * 4. 若守护开启且发现被约束 APP 全新打开/切回 → 触发反思页（checkAndTrigger）
     * 5. 若守护开启且用户持续使用已进入的被约束 APP 达到「再次提醒间隔」→ 触发 recurring 反思页（checkRecurringTrigger）
     * 6. 若用户持续离开已进入的被约束 APP（连续 CLEAR_SESSION_THRESHOLD 次检测到非拦截应用）→ 清空会话
     *
     * 修复 B：原逻辑检测到任何非拦截应用就立即 clearAppEnterSession，
     * 任务切换瞬态（桌面/输入法过渡帧）会误清 recurring 计时器。
     * 现改为连续 3 次（≈4.5s）才清空，过滤瞬态。
     *
     * 修复 C：getForegroundPackage() 在用户停留同一应用 >5s 后可能返回 null，
     * 导致整个检测块被跳过、recurring 永不触发。现回退使用上次缓存的前台包名
     * （5 分钟内有效），保证长停留场景下检测不中断。
     */
    private fun startPollingIfNeeded() {
        if (pollingJob?.isActive == true) return
        var heartbeatCounter = 0
        pollingJob = serviceScope.launch {
            while (true) {
                try {
                    // 每 HEARTBEAT_INTERVAL_TICKS 次轮询做一次心跳检测
                    heartbeatCounter++
                    if (heartbeatCounter >= HEARTBEAT_INTERVAL_TICKS) {
                        heartbeatCounter = 0
                        performAccessibilityHeartbeat()
                    }

                    // 优先级 0：处理挂起的拦截请求（无障碍服务提交的）
                    // 由前台服务统一启动 Activity，避免 BAL 限制
                    val pending = AppDetectionManager.consumePendingTrigger()
                    if (pending != null) {
                        val (pkg, isRecurring) = pending
                        if (AppDetectionManager.shouldBlock(pkg) && !AppDetectionManager.isQuizShowing()) {
                            AppDetectionManager.onQuizShown(pkg)
                            launchReflectionActivity(pkg, isRecurring)
                        }
                    }

                    // 获取前台包名（getForegroundPackage 内部已集成缓存策略）
                    val fg = getForegroundPackage()

                    if (fg != null) {
                        // 优先级 1：专注模式防分心检测
                        // 用户在专注计时中切到其他应用 → 立刻拉回 FocusCat
                        if (com.focusguard.app.util.FocusSessionState.isActive &&
                            fg != packageName &&
                            fg != "com.android.systemui"
                        ) {
                            bringFocusBackToForeground()
                        }

                        // 优先级 2：被约束 APP 检测
                        if (AppDetectionManager.isProtectionEnabled) {
                            // 修复 B：去抖动清空会话
                            // 任务切换瞬间 fg 可能短暂为桌面/输入法/系统组件，
                            // 立即 clearAppEnterSession 会把 recurring 计时器清零。
                            // 连续 CLEAR_SESSION_THRESHOLD 次检测到非拦截应用才清空，
                            // 过滤掉 <4.5s 的瞬态切换。
                            val isNonBlockedNonSelf = fg != packageName &&
                                fg != "com.android.systemui" &&
                                AppDetectionManager.shouldBlock(fg).not()
                            if (isNonBlockedNonSelf) {
                                nonBlockedForegroundCount++
                                if (nonBlockedForegroundCount >= CLEAR_SESSION_THRESHOLD) {
                                    AppDetectionManager.clearAppEnterSession()
                                    nonBlockedForegroundCount = 0
                                }
                            } else {
                                // fg 是拦截应用或自身，重置计数器
                                nonBlockedForegroundCount = 0
                            }

                            // 首次打开 / 切回：事件式触发
                            if (AppDetectionManager.checkAndTrigger(fg)) {
                                AppDetectionManager.onQuizShown(fg)
                                launchReflectionActivity(fg, isRecurring = false)
                            } else if (AppDetectionManager.checkRecurringTrigger(fg)) {
                                // 持续使用再次提醒：recurring 触发
                                AppDetectionManager.onQuizShown(fg)
                                launchReflectionActivity(fg, isRecurring = true)
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 协程取消必须重新抛出，否则服务销毁后协程会延迟退出
                    throw e
                } catch (e: Exception) {
                    // 单次轮询失败不影响后续轮询
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 无障碍服务心跳检测
     *
     * 注意：Android 不允许代码直接开启无障碍服务，只能引导用户去系统设置开启。
     * 此处的作用：
     * 1. 确保前台服务自身存活（防止被系统回收导致轮询中断）
     * 2. 当无障碍断开时，前台服务自身的轮询引擎继续作为兜底
     *    （isAccessibilityConnected 状态已通过 StateFlow 自动同步给 UI，
     *     UI 会在权限页提示用户重新启用）
     */
    private fun performAccessibilityHeartbeat() {
        if (!AppDetectionManager.isProtectionEnabled) return
        // 重提升前台状态，防止系统在低内存时把服务降级
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            // 重提升失败静默忽略（如服务已被销毁）
        }
    }

    /**
     * 把 FocusCat 主界面拉回前台（专注模式防分心）
     * 通过 MainActivity Intent，Intent extra 标记 "focus_mode" 让 MainScreen 自动跳到 Focus 路由
     */
    private fun bringFocusBackToForeground() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_FOCUS_MODE_RECALL, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // 启动失败静默忽略，下次轮询会重试
        }
    }

    /**
     * 通过 UsageStatsManager 获取当前前台应用包名
     *
     * 三级策略检测，兼顾精确性、性能和可靠性：
     *
     * 策略 1（轻量）：queryEvents 查询最近 QUERY_WINDOW_MS(5s) 内的 ACTIVITY_RESUMED 事件
     *   —— 精确检测应用切换，5s 窗口小、IPC 开销低
     *
     * 策略 2（缓存）：当 queryEvents 返回空（用户停留同一应用 >5s 无新事件）时，
     *   优先使用 lastKnownForeground 缓存（2h 内有效）
     *   —— 避免每 1.5s 调用昂贵的 queryUsageStats(2h)，这是性能优化的关键
     *   —— 用户没切换应用 = 仍在缓存的应用里，用缓存值驱动检测是正确的
     *
     * 策略 3（兜底）：无缓存或缓存过期时，queryUsageStats 查询 2h 窗口取 lastTimeUsed 最大的应用
     *   —— 重量级 IPC 调用，只在首次启动或缓存过期（2h）时执行
     *
     * 关键：必须使用 queryEvents / queryUsageStats（返回所有应用数据），不能用 queryEventsForSelf
     * （后者只返回调用应用自身事件，无法监控其他应用）
     *
     * @return 前台包名，无权限或无数据时返回 null
     */
    private fun getForegroundPackage(): String? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()

        // 策略 1：queryEvents 查询事件流（轻量，5s 窗口）
        val events = try {
            usm.queryEvents(now - QUERY_WINDOW_MS, now)
        } catch (e: Exception) {
            null
        }

        if (events != null) {
            var lastFgPackage: String? = null
            val resumeEventType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                if (event.eventType == resumeEventType) {
                    lastFgPackage = event.packageName
                }
            }
            if (lastFgPackage != null) {
                // 检测到应用切换事件，更新缓存并返回
                lastKnownForeground = lastFgPackage
                lastKnownForegroundTime = now
                return lastFgPackage
            }
            // queryEvents 返回空 = 5s 内无应用切换 = 用户仍在同一应用
            // 策略 2：优先使用缓存，避免每 1.5s 调用 queryUsageStats(2h) 的性能灾难
            val cached = lastKnownForeground
            val cachedAge = now - lastKnownForegroundTime
            if (cached != null && cachedAge < CACHE_VALIDITY_MS) {
                return cached
            }
        } else {
            // queryEvents 本身异常时，也尝试缓存
            val cached = lastKnownForeground
            val cachedAge = now - lastKnownForegroundTime
            if (cached != null && cachedAge < CACHE_VALIDITY_MS) {
                return cached
            }
        }

        // 策略 3：queryUsageStats 兜底（重量级，只在无事件且无缓存时调用）
        return try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - STATS_WINDOW_MS,
                now
            )
            val result = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (result != null) {
                lastKnownForeground = result
                lastKnownForegroundTime = now
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 启动反思拦截页面
     * 前台服务拥有 BAL 权限，可直接 startActivity
     * @param targetPackage 触发的应用包名
     * @param isRecurring true=持续使用再次提醒（差异化文案），false=首次拦截
     *
     * recurring 场景下 elapsedMin 取真实累计使用时长（修复 P0-2），
     * 让用户看到自己真实的使用时长，而非设置间隔值。
     */
    private fun launchReflectionActivity(targetPackage: String, isRecurring: Boolean) {
        try {
            val elapsedMin = if (isRecurring) {
                AppDetectionManager.getAppEnterElapsedMin(targetPackage)
            } else 0
            val intent = Intent(this, ReflectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ReflectionActivity.EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(ReflectionActivity.EXTRA_IS_RECURRING, isRecurring)
                if (isRecurring) {
                    putExtra(ReflectionActivity.EXTRA_ELAPSED_MIN, elapsedMin)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            // 启动失败时重置去重状态，以便下次重试
            AppDetectionManager.onQuizDismissed()
        }
    }

    /**
     * 构建前台通知
     */
    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, FocusGuardApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        /** 前台通知 ID */
        const val NOTIFICATION_ID = 1001
        /** 轮询间隔（毫秒）- 1.5 秒，兼顾响应速度与电量 */
        private const val POLL_INTERVAL_MS = 1500L
        /** queryEvents 查询时间窗口（毫秒）- 查询最近 5 秒事件，精确检测应用切换 */
        private const val QUERY_WINDOW_MS = 5_000L
        /** queryUsageStats 兜底查询窗口（毫秒）- 2 小时，覆盖最大 recurring 间隔 120 分钟
         *  修复盲区：原 60s 窗口在用户停留同一应用 >60s 后失效，导致 recurring 永不触发 */
        private const val STATS_WINDOW_MS = 2 * 60 * 60 * 1000L
        /** 心跳检测间隔（轮询次数）- 每 30 次轮询检测一次，约 45 秒 */
        private const val HEARTBEAT_INTERVAL_TICKS = 30
        /** 前台包名缓存有效期（毫秒）- 2 小时，覆盖最大 recurring 间隔 120 分钟
         *  修复盲区：原 5 分钟缓存短于默认 30 分钟 recurring 间隔，导致持续使用 >5min 后
         *  检测停摆，recurring 永不触发。现扩展到 2 小时，确保覆盖最大间隔场景 */
        private const val CACHE_VALIDITY_MS = 2 * 60 * 60 * 1000L
        /** 清空会话去抖动阈值（连续检测到非拦截应用的次数）- 3 次 ≈ 4.5 秒，过滤瞬态切换 */
        private const val CLEAR_SESSION_THRESHOLD = 3
    }
}
