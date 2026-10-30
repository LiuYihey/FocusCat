package com.focusguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.detection.AppDetectionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Application 入口类
 * 使用 Hilt 进行依赖注入，注意：数据库初始化由 Hilt 处理，不在此处手动初始化
 */
@HiltAndroidApp
class FocusGuardApp : Application() {

    /** Hilt EntryPoint，用于在 Application 中获取仓库实例 */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppDetectionEntryPoint {
        fun appRepository(): AppRepositoryImpl
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化守护开关状态（从 SharedPreferences 恢复）
        AppDetectionManager.init(this)
        // 注入仓库，用于拦截次数统计
        val entryPoint = EntryPointAccessors.fromApplication(
            this,
            AppDetectionEntryPoint::class.java
        )
        AppDetectionManager.setAppRepository(entryPoint.appRepository())
        createNotificationChannel()
        createAlertNotificationChannel()
    }

    /**
     * 创建通知渠道
     * 渠道 ID: focus_guard_service
     * P2：minSdk=26 (O)，SDK_INT >= O 检查冗余已移除
     */
    private fun createNotificationChannel() {
        val channelId = NOTIFICATION_CHANNEL_ID
        val name = getString(R.string.notification_channel_name)
        val descriptionText = getString(R.string.notification_channel_desc)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
            // 守护服务通知不应发出声音
            setShowBadge(false)
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 创建「守护异常提醒」通知渠道（需求 1/4）
     *
     * 与保活通知渠道分开：保活通知是常驻静音的（IMPORTANCE_LOW），
     * 而「无障碍被系统关闭」是需要用户立刻看到并处理的告警，
     * 因此使用 IMPORTANCE_DEFAULT 让它能正常弹出横幅。
     */
    private fun createAlertNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ALERT,
            "守护提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "无障碍服务被系统关闭等守护异常时的提醒"
            setShowBadge(true)
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        /** 通知渠道 ID */
        const val NOTIFICATION_CHANNEL_ID = "focus_guard_service"

        /** 守护异常提醒通知渠道 ID */
        const val NOTIFICATION_CHANNEL_ALERT = "focus_guard_alert"
    }
}
