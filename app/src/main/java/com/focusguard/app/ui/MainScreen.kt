package com.focusguard.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusguard.app.MainActivity
import com.focusguard.app.ui.apps.AppsScreen
import com.focusguard.app.ui.cat.CatScreen
import com.focusguard.app.ui.components.SimpleNavEntry
import com.focusguard.app.ui.components.SimpleNavigationBar
import com.focusguard.app.ui.cat.CatSelectionScreen
import com.focusguard.app.ui.focus.FocusScreen
import com.focusguard.app.ui.settings.SettingsScreen
import com.focusguard.app.ui.settings.QuotesScreen
import com.focusguard.app.ui.settings.ReflectionQuestionsScreen
import com.focusguard.app.ui.stats.StatsScreen

/**
 * 底部导航项数据类
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    /** 猫咪（默认首页） */
    object Cat : BottomNavItem("cat", "猫咪", Icons.Filled.Pets)

    /** 约束应用 */
    object Apps : BottomNavItem("apps", "约束应用", Icons.Filled.Shield)

    /** 统计 */
    object Stats : BottomNavItem("stats", "统计", Icons.Filled.BarChart)

    /** 设置 */
    object Settings : BottomNavItem("settings", "设置", Icons.Filled.Settings)
}

/** 所有底部导航项列表 */
private val bottomNavItems = listOf(
    BottomNavItem.Cat,
    BottomNavItem.Apps,
    BottomNavItem.Stats,
    BottomNavItem.Settings
)

/** 底部导航栏渲染用的数据（与路由表一一对应） */
private val bottomNavEntries = bottomNavItems.map {
    SimpleNavEntry(route = it.route, label = it.title, icon = it.icon)
}

/**
 * 主界面入口
 * 根据猫咪选择状态决定显示选择页还是主界面
 */
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    // 状态切换使用 AnimatedContent 淡入淡出，避免 Loading → Ready 的硬切
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(300))
        },
        label = "main_state"
    ) { currentState ->
        when (currentState) {
            MainUiState.Loading -> {
                // 入场淡入，避免突兀闪现
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(300),
                    label = "loading_fade"
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "FocusCat",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            MainUiState.NeedCatSelection -> {
                CatSelectionScreen(
                    onCatCreated = { mainViewModel.checkCatExistence() }
                )
            }

            MainUiState.Ready -> {
                MainTabScreen()
            }
        }
    }
}

/**
 * 主界面（底部导航 + 各页面）
 */
@Composable
private fun MainTabScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val hapticFeedback = LocalHapticFeedback.current

    // 统一的 Tab 切换导航：底部导航与首页卡片入口使用同一套逻辑，
    // 保证从首页进入猫咪/约束应用后，底部点首页能正常返回（行为一致）
    // 硬约束：所有 Tab 导航必须经此 helper，禁止直接 navController.navigate 切 Tab
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 收集「去投喂」导航事件：StateFlow 持续收集，即使 MainActivity 已在前台也能响应
    val navigateToCat by com.focusguard.app.MainActivity.navigateToCatEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigateToCat) {
        if (navigateToCat) {
            // 消费事件：重置标志并跳转到猫咪 Tab（经 navigateToTab 统一导航）
            com.focusguard.app.MainActivity.requestNavigateToCatConsumed()
            navigateToTab(BottomNavItem.Cat.route)
        }
    }

    // 收集「去添加约束应用」导航事件：从 CatScreen 空库存按钮触发
    val navigateToApps by com.focusguard.app.MainActivity.navigateToAppsEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigateToApps) {
        if (navigateToApps) {
            com.focusguard.app.MainActivity.requestNavigateToAppsConsumed()
            navigateToTab(BottomNavItem.Apps.route)
        }
    }

    // 收集「专注模式拉回」事件：FocusGuardService 检测到用户切屏时触发
    // 用户切到其他应用 → 服务拉回 MainActivity → MainScreen 跳到 Focus 路由
    val focusRecall by com.focusguard.app.MainActivity.focusRecallEvent.collectAsStateWithLifecycle()
    LaunchedEffect(focusRecall) {
        if (focusRecall) {
            com.focusguard.app.MainActivity.requestFocusRecallConsumed()
            // 跳到 Focus 路由（全屏页，非 Tab；若已在 Focus 页则 launchSingleTop 保证不重复创建）
            navController.navigate("focus") {
                launchSingleTop = true
            }
        }
    }

    // 当前路由是否为全屏页（专注模式）：全屏时隐藏底部导航栏
    val currentRoute = currentDestination?.route
    val isFullScreen = currentRoute == "focus"

    Scaffold(
        bottomBar = {
            // 全屏页（专注模式）不显示底部导航，避免用户切到其他 Tab 导致 bug
            if (!isFullScreen) {
                // 简洁底栏：自绘（非 M3 NavigationBar）
                // M3 的 NavigationBar 会把底部系统手势 inset 挤进固定高度，
                // 导致图标上方留白被压没、选中胶囊被裁切，故改为自绘以精确控制留白。
                // 尝试过液态玻璃（多层 Canvas 模拟折射），在纯色背景上只呈现"脏边缘"，
                // 反而破坏了简洁感，故回退到此方案。
                SimpleNavigationBar(
                    items = bottomNavEntries,
                    selectedRoute = currentDestination?.hierarchy
                        ?.firstOrNull { dest -> bottomNavEntries.any { it.route == dest.route } }
                        ?.route,
                    onSelect = { route ->
                        // Tab 切换是高频轻操作，按项目触觉规范使用 TextHandleMove（轻震）
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navigateToTab(route)
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Cat.route,
            // 全屏页不应用 bottom padding，让视频真正铺满全屏
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.02f, animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.02f, animationSpec = tween(200)) }
        ) {
            // 猫咪养成（默认首页）
            composable(BottomNavItem.Cat.route) {
                CatScreen(
                    onNavigateToFocus = { navController.navigate("focus") }
                )
            }
            // 约束应用
            composable(BottomNavItem.Apps.route) {
                AppsScreen()
            }
            // 统计
            composable(BottomNavItem.Stats.route) {
                StatsScreen(
                    onNavigateToFocus = { navController.navigate("focus") }
                )
            }
            // 设置
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    onNavigateToReflectionQuestions = {
                        navController.navigate("reflection_questions")
                    },
                    onNavigateToCatSwitch = {
                        navController.navigate("cat_switch")
                    },
                    onNavigateToQuotes = {
                        navController.navigate("quotes")
                    }
                )
            }
            // 小猫格言管理（需求 1）
            composable("quotes") {
                QuotesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // 专注模式（全屏，无底部导航）
            composable(
                route = "focus",
                enterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f, animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f, animationSpec = tween(200)) }
            ) {
                FocusScreen(
                    onExit = { navController.popBackStack() }
                )
            }
            // 切换猫咪（设置中入口，保留好感度）
            // 统一使用 NavHost 默认的 fadeIn + scaleIn 过渡，与 Tab 切换保持一致
            composable(route = "cat_switch") {
                CatSelectionScreen(
                    isSwitchMode = true,
                    onCatCreated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            // 反思问题管理
            // 统一使用 NavHost 默认的 fadeIn + scaleIn 过渡，与 Tab 切换保持一致
            composable(route = "reflection_questions") {
                ReflectionQuestionsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
