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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.ncepu.jw.data.BillUi
import com.ncepu.jw.data.BillUiState

// ===== 本页专属的形状常量:只建一次,不在组合里每帧 new =====

/** 整页只有这一块底板:20dp,和课表/饮水的内容卡一致 */
private val PanelShape = RoundedCornerShape(20.dp)

/** 统计块:底板里再切一小块,12dp */
private val StatShape = RoundedCornerShape(12.dp)

/** 抵扣小标 / 胶囊 */
private val ChipShape = RoundedCornerShape(6.dp)
private val PillShape = RoundedCornerShape(percent = 50)

/** 行内分割线颜色 */
private fun dividerColor(onSurface: androidx.compose.ui.graphics.Color) = onSurface.copy(alpha = 0.08f)

/**
 * 『我的账单』页(纯 UI,数据由 [BillUiState] 一次给全)。
 *
 * 结构:整页一块毛玻璃底板 —— 顶部『账单记录 / 共 N 笔』+ 刷新入口 →
 * 本月 / 今日 / 昨日三块等宽统计 → 本月积分抵扣小字 → 提示条 → 明细行(在底板内滚动)。
 * 未登录时明细区换成登录入口,已登录但没数据时换成空态文案。
 *
 * 底板一律走 [GlassCard]:没开背景图时是圆角衬底,开了就是毛玻璃。
 */
@Composable
fun BillPage(
    state: BillUiState,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    bgEnabled: Boolean = false,
    glassBackdrop: Backdrop? = null,
) {
    val bottomInset = LocalBottomBarInset.current
    // 条数优先用后端给的 total,缺省时退回实际行数
    val count = if (state.total > 0) state.total else state.items.size

    Box(
        Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomInset + 10.dp),
    ) {
        GlassCard(bgEnabled, glassBackdrop, Modifier.fillMaxSize(), shape = PanelShape) {
            PanelHeader(
                count = count,
                loggedIn = state.loggedIn,
                loading = state.loading,
                onRefresh = onRefresh,
            )

            if (state.loggedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatBlock("本月消费", state.monthText, Modifier.weight(1f))
                    StatBlock("今日消费", state.todayText, Modifier.weight(1f))
                    StatBlock("昨日消费", state.yesterdayText, Modifier.weight(1f))
                }
                val deduct = state.monthDeductText
                if (!deduct.isNullOrBlank()) {
                    Text(
                        "本月积分抵扣 $deduct",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }

            val message = state.message
            if (!message.isNullOrBlank()) {
                InlineBanner(message)
            }

            HorizontalDivider(color = dividerColor(MaterialTheme.colorScheme.onSurface))

            when {
                !state.loggedIn -> LoginBody(onLogin)
                state.items.isEmpty() -> EmptyBody(state.loading)
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(state.items, key = { _, bill -> bill.id }) { index, bill ->
                        BillRow(bill)
                        if (index != state.items.lastIndex) {
                            HorizontalDivider(
                                color = dividerColor(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 底板头部:左上标题 + 条数,右上刷新(加载中换进度圈并禁用) */
@Composable
private fun PanelHeader(
    count: Int,
    loggedIn: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "账单记录",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (loggedIn) "共 $count 笔" else "未登录",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (loggedIn) {
            Box(
                Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .clickable(enabled = !loading, onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "刷新",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 底板内的一块统计:小标题 + 金额 */
@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(StatShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 底板内的提示条(接口报错 / 需要重新登录) */
@Composable
private fun InlineBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp)
            .clip(StatShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            message,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.error,
            lineHeight = 15.sp,
        )
    }
}

/** 明细行:标题 + 时间 + 状态行(+ 抵扣小标),右侧金额 */
@Composable
private fun BillRow(bill: BillUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                bill.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (bill.timeText.isNotBlank()) {
                Text(
                    bill.timeText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bill.statusLine,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val deduct = bill.deductText
                if (!deduct.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        deduct,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(ChipShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            bill.amountText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 未登录:底板内的登录入口 */
@Composable
private fun LoginBody(onLogin: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "登录后查看消费明细",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "账单走饮水账号,登录一次即可",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
        Button(
            onClick = onLogin,
            shape = PillShape,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Text("登录", fontSize = 13.sp)
        }
    }
}

/** 已登录但没数据 */
@Composable
private fun EmptyBody(loading: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                Icons.Rounded.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "还没有账单",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
