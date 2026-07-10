package com.focusguard.app.data.repository

import com.focusguard.app.data.local.dao.ReflectionAnswerDao
import com.focusguard.app.data.local.dao.ReflectionQuestionDao
import com.focusguard.app.data.local.entity.ReflectionAnswerEntity
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity

/**
 * 反思问答仓库
 * 封装反思问题和回答记录的数据访问
 */
class ReflectionRepository(
    private val questionDao: ReflectionQuestionDao,
    private val answerDao: ReflectionAnswerDao
) {

    /** 获取所有启用的反思问题（按顺序） */
    suspend fun getActiveQuestions(): List<ReflectionQuestionEntity> =
        questionDao.getActiveQuestions()

    /** 获取所有反思问题（含禁用） */
    suspend fun getAllQuestions(): List<ReflectionQuestionEntity> =
        questionDao.getAllQuestions()

    /** 获取启用问题数量 */
    suspend fun getActiveQuestionCount(): Int = questionDao.countActive()

    /** 获取当前最大排序值（用于新增问题时分配 order，避免并发保存导致 order 重复） */
    suspend fun getMaxOrder(): Int = questionDao.getMaxOrder()

    /** 更新问题 */
    suspend fun updateQuestion(question: ReflectionQuestionEntity) {
        questionDao.update(question)
    }

    /** 删除问题 */
    suspend fun deleteQuestion(question: ReflectionQuestionEntity) {
        questionDao.delete(question)
    }

    /** 插入新问题 */
    suspend fun addQuestion(question: ReflectionQuestionEntity) {
        questionDao.insert(question)
    }

    /**
     * 批量保存用户回答
     * @param answers 回答列表
     */
    suspend fun saveAnswers(answers: List<ReflectionAnswerEntity>) {
        answerDao.insertAll(answers)
    }

    /** 获取今日回答数量 */
    suspend fun getTodayAnswerCount(startOfDay: Long): Int =
        answerDao.getTodayCount(startOfDay)

    /** 获取总回答数量 */
    suspend fun getTotalAnswerCount(): Int = answerDao.getTotalCount()
}
