package com.focusguard.app.detection

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.service.FocusGuardService
import com.focusguard.app.service.KeepAliveManager
import com.focusguard.app.service.ServiceRestartReceiver
import com.focusguard.app.util.PermissionHelper
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

    /** 临时豁免窗口的存储 Key 前缀（需求 3），完整键 = 前缀 + 包名 */
    private const val KEY_EXEMPTION_PREFIX = "exempt_until_"

    /** 临时豁免「时长（分钟）」的存储 Key 前缀，完整键 = 前缀 + 包名 */
    private const val KEY_EXEMPTION_DURATION_PREFIX = "exempt_dur_"

    /** 无障碍服务被系统关闭的标记（需求 1/4），跨进程存活 */
    private const val KEY_ACCESSIBILITY_DISABLED = "accessibility_disabled"

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

    /**
     * 无障碍服务是否被系统/用户关闭（需求 1+4）
     *
     * 与 isAccessibilityConnected 区分：
     * - isAccessibilityConnected=false 仅表示当前进程没有连上（可能只是被 ROM 杀进程后未重绑）
     * - isAccessibilityDisabled=true 表示系统设置里的无障碍开关已被关闭，
     *   此时 Android 不允许 App 自行开启，只能引导用户手动恢复
     */
    private val _isAccessibilityDisabled = MutableStateFlow(false)
    val isAccessibilityDisabled: StateFlow<Boolean> = _isAccessibilityDisabled.asStateFlow()

    /**
     * 标记无障碍服务已被系统关闭
     * 由 KeepAliveManager.checkAccessibilityHealth 在检测到系统设置里的开关已关闭时调用
     *
     * 持久化（实测修复）：ColorOS 的典型表现是"杀进程 + 关开关"，
     * 进程被杀后内存态全部丢失，用户重新打开 App 时又看到"正在恢复中"，
     * 永远不知道守护其实已被系统关闭。写入 SharedPreferences 让状态跨进程存活。
     */
    fun markAccessibilityDisabled() {
        _isAccessibilityDisabled.value = true
        prefs?.edit()?.putBoolean(KEY_ACCESSIBILITY_DISABLED, true)?.apply()
    }

    /**
     * 标记无障碍服务已恢复连接
     * AppMonitorService.onServiceConnected 时调用，清除「已关闭」标记
     */
    fun markAccessibilityRestored() {
        _isAccessibilityDisabled.value = false
        prefs?.edit()?.remove(KEY_ACCESSIBILITY_DISABLED)?.apply()
    }

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

    /**
     * 临时豁免窗口（需求 3）
     *
     * Key = 被约束应用包名，Value = 豁免截止时间戳（毫秒）。
     * 用户在弹窗中选择「本次使用时长」并确认进入后写入，
     * 该时长内再次进入该应用不再弹窗；到期后自动移除并恢复拦截提醒。
     */
    private val temporaryExemptUntil: MutableMap<String, Long> = mutableMapOf()

    /**
     * 临时豁免窗口对应的「时长（分钟）」
     *
     * Key = 被约束应用包名，Value = 用户当时选中的分钟数。
     * 用于到期提醒时告诉用户「你当时说的是 N 分钟」，也让恢复流程能读回真实时长。
     */
    private val temporaryExemptDuration: MutableMap<String, Int> = mutableMapOf()

    /**
     * 待提醒的「时长到期」事件
     *
     * Key = 包名，Value = [ExpiryReminder]。
     *
     * 只有「用户仍在该应用内、直到豁免到期」时才会写入，
     * 这样到期提醒只会在用户确实还在里面时弹出；
     * 用户提前退出（clearAppEnterSession）则不会留下到期事件。
     */
    private val pendingExpiryReminder: MutableMap<String, ExpiryReminder> = mutableMapOf()

    /**
     * 用户当前停留的应用（无障碍窗口事件驱动，即时信号）
     *
     * 为什么不用轮询引擎的 UsageStats 前台包名：
     * UsageStats 的前台信息是批量延迟入库的，
     * 而轮询只回看 5 秒窗口 —— 切换事件若延迟入库超过 5 秒就永远不会被看到，
     * 缓存会一直停在旧包名（最长 2 小时）。
     * 若到期时只信这个包名，就会误判「用户已离开」→ 既不弹提醒也不恢复拦截。
     *
     * 无障碍服务的窗口事件是实时的，且本 App 的拦截链路本就依赖它，
     * 因此用它作为权威信号；轮询包名只作为降级兜底。
     */
    @Volatile
    private var foregroundAppPkg: String? = null

    /** 再次提醒间隔（分钟），0=关闭。默认 30，由设置页写入 */
    @Volatile
    var recurringIntervalMin: Int = DEFAULT_RECURRING_INTERVAL_MIN
        private set

    /** 去重时间窗口（毫秒），5秒内同一个包名只触发一次 */
    private const val TRIGGER_DEBOUNCE_MS = 5_000L

    /**
     * 挂起的拦截请求（无障碍服务检测到后放入，由前台服务统一启动 Activity）
     * 解决 BAL（后台 Activity 启动）限制：前台服务拥有豁免权，可可靠启动 Activity
     * 格式：Pair(包名, 触发类型)
     *
     * 修复 ADM-1：@Volatile 只保证可见性不保证读-写原子性。
     * consumePendingTrigger 的"读后置 null"与 postPendingTrigger 的"写"存在竞态：
     * consume 读到旧值 → post 写新值 → consume 置 null 覆盖新值 → 新触发丢失。
     * 用 synchronized 保护读写复合操作。
     */
    private var pendingTrigger: Pair<String, QuizTrigger>? = null

    /**
     * 获取并清除挂起的拦截请求
     * 由前台服务轮询时调用，返回后自动清空
     * @return 挂起的拦截 (pkg, trigger)，无则返回 null
     */
    fun consumePendingTrigger(): Pair<String, QuizTrigger>? {
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
     * @param trigger 触发类型（首次拦截 / 再次提醒 / 时长到期）
     */
    fun postPendingTrigger(pkg: String, trigger: QuizTrigger) {
        synchronized(this) {
            pendingTrigger = Pair(pkg, trigger)
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
        // 恢复「无障碍已被系统关闭」标记（需求 1/4）：
        // ColorOS 杀进程后内存态丢失，用户回到 App 会误以为"正在恢复中"。
        // 但若此刻系统开关其实已恢复（用户已手动开回来），应以系统实际状态为准 → 清除标记。
        // 注意：必须无条件以系统实际状态为准，不能只在标记为 true 时才去查系统开关 ——
        // 否则进程重建后标记会一直挂着，设置页永远显示"已被系统关闭"（实测误报）。
        if (!PermissionHelper.checkAccessibilityPermission(context)) {
            _isAccessibilityDisabled.value =
                prefs?.getBoolean(KEY_ACCESSIBILITY_DISABLED, false) ?: false
        } else {
            _isAccessibilityDisabled.value = false
            prefs?.edit()?.remove(KEY_ACCESSIBILITY_DISABLED)?.apply()
        }
        // 恢复临时豁免窗口（需求 3）：进程被杀后重启，豁免窗口依然有效
        restoreExemptions()
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
            // 临时豁免窗口内不拦截（用户已明确选择本次使用时长）
            if (isInExemptionWindow(packageName)) return false
            return blockedPackages.contains(packageName)
        }
    }

    /**
     * 纯拦截列表判断（不考虑守护开关与临时豁免）
     *
     * 用途：前台服务的「离开应用 → 清空进入会话」去抖判断。
     * 必须用本方法而非 shouldBlock：豁免窗口内 shouldBlock 返回 false，
     * 会把用户仍停留在被约束应用的状态误判为「已离开」而清空会话，
     * 导致豁免到期后无法准确触发提醒、实时使用时长也会丢失。
     */
    fun isBlockedTarget(packageName: String): Boolean {
        synchronized(this) { return blockedPackages.contains(packageName) }
    }

    /**
     * 是否处于临时豁免窗口内（纯查询，无副作用）
     *
     * 到期的清理交给 [settleExpiredExemption]，
     * 保证「到期」这一状态只被结算一次，不会被多个调用方重复消费。
     */
    private fun isInExemptionWindow(packageName: String): Boolean {
        val until = temporaryExemptUntil[packageName] ?: return false
        return System.currentTimeMillis() < until
    }

    /**
     * 结算已到期的豁免窗口（由轮询引擎调用）
     *
     * 到期即移除豁免（恢复拦截），让该应用重新进入被拦截状态。
     *
     * 只有「用户此刻仍停留在该应用内」，
     * 才判定为「时长到期」事件并登记到 pendingExpiryReminder；
     * 用户提前退出（前台已切走）则不产生到期提醒 ——
     * 自然也就没提醒，下次再打开该应用按首次拦截处理。
     *
     * 「是否还在应用内」由 [isUserInside] 判定：
     * 优先用无障碍窗口事件记录的即时信号（零延迟、能捕捉退出后重进），
     * 缺失时才回退到轮询引擎观测到的前台包名。
     *
     * 结算结果是幂等的：豁免一旦移除就不会重复到期，
     * 保证「到期」这一状态只被结算一次。
     *
     * 必须由轮询引擎调用（而非 checkAndTrigger）：
     * 只有轮询引擎掌握「此刻是否已到点」这个权威信息。
     *
     * @param foregroundPkg 轮询引擎观测到的前台包名（降级兜底用）
     * @return 需要弹出「时间到了」提醒的包名列表（可能为空）
     */
    fun settleExpiredExemptions(foregroundPkg: String?): List<String> {
        synchronized(this) {
            val now = System.currentTimeMillis()
            val expired = temporaryExemptUntil.filterValues { it <= now }.keys.toList()
            if (expired.isEmpty()) return emptyList()

            val needReminder = mutableListOf<String>()
            for (pkg in expired) {
                val until = temporaryExemptUntil[pkg] ?: continue
                // 已到期：移除豁免并恢复拦截
                temporaryExemptUntil.remove(pkg)
                val durationMin = temporaryExemptDuration.remove(pkg) ?: 0
                prefs?.edit()
                    ?.remove(exemptionKey(pkg))
                    ?.remove(exemptionDurationKey(pkg))?.apply()

                // 「用户此刻是否仍在该应用内」：
                // 优先用无障碍窗口事件记录的即时信号（无延迟），
                // 缺失时才回退到轮询引擎观测到的前台包名。
                if (isUserInside(pkg, foregroundPkg)) {
                    // 用户仍在该应用内 → 登记「时长到期」事件，供弹窗显示差异化文案
                    pendingExpiryReminder[pkg] = ExpiryReminder(durationMin)
                    needReminder.add(pkg)
                }

                // 豁免窗口结束 → 「进入会话」随之结束。
                // 否则 checkAndTrigger 会命中「pkg == lastAppEnterPkg」分支跳过拦截，
                // 用户可以无限期继续使用该应用。
                if (lastAppEnterPkg == pkg) {
                    lastAppEnterPkg = null
                    lastAppEnterTimeMs = 0L
                }
            }
            return needReminder
        }
    }

    /**
     * 记录「用户当前停留在哪个应用」
     *
     * 由无障碍服务的窗口事件驱动（即时信号，零延迟）：
     * 用户切到哪个应用就立刻更新，退出也会立刻更新。
     *
     * 只接受「窗口获得焦点/处于活动状态」的事件，
     * 避免应用失去焦点时（弹出输入法、系统弹窗）被误判为已切换。
     *
     * @param pkg 当前获得窗口焦点的应用包名
     */
    fun onForegroundAppChanged(pkg: String) {
        if (pkg.isBlank()) return
        foregroundAppPkg = pkg
    }

    /**
     * 用户当前是否停留在指定应用内
     *
     * 优先用无障碍窗口事件记录的即时信号；
     * 若该信号缺失（例如无障碍服务刚重启），回退到轮询引擎观测到的前台包名。
     *
     * @param pkg 目标应用包名
     * @param polledForegroundPkg 轮询引擎观测到的前台包名（可为 null）
     * @return true 表示用户此刻仍停留在该应用内
     */
    fun isUserInside(pkg: String, polledForegroundPkg: String?): Boolean {
        if (pkg.isBlank()) return false
        val immediate = foregroundAppPkg
        if (immediate != null) return immediate == pkg
        return polledForegroundPkg == pkg
    }

    /**
     * 设置临时豁免窗口（需求 3）
     *
     * 用户在弹窗中选择了「本次使用时长」，并确认进入应用后调用。
     * 在该时长内再次进入该应用不再弹窗；时间到后自动恢复拦截并提醒。
     *
     * @param pkg 目标应用包名
     * @param durationMinutes 允许使用的分钟数，<=0 表示不设置豁免
     */
    fun setTemporaryExemption(pkg: String, durationMinutes: Int) {
        if (pkg.isBlank() || durationMinutes <= 0) return
        val until = System.currentTimeMillis() + durationMinutes * 60_000L
        synchronized(this) {
            temporaryExemptUntil[pkg] = until
            temporaryExemptDuration[pkg] = durationMinutes
            // 持久化，保证进程被杀后豁免窗口依然有效
            prefs?.edit()
                ?.putLong(exemptionKey(pkg), until)
                ?.putInt(exemptionDurationKey(pkg), durationMinutes)
                ?.apply()
        }
    }

    /**
     * 该包是否存在待提醒的「时长到期」事件（不移除）
     */
    fun hasPendingExpiryReminder(pkg: String): Boolean {
        synchronized(this) { return pendingExpiryReminder.containsKey(pkg) }
    }

    /**
     * 读取该包待提醒的「时长到期」事件（不移除）
     *
     * 由弹窗页展示「说好只用 N 分钟」时调用。
     * 只读不消费：事件在「用户做出选择」或「弹窗关闭」时才清除，
     * 避免 onNewIntent 重入时读不到分钟数。
     */
    fun peekPendingExpiryReminder(pkg: String): ExpiryReminder? {
        synchronized(this) { return pendingExpiryReminder[pkg] }
    }

    /**
     * 取走并清除该包的「时长到期」事件
     */
    fun consumePendingExpiryReminder(pkg: String): ExpiryReminder? {
        synchronized(this) { return pendingExpiryReminder.remove(pkg) }
    }

    /**
     * 获取剩余豁免分钟数（向上取整），无豁免或已过期返回 0
     */
    fun getExemptionRemainingMin(pkg: String): Int {
        val until = synchronized(this) { temporaryExemptUntil[pkg] } ?: return 0
        val remain = until - System.currentTimeMillis()
        if (remain <= 0) return 0
        return ((remain + 59_999L) / 60_000L).toInt()
    }

    /**
     * 豁免窗口的 SharedPreferences 键
     */
    private fun exemptionKey(pkg: String) = "$KEY_EXEMPTION_PREFIX$pkg"

    /**
     * 豁免时长（分钟）的 SharedPreferences 键
     */
    private fun exemptionDurationKey(pkg: String) = "$KEY_EXEMPTION_DURATION_PREFIX$pkg"

    /**
     * 从持久化存储恢复临时豁免窗口
     * 在 init 中调用：进程被杀后重启时豁免窗口依然有效
     */
    private fun restoreExemptions() {
        val all = prefs?.all ?: return
        val now = System.currentTimeMillis()
        all.forEach { (key, value) ->
            if (!key.startsWith(KEY_EXEMPTION_PREFIX)) return@forEach
            val until = value as? Long ?: return@forEach
            val pkg = key.removePrefix(KEY_EXEMPTION_PREFIX)
            if (until > now) {
                temporaryExemptUntil[pkg] = until
                temporaryExemptDuration[pkg] =
                    prefs?.getInt(exemptionDurationKey(pkg), 0) ?: 0
            } else {
                prefs?.edit()?.remove(key)?.remove(exemptionDurationKey(pkg))?.apply()
            }
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
            // 需求 1+4：启动 JobScheduler + AlarmManager 双路保活，
            // 进程被 ColorOS 杀掉后由系统拉起并自动重绑无障碍服务
            appContext?.let { KeepAliveManager.schedule(it) }
        } else {
            stopForegroundService()
            // 修复 C：取消闹钟，守护关闭后不再定时拉起服务
            appContext?.let { ServiceRestartReceiver.cancel(it) }
            // 需求 1+4：取消保活任务，避免守护关闭后仍被系统拉起
            appContext?.let { KeepAliveManager.cancel(it) }
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
            // 清空临时豁免窗口（需求 3）：守护关闭后不应再保留免打扰状态
            clearExemptions()
        }
    }

    /**
     * 清空所有临时豁免窗口（含持久化）
     * 守护关闭时调用，确保重新开启后不会残留旧的豁免状态
     */
    private fun clearExemptions() {
        synchronized(this) {
            temporaryExemptUntil.clear()
            temporaryExemptDuration.clear()
            pendingExpiryReminder.clear()
            foregroundAppPkg = null
            val editor = prefs?.edit() ?: return
            prefs?.all?.keys?.forEach { key ->
                if (key.startsWith(KEY_EXEMPTION_PREFIX) ||
                    key.startsWith(KEY_EXEMPTION_DURATION_PREFIX)
                ) {
                    editor.remove(key)
                }
            }
            editor.apply()
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
     *
     * @param pkg 触发的应用包名
     * @return 触发类型；null 表示被去重过滤，本次不弹窗
     */
    fun checkAndTrigger(pkg: String): QuizTrigger? {
        if (!shouldBlock(pkg)) return null

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
        if (!shouldTrigger) return null

        managerScope.launch { appRepository?.incrementBlockCount(pkg) }

        // 豁免窗口刚刚到期 → 本次是「用户自己选的时长到了」的提醒
        // 注意：这里只做「查看」不消费，具体分钟数由弹窗链路消费，
        // 这样弹窗启动失败时到期事件不会丢失。
        return if (hasPendingExpiryReminder(pkg)) {
            QuizTrigger.EXPIRY
        } else {
            QuizTrigger.FIRST
        }
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
     * @return QuizTrigger.RECURRING 表示触发；null 表示不触发
     */
    fun checkRecurringTrigger(pkg: String): QuizTrigger? {
        if (!shouldBlock(pkg)) return null
        if (recurringIntervalMin <= 0) return null

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
        if (!shouldTrigger) return null

        managerScope.launch { appRepository?.incrementBlockCount(pkg) }
        return QuizTrigger.RECURRING
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
            // 「时长到期」事件在弹窗展示时已消费；此处再兜底一次，
            // 防止弹窗未展示就被关闭时事件残留、误用于后续弹窗
            currentQuizTarget?.let { pendingExpiryReminder.remove(it) }
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
     *
     * @param pkg 目标应用包名
     * @param durationMinutes 用户在弹窗中选择的「本次使用时长」（分钟）。
     *        >0 时开启临时豁免窗口：该时长内再次进入该应用不再弹窗，到期后自动恢复提醒。
     */
    fun onAppEntered(pkg: String, durationMinutes: Int = 0) {
        synchronized(this) {
            lastAppEnterPkg = pkg
            lastAppEnterTimeMs = System.currentTimeMillis()
            // 进入应用视为本「拦截会话」已消化，重置去重以便后续正常触发
            lastTriggeredPkg = null
            lastTriggerTime = 0L
        }
        // 需求 3：用户主动选择的使用时长 → 写入临时豁免窗口
        if (durationMinutes > 0) {
            setTemporaryExemption(pkg, durationMinutes)
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
