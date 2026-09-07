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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ncepu.jw.data.SelectedCourse
import com.ncepu.jw.data.XkRound

/**
 * 选课页:上半为选课中心轮次(点"进入"用内嵌网页打开学校选课界面),
 * 下半为当前学期已选结果(原生只读)。
 */
@Composable
fun SelectionScreen(
    loading: Boolean,
    error: String?,
    rounds: List<XkRound>,
    courses: List<SelectedCourse>,
    loggedIn: Boolean = true,
    onEnterRound: (XkRound) -> Unit,
    onRetry: () -> Unit,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "选课中心",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "刷新",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onRetry() },
                )
            }
        }
        if (error != null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("点此重试", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onRetry() })
                }
            }
        }
        items(rounds) { round ->
            RoundCard(round, onEnterRound)
        }
        if (!loggedIn && error == null) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("尚未登录教务系统", fontWeight = FontWeight.Bold)
                        Text(
                            "选课轮次需要登录后查看:请到「我的」→ 登录教务系统(或统一身份认证),完成后回到本页点“刷新”",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        } else if (rounds.isEmpty() && error == null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("当前没有选课轮次", color = MaterialTheme.colorScheme.outline)
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
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "我的选课结果",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
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
                Text(
                    "暂无选课数据",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(courses) { c ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium, maxLines = 2)
                        Spacer(Modifier.height(2.dp))
                        val tags = buildList {
                            if (c.teacher.isNotBlank()) add(c.teacher)
                            if (c.group.isNotBlank()) add(c.group)
                            add("学分${c.credit}")
                            if (c.attr.isNotBlank()) add(c.attr)
                        }
                        Text(tags.joinToString(" · "), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.code, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text("课序号 ${c.seq}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RoundCard(round: XkRound, onEnter: (XkRound) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    round.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    round.status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (round.status) {
                        "进行中" -> MaterialTheme.colorScheme.primary
                        "未开始" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            if (round.startText.isNotBlank()) {
                Text("开始  ${round.startText}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (round.endText.isNotBlank()) {
                Text("截止  ${round.endText}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (round.dailyText.isNotBlank()) {
                Text("每日开放  ${round.dailyText}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "进入选课 →",
                color = if (round.ongoing) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onEnter(round) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}
