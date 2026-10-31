plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.focusguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusguard.app"
        minSdk = 26
        targetSdk = 35
        // FocusCat v1.2.1 - 正式发布版本
        // v1.2.0：无障碍保活（ColorOS）、视频随机起点、反思弹窗选择题 + 使用时长豁免
        // v1.2.1：实测修复三个真实缺陷
        //   1) 设置页误报「无障碍服务已被系统关闭」——改为 AccessibilityManager + Settings 双源判定，
        //      并在检测到开关仍在时主动清除标记（自愈）
        //   2) 无障碍已绑定却一直显示「正在恢复中」——断连上报状态改为进程级，
        //      避免系统重绑时新实例取消不掉旧实例挂的延迟任务
        //   3) JobScheduler 重复注册 / AlarmManager 精确闹钟被无限推后 —— 幂等注册 + 链式闹钟模型
        versionCode = 121
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ABI 拆分：仅保留 arm64-v8a（现代 64 位 ARM 设备，覆盖 99%+ 真机）
        // 排除 x86/x86_64（模拟器架构）和 armeabi-v7a（老旧 32 位），显著减小原生库体积
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // 签名配置：优先从环境变量读取（CI/CD 友好），fallback 到 gradle.properties
    // P0-1：避免在项目内 gradle.properties 长期保存明文密码，
    // 推荐通过环境变量或 ~/.gradle/gradle.properties（用户级，不入项目仓库）注入
    signingConfigs {
        create("release") {
            // 优先级：环境变量 > gradle.properties（项目级或用户级）
            val storeFilePath = (System.getenv("FOCUSCAT_STORE_FILE")
                ?: project.findProperty("FOCUSCAT_STORE_FILE") as String?)
            val storePasswordVal = (System.getenv("FOCUSCAT_STORE_PASSWORD")
                ?: project.findProperty("FOCUSCAT_STORE_PASSWORD") as String?)
            val keyAliasVal = (System.getenv("FOCUSCAT_KEY_ALIAS")
                ?: project.findProperty("FOCUSCAT_KEY_ALIAS") as String?)
            val keyPasswordVal = (System.getenv("FOCUSCAT_KEY_PASSWORD")
                ?: project.findProperty("FOCUSCAT_KEY_PASSWORD") as String?)

            if (storeFilePath != null && storePasswordVal != null &&
                keyAliasVal != null && keyPasswordVal != null
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordVal
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal
            }
        }
    }

    buildTypes {
        release {
            // 启用代码混淆和压缩，减小 APK 体积
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 应用签名配置（若已配置）
            // P1-5：未配置签名密钥时输出明确警告，避免用户拿到未签名 APK 后困惑无法安装
            val storeFilePath = project.findProperty("FOCUSCAT_STORE_FILE") as String?
            if (storeFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("FocusCat release: 未配置签名密钥（FOCUSCAT_STORE_FILE 等属性缺失），" +
                    "生成的 APK 将无法在真机安装。请在 ~/.gradle/gradle.properties 或项目根 gradle.properties 配置签名信息。")
            }
        }
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// P1-4：Room schema 导出目录，编译期生成 JSON schema 用于追踪数据库版本变化
ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

dependencies {
    // AndroidX 核心
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room 数据库
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt 依赖注入
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lottie 动画 - 用于复杂庆祝/进食动画
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Media3 ExoPlayer - 用于猫咪进食/活动视频播放
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // 调试工具
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
