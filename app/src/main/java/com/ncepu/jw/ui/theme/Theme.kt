package com.ncepu.jw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.dynamicColorScheme
import com.ncepu.jw.data.ThemeMode

/** 主题预设:固定种子色(动态取色由独立开关控制,不占预设位) */
@Immutable
data class ThemePreset(val key: String, val label: String, val seed: Color)

object ThemePresets {
    /**
     * 华电品牌蓝。登录页渐变这类装饰色用它,不跟随莫奈/预设——
     * 否则动态取色一开,品牌色就被随机色相冲掉了。
     */
    val Brand = Color(0xFF1E4F91)

    val ALL = listOf(
        ThemePreset("NCEPU", "华电蓝", Brand),
        ThemePreset("CYAN", "青碧", Color(0xFF00897B)),
        ThemePreset("PURPLE", "黛紫", Color(0xFF7C4DFF)),
        ThemePreset("GREEN", "松绿", Color(0xFF2E7D32)),
        ThemePreset("PINK", "樱粉", Color(0xFFD81B60)),
        ThemePreset("ORANGE", "暖橙", Color(0xFFF57C00)),
        ThemePreset("INDIGO", "靛蓝", Color(0xFF3949AB)),
    )

    fun byKey(key: String): ThemePreset = ALL.firstOrNull { it.key == key } ?: ALL.first()
}

/**
 * Material You 主题:
 * - 动态取色开关(独立于预设):Android 12+ 从系统壁纸生成配色;关闭或切预设时清掉缓存,
 *   否则旧莫奈配色残留会导致"主题色彩不生效"
 * - 预设:种子色离线生成完整配色(material-kolor)
 * - 深浅色:跟随系统 / 浅色 / 深色,直接换色
 *   (不做逐色动画:40 个 animateColorAsState 会让整棵树每帧重组,深浅色切换必掉帧)
 */
@Composable
fun NcepuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    presetKey: String = "NCEPU",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val preset = ThemePresets.byKey(presetKey)
    val context = LocalContext.current
    val useMonet = dynamicColor && android.os.Build.VERSION.SDK_INT >= 31

    // 整套配色(HCT 约 40 个角色)只在种子/深浅/莫奈开关变化时生成一次,重组不重算;
    // 莫奈同步取系统色而非协程,避免首帧先渲染预设色再跳变
    val baseScheme = remember(useMonet, darkTheme, preset.seed) {
        val monet = if (useMonet) runCatching {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrNull() else null
        monet ?: dynamicColorScheme(seedColor = preset.seed, isDark = darkTheme, isAmoled = false)
    }

    MaterialTheme(
        colorScheme = baseScheme,
        shapes = NcepuShapes,
        content = content,
    )
}

/**
 * 全局圆角。默认 M3 的 Card 是 12dp,和各页手写的 8/12/14dp 混在一起显得碎;
 * 统一抬到 small 8 / medium 16 / large 20 / extraLarge 28(弹层)。
 * 课表格子这类密集小卡仍显式用 8dp,不受 medium 影响。
 */
private val NcepuShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
