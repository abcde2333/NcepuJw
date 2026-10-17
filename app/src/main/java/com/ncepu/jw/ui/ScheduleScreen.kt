package com.ncepu.jw.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.Semester
import com.ncepu.jw.data.parseWeeks
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CourseColors = listOf(
    Color(0xFFE05565), Color(0xFF4A90D9), Color(0xFFE0913C), Color(0xFF67B279),
    Color(0xFF9A6FD0), Color(0xFF3CA6A6), Color(0xFFD9784A), Color(0xFF5B8DEF),
)

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// 预编译正则/格式器:滑动翻页时每页都会用,现场编译是逐帧开销
private val TIME_RE = Regex("""(\d{1,2}):(\d{2})""")
private val HH_MM = SimpleDateFormat("HH:mm", Locale.US)
private val HEADER_DATE_FMT = SimpleDateFormat("yyyy/M/d", Locale.US)
private val WEEK_DATE_FMT = SimpleDateFormat("M.d", Locale.US)

/** 悬浮在背景图上的小图标/小字统一用这道阴影(与选课页大标题同一配方) */
private val HEADER_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1.5f),
    blurRadius = 6f,
)

/** 阴影副本的颜色:比标题阴影再深一点,因为图标是细线条 */
private val HEADER_SHADOW_COLOR = Color.Black.copy(alpha = 0.5f)

/**
 * 顶栏文字的描边光晕:浅色文字配深色光晕,深色文字配浅色光晕。
 *
 * 开了背景图时文字颜色由 Palette 定(见 HeaderPalette),亮壁纸下会变成近黑色,
 * 这时再用原来的深色阴影就成了"黑字描黑边",糊成一团;所以跟着文字颜色换。
 * textColor = null(没开背景图)时保持原来的深色阴影。
 */
internal fun headerHaloColor(textColor: Color?): Color = when {
    textColor == null -> HEADER_SHADOW_COLOR
    textColor.luminance() > 0.5f -> HEADER_SHADOW_COLOR
    else -> Color.White.copy(alpha = 0.55f)
}

/** 六大节开始时间 → 12 个小节的 (节号, 开始, 结束)
 *  华电作息:每节 45 分钟,大节内两节间课间 10 分钟 */
internal fun sectionSlots(times: List<String>): List<Triple<Int, String, String>> {
    val out = mutableListOf<Triple<Int, String, String>>()
    val cal = Calendar.getInstance()
    for ((row, s) in times.withIndex()) {
        val m = TIME_RE.find(s) ?: continue
        cal.set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
        cal.set(Calendar.MINUTE, m.groupValues[2].toInt())
        cal.set(Calendar.SECOND, 0)
        val base = cal.timeInMillis
        out += Triple(row * 2 + 1, HH_MM.format(java.util.Date(base)), HH_MM.format(java.util.Date(base + 45 * 60000)))
        out += Triple(row * 2 + 2, HH_MM.format(java.util.Date(base + 55 * 60000)), HH_MM.format(java.util.Date(base + 100 * 60000)))
    }
    return out
}

private data class PlacedCourse(val course: Course, val day: Int, val colIdx: Int, val colCount: Int)

/** 一天内课程放置:节次重叠的课聚类后水平分栏(区间图着色) */
private fun placeCourses(courses: List<Course>): List<PlacedCourse> {
    val out = mutableListOf<PlacedCourse>()
    for ((day, list) in courses.groupBy { it.day }) {
        val sorted = list.sortedBy { it.sections.first }
        var i = 0
        while (i < sorted.size) {
            var clusterEnd = sorted[i].sections.last
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1].sections.first <= clusterEnd) {
                j++
                clusterEnd = maxOf(clusterEnd, sorted[j].sections.last)
            }
            val colEnds = mutableListOf<Int>()
            val colOf = mutableMapOf<Int, Int>()
            for (k in i..j) {
                val c = sorted[k]
                var ci = colEnds.indexOfFirst { it < c.sections.first }
                if (ci < 0) {
                    colEnds.add(c.sections.last)
                    ci = colEnds.size - 1
                } else {
                    colEnds[ci] = c.sections.last
                }
                colOf[k] = ci
            }
            val cols = colEnds.size
            for (k in i..j) {
                out += PlacedCourse(sorted[k], day, colOf[k] ?: 0, cols)
            }
            i = j + 1
        }
    }
    return out
}

/**
 * 课表页:周视图(数据来自 xskb_list.do 全量解析,本地按周过滤),HorizontalPager 无缝左右滑。
 * 只保留周视图 —— 「查看全部课表(学期全量)」已移除;表头/时间轴字号统一走 AppTypography。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    loading: Boolean,
    error: String?,
    allCourses: List<Course>,
    officialWeek: Int,
    weekStartMillis: Long = 0L,
    sectionTimes: List<String>,
    bgEnabled: Boolean,
    /** 背景图的隐形折射源(与液态玻璃底栏同源);为空时衬底退回半透明纯色 */
    glassBackdrop: Backdrop? = null,
    onRetry: () -> Unit,
    /** 扫码入口:扫洗衣机/饮水机机身码后自动分页(见 QrScanRouter) */
    onScan: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    diag: String = "",
    onOpenExams: (() -> Unit)? = null,
) {
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    val todayText = remember { HEADER_DATE_FMT.format(Calendar.getInstance().time) }
    val scope = rememberCoroutineScope()

    // 周 x 25 页;初始页 = 当前周
    val initialPage = ((officialWeek.takeIf { it > 0 } ?: 1) - 1).coerceIn(0, 24)
    val pagerState = rememberPagerState(initialPage = initialPage) { 25 }
    // 官方周异步到达后跳到当前周(仅初始,且用户未滑动时)
    androidx.compose.runtime.LaunchedEffect(officialWeek) {
        if (officialWeek in 2..25 && pagerState.currentPage == 0 && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(officialWeek - 1)
        }
    }
    val page = pagerState.currentPage + 1

    // 一次性把全量课表按周分桶(含无周次信息的课全周显示),
    // 翻页时直接查表,不再逐帧"编译正则+解析字符串"
    val coursesByWeek = remember(allCourses) {
        val m = mutableMapOf<Int, MutableList<Course>>()
        fun bucket(w: Int): MutableList<Course> = m.getOrPut(w) { mutableListOf() }
        for (c in allCourses) {
            val weeks = parseWeeks(c.weeks)
            if (weeks == null) for (w in 1..25) bucket(w).add(c)
            else weeks.forEach { w -> if (w in 1..25) bucket(w).add(c) }
        }
        m
    }

    Column(Modifier.fillMaxSize()) {
        // 日期行:留在面板之外,不铺衬底;右侧是图标在上、文字在下的两个动作(扫码在刷新左边)
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                todayText,
                // 开了背景图时由 Palette 定(见 HeaderPalette),否则用主题色
                color = LocalHeaderContentColor.current ?: MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onScan != null) {
                HeaderIconAction(label = "扫码", onClick = onScan) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (onScan != null && onRefresh != null) Spacer(Modifier.width(10.dp))
            if (onRefresh != null) {
                HeaderIconAction(
                    label = "刷新",
                    onClick = onRefresh,
                    enabled = !loading,
                    shadow = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        // 背景图模式下:信息栏与网格共用一块衬底。拿到折射源且系统支持 RenderEffect(API 31+)
        // 时做成真毛玻璃——壁纸采样后去饱和+模糊再压一层 surface 色调;不支持时退回半透明纯色,
        // 否则低版本上会垫一张没糊的壁纸,课卡文字直接读不出来。
        val panelShape = RoundedCornerShape(16.dp)
        val panelTint = if (bgEnabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
        else Color.Transparent
        val glass = glassBackdrop.takeIf { bgEnabled && it != null && isRenderEffectSupported() }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 6.dp)
                .background(panelTint, panelShape)
                .then(
                    if (glass != null) Modifier.drawBackdrop(
                        backdrop = glass,
                        shape = { panelShape },
                        effects = {
                            vibrancy()
                            blur(22f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(panelTint) },
                    ) else Modifier
                )
                .padding(bottom = 8.dp),
        ) {
            // ---- 信息栏:翻页 / 全部周次 + 右侧工具组 ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    Text(
                        "第 $page 周",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(24))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                    }
                    if (page != officialWeek) {
                        Text(
                            "(本周第${officialWeek}周)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(officialWeek - 1) }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
                if (onOpenExams != null) {
                    Text(
                        "考试安排 →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenExams() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            // ---- 内容区 ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loading && allCourses.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                allCourses.isNotEmpty() -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    val week = pageIndex + 1
                    val weekCourses = coursesByWeek[week].orEmpty()
                    if (weekCourses.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("第 $week 周暂无课程", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        val weekDates = remember(week, weekStartMillis) { weekDatesFor(week, weekStartMillis) }
                        CourseGrid(
                            courses = weekCourses,
                            sectionTimes = sectionTimes,
                            onBackground = bgEnabled,
                            weekDates = weekDates,
                            onCourseClick = { selectedCourse = it },
                        )
                    }
                }
                loading -> ScheduleSkeleton()
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            error, color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                        )
                        Text(
                            "点此重试", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onRetry() },
                        )
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本学期暂无课程", color = MaterialTheme.colorScheme.outline)
                        if (diag.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                diag,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                }
            }
        }

        // ---- 课程详情 ----
        selectedCourse?.let { c ->
            ModalBottomSheet(onDismissRequest = { selectedCourse = null }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        "老师" to c.teacher,
                        "地点" to c.room,
                        "周次" to c.weeks,
                        "学分" to c.credit,
                        "节次" to "第${c.sections.first}-${c.sections.last}节",
                        "属性" to c.attr,
                        "分组" to c.group,
                    ).forEach { (k, v) ->
                        if (v.isNotBlank()) Text(
                            "$k:$v",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 第 week 周的周一~周日日期:锚定设置里的"第一周周一"(weekStartMillis) */
private fun weekDatesFor(week: Int, weekStartMillis: Long): List<String> {
    val cal = Calendar.getInstance()
    if (weekStartMillis > 0) {
        cal.timeInMillis = weekStartMillis
    } else {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_MONTH, -((dow + 5) % 7)) // 未设置:回退本周一(旧逻辑)
    }
    cal.add(Calendar.DAY_OF_MONTH, (week - 1) * 7)
    return List(7) { i ->
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_MONTH, i)
        WEEK_DATE_FMT.format(c.time)
    }
}

/**
 * 日期行右侧的图标动作:图标在上、文字在下、无底色无描边(照参考图)。
 * 点击区带 8dp/4dp 内边距 + 圆角水波纹,保证手感;enabled=false 时(如刷新中)不响应点击。
 */
@Composable
internal fun HeaderIconAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** 图标阴影:加载中的圆圈指示器自带颜色,给它加阴影会变成一圈彩色光晕,所以那种情况传 false */
    shadow: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            // 图标阴影:同一路径用深色再画一遍,下移 1.5dp 并虚化,压在花哨背景上也不会糊掉。
            // 用 LocalContentColor 改色,所以调用点传进来的 Icon(默认取当前内容色)自动跟着变深。
            // 系统 API 31 以下 Modifier.blur 是空操作,退化成"硬边深色副本",仍然有描深效果。
            if (shadow) {
                CompositionLocalProvider(
                    LocalContentColor provides headerHaloColor(LocalHeaderContentColor.current),
                ) {
                    Box(
                        Modifier
                            .graphicsLayer { translationY = 1.5.dp.toPx() }
                            .blur(4.dp),
                    ) { icon() }
                }
            }
            // 开了背景图时图标跟着顶栏色走(Palette 算出来的黑或白),否则保持当前内容色
            val headerColor = LocalHeaderContentColor.current
            if (headerColor != null) {
                CompositionLocalProvider(LocalContentColor provides headerColor) { icon() }
            } else {
                icon()
            }
        }
        Text(
            label,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            color = LocalHeaderContentColor.current?.copy(alpha = 0.92f)
                ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            // 小字用同一道阴影(比大标题紧一点,11sp 用 6px 模糊会发虚)
            style = LocalTextStyle.current.copy(
                shadow = HEADER_SHADOW.copy(
                    color = headerHaloColor(LocalHeaderContentColor.current),
                    blurRadius = 4f,
                ),
            ),
        )
    }
}

/** 通用学期标题栏(成绩/考试页使用) */
@Composable
fun SemesterBar(
    semesters: List<Semester>,
    selected: Semester,
    onChange: (Semester) -> Unit,
    title: String,   // 无默认值:成绩/考试页都靠它区分,给默认"课表"会让漏传的页面静默显示错标题
    trailing: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Box {
            Text(
                "  ${selected.displayName} ▾",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                semesters.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.displayName) },
                        onClick = { expanded = false; onChange(s) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** "HH:mm" → 当日分钟数;解析不出来返回 null */
private fun minutesOf(hhmm: String): Int? {
    val m = TIME_RE.find(hhmm) ?: return null
    return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
}

/**
 * 这节课此刻是否正在上。
 * 三个条件都要满足:眼前这一列确实是今天(weekDates[day] == 今天)、时间落在它第一节到
 * 最后一节之间、课表已知道节次时间。跨大节的中场休息(同一节课内部)也算在内,避免中途闪一下。
 */
private fun isOngoingNow(
    course: Course,
    dayIdx: Int,
    slots: List<Triple<Int, String, String>>,
    weekDates: List<String>,
    todayDate: String,
    nowMinutes: Int,
): Boolean {
    if (weekDates.getOrNull(dayIdx - 1) != todayDate) return false
    val first = slots.firstOrNull { it.first == course.sections.first } ?: return false
    val last = slots.firstOrNull { it.first == course.sections.last } ?: return false
    val start = minutesOf(first.second) ?: return false
    val end = minutesOf(last.third) ?: return false
    return nowMinutes in start until end
}

// ---- 课表网格几何:真网格与骨架屏必须共用同一份,否则加载完成换成真网格时会跳 ----
/** 单节行高 */
private val GRID_ROW_H = 46.dp
/** 左侧时间列宽 */
private val GRID_TIME_COL_W = 44.dp
/** 表头最小高度(真表头两行小字的高度下限) */
private val GRID_HEADER_MIN_H = 36.dp

/** 每天一列的列宽:让出时间列后七等分,左右各留 5dp */
private fun gridColW(screenW: Float): Dp =
    max(((screenW - GRID_TIME_COL_W.value - 10f) / 7f), 40f).dp

/** 竖排网格:固定表头 + 自定义 Layout 平铺课程块(冲突课自动分栏),点击弹详情 */
@Composable
private fun CourseGrid(
    courses: List<Course>,
    sectionTimes: List<String>,
    onBackground: Boolean,
    weekDates: List<String> = emptyList(),
    onCourseClick: (Course) -> Unit,
) {
    val slots = remember(sectionTimes) { sectionSlots(sectionTimes) }
    val rowH = GRID_ROW_H
    val timeColW = GRID_TIME_COL_W
    val todayIdx = remember {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        when (dow) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
    }
    val placed = remember(courses) { placeCourses(courses) }

    // 正在上的课要描一圈主题色边框:每 20s 走一次时钟(跨节次边界最多晚 20s 出现/消失)。
    // 用 weekDates 里的日期判"眼前这一列就是今天",翻到别的周不会误高亮。
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val nowCal = remember(nowMillis) {
        Calendar.getInstance().apply { timeInMillis = nowMillis }
    }
    val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val todayDate = remember(nowMillis) { WEEK_DATE_FMT.format(nowCal.time) }
    val rowHpx = with(LocalDensity.current) { rowH.toPx() }

    // BoxWithConstraints:不订阅 LocalConfiguration(避免配置变化引发整格重组),直接用分配到的宽度
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth.value
        val colW = gridColW(screenW)
        val colWpx = with(LocalDensity.current) { colW.toPx() }

        // 衬底已上移到课表页的面板列(信息栏 + 网格共用),这里只管铺格子
        Column(Modifier.fillMaxSize()) {
        // 表头
        Row(Modifier.padding(start = timeColW)) {
            DAY_LABELS.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(colW).padding(1.dp).heightIn(min = GRID_HEADER_MIN_H)
                        .background(
                            if (i + 1 == todayIdx) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label, style = MaterialTheme.typography.titleMedium,
                            color = if (i + 1 == todayIdx) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (weekDates.getOrNull(i)?.isNotBlank() == true) {
                            Text(
                                weekDates[i], style = MaterialTheme.typography.labelSmall,
                                color = if (i + 1 == todayIdx) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }

        // 主体:时间轴 + 自定义 Layout 课程网格(垂直滚动)
        Row(
            Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
        ) {
            Column(Modifier.width(timeColW)) {
                slots.forEach { (no, start, end) ->
                    Box(Modifier.height(rowH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "$no\n$start\n$end",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Layout(
                content = {
                    placed.forEach { p ->
                        Box(
                            Modifier
                                .padding(1.dp)
                                .clickable { onCourseClick(p.course) },
                        ) {
                            val colorIdx = p.course.name.hashCode().mod(CourseColors.size)
                            val fg = CourseColors[colorIdx]
                            val ongoing = isOngoingNow(
                                p.course, p.day, slots, weekDates, todayDate, nowMinutes,
                            )
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = fg.copy(alpha = if (onBackground) 0.92f else 0.95f),
                                ),
                                // 正在上的课:一圈半透明主题色描边。底色本身就是饱和课程色,
                                // 所以用淡化后的 primary(而不是实心)才不会糊住课程颜色
                                border = if (ongoing) {
                                    BorderStroke(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    )
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(Modifier.padding(4.dp)) {
                                    Text(
                                        p.course.name,
                                        fontSize = 10.sp, lineHeight = 12.sp,
                                        color = Color.White, fontWeight = FontWeight.Bold,
                                        maxLines = 4,
                                    )
                                    if (p.course.room.isNotBlank()) Text(
                                        "@${p.course.room}",
                                        fontSize = 9.sp, lineHeight = 10.sp,
                                        color = Color.White.copy(alpha = 0.95f),
                                        maxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val placeables = measurables.mapIndexed { index, m ->
                    val p = placed[index]
                    val w = (colWpx / p.colCount).roundToInt() - 2
                    val h = ((p.course.sections.last - p.course.sections.first + 1) * rowHpx).roundToInt() - 2
                    val x = ((p.day - 1) * colWpx).roundToInt() + 1
                    val y = ((p.course.sections.first - 1) * rowHpx).roundToInt() + 1
                    Triple(
                        m.measure(
                            Constraints(
                                minWidth = w.coerceAtLeast(1), maxWidth = w.coerceAtLeast(1),
                                minHeight = h.coerceAtLeast(1), maxHeight = h.coerceAtLeast(1),
                            )
                        ),
                        x, y,
                    )
                }
                layout((colWpx * 7).roundToInt(), (rowHpx * slots.size).roundToInt()) {
                    placeables.forEach { (pl, x, y) -> pl.place(x, y) }
                }
            }
        }
        }
    }
}

/** 骨架占位:每列一组 (起始节次, 跨节数),共 10 节 */
private val SkeletonBlocks = listOf(
    listOf(0 to 2, 3 to 2, 6 to 1),
    listOf(1 to 3, 5 to 2),
    listOf(0 to 1, 2 to 4),
    listOf(1 to 2, 4 to 3),
    listOf(0 to 3, 4 to 2),
    listOf(2 to 2),
    listOf(0 to 1, 3 to 1),
)

/**
 * 首次加载骨架:几何全部来自 GRID_* 常量(时间列宽/行高/列宽公式/表头起点),
 * 与真课表逐像素对齐,加载完成换成真网格时不会跳。替代"白屏 + 转圈"。
 * 呼吸用整块 graphicsLayer 的 alpha,读状态不触发子节点重组。
 */
@Composable
private fun ScheduleSkeleton(modifier: Modifier = Modifier) {
    val blockColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val shimmer = rememberInfiniteTransition(label = "skeleton")
    val shimmerAlpha by shimmer.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val colW = gridColW(maxWidth.value)
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shimmerAlpha },
        ) {
            // 表头:起点让出时间列,每格宽列表宽,最小高度与真表头一致
            Row(Modifier.padding(start = GRID_TIME_COL_W)) {
                repeat(7) {
                    Box(
                        Modifier.width(colW).height(GRID_HEADER_MIN_H).padding(1.dp)
                            .background(blockColor, RoundedCornerShape(6.dp)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(GRID_TIME_COL_W)) {
                    repeat(10) {
                        Box(
                            Modifier.height(GRID_ROW_H).fillMaxWidth().padding(4.dp)
                                .background(blockColor, RoundedCornerShape(4.dp)),
                        )
                    }
                }
                SkeletonBlocks.forEach { blocks ->
                    Column(Modifier.width(colW)) {
                        var cursor = 0
                        blocks.forEach { (start, span) ->
                            if (start > cursor) {
                                Spacer(Modifier.height(GRID_ROW_H * (start - cursor)))
                            }
                            Box(
                                Modifier.height(GRID_ROW_H * span).fillMaxWidth().padding(1.dp)
                                    .background(blockColor, RoundedCornerShape(8.dp)),
                            )
                            cursor = start + span
                        }
                        if (cursor < 10) Spacer(Modifier.height(GRID_ROW_H * (10 - cursor)))
                    }
                }
            }
        }
    }
}
