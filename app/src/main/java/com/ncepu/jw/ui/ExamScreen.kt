package com.ncepu.jw.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.ncepu.jw.data.Exam
import com.ncepu.jw.data.Semester

/** 考试安排页:按学期查询考试(类型/时间/地点/座位),有考试时自动设置提醒 */
@Composable
fun ExamScreen(
    semesters: List<Semester>,
    selected: Semester,
    loading: Boolean,
    error: String?,
    exams: List<Exam>,
    onSemesterChange: (Semester) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SemesterBar(semesters, selected, onSemesterChange, title = "考试安排")

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        if (exams.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "本学期暂无考试安排\n(期末考试安排一般在考前一至两个月发布)",
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
            return@Column
        }

        Text(
            "已按考试时间自动设置提醒",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(exams.size, key = { exams[it].code + exams[it].timeText + it }) { i ->
                val e = exams[i]
                Column(Modifier.fillMaxWidth()) {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    e.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (e.type.isNotBlank()) {
                                    Text(
                                        e.type,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(e.timeText, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium)
                            val lines = buildList {
                                if (e.room.isNotBlank()) add("地点:${e.room}")
                                if (e.teacher.isNotBlank()) add("老师:${e.teacher}")
                                if (e.seat.isNotBlank()) add("座位:${e.seat}")
                                if (e.note.isNotBlank()) add(e.note)
                            }
                            if (lines.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(lines.joinToString("  "), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
