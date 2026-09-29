package com.ncepu.jw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「我的」页:分组列表样式 —— 每个分区一个圆角容器,内部多行 + 行间细分隔线,
 * 图标统一主色、行尾 chevron。数据来源/凭据说明已挪到「关于」页。
 */
@Composable
fun ProfileScreen(
    account: String,
    name: String?,
    loggedIn: Boolean = true,
    onOpenJwxtLogin: () -> Unit = {},
    onOpenSettings: () -> Unit,
    update: com.ncepu.jw.update.Updater.State? = null,
    onUpdateAction: () -> Unit = {},
    onOpenPyfa: () -> Unit,
    onOpenWasher: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        // 更新横幅(有新版本/下载完成时显示)
        when (val u = update) {
            is com.ncepu.jw.update.Updater.State.Available -> UpdateBanner(
                "发现新版本 v${u.info.versionName} · 点击下载", onUpdateAction)
            is com.ncepu.jw.update.Updater.State.Downloading -> UpdateBanner(
                "新版本下载中 ${u.progress}%", null)
            is com.ncepu.jw.update.Updater.State.Downloaded -> UpdateBanner(
                "安装包已就绪 · 点击安装", onUpdateAction)
            else -> {}
        }

        SectionLabel("账号")
        GroupCard {
            AccountRow(name, account, loggedIn)
            if (loggedIn) {
                RowDivider(startInset = 74.dp)
                GroupRow(
                    Icons.Filled.Logout, "退出登录", c.error,
                    onClick = { confirmLogout = true },
                )
            }
        }

        SectionLabel("应用")
        GroupCard {
            if (loggedIn) {
                GroupRow(Icons.Filled.BarChart, "成绩查询", c.primary, onOpenGrades)
                RowDivider()
            } else {
                GroupRow(Icons.Filled.Login, "登录教务系统", c.primary, onOpenJwxtLogin)
                RowDivider()
            }
            GroupRow(Icons.Filled.LocalLaundryService, "U净洗衣", c.primary, onOpenWasher)
            RowDivider()
            GroupRow(Icons.Filled.MenuBook, "培养方案", c.primary, onOpenPyfa)
            RowDivider()
            GroupRow(Icons.Filled.Settings, "设置", c.primary, onOpenSettings)
            RowDivider()
            GroupRow(Icons.Filled.Info, "关于", c.primary, onOpenAbout)
        }

        Text(
            "仅供个人学习查分使用,请勿用于商业用途\n凭据仅保存在本机",
            style = MaterialTheme.typography.bodySmall,
            color = c.outline,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("只清除教务账号密码,饮水机登录与主题设置会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) {
                    Text("退出", color = c.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** 一个分区:单张圆角卡内竖排多行 */
@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = profileCardColors(),
    ) {
        Column(content = content)
    }
}

/**
 * 行间分隔线:两端都留边(左侧缩进到本行文字起笔处,右侧对齐行的水平内边距),
 * 只缩进一边会顶到卡片圆角、看着歪。startInset 按组内实际起笔位置传:
 * 图标行 16+22+14=52,头像行 16+44+14=74。
 */
@Composable
private fun RowDivider(startInset: Dp = 52.dp) {
    HorizontalDivider(
        Modifier.padding(start = startInset, end = 16.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

/** 标准行:主色图标 + 文字 + 尾部 chevron */
@Composable
private fun GroupRow(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 首行账号身份:头像 + 姓名/学号。纯展示,登录入口留在下方「应用」组,避免两处都能点 */
@Composable
private fun AccountRow(name: String?, account: String, loggedIn: Boolean) {
    val title = name?.takeIf { it.isNotBlank() }
        ?: account.ifBlank { if (loggedIn) "已登录" else "未登录" }
    val subtitle = when {
        !loggedIn -> "在下方「应用」里登录教务系统"
        name.isNullOrBlank() -> "已登录"
        else -> "学号 $account"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, account)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 头像:姓名首字(退回学号首字)配主题渐变圆 */
@Composable
private fun Avatar(name: String?, account: String) {
    val initial = (name?.trim()?.firstOrNull() ?: account.trim().firstOrNull() ?: '?').toString()
    val c = MaterialTheme.colorScheme
    // 渐变只在 primary 同色系内走暗:跨到 tertiary 会让首字在两端的对比度不可控
    val from = c.primary
    val to = Color(from.red * 0.72f, from.green * 0.72f, from.blue * 0.72f)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(from, to))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = c.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 卡片容器色:比 M3 默认的 surfaceContainerLow 抬一档并留 8% 透明,
 * 深色模式下压在壁纸上不至于是块死灰,材质上和玻璃底栏呼应。
 */
@Composable
fun profileCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
)

@Composable
private fun UpdateBanner(text: String, onClick: (() -> Unit)?) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = profileCardColors(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
