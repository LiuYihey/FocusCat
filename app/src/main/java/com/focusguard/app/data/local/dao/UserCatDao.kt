package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.UserCatEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户猫咪 DAO
 *
 * 支持多只猫实例化：每只猫独立存储，通过 [isActive] 标记当前激活的猫。
 * 切换猫 = 旧猫 isActive=false + 新猫 isActive=true（事务由 Repository 层保证）。
 */
@Dao
interface UserCatDao {

    /** 观察当前激活的猫咪（全局唯一），UI 监听此 Flow 自动响应切换 */
    @Query("SELECT * FROM user_cat WHERE isActive = 1 LIMIT 1")
    fun observeUserCat(): Flow<UserCatEntity?>

    /** 获取当前激活的猫咪（单次查询） */
    @Query("SELECT * FROM user_cat WHERE isActive = 1 LIMIT 1")
    suspend fun getUserCat(): UserCatEntity?

    /** 观察所有已创建的猫咪（按创建时间升序），用于切换猫咪列表展示 */
    @Query("SELECT * FROM user_cat ORDER BY createdAt ASC")
    fun observeAllCats(): Flow<List<UserCatEntity>>

    /** 获取所有已创建的猫咪（单次查询） */
    @Query("SELECT * FROM user_cat ORDER BY createdAt ASC")
    suspend fun getAllCats(): List<UserCatEntity>

    /** 根据 ID 获取猫咪 */
    @Query("SELECT * FROM user_cat WHERE id = :catId LIMIT 1")
    suspend fun getCatById(catId: Int): UserCatEntity?

    /** 插入新猫咪（id 自增），返回新 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cat: UserCatEntity): Long

    /** 将所有猫设为未激活 */
    @Query("UPDATE user_cat SET isActive = 0")
    suspend fun deactivateAll()

    /** 将指定猫设为激活 */
    @Query("UPDATE user_cat SET isActive = 1 WHERE id = :catId")
    suspend fun activateCat(catId: Int)

    /** 增加当前激活猫的好感度 */
    @Query("UPDATE user_cat SET affinityLevel = affinityLevel + :amount WHERE isActive = 1")
    suspend fun addAffinity(amount: Int)

    /** 增加当前激活猫的投喂次数和好感度 */
    @Query("UPDATE user_cat SET totalFeedCount = totalFeedCount + 1, affinityLevel = affinityLevel + :affinityBonus WHERE isActive = 1")
    suspend fun incrementFeed(affinityBonus: Int)

    /** 用户是否已创建过任何猫咪 */
    @Query("SELECT COUNT(*) > 0 FROM user_cat")
    suspend fun hasAnyCat(): Boolean

    /** 删除指定猫咪（不可删除当前激活的猫） */
    @Query("DELETE FROM user_cat WHERE id = :catId AND isActive = 0")
    suspend fun deleteCatById(catId: Int)
}
