package com.focusguard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.focusguard.app.data.SeedData
import com.focusguard.app.data.local.dao.AffinityAchievementDao
import com.focusguard.app.data.local.dao.CatCatalogDao
import com.focusguard.app.data.local.dao.FoodCatalogDao
import com.focusguard.app.data.local.dao.QuizQuestionDao
import com.focusguard.app.data.local.dao.ReflectionQuestionDao
import com.focusguard.app.data.repository.AppRepositoryImpl
import com.focusguard.app.detection.AppDetectionManager
import com.focusguard.app.service.FocusGuardService
import com.focusguard.app.ui.MainScreen
import com.focusguard.app.ui.theme.FocusGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主 Activity
 * 应用入口，使用 Compose 构建 UI，使用 Hilt 进行依赖注入
 * 负责初始化种子数据、更新拦截列表、启动前台保活服务
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Intent extra：专注模式防分心拉回标记，FocusGuardService 检测到用户切屏时带上 */
        const val EXTRA_FOCUS_MODE_RECALL = "focus_mode_recall"

        /** 通知权限请求码（Android 13+ POST_NOTIFICATIONS 运行时权限） */
        private const val REQUEST_POST_NOTIFICATIONS = 1001

        /** 一次性导航事件流：从 ConfirmActivity "去投喂"跳转时发射 true，MainScreen 收集后跳猫咪 Tab */
        private val _navigateToCatEvent = MutableStateFlow(false)
        val navigateToCatEvent = _navigateToCatEvent.asStateFlow()

        /** 供 ConfirmActivity 调用：触发跳转到猫咪 Tab */
        fun requestNavigateToCat() {
            _navigateToCatEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志，避免重复跳转 */
        fun requestNavigateToCatConsumed() {
            _navigateToCatEvent.value = false
        }

        /** 一次性导航事件流：从 CatScreen 空库存"去添加约束应用"跳转时发射 true */
        private val _navigateToAppsEvent = MutableStateFlow(false)
        val navigateToAppsEvent = _navigateToAppsEvent.asStateFlow()

        /** 供 CatScreen 调用：触发跳转到约束应用 Tab */
        fun requestNavigateToApps() {
            _navigateToAppsEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志，避免重复跳转 */
        fun requestNavigateToAppsConsumed() {
            _navigateToAppsEvent.value = false
        }

        /** 一次性导航事件流：FocusGuardService 专注模式拉回时发射 true，MainScreen 跳 Focus 路由 */
        private val _focusRecallEvent = MutableStateFlow(false)
        val focusRecallEvent = _focusRecallEvent.asStateFlow()

        /** 供 FocusGuardService 调用：触发跳转回专注页 */
        fun requestFocusRecall() {
            _focusRecallEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志 */
        fun requestFocusRecallConsumed() {
            _focusRecallEvent.value = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 专注模式防分心拉回：FocusGuardService 检测到用户切屏后通过 Intent extra 通知
        if (intent.getBooleanExtra(EXTRA_FOCUS_MODE_RECALL, false)) {
            requestFocusRecall()
        }
    }

    /** 应用仓库，用于更新拦截列表 */
    @Inject
    lateinit var appRepository: AppRepositoryImpl

    /** 测验题目 DAO，用于初始化种子题库 */
    @Inject
    lateinit var quizQuestionDao: QuizQuestionDao

    /** 猫咪品种目录 DAO */
    @Inject
    lateinit var catCatalogDao: CatCatalogDao

    /** 食物目录 DAO */
    @Inject
    lateinit var foodCatalogDao: FoodCatalogDao

    /** 反思问题 DAO */
    @Inject
    lateinit var reflectionQuestionDao: ReflectionQuestionDao

    /** 好感度成就 DAO */
    @Inject
    lateinit var achievementDao: AffinityAchievementDao

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装 Splash Screen（必须在 super.onCreate 之前）
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 启用 edge-to-edge 沉浸式显示
        enableEdgeToEdge()
        // 初始化守护开关状态（从 SharedPreferences 恢复）
        AppDetectionManager.init(this)
        setContent {
            FocusGuardTheme {
                MainScreen()
            }
        }
        // 初始化所有种子数据
        initSeedData()
        // 更新拦截列表
        updateBlockedApps()
        // Android 13+ 需运行时请求通知权限，否则前台服务通知不显示、部分 ROM startForeground 崩溃
        requestNotificationPermissionIfNeeded()
        // 启动前台保活服务
        startFocusGuardService()
        // 处理冷启动时的专注模式拉回 Intent（进程被杀后重新拉起）
        if (intent?.getBooleanExtra(EXTRA_FOCUS_MODE_RECALL, false) == true) {
            requestFocusRecall()
        }
    }

    /**
     * 初始化种子数据
     * 首次启动时插入：题库、猫咪品种、食物目录、反思问题、成就
     */
    private fun initSeedData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 兼容旧版选择题题库
            if (quizQuestionDao.count() == 0) {
                quizQuestionDao.insertAll(SeedData.getSeedQuestions())
            }
            // 猫咪品种目录
            if (catCatalogDao.getAllBreeds().isEmpty()) {
                catCatalogDao.insertAll(SeedData.getSeedCatBreeds())
            }
            // 食物目录
            if (foodCatalogDao.getAllFoods().isEmpty()) {
                foodCatalogDao.insertAll(SeedData.getSeedFoods())
            } else {
                // 同步最新 dropRate（种子数据可能因版本更新而调整概率）
                SeedData.getSeedFoods().forEach { seed ->
                    foodCatalogDao.updateDropRate(seed.foodId, seed.dropRate)
                }
            }
            // 反思问题
            if (reflectionQuestionDao.countActive() == 0) {
                reflectionQuestionDao.insertAll(SeedData.getSeedReflectionQuestions())
            }
            // 好感度成就
            if (achievementDao.getAllAchievements().isEmpty()) {
                achievementDao.insertAll(SeedData.getSeedAchievements())
            }
        }
    }

    /**
     * 更新拦截列表
     * 从数据库获取激活的拦截应用，更新到 AppDetectionManager
     */
    private fun updateBlockedApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val activeApps = appRepository.getActiveBlockedApps()
            val packages = activeApps.map { it.packageName }.toSet()
            AppDetectionManager.updateBlockedApps(packages)
        }
    }

    /**
     * 启动前台保活服务
     * 仅在守护开关开启时启动，避免关闭状态下仍常驻通知
     */
    private fun startFocusGuardService() {
        if (!AppDetectionManager.isProtectionEnabled) return
        try {
            val intent = Intent(this, FocusGuardService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限，忽略避免崩溃
        }
    }

    /**
     * Android 13+ 请求 POST_NOTIFICATIONS 运行时权限
     * 前台服务需要通知权限才能显示保活通知；部分 ROM 在无权限时 startForeground 会崩溃
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        try {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        } catch (e: Exception) {
            // 部分 ROM 可能不支持，忽略避免崩溃
        }
    }
}
