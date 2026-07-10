package com.focusguard.app.util

import android.os.Build

/**
 * ROM 厂商检测工具
 *
 * 分发级 APP 在国产 ROM 上最大的障碍是后台保活：
 * 各厂商 ROM 对无障碍/前台服务的杀后台策略不同，需要针对性引导用户设置。
 *
 * 检测原理：通过 Build.MANUFACTURER / Build.BRAND 判断厂商，
 * 返回对应的「保活设置指引」文案与跳转动作。
 */
object RomUtils {

    enum class RomBrand {
        MIUI,       // 小米
        EMUI,       // 华为
        HARMONYOS,  // 华为鸿蒙
        HONOR,      // 荣耀（MagicOS，独立于华为后的新品牌，设置路径与 EMUI 不同）
        COLOROS,    // OPPO
        ORIGINOS,   // vivo
        ONEUI,      // 三星
        FLYME,      // 魅族
        STOCK,      // 原生 / Pixel / 其他
        UNKNOWN
    }

    /**
     * 检测当前设备 ROM 品牌
     */
    fun detectBrand(): RomBrand {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi") || brand.contains("poco") -> RomBrand.MIUI

            // 荣耀独立后运行 MagicOS，设置路径与华为 EMUI/鸿蒙不同，需单独处理
            brand.contains("honor") -> RomBrand.HONOR

            manufacturer.contains("huawei") || brand.contains("huawei") -> {
                // 鸿蒙与 EMUI 区分：鸿蒙 4.0+ 可通过 system property 检测
                // 简化处理：华为品牌统一当作 EMUI，鸿蒙保活步骤与 EMUI 基本一致
                RomBrand.EMUI
            }

            manufacturer.contains("oppo") || brand.contains("oppo") ||
                brand.contains("realme") || brand.contains("oneplus") -> RomBrand.COLOROS

            manufacturer.contains("vivo") || brand.contains("vivo") ||
                brand.contains("iqoo") -> RomBrand.ORIGINOS

            manufacturer.contains("samsung") || brand.contains("samsung") -> RomBrand.ONEUI

            manufacturer.contains("meizu") || brand.contains("meizu") -> RomBrand.FLYME

            manufacturer.contains("google") || brand.contains("google") ||
                brand.contains("pixel") -> RomBrand.STOCK

            else -> RomBrand.UNKNOWN
        }
    }

    /**
     * 国产 ROM 保活指引数据
     * @param title 引导标题
     * @param steps 具体操作步骤（用户照做即可保活）
     */
    data class KeepAliveGuide(
        val brandName: String,
        val title: String,
        val steps: List<String>
    )

    /**
     * 获取当前 ROM 的保活指引
     * 国产 ROM 返回详细步骤，原生/未知 ROM 返回通用指引
     */
    fun getKeepAliveGuide(): KeepAliveGuide {
        return when (detectBrand()) {
            RomBrand.MIUI -> KeepAliveGuide(
                brandName = "小米 / Redmi（MIUI）",
                title = "MIUI 保活设置",
                steps = listOf(
                    "「安全中心」→「应用管理」→ FocusCat →「自启动」打开",
                    "「安全中心」→「应用管理」→ FocusCat → 省电策略选「无限制」",
                    "「设置」→「应用设置」→「应用锁」确保未锁定 FocusCat",
                    "近期任务列表里 FocusCat 卡片下拉加锁，防止被一键清理"
                )
            )

            RomBrand.EMUI -> KeepAliveGuide(
                brandName = "华为（EMUI / 鸿蒙）",
                title = "EMUI / 鸿蒙 保活设置",
                steps = listOf(
                    "「设置」→「应用启动管理」→ FocusCat 关闭自动管理 → 全部手动开启",
                    "「设置」→「电池」→「启动应用管理」→ FocusCat 允许后台活动",
                    "「手机管家」→「应用启动管理」确认 FocusCat 已允许自启动、关联启动、后台活动",
                    "近期任务列表里 FocusCat 卡片下拉加锁"
                )
            )

            RomBrand.HONOR -> KeepAliveGuide(
                brandName = "荣耀（MagicOS）",
                title = "MagicOS 保活设置",
                steps = listOf(
                    "「设置」→「应用启动管理」→ FocusCat 关闭自动管理 → 全部手动开启",
                    "「设置」→「电池」→ 确认 FocusCat 允许后台运行",
                    "「系统管家」→「应用启动管理」确认 FocusCat 已允许自启动、关联启动、后台活动",
                    "近期任务列表里 FocusCat 卡片下拉加锁"
                )
            )

            RomBrand.HARMONYOS -> KeepAliveGuide(
                brandName = "鸿蒙 HarmonyOS",
                title = "鸿蒙保活设置",
                steps = listOf(
                    "「设置」→「应用启动管理」→ FocusCat 关闭自动管理 → 全部开启",
                    "「设置」→「电池」→ 确认 FocusCat 允许后台运行",
                    "「手机管家」→ 关闭对 FocusCat 的清理"
                )
            )

            RomBrand.COLOROS -> KeepAliveGuide(
                brandName = "OPPO / 一加 / realme（ColorOS）",
                title = "ColorOS 保活设置",
                steps = listOf(
                    "「设置」→「电池」→「应用耗电管理」→ FocusCat 允许后台运行 + 允许自启动",
                    "「手机管家」→「权限隐私」→「自启动管理」→ FocusCat 打开",
                    "「设置」→「应用管理」→ FocusCat →「耗电管理」→ 允许后台运行",
                    "近期任务列表里 FocusCat 卡片加锁"
                )
            )

            RomBrand.ORIGINOS -> KeepAliveGuide(
                brandName = "vivo / iQOO（OriginOS）",
                title = "OriginOS 保活设置",
                steps = listOf(
                    "「设置」→「电池」→「后台耗电管理」→ FocusCat 允许后台高耗电",
                    "「i 管家」→「应用管理」→「权限管理」→「自启动」→ FocusCat 打开",
                    "「设置」→「应用与权限」→ FocusCat → 允许后台弹出界面",
                    "近期任务列表里 FocusCat 卡片加锁"
                )
            )

            RomBrand.ONEUI -> KeepAliveGuide(
                brandName = "三星（One UI）",
                title = "One UI 保活设置",
                steps = listOf(
                    "「设置」→「电池和设备维护」→「电池」→「后台使用限制」→ FocusCat 从不休眠",
                    "「设置」→「应用」→ FocusCat →「电池」→ 不受限",
                    "「设备维护」→ 关闭自动优化（或排除 FocusCat）"
                )
            )

            RomBrand.FLYME -> KeepAliveGuide(
                brandName = "魅族（Flyme）",
                title = "Flyme 保活设置",
                steps = listOf(
                    "「手机管家」→「权限管理」→「后台管理」→ FocusCat 允许后台运行",
                    "「手机管家」→「省电模式」→ 排除 FocusCat",
                    "「设置」→「应用管理」→ FocusCat → 允许自启动"
                )
            )

            RomBrand.STOCK, RomBrand.UNKNOWN -> KeepAliveGuide(
                brandName = "原生 Android",
                title = "保活设置",
                steps = listOf(
                    "「设置」→「应用」→ FocusCat → 电池 → 不受限",
                    "「设置」→「电池」→ 关闭对 FocusCat 的后台限制",
                    "确认无障碍服务已开启并加入电池优化白名单"
                )
            )
        }
    }

    /**
     * 是否为国产 ROM（需要额外保活引导）
     */
    fun isChineseRom(): Boolean {
        return when (detectBrand()) {
            RomBrand.STOCK, RomBrand.UNKNOWN -> false
            else -> true
        }
    }
}
