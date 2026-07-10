package com.focusguard.app.detection

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.service.FocusGuardService
import com.focusguard.app.service.ServiceRestartReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用检测管理器（单例）
 * 维护被拦截的应用包名集合、无障碍服务连接状态、守护开关状态
 * 同时负责触发去重逻辑（5秒内同一个包名只触发一次）
 */
object AppDetectionManager {

    /** SharedPreferences 文件名 */
    private const val PREFS_NAME = "focus_guard_prefs"

    /** 守护开关状态的存储 Key */
    private const val KEY_PROTECTION_ENABLED = "protection_enabled"

    /** 再次提醒间隔（分钟）的存储 Key，0=关闭 */
    private const val KEY_RECURRING_INTERVAL_MIN = "recurring_interval_min"

    /** 再次提醒间隔默认值（分钟）—— 每使用 30 分钟再次弹出问答 */
    private const val DEFAULT_RECURRING_INTERVAL_MIN = 30

    /** SharedPreferences 实例，通过 init 方法初始化 */
    private var prefs: SharedPreferences? = null

    /** Application 上下文，用于启动服务 */
    private var appContext: Context? = null

    /** 应用仓库，用于统计拦截次数 */
    private var appRepository: AppRepositoryImpl? = null

    /** 管理器内部协程作用域，用于异步统计拦截次数 */
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 注入应用仓库
     * 在 Application 初始化时调用，以便拦截触发时自增 blockCount
     */
    fun setAppRepository(repository: AppRepositoryImpl) {
        appRepository = repository
    }

    /** 被拦截的应用包名集合 */
    private val blockedPackages: MutableSet<String> = mutableSetOf()

    /** 无障碍服务是否已连接（可观察的 StateFlow，UI 可监听服务运行状态） */
    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    /** 守护功能是否开启 */
    @Volatile
    var isProtectionEnabled: Boolean = false
        private set

    /** 上次触发的应用包名（用于去重） */
    private var lastTriggeredPkg: String? = null

    /** 上次触发的时间戳（毫秒） */
    private var lastTriggerTime: Long = 0L

    /** 当前 Quiz 拦截页面是否正在显示 */
    @Volatile
    private var isQuizShowing: Boolean = false

    /** 当前 Quiz 正在拦截的应用包名 */
    @Volatile
    private var currentQuizTarget: String? = null

    /**
     * 用户最近一次「确认进入」的被约束应用包名。
     * 由 ConfirmActivity.onConfirmEnter → onAppEntered(pkg) 设置。
     * 用于：
     * 1. 防止进入应用后 polling 立即重复弹窗（checkAndTrigger 见到此值返回 false）
     * 2. 驱动 recurring quiz：持续使用同一应用超过间隔后由 checkRecurringTrigger 触发
     * 当用户离开该应用（fg != 本包名 且非自身包名）时，由 polling 调用 clearAppEnterSession() 清空
     */
    @Volatile
    private var lastAppEnterPkg: String? = null

    /** 用户进入 lastAppEnterPkg 的时间戳（毫秒） */
    @Volatile
    private var lastAppEnterTimeMs: Long = 0L

    /** 再次提醒间隔（分钟），0=关闭。默认 30，由设置页写入 */
    @Volatile
    var recurringIntervalMin: Int = DEFAULT_RECURRING_INTERVAL_MIN
        private set

    /** 去重时间窗口（毫秒），5秒内同一个包名只触发一次 */
    private const val TRIGGER_DEBOUNCE_MS = 5_000L

    /**
     * 挂起的拦截请求（无障碍服务检测到后放入，由前台服务统一启动 Activity）
     * 解决 BAL（后台 Activity 启动）限制：前台服务拥有豁免权，可可靠启动 Activity
     * 格式：Pair(包名, 是否recurring)
     *
     * 修复 ADM-1：@Volatile 只保证可见性不保证读-写原子性。
     * consumePendingTrigger 的"读后置 null"与 postPendingTrigger 的"写"存在竞态：
     * consume 读到旧值 → post 写新值 → consume 置 null 覆盖新值 → 新触发丢失。
     * 用 synchronized 保护读写复合操作。
     */
    private var pendingTrigger: Pair<String, Boolean>? = null

    /**
     * 获取并清除挂起的拦截请求
     * 由前台服务轮询时调用，返回后自动清空
     * @return 挂起的拦截 (pkg, isRecurring)，无则返回 null
     */
    fun consumePendingTrigger(): Pair<String, Boolean>? {
        synchronized(this) {
            val result = pendingTrigger
            pendingTrigger = null
            return result
        }
    }

    /**
     * 提交一个挂起的拦截请求
     * 由无障碍服务调用，避免 BAL 限制导致无法启动 Activity
     * @param pkg 目标包名
     * @param isRecurring 是否为 recurring 提醒
     */
    fun postPendingTrigger(pkg: String, isRecurring: Boolean) {
        synchronized(this) {
            pendingTrigger = Pair(pkg, isRecurring)
        }
    }

    /**
     * 初始化守护开关状态
     * 从 SharedPreferences 恢复 isProtectionEnabled 状态
     * 应在 Application 或 Activity 启动时调用
     *
     * 注意：此方法不启动前台服务。init 可能在后台进程（如无障碍服务重连）中被调用，
     * Android 12+ 后台启动前台服务受限会导致失败。FGS 启动交由前台调用方负责：
     * - MainActivity.startFocusGuardService()（前台，安全）
     * - BootReceiver（BOOT_COMPLETED 豁免 BAL 限制）
     * - setProtectionEnabled(true)（用户在 UI 操作，前台）
     *
     * @param context 上下文
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isProtectionEnabled = prefs?.getBoolean(KEY_PROTECTION_ENABLED, false) ?: false
        recurringIntervalMin = prefs?.getInt(KEY_RECURRING_INTERVAL_MIN, DEFAULT_RECURRING_INTERVAL_MIN)
            ?: DEFAULT_RECURRING_INTERVAL_MIN
        // 注意：lastAppEnterPkg / lastAppEnterTimeMs 不持久化
        // 这两个变量的语义是「用户当前在此应用内」，进程被杀后无法确定用户是否仍在该应用内。
        // 若持久化恢复，进程被杀期间用户离开又回来时，checkAndTrigger 会因 pkg == lastAppEnterPkg
        // 误判为「用户一直在该应用内」而漏掉首次拦截（安全问题）。
        // 进程被杀后 recurring 计时器重置是可接受的代价 —— 安全优先于体验。
    }

    /**
     * 更新拦截列表
     */
    fun updateBlockedApps(packages: Set<String>) {
        synchronized(this) {
            blockedPackages.clear()
            blockedPackages.addAll(packages)
        }
    }

    /**
     * 判断是否应该拦截该应用
     */
    fun shouldBlock(packageName: String): Boolean {
        if (!isProtectionEnabled) return false
        if (packageName.isBlank()) return false
        synchronized(this) {
            return blockedPackages.contains(packageName)
        }
    }

    /**
     * 设置守护开关
     * 同时将状态持久化到 SharedPreferences
     * 开启时启动前台轮询服务（recurring + 兜底检测 + BAL豁免），关闭时停止
     *
     * 关闭时必须清空所有拦截会话状态，否则残留的 lastAppEnterPkg / isQuizShowing 会导致：
     * - 再次开启守护后，对已进入过的应用 checkAndTrigger 走 `pkg == lastAppEnterPkg` 分支返回 false，漏掉首次拦截
     * - 残留的 isQuizShowing=true 让 polling 永远走「Quiz 显示同一应用」分支，永久漏拦截
     * @param enabled 是否开启守护
     */
    fun setProtectionEnabled(enabled: Boolean) {
        isProtectionEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_PROTECTION_ENABLED, enabled)?.apply()
        if (enabled) {
            startForegroundService()
            // 修复 C：注册 AlarmManager 第三方守护者，独立于 app 进程
            // 确保前台服务被 ROM 杀死后能被定时拉起，轮询/pending trigger 消费不中断
            appContext?.let { ServiceRestartReceiver.schedule(it) }
        } else {
            stopForegroundService()
            // 修复 C：取消闹钟，守护关闭后不再定时拉起服务
            appContext?.let { ServiceRestartReceiver.cancel(it) }
            // 清空所有运行时拦截状态，避免残留影响下次开启
            synchronized(this) {
                isQuizShowing = false
                currentQuizTarget = null
                lastTriggeredPkg = null
                lastTriggerTime = 0L
                lastAppEnterPkg = null
                lastAppEnterTimeMs = 0L
                pendingTrigger = null
            }
        }
    }

    /**
     * 启动前台保活+轮询服务
     * 幂等：重复调用安全
     */
    private fun startForegroundService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, FocusGuardService::class.java)
            ctx.startForegroundService(intent)
        } catch (e: Exception) {
            // 启动失败静默忽略（如 Android 12+ 后台启动前台服务限制）
        }
    }

    /**
     * 停止前台保活+轮询服务
     */
    private fun stopForegroundService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, FocusGuardService::class.java)
            ctx.stopService(intent)
        } catch (e: Exception) {
            // 停止失败静默忽略
        }
    }

    /**
     * 设置无障碍服务连接状态
     * 通过 StateFlow 通知所有监听者（如 UI），服务崩溃/断开时 UI 能立即感知
     *
     * 当服务首次连接且用户尚未操作过守护开关时，自动开启守护，
     * 解决「权限都开了但拦截不触发」的核心痛点：守护开关默认 false。
     *
     * 连接时确保前台服务在运行（即使守护已开启，也可能因系统回收导致服务死掉）。
     */
    fun setAccessibilityConnected(connected: Boolean) {
        val wasConnected = _isAccessibilityConnected.value
        _isAccessibilityConnected.value = connected
        // 无障碍服务从断开 → 连接 时，仅在用户从未操作过守护开关的情况下自动开启
        if (connected && !wasConnected && !isProtectionEnabled) {
            // 通过 contains 区分两种场景：
            // - 用户从未操作过（key 不存在）→ 自动开启守护，让拦截立即生效
            // - 用户显式关闭过（key 存在，值为 false）→ 不自动开启，尊重用户选择
            val hasUserOperated = prefs?.contains(KEY_PROTECTION_ENABLED) ?: false
            if (!hasUserOperated) {
                setProtectionEnabled(true)
            }
        }
        // 无障碍服务连接且守护开启时，确保前台服务也在运行（双引擎兜底）
        if (connected && isProtectionEnabled) {
            startForegroundService()
        }
    }

    /**
     * 获取当前拦截列表快照（用于诊断面板）
     */
    fun getBlockedPackagesSnapshot(): Set<String> = synchronized(this) { blockedPackages.toSet() }

    /**
     * 当前是否有 Quiz 拦截页面正在显示
     */
    fun isQuizShowing(): Boolean = isQuizShowing

    /**
     * 触发链路诊断：返回当前拦截链路各环节状态，供 UI 展示
     * 任意一环为 false 都会导致打开被约束 APP 时不弹出问答页
     */
    data class TriggerChainStatus(
        val isProtectionEnabled: Boolean,
        val isAccessibilityConnected: Boolean,
        val blockedAppsCount: Int,
        val isReady: Boolean
    )

    fun getTriggerChainStatus(): TriggerChainStatus {
        val snapshot = getBlockedPackagesSnapshot()
        return TriggerChainStatus(
            isProtectionEnabled = isProtectionEnabled,
            isAccessibilityConnected = _isAccessibilityConnected.value,
            blockedAppsCount = snapshot.size,
            isReady = isProtectionEnabled && _isAccessibilityConnected.value && snapshot.isNotEmpty()
        )
    }

    /**
     * 检查并触发拦截（首次打开 / 切回场景，由无障碍事件驱动）
     * 去重策略：
     * 1. 如果 Quiz 正在显示同一应用，不重复弹窗（避免重复 Intent）
     * 2. 如果 Quiz 正在显示不同应用，允许触发（由 onNewIntent 处理）
     * 3. 若 pkg == lastAppEnterPkg（用户刚「进入」该应用且未离开），返回 false
     *    —— 交由 checkRecurringTrigger 处理持续使用再提醒，避免进入后立即重复弹窗
     * 4. Quiz 未显示时，使用 5 秒时间窗口去重防止事件抖动
     * @param pkg 触发的应用包名
     * @return 是否应该触发（true 表示应该弹出拦截页，false 表示被去重过滤）
     */
    fun checkAndTrigger(pkg: String): Boolean {
        if (!shouldBlock(pkg)) return false

        val now = System.currentTimeMillis()
        val shouldTrigger = synchronized(this) {
            when {
                // Quiz 正在显示同一应用，不重复弹窗
                isQuizShowing && pkg == currentQuizTarget -> false
                // Quiz 正在显示不同应用，允许触发新拦截（onNewIntent 会更新题目）
                isQuizShowing && pkg != currentQuizTarget -> {
                    currentQuizTarget = pkg
                    lastTriggeredPkg = pkg
                    lastTriggerTime = now
                    true
                }
                // 用户已「进入」此应用且未离开：首次拦截交给 checkRecurringTrigger，
                // 这里返回 false 防止 polling 在进入应用后立即重复弹窗
                pkg == lastAppEnterPkg -> false
                // Quiz 未显示时，使用时间窗口去重防止事件抖动
                pkg == lastTriggeredPkg && now - lastTriggerTime < TRIGGER_DEBOUNCE_MS -> false
                else -> {
                    lastTriggeredPkg = pkg
                    lastTriggerTime = now
                    true
                }
            }
        }
        if (shouldTrigger) {
            managerScope.launch { appRepository?.incrementBlockCount(pkg) }
        }
        return shouldTrigger
    }

    /**
     * 检查「持续使用再次提醒」（recurring quiz，由轮询引擎驱动）
     *
     * 触发条件：
     * 1. 守护开启且 pkg 在拦截列表
     * 2. 当前没有 Quiz 在显示
     * 3. pkg == lastAppEnterPkg（用户已确认进入此应用）
     * 4. recurringIntervalMin > 0（功能开启）
     * 5. 距上次进入时间 >= 间隔
     *
     * @param pkg 当前前台应用包名
     * @return 是否应该弹出 recurring 问答页
     */
    fun checkRecurringTrigger(pkg: String): Boolean {
        if (!shouldBlock(pkg)) return false
        if (recurringIntervalMin <= 0) return false

        val now = System.currentTimeMillis()
        val intervalMs = recurringIntervalMin.toLong() * 60_000L
        val shouldTrigger = synchronized(this) {
            when {
                isQuizShowing -> false
                pkg != lastAppEnterPkg -> false
                now - lastAppEnterTimeMs < intervalMs -> false
                else -> {
                    // 命中再次提醒：更新触发态，标记 Quiz 即将显示
                    currentQuizTarget = pkg
                    lastTriggeredPkg = pkg
                    lastTriggerTime = now
                    true
                }
            }
        }
        if (shouldTrigger) {
            managerScope.launch { appRepository?.incrementBlockCount(pkg) }
        }
        return shouldTrigger
    }

    /**
     * 计算距下次 recurring 提醒的剩余毫秒（用于 UI 展示，<0 表示已到点）
     * 仅当 pkg == lastAppEnterPkg 且功能开启时有意义
     */
    fun millisToNextRecurring(pkg: String): Long {
        if (recurringIntervalMin <= 0) return -1L
        synchronized(this) {
            if (pkg != lastAppEnterPkg) return -1L
            val intervalMs = recurringIntervalMin.toLong() * 60_000L
            return intervalMs - (System.currentTimeMillis() - lastAppEnterTimeMs)
        }
    }

    /**
     * 标记 Quiz 拦截页面已显示
     * 应在拦截页 onCreate/onNewIntent 时调用
     * @param pkg 当前拦截的应用包名
     */
    fun onQuizShown(pkg: String) {
        synchronized(this) {
            isQuizShowing = true
            currentQuizTarget = pkg
        }
    }

    /**
     * 标记 Quiz 拦截页面已关闭
     * 应在 QuizActivity onDestroy 时调用
     * 同时重置去重状态，确保下次打开应用能立即拦截，避免漏拦截
     *
     * 注意：不清空 lastAppEnterPkg —— 它由 onAppEntered / clearAppEnterSession 单独管理，
     * 因为 Quiz 关闭后用户可能「进入应用」（需保留会话）或「退出获食物」（需清空会话）。
     *
     * 特别地，若当前 Quiz 是 recurring 触发的（lastTriggeredPkg == lastAppEnterPkg 且非空），
     * 视为"已经提醒过"：将 lastAppEnterTimeMs 重置为 now，让下个间隔从此刻重新计时，
     * 防止用户按返回键关闭后被 polling 立即再次弹窗（修复 P0-1）。
     */
    fun onQuizDismissed() {
        synchronized(this) {
            val wasRecurring = lastAppEnterPkg != null && lastTriggeredPkg == lastAppEnterPkg
            isQuizShowing = false
            currentQuizTarget = null
            // 重置去重状态，确保 Quiz 关闭后能立即重新拦截
            lastTriggeredPkg = null
            lastTriggerTime = 0L
            // recurring Quiz 被关闭（返回键 / 跳页等）→ 重置计时起点，
            // 避免下一次 polling 立即命中 interval 条件而连环弹窗
            if (wasRecurring && lastAppEnterPkg != null) {
                lastAppEnterTimeMs = System.currentTimeMillis()
            }
        }
    }

    /**
     * 获取用户在 lastAppEnterPkg 内的累计使用分钟数（真实时长，非间隔值）
     * 用于 recurring Quiz 文案展示真实使用时长（修复 P0-2）
     * @param pkg 当前应用包名，仅当与 lastAppEnterPkg 一致时返回有效值
     * @return 已使用分钟数（向下取整），无活跃会话时返回 0
     */
    fun getAppEnterElapsedMin(pkg: String): Int {
        synchronized(this) {
            if (pkg != lastAppEnterPkg || lastAppEnterTimeMs == 0L) return 0
            val elapsedMs = System.currentTimeMillis() - lastAppEnterTimeMs
            return (elapsedMs / 60_000L).toInt().coerceAtLeast(0)
        }
    }

    /**
     * 用户在确认页选择「还是进入 X」时调用
     * 记录进入的应用包名与时间，作为 recurring quiz 计时起点，
     * 同时让 checkAndTrigger 在此期间对此包返回 false（防进入后立即重复弹窗）
     */
    fun onAppEntered(pkg: String) {
        synchronized(this) {
            lastAppEnterPkg = pkg
            lastAppEnterTimeMs = System.currentTimeMillis()
            // 进入应用视为本「拦截会话」已消化，重置去重以便后续正常触发
            lastTriggeredPkg = null
            lastTriggerTime = 0L
        }
    }

    /**
     * 清空「进入应用」会话状态
     * 由轮询引擎在检测到用户离开被约束应用（切到桌面 / 其他非自身应用）时调用，
     * 确保「退出应用再回来」能被 checkAndTrigger 正常拦截为新会话
     */
    fun clearAppEnterSession() {
        synchronized(this) {
            lastAppEnterPkg = null
            lastAppEnterTimeMs = 0L
        }
    }

    /**
     * 由设置页调用，更新再次提醒间隔并持久化
     * @param minutes 间隔分钟数，0=关闭，范围 [0, 120]
     */
    fun setRecurringInterval(minutes: Int) {
        val safe = minutes.coerceIn(0, 120)
        recurringIntervalMin = safe
        prefs?.edit()?.putInt(KEY_RECURRING_INTERVAL_MIN, safe)?.apply()
    }

    /**
     * 重置去重状态（可选，用于测试或手动重置）
     */
    fun resetTriggerState() {
        synchronized(this) {
            lastTriggeredPkg = null
            lastTriggerTime = 0L
        }
    }
}
