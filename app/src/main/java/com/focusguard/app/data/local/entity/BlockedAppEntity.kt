package com.focusguard.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 被拦截应用实体类
 * 用于存储用户设置的需拦截的应用信息
 */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    /** 应用包名，作为主键 */
    @PrimaryKey
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 应用图标字节数组（可为空） */
    val iconBytes: ByteArray?,
    /** 添加时间戳（毫秒） */
    val addedAt: Long,
    /** 是否激活拦截，默认为 true */
    val isActive: Boolean = true,
    /** 累计拦截次数，默认为 0 */
    val blockCount: Int = 0
) {
    /**
     * 重写 equals 方法，因为包含 ByteArray 字段
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BlockedAppEntity
        if (packageName != other.packageName) return false
        if (appName != other.appName) return false
        if (iconBytes != null) {
            if (other.iconBytes == null) return false
            if (!iconBytes.contentEquals(other.iconBytes)) return false
        } else if (other.iconBytes != null) return false
        if (addedAt != other.addedAt) return false
        if (isActive != other.isActive) return false
        if (blockCount != other.blockCount) return false
        return true
    }

    /**
     * 重写 hashCode 方法，因为包含 ByteArray 字段
     */
    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
        result = 31 * result + addedAt.hashCode()
        result = 31 * result + isActive.hashCode()
        result = 31 * result + blockCount
        return result
    }
}
