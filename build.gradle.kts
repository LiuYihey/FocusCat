// 顶层构建文件
plugins {
    id("com.android.application") version "8.3.2" apply false
    // Kotlin 1.9.25：Compose Compiler 1.5.15 的最低要求版本
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
}
