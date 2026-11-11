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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onRefresh: () -> Unit = onRetry,
    /** 列表来自本地缓存(没连校园网):在标题下说明数据来源,别让人误以为是最新的 */
    fromCache: Boolean = false,
    cachedAtMillis: Long = 0L,
) {
    Column(Modifier.fillMaxSize()) {
        SemesterBar(
            semesters = semesters,
            selected = selected,
            onChange = onSemesterChange,
            title = "考试安排",
            // 与课表页右上角同一套 HeaderIconAction(阴影 + 判色),加载中置灰防连点
            trailing = {
                HeaderIconAction(
                    label = "刷新",
                    onClick = onRefresh,
                    enabled = !loading,
                    icon = { Icon(Icons.Filled.Refresh, "刷新考试安排", Modifier.size(22.dp)) },
                )
            },
        )

        // 刷新失败(还有旧数据):与课表页同一条非阻断提示,带原因和重试
        if (error != null && exams.isNotEmpty()) {
            StaleScheduleBanner(
                reason = error,
                updatedAtMillis = cachedAtMillis,
                onRetry = onRetry,
                message = "刷新失败,显示的是上次缓存的考试安排",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // 离线提示:只说明数据来源,不挡内容、不需要手动关(刷新成功后自动消失);
        // 已经有失败横幅时不重复说一遍
        if (fromCache && error == null && exams.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = if (cachedAtMillis > 0L) {
                        "离线缓存 · 更新于 ${formatCachedAt(cachedAtMillis)} · 点右上角刷新可重试"
                    } else {
                        "离线缓存 · 点右上角刷新可重试"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        if (error != null && exams.isEmpty()) {
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
            EmptyState(
                Icons.Filled.DateRange,
                "本学期暂无考试安排",
                "期末考试安排一般在考前一至两个月发布",
                modifier = Modifier.fillMaxSize(),
            )
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
                                    // 补上 primaryContainer 胶囊底:光有字浮在卡片上像写了一半
                                    Text(
                                        e.type,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
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
                // 卡片之间靠间距分隔即可,再叠一条 Divider 就成了双重分隔
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 缓存时间:显示 月-日 时:分 */
private fun formatCachedAt(ms: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(ms))
