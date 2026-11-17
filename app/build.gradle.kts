plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bilipod.app"
    compileSdk = 35
    // 本机 SDK 只装了 build-tools 36.0.0，显式指定可避免首次同步再去下载 35.0.0
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.bilipod.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 学习用途，不做分发，所以不开启混淆，避免自己调试时看不懂堆栈
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ---------- Compose ----------
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---------- 基础组件 ----------
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // ---------- 播放器 Media3 ----------
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // ---------- 网络 ----------
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // ---------- 数据库 ----------
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // ---------- 图片 ----------
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ---------- 二维码（扫码登录展示用，仅生成不识别） ----------
    implementation("com.google.zxing:core:3.5.3")
}
