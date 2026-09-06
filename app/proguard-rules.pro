# ===== 项目 keep 规则(R8 release 压缩) =====

# JXL(xls 解析):内部有反射与 native 资源加载,整包保留(库本身很小)
-keep class jxl.** { *; }
-dontwarn jxl.**

# OkHttp / Okio / Conscrypt 等可选平台适配缺省告警
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ML Kit 条码识别(自带 consumer 规则,此处兜底防裁剪)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# 强智登录加密等被 JNI/反射调用的入口(目前无反射,预防性)
-keep class com.ncepu.jw.data.QiangzhiCrypto { *; }

# Compose/kotlinx 默认已带 consumer 规则,无需额外配置
