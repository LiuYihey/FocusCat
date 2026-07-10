package com.focusguard.app.di

import android.content.Context
import com.focusguard.app.data.local.AppDatabase
import com.focusguard.app.data.local.dao.AffinityAchievementDao
import com.focusguard.app.data.local.dao.BlockedAppDao
import com.focusguard.app.data.local.dao.CatCatalogDao
import com.focusguard.app.data.local.dao.FoodCatalogDao
import com.focusguard.app.data.local.dao.FoodInventoryDao
import com.focusguard.app.data.local.dao.FocusSessionDao
import com.focusguard.app.data.local.dao.QuizHistoryDao
import com.focusguard.app.data.local.dao.QuizQuestionDao
import com.focusguard.app.data.local.dao.ReflectionAnswerDao
import com.focusguard.app.data.local.dao.ReflectionQuestionDao
import com.focusguard.app.data.local.dao.UserCatDao
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.data.repository.CatRepository
import com.focusguard.app.data.repository.FoodRepository
import com.focusguard.app.data.repository.QuizRepositoryImpl
import com.focusguard.app.data.repository.ReflectionRepository
import com.focusguard.app.data.repository.StatsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 * 提供数据库实例、各表 DAO 和各 Repository 的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 AppDatabase 单例
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    // ===== 原有 DAO =====

    @Provides
    fun provideBlockedAppDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    fun provideQuizQuestionDao(db: AppDatabase): QuizQuestionDao = db.quizQuestionDao()

    @Provides
    fun provideQuizHistoryDao(db: AppDatabase): QuizHistoryDao = db.quizHistoryDao()

    @Provides
    fun provideFocusSessionDao(db: AppDatabase): FocusSessionDao = db.focusSessionDao()

    // ===== FocusCat 新增 DAO =====

    @Provides
    fun provideCatCatalogDao(db: AppDatabase): CatCatalogDao = db.catCatalogDao()

    @Provides
    fun provideUserCatDao(db: AppDatabase): UserCatDao = db.userCatDao()

    @Provides
    fun provideFoodCatalogDao(db: AppDatabase): FoodCatalogDao = db.foodCatalogDao()

    @Provides
    fun provideFoodInventoryDao(db: AppDatabase): FoodInventoryDao = db.foodInventoryDao()

    @Provides
    fun provideReflectionQuestionDao(db: AppDatabase): ReflectionQuestionDao =
        db.reflectionQuestionDao()

    @Provides
    fun provideReflectionAnswerDao(db: AppDatabase): ReflectionAnswerDao =
        db.reflectionAnswerDao()

    @Provides
    fun provideAffinityAchievementDao(db: AppDatabase): AffinityAchievementDao =
        db.affinityAchievementDao()

    // ===== 原有 Repository =====

    @Provides
    @Singleton
    fun provideAppRepository(dao: BlockedAppDao): AppRepositoryImpl = AppRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideQuizRepository(
        db: AppDatabase,
        questionDao: QuizQuestionDao,
        historyDao: QuizHistoryDao
    ): QuizRepositoryImpl = QuizRepositoryImpl(db, questionDao, historyDao)

    @Provides
    @Singleton
    fun provideStatsRepository(dao: FocusSessionDao): StatsRepositoryImpl = StatsRepositoryImpl(dao)

    // ===== FocusCat 新增 Repository =====

    /**
     * 提供 CatRepository，注入喂食频率限制用的 SharedPreferences（独立文件名，避免与设置项混淆）
     */
    @Provides
    @Singleton
    fun provideCatRepository(
        @ApplicationContext context: Context,
        db: AppDatabase,
        catCatalogDao: CatCatalogDao,
        userCatDao: UserCatDao,
        achievementDao: AffinityAchievementDao,
        foodInventoryDao: FoodInventoryDao
    ): CatRepository = CatRepository(
        db = db,
        catCatalogDao = catCatalogDao,
        userCatDao = userCatDao,
        achievementDao = achievementDao,
        foodInventoryDao = foodInventoryDao,
        feedLimitPrefs = context.getSharedPreferences("focus_guard_feed_limit", Context.MODE_PRIVATE)
    )

    @Provides
    @Singleton
    fun provideFoodRepository(
        foodCatalogDao: FoodCatalogDao,
        foodInventoryDao: FoodInventoryDao
    ): FoodRepository = FoodRepository(foodCatalogDao, foodInventoryDao)

    @Provides
    @Singleton
    fun provideReflectionRepository(
        questionDao: ReflectionQuestionDao,
        answerDao: ReflectionAnswerDao
    ): ReflectionRepository = ReflectionRepository(questionDao, answerDao)
}
