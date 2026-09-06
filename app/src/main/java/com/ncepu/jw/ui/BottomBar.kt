package com.ncepu.jw.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.qmdeve.liquidglass.Config
import com.qmdeve.liquidglass.LiquidGlass

private data class TabSpec(val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabSpec(Icons.Filled.CalendarMonth, "课表"),
    TabSpec(Icons.Filled.WaterDrop, "饮水"),
    TabSpec(Icons.Filled.HowToReg, "选课"),
    TabSpec(Icons.Filled.Person, "我的"),
)

/**
 * 底部导航栏:形状(标准/悬浮)× 材质(实色/液态玻璃/高斯模糊)自由组合。
 * 液态玻璃与高斯模糊使用 QmDeve AndroidLiquidGlassView(RenderNode + AGSL 真折射/色散),
 * 需要 Android 13+,低版本回退半透明玻璃质感。
 * 按住底栏左右滑动可快速切换页面。
 */
@Composable
fun AppBottomBar(
    shape: NavBarShape,
    material: NavMaterial,
    tab: Int,
    onSelect: (Int) -> Unit,
    glassTarget: ViewGroup?,
    isDark: Boolean,
    bgBlurDp: Float,
) {
    val density = LocalDensity.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 64.dp.toPx() }

    val swipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { dragAccum = 0f },
            onDragEnd = { dragAccum = 0f },
            onDragCancel = { dragAccum = 0f },
        ) { change, dragAmount ->
            change.consume()
            dragAccum += dragAmount
            while (dragAccum >= thresholdPx) {
                if (tab < TABS.size - 1) onSelect(tab + 1)
                dragAccum -= thresholdPx
            }
            while (dragAccum <= -thresholdPx) {
                if (tab > 0) onSelect(tab - 1)
                dragAccum += thresholdPx
            }
        }
    }

    when (shape) {
        NavBarShape.STANDARD -> {
            Surface(color = barColor(material, isDark)) {
                NavRow(
                    tab, onSelect,
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .then(swipeModifier),
                )
            }
        }
        NavBarShape.FLOATING -> {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 10.dp,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(swipeModifier),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                barColor(material, isDark),
                                RoundedCornerShape(26.dp),
                            )
                            .border(
                                0.8.dp,
                                if (isDark) Color.White.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.55f),
                                RoundedCornerShape(26.dp),
                            ),
                    ) {
                        LiquidGlassLayer(material, glassTarget, isDark, bgBlurDp, density)
                        NavRow(tab, onSelect, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun barColor(material: NavMaterial, isDark: Boolean): Color = when (material) {
    NavMaterial.SOLID -> MaterialTheme.colorScheme.surfaceContainer
    NavMaterial.LIQUID -> if (isDark) Color(0xFF12181F).copy(alpha = 0.40f)
                          else Color(0xFFFAFAFA).copy(alpha = 0.40f)
    NavMaterial.BLUR -> if (isDark) Color(0xFF12181F).copy(alpha = 0.30f)
                        else Color(0xFFFAFAFA).copy(alpha = 0.30f)
}

/** 液态玻璃/高斯模糊材质层(Android 13+ 真折射色散;否则透明回退) */
@Composable
private fun LiquidGlassLayer(
    material: NavMaterial,
    glassTarget: ViewGroup?,
    isDark: Boolean,
    bgBlurDp: Float,
    density: androidx.compose.ui.unit.Density,
) {
    val canRender = glassTarget != null && Build.VERSION.SDK_INT >= 33
    if (!canRender) return
    AndroidView(
        factory = { ctx ->
            val config = Config().apply {
                CORNER_RADIUS_PX = with(density) { 26.dp.toPx() }
                BLUR_RADIUS = with(density) {
                    (if (material == NavMaterial.LIQUID) 10.dp else (bgBlurDp.coerceAtLeast(12f) + 6f).dp).toPx()
                }
                if (material == NavMaterial.LIQUID) {
                    REFRACTION_HEIGHT = with(density) { 14.dp.toPx() }
                    REFRACTION_OFFSET = with(density) { 4.dp.toPx() }
                } else {
                    REFRACTION_HEIGHT = 0f
                    REFRACTION_OFFSET = 0f
                }
                TINT_ALPHA = if (isDark) 0.22f else 0.10f
                TINT_COLOR_RED = if (isDark) 0.08f else 1f
                TINT_COLOR_GREEN = if (isDark) 0.09f else 1f
                TINT_COLOR_BLUE = if (isDark) 0.12f else 1f
            }
            LiquidGlass(ctx, config).apply { init(glassTarget!!) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun NavRow(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { i, spec ->
            NavItem(spec, tab == i, Modifier.weight(1f)) { onSelect(i) }
        }
    }
}

@Composable
private fun NavItem(
    spec: TabSpec,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            spec.icon,
            contentDescription = spec.label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            spec.label,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 给背景 ImageView 应用高斯模糊(API 31+) */
fun applyBackgroundBlur(imageView: ImageView, radiusDp: Float, density: Float) {
    if (Build.VERSION.SDK_INT >= 31) {
        val r = radiusDp * density
        imageView.setRenderEffect(
            if (r > 0.5f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null
        )
    } else {
        imageView.setRenderEffect(null)
    }
}
