package com.focusguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.detection.AppDetectionManager

/**
 * 开机自启动接收器
 * 接收 BOOT_COMPLETED 广播，仅在守护开关开启时启动前台保活服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // 先恢复守护开关状态，再决定是否启动服务
        AppDetectionManager.init(context)
        if (!AppDetectionManager.isProtectionEnabled) return

        // 启动前台保活服务（minSdk=26，直接使用 startForegroundService）
        val serviceIntent = Intent(context, FocusGuardService::class.java)
        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限，忽略避免崩溃
        }
        // 修复 C：恢复 AlarmManager 守护者闹钟
        // 开机后所有闹钟被清空，需重新注册以确保前台服务被杀后能定时拉起
        ServiceRestartReceiver.schedule(context)
    }
}
