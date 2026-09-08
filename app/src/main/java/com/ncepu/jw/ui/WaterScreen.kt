package com.ncepu.jw.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncepu.jw.data.Course

/** 饮水机数据(由 MainActivity 传入的状态) */
data class WaterUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val captchaKey: String = "",
    val captchaBmp: androidx.compose.ui.graphics.ImageBitmap? = null,
    val smsSent: Boolean = false,
    val devices: List<Triple<String, String, Boolean>> = emptyList(), // did, name, running
)

/** 饮水机页:短信登录 → 设备列表 → 一键开关水(onBack=null 时为底栏内嵌,无返回键) */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WaterScreen(
    state: WaterUiState,
    phone: String,
    smsCode: String,
    captchaInput: String,
    smsCooldown: Int = 0,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onCaptchaInputChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onRefreshDevices: () -> Unit,
    onStartDevice: (String) -> Unit,
    onEndDevice: (String) -> Unit,
    onAddDevice: (String, String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onScan: () -> Unit,
    scanResult: String? = null,
    onBack: (() -> Unit)? = null,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // 扫码返回:自动弹出添加对话框并回填设备编号
    LaunchedEffect(scanResult) {
        if (!scanResult.isNullOrBlank()) showAddDialog = true
    }
    // 透明 Scaffold/顶栏:底栏 tab 内嵌时透出自定义背景
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("饮水机") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.loggedIn) {
                // ---------- 登录 ----------
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp),
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = { Text("手机号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 图形验证码
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = captchaInput,
                            onValueChange = onCaptchaInputChange,
                            label = { Text("图形验证码") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .width(110.dp)
                                .height(52.dp)
                                .clickable { onRefreshCaptcha() },
                            contentAlignment = Alignment.Center,
                        ) {
                            val bmp = state.captchaBmp
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "图形验证码,点击刷新",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text("点击加载", fontSize = 12.sp)
                            }
                        }
                    }
                    // 短信验证码
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = onSmsCodeChange,
                            label = { Text("短信验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onSendSms,
                            enabled = smsCooldown <= 0 && !state.loading &&
                                phone.length == 11 && captchaInput.isNotBlank(),
                        ) {
                            Text(if (smsCooldown > 0) "重发(${smsCooldown}s)" else "发送")
                        }
                    }
                    Button(
                        onClick = onLogin,
                        enabled = !state.loading && phone.length == 11 && smsCode.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(48.dp),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("登录(慧生活798)")
                        }
                    }
                    state.message?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Text(
                        "登录慧生活798(校园直饮水服务)\n需要接收短信验证码;凭据仅保存在本机",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            } else {
                // ---------- 设备列表 ----------
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("我的设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onScan, enabled = !state.loading) {
                        Text("扫码")
                    }
                    TextButton(onClick = { showAddDialog = true }, enabled = !state.loading) {
                        Text("添加")
                    }
                    TextButton(onClick = onRefreshDevices, enabled = !state.loading) {
                        Text("刷新")
                    }
                }
                if (state.devices.size > 1) {
                    Text(
                        "长按卡片即可拖动调整顺序",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                state.message?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.loading && state.devices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.devices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无收藏设备\n请先在慧生活798 App 中扫码绑定饮水机",
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }
                } else {
                    val lazyListState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        onReorder(from.index, to.index)
                    }
                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.devices, key = { _, d -> d.first }) { _, (did, name, running) ->
                            ReorderableItem(reorderState, key = did) { isDragging ->
                                Box(
                                    Modifier
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            if (isDragging) { scaleX = 1.03f; scaleY = 1.03f }
                                        }
                                        .longPressDraggableHandle(),
                                ) {
                                    Card(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = if (isDragging) 8.dp else 0.dp,
                                        ),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.DragHandle,
                                                contentDescription = "长按拖动排序",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                            Icon(
                                                Icons.Filled.WaterDrop,
                                                contentDescription = null,
                                                tint = if (running) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                            )
                                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                                Text(
                                                    name.ifBlank { "饮水设备" },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Text(
                                                    "编号 $did",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                )
                                            }
                                            Text(
                                                if (running) "结束出水" else "出水",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                    )
                                                    .clickable {
                                                        if (running) onEndDevice(did) else onStartDevice(did)
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                        Text(
                                            "移除",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .align(Alignment.End)
                                                .clickable { onRemoveDevice(did) }
                                                .padding(horizontal = 16.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddDeviceDialog(
                    initialDid = scanResult,
                    onScan = {
                        // 关闭对话框 → 进扫码页;扫完 LaunchedEffect 会再次弹窗回填
                        showAddDialog = false
                        onScan()
                    },
                    onDismiss = { showAddDialog = false },
                    onConfirm = { did, name ->
                        showAddDialog = false
                        onAddDevice(did.trim(), name.trim())
                    },
                )
            }
        }
    }
}

@Composable
private fun AddDeviceDialog(
    initialDid: String? = null,
    onScan: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    // keyed by initialDid:扫码回填时重新初始化
    var did by remember(initialDid) { mutableStateOf(initialDid ?: "") }
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加设备") },
        text = {
            Column {
                OutlinedTextField(
                    value = did,
                    onValueChange = { did = it },
                    label = { Text("设备编号(did)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("备注名称(可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "设备编号可在机身二维码中查看",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onScan) { Text("扫码") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (did.isNotBlank()) onConfirm(did, name) }, enabled = did.isNotBlank()) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
