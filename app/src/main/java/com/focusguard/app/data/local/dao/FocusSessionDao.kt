package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 专注会话的数据访问对象（DAO）
 * 提供对 focus_sessions 表的插入、更新和查询操作
 */
@Dao
interface FocusSessionDao {

    /**
     * 插入一条专注会话记录
     * @param entity 待插入的会话实体
     * @return 新插入记录的 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FocusSessionEntity): Long

    /**
     * 更新指定会话的结束时间和时长
     * @param id 会话 ID
     * @param endTime 结束时间戳（毫秒）
     * @param duration 会话时长（秒）
     */
    @Query("UPDATE focus_sessions SET endTime = :endTime, durationSeconds = :duration WHERE id = :id")
    suspend fun updateEndTime(id: Long, endTime: Long, duration: Int)

    /**
     * 获取今日的专注会话列表（以 Flow 形式返回，可观察数据变化）
     * @param startOfDay 今日起始时间戳（毫秒）
     */
    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startOfDay ORDER BY startTime DESC")
    fun getTodaySessions(startOfDay: Long): Flow<List<FocusSessionEntity>>

    /**
     * 查询指定时间范围内的专注会话列表
     * @param start 起始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 符合条件的会话列表
     */
    @Query("SELECT * FROM focus_sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime DESC")
    suspend fun getSessionsByDateRange(start: Long, end: Long): List<FocusSessionEntity>

    /**
     * 获取累计专注总时长（秒）
     * @return 累计专注总时长，若无记录则返回 0
     */
    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions")
    suspend fun getTotalFocusTime(): Long
}
