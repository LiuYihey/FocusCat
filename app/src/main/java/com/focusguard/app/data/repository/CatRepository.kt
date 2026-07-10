package com.focusguard.app.data.repository

import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.focusguard.app.data.local.dao.AffinityAchievementDao
import com.focusguard.app.data.local.dao.CatCatalogDao
import com.focusguard.app.data.local.dao.FoodInventoryDao
import com.focusguard.app.data.local.dao.UserCatDao
import com.focusguard.app.data.local.entity.AffinityAchievementEntity
import com.focusguard.app.data.local.entity.CatCatalogEntity
import com.focusguard.app.data.local.entity.UserCatEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 猫咪仓库
 * 封装猫咪品种目录、用户猫咪、好感度成就的数据访问
 *
 * 喂食频率限制：每个自然日（0:00-24:00）最多 [MAX_FEED_PER_DAY] 次，
 * 超出后 [canFeedNow] 返回 false，由 UI 提示"小猫已经吃饱啦，明天再喂吧~"。
 * 跨天自动重置（以本地时区 0 点为界）。
 * 该限制为全局共享（不分猫咪），符合"切换猫咪时好感度保持不变"的语义。
 */
class CatRepository(
    private val db: RoomDatabase,
    private val catCatalogDao: CatCatalogDao,
    private val userCatDao: UserCatDao,
    private val achievementDao: AffinityAchievementDao,
    private val foodInventoryDao: FoodInventoryDao,
    private val feedLimitPrefs: SharedPreferences
) {

    companion object {
        /** 每个自然日最多喂食次数 */
        private const val MAX_FEED_PER_DAY = 5
        /** SharedPreferences key：存储"当天喂食次数" */
        private const val KEY_FEED_COUNT_TODAY = "feed_count_today"
        /** SharedPreferences key：存储"上次喂食所属的自然日（yyyyMMdd）" */
        private const val KEY_FEED_DAY_KEY = "feed_day_key"
    }

    /**
     * 是否还能继续喂食（今日未超过 [MAX_FEED_PER_DAY] 次）
     */
    fun canFeedNow(): Boolean = getFeedCountToday() < MAX_FEED_PER_DAY

    /**
     * 今日剩余可喂食次数（0 表示已达上限）
     */
    fun getRemainingFeedCountToday(): Int =
        (MAX_FEED_PER_DAY - getFeedCountToday()).coerceAtLeast(0)

    /**
     * 获取今日已喂食次数，跨天时自动归零
     */
    private fun getFeedCountToday(): Int {
        val todayKey = todayKey()
        val savedDayKey = feedLimitPrefs.getString(KEY_FEED_DAY_KEY, null)
        // 跨天：次数归零，更新日期 key
        if (savedDayKey != todayKey) {
            feedLimitPrefs.edit()
                .putString(KEY_FEED_DAY_KEY, todayKey)
                .putInt(KEY_FEED_COUNT_TODAY, 0)
                .apply()
            return 0
        }
        return feedLimitPrefs.getInt(KEY_FEED_COUNT_TODAY, 0)
    }

    /**
     * 记录一次喂食，今日计数 +1（跨天则从 1 开始）
     */
    private fun recordFeedTimestamp() {
        val todayKey = todayKey()
        val savedDayKey = feedLimitPrefs.getString(KEY_FEED_DAY_KEY, null)
        val newCount = if (savedDayKey == todayKey) {
            feedLimitPrefs.getInt(KEY_FEED_COUNT_TODAY, 0) + 1
        } else {
            1
        }
        feedLimitPrefs.edit()
            .putString(KEY_FEED_DAY_KEY, todayKey)
            .putInt(KEY_FEED_COUNT_TODAY, newCount)
            .apply()
    }

    /** 今日日期 key（yyyyMMdd，本地时区） */
    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            "%04d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 获取所有猫咪品种 */
    suspend fun getAllBreeds(): List<CatCatalogEntity> = catCatalogDao.getAllBreeds()

    /** 观察所有猫咪品种（Flow），种子数据插入后自动通知 UI */
    fun observeAllBreeds(): Flow<List<CatCatalogEntity>> = catCatalogDao.observeAllBreeds()

    /** 根据品种 ID 获取品种 */
    suspend fun getBreedById(breedId: String): CatCatalogEntity? = catCatalogDao.getById(breedId)

    /** 观察用户猫咪（Flow） */
    fun observeUserCat(): Flow<UserCatEntity?> = userCatDao.observeUserCat()

    /** 获取用户猫咪 */
    suspend fun getUserCat(): UserCatEntity? = userCatDao.getUserCat()

    /** 用户是否已创建过任何猫咪 */
    suspend fun hasCat(): Boolean = userCatDao.hasAnyCat()

    /** 观察所有已创建的猫咪（用于切换猫咪列表） */
    fun observeAllCats(): Flow<List<UserCatEntity>> = userCatDao.observeAllCats()

    /** 获取所有已创建的猫咪 */
    suspend fun getAllCats(): List<UserCatEntity> = userCatDao.getAllCats()

    /**
     * 创建新猫咪（首次选择或新增猫咪）
     *
     * 多实例化设计：每只猫是独立记录，有独立的 affinityLevel/totalFeedCount。
     * 创建时自动将旧猫（若有）设为 inactive，新猫设为 active。
     *
     * @param breedId 品种 ID
     * @param name 猫咪名字
     */
    suspend fun createCat(breedId: String, name: String) {
        db.withTransaction {
            userCatDao.deactivateAll()
            userCatDao.insert(
                UserCatEntity(
                    breedId = breedId,
                    name = name,
                    isActive = true
                )
            )
        }
    }

    /**
     * 切换到指定猫咪（实例化切换，恢复该猫的独立数据）
     *
     * 每只猫有独立的 name/affinityLevel/totalFeedCount，切换即恢复该猫的完整状态。
     * 类似"切换账号"：不同猫的养成进度独立保留。
     *
     * @param catId 目标猫咪 ID
     */
    suspend fun switchToCat(catId: Int) {
        db.withTransaction {
            userCatDao.deactivateAll()
            userCatDao.activateCat(catId)
        }
    }

    /** 删除指定猫咪（不可删除当前激活的猫） */
    suspend fun deleteCat(catId: Int) {
        userCatDao.deleteCatById(catId)
    }

    /**
     * 投喂猫咪：增加投喂次数和好感度，并检查成就解锁
     *
     * 修复 B3：用 @Transaction 包裹多步写操作，确保 totalFeedCount+1 与成就解锁原子完成。
     * 原代码无事务，进程在 incrementFeed 后被杀会导致 totalFeedCount 已+1 但成就未解锁，
     * 下次投喂才解锁本应本次解锁的成就，进度错位。
     *
     * 投喂频率限制：事务成功后记录时间戳，[canFeedNow] 据此判断是否已超 24h/5 次上限。
     * 调用方应在调用前先调用 [canFeedNow]，超限时给出"小猫已经吃饱啦"提示。
     *
     * @param affinityBonus 该食物提供的好感度加成
     * @return 新解锁的成就列表（可能为空）
     */
    suspend fun feedCat(affinityBonus: Int): List<AffinityAchievementEntity> {
        val newlyUnlocked = db.withTransaction {
            userCatDao.incrementFeed(affinityBonus)
            val cat = userCatDao.getUserCat() ?: return@withTransaction emptyList()
            // 检查是否有新成就可解锁
            val lockedAchievements = achievementDao.getLockedAchievements()
            val newlyUnlocked = mutableListOf<AffinityAchievementEntity>()
            val now = System.currentTimeMillis()
            for (achievement in lockedAchievements) {
                if (cat.totalFeedCount >= achievement.milestone) {
                    achievementDao.unlock(achievement.milestone, now)
                    newlyUnlocked.add(achievement.copy(unlockedAt = now))
                }
            }
            newlyUnlocked
        }
        // 事务成功后才记录喂食时间戳，失败回滚则不占用每日额度
        recordFeedTimestamp()
        return newlyUnlocked
    }

    /**
     * 投喂猫咪（含消耗食物）：单事务包裹"消耗食物 + 增加好感度 + 解锁成就"
     *
     * 修复迭代5 Bug #3：原 CatViewModel 分两步调用
     * foodRepository.consumeOne(foodId) + catRepository.feedCat(affinityBonus)，
     * 两个独立写操作无外层事务。若 consumeOne 成功后 feedCat 抛异常，食物被消耗但
     * totalFeedCount/affinityLevel 未增加，成就未解锁，数据不一致。
     *
     * 此方法将三步合并为一个 Room 事务，任一步失败整体回滚。
     *
     * 投喂频率限制：事务成功后记录时间戳，[canFeedNow] 据此判断是否已超 24h/5 次上限。
     * 调用方应在调用前先调用 [canFeedNow]，超限时给出"小猫已经吃饱啦"提示。
     *
     * @param foodId 要消耗的食物 ID
     * @param affinityBonus 该食物提供的好感度加成
     * @return 新解锁的成就列表（可能为空）
     */
    suspend fun feedCatWithFood(
        foodId: String,
        affinityBonus: Int
    ): List<AffinityAchievementEntity> {
        val newlyUnlocked = db.withTransaction {
            foodInventoryDao.consumeOne(foodId, System.currentTimeMillis())
            userCatDao.incrementFeed(affinityBonus)
            val cat = userCatDao.getUserCat() ?: return@withTransaction emptyList()
            val lockedAchievements = achievementDao.getLockedAchievements()
            val newlyUnlocked = mutableListOf<AffinityAchievementEntity>()
            val now = System.currentTimeMillis()
            for (achievement in lockedAchievements) {
                if (cat.totalFeedCount >= achievement.milestone) {
                    achievementDao.unlock(achievement.milestone, now)
                    newlyUnlocked.add(achievement.copy(unlockedAt = now))
                }
            }
            newlyUnlocked
        }
        // 事务成功后才记录喂食时间戳，失败回滚则不占用每日额度
        recordFeedTimestamp()
        return newlyUnlocked
    }

    /** 观察所有成就 */
    fun observeAchievements(): Flow<List<AffinityAchievementEntity>> =
        achievementDao.observeAllAchievements()

    /** 获取所有成就 */
    suspend fun getAllAchievements(): List<AffinityAchievementEntity> =
        achievementDao.getAllAchievements()
}
