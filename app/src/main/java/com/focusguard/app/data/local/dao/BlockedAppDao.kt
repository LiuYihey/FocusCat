package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * 被拦截应用的数据访问对象（DAO）
 * 提供对 blocked_apps 表的增删改查操作
 */
@Dao
interface BlockedAppDao {

    /**
     * 获取所有被拦截的应用列表（以 Flow 形式返回，可观察数据变化）
     */
    @Query("SELECT * FROM blocked_apps ORDER BY addedAt DESC")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    /**
     * 获取所有处于激活状态的被拦截应用列表
     */
    @Query("SELECT * FROM blocked_apps WHERE isActive = 1")
    suspend fun getActiveBlockedApps(): List<BlockedAppEntity>

    /**
     * 根据包名获取被拦截应用
     * @param pkg 应用包名
     * @return 对应的被拦截应用实体，若不存在则返回 null
     */
    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackageName(pkg: String): BlockedAppEntity?

    /**
     * 插入一个被拦截应用（若已存在则替换）
     * @param entity 待插入的实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedAppEntity)

    /**
     * 删除一个被拦截应用
     * @param entity 待删除的实体
     */
    @Delete
    suspend fun delete(entity: BlockedAppEntity)

    /**
     * 更新指定应用的拦截次数（自增 1）
     * @param pkg 应用包名
     */
    @Query("UPDATE blocked_apps SET blockCount = blockCount + 1 WHERE packageName = :pkg")
    suspend fun updateBlockCount(pkg: String)

    /**
     * 设置应用的激活状态
     * @param pkg 应用包名
     * @param active 是否激活
     */
    @Query("UPDATE blocked_apps SET isActive = :active WHERE packageName = :pkg")
    suspend fun setActive(pkg: String, active: Boolean)
}
