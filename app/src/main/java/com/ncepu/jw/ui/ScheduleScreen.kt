package com.ncepu.jw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.Semester
import com.ncepu.jw.data.parseWeeks
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
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
 * 课表页。
 * mode = WEEK:周视图(数据来自 xskb_list.do 全量解析,本地按周过滤),HorizontalPager 无缝左右滑
 * mode = ALL :学期全量视图(卡片带周次)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    loading: Boolean,
    error: String?,
    allCourses: List<Course>,
    mode: String,
    officialWeek: Int,
    weekStartMillis: Long = 0L,
    semesters: List<Semester>,
    selected: Semester,
    sectionTimes: List<String>,
    bgEnabled: Boolean,
    onSelectWeek: (Int) -> Unit,
    onShowAll: () -> Unit,
    onBackToWeek: () -> Unit,
    onSemesterChange: (Semester) -> Unit,
    onRetry: () -> Unit,
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
        // ---- 头部 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    todayText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (mode == "WEEK") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            "全部",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onShowAll() },
                        )
                        if (page != officialWeek) {
                            Text(
                                "(本周第${officialWeek}周)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(officialWeek - 1) }
                                    },
                            )
                        }
                    }
                } else {
                    Text(
                        "学期课表(全部周次) · ${selected.displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onBackToWeek() },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (onRefresh != null) {
                    Text(
                        "刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onRefresh() }
                            .padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                if (mode == "ALL") {
                    SemesterPicker(semesters, selected, onSemesterChange)
                }
                if (onOpenExams != null) {
                    Text(
                        "考试安排 →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenExams() }.padding(top = 4.dp),
                    )
                }
            }
        }

        // ---- 内容区 ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loading && allCourses.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                mode == "ALL" -> CourseGrid(
                    courses = allCourses,
                    showWeeks = true,
                    sectionTimes = sectionTimes,
                    onBackground = bgEnabled,
                    onCourseClick = { selectedCourse = it },
                )
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
                            showWeeks = false,
                            sectionTimes = sectionTimes,
                            onBackground = bgEnabled,
                            weekDates = weekDates,
                            onCourseClick = { selectedCourse = it },
                        )
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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

@Composable
private fun SemesterPicker(semesters: List<Semester>, selected: Semester, onChange: (Semester) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            "${selected.displayName} ▾",
            style = MaterialTheme.typography.bodyMedium,
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
}

/** 通用学期标题栏(成绩/考试页使用) */
@Composable
fun SemesterBar(
    semesters: List<Semester>,
    selected: Semester,
    onChange: (Semester) -> Unit,
    title: String = "课表",
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

/** 竖排网格:固定表头 + 自定义 Layout 平铺课程块(冲突课自动分栏),点击弹详情 */
@Composable
private fun CourseGrid(
    courses: List<Course>,
    showWeeks: Boolean,
    sectionTimes: List<String>,
    onBackground: Boolean,
    weekDates: List<String> = emptyList(),
    onCourseClick: (Course) -> Unit,
) {
    val slots = remember(sectionTimes) { sectionSlots(sectionTimes) }
    val rowH = 46.dp
    val timeColW = 44.dp
    val todayIdx = remember {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        when (dow) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
    }
    val placed = remember(courses) { placeCourses(courses) }
    val rowHpx = with(LocalDensity.current) { rowH.toPx() }

    // BoxWithConstraints:不订阅 LocalConfiguration(避免配置变化引发整格重组),直接用分配到的宽度
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth.value
        val colW = max(((screenW - timeColW.value - 10f) / 7f), 40f).dp
        val colWpx = with(LocalDensity.current) { colW.toPx() }

        Column(Modifier.fillMaxSize()) {
        // 表头
        Row(Modifier.padding(start = timeColW)) {
            DAY_LABELS.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(colW).padding(1.dp)
                        .background(
                            if (i + 1 == todayIdx) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label, fontSize = 12.sp,
                            color = if (i + 1 == todayIdx) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (weekDates.getOrNull(i)?.isNotBlank() == true) {
                            Text(
                                weekDates[i], fontSize = 8.sp,
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
                            fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center,
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
                            val colorIdx = abs(p.course.name.hashCode()) % CourseColors.size
                            val fg = CourseColors[colorIdx]
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = fg.copy(alpha = if (onBackground) 0.92f else 0.95f),
                                ),
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
                                    if (showWeeks && p.course.weeks.isNotBlank()) Text(
                                        p.course.weeks,
                                        fontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
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
