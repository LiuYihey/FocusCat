package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.ReflectionAnswerEntity

/**
 * 反思回答记录 DAO
 * 提供对 reflection_answers 表的增删改查操作
 */
@Dao
interface ReflectionAnswerDao {

    /** 插入一条回答记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(answer: ReflectionAnswerEntity): Long

    /** 批量插入回答记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(answers: List<ReflectionAnswerEntity>)

    /** 获取今日回答数量 */
    @Query("SELECT COUNT(*) FROM reflection_answers WHERE answeredAt >= :startOfDay")
    suspend fun getTodayCount(startOfDay: Long): Int

    /** 获取总回答数量 */
    @Query("SELECT COUNT(*) FROM reflection_answers")
    suspend fun getTotalCount(): Int
}
