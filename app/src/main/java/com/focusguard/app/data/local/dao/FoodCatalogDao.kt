package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.FoodCatalogEntity

/**
 * 食物目录 DAO
 * 提供对 food_catalog 表的查询操作
 */
@Dao
interface FoodCatalogDao {

    /** 获取所有食物类型 */
    @Query("SELECT * FROM food_catalog ORDER BY affinityBonus")
    suspend fun getAllFoods(): List<FoodCatalogEntity>

    /** 根据食物 ID 获取食物信息 */
    @Query("SELECT * FROM food_catalog WHERE foodId = :foodId LIMIT 1")
    suspend fun getById(foodId: String): FoodCatalogEntity?

    /** 插入食物类型（若已存在则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodCatalogEntity>)

    /** 更新指定食物的掉落概率 */
    @Query("UPDATE food_catalog SET dropRate = :dropRate WHERE foodId = :foodId")
    suspend fun updateDropRate(foodId: String, dropRate: Double)
}
