package com.ncepu.jw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.dynamicColorScheme
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.ThemeMode
import com.ncepu.jw.reminder.ReminderScheduler
import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ncepu.jw.ui.theme.ThemePresets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    presetKey: String,
    dynamicColor: Boolean,
    fontScale: Float,
    navShape: NavBarShape,
    navMaterial: NavMaterial,
    hasBackground: Boolean,
    bgBlur: Float,
    bgDim: Float,
    reminderEnabled: Boolean,
    examReminderEnabled: Boolean,
    leadMinutes: Int,
    sectionTimes: List<String>,
    weekStartMillis: Long,
    exactAlarmGranted: Boolean,
    scheduleSource: String = "AUTO",
    onImportScheduleXls: () -> Unit = {},
    onUseImportedChange: (Boolean) -> Unit = {},
    webvpnEnabled: Boolean = false,
    onWebvpnChange: (Boolean) -> Unit = {},
    widgetStyle: String = "dark",
    onWidgetStyleChange: (String) -> Unit = {},
    update: com.ncepu.jw.update.Updater.State = com.ncepu.jw.update.Updater.State.Idle,
    currentVersion: String = "",
    onCheckUpdate: () -> Unit = {},
    onUpdateAction: () -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit,
    onPresetChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onNavShapeChange: (NavBarShape) -> Unit,
    onNavMaterialChange: (NavMaterial) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onBgBlurChange: (Float) -> Unit,
    onBgDimChange: (Float) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onExamReminderToggle: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onTimesChange: (List<String>) -> Unit,
    onWeekStartChange: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var showTimeDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showLeadDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // ---------- 外观 ----------
        SectionHeader(Icons.Filled.Palette, "外观")
        ListItem(
            headlineContent = { Text("深浅色") },
            supportingContent = {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) {
                            Text(when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色"
                            })
                        }
                    }
                }
            },
        )

        if (!dynamicColor) {
            ListItem(
                headlineContent = { Text("主题色彩") },
                supportingContent = {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ThemePresets.ALL.forEach { p ->
                            val swatch = dynamicColorScheme(
                                seedColor = p.seed, isDark = false, isAmoled = false,
                            ).primary
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(swatch, CircleShape)
                                    .border(
                                        if (presetKey == p.key) 3.dp else 1.dp,
                                        if (presetKey == p.key) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape,
                                    )
                                    .clickable { onPresetChange(p.key) },
                                contentAlignment = Alignment.Center,
                            ) {}
                        }
                    }
                },
            )
        }
        ListItem(
            headlineContent = { Text("莫奈动态取色") },
            supportingContent = {
                Text(
                    if (dynamicColor) "按系统壁纸自动配色(Android 12+);关闭后可自选主题色彩"
                    else "关闭中:使用下方主题色彩预设",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = { onDynamicColorChange(it) },
                )
            },
        )

        ListItem(
            headlineContent = { Text("小部件样式") },
            supportingContent = {
                Column(Modifier.padding(top = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("dark" to "深色", "light" to "浅色", "translucent" to "半透明")
                            .forEachIndexed { i, (key, label) ->
                                SegmentedButton(
                                    selected = widgetStyle == key,
                                    onClick = { onWidgetStyleChange(key) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                ) { Text(label) }
                            }
                    }
                    Text(
                        "桌面「今日课表」小部件背景;半透明在 Android 12+ 且启动器支持时呈毛玻璃",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )

        ListItem(
            headlineContent = { Text("文本大小") },
            supportingContent = {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf(0.85f, 1.0f, 1.15f, 1.3f).forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = fontScale == s,
                            onClick = { onFontScaleChange(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, 4),
                        ) {
                            Text(when (s) {
                                0.85f -> "小"; 1.0f -> "标准"; 1.15f -> "大"; else -> "特大"
                            })
                        }
                    }
                }
            },
        )

        ListItem(
            headlineContent = { Text("底部导航栏形状") },
            supportingContent = {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf(NavBarShape.STANDARD to "标准", NavBarShape.FLOATING to "悬浮").forEachIndexed { i, (s, label) ->
                        SegmentedButton(
                            selected = navShape == s,
                            onClick = { onNavShapeChange(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text("底栏材质") },
            supportingContent = {
                Column(Modifier.padding(top = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        // 液态玻璃仅悬浮形状支持
                        val materials = buildList {
                            add(NavMaterial.SOLID to "实色")
                            if (navShape == NavBarShape.FLOATING) add(NavMaterial.LIQUID to "液态玻璃")
                            add(NavMaterial.BLUR to "高斯模糊")
                        }
                        materials.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = navMaterial == m,
                                onClick = { onNavMaterialChange(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, materials.size),
                            ) { Text(label) }
                        }
                    }
                    Text(
                        if (navShape == NavBarShape.FLOATING)
                            "液态玻璃为悬浮胶囊底栏:按住当前页签出现液态玻璃泡,可左右拖动快速切换页面(需要背景图折射,Android 13+ 效果最佳)"
                        else "切换到悬浮形状后可选择液态玻璃",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // ---------- 课表 ----------
        SectionHeader(Icons.Filled.CalendarMonth, "课表")
        ListItem(
            headlineContent = { Text("使用导入的课表") },
            supportingContent = {
                Text(
                    if (scheduleSource == "MANUAL") "开启中:显示手动导入的课表,不联网刷新"
                    else "关闭中:登录/刷新时联网获取课表(切换学期同样联网)"
                )
            },
            trailingContent = {
                Switch(
                    checked = scheduleSource == "MANUAL",
                    onCheckedChange = onUseImportedChange,
                )
            },
        )
        ListItem(
            headlineContent = { Text("导入课表文件(XLS)") },
            supportingContent = {
                Text(
                    if (scheduleSource == "MANUAL")
                        "已导入教务「打印课表」导出的 .xls 文件;重新导入会覆盖"
                    else
                        "导入教务「打印课表」导出的 .xls 文件,导入后可在上方开关启用"
                )
            },
            trailingContent = { Button(onClick = onImportScheduleXls) { Text("选择文件") } },
        )
        ListItem(
            headlineContent = { Text("课表背景图") },
            supportingContent = { Text(if (hasBackground) "已设置(仅课表页显示)" else "从相册选择图片") },
            trailingContent = {
                if (hasBackground) {
                    TextButton(onClick = onClearBackground) { Text("清除") }
                } else {
                    Button(onClick = onPickBackground) { Text("选择") }
                }
            },
        )
        var localBlur by remember(bgBlur) { mutableStateOf(bgBlur) }
        var localDim by remember(bgDim) { mutableStateOf(bgDim) }
        ListItem(
            headlineContent = { Text("背景模糊 ${localBlur.toInt()}dp") },
            supportingContent = {
                Slider(
                    value = localBlur, onValueChange = { localBlur = it },
                    onValueChangeFinished = { onBgBlurChange(localBlur) },
                    valueRange = 0f..24f, enabled = hasBackground,
                )
            },
        )
        ListItem(
            headlineContent = { Text("背景暗化 ${"%.0f".format(localDim * 100)}%") },
            supportingContent = {
                Slider(
                    value = localDim, onValueChange = { localDim = it },
                    onValueChangeFinished = { onBgDimChange(localDim) },
                    valueRange = 0f..0.8f, enabled = hasBackground,
                )
            },
        )
        ListItem(
            headlineContent = { Text("第一周周一") },
            supportingContent = {
                Text(
                    "用于计算当前教学周 · " +
                        SimpleDateFormat("yyyy年M月d日", Locale.US).format(Date(weekStartMillis)),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                TextButton(onClick = { showDateDialog = true }) { Text("修改") }
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // ---------- 提醒 ----------
        SectionHeader(Icons.Filled.Alarm, "提醒")
        ListItem(
            headlineContent = { Text("上课提醒") },
            supportingContent = {
                Text(
                    when {
                        !reminderEnabled -> "按课表在上课前提醒"
                        !exactAlarmGranted -> "已开启(未授予精确闹钟,可能略有偏差)"
                        else -> "已开启"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = { Switch(checked = reminderEnabled, onCheckedChange = onReminderToggle) },
        )
        ListItem(
            headlineContent = { Text("考试提醒") },
            supportingContent = {
                Text(
                    if (examReminderEnabled) "按考试安排提前提醒" else "已关闭",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = { Switch(checked = examReminderEnabled, onCheckedChange = onExamReminderToggle) },
        )
        ListItem(
            headlineContent = { Text("提前提醒") },
            supportingContent = {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val fixed = listOf(0, 10, 15, 20)
                    val custom = leadMinutes !in fixed
                    fixed.forEachIndexed { i, min ->
                        SegmentedButton(
                            selected = leadMinutes == min,
                            onClick = { onLeadChange(min) },
                            shape = SegmentedButtonDefaults.itemShape(i, fixed.size + 1),
                        ) { Text(if (min == 0) "准时" else "${min}分") }
                    }
                    SegmentedButton(
                        selected = custom,
                        onClick = { showLeadDialog = true },
                        shape = SegmentedButtonDefaults.itemShape(fixed.size, fixed.size + 1),
                    ) { Text(if (custom && leadMinutes > 0) "${leadMinutes}分" else "自定义") }
                }
            },
        )
        ListItem(
            headlineContent = { Text("节次上课时间") },
            supportingContent = { Text(sectionTimes.joinToString(" / ")) },
            trailingContent = { TextButton(onClick = { showTimeDialog = true }) { Text("修改") } },
        )
        if (reminderEnabled || examReminderEnabled) ReminderReliabilityCard()
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // ---------- 更新 ----------
        SectionHeader(Icons.Filled.SystemUpdate, "更新")
        ListItem(
            headlineContent = { Text("检查更新") },
            supportingContent = {
                val status = when (update) {
                    is com.ncepu.jw.update.Updater.State.Idle -> "当前版本 $currentVersion"
                    is com.ncepu.jw.update.Updater.State.Checking -> "正在检查…"
                    is com.ncepu.jw.update.Updater.State.Latest -> "已是最新(${(update as com.ncepu.jw.update.Updater.State.Latest).versionName})"
                    is com.ncepu.jw.update.Updater.State.Available -> "发现新版本 v${(update as com.ncepu.jw.update.Updater.State.Available).info.versionName}"
                    is com.ncepu.jw.update.Updater.State.Downloading -> "下载中 ${(update as com.ncepu.jw.update.Updater.State.Downloading).progress}%"
                    is com.ncepu.jw.update.Updater.State.Downloaded -> "下载完成,点击安装"
                    is com.ncepu.jw.update.Updater.State.Failed -> (update as com.ncepu.jw.update.Updater.State.Failed).message ?: "检查失败"
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            },
            trailingContent = {
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
                        TextButton(onClick = { onCheckUpdate() }) { Text("检查更新") }
                }
            },
        )
        Text(
            "更新通过 GitHub Release 分发,自动尝试国内镜像加速;下载完成后按提示安装",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        // ---------- 网络 ----------
        SectionHeader(Icons.Filled.Public, "网络")
        ListItem(
            headlineContent = { Text("校外模式(免校园网)") },
            supportingContent = {
                Text(
                    if (webvpnEnabled)
                        "开启中:教务/统一认证走 myvpn 隧道,登录需一次短信验证码"
                    else
                        "关闭:需校园网或已连 EasyConnect。开启后可在校外直接访问教务",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Switch(checked = webvpnEnabled, onCheckedChange = onWebvpnChange)
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(24.dp))
        Text(
            "提醒依赖系统闹钟权限;重启手机后会自动重建。\n节次时间默认为华电作息(1-2节08:00 / 3-4节10:00 / 5-6节14:30 / 7-8节16:30 / 9-10节19:30)。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(32.dp))
    }

    if (showTimeDialog) {
        TimeEditDialog(
            initial = sectionTimes,
            onDismiss = { showTimeDialog = false },
            onConfirm = { times ->
                showTimeDialog = false
                onTimesChange(times)
            },
        )
    }
    if (showDateDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = weekStartMillis)
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onWeekStartChange(it) }
                    showDateDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDateDialog = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }
    if (showLeadDialog) {
        LeadEditDialog(
            initial = leadMinutes,
            onDismiss = { showLeadDialog = false },
            onConfirm = { min ->
                showLeadDialog = false
                onLeadChange(min)
            },
        )
    }
}

@Composable
private fun LeadEditDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in 1..120
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义提前提醒") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("提前分钟数") },
                    supportingText = { Text("1 - 120 分钟") },
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (valid) onConfirm(parsed!!) }, enabled = valid) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeEditDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val values = remember { initial.toMutableList() }
    val labels = listOf("1-2节", "3-4节", "5-6节", "7-8节", "9-10节")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节次上课时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, label ->
                    var text by remember(i) { mutableStateOf(initial[i]) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; values[i] = it },
                        label = { Text(label) },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(values.mapIndexed { i, v ->
                    ReminderScheduler.normalizeTime(v, initial[i])
                })
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 提醒可靠性引导卡:通知权限 / 精确闹钟 / 忽略电池优化 / 自启动。
 * 国产 ROM(MIUI 等)会冻结后台进程,这几项是"提醒不响/不准"的根因。
 * 每项一键跳转对应系统设置页,找不到时兜底到应用详情页并提示手动允许;
 * 状态在每次 ON_RESUME 复检(从系统设置返回即刷新)。
 */
@Composable
private fun ReminderReliabilityCard() {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()

    val notifGranted = tick.let {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val exactGranted = tick.let {
        Build.VERSION.SDK_INT < 31 ||
            (ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
    }
    val batteryOk = tick.let {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "为保证提醒准时,建议开启以下系统权限(国产 ROM 会冻结后台)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        PermissionRow(
            title = "通知权限",
            ok = notifGranted,
            onClick = {
                tryStart(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    || openAppDetails(ctx)
            },
        )
        if (Build.VERSION.SDK_INT >= 31) PermissionRow(
            title = "精确闹钟",
            ok = exactGranted,
            onClick = {
                tryStart(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${ctx.packageName}")))
                    || openAppDetails(ctx)
            },
        )
        PermissionRow(
            title = "忽略电池优化",
            ok = batteryOk,
            onClick = {
                if (!tryStart(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}")))
                ) openAppDetails(ctx)
            },
        )
        PermissionRow(
            title = "自启动管理",
            ok = false,
            okText = "去设置",
            onClick = {
                val miui = Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                )
                if (!tryStart(ctx, miui)) {
                    openAppDetails(ctx)
                    Toast.makeText(ctx, "请在应用信息中手动允许自启动/后台运行", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, okText: String = "", onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                if (ok) "已开启" else okText.ifBlank { "去开启" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
    )
}

/** 每次页面 ON_RESUME 自增一次,驱动权限状态复检(从系统设置返回即刷新)。 */
@Composable
private fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/** 依次尝试启动 intent(自动加 NEW_TASK),全部失败返回 false。 */
private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
    for (i in intents) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return true
    }
    return false
}

private fun openAppDetails(ctx: Context): Boolean =
    tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
