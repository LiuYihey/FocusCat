package com.focusguard.app.data

import com.focusguard.app.data.local.entity.AffinityAchievementEntity
import com.focusguard.app.data.local.entity.CatCatalogEntity
import com.focusguard.app.data.local.entity.FoodCatalogEntity
import com.focusguard.app.data.local.entity.QuizQuestionEntity
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity

/**
 * 种子数据对象
 * 用于应用首次启动时初始化题库、猫咪品种、食物目录、反思问题、成就
 */
object SeedData {

    /**
     * 获取种子题目列表（保留兼容旧版选择题）
     */
    fun getSeedQuestions(): List<QuizQuestionEntity> {
        return listOf(
            QuizQuestionEntity(
                question = "2 + 3 等于多少？",
                optionsJson = """["3","4","5","6"]""",
                correctIndex = 2,
                category = "数学",
                difficulty = 1
            ),
            QuizQuestionEntity(
                question = "中国的首都是？",
                optionsJson = """["上海","北京","广州","深圳"]""",
                correctIndex = 1,
                category = "常识",
                difficulty = 1
            )
        )
    }

    /**
     * 获取猫咪品种种子数据
     * 2 种品种（布偶猫、橘猫），每种对应不同的动画资源名
     */
    fun getSeedCatBreeds(): List<CatCatalogEntity> {
        return listOf(
            CatCatalogEntity(
                breedId = "ragdoll",
                displayName = "布偶",
                description = "蓝眼温柔，粘人治愈，专注路上的最佳搭档",
                iconAsset = "cat_ragdoll",
                idleAnimAsset = "cat_ragdoll_idle",
                eatAnimAsset = "cat_ragdoll_eat",
                happyAnimAsset = "cat_ragdoll_happy"
            ),
            CatCatalogEntity(
                breedId = "orange",
                displayName = "橘猫",
                description = "憨厚贪吃，亲和力满分，永远在等下一顿",
                iconAsset = "cat_orange",
                idleAnimAsset = "cat_orange_idle",
                eatAnimAsset = "cat_orange_eat",
                happyAnimAsset = "cat_orange_happy"
            )
        )
    }

    /**
     * 获取食物目录种子数据
     * 4 种食物，不同稀有度和好感度加成
     * foodId 与 assets/foods 下的图片文件名一致
     */
    fun getSeedFoods(): List<FoodCatalogEntity> {
        return listOf(
            FoodCatalogEntity(
                foodId = "猫粮",
                displayName = "猫粮",
                rarity = "common",
                affinityBonus = 1,
                dropRate = 0.40,
                iconAsset = "food_cat_food"
            ),
            FoodCatalogEntity(
                foodId = "冻干",
                displayName = "冻干",
                rarity = "rare",
                affinityBonus = 3,
                dropRate = 0.20,
                iconAsset = "food_freeze_dried"
            ),
            FoodCatalogEntity(
                foodId = "酸奶",
                displayName = "酸奶",
                rarity = "rare",
                affinityBonus = 3,
                dropRate = 0.20,
                iconAsset = "food_yogurt"
            ),
            FoodCatalogEntity(
                foodId = "罐头",
                displayName = "罐头",
                rarity = "epic",
                affinityBonus = 5,
                dropRate = 0.20,
                iconAsset = "food_canned"
            )
        )
    }

    /**
     * 获取默认反思问题种子数据
     * 2 个目标强化问题，无标准答案
     */
    fun getSeedReflectionQuestions(): List<ReflectionQuestionEntity> {
        return listOf(
            ReflectionQuestionEntity(
                questionText = "你进入这个 app 是来做什么的？",
                order = 1,
                isActive = true,
                isCustom = false,
                placeholder = "写下你此刻的真实目的..."
            ),
            ReflectionQuestionEntity(
                questionText = "你今天的 todo list 还有哪些？",
                order = 2,
                isActive = true,
                isCustom = false,
                placeholder = "列出今天还没完成的任务..."
            )
        )
    }

    /**
     * 获取好感度成就种子数据
     * 3 个里程碑：10/50/100 次投喂
     */
    fun getSeedAchievements(): List<AffinityAchievementEntity> {
        return listOf(
            AffinityAchievementEntity(
                milestone = 10,
                title = "初识之友",
                description = "投喂 10 次，猫咪开始信任你"
            ),
            AffinityAchievementEntity(
                milestone = 50,
                title = "默契伙伴",
                description = "投喂 50 次，猫咪与你心意相通"
            ),
            AffinityAchievementEntity(
                milestone = 100,
                title = "灵魂伴侣",
                description = "投喂 100 次，猫咪是你最忠实的专注伙伴"
            )
        )
    }
}
