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

    /**
     * 查询指定里程碑成就的解锁时间戳
     * 用于"解锁新伙伴"前置条件判断：只有解锁"初识之友"(milestone=10) 后才能添加第二只猫。
     *
     * @return 解锁时间戳，null 表示未解锁
     */
    @Query("SELECT unlockedAt FROM affinity_achievements WHERE milestone = :milestone LIMIT 1")
    suspend fun getUnlockedAt(milestone: Int): Long?

    /** 解锁指定里程碑成就 */
    @Query("UPDATE affinity_achievements SET unlockedAt = :unlockedAt WHERE milestone = :milestone AND unlockedAt IS NULL")
    suspend fun unlock(milestone: Int, unlockedAt: Long)

    /**
     * 更新成就描述（用于种子数据版本更新时同步新文案）
     * 仅更新未解锁的成就，避免覆盖已解锁成就的历史描述
     */
    @Query("UPDATE affinity_achievements SET description = :description WHERE milestone = :milestone")
    suspend fun updateDescription(milestone: Int, description: String)

    /** 插入成就（若已存在则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<AffinityAchievementEntity>)
}
