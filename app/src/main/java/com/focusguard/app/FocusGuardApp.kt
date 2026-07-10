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

    companion object {
        /** 通知渠道 ID */
        const val NOTIFICATION_CHANNEL_ID = "focus_guard_service"
    }
}
