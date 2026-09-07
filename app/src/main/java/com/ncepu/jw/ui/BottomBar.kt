package com.ncepu.jw.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.ncepu.jw.ui.glass.DampedDragAnimation
import com.ncepu.jw.ui.glass.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private data class TabSpec(val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabSpec(Icons.Filled.CalendarMonth, "课表"),
    TabSpec(Icons.Filled.WaterDrop, "饮水"),
    TabSpec(Icons.Filled.HowToReg, "选课"),
    TabSpec(Icons.Filled.Person, "我的"),
)

/** 按压时 tab 内容缩放比例(液态玻璃底栏) */
private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 底部导航栏:形状(标准/悬浮)× 材质(实色/液态玻璃/高斯模糊)自由组合。
 * 液态玻璃(仅悬浮):Kyant0/AndroidLiquidGlass,按住当前 tab 出现液态玻璃泡,
 * 可左右拖动快速切换页面,松手落入目标 tab;需要 Android 13+ 完整折射效果。
 */
@Composable
fun AppBottomBar(
    shape: NavBarShape,
    material: NavMaterial,
    tab: Int,
    onSelect: (Int) -> Unit,
    isDark: Boolean,
    backdrop: LayerBackdrop? = null,
) {
    when (shape) {
        NavBarShape.STANDARD -> {
            Surface(color = barColor(material, isDark)) {
                NavRow(tab, onSelect, Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }
        NavBarShape.FLOATING -> {
            if (material == NavMaterial.LIQUID && backdrop != null) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    LiquidTabs(
                        selectedTabIndex = { tab },
                        onTabSelected = onSelect,
                        backdrop = backdrop,
                        tabsCount = TABS.size,
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        shadowElevation = 10.dp,
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
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
                            NavRow(tab, onSelect, Modifier.fillMaxSize())
                        }
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

/**
 * 液态玻璃底部标签栏。移植自 Kyant0/AndroidLiquidGlass catalog 的 LiquidBottomTabs:
 * 玻璃胶囊底板 + 隐形标签折射层 + 跟手的液态玻璃泡(按住出现,拖动切页)。
 */
@Composable
private fun LiquidTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (isDark) Color(0xFF9AC1FF) else MaterialTheme.colorScheme.primary
    val containerColor =
        if (isDark) Color(0xFF12181F).copy(0.4f)
        else Color(0xFFFAFAFA).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // ---- 可见的玻璃胶囊底板(折射壁纸/背景,半透明蒙层) ----
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEachIndexed { i, spec ->
                LiquidTab(i, spec, i == selectedTabIndex(), onTabSelected)
            }
        }

        // ---- 隐形标签层:供玻璃泡折射(青色染色副本) ----
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLiquidTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEachIndexed { i, spec ->
                    LiquidTab(i, spec, i == selectedTabIndex(), onTabSelected, forcedColor = accentColor)
                }
            }
        }

        // ---- 液态玻璃泡:按住出现,跟手拖动,松手落入目标 tab ----
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/** 单个液态玻璃 tab(图标+文字,按住随气泡微放大) */
@Composable
private fun RowScope.LiquidTab(
    index: Int,
    spec: TabSpec,
    selected: Boolean,
    onTabSelected: (Int) -> Unit,
    forcedColor: Color? = null,
) {
    val scale = LocalLiquidTabScale.current
    Column(
        Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
            ) { onTabSelected(index) }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconColor = forcedColor ?: if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
        val textColor = forcedColor ?: if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(spec.icon, contentDescription = spec.label, tint = iconColor)
        Text(spec.label, fontSize = 10.sp, color = textColor)
    }
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
