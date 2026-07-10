package com.focusguard.app.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.focusguard.app.data.local.dao.QuizHistoryDao
import com.focusguard.app.data.local.dao.QuizQuestionDao
import com.focusguard.app.data.local.entity.QuizHistoryEntity
import com.focusguard.app.data.local.entity.QuizQuestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 测验仓库实现类
 * 封装 QuizQuestionDao 和 QuizHistoryDao，提供测验相关的数据访问方法
 * @param db Room 数据库实例，用于事务包裹多步写入
 * @param quizQuestionDao 测验题目 DAO
 * @param quizHistoryDao 测验历史 DAO
 */
class QuizRepositoryImpl(
    private val db: RoomDatabase,
    private val quizQuestionDao: QuizQuestionDao,
    private val quizHistoryDao: QuizHistoryDao
) {

    /**
     * 随机获取一道题目
     * @return 随机题目，若题库为空则返回 null
     */
    suspend fun getRandomQuestion(): QuizQuestionEntity? {
        return quizQuestionDao.getRandomQuestion()
    }

    /**
     * 记录一次答题结果
     * @param question 对应的题目实体
     * @param selectedAnswer 用户选择的答案索引
     * @param isCorrect 是否回答正确
     * @param targetApp 触发测验的目标应用包名（可为空）
     */
    suspend fun recordQuizResult(
        question: QuizQuestionEntity,
        selectedAnswer: Int,
        isCorrect: Boolean,
        targetApp: String?
    ) {
        // 用事务包裹三步写入，确保历史记录与题目统计原子更新
        db.withTransaction {
            // 记录历史
            val history = QuizHistoryEntity(
                questionId = question.id,
                questionText = question.question,
                selectedAnswer = selectedAnswer,
                isCorrect = isCorrect,
                targetApp = targetApp,
                timestamp = System.currentTimeMillis()
            )
            quizHistoryDao.insert(history)

            // 更新题目统计
            quizQuestionDao.incrementShown(question.id)
            if (isCorrect) {
                quizQuestionDao.incrementCorrect(question.id)
            }
        }
    }

    /**
     * 获取今日的答题历史记录（以 Flow 形式返回，可观察数据变化）
     * @param startOfDay 今日起始时间戳（毫秒）
     * @return 今日答题历史的 Flow
     */
    fun getTodayQuizHistory(startOfDay: Long): Flow<List<QuizHistoryEntity>> {
        return quizHistoryDao.getTodayHistory(startOfDay)
    }

    /**
     * 获取指定时间范围内的答题历史记录（一次性，用于统计周期聚合）
     * @param start 起始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 符合条件的历史记录列表
     */
    suspend fun getQuizHistoryByDateRange(start: Long, end: Long): List<QuizHistoryEntity> {
        return quizHistoryDao.getHistoryByDateRange(start, end)
    }

    /**
     * 获取测验统计数据
     * @return 包含答对数和总数的统计对象
     */
    suspend fun getQuizStats(): QuizStats {
        val correctCount = quizHistoryDao.getCorrectCount()
        val totalCount = quizHistoryDao.getTotalCount()
        return QuizStats(
            correctCount = correctCount,
            totalCount = totalCount
        )
    }

    /**
     * 测验统计数据类
     * @property correctCount 答对题数
     * @property totalCount 答题总数
     */
    data class QuizStats(
        val correctCount: Int,
        val totalCount: Int
    )
}
