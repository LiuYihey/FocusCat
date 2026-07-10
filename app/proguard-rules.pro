# FocusCat ProGuard 规则

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class androidx.room.* { *; }

# ===== Hilt / Dagger =====
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# ===== Kotlin Coroutines =====
-keep class kotlinx.coroutines.** { *; }

# ===== Lottie =====
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ===== Compose =====
-keep class androidx.compose.** { *; }

# ===== Media3 / ExoPlayer（视频播放，修复 P0-4 release 崩溃） =====
# ExoPlayer/Media3 内部大量使用反射实例化 Renderer，必须保留
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ===== 应用实体类和 DAO（Room 反射需要） =====
-keep class com.focusguard.app.data.local.entity.** { *; }
-keep class com.focusguard.app.data.local.dao.** { *; }

# ===== 保留反射调用的类（Hilt 生成的基类需要） =====
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
