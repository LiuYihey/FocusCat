package com.focusguard.app.data.repository

import com.focusguard.app.data.local.dao.QuoteDao
import com.focusguard.app.data.local.entity.QuoteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 小猫格言仓库
 *
 * 需求 1：退出被约束应用时不再发放猫粮，改为随机展示一句小猫格言。
 * 格言由用户自行编写，可在「设置 → 小猫格言管理」中增删改。
 */
@Singleton
class QuoteRepository @Inject constructor(
    private val quoteDao: QuoteDao
) {

    /** 获取所有格言（含停用的），按 order 升序 */
    suspend fun getAllQuotes(): List<QuoteEntity> = quoteDao.getAllQuotes()

    /** 获取启用中的格言数量 */
    suspend fun countActive(): Int = quoteDao.countActive()

    /** 获取最大排序值 */
    suspend fun getMaxOrder(): Int = quoteDao.getMaxOrder()

    /**
     * 随机抽取一句格言
     *
     * 优先从启用中的格言里抽；全部停用时回退到所有格言；
     * 一条都没有时返回 null（此时调用方需降级处理，不阻断主流程）。
     */
    suspend fun getRandomQuote(): QuoteEntity? {
        val all = quoteDao.getAllQuotes()
        if (all.isEmpty()) return null
        val active = all.filter { it.isActive }
        val pool = active.ifEmpty { all }
        return pool[Random.nextInt(pool.size)]
    }

    /** 新增格言（order 由调用方根据 getMaxOrder 分配） */
    suspend fun addQuote(quote: QuoteEntity) {
        quoteDao.insert(quote)
    }

    /** 更新格言 */
    suspend fun updateQuote(quote: QuoteEntity) {
        quoteDao.update(quote)
    }

    /** 删除格言 */
    suspend fun deleteQuote(quote: QuoteEntity) {
        quoteDao.delete(quote)
    }
}
