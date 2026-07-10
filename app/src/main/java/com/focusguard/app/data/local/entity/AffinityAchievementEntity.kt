package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 好感度成就实体
 * 存储投喂里程碑成就信息（10/50/100 次）
 */
@Entity(tableName = "affinity_achievements")
data class AffinityAchievementEntity(
    /** 里程碑数值（10、50、100），作为主键 */
    @PrimaryKey
    val milestone: Int,
    /** 成就称号，如 "初识之友" */
    val title: String,
    /** 成就描述 */
    val description: String,
    /** 解锁时间戳，null 表示未解锁 */
    val unlockedAt: Long? = null
)
