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
        SemesterBar(semesters, selected, onSemesterChange, trailing = if (onEvaluate != null) {
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

        // GPA 汇总
        val scored = grades.filter {
            it.score.toDoubleOrNull() != null && it.credit.toDoubleOrNull() != null && it.gradePoint.toDoubleOrNull() != null
        }
        val totalCredit = scored.sumOf { it.credit.toDouble() }
        val avgScore = if (scored.isEmpty()) null else
            scored.sumOf { it.score.toDouble() * it.credit.toDouble() } / totalCredit
        val avgJd = if (scored.isEmpty()) null else
            scored.sumOf { it.gradePoint.toDouble() * it.credit.toDouble() } / totalCredit

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
                StatCell("加权平均分", avgScore?.let { String.format(Locale.US, "%.2f", it) } ?: "--")
                StatCell("加权绩点", avgJd?.let { String.format(Locale.US, "%.2f", it) } ?: "--")
                StatCell("总学分", if (scored.isEmpty()) "--" else String.format(Locale.US, "%.1f", totalCredit))
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(grades, key = { i, g -> "$i-${g.term}-${g.course}" }) { _, g ->
                GradeItem(g)
            }
            if (grades.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center) {
                        Text("本学期暂无成绩", color = MaterialTheme.colorScheme.outline)
                    }
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
                Text("学分${g.credit} 绩点${g.gradePoint.ifBlank { "-" }}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
