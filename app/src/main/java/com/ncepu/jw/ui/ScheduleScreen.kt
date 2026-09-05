package com.ncepu.jw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures as bg2
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.Semester
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private val CourseColors = listOf(
    Color(0xFFE05565), Color(0xFF4A90D9), Color(0xFFE0913C), Color(0xFF67B279),
    Color(0xFF9A6FD0), Color(0xFF3CA6A6), Color(0xFFD9784A), Color(0xFF5B8DEF),
)

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** "2-9(周),11(周)" → 周集合;无信息返回 null(全周显示) */
fun parseWeeks(weeks: String): Set<Int>? {
    if (weeks.isBlank()) return null
    val set = mutableSetOf<Int>()
    Regex("""(\d+)\s*(?:[-~—]\s*(\d+))?""").findAll(weeks).forEach { m ->
        val a = m.groupValues[1].toIntOrNull() ?: return@forEach
        val b = m.groupValues[2].ifEmpty { m.groupValues[1] }.toIntOrNull() ?: a
        if (a in 1..30) for (x in a..minOf(b, 30)) set.add(x)
    }
    return if (set.isEmpty()) null else set
}

/** 六大节开始时间 → 12 个小节的 (节号, 开始, 结束)
 *  华电作息:每节 45 分钟,大节内两节间课间 10 分钟(如 08:00-08:45 / 08:55-09:40) */
internal fun sectionSlots(times: List<String>): List<Triple<Int, String, String>> {
    val out = mutableListOf<Triple<Int, String, String>>()
    val cal = Calendar.getInstance()
    val fmt = { t: Long -> SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(t)) }
    for ((row, s) in times.withIndex()) {
        val m = Regex("""(\d{1,2}):(\d{2})""").find(s) ?: continue
        cal.set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
        cal.set(Calendar.MINUTE, m.groupValues[2].toInt())
        cal.set(Calendar.SECOND, 0)
        val base = cal.timeInMillis
        out += Triple(row * 2 + 1, fmt(base), fmt(base + 45 * 60000))
        out += Triple(row * 2 + 2, fmt(base + 55 * 60000), fmt(base + 100 * 60000))
    }
    return out
}

/**
 * 课表页。
 * mode = WEEK:官方"我的周课表"(首页同源,按日期查询,周次由教务系统计算,零误差)
 * mode = ALL :学期全量课表(旧接口,卡片带周次)
 */
@Composable
fun ScheduleScreen(
    loading: Boolean,
    error: String?,
    courses: List<Course>,
    mode: String,
    officialWeek: Int,
    selectedWeek: Int,
    semesters: List<Semester>,
    selected: Semester,
    sectionTimes: List<String>,
    bgEnabled: Boolean,
    onWeekChange: (Int) -> Unit,
    onShowAll: () -> Unit,
    onSemesterChange: (Semester) -> Unit,
    onRetry: () -> Unit,
    onOpenExams: (() -> Unit)? = null,
) {
    val today = Calendar.getInstance()

    Column(Modifier.fillMaxSize()) {
        // ---- 头部:日期 + 周切换 + 右侧操作 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("yyyy/M/d", Locale.US).format(today.time),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (mode == "WEEK") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (selectedWeek > 1) onWeekChange(selectedWeek - 1)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                        }
                        Text(
                            "第 $selectedWeek 周",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = {
                            if (selectedWeek < 30) onWeekChange(selectedWeek + 1)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                        }
                        Text(
                            "全部",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onShowAll() },
                        )
                        if (selectedWeek != officialWeek) {
                            Text(
                                "本周第${officialWeek}周",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clickable { onWeekChange(officialWeek) },
                            )
                        }
                    }
                } else {
                    Text(
                        "学期课表(全部周次) · ${selected.displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { if (officialWeek > 0) onWeekChange(officialWeek) },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
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

        // ---- 网格(WEEK 模式下左右滑动切换周次) ----
        val density = LocalDensity.current
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (mode == "WEEK") Modifier.pointerInput(selectedWeek) {
                        var acc = 0f
                        val threshold = 90.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = { acc = 0f },
                            onDragCancel = { acc = 0f },
                        ) { change, amount ->
                            change.consume()
                            acc += amount
                            while (acc >= threshold) {
                                if (selectedWeek < 30) onWeekChange(selectedWeek + 1)
                                acc -= threshold
                            }
                            while (acc <= -threshold) {
                                if (selectedWeek > 1) onWeekChange(selectedWeek - 1)
                                acc += threshold
                            }
                        }
                    } else Modifier
                ),
        ) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                courses.isNotEmpty() -> CourseGrid(
                    courses = courses,
                    showWeeks = mode == "ALL",
                    sectionTimes = sectionTimes,
                    onBackground = bgEnabled,
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                        Text("点此重试", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onRetry() })
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("本学期暂无课程", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
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

/** 竖排网格:行=小节(带时间轴),列=星期,课程块按节次跨行 */
@Composable
private fun CourseGrid(
    courses: List<Course>,
    showWeeks: Boolean,
    sectionTimes: List<String>,
    onBackground: Boolean,
) {
    val slots = remember(sectionTimes) { sectionSlots(sectionTimes) }
    val rowH = 46.dp
    val timeColW = 44.dp
    // 7 列自适应屏宽:一屏完整显示周一~周日,无需横向滚动
    val screenW = LocalConfiguration.current.screenWidthDp.toFloat()
    val colW = max(((screenW - timeColW.value - 10f) / 7f), 40f).dp
    val todayIdx = remember {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        when (dow) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
    }
    val colorIdx = remember(courses) {
        courses.map { it.name }.distinct().withIndex().associate { (i, n) -> n to i }
    }
    val gridH = rowH * slots.size

    Column(Modifier.fillMaxSize()) {
        // 表头
        Row(
            Modifier
                .padding(start = timeColW.value.dp),
        ) {
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
                    Text(
                        label, fontSize = 12.sp,
                        color = if (i + 1 == todayIdx) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 主体:时间轴 + 7 天列
        Row(
            Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
        ) {
            // 时间轴
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
            // 7 天列
            Row {
                DAY_LABELS.indices.forEach { dayCol ->
                    val day = dayCol + 1
                    val dayCourses = courses.filter { it.day == day }
                    Box(
                        Modifier
                            .width(colW)
                            .height(gridH)
                            .padding(1.dp)
                            .then(
                                if (day == todayIdx && !onBackground)
                                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                                else Modifier
                            ),
                    ) {
                        dayCourses.forEach { c ->
                            val startRow = (c.sections.first - 1).coerceIn(0, slots.size - 1)
                            val span = (c.sections.last - c.sections.first + 1)
                                .coerceIn(1, slots.size - startRow)
                            val fg = CourseColors[(colorIdx[c.name] ?: 0) % CourseColors.size]
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = fg.copy(alpha = if (onBackground) 0.92f else 0.95f),
                                ),
                                modifier = Modifier
                                    .offset(y = rowH * startRow + 1.dp)
                                    .width(colW - 4.dp)
                                    .height(rowH * span - 2.dp),
                            ) {
                                Column(Modifier.padding(4.dp)) {
                                    Text(
                                        c.name,
                                        fontSize = 10.sp, lineHeight = 12.sp,
                                        color = Color.White, fontWeight = FontWeight.Bold,
                                        maxLines = 4,
                                    )
                                    if (c.room.isNotBlank()) Text(
                                        "@${c.room}",
                                        fontSize = 9.sp, lineHeight = 10.sp,
                                        color = Color.White.copy(alpha = 0.95f),
                                        maxLines = 3,
                                    )
                                    if (span >= 2 && c.teacher.isNotBlank()) Text(
                                        c.teacher,
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 2,
                                    )
                                    if (showWeeks && c.weeks.isNotBlank()) Text(
                                        c.weeks,
                                        fontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
