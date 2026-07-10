package com.focusguard.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 生成图片食物展示组件
 *
 * 使用 mmx image 生成的 3D 卡通食物 PNG 替代旧的 SVG 矢量拼凑绘制。
 * 接口与旧 FoodCanvas 完全一致（foodId / modifier / size），调用方零改动。
 *
 * 支持食物类型：猫粮 / 冻干 / 酸奶 / 罐头
 */
@Composable
fun FoodCanvas(
    foodId: String,
    modifier: Modifier = Modifier,
    size: Int = 120
) {
    val context = LocalContext.current
    val assetPath = remember(foodId) { "foods/$foodId.png" }

    val bitmapState = produceState<Bitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            loadFoodAssetBitmap(context, assetPath)
        }
    }

    val bitmap = bitmapState.value

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "食物 $foodId",
                modifier = Modifier.fillMaxSize()
            )
        }
        // 边缘柔化过渡：径向渐变遮罩，让食物图片自然融入背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }
}

private fun loadFoodAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        context.assets.open(path).use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        null
    }
}
