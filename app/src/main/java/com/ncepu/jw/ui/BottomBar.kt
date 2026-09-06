package com.ncepu.jw.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial

private data class TabSpec(val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabSpec(Icons.Filled.CalendarMonth, "课表"),
    TabSpec(Icons.Filled.WaterDrop, "饮水"),
    TabSpec(Icons.Filled.HowToReg, "选课"),
    TabSpec(Icons.Filled.Person, "我的"),
)

/**
 * 底部导航栏:形状(标准/悬浮)× 材质(实色/高斯模糊)自由组合。
 * 高斯模糊为半透明底,配合背景图自身的模糊设置形成毛玻璃观感。
 */
@Composable
fun AppBottomBar(
    shape: NavBarShape,
    material: NavMaterial,
    tab: Int,
    onSelect: (Int) -> Unit,
    isDark: Boolean,
) {
    when (shape) {
        NavBarShape.STANDARD -> {
            Surface(color = barColor(material, isDark)) {
                NavRow(
                    tab, onSelect,
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                )
            }
        }
        NavBarShape.FLOATING -> {
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

@Composable
private fun barColor(material: NavMaterial, isDark: Boolean): Color = when (material) {
    NavMaterial.SOLID -> MaterialTheme.colorScheme.surfaceContainer
    NavMaterial.BLUR -> if (isDark) Color(0xFF12181F).copy(alpha = 0.30f)
                        else Color(0xFFFAFAFA).copy(alpha = 0.30f)
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
