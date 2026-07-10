package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 食物库存实体
 * 存储用户拥有的各类食物数量
 */
@Entity(tableName = "food_inventory")
data class FoodInventoryEntity(
    /** 食物 ID，关联 food_catalog，作为主键 */
    @PrimaryKey
    val foodId: String,
    /** 拥有数量 */
    val count: Int = 0,
    /** 最后更新时间戳（毫秒） */
    val updatedAt: Long = System.currentTimeMillis()
)
