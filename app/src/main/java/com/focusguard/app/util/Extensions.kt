package com.focusguard.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 应用信息数据类
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

/**
 * dp 转 px
 */
fun Context.dpToPx(dp: Int): Int {
    val density = resources.displayMetrics.density
    return (dp * density + 0.5f).toInt()
}

/**
 * 通过 PackageManager 获取指定应用图标
 */
fun Context.getAppIcon(packageName: String): Drawable? {
    return try {
        val pm = packageManager
        pm.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/**
 * 获取指定应用名称
 */
fun Context.getAppName(packageName: String): String {
    return try {
        val pm = packageManager
        val applicationInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(applicationInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

/**
 * 获取已安装的第三方应用列表（排除系统应用和本应用）
 *
 * P1-7：改为 suspend 函数并切换到 IO 调度器，API 层面强制线程安全，
 * 避免未来在 Main 线程误调用导致 ANR。PackageManager 查询涉及 IPC + 反序列化，
 * 单次调用在应用数量多的设备上可达 100-300ms。
 */
suspend fun Context.getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val result = mutableListOf<AppInfo>()
    for (appInfo in packages) {
        // 排除系统应用
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        // 排除本应用
        val isSelf = appInfo.packageName == packageName
        if (isSystemApp || isSelf) continue

        val appName = pm.getApplicationLabel(appInfo).toString()
        val icon = try {
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            null
        }
        result.add(AppInfo(appInfo.packageName, appName, icon))
    }
    // 按应用名排序
    result.sortedBy { it.appName }
}

/**
 * 将毫秒格式化为中文时长
 * - 超过1小时：显示 "X小时Y分"
 * - 不足1小时：显示 "Y分Z秒"
 * - 为0：显示 "暂无"
 */
fun Long.formatDuration(): String {
    if (this <= 0L) return "暂无"

    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) - hours * 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) - hours * 3600 - minutes * 60

    return if (hours > 0) {
        "${hours}小时${minutes}分"
    } else if (minutes > 0) {
        "${minutes}分${seconds}秒"
    } else {
        "${seconds}秒"
    }
}

/**
 * 格式化为 "MM-dd" 格式日期
 */
fun Long.formatDate(): String {
    val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    return dateFormat.format(Date(this))
}

/**
 * 将 Drawable 转换为 ImageBitmap（用于 Compose Image 显示）
 */
fun Drawable.toImageBitmap(): ImageBitmap {
    return toBitmap().asImageBitmap()
}

/**
 * 将 PNG 字节数组转换为 ImageBitmap（用于从数据库恢复应用图标）
 * @return ImageBitmap，若解码失败则返回 null
 */
fun ByteArray?.toImageBitmap(): ImageBitmap? {
    if (this == null || isEmpty()) return null
    return try {
        android.graphics.BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
