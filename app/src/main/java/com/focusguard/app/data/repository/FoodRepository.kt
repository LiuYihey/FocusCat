package com.focusguard.app.data.repository

import com.focusguard.app.data.local.dao.FoodCatalogDao
import com.focusguard.app.data.local.dao.FoodInventoryDao
import com.focusguard.app.data.local.entity.FoodCatalogEntity
import com.focusguard.app.data.local.entity.FoodInventoryEntity
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * 食物仓库
 * 封装食物目录、库存的数据访问和随机食物发放逻辑
 */
class FoodRepository(
    private val foodCatalogDao: FoodCatalogDao,
    private val foodInventoryDao: FoodInventoryDao
) {

    /** 获取所有食物类型 */
    suspend fun getAllFoods(): List<FoodCatalogEntity> = foodCatalogDao.getAllFoods()

    /** 根据食物 ID 获取食物信息 */
    suspend fun getFoodById(foodId: String): FoodCatalogEntity? = foodCatalogDao.getById(foodId)

    /** 观察食物库存（Flow） */
    fun observeInventory(): Flow<List<FoodInventoryEntity>> = foodInventoryDao.observeAllInventory()

    /** 获取所有库存（单次查询） */
    suspend fun getInventory(): List<FoodInventoryEntity> = foodInventoryDao.getAllInventory()

    /** 获取指定食物的库存数量 */
    suspend fun getFoodCount(foodId: String): Int =
        foodInventoryDao.getByFoodId(foodId)?.count ?: 0

    /** 增加食物数量 */
    suspend fun addFood(foodId: String, amount: Int = 1) {
        foodInventoryDao.addFood(foodId, amount, System.currentTimeMillis())
    }

    /** 消耗一个食物 */
    suspend fun consumeOne(foodId: String) {
        foodInventoryDao.consumeOne(foodId, System.currentTimeMillis())
    }

    /**
     * 随机发放一份食物（根据 dropRate 概率加权随机）
     * @return 获得的食物类型，若食物目录为空则返回 null
     */
    suspend fun grantRandomFood(): FoodCatalogEntity? {
        val foods = foodCatalogDao.getAllFoods()
        if (foods.isEmpty()) return null
        // 加权随机：根据 dropRate 选择
        val totalRate = foods.sumOf { it.dropRate }
        if (totalRate <= 0.0) return foods.first()
        var random = Random.nextDouble(totalRate)
        for (food in foods) {
            random -= food.dropRate
            if (random <= 0) {
                addFood(food.foodId, 1)
                return food
            }
        }
        // 兜底返回最后一个
        val last = foods.last()
        addFood(last.foodId, 1)
        return last
    }

}
