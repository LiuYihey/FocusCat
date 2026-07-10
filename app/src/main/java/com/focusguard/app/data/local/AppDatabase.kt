package com.focusguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.focusguard.app.data.local.entity.AffinityAchievementEntity
import com.focusguard.app.data.local.entity.BlockedAppEntity
import com.focusguard.app.data.local.entity.CatCatalogEntity
import com.focusguard.app.data.local.entity.FoodCatalogEntity
import com.focusguard.app.data.local.entity.FoodInventoryEntity
import com.focusguard.app.data.local.entity.FocusSessionEntity
import com.focusguard.app.data.local.entity.QuizHistoryEntity
import com.focusguard.app.data.local.entity.QuizQuestionEntity
import com.focusguard.app.data.local.entity.ReflectionAnswerEntity
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity
import com.focusguard.app.data.local.entity.UserCatEntity

/**
 * 应用主数据库
 * 包含被拦截应用、测验题目、测验历史、专注会话、
 * 猫咪品种目录、用户猫咪、食物目录、食物库存、
 * 反思问题、反思回答、好感度成就共 11 张表
 *
 * P1-4：开启 exportSchema=true，让 Room 编译期导出 JSON schema 到
 * app/schemas/，便于版本化追踪数据库结构变化、CI 校验、自动生成迁移测试。
 */
@Database(
    entities = [
        BlockedAppEntity::class,
        QuizQuestionEntity::class,
        QuizHistoryEntity::class,
        FocusSessionEntity::class,
        CatCatalogEntity::class,
        UserCatEntity::class,
        FoodCatalogEntity::class,
        FoodInventoryEntity::class,
        ReflectionQuestionEntity::class,
        ReflectionAnswerEntity::class,
        AffinityAchievementEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /** 被拦截应用 DAO */
    abstract fun blockedAppDao(): BlockedAppDao

    /** 测验题目 DAO（保留兼容） */
    abstract fun quizQuestionDao(): QuizQuestionDao

    /** 测验历史 DAO（保留兼容） */
    abstract fun quizHistoryDao(): QuizHistoryDao

    /** 专注会话 DAO */
    abstract fun focusSessionDao(): FocusSessionDao

    /** 猫咪品种目录 DAO */
    abstract fun catCatalogDao(): CatCatalogDao

    /** 用户猫咪 DAO */
    abstract fun userCatDao(): UserCatDao

    /** 食物目录 DAO */
    abstract fun foodCatalogDao(): FoodCatalogDao

    /** 食物库存 DAO */
    abstract fun foodInventoryDao(): FoodInventoryDao

    /** 反思问题 DAO */
    abstract fun reflectionQuestionDao(): ReflectionQuestionDao

    /** 反思回答 DAO */
    abstract fun reflectionAnswerDao(): ReflectionAnswerDao

    /** 好感度成就 DAO */
    abstract fun affinityAchievementDao(): AffinityAchievementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2 迁移：新增 FocusCat 相关 7 张表
         * 保留原有 4 张表数据，不破坏用户已有配置
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 猫咪品种目录
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS cat_catalog (
                        breedId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconAsset TEXT NOT NULL,
                        idleAnimAsset TEXT NOT NULL,
                        eatAnimAsset TEXT NOT NULL,
                        happyAnimAsset TEXT NOT NULL
                    )
                """.trimIndent())

                // 用户猫咪（单例）
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_cat (
                        id INTEGER NOT NULL PRIMARY KEY,
                        breedId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        affinityLevel INTEGER NOT NULL,
                        totalFeedCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 食物目录
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS food_catalog (
                        foodId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        rarity TEXT NOT NULL,
                        affinityBonus INTEGER NOT NULL,
                        dropRate REAL NOT NULL,
                        iconAsset TEXT NOT NULL
                    )
                """.trimIndent())

                // 食物库存
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS food_inventory (
                        foodId TEXT NOT NULL PRIMARY KEY,
                        count INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 反思问题
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reflection_questions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questionText TEXT NOT NULL,
                        `order` INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        isCustom INTEGER NOT NULL,
                        placeholder TEXT NOT NULL
                    )
                """.trimIndent())

                // 反思回答
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reflection_answers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questionId INTEGER NOT NULL,
                        answerText TEXT NOT NULL,
                        targetApp TEXT,
                        answeredAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 好感度成就
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS affinity_achievements (
                        milestone INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        unlockedAt INTEGER
                    )
                """.trimIndent())
            }
        }

        /**
         * v2 → v3 迁移：食物目录调整（移除鸡胸肉，foodId 改为中文与图片文件名一致）
         * 清空 food_catalog 和 food_inventory，由 initSeedData 重新写入种子数据
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM food_inventory")
                database.execSQL("DELETE FROM food_catalog")
            }
        }

        /**
         * v3 → v4 迁移：反思问题集同步
         * SeedData.getSeedReflectionQuestions() 已移除第 3 个问题（"你这段时间的长期目标是什么？"），
         * 但 MainActivity.initSeedData 仅在 countActive()==0 时写入种子，老用户仍保留 3 题。
         * 此迁移删除已被移除的第 3 题，并对剩余问题的 order 重新索引为连续值（1, 2, ...）。
         *
         * FK 行为：reflection_answers 表无 @ForeignKey 约束（实体仅持有 questionId 字段），
         * 删除问题后历史回答记录保留，questionId 成为孤儿 ID —— 作为历史数据可接受。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 删除已移除的第 3 个反思问题（按问题文本精确匹配）
                database.execSQL("DELETE FROM reflection_questions WHERE questionText = '你这段时间的长期目标是什么？'")
                // 重新索引剩余问题的 order 字段为连续值（1, 2, ...），按 (order, id) 排序。
                // 使用临时表预计算新 order，避免 UPDATE 关联子查询看到已更新行的值。
                database.execSQL("CREATE TEMP TABLE IF NOT EXISTS _rq_reorder (id INTEGER PRIMARY KEY, new_order INTEGER NOT NULL)")
                database.execSQL("DELETE FROM _rq_reorder")
                database.execSQL("""
                    INSERT INTO _rq_reorder (id, new_order)
                    SELECT r1.id, (
                        SELECT COUNT(*) + 1
                        FROM reflection_questions r2
                        WHERE r2.`order` < r1.`order`
                           OR (r2.`order` = r1.`order` AND r2.id < r1.id)
                    )
                    FROM reflection_questions r1
                """.trimIndent())
                database.execSQL("""
                    UPDATE reflection_questions
                    SET `order` = (SELECT new_order FROM _rq_reorder WHERE _rq_reorder.id = reflection_questions.id)
                """.trimIndent())
                database.execSQL("DROP TABLE IF EXISTS _rq_reorder")
            }
        }

        /**
         * v4 → v5 迁移：用户猫咪多实例化
         *
         * 原表结构：id 固定为 1（单例），无 isActive 字段
         * 新表结构：id 自增，新增 isActive 字段标记当前激活的猫
         *
         * 迁移逻辑：
         * 1. 创建新表 user_cat_new（id 自增、isActive 字段）
         * 2. 将旧表数据迁移到新表，旧猫设为 isActive=1（保留为当前激活）
         * 3. 删除旧表，重命名新表为 user_cat
         *
         * 这样老用户的猫咪数据（breedId/name/affinityLevel/totalFeedCount）完整保留，
         * 后续新建猫时会自动将旧猫设为 inactive 并插入新猫为 active。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建新表（id 自增、isActive 字段）
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_cat_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        breedId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        affinityLevel INTEGER NOT NULL,
                        totalFeedCount INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // 迁移旧数据：旧单例猫（id=1）设为 isActive=1，保留所有养成数据
                database.execSQL("""
                    INSERT INTO user_cat_new (breedId, name, affinityLevel, totalFeedCount, isActive, createdAt)
                    SELECT breedId, name, affinityLevel, totalFeedCount, 1, createdAt
                    FROM user_cat
                """.trimIndent())
                // 替换旧表
                database.execSQL("DROP TABLE user_cat")
                database.execSQL("ALTER TABLE user_cat_new RENAME TO user_cat")
            }
        }

        /**
         * 获取数据库单例实例
         * @param context 上下文
         * @return 数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // 仅在数据库降级时销毁数据，升级时通过 Migration 保留用户数据
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
