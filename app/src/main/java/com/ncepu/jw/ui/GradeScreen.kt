package com.ncepu.jw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.ncepu.jw.data.Grade
import com.ncepu.jw.data.GradeStats
import com.ncepu.jw.data.Semester
import java.util.Locale

/**
 * 成绩页。
 *
 * 学分绩口径(参考 Ncepu-Assistant):学分绩 = 必修课按学分加权的平均分,
 * 排序/筛选只影响显示,勾选模式下只算勾中的必修课(方便算保研/专业排名那几门)。
 * 学期下拉由调用方只传「有成绩的学期」。
 */
@Composable
fun GradeScreen(
    semesters: List<Semester>,
    selected: Semester,
    loading: Boolean,
    error: String?,
    grades: List<Grade>,
    onSemesterChange: (Semester) -> Unit,
    onRetry: () -> Unit,
    onEvaluate: (() -> Unit)? = null,
    /** 当前列表是本地缓存(离线/拉取失败):顶部挂一条说明横幅 */
    fromCache: Boolean = false,
    /** 缓存写入时间,用于横幅上的"更新于" */
    cachedAtMillis: Long = 0L,
    /** 背景图开关:卡片底色交给 glassSurface(未开启时回退成圆角矩形衬底) */
    bgEnabled: Boolean = false,
    /** 毛玻璃采样源(MainActivity 的隐形壁纸副本) */
    glassBackdrop: Backdrop? = null,
    /** 是否已登录教务:未登录也能进成绩页看本机缓存,只提示去登录 */
    loggedIn: Boolean = true,
    /** 未登录时的登录入口 */
    onLogin: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(0) }      // 0 默认 1 学期 2 成绩 3 学分
    var desc by rememberSaveable { mutableStateOf(true) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    fun rowKey(g: Grade) = "${g.term}|${g.course}|${g.score}|${g.credit}"

    Column(Modifier.fillMaxSize()) {
        SemesterBar(semesters, selected, onSemesterChange, title = "成绩查询", trailing = if (onEvaluate != null) {
            {
                Text(
                    "去评教",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onEvaluate() },
                )
            }
        } else null)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp))
                    Text("点此重试", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onRetry() })
                }
            }
            return@Column
        }

        if (!loggedIn) {
            LoginNotice(onLogin)
        }

        if (fromCache && grades.isNotEmpty()) {
            CacheBanner(cachedAtMillis, onRetry)
        }

        // 未登录且本机没有缓存:直接给登录入口,别让用户对着空汇总卡发呆
        if (!loggedIn && grades.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        Icons.Filled.BarChart,
                        "未登录教务系统",
                        "登录后可查看成绩、学分绩与排名",
                    )
                    Spacer(Modifier.height(16.dp))
                    LoginButton(onLogin)
                }
            }
            return@Column
        }

        // ---- 汇总:全部课程口径(对齐教务「成绩预览」单)+ 必修学分绩 ----
        val sum = GradeStats.summarize(grades)
        val must = GradeStats.summarize(grades, mustOnly = true)
        val pickedSum = GradeStats.summarize(grades.filter { rowKey(it) in picked })

        GlassCard(
            bgEnabled,
            glassBackdrop,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell("加权平均分", sum.avgScore?.let { fmt2(it) } ?: "--")
                StatCell("加权绩点", sum.avgPoint?.let { fmt2(it) } ?: "--")
                StatCell("总学分", if (sum.counted == 0) "--" else String.format(Locale.US, "%.1f", sum.credits))
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (selectMode) {
                        Text(
                            "已选 ${picked.size} 门 · 学分绩 ${fmt2(pickedSum.avgScore)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "学分 ${String.format(Locale.US, "%.1f", pickedSum.credits)} · 只算勾中的课",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        Text(
                            "必修学分绩 ${fmt2(must.avgScore)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "必修 ${must.counted} 门 · ${String.format(Locale.US, "%.1f", must.credits)} 学分 · 按学分加权",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                SelectChip(selectMode) {
                    selectMode = !selectMode
                    if (!selectMode) picked = emptySet()
                }
            }
        }

        // ---- 搜索 + 排序 ----
        GlassCard(
            bgEnabled,
            glassBackdrop,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("搜索课程", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("默认", "成绩", "学分").forEachIndexed { i, label ->
                    val active = sort == i
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                            .clickable {
                                if (active && i != 0) desc = !desc else { sort = i; desc = true }
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (active && i != 0) "$label ${if (desc) "↓" else "↑"}" else label,
                            fontSize = 11.sp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // ---- 列表:搜索只影响显示,勾选照旧参与计算 ----
        val shown = remember(grades, query, sort, desc) {
            val base = if (query.isBlank()) grades
            else grades.filter { it.course.contains(query.trim(), ignoreCase = true) }
            // 学期顺序在本页由「全部学期」汇总看,列表不再提供学期排序
            val sorted = when (sort) {
                1 -> base.sortedBy { GradeStats.toScore(it.score) ?: -1.0 }
                2 -> base.sortedBy { it.credit.toDoubleOrNull() ?: -1.0 }
                else -> base
            }
            if (sort == 0 || desc) sorted.reversed() else sorted
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(shown, key = { i, g -> "$i-${g.term}-${g.course}" }) { _, g ->
                GradeItem(
                    g = g,
                    bgEnabled = bgEnabled,
                    glassBackdrop = glassBackdrop,
                    selectable = selectMode,
                    checked = rowKey(g) in picked,
                    onToggle = {
                        val k = rowKey(g)
                        picked = if (k in picked) picked - k else picked + k
                    },
                )
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.BarChart,
                        if (query.isBlank()) "本学期暂无成绩" else "没有匹配的课程",
                        if (query.isBlank()) "成绩一般在考试后一到两周公布,可切换学期试试" else "换个关键词试试",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                }
            }
        }
    }
}

private fun fmt2(v: Double?): String = v?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

/** 未登录提示条:带「去登录」按钮,与缓存横幅同一套视觉 */
@Composable
private fun LoginNotice(onLogin: () -> Unit) {
    GlassCard(
        bgEnabled = false,
        backdrop = null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        tintOverride = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "未登录教务系统,当前只显示本机缓存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text(
                "去登录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLogin() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** 空态里的登录按钮:主色胶囊,和「关于」页按钮同款圆角 */
@Composable
private fun LoginButton(onLogin: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onLogin() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            "登录教务系统",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** 「正在查看缓存」提示:中性底色(不是错误色),右侧可点刷新 */
@Composable
private fun CacheBanner(cachedAtMillis: Long, onRetry: () -> Unit) {
    val at = if (cachedAtMillis > 0L) {
        java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US).format(java.util.Date(cachedAtMillis))
    } else ""
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "正在查看缓存的成绩",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                if (at.isNotEmpty()) "更新于 $at · 联网后自动刷新" else "联网后自动刷新",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            "刷新",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable { onRetry() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 勾选模式开关:开启后点必修课卡片累加学分绩 */
@Composable
private fun SelectChip(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Checklist,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (on) "勾选中" else "勾选计算",
            fontSize = 11.sp,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GradeItem(
    g: Grade,
    bgEnabled: Boolean,
    glassBackdrop: Backdrop?,
    selectable: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    // 单条成绩 = 一张小卡(14dp):行与行之间靠间距分隔,不再画通栏分割线
    GlassCard(
        bgEnabled,
        glassBackdrop,
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (selectable) Modifier.clickable { onToggle() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectable) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Text("✓", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(g.course, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                val tags = listOfNotNull(
                    g.term.takeIf { it.isNotBlank() },
                    g.type.takeIf { it.isNotBlank() },
                    g.examType.takeIf { it.isNotBlank() && it != "正常" },
                )
                if (tags.isNotEmpty()) {
                    Text(tags.joinToString(" · "), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    g.score.ifBlank { "--" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        GradeStats.toScore(g.score)?.let { it < 60 } == true ->
                            MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                // 该部署的绩点列常为空:空时按汇总同一口径折算,保证单行与合计不打架
                val point = g.gradePoint.ifBlank {
                    GradeStats.toScore(g.score)?.let {
                        String.format(Locale.US, "%.1f", GradeStats.toPoint(it))
                    }.orEmpty()
                }
                Text("学分${g.credit} 绩点${point.ifBlank { "-" }}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
