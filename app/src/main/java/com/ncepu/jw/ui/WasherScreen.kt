package com.ncepu.jw.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.SavedWasher
import com.ncepu.jw.data.UjingClient
import com.ncepu.jw.data.WasherModel
import com.ncepu.jw.data.WasherOrderInfo

/** 洗衣页状态(MainActivity 持有) */
data class WasherUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val smsSent: Boolean = false,
    val scannedDevice: String? = null,       // deviceId(扫码/手输后)
    val deviceSummary: String = "",          // 设备名/门店摘要
    val models: List<WasherModel> = emptyList(),          // 洗涤模式(含各模式加购组)
    val selectedModelId: Int? = null,        // 当前选中的洗涤模式
    // 选项组(温度/筒自洁/洗衣液/除菌液)选中:key → 档位 id(null=不添加/未选)
    val selectedAdditions: Map<String, Int?> = emptyMap(),
    val autoStartAfterPay: Boolean = false,               // 支付成功后自动启动洗衣机
    val selfCleanOrdered: Boolean = false,                // 本单是否含筒自洁(自洁完成后需再启动)
    val currentOrder: WasherOrderInfo? = null,
    val payUrl: String = "",
    val savedWashers: List<com.ncepu.jw.data.SavedWasher> = emptyList(),
    val isDryer: Boolean = false,             // 当前设备是烘干机
    val dryTimeMinutes: Int = 60,             // 烘干机分计时时长(分钟)
)

/** 洗衣页:登录 → 扫码/输设备号 → 模式/温度/加购下单 → 支付 → 启动/状态 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WasherScreen(
    state: WasherUiState,
    phone: String,
    smsCode: String,
    smsCooldown: Int = 0,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onScanOrInput: (String) -> Unit,
    onSelectSaved: (String) -> Unit,
    onSelectModel: (Int) -> Unit,
    onSelectAddition: (String, Int?) -> Unit,
    onCreateOrder: () -> Unit,
    onPay: () -> Unit,
    onRefreshOrder: () -> Unit,
    onStartWash: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit = {},
    onRemoveWasher: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onDryTimeChange: (Int) -> Unit = {},
    onSetWasherNote: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    var noteTarget by remember { mutableStateOf<SavedWasher?>(null) }   // 正在改备注的设备
    noteTarget?.let { target ->
        var text by remember(target.did) { mutableStateOf(target.note) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("设备备注") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(20) },
                    label = { Text("如:三教2楼 / 南院1号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetWasherNote(target.did, text.trim())
                    noteTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text("取消") } },
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isDryer) "U净烘干" else "U净洗衣") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.message?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (!state.loggedIn) {
                // ---------- 登录 ----------
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = { Text("手机号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = smsCode,
                        onValueChange = onSmsCodeChange,
                        label = { Text("短信验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onSendSms,
                        enabled = smsCooldown <= 0 && !state.loading && phone.length == 11,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(if (smsCooldown > 0) "重新发送(${smsCooldown}s)" else "发送验证码")
                    }
                    Button(
                        onClick = onLogin,
                        enabled = !state.loading && phone.length == 11 && smsCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("登录 U净")
                        }
                    }
                    Text(
                        "使用慧生活/U净 App 同一手机号登录;\n验证码短信由 U净官方发送",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            } else {
                // ---------- 业务流 ----------
                val selectedModel = state.models.firstOrNull { it.id == state.selectedModelId }
                LazyColumn(Modifier.fillMaxSize()) {
                    // 1. 扫码/输设备号
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("1. 添加设备(洗衣机/烘干机)", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                InputDeviceRow(onScanOrInput, onScan)
                                if (state.scannedDevice != null) {
                                    Text(
                                        "设备 ${state.scannedDevice} ${state.deviceSummary}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    // 已保存的洗衣机(点选即用)
                    if (state.savedWashers.isNotEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("我的设备", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    state.savedWashers.forEach { saved ->
                                        val selected = saved.did == state.scannedDevice
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                                )
                                                .border(
                                                    if (selected) 1.5.dp else 0.8.dp,
                                                    if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant,
                                                    RoundedCornerShape(12.dp),
                                                )
                                                .clickable { onSelectSaved(saved.did) }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        // 优先显示用户备注,否则设备号/编号
                                                        saved.note.ifBlank { saved.name.ifBlank { saved.did } },
                                                        fontSize = 13.sp,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    if (saved.dryer) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            "烘干", fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "编号 " + (saved.name.ifBlank { saved.did }),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    // 占用徽标:只反映自己在这台上的进行中单(使用中),否则空闲
                                                    val busy = saved.status == "使用中" || saved.status == "忙碌"
                                                    Text(
                                                        if (busy) "使用中" else "空闲",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (busy) MaterialTheme.colorScheme.error
                                                        else MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            Text(
                                                "备注",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clickable { noteTarget = saved }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                            Text(
                                                "删除",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .clickable { onRemoveWasher(saved.did) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                    // 2~4. 设备相关配置:切换不同洗衣机时,整块做淡入+水平滑动过渡
                    item(key = "dev-config") {
                        AnimatedContent(
                            targetState = state.scannedDevice ?: "",
                            label = "washer-device-switch",
                            transitionSpec = {
                                (slideInHorizontally(tween(260)) { full -> full / 6 } + fadeIn(tween(260)))
                                    .togetherWith(fadeOut(tween(120)))
                            },
                        ) {
                            Column {
                                // 2. 程序(接口按设备返回,洗衣机=洗涤模式/烘干机=烘干程序)
                                if (state.models.isNotEmpty()) {
                                    OrderSection(
                                        title = if (state.isDryer) "2. 烘干程序" else "2. 洗涤模式",
                                        tail = if (state.isDryer) "请选择烘干程序" else "请选择洗涤模式",
                                    ) {
                                        state.models.chunked(4).forEach { rowModels ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                rowModels.forEach { m ->
                                                    OptionChip(
                                                        title = m.name,
                                                        subtitle = m.priceText,
                                                        selected = state.selectedModelId == m.id,
                                                        modifier = Modifier.weight(1f),
                                                        onClick = { onSelectModel(m.id) },
                                                    )
                                                }
                                                repeat(4 - rowModels.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                                // 3. 选项组:洗衣液/除菌液/温度/筒自洁…,均由接口 additionDevices/additionParams 动态返回。
                                //    加热机器的模型才带 washTemperatureId 组 → 温度选择只在其上出现;加购型额外给"不添加"。
                                selectedModel?.additions?.forEach { group ->
                                    OrderSection(title = group.name, tail = if (group.purchasable) "可选加购" else "请选择") {
                                        val options: List<Pair<Int?, Pair<String, Int>>> = buildList {
                                            if (group.purchasable) add(null to ("不添加" to 0))
                                            group.options.forEach { add(it.id to (it.name to it.priceFen)) }
                                        }
                                        options.chunked(3).forEach { rowOpts ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                rowOpts.forEach { (optId, ui) ->
                                                    OptionChip(
                                                        title = ui.first,
                                                        subtitle = if (ui.second > 0) "+¥${UjingClient.fen2yuan(ui.second)}" else null,
                                                        selected = state.selectedAdditions[group.key] == optId,
                                                        modifier = Modifier.weight(1f),
                                                        onClick = { onSelectAddition(group.key, optId) },
                                                    )
                                                }
                                                repeat(3 - rowOpts.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                                // 烘干时长(仅烘干机,分计时;下单以 dryTime=分钟×10 提交)
                                if (state.isDryer) {
                                    OrderSection(title = "烘干时长", tail = "分计时计费") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(30, 60, 90, 120).forEach { t ->
                                                OptionChip(
                                                    title = "$t 分",
                                                    subtitle = null,
                                                    selected = state.dryTimeMinutes == t,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { onDryTimeChange(t) },
                                                )
                                            }
                                        }
                                    }
                                }
                                // 4. 下单(预估价 = 模式 + 各选项组所选档位加价)
                                if (selectedModel != null) {
                                    val addFen = selectedModel.additions.sumOf { g ->
                                        g.options.firstOrNull { it.id == state.selectedAdditions[g.key] }?.priceFen ?: 0
                                    }
                                    val estimate = selectedModel.priceFen + addFen
                                    Button(
                                        onClick = onCreateOrder,
                                        enabled = !state.loading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .height(48.dp),
                                    ) {
                                        Text("创建订单 · 预估 ¥${UjingClient.fen2yuan(estimate)}")
                                    }
                                }
                            }
                        }
                    }
                    // 6. 订单与支付
                    state.currentOrder?.let { order ->
                        item {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("订单", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("订单号 ${order.orderId}", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        "状态:${order.statusText}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (state.selfCleanOrdered) {
                                        Text(
                                            "本单含筒自洁:自洁完成后需再次点击「启动洗衣」开始洗涤",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    if (order.payPrice.isNotBlank()) {
                                        Text("金额:${order.payPrice}", fontSize = 13.sp)
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "支付成功后自动启动洗衣机",
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        androidx.compose.material3.Switch(
                                            checked = state.autoStartAfterPay,
                                            onCheckedChange = onAutoStartChange,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (order.remainTimeSeconds > 0) {
                                        Text(
                                            "剩余 ${order.remainTimeSeconds / 60} 分 ${order.remainTimeSeconds % 60} 秒",
                                            fontSize = 13.sp,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (order.status == "35") {
                                        Text(
                                            "筒自洁已完成,请在 5 分钟内点击「启动洗衣」,否则订单会自动取消",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        )
                                    }
                                    Row {
                                        if (order.status == "10") {
                                            Button(onClick = onPay, enabled = !state.loading) {
                                                Text("支付宝支付")
                                            }
                                        }
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = onRefreshOrder, enabled = !state.loading) {
                                            Text("刷新状态")
                                        }
                                        if (UjingClient.canStartWash(order.status)) {
                                            Button(onClick = onStartWash, enabled = !state.loading) {
                                                Text(if (state.isDryer) "启动烘干" else "启动洗衣")
                                            }
                                        }
                                    }
                                    if (state.payUrl.isNotBlank()) {
                                        Text(
                                            "支付参数已生成,如未自动跳转请打开支付宝完成支付",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 下单配置区块(标题 + 右侧灰字 + 内容) */
@Composable
private fun OrderSection(title: String, tail: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(tail, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** 可选项芯片(官方同款观感:圆角卡片,选中主色描边) */
@Composable
private fun OptionChip(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 0.8.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.clickable { onClick() },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private data class WasherAdditionUiOption(val name: String, val priceFen: Int)

@Composable
private fun InputDeviceRow(onScanOrInput: (String) -> Unit, onScan: () -> Unit) {
    var input by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("输二维码内容 / 设备编号") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (input.isNotBlank()) onScanOrInput(input) },
                enabled = input.isNotBlank(),
            ) { Text("识别") }
        }
        TextButton(onClick = onScan, modifier = Modifier.align(Alignment.End)) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("扫码添加")
        }
    }
}
