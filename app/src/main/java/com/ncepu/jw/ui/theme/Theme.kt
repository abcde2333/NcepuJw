package com.ncepu.jw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamicColorScheme
import com.ncepu.jw.data.ThemeMode

/** 主题预设:固定种子色(动态取色由独立开关控制,不占预设位) */
@Immutable
data class ThemePreset(val key: String, val label: String, val seed: Color)

val NcepuBlue = Color(0xFF1E4F91)

object ThemePresets {
    val ALL = listOf(
        ThemePreset("NCEPU", "华电蓝", Color(0xFF1E4F91)),
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

    // 莫奈取色较昂贵(读壁纸),缓存到状态里;不启用时必须清空,否则预设色永远被旧缓存盖住
    var monetScheme by remember { mutableStateOf<ColorScheme?>(null) }
    LaunchedEffect(useMonet, darkTheme) {
        monetScheme = when {
            useMonet && darkTheme -> dynamicDarkColorScheme(context)
            useMonet -> dynamicLightColorScheme(context)
            else -> null
        }
    }

    val baseScheme = monetScheme
        ?: dynamicColorScheme(seedColor = preset.seed, isDark = darkTheme, isAmoled = false)

    MaterialTheme(
        colorScheme = baseScheme,
        content = content,
    )
}

private val LightColors = lightColorScheme(
    primary = NcepuBlue,
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
)
