package com.ncepu.jw.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.WasherOrderInfo

/** 洗衣页状态(MainActivity 持有) */
data class WasherUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val smsSent: Boolean = false,
    val scannedDevice: String? = null,       // deviceId(扫码/手输后)
    val deviceSummary: String = "",          // 设备名/门店摘要
    val models: List<Triple<Int, String, String>> = emptyList(), // id, 名称, 价格
    val currentOrder: WasherOrderInfo? = null,
    val payUrl: String = "",                 // 支付宝收银台参数(orderInfo),展示给用户跳转
    val savedWashers: List<Pair<String, String>> = emptyList(), // 已保存洗衣机(did, deviceNo)
)

/** 洗衣页:登录 → 扫码/输设备号 → 选套餐下单 → 支付 → 启动/状态 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasherScreen(
    state: WasherUiState,
    phone: String,
    smsCode: String,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onScanOrInput: (String) -> Unit,
    onSelectModel: (Int, Int) -> Unit,
    onCreateOrder: () -> Unit,
    onPay: () -> Unit,
    onRefreshOrder: () -> Unit,
    onStartWash: () -> Unit,
    onRemoveWasher: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("U净洗衣") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
                        enabled = !state.loading && phone.length == 11,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("发送验证码") }
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
                LazyColumn(Modifier.fillMaxSize()) {
                    // 1. 扫码/输设备号
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("1. 添加洗衣机", fontWeight = FontWeight.Bold)
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
                                    Text("我的洗衣机", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    state.savedWashers.forEach { (did, name) ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                name.ifBlank { did },
                                                Modifier.weight(1f).clickable { onScanOrInput(did) },
                                                color = if (did == state.scannedDevice)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                "删除",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .clickable { onRemoveWasher(did) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 2. 选套餐下单
                    if (state.models.isNotEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("2. 选择洗涤模式并下单", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    state.models.forEach { (id, name, extra) ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectModel(id, id) }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(name, Modifier.weight(1f))
                                            Text(
                                                extra,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                    Button(
                                        onClick = onCreateOrder,
                                        enabled = !state.loading,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    ) { Text("创建订单") }
                                }
                            }
                        }
                    }
                    // 3. 订单与支付
                    state.currentOrder?.let { order ->
                        item {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("3. 订单", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("订单号 ${order.orderId}", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        "状态:${order.statusText}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (order.payPrice.isNotBlank()) {
                                        Text("金额:¥${order.payPrice}", fontSize = 13.sp)
                                    }
                                    if (order.remainTimeSeconds > 0) {
                                        Text(
                                            "剩余 ${order.remainTimeSeconds / 60} 分 ${order.remainTimeSeconds % 60} 秒",
                                            fontSize = 13.sp,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
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
                                        if (order.status == "20") {
                                            Button(onClick = onStartWash, enabled = !state.loading) {
                                                Text("启动洗衣")
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
            Text("📷 扫码添加")
        }
    }
}
