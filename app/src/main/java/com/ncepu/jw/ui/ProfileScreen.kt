package com.ncepu.jw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    onLogout: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        name?.takeIf { it.isNotBlank() }
                            ?: account.ifBlank { if (loggedIn) "已登录" else "未登录" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (!loggedIn) "点击下方「登录教务系统」查看课表"
                        else if (name.isNullOrBlank()) "已登录"
                        else "学号 $account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

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

        // 功能入口(独立图标区分)
        if (loggedIn) {
            EntryCard("成绩查询", Icons.Filled.BarChart, onOpenGrades)
        } else {
            EntryCard("登录教务系统", Icons.Filled.Login, onOpenJwxtLogin)
        }
        EntryCard("U净洗衣", Icons.Filled.LocalLaundryService, onOpenWasher)
        EntryCard("培养方案", Icons.Filled.MenuBook, onOpenPyfa)
        EntryCard("设置", Icons.Filled.Settings, onOpenSettings)

        InfoRow(Icons.Filled.Info, "数据来源", "华北电力大学教务系统(强智 R4.5)\njwxt.ncepu.edu.cn")

        if (loggedIn) {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text("退出登录")
            }
        }

        Text(
            "仅供个人学习查分使用,请勿用于商业用途\n凭据仅保存在本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp),
        )
    }
}

@Composable
private fun UpdateBanner(text: String, onClick: (() -> Unit)?) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EntryCard(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}
