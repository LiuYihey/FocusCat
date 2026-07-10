package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity

/**
 * 反思问题 DAO
 * 提供对 reflection_questions 表的增删改查操作
 */
@Dao
interface ReflectionQuestionDao {

    /** 获取所有启用的反思问题（按顺序排列） */
    @Query("SELECT * FROM reflection_questions WHERE isActive = 1 ORDER BY `order` ASC")
    suspend fun getActiveQuestions(): List<ReflectionQuestionEntity>

    /** 获取所有反思问题（含禁用的） */
    @Query("SELECT * FROM reflection_questions ORDER BY `order` ASC")
    suspend fun getAllQuestions(): List<ReflectionQuestionEntity>

    /** 获取启用的反思问题数量 */
    @Query("SELECT COUNT(*) FROM reflection_questions WHERE isActive = 1")
    suspend fun countActive(): Int

    /** 获取最大排序值（用于新增问题时分配 order，避免并发保存导致 order 重复） */
    @Query("SELECT COALESCE(MAX(`order`), 0) FROM reflection_questions")
    suspend fun getMaxOrder(): Int

    /** 插入问题（若已存在则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: ReflectionQuestionEntity)

    /** 批量插入问题 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<ReflectionQuestionEntity>)

    /** 更新问题 */
    @Update
    suspend fun update(question: ReflectionQuestionEntity)

    /** 删除问题 */
    @Delete
    suspend fun delete(question: ReflectionQuestionEntity)
}
