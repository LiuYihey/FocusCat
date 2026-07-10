package com.focusguard.app.data.repository

import com.focusguard.app.data.local.dao.FocusSessionDao
import com.focusguard.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 统计仓库实现类
 * 封装 FocusSessionDao，提供专注会话相关的数据访问方法
 * @param focusSessionDao 专注会话 DAO
 */
class StatsRepositoryImpl(
    private val focusSessionDao: FocusSessionDao
) {

    /**
     * 记录一条专注会话
     * @param targetApp 目标应用包名
     * @param startTime 会话开始时间戳（毫秒）
     * @param endTime 会话结束时间戳（毫秒），可为空表示会话未结束
     * @param quizPassed 是否通过测验
     * @param durationSeconds 会话时长（秒）
     * @return 新插入会话的 ID
     */
    suspend fun recordFocusSession(
        targetApp: String,
        startTime: Long,
        endTime: Long?,
        quizPassed: Boolean,
        durationSeconds: Int
    ): Long {
        val entity = FocusSessionEntity(
            targetApp = targetApp,
            startTime = startTime,
            endTime = endTime,
            quizPassed = quizPassed,
            durationSeconds = durationSeconds
        )
        return focusSessionDao.insert(entity)
    }

    /**
     * 更新指定专注会话的结束时间和时长
     * @param id 会话 ID
     * @param endTime 结束时间戳（毫秒）
     * @param duration 会话时长（秒）
     */
    suspend fun updateFocusSession(id: Long, endTime: Long, duration: Int) {
        focusSessionDao.updateEndTime(id, endTime, duration)
    }

    /**
     * 获取今日的专注会话列表（以 Flow 形式返回，可观察数据变化）
     * @param startOfDay 今日起始时间戳（毫秒）
     * @return 今日专注会话的 Flow
     */
    fun getTodayStats(startOfDay: Long): Flow<List<FocusSessionEntity>> {
        return focusSessionDao.getTodaySessions(startOfDay)
    }

    /**
     * 获取指定时间范围内的专注会话列表
     * 用于趋势图按日聚合统计
     * @param start 起始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 符合条件的会话列表
     */
    suspend fun getSessionsByDateRange(start: Long, end: Long): List<FocusSessionEntity> {
        return focusSessionDao.getSessionsByDateRange(start, end)
    }

    /**
     * 获取累计专注总时长（秒）
     * @return 累计专注总时长，若无记录则返回 0
     */
    suspend fun getTotalStats(): Long {
        return focusSessionDao.getTotalFocusTime()
    }
}
