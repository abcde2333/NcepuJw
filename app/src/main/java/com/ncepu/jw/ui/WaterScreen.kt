package com.ncepu.jw.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.PI
import kotlin.math.sin
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

private const val BOTTOM_BAR_EXIT_MS = 220L   // 与 MainActivity 底栏 exit 动画时长对齐
private const val DELETE_BAR_EXIT_MS = 200     // 删除条退场时长,须与下方 exit 里的 tween 一致
private const val BURST_MS = 1100              // 开水成功动效时长

/** 饮水机数据(由 MainActivity 传入的状态) */
data class WaterUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val captchaKey: String = "",
    val captchaBmp: androidx.compose.ui.graphics.ImageBitmap? = null,
    val smsSent: Boolean = false,
    val devices: List<Triple<String, String, Boolean>> = emptyList(), // did, name, running
    /** 开水成功的一次性事件:tick 递增驱动按钮上的水滴动效+震动,did 决定在哪张卡播放 */
    val successDid: String? = null,
    val successTick: Int = 0,
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
    /** 拖拽设备卡片时通知外层收起底栏,给底部删除条腾位置 */
    onDragActiveChange: (Boolean) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // 拖到删除条松手后先弹确认:拖拽误操作比点击更常见
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    var draggingDid by remember { mutableStateOf<String?>(null) }
    var rootHpx by remember { mutableStateOf(0) }
    var pointerY by remember { mutableStateOf(0f) }
    val deleteBarPx = with(LocalDensity.current) { 104.dp.toPx() }
    val overDelete = draggingDid != null && rootHpx > 0 &&
        pointerY >= rootHpx - deleteBarPx
    // 长按拖拽与底栏/删除条的进出顺序:开始时底栏先滑出→删除条升起;
    // 松手时反过来,删除条先退场再放底栏回来,两条动画不叠在一起。
    // 时长与 MainActivity 底栏动画对齐,改一处要改两处。
    var deleteBarShown by remember { mutableStateOf(false) }
    LaunchedEffect(draggingDid) {
        if (draggingDid != null) {
            onDragActiveChange(true)
            kotlinx.coroutines.delay(BOTTOM_BAR_EXIT_MS)
            deleteBarShown = true
        } else {
            deleteBarShown = false
            kotlinx.coroutines.delay(DELETE_BAR_EXIT_MS.toLong())
            onDragActiveChange(false)
        }
    }
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
        Box(Modifier.padding(padding).fillMaxSize()
            // Initial 通道只"看"事件不消费:排序拖拽由子节点处理,这里只量指针高度
            .onSizeChanged { rootHpx = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull() ?: break
                        pointerY = ch.position.y
                        if (ch.changedToUpIgnoreConsumed()) break
                    }
                }
            }) {
        Column(Modifier.fillMaxSize()) {
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
                    // 图标都取"一看就懂"的形:扫码框 / 加号圆 / 环形箭头,并保留 contentDescription
                    IconButton(onClick = onScan, enabled = !state.loading) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = "扫码添加设备",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }, enabled = !state.loading) {
                        Icon(
                            Icons.Filled.AddCircleOutline,
                            contentDescription = "手动添加设备",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onRefreshDevices, enabled = !state.loading) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新设备列表",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (state.devices.size > 1) {
                    Text(
                        "长按卡片可拖动排序;拖到屏幕底部红色区域可删除",
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
                    EmptyState(
                        Icons.Filled.WaterDrop,
                        "暂无收藏设备",
                        "先在慧生活798 App 里扫码绑定饮水机,再回本页添加",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 长按卡片即可拖动排序;拖到屏幕底部的红色删除条上松手 = 移除该设备
                    val lazyListState = rememberLazyListState()
                    // 震动走 View.performHapticFeedback:系统"触摸反馈"开关生效,也不需要 VIBRATE 权限
                    val haptic = LocalHapticFeedback.current
                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        onReorder(from.index, to.index)
                    }
                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.devices, key = { _, d -> d.first }) { _, (did, name, running) ->
                            ReorderableItem(reorderState, key = did) { isDragging ->
                                val armed = draggingDid == did && overDelete
                                // 松手那一刻指针停在删除条上才弹确认;拖动中途不动数据
                                LaunchedEffect(isDragging) {
                                    if (isDragging) {
                                        // 抓到卡片给一下确认震动:长按没有视觉反馈时用户不知道能不能拖
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingDid = did
                                    } else if (draggingDid == did) {
                                        draggingDid = null
                                        if (deleteBarShown && rootHpx > 0 &&
                                            pointerY >= rootHpx - deleteBarPx
                                        ) {
                                            pendingRemove = did
                                        }
                                    }
                                }
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
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .then(
                                                // 0dp 宽的 border 不是"不画":Skia 把 strokeWidth=0 当 hairline,
                                                // 常态会留一圈 1px 红框。不 armed 时干脆不挂描边
                                                if (armed) Modifier.border(
                                                    BorderStroke(2.dp, deleteRed()),
                                                    RoundedCornerShape(16.dp),
                                                ) else Modifier
                                            ),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = if (isDragging || armed) 8.dp else 0.dp,
                                        ),
                                        colors = if (armed) CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ) else CardDefaults.cardColors(),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.DragHandle,
                                                contentDescription = "长按拖动排序",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                            // 出水中让水滴呼吸:光靠 tint 变色在远处/强光下看不出来
                                            DropIcon(running)
                                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                                Text(
                                                    name.ifBlank { "饮水设备" },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Text(
                                                    if (armed) "松手即从本应用移除" else "编号 $did",
                                                    fontSize = 11.sp,
                                                    color = if (armed) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.outline,
                                                )
                                            }
                                            // 实体胶囊按钮 + 微弱投影:浅色模式下 tonal 底几乎看不出来,
                                            // 而这是本页唯一高频动作
                                            Box(contentAlignment = Alignment.Center) {
                                                Button(
                                                    onClick = {
                                                        if (running) onEndDevice(did) else onStartDevice(did)
                                                    },
                                                    modifier = Modifier.defaultMinSize(minWidth = 100.dp, minHeight = 48.dp),
                                                    // 胶囊:material3 1.3 还没有 Shapes.full token,显式写 50%
                                                    shape = RoundedCornerShape(percent = 50),
                                                    elevation = ButtonDefaults.buttonElevation(
                                                        defaultElevation = 3.dp,
                                                        pressedElevation = 6.dp,
                                                        disabledElevation = 0.dp,
                                                    ),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (running) MaterialTheme.colorScheme.error
                                                        else MaterialTheme.colorScheme.primary,
                                                        contentColor = if (running) MaterialTheme.colorScheme.onError
                                                        else MaterialTheme.colorScheme.onPrimary,
                                                    ),
                                                ) {
                                                    Text(
                                                        if (running) "结束出水" else "出水",
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                                // 开水成功:涟漪+水滴从按钮中心扩一次,和震动同时触发。
                                                // 画在按钮之上(压在按钮背后会被不透明的按钮整个挡住),
                                                // 半径压在卡片裁剪范围以内,否则边缘会被 Card 切出硬边。
                                                WaterStartBurst(
                                                    trigger = if (state.successDid == did) state.successTick else 0,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.matchParentSize(),
                                                )
                                            }
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

            // 删除条:只在长按拖拽时从底部升起,常态不占位
            AnimatedVisibility(
                visible = deleteBarShown,
                enter = fadeIn() + slideInVertically { it / 2 },
                // 退场时长写死成常量:外层的 LaunchedEffect 按它延时再放回底栏
                exit = fadeOut(tween(DELETE_BAR_EXIT_MS)) +
                    slideOutVertically(tween(DELETE_BAR_EXIT_MS)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                val red = deleteRed()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    red.copy(alpha = if (overDelete) 0.55f else 0.22f),
                                    red.copy(alpha = if (overDelete) 0.95f else 0.45f),
                                ),
                            ),
                        )
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // 底边光效:纯竖向渐变带。不用 blur —— 模糊层会被父级裁出硬边,
                    // 在屏幕上就是一条奇怪的色带
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        red.copy(alpha = if (overDelete) 0.60f else 0.30f),
                                    ),
                                ),
                            ),
                    )
                    Row(
                        Modifier
                            .padding(bottom = 18.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (overDelete) red else red.copy(alpha = 0.30f))
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            if (overDelete) "松手移除该设备" else "拖到这里删除",
                            fontSize = 13.sp,
                            fontWeight = if (overDelete) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White,
                            modifier = Modifier.padding(start = 6.dp),
                        )
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

            pendingRemove?.let { did ->
                val label = state.devices.firstOrNull { it.first == did }?.second?.ifBlank { "该设备" }
                    ?: "该设备"
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pendingRemove = null },
                    title = { Text("移除设备") },
                    text = {
                        Text("将从本应用移除「$label」的快捷入口。不影响慧生活798 里的收藏,之后可重新扫码添加。")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingRemove = null
                                onRemoveDevice(did)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("移除") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRemove = null }) { Text("取消") }
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

/** 水滴状态图标:出水中做呼吸动画。单独成 composable 是为了把每帧重组限制在图标,不带着整张卡片重算 */
@Composable
private fun DropIcon(running: Boolean) {
    val pulse = if (!running) 1f else rememberInfiniteTransition(label = "drop").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "dropPulse",
    ).value
    Icon(
        Icons.Filled.WaterDrop,
        contentDescription = if (running) "出水中" else null,
        tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.graphicsLayer {
            alpha = pulse
            scaleX = 0.86f + 0.14f * pulse
            scaleY = scaleX
        },
    )
}

/**
 * 开水成功动效:从"出水"按钮中心扩两圈涟漪 + 三颗上抛水滴,一次性播放约 1.1s。
 * trigger 由外层每次成功 +1;trigger<=0(不是刚成功的那张卡)时不画也不震。
 * 只用 Canvas + Animatable,不引 Lottie:构建走 --offline,多一个依赖就多一次联网风险。
 */
@Composable
private fun WaterStartBurst(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    if (trigger <= 0) return
    val haptic = LocalHapticFeedback.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        p.snapTo(0f)
        p.animateTo(1f, tween(BURST_MS))
        // 归零让常态不占绘制;若中途来了新 trigger,上面这行会被取消,不会走到这里
        p.snapTo(0f)
    }
    // 进度在组合阶段读:每帧只重组这一个叶子 composable,不依赖绘制期的状态观察
    val t = p.value
    Canvas(modifier) {
        if (t <= 0f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        // 两圈错开的涟漪:外扩同时淡出
        listOf(0f, 0.3f).forEach { off ->
            val q = ((t - off) / (1f - off)).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = (1f - q) * 0.5f),
                radius = 10.dp.toPx() + q * 24.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        // 三颗水滴:上抛后回落,末尾淡出
        listOf(-1f, 0f, 1f).forEachIndexed { i, side ->
            val q = (t - i * 0.07f).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = (1f - q) * 0.85f),
                radius = 4.5.dp.toPx() * (1f - q * 0.35f),
                center = Offset(
                    cx + side * 20.dp.toPx(),
                    cy - sin(q * PI.toFloat()) * 22.dp.toPx(),
                ),
            )
        }
    }
}

private val DangerRed = Color(0xFFD32F2F)

private fun Color.mixTowards(other: Color, t: Float): Color = Color(
    red + (other.red - red) * t,
    green + (other.green - green) * t,
    blue + (other.blue - blue) * t,
)

/**
 * 删除区的红。深色模式下 colorScheme.error 是浅鲑红(亮度 > 0.55),半透明叠在暗底上会发灰,
 * 读不出"危险红";这时向固定红压一档。浅色模式的 error 本身就是饱和红,原样用。
 */
@Composable
private fun deleteRed(): Color {
    val base = MaterialTheme.colorScheme.error
    return if (base.luminance() > 0.55f) base.mixTowards(DangerRed, 0.6f) else base
}
