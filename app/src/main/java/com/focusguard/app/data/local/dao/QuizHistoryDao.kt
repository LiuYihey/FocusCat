package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.QuizHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 测验历史记录的数据访问对象（DAO）
 * 提供对 quiz_history 表的插入和查询操作
 */
@Dao
interface QuizHistoryDao {

    /**
     * 插入一条答题历史记录
     * @param entity 待插入的历史记录实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QuizHistoryEntity)

    /**
     * 查询指定时间范围内的答题历史记录
     * @param start 起始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 符合条件的历史记录列表
     */
    @Query("SELECT * FROM quiz_history WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getHistoryByDateRange(start: Long, end: Long): List<QuizHistoryEntity>

    /**
     * 获取今日的答题历史记录（以 Flow 形式返回，可观察数据变化）
     * @param startOfDay 今日起始时间戳（毫秒）
     */
    @Query("SELECT * FROM quiz_history WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayHistory(startOfDay: Long): Flow<List<QuizHistoryEntity>>

    /**
     * 获取累计答对题数
     * @return 答对题数
     */
    @Query("SELECT COUNT(*) FROM quiz_history WHERE isCorrect = 1")
    suspend fun getCorrectCount(): Int

    /**
     * 获取累计答题总数
     * @return 答题总数
     */
    @Query("SELECT COUNT(*) FROM quiz_history")
    suspend fun getTotalCount(): Int
}
