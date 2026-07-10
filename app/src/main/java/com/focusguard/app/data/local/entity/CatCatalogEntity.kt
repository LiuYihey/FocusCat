package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 猫咪品种目录实体
 * 存储可选猫咪品种的目录信息（2 种品种：布偶猫、橘猫）
 */
@Entity(tableName = "cat_catalog")
data class CatCatalogEntity(
    /** 品种 ID，如 "ragdoll"、"orange" */
    @PrimaryKey
    val breedId: String,
    /** 品种显示名称，如 "橘猫" */
    val displayName: String,
    /** 品种描述 */
    val description: String,
    /** 图标资源名（drawable 或 asset） */
    val iconAsset: String,
    /** 待机动画资源名 */
    val idleAnimAsset: String,
    /** 进食动画资源名 */
    val eatAnimAsset: String,
    /** 开心动画资源名 */
    val happyAnimAsset: String
)
