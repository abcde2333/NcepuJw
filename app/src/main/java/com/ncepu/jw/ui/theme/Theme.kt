package com.ncepu.jw.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

/** 主题预设:莫奈动态取色或固定种子色 */
@Immutable
data class ThemePreset(val key: String, val label: String, val seed: Color, val dynamic: Boolean = false)

val NcepuBlue = Color(0xFF1E4F91)

object ThemePresets {
    val ALL = listOf(
        ThemePreset("DYNAMIC", "莫奈取色", Color(0xFF1E4F91), dynamic = true),
        ThemePreset("NCEPU", "华电蓝", Color(0xFF1E4F91)),
        ThemePreset("CYAN", "青碧", Color(0xFF00897B)),
        ThemePreset("PURPLE", "黛紫", Color(0xFF7C4DFF)),
        ThemePreset("GREEN", "松绿", Color(0xFF2E7D32)),
        ThemePreset("PINK", "樱粉", Color(0xFFD81B60)),
        ThemePreset("ORANGE", "暖橙", Color(0xFFF57C00)),
        ThemePreset("INDIGO", "靛蓝", Color(0xFF3949AB)),
    )

    fun byKey(key: String): ThemePreset = ALL.firstOrNull { it.key == key } ?: ALL[1]
}

/**
 * Material You 主题:
 * - 莫奈预设(Android 12+):从系统壁纸生成配色(结果缓存,避免切换动画期间反复取壁纸色卡顿)
 * - 其余预设:种子色离线生成完整配色(material-kolor)
 * - 深浅色:跟随系统 / 浅色 / 深色;切换时颜色平滑过渡
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

    // 莫奈取色较昂贵(读壁纸),缓存到状态里,避免主题过渡动画期间每帧重新生成
    var monetScheme by remember(darkTheme) { mutableStateOf<ColorScheme?>(null) }
    if (preset.dynamic && dynamicColor && android.os.Build.VERSION.SDK_INT >= 31) {
        LaunchedEffect(darkTheme) {
            monetScheme = if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
    }

    val baseScheme = monetScheme
        ?: dynamicColorScheme(seedColor = preset.seed, isDark = darkTheme, isAmoled = false)

    MaterialTheme(
        colorScheme = baseScheme.animated(),
        content = content,
    )
}

/** 深浅色/主题切换时所有颜色平滑过渡(时长控制以兼顾流畅度) */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable fun c(color: Color) = animateColorAsState(
        color, tween(280), label = "theme",
    ).value
    return ColorScheme(
        primary = c(primary),
        onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer),
        onPrimaryContainer = c(onPrimaryContainer),
        inversePrimary = c(inversePrimary),
        secondary = c(secondary),
        onSecondary = c(onSecondary),
        secondaryContainer = c(secondaryContainer),
        onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary),
        onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer),
        onTertiaryContainer = c(onTertiaryContainer),
        background = c(background),
        onBackground = c(onBackground),
        surface = c(surface),
        onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant),
        onSurfaceVariant = c(onSurfaceVariant),
        surfaceTint = c(surfaceTint),
        inverseSurface = c(inverseSurface),
        inverseOnSurface = c(inverseOnSurface),
        error = c(error),
        onError = c(onError),
        errorContainer = c(errorContainer),
        onErrorContainer = c(onErrorContainer),
        outline = c(outline),
        outlineVariant = c(outlineVariant),
        scrim = c(scrim),
        surfaceBright = c(surfaceBright),
        surfaceDim = c(surfaceDim),
        surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh),
        surfaceContainerHighest = c(surfaceContainerHighest),
        surfaceContainerLow = c(surfaceContainerLow),
        surfaceContainerLowest = c(surfaceContainerLowest),
    )
}

private val LightColors = lightColorScheme(
    primary = NcepuBlue,
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
)
