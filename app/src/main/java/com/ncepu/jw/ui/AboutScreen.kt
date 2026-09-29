package com.ncepu.jw.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val REPO_URL = "https://github.com/abcde2333/NcepuJw"
private const val REPO_LABEL = "github.com/abcde2333/NcepuJw"

/** 关于页:版本信息 / 获取更新 / 开源地址 / 开源声明 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Long,
    update: com.ncepu.jw.update.Updater.State = com.ncepu.jw.update.Updater.State.Idle,
    onCheckUpdate: () -> Unit = {},
    onUpdateAction: () -> Unit = {},
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "华电教务",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "v$versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(24.dp))

            AboutCard(Icons.Filled.Info, "版本信息", "版本 $versionName（构建 $versionCode）")

            AboutCard(
                Icons.Filled.SystemUpdate,
                "获取更新",
                "GitHub 主站不可用时,自动改用镜像下载;下载完成后按提示安装",
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        updateStatusText(update, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    UpdateAction(update, onCheckUpdate, onUpdateAction)
                }
            }

            AboutCard(Icons.Filled.Code, "开源地址", REPO_LABEL) {
                Row(
                    Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "在 GitHub 查看源码与发行版",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            AboutCard(
                Icons.Filled.Gavel,
                "开源声明",
                "NcepuJw 是一款开源的华电教务助手,源代码基于 GNU General Public License " +
                    "v3.0(或更新版本)发布。你可以自由使用、修改和分发本软件;" +
                    "分发修改版时须以相同协议公开源代码。协议全文见仓库根目录 LICENSE。",
            )

            Spacer(Modifier.height(10.dp))
            Text(
                "课表与成绩数据来自华北电力大学教务系统\n账号凭据仅保存在本机,不上传第三方",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun AboutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = profileCardColors(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                )
                extra?.invoke()
            }
        }
    }
}

private fun updateStatusText(update: com.ncepu.jw.update.Updater.State, currentVersion: String): String =
    when (update) {
        is com.ncepu.jw.update.Updater.State.Idle -> "当前版本 $currentVersion"
        is com.ncepu.jw.update.Updater.State.Checking -> "正在检查…"
        is com.ncepu.jw.update.Updater.State.Latest -> "已是最新(${update.versionName})"
        is com.ncepu.jw.update.Updater.State.Available -> "发现新版本 v${update.info.versionName}"
        is com.ncepu.jw.update.Updater.State.Downloading -> "下载中 ${update.progress}%"
        is com.ncepu.jw.update.Updater.State.Downloaded -> "下载完成,点击安装"
        is com.ncepu.jw.update.Updater.State.Failed -> update.message ?: "检查失败"
    }

@Composable
private fun UpdateAction(
    update: com.ncepu.jw.update.Updater.State,
    onCheckUpdate: () -> Unit,
    onUpdateAction: () -> Unit,
) {
    when (val s = update) {
        is com.ncepu.jw.update.Updater.State.Checking ->
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        is com.ncepu.jw.update.Updater.State.Downloading ->
            CircularProgressIndicator(
                progress = { s.progress / 100f },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        is com.ncepu.jw.update.Updater.State.Available ->
            Button(onClick = onUpdateAction) { Text("下载 v${s.info.versionName}") }
        is com.ncepu.jw.update.Updater.State.Downloaded ->
            Button(onClick = onUpdateAction) { Text("安装") }
        else ->
            TextButton(onClick = onCheckUpdate) { Text("检查更新") }
    }
}
