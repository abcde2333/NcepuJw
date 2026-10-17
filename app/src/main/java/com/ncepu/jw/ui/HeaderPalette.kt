package com.ncepu.jw.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 顶栏(左上角标题 / 右上角图标)在背景图上的颜色。
//
// 开了背景图时由 headerTextColorFrom 用 Palette 从图片顶部区域算出来,让花哨壁纸下标题也读得清;
// null = 没开背景图或算不出来,调用点退回主题色。
internal val LocalHeaderContentColor = staticCompositionLocalOf<Color?> { null }

/**
 * 背景图取色器(见 [HeaderPaletteSampler])。
 *
 * 顶栏只要一个颜色,内容区标题("我的设备" / "我的选课结果")在页面中部、和顶栏不在同一条背景带上,
 * 所以把取色器本身下发下去,让它们按自己所在的高度带各判一次。
 */
internal val LocalHeaderPalette = staticCompositionLocalOf<HeaderPaletteSampler?> { null }

/** 顶栏取样带的下沿(占图高比例):左上角标题和右上角图标都落在这一条里 */
internal const val HEADER_BAND_BOTTOM = 0.16f

/**
 * 黑字 / 白字的分界。
 *
 * WCAG 里纯白文字与纯黑文字的对比度在背景相对亮度 ≈0.18 处相等,低于它就是白字更清楚。
 * Palette 的 Swatch#getBodyTextColor 用的正是同一套对比度判据,
 * 区别只在于它看不到我们叠在图片上方的压暗层,所以这里用同一判据自己再算一次。
 */
private const val DARK_TEXT_LUMINANCE = 0.18f

/** 近黑(不用纯黑:纯黑在深色玻璃上偏死) */
private val HEADER_TEXT_DARK = Color(0xFF16161A)

/** 纯白 */
private val HEADER_TEXT_LIGHT = Color(0xFFFFFFFF)

/** 背景图顶部那层 scrim 的起始不透明度(与 MainActivity 里顶/底渐变保持一致) */
private const val SCRIM_TOP_ALPHA = 0.45f

/** scrim 消失的高度比例(到屏幕 30% 高处就完全透明,同样对齐 MainActivity) */
private const val SCRIM_FADE_FRACTION = 0.3f

/**
 * 取背景图 [topFraction]~[bottomFraction] 这一条交给 Palette 求代表色,
 * 把 App 自己叠在图上的压暗层算进去,返回对比度更高的黑或白。
 *
 * 不把压暗层算进去的话,原图很亮、实际看起来被 dim + 顶部 scrim 压得很暗时会算出黑字,直接糊掉。
 * 用 clearFilters():默认滤镜会剔掉接近纯白/纯黑与低饱和的颜色,灰度壁纸很容易一个 swatch 都拿不到。
 * 会读像素,调用点请放在 Dispatchers.Default。
 */
internal fun headerTextColorFrom(
    bitmap: Bitmap,
    overlayAlpha: Float,
    topFraction: Float = 0f,
    bottomFraction: Float = HEADER_BAND_BOTTOM,
): Color? {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
    val topF = topFraction.coerceIn(0f, 0.98f)
    val bottomF = bottomFraction.coerceIn(topF + 0.01f, 1f)
    val top = (bitmap.height * topF).toInt()
    val bottom = (bitmap.height * bottomF).toInt().coerceIn(top + 1, bitmap.height)
    val palette = Palette.from(bitmap)
        .setRegion(0, top, bitmap.width, bottom)
        .clearFilters()
        .generate()
    val swatch = palette.dominantSwatch
        ?: palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.lightMutedSwatch
        ?: palette.darkMutedSwatch
        ?: return null
    // 代表色是原图的颜色;黑色半透明叠上去等价于把各通道按 (1-alpha) 缩放
    val base = Color(swatch.rgb)
    val keep = 1f - overlayAlpha.coerceIn(0f, 0.95f)
    val shown = Color(base.red * keep, base.green * keep, base.blue * keep)
    return if (shown.luminance() > DARK_TEXT_LUMINANCE) HEADER_TEXT_DARK else HEADER_TEXT_LIGHT
}

/**
 * 某个高度处实际叠在图上的黑色不透明度。
 *
 * dim 是整张图都有的背景压暗层;顶部 scrim 只压最上面一截,到 [SCRIM_FADE_FRACTION] 高处就没有了。
 */
internal fun overlayAlphaAt(heightFraction: Float, dimAlpha: Float): Float {
    val scrim = SCRIM_TOP_ALPHA * (1f - (heightFraction / SCRIM_FADE_FRACTION).coerceIn(0f, 1f))
    return 1f - (1f - dimAlpha.coerceIn(0f, 0.85f)) * (1f - scrim)
}

/**
 * 背景图按高度带判色的取色器。
 *
 * 同一张图,顶部被 scrim 压暗、中部只有 dim,亮度可以差很多,所以每个高度带各判一次并缓存。
 * 所有方法都会读像素(每次约 10~30ms),请在 Dispatchers.Default 上调用。
 */
internal class HeaderPaletteSampler(private val bitmap: Bitmap, private val dimAlpha: Float) {
    private val cache = HashMap<Int, Color?>()

    fun colorFor(topFraction: Float, bottomFraction: Float): Color? {
        val key = (topFraction * 1000f).toInt() * 1000 + (bottomFraction * 1000f).toInt()
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val color = headerTextColorFrom(
            bitmap = bitmap,
            overlayAlpha = overlayAlphaAt((topFraction + bottomFraction) / 2f, dimAlpha),
            topFraction = topFraction,
            bottomFraction = bottomFraction,
        )
        synchronized(cache) { cache[key] = color }
        return color
    }
}

/**
 * 内容区标题的自动颜色:按标题自己所在的高度带判一次。
 *
 * 第一帧返回 null(调用点退回主题色),算完自动重组。没开背景图时直接是 null——
 * 此时背景就是主题色背景,主题色文字本来就是最清楚的。
 */
@Composable
internal fun rememberSectionTextColor(topFraction: Float, bottomFraction: Float): Color? {
    val sampler = LocalHeaderPalette.current ?: return null
    var color by remember(sampler, topFraction, bottomFraction) { mutableStateOf<Color?>(null) }
    LaunchedEffect(sampler, topFraction, bottomFraction) {
        color = withContext(Dispatchers.Default) { sampler.colorFor(topFraction, bottomFraction) }
    }
    return color
}
