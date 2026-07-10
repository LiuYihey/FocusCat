package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 食物目录实体
 * 存储所有食物类型的目录信息
 */
@Entity(tableName = "food_catalog")
data class FoodCatalogEntity(
    /** 食物 ID，如 "cat_food"、"chicken" */
    @PrimaryKey
    val foodId: String,
    /** 食物显示名称，如 "猫粮" */
    val displayName: String,
    /** 稀有度：common / rare / epic */
    val rarity: String,
    /** 好感度加成数值 */
    val affinityBonus: Int,
    /** 获取概率（0.0-1.0） */
    val dropRate: Double,
    /** 图标资源名 */
    val iconAsset: String
)
