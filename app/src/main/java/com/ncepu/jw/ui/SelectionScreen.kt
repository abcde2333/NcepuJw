package com.ncepu.jw.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.ncepu.jw.data.SelectedCourse
import com.ncepu.jw.data.XkRound

/**
 * 选课页:上半为选课中心轮次(点"进入"用内嵌网页打开学校选课界面),
 * 下半为当前学期已选结果(原生只读)。
 *
 * 卡片衬底与课表页同一套毛玻璃配方(见 ScheduleScreen 的信息栏面板):
 * 底 = `surface` 半透明 —— 深色主题是烟熏玻璃,浅色主题是白色毛玻璃;
 * 有背景图且系统支持 RenderEffect(API 31+)时再叠壁纸采样 + 去饱和 + 22px 模糊。
 * 文字全部走主题色(onSurface / onSurfaceVariant / outline),两种主题下都读得清。
 */

internal val CardShape = RoundedCornerShape(20.dp)

/** 有背景图时衬底的通透度,与课表页面板一致 */
internal const val TINT_ALPHA_WITH_BG = 0.52f

/** 没背景图时衬底不采样任何东西,压得实一点,免得卡片糊成一片 */
internal const val TINT_ALPHA_PLAIN = 0.86f

@Composable
fun SelectionScreen(
    loading: Boolean,
    error: String?,
    rounds: List<XkRound>,
    courses: List<SelectedCourse>,
    loggedIn: Boolean = true,
    /** 是否有背景图:决定毛玻璃采样与衬底通透度 */
    bgEnabled: Boolean = false,
    glassBackdrop: Backdrop? = null,
    onOpenLogin: () -> Unit = {},
    onEnterRound: (XkRound) -> Unit,
    onRetry: () -> Unit,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // 悬浮底栏压在内容上方时,列表自己把底栏实高留出来(内容能从底栏玻璃下面穿过)
    val barInset = LocalBottomBarInset.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = barInset + 14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("选课中心", LocalHeaderContentColor.current)
                Spacer(Modifier.weight(1f))
                // 与课表页右上角同款:图标在上、文字在下,点整块区域都能触发
                HeaderIconAction(label = "刷新", onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        if (!bgEnabled) {
            // 没开背景图时给「选课中心」补一道分割线(见 HeaderDivider:与结果卡里的分割线等长)
            item { HeaderDivider() }
        }
        if (error != null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点此重试",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRetry() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        items(rounds.size) { i -> RoundCard(rounds[i], bgEnabled, glassBackdrop, onEnterRound) }
        if (!loggedIn && error == null) {
            item { NotLoggedInCard(bgEnabled, glassBackdrop, onOpenLogin) }
        } else if (rounds.isEmpty() && error == null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("当前没有选课轮次", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "若怀疑登录已过期,请到「我的」重新登录教务系统后点“刷新”",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 与顶栏不在同一条背景带上:按标题自己所在的高度带(这里是"两个轮次卡 + 未登录卡"下面)
                // 再判一次色,否则同一张图上顶部判白、这里该判黑的情况会糊掉
                SectionTitle("我的选课结果", rememberSectionTextColor(0.36f, 0.58f))
                Spacer(Modifier.weight(1f))
                Text(
                    "退选请在学校选课页操作",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (courses.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.HowToReg,
                    "暂无选课数据",
                    "选课开放期内选完课后下拉刷新即可看到",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        } else {
            // 整块结果收进一张毛玻璃大卡片,与课表页的衬底同一套配方
            item {
                GlassCard(bgEnabled, glassBackdrop, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    courses.forEachIndexed { i, c ->
                        CourseRow(c)
                        if (i != courses.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 大标题:背景图花哨时纯色文字容易被吃掉,补一道很淡的文字阴影。
 *
 * color 只由页面最外层那个大标题传(开了背景图时是 Palette 算出来的顶栏色,见 HeaderPalette);
 * 卡片里的分节标题不传,继续用主题色。
 */
@Composable
internal fun SectionTitle(
    text: String,
    color: Color? = null,
    /** 层级:页面级标题用 titleLarge(默认),区块标题用 titleMedium */
    style: TextStyle = MaterialTheme.typography.titleLarge,
    weight: FontWeight = FontWeight.Bold,
) {
    Text(
        text,
        style = style.copy(
            shadow = Shadow(
                color = headerHaloColor(color),
                offset = Offset(0f, 1.5f),
                blurRadius = 6f,
            ),
        ),
        fontWeight = weight,
        color = color ?: MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 毛玻璃衬底(修饰符版),配方与课表页信息栏面板一致:
 * `surface` 半透明衬底(深色=烟熏/浅色=白) + 有背景图时叠 vibrancy + 22px 模糊。
 *
 * 做成修饰符是为了让"必须保留原控件语义"的卡片也能用同一套材质
 * (例如饮水页可长按拖拽的设备卡片要保留 Card 的阴影与描边):
 * 卡片自己把 containerColor 设成透明,衬底交给这里画在它下面。
 */
@Composable
internal fun Modifier.glassSurface(
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    shape: Shape = CardShape,
    /** 特殊状态(例如拖到删除条上的警示色)可整块换色,通透度沿用同一套 */
    tintOverride: Color? = null,
    /** 液态玻璃:在毛玻璃之上再叠一层透镜折射,边缘会"吸住"背景(与悬浮底栏同一套配方) */
    liquid: Boolean = false,
): Modifier {
    val tint = tintOverride ?: MaterialTheme.colorScheme.surface.copy(
        alpha = if (bgEnabled) TINT_ALPHA_WITH_BG else TINT_ALPHA_PLAIN,
    )
    val glass = backdrop.takeIf { bgEnabled && it != null && isRenderEffectSupported() }
    val base = clip(shape).background(tint, shape)
    return base.then(
        if (glass != null) {
            Modifier.drawBackdrop(
                backdrop = glass,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(22f.dp.toPx())
                    if (liquid) lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(tint) },
            )
        } else {
            Modifier
        },
    )
}

/** 毛玻璃卡片:就是 [glassSurface] + 一个 Column */
@Composable
internal fun GlassCard(
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    tintOverride: Color? = null,
    liquid: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.glassSurface(
            bgEnabled = bgEnabled,
            backdrop = backdrop,
            shape = shape,
            tintOverride = tintOverride,
            liquid = liquid,
        ),
        content = content,
    )
}

/**
 * 标题下的分割线:颜色与结果卡内部的分割线一致,左右各内缩 12dp
 * ——正好等于内容卡的水平外边距,所以标题下的线与卡片里的线一样长。
 */
@Composable
internal fun HeaderDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        modifier = modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun RoundCard(
    round: XkRound,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    onEnter: (XkRound) -> Unit,
) {
    GlassCard(
        bgEnabled,
        backdrop,
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    round.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (round.status.isNotBlank()) {
                    Text(
                        round.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (round.startText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开始", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(round.startText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (round.endText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("截止", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(round.endText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (round.dailyText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "每日开放  ${round.dailyText}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 已结束轮次里"进入选课"最容易误导:弱化成暗色,真正要点的搬到右下角
                Text(
                    "进入选课 →",
                    color = if (round.ongoing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEnter(round) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!round.ongoing) {
                    // 反色小胶囊:深色主题下是白底黑字,浅色主题下是深底白字
                    PillButton(
                        "查看详情 →",
                        MaterialTheme.colorScheme.inverseSurface,
                        MaterialTheme.colorScheme.inverseOnSurface,
                    ) { onEnter(round) }
                }
            }
        }
    }
}

@Composable
private fun NotLoggedInCard(
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    onOpenLogin: () -> Unit,
) {
    GlassCard(
        bgEnabled,
        backdrop,
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "尚未登录教务系统",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "选课轮次需要登录后查看:请到「我的」→ 登录教务系统(或统一身份认证),完成后回到本页点“刷新”",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                // 不再让用户看着一堆提示字自己翻 Tab 找登录入口
                PillButton(
                    "去登录 →",
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                ) { onOpenLogin() }
            }
        }
    }
}

/** 小胶囊按钮 */
@Composable
private fun PillButton(
    text: String,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CourseRow(c: SelectedCourse) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                c.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(c.code, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // 课堂派加课码单独做成可点的胶囊(点一下复制码),其余信息仍是普通文本
                val tokens = c.group.split("·").map { it.trim() }.filter { it.isNotEmpty() }
                val pillToken = tokens.firstOrNull { it.contains("课堂派") }
                val head = buildList {
                    if (c.teacher.isNotBlank()) add(c.teacher)
                    tokens.filter { it != pillToken }.forEach { add(it) }
                }
                if (head.isNotEmpty()) {
                    TagText(head.joinToString(" · "))
                    TagSep()
                }
                if (pillToken != null) {
                    val copyText = pillToken.substringAfterLast(' ').trim().ifEmpty { pillToken }
                    CopyPill(pillToken) {
                        clipboard.setText(AnnotatedString(copyText))
                        Toast.makeText(ctx, "已复制加课码 $copyText", Toast.LENGTH_SHORT).show()
                    }
                    TagSep()
                }
                TagText("学分${c.credit}")
                if (c.attr.isNotBlank()) {
                    TagSep()
                    TagText(c.attr)
                }
            }
            Text("课序号 ${c.seq}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TagText(text: String) {
    Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun TagSep() {
    Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1)
}

/** 带复制图标的小胶囊 */
@Composable
private fun CopyPill(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = "复制加课码",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
    }
}
