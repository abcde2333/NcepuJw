package com.ncepu.jw.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.ncepu.jw.data.IlifeClient
import com.ncepu.jw.data.PointsUiState

/**
 * 积分任务页(慧生活798 积分通道)。只做 UI:状态由 AppViewModel 持有,这里只读 + 回调,不发任何请求。
 *
 * 结构与「积分任务」参考图一致:
 * 顶栏(标题 + 可用/累计积分 + 刷新) → 可关闭提示 → 未登录表单 / 已登录运行胶囊 →
 * 任务列表 → 运行日志 → 自动运行开关。
 *
 * 卡片衬底一律走 [GlassCard](没开背景图时回退成圆角矩形衬底,开了就是毛玻璃),
 * 不在页面里手写 background(color) 当卡片底。
 * 整页是一个 LazyColumn;日志在卡片里平铺(只留最近 [MAX_LOG_LINES] 条),
 * 不做嵌套滚动 / 嵌套 LazyColumn,避免滚动冲突。
 */

/** 胶囊:material3 1.3.0 没有 Shapes.full,统一用百分比圆角 */
private val CapsuleShape = RoundedCornerShape(percent = 50)

/** 小卡圆角 14dp */
private val SmallCardShape = RoundedCornerShape(14.dp)

/** 输入框 / 小图块圆角 */
private val FieldShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(8.dp)

/** 日志区最多显示的行数(超出的靠"复制"取全文) */
private const val MAX_LOG_LINES = 8

/** 日志正文:等宽 11sp,便于对齐时间戳与任务名 */
private val LogTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 16.sp,
)

@Composable
fun PointsPage(
    state: PointsUiState,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onCaptchaInputChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onRunAll: () -> Unit,
    onToggleAutoDaily: (Boolean) -> Unit,
    onToggleAutoDeduct: (Boolean) -> Unit,
    bgEnabled: Boolean = false,
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    // 分割线 / 次要文字色在组合阶段取一次,下发给各卡片复用,避免每行都 copy 一遍
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 提示条:同一条 message 点掉后不再出现,换成新 message 时自动恢复
    val message = state.message
    var dismissedMessage by remember(message) { mutableStateOf<String?>(null) }

    // 悬浮底栏压在内容上方:列表自己把底栏实高留出来,最后一张卡不会被底栏盖住
    val barInset = LocalBottomBarInset.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = barInset + 14.dp),
    ) {
        item { PointsHeader(state, onRefresh) }
        // 与选课页一致:没开背景图时给标题补一道分割线
        if (!bgEnabled) item { HeaderDivider() }
        if (message != null && dismissedMessage != message) {
            item {
                MessageBanner(message, bgEnabled, glassBackdrop) { dismissedMessage = message }
            }
        }
        if (!state.loggedIn) {
            item { LoginIntroCard(state, secondaryColor, bgEnabled, glassBackdrop) }
            item {
                LoginFormCard(
                    state = state,
                    secondaryColor = secondaryColor,
                    bgEnabled = bgEnabled,
                    backdrop = glassBackdrop,
                    onPhoneChange = onPhoneChange,
                    onSmsCodeChange = onSmsCodeChange,
                    onCaptchaInputChange = onCaptchaInputChange,
                    onRefreshCaptcha = onRefreshCaptcha,
                    onSendSms = onSendSms,
                    onLogin = onLogin,
                )
            }
        } else {
            item { RunCard(state, secondaryColor, bgEnabled, glassBackdrop, onRunAll) }
            item { MissionCard(state, dividerColor, secondaryColor, bgEnabled, glassBackdrop) }
            item { LogCard(state, dividerColor, secondaryColor, bgEnabled, glassBackdrop) }
        }
        // 底部提示条:自动运行说明 + 两个开关。开关只是本机设置,未登录时也可以先设好
        item {
            AutoCard(
                state = state,
                dividerColor = dividerColor,
                secondaryColor = secondaryColor,
                bgEnabled = bgEnabled,
                backdrop = glassBackdrop,
                onToggleAutoDaily = onToggleAutoDaily,
                onToggleAutoDeduct = onToggleAutoDeduct,
            )
        }
    }
}

/** 顶栏:标题 + 右侧积分数字(可用 / 累计) + 刷新 */
@Composable
private fun PointsHeader(state: PointsUiState, onRefresh: () -> Unit) {
    val headerColor = LocalHeaderContentColor.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("积分任务", headerColor)
        Spacer(Modifier.weight(1f))
        // 两个小字:可用积分在上(强调),累计积分在下
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "可用积分 ${state.validScore}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = headerColor ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "累计积分 ${state.totalScore}",
                fontSize = 11.sp,
                color = headerColor?.copy(alpha = 0.85f) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(6.dp))
        HeaderIconAction(label = "刷新", onClick = onRefresh, enabled = !state.loading) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** 顶部可关闭提示(state.message) */
@Composable
private fun MessageBanner(
    text: String,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
        // 提示条用主色容器色整块换色,通透度沿用同一套衬底配方
        tintOverride = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                fontSize = 12.sp,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭提示",
                tint = fg.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(ChipShape)
                    .clickable(onClick = onDismiss)
                    .padding(6.dp)
                    .size(16.dp),
            )
        }
    }
}

/** 未登录时的说明卡:讲清积分通道是另一套登录,不影响直饮水 */
@Composable
private fun LoginIntroCard(
    state: PointsUiState,
    secondaryColor: Color,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
) {
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "积分通道需要单独登录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "积分任务走慧生活798 的积分通道(小程序口),要用手机号 + 短信验证码单独登录一次;" +
                    "这里只换取积分 token,直饮水设备控制不受影响。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = secondaryColor,
            )
            if (state.hint.isNotBlank()) {
                Text(
                    state.hint,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 未登录时的登录表单:手机号 + 图形验证码(可点图刷新) + 短信验证码 + 发送 / 登录 */
@Composable
private fun LoginFormCard(
    state: PointsUiState,
    secondaryColor: Color,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onCaptchaInputChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
) {
    // 进页面先取一张图形码:短信前必须填,没图用户直接卡死在这一步
    LaunchedEffect(Unit) {
        if (state.captchaBmp == null && !state.loggedIn) onRefreshCaptcha()
    }
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "登录积分通道",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                label = { Text("手机号") },
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.captchaInput,
                    onValueChange = onCaptchaInputChange,
                    label = { Text("图形验证码") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.weight(1f),
                )
                // 图纸固定占位:没拉到时点一下就能取,别让输入框孤零零挂在那里
                Spacer(Modifier.width(10.dp))
                val bmp = state.captchaBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "图形验证码,点击刷新",
                        modifier = Modifier
                            .size(width = 92.dp, height = 56.dp)
                            .clip(ChipShape)
                            .clickable(onClick = onRefreshCaptcha),
                    )
                } else {
                    Box(
                        Modifier
                            .size(width = 92.dp, height = 56.dp)
                            .clip(ChipShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable(onClick = onRefreshCaptcha),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (state.loading) "加载中" else "点击获取",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.smsCode,
                    onValueChange = onSmsCodeChange,
                    label = { Text("短信验证码") },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onSendSms,
                    enabled = state.smsCooldown <= 0 && !state.loading,
                    shape = CapsuleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(
                        if (state.smsCooldown > 0) "重发(${state.smsCooldown}s)" else "发送验证码",
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onLogin,
                enabled = !state.loading,
                shape = CapsuleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录积分通道", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(
                "验证码短信由慧生活798 发出;token 约 4~5 天过期,过期后回到本页重新登录即可",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = secondaryColor,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** 已登录时的宽胶囊运行按钮:未运行 = 开始运行,running = 点击停止(onRunAll 即切换) */
@Composable
private fun RunCard(
    state: PointsUiState,
    secondaryColor: Color,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    onRunAll: () -> Unit,
) {
    // 运行中用错误色提示"再点会中断",静止态用主题主色
    val runColors = if (state.running) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
    ) {
        Column(Modifier.padding(14.dp)) {
            Button(
                onClick = onRunAll,
                shape = CapsuleShape,
                colors = runColors,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (state.running) "◎ 运行中,点击停止" else "开始运行",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (state.running && state.runningName.isNotBlank()) {
                    "正在执行:${state.runningName}"
                } else {
                    "按列表顺序把今天还没做完的任务做完;每步间隔约 30 秒(服务端频控),中途可随时点上面的按钮停下"
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = secondaryColor,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 任务列表卡:每行任务名 + 单次分值 / 上限,右侧状态;完成的整行文字变淡 */
@Composable
private fun MissionCard(
    state: PointsUiState,
    dividerColor: Color,
    secondaryColor: Color,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
) {
    val missions = state.missions
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "任务列表 (${missions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (missions.isNotEmpty()) {
                    Text(
                        "今日已完成 ${state.doneCount}/${missions.size}",
                        fontSize = 11.sp,
                        color = secondaryColor,
                    )
                }
            }
            HorizontalDivider(color = dividerColor)
            if (missions.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (state.loading) "正在拉取任务列表…" else "暂无任务,点右上角刷新试试",
                        fontSize = 11.sp,
                        color = secondaryColor,
                    )
                }
            } else {
                missions.forEachIndexed { i, mission ->
                    MissionRow(mission, state.signedToday, secondaryColor)
                    if (i != missions.lastIndex) {
                        HorizontalDivider(
                            color = dividerColor,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionRow(
    mission: IlifeClient.Mission,
    signedToday: Boolean,
    secondaryColor: Color,
) {
    // 签到行看今天有没有签到,其余行看 done >= limit
    val finished = if (mission.dailySignin) signedToday else mission.finished
    val nameColor = if (finished) secondaryColor else MaterialTheme.colorScheme.onSurface
    val statusText = when {
        mission.dailySignin -> if (signedToday) "已签到" else "未签到"
        mission.finished -> "已完成 (${mission.done}/${mission.limit})"
        else -> "待完成"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                mission.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (finished) FontWeight.Normal else FontWeight.Medium,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (mission.dailySignin) "每日一次 · +${mission.score} 分"
                else "每次 +${mission.score} 分 · 上限 ${mission.limit} 次",
                fontSize = 11.sp,
                color = if (finished) secondaryColor.copy(alpha = 0.7f) else secondaryColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            statusText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
            modifier = Modifier
                .clip(CapsuleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** 运行日志卡:标题右侧复制全部日志;正文等宽 11sp,只留最近 MAX_LOG_LINES 条 */
@Composable
private fun LogCard(
    state: PointsUiState,
    dividerColor: Color,
    secondaryColor: Color,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
) {
    val clipboard = LocalClipboardManager.current
    val logs = state.logs
    val shown = if (logs.size > MAX_LOG_LINES) logs.takeLast(MAX_LOG_LINES) else logs
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "运行日志",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                // 复制的是全部日志(不只屏幕上这 8 条);卡片里不用阴影,免得玻璃上浮一圈光
                HeaderIconAction(
                    label = "复制",
                    onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) },
                    enabled = logs.isNotEmpty(),
                    shadow = false,
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(color = dividerColor)
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (shown.isEmpty()) {
                    Text(
                        "暂无日志;运行一次后会在这里逐条显示",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    shown.forEach { line ->
                        Text(
                            line,
                            style = LogTextStyle,
                            color = secondaryColor,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        )
                    }
                    if (logs.size > shown.size) {
                        Text(
                            "只显示最近 ${shown.size} 条,点右上角复制可取全部",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 底部:自动运行说明 + 两个开关行 */
@Composable
private fun AutoCard(
    state: PointsUiState,
    dividerColor: Color,
    secondaryColor: Color,
    bgEnabled: Boolean,
    backdrop: Backdrop?,
    onToggleAutoDaily: (Boolean) -> Unit,
    onToggleAutoDeduct: (Boolean) -> Unit,
) {
    GlassCard(
        bgEnabled = bgEnabled,
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = SmallCardShape,
    ) {
        Column {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
                Text(
                    "自动运行",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "两个开关都只保存到本机设置;每日自动运行不需要保持 App 在前台。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = secondaryColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HorizontalDivider(color = dividerColor)
            SwitchRow(
                title = "每日自动运行积分任务",
                desc = "每天首次打开 App 时自动跑一遍当天任务",
                checked = state.autoDaily,
                secondaryColor = secondaryColor,
                onCheckedChange = onToggleAutoDaily,
            )
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 14.dp))
            SwitchRow(
                title = "自动积分抵扣",
                desc = "出水结算时优先用积分抵扣(同步慧生活的 useScore 设置)",
                checked = state.autoDeduct,
                secondaryColor = secondaryColor,
                onCheckedChange = onToggleAutoDeduct,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    secondaryColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        // 整行可点:Switch 自己不吃点击(onCheckedChange = null),避免点一下切换两次
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = secondaryColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
