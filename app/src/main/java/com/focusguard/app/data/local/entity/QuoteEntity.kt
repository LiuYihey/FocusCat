package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 小猫格言实体
 *
 * 需求 1：退出约束应用时不再发放猫粮，改为随机展示一句小猫格言。
 * 格言内容由用户自行编写，可在「设置 → 小猫格言管理」中增删改。
 *
 * @property id 自增主键
 * @property text 格言正文
 * @property order 显示顺序（1、2、3…）
 * @property isActive 是否启用（停用后不参与随机抽取）
 * @property isCustom 是否用户自定义（内置格言为 false）
 */
@Entity(tableName = "quotes")
data class QuoteEntity(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 格言正文 */
    val text: String,
    /** 显示顺序 */
    val order: Int,
    /** 是否启用 */
    val isActive: Boolean = true,
    /** 是否用户自定义 */
    val isCustom: Boolean = false
)
