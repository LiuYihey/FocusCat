package com.focusguard.app.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.focusguard.app.detection.AppDetectionManager

/**
 * 保活 JobService（JobScheduler 守护入口）
 *
 * 为什么需要它：
 * AlarmManager 在 ColorOS 的深度休眠下会被大幅延迟甚至丢弃，
 * 而 JobScheduler 由系统保证执行 —— 即便 App 进程被完全杀死，
 * 系统仍会在下一个调度点拉起进程执行本任务。
 *
 * 进程一旦重建，Android 系统会自动把仍处于"开启"状态的无障碍服务重新绑定回来，
 * 这就是绕过 ColorOS「退出后台即关闭无障碍服务」限制的关键环节。
 *
 * 任务内容：
 * 1. 恢复守护开关状态（进程被杀后单例内存态丢失）
 * 2. 拉起前台保活服务（双引擎的轮询引擎）
 * 3. 无障碍服务健康自检
 */
class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // 恢复守护开关状态
        AppDetectionManager.init(this)
        // 拉起前台服务 + 无障碍自检
        KeepAliveManager.ensureForegroundService(this)
        KeepAliveManager.checkAccessibilityHealth(this)
        // 任务已同步完成，不需要系统继续持有唤醒锁
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // 被系统提前回收时要求重新调度
        return true
    }
}
