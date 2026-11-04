package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.local.entity.QuoteEntity

/**
 * 小猫格言 DAO
 * 提供对 quotes 表的增删改查
 */
@Dao
interface QuoteDao {

    /** 获取所有格言（含停用的），按顺序排列 */
    @Query("SELECT * FROM quotes ORDER BY `order` ASC, id ASC")
    suspend fun getAllQuotes(): List<QuoteEntity>

    /** 获取启用的格言数量 */
    @Query("SELECT COUNT(*) FROM quotes WHERE isActive = 1")
    suspend fun countActive(): Int

    /** 获取最大排序值（新增格言时分配 order，避免并发保存导致 order 重复） */
    @Query("SELECT COALESCE(MAX(`order`), 0) FROM quotes")
    suspend fun getMaxOrder(): Int

    /** 插入格言 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quote: QuoteEntity)

    /** 批量插入格言 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<QuoteEntity>)

    /** 更新格言 */
    @Update
    suspend fun update(quote: QuoteEntity)

    /** 删除格言 */
    @Delete
    suspend fun delete(quote: QuoteEntity)
}
