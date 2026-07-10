package com.focusguard.app.detection

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusguard.app.util.PermissionHelper
import java.util.Calendar

/**
 * 使用情况统计监控工具
 * 封装 UsageStatsManager 的常用查询，需要 PACKAGE_USAGE_STATS 权限
 */
object UsageStatsMonitor {

    /**
     * 获取当前前台应用包名
     * 查询最近5秒的 UsageEvents，返回最后一条 ACTIVITY_RESUMED 事件的包名
     * @return 前台应用包名，无权限或无数据时返回 null
     */
    fun getForegroundApp(context: Context): String? {
        if (!PermissionHelper.checkUsageStatsPermission(context)) {
            return null
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as UsageStatsManager

        val now = System.currentTimeMillis()
        val beginTime = now - 5_000L  // 最近5秒

        val events = usageStatsManager.queryEvents(beginTime, now) ?: return null

        // API 29+ 使用 ACTIVITY_RESUMED，旧版本使用 MOVE_TO_FOREGROUND（已废弃但仍可用）
        val resumeEventType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

        var lastForegroundPkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == resumeEventType) {
                lastForegroundPkg = event.packageName
            }
        }
        return lastForegroundPkg
    }

    /**
     * 查询今天某应用的使用时长
     * @param packageName 应用包名
     * @return 使用时长（毫秒），无权限或无数据时返回 0
     */
    fun getUsageTimeToday(context: Context, packageName: String): Long {
        if (!PermissionHelper.checkUsageStatsPermission(context)) {
            return 0L
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as UsageStatsManager

        // 获取今天 0 点的时间戳
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val beginTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)
            ?: return 0L

        val appStats = stats[packageName] ?: return 0L
        return appStats.totalTimeInForeground
    }
}
