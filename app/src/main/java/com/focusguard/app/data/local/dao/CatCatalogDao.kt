package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.CatCatalogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 猫咪品种目录 DAO
 * 提供对 cat_catalog 表的查询操作
 */
@Dao
interface CatCatalogDao {

    /** 获取所有猫咪品种 */
    @Query("SELECT * FROM cat_catalog ORDER BY displayName")
    suspend fun getAllBreeds(): List<CatCatalogEntity>

    /** 观察所有猫咪品种（Flow），种子数据插入后自动通知 UI */
    @Query("SELECT * FROM cat_catalog ORDER BY displayName")
    fun observeAllBreeds(): Flow<List<CatCatalogEntity>>

    /** 根据品种 ID 获取品种信息 */
    @Query("SELECT * FROM cat_catalog WHERE breedId = :breedId LIMIT 1")
    suspend fun getById(breedId: String): CatCatalogEntity?

    /** 插入品种（若已存在则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(breeds: List<CatCatalogEntity>)
}
