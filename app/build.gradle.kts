plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ncepu.jw"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.ncepu.jw"
        minSdk = 26
        targetSdk = 35
        versionCode = 46
        versionName = "4.15"
    }

    signingConfigs {
        create("release") {
            // 固定签名身份:本地/CI 共用同一 keystore(CI 从 Secrets 还原),
            // 保证应用内更新覆盖安装不因签名不一致失败;缺失时回退 debug 签名
            val ksFile = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
            }
        }
    }
    buildTypes {
        release {
            // R8 混淆+压缩:material-icons-extended 全量图标/ML Kit/jxl 里未用到的类
            // 不再进 dex,冷启动类加载和安装体积显著下降
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (signingConfigs.getByName("release").storeFile != null) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.materialkolor:material-kolor:2.0.0")
    // BouncyCastle:统一身份认证登录密码 SM2(C1C3C2) 加密
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // 支付宝 SDK:U净 orderInfo 为 PayTask.payV2 签名订单串
    implementation("com.alipay.sdk:alipaysdk-android:15.8.42")
    // Kyant0/AndroidLiquidGlass(Compose 多平台 backdrop):液态玻璃悬浮底栏
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
}
