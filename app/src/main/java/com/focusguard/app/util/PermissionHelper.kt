package com.focusguard.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * 权限检查工具类
 * 提供使用情况访问、悬浮窗、无障碍服务、电池优化白名单等权限的检查与跳转授权
 */
object PermissionHelper {

    /**
     * 检查是否拥有使用情况访问权限（PACKAGE_USAGE_STATS）
     * 通过 AppOpsManager 检查对应操作的模式是否为允许
     */
    fun checkUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // Android 29+ 使用 unsafeCheckOpNoThrow，旧版本使用 checkOpNoThrow
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 检查是否拥有悬浮窗权限（SYSTEM_ALERT_WINDOW）
     */
    fun checkOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 检查无障碍服务是否启用
     *
     * 双重判定（实测修复误报）：
     * 1. 首选 AccessibilityManager.getEnabledAccessibilityServiceList() ——
     *    这是系统给出的"当前已启用无障碍服务"权威列表，不受 Settings 字符串格式影响。
     * 2. 回退 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 字符串匹配 ——
     *    部分 ROM 上 AccessibilityManager 返回空但设置里确实已开启。
     *
     * 两者任一命中即视为已启用。
     */
    fun checkAccessibilityPermission(context: Context): Boolean {
        if (isAccessibilityServiceEnabledByManager(context)) return true
        return isAccessibilityServiceEnabledBySettings(context)
    }

    /**
     * 通过 AccessibilityManager 查询系统当前已启用的无障碍服务列表
     * 这是判断"系统开关是否还在"最可靠的来源
     */
    private fun isAccessibilityServiceEnabledByManager(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager ?: return false
            val services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            services.any { info ->
                // info.id 形如 "com.focusguard.app/.accessibility.AppMonitorService"，
                // 是系统给出的扁平化组件名，比 resolveInfo 更稳定（resolveInfo 可能为 null）
                val id = info.id
                if (id != null && id.startsWith("${context.packageName}/", ignoreCase = true)) {
                    return@any true
                }
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo?.packageName == context.packageName &&
                    serviceInfo?.name?.contains("AppMonitorService") == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 通过 Settings.Secure 字符串匹配判断无障碍服务是否启用
     * 保留为回退方案：部分 ROM 上 AccessibilityManager 不返回已启用的无障碍服务
     */
    private fun isAccessibilityServiceEnabledBySettings(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        if (TextUtils.isEmpty(enabledServices)) return false

        val serviceName = "${context.packageName}/${context.packageName}.accessibility.AppMonitorService"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (entry in splitter) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.equals(serviceName, ignoreCase = true)) {
                return true
            }
            // 兼容系统存储的简写形式：包名/.accessibility.AppMonitorService
            if (trimmed.startsWith("${context.packageName}/", ignoreCase = true)) {
                return true
            }
            // 兼容只记录包名的形式
            if (trimmed.equals(context.packageName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 检查应用是否已加入电池优化白名单（免被系统杀死无障碍服务）
     * 这是「退出 APP 后权限消失」问题的关键修复：系统电池优化会杀死后台服务
     */
    fun checkBatteryOptimization(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求加入电池优化白名单
     */
    fun requestBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 部分 ROM 可能不支持，降级到电池优化设置列表页
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // 仍不支持则忽略
            }
        }
    }

    /**
     * 跳转到使用情况访问权限授权页面
     */
    fun requestUsageStatsPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 部分 ROM 可能无对应 Settings Activity，忽略避免崩溃
        }
    }

    /**
     * 跳转到悬浮窗权限授权页面
     */
    fun requestOverlayPermission(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 部分 ROM 可能无对应 Settings Activity，忽略避免崩溃
        }
    }

    /**
     * 跳转到无障碍服务设置页面
     */
    fun requestAccessibilityPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 部分 ROM 可能无对应 Settings Activity，忽略避免崩溃
        }
    }

    /**
     * 跳转到厂商自启动管理页面（解决国产 ROM 杀后台导致无障碍服务失效）
     * 依次尝试主流厂商的自启动 Intent，全部失败则回退到应用详情页
     */
    fun requestAutoStart(context: Context) {
        val intents = listOf(
            // 小米 MIUI
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // 华为 EMUI
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            // 荣耀 MagicOS
            Intent().setClassName(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            // OPPO ColorOS
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            // vivo OriginOS / FuntouchOS
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // 三星
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // 该厂商页面不存在，尝试下一个
            }
        }
        // 全部失败，回退到应用详情页（用户可在此查找电池/自启动选项）
        requestAppDetails(context)
    }

    /**
     * 跳转到 ColorOS「允许后台运行 / 完全后台行为」设置页
     *
     * ColorOS 把后台保活拆成了多层开关，缺一不可：
     * - 自启动管理：允许应用自启
     * - 允许后台运行 / 完全后台行为：允许应用在后台持续运行（最关键的一层）
     * - 电池优化白名单：防止系统直接冻结进程
     *
     * 各版本 ColorOS 的 Activity 名称不一致，故按优先级依次尝试，
     * 全部失败回退到应用详情页。
     */
    fun requestColorOsBackgroundRun(context: Context) {
        val intents = listOf(
            // ColorOS 13+ 允许后台运行 / 完全后台行为（自启动管理里的二级列表）
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            // ColorOS 12 及更早的允许后台运行列表
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.startupappinfo.StartupAppInfoActivity"
            ),
            // 手机管家 → 应用耗电管理（后台高耗电 / 允许后台运行）
            Intent().setClassName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity"
            ),
            // 一加 / realme 合并包名下的保活页
            Intent().setClassName(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
            )
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // 该版本没有此页面，尝试下一个
            }
        }
        requestAppDetails(context)
    }

    /**
     * 跳转到应用详情页（电池优化 / 后台运行设置的通用入口）
     */
    fun requestAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 忽略
        }
    }
}
