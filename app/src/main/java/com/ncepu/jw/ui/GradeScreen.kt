package com.ncepu.jw.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.Grade
import com.ncepu.jw.data.Semester
import java.util.Locale

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
) {
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp))
                    Text("点此重试", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onRetry() })
                }
            }
            return@Column
        }

        // GPA 汇总:口径对齐教务「成绩预览」单(等级制折算百分制 + 全部学分加权),算法见 GradeStats。
        // 原来还额外要求"绩点列非空",而该部署的绩点列基本是空的 → 一条都不计入,三个数全成 "--"
        val sum = com.ncepu.jw.data.GradeStats.summarize(grades)

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell("加权平均分", sum.avgScore?.let { String.format(Locale.US, "%.2f", it) } ?: "--")
                StatCell("加权绩点", sum.avgPoint?.let { String.format(Locale.US, "%.2f", it) } ?: "--")
                StatCell("总学分", if (sum.counted == 0) "--" else String.format(Locale.US, "%.1f", sum.credits))
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(grades, key = { i, g -> "$i-${g.term}-${g.course}" }) { _, g ->
                GradeItem(g)
            }
            if (grades.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.BarChart,
                        "本学期暂无成绩",
                        "成绩一般在考试后一到两周公布,可切换学期试试",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                }
            }
        }
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

@Composable
private fun GradeItem(g: Grade) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(g.course, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                val tags = listOfNotNull(
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
                        g.score.toDoubleOrNull()?.let { it < 60 } == true ->
                            MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                // 该部署的绩点列常为空:空时按汇总同一口径折算,保证单行与合计不打架
                val point = g.gradePoint.ifBlank {
                    com.ncepu.jw.data.GradeStats.toScore(g.score)?.let {
                        String.format(Locale.US, "%.1f", com.ncepu.jw.data.GradeStats.toPoint(it))
                    }.orEmpty()
                }
                Text("学分${g.credit} 绩点${point.ifBlank { "-" }}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
