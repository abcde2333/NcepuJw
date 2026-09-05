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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialkolor.dynamicColorScheme
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.ThemeMode
import com.ncepu.jw.reminder.ReminderScheduler
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
    val preset = ThemePresets.byKey(presetKey)

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

        ListItem(
            headlineContent = { Text("主题色彩") },
            supportingContent = {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemePresets.ALL.forEach { p ->
                        val swatch = if (p.dynamic) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            dynamicColorScheme(seedColor = p.seed, isDark = false, isAmoled = false).primary
                        }
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
        ListItem(
            headlineContent = { Text("莫奈动态取色") },
            supportingContent = {
                Text(
                    if (preset.dynamic) "选中“莫奈取色”时按系统壁纸配色"
                    else "切到“莫奈取色”预设后生效",
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
                        listOf(
                            NavMaterial.SOLID to "实色",
                            NavMaterial.LIQUID to "液态玻璃",
                            NavMaterial.BLUR to "高斯模糊",
                        ).forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = navMaterial == m,
                                onClick = { onNavMaterialChange(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(label) }
                        }
                    }
                    Text(
                        "液态玻璃/高斯模糊需要 Android 13+,且建议先设置背景图;按住底栏左右滑动可快速切换页面",
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
                    listOf(0, 5, 10, 15, 20).forEachIndexed { i, min ->
                        SegmentedButton(
                            selected = leadMinutes == min,
                            onClick = { onLeadChange(min) },
                            shape = SegmentedButtonDefaults.itemShape(i, 5),
                        ) { Text(if (min == 0) "准时" else "${min}分") }
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text("节次上课时间") },
            supportingContent = { Text(sectionTimes.joinToString(" / ")) },
            trailingContent = { TextButton(onClick = { showTimeDialog = true }) { Text("修改") } },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

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
    val labels = listOf("1-2节", "3-4节", "5-6节", "7-8节", "9-10节", "11-12节")

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
