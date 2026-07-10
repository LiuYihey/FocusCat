package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.FoodInventoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 食物库存 DAO
 * 提供对 food_inventory 表的增删改查操作
 */
@Dao
interface FoodInventoryDao {

    /** 获取所有食物库存（以 Flow 形式返回，可观察变化） */
    @Query("SELECT * FROM food_inventory WHERE count > 0 ORDER BY updatedAt DESC")
    fun observeAllInventory(): Flow<List<FoodInventoryEntity>>

    /** 获取所有食物库存（单次查询） */
    @Query("SELECT * FROM food_inventory WHERE count > 0")
    suspend fun getAllInventory(): List<FoodInventoryEntity>

    /** 根据食物 ID 获取库存 */
    @Query("SELECT * FROM food_inventory WHERE foodId = :foodId LIMIT 1")
    suspend fun getByFoodId(foodId: String): FoodInventoryEntity?

    /** 插入或更新库存 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FoodInventoryEntity)

    /** 增加指定食物数量（若不存在则插入） */
    @Query("INSERT OR REPLACE INTO food_inventory (foodId, count, updatedAt) VALUES (:foodId, COALESCE((SELECT count FROM food_inventory WHERE foodId = :foodId), 0) + :amount, :updatedAt)")
    suspend fun addFood(foodId: String, amount: Int, updatedAt: Long)

    /** 消耗一个食物（数量减 1，不低于 0） */
    @Query("UPDATE food_inventory SET count = MAX(count - 1, 0), updatedAt = :updatedAt WHERE foodId = :foodId")
    suspend fun consumeOne(foodId: String, updatedAt: Long)
}
