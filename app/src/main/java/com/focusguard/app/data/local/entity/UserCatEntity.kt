package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户猫咪实体
 *
 * 支持多只猫实例化设计：每只猫是独立记录，有独立的 breedId/name/affinityLevel/totalFeedCount。
 * 通过 [isActive] 标记当前激活的猫（全局唯一），切换时：
 * - 旧猫 isActive=false（保留其好感度/投喂次数等数据）
 * - 新猫 isActive=true（恢复其独立数据）
 *
 * 类似"切换账号"语义：每只猫的养成进度独立保留，切换即恢复该猫的完整状态。
 */
@Entity(tableName = "user_cat")
data class UserCatEntity(
    /** 主键自增，支持多只猫 */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** 猫咪品种 ID，关联 cat_catalog */
    val breedId: String,
    /** 用户给猫咪起的名字 */
    val name: String,
    /** 好感度数值，默认 0（每只猫独立） */
    val affinityLevel: Int = 0,
    /** 累计投喂次数，默认 0（每只猫独立） */
    val totalFeedCount: Int = 0,
    /** 是否为当前激活的猫（全局唯一，切换时由 DAO 事务保证） */
    val isActive: Boolean = false,
    /** 创建时间戳（毫秒） */
    val createdAt: Long = System.currentTimeMillis()
)
