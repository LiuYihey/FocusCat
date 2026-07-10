package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.AffinityAchievementEntity
import kotlinx.coroutines.flow.Flow

/**
 * 好感度成就 DAO
 * 提供对 affinity_achievements 表的查询和解锁操作
 */
@Dao
interface AffinityAchievementDao {

    /** 获取所有成就（以 Flow 形式返回，可观察变化） */
    @Query("SELECT * FROM affinity_achievements ORDER BY milestone ASC")
    fun observeAllAchievements(): Flow<List<AffinityAchievementEntity>>

    /** 获取所有成就（单次查询） */
    @Query("SELECT * FROM affinity_achievements ORDER BY milestone ASC")
    suspend fun getAllAchievements(): List<AffinityAchievementEntity>

    /** 获取未解锁的成就（按里程碑升序） */
    @Query("SELECT * FROM affinity_achievements WHERE unlockedAt IS NULL ORDER BY milestone ASC")
    suspend fun getLockedAchievements(): List<AffinityAchievementEntity>

    /** 解锁指定里程碑成就 */
    @Query("UPDATE affinity_achievements SET unlockedAt = :unlockedAt WHERE milestone = :milestone AND unlockedAt IS NULL")
    suspend fun unlock(milestone: Int, unlockedAt: Long)

    /** 插入成就（若已存在则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<AffinityAchievementEntity>)
}
