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
        versionCode = 45
        versionName = "4.14"
    }

    buildTypes {
        release {
            // R8 混淆+压缩:material-icons-extended 全量图标/ML Kit/jxl 里未用到的类
            // 不再进 dex,冷启动类加载和安装体积显著下降
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 个人项目:release 直接用 debug 签名,产物可直接覆盖安装
            signingConfig = signingConfigs.getByName("debug")
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
    // Kyant0/AndroidLiquidGlass(Compose 多平台 backdrop):液态玻璃悬浮底栏
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
}
