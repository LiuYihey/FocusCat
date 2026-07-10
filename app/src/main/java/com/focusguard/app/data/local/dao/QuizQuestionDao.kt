package com.focusguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.local.entity.QuizQuestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 测验题目的数据访问对象（DAO）
 * 提供对 quiz_questions 表的查询和更新操作
 */
@Dao
interface QuizQuestionDao {

    /**
     * 获取所有题目列表（以 Flow 形式返回，可观察数据变化）
     */
    @Query("SELECT * FROM quiz_questions ORDER BY id ASC")
    fun getAllQuestions(): Flow<List<QuizQuestionEntity>>

    /**
     * 随机获取一道题目
     * @return 随机题目，若表为空则返回 null
     */
    @Query("SELECT * FROM quiz_questions ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomQuestion(): QuizQuestionEntity?

    /**
     * 根据分类随机获取一道题目
     * @param category 题目分类
     * @return 随机题目，若该分类无题目则返回 null
     */
    @Query("SELECT * FROM quiz_questions WHERE category = :category ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomQuestionByCategory(category: String): QuizQuestionEntity?

    /**
     * 获取题目总数
     * @return 题目数量
     */
    @Query("SELECT COUNT(*) FROM quiz_questions")
    suspend fun count(): Int

    /**
     * 批量插入题目（若已存在则忽略）
     * @param entities 待插入的题目列表
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<QuizQuestionEntity>)

    /**
     * 将指定题目的展示次数自增 1
     * @param id 题目 ID
     */
    @Query("UPDATE quiz_questions SET timesShown = timesShown + 1 WHERE id = :id")
    suspend fun incrementShown(id: Long)

    /**
     * 将指定题目的答对次数自增 1
     * @param id 题目 ID
     */
    @Query("UPDATE quiz_questions SET timesCorrect = timesCorrect + 1 WHERE id = :id")
    suspend fun incrementCorrect(id: Long)
}
