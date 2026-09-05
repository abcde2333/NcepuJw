package com.ncepu.jw.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.PyfaData

/** 培养方案页:专业信息 + 培养目标/要求(可折叠)+ 核心课程 + 教学进程课程表(按学期分组) */
@Composable
fun PyfaScreen(
    loading: Boolean,
    error: String?,
    data: PyfaData?,
    onRetry: () -> Unit,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null || data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "暂无培养方案数据",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(24.dp))
                Text("点此重试", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onRetry() })
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp).statusBarsPadding().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        data.major.ifBlank { "培养方案" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (data.grade.isNotBlank()) {
                        Text(
                            "适用年级 ${data.grade} 级 · 共 ${data.courses.size} 门课程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        item {
            ExpandableCard("培养目标", data.goals)
            ExpandableCard("专业培养基本要求", data.requirements)
            if (data.coreCourses.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("专业核心课程", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            data.coreCourses,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
            Text(
                "教学进程(按开设学期)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        val grouped = data.courses.groupBy { it.term }.toSortedMap(compareBy { it.toIntOrNull() ?: 99 })
        grouped.forEach { (term, courses) ->
            item(key = "term_$term") {
                Text(
                    "第${term}学期 · ${courses.size} 门",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(courses.size, key = { "c_${term}_${courses[it].code}_${courses[it].name}" }) { i ->
                val c = courses[i]
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium, maxLines = 2)
                            Spacer(Modifier.height(2.dp))
                            val tags = buildList {
                                if (c.category.isNotBlank()) add(c.category)
                                add("学分${c.credit}")
                                val hours = c.hoursText.ifBlank { c.lecture }
                                if (hours.isNotBlank() && hours != "0") add("${hours}学时")
                            }
                            Text(tags.joinToString(" · "), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline)
                        }
                        Text(c.code, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ExpandableCard(title: String, paragraphs: List<String>) {
    if (paragraphs.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 6.dp)) {
                    paragraphs.forEach { p ->
                        Text(
                            p,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
