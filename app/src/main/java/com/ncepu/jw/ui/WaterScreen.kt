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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.kyant.backdrop.Backdrop
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
import androidx.compose.ui.graphics.Shadow
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
private const val FOCUS_HIGHLIGHT_MS = 3000L   // 扫码定位后高亮保持时长

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
    /** 扫码命中序号:同一台设备重复扫也要重新定位(见 AppViewModel.setWaterScan) */
    scanTick: Int = 0,
    /**
     * 扫码信号处理完的回调:调用方应清掉 VM 里的待处理扫码(一次性信号)。
     * 不清的话,离开饮水页再回来 LaunchedEffect 会拿着同一个 scanResult 重跑,设备又被高亮一次。
     */
    onScanConsumed: () -> Unit = {},
    /** 拖拽设备卡片时通知外层收起底栏,给底部删除条腾位置 */
    onDragActiveChange: (Boolean) -> Unit = {},
    /** 是否有背景图:决定设备卡片的毛玻璃采样与通透度(与课表/选课页同一套) */
    bgEnabled: Boolean = false,
    glassBackdrop: Backdrop? = null,
    onBack: (() -> Unit)? = null,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // 待添加的设备编号(扫码带过来的)。不放 VM:VM 里的扫码信号是一次性的,处理完就清
    var addDid by remember { mutableStateOf<String?>(null) }
    // 设备列表的滚动状态提到这里:扫码命中已有设备时要滚到那一张(见下面的 focusedDid)
    val deviceListState = rememberLazyListState()
    // 扫码命中列表里已有的设备时高亮那一张,FOCUS_HIGHLIGHT_MS 后自动褪去
    var focusedDid by remember { mutableStateOf<String?>(null) }
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
    // 扫码返回:列表里已经有这台 → 不弹添加框,直接滚到它并高亮(重复码再问一次"添加"很多余);
    // 列表里没有 → 照旧弹添加对话框并回填设备编号。
    // 键里带 scanTick:同一台设备连扫两次时 scanResult 的值没变,靠它重新触发定位。
    LaunchedEffect(scanResult, scanTick) {
        val code = scanResult?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (state.devices.any { it.first == code }) {
            focusedDid = code
        } else {
            addDid = code
            showAddDialog = true
        }
        // 一次性信号:处理完就让 VM 清掉。否则离开饮水页再回来,effect 会拿同一个 scanResult
        // 重跑一遍 —— 设备被重复高亮、添加框被重复弹出,都是这么来的
        onScanConsumed()
    }
    // 定位:滚到那一张(带回弹动画),高亮一段时间后自动褪去
    LaunchedEffect(focusedDid) {
        val did = focusedDid ?: return@LaunchedEffect
        state.devices.indexOfFirst { it.first == did }
            .takeIf { it >= 0 }
            ?.let { deviceListState.animateScrollToItem(it) }
        kotlinx.coroutines.delay(FOCUS_HIGHLIGHT_MS)
        focusedDid = null
    }
    // 透明 Scaffold/顶栏:底栏 tab 内嵌时透出自定义背景
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 开了背景图时顶栏颜色由 Palette 定(见 HeaderPalette):标题、返回键、右上角图标一起跟着走
            val headerColor = LocalHeaderContentColor.current
            TopAppBar(
                // 与选课页 SectionTitle 同款:加粗 + 一道很淡的文字阴影,背景图花哨时也不会被吃掉
                title = {
                    Text(
                        "饮水机",
                        style = MaterialTheme.typography.titleLarge.copy(
                            shadow = Shadow(
                                color = headerHaloColor(headerColor),
                                offset = Offset(0f, 1.5f),
                                blurRadius = 6f,
                            ),
                        ),
                        fontWeight = FontWeight.Bold,
                        color = headerColor ?: MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                // 与课表页右上角同款:图标在上、文字在下,点整块区域都能触发。
                // 刻意不给 tint:让图标取当前内容色,HeaderIconAction 的深色阴影副本才会跟着变深。
                // 登录页(未登录时)不显示这两个动作:还没登录时没有设备列表可刷,也扫不了设备
                actions = {
                    if (state.loggedIn) {
                        HeaderIconAction(
                            label = "扫码",
                            onClick = onScan,
                            enabled = !state.loading,
                        ) {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = "扫码添加设备",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        HeaderIconAction(
                            label = "刷新",
                            onClick = onRefreshDevices,
                            enabled = !state.loading,
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "刷新设备列表",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                // 只有开了背景图(headerColor != null)时才接管颜色,其余情况保持 Material 默认值
                colors = if (headerColor != null) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = headerColor,
                        actionIconContentColor = headerColor,
                        navigationIconContentColor = headerColor,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                },
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
            // 与选课页一致:没开背景图时,标题下补一道分割线
            if (!bgEnabled) HeaderDivider()
            if (!state.loggedIn) {
                // ---------- 登录 ----------
                // 登录表单也做成液态玻璃卡片:垫在背景图上折射 + 透镜边缘,和悬浮底栏同一套配方;
                // 没开背景图时 glassSurface 会退回普通衬底(没有可折射的东西)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                        .padding(top = 12.dp)
                        .padding(bottom = LocalBottomBarInset.current + 12.dp)
                        .glassSurface(
                            bgEnabled = bgEnabled,
                            backdrop = glassBackdrop,
                            shape = CardShape,
                            liquid = true,
                        )
                        .padding(16.dp),
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
                    // 层级:顶栏「饮水机」= titleLarge(页面标题),这里「我的设备」降到 titleMedium(区块标题)。
                    // 间距只走 4/24:段与段之间 24dp(顶栏 -> 我的设备),同段内 4dp(我的设备 -> 提示 / 提示 -> 列表)
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 阴影与选课页 SectionTitle 同款(淡阴影);扫码/刷新已挪到顶栏。
                    // 颜色按这一行自己所在的高度带(顶栏正下方)从背景图判,顶部 scrim 到这里还没完全散掉
                    SectionTitle(
                        "我的设备",
                        color = rememberSectionTextColor(0.05f, 0.16f),
                        style = MaterialTheme.typography.titleMedium,
                        weight = FontWeight.SemiBold,
                    )
                }
                if (state.devices.size > 1) {
                    Text(
                        "长按卡片可拖动排序;拖到屏幕底部红色区域可删除",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                state.message?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                }
                if (state.loading && state.devices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.devices.isEmpty()) {
                    EmptyState(
                        Icons.Filled.WaterDrop,
                        "暂无设备",
                        "点右上角扫码图标,对着机身二维码扫一下即可添加",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 长按卡片即可拖动排序;拖到屏幕底部的红色删除条上松手 = 移除该设备
                    // 列表状态用上面提升出来的 deviceListState:扫码定位要跨这个分支滚动
                    val lazyListState = deviceListState
                    // 震动走 View.performHapticFeedback:系统"触摸反馈"开关生效,也不需要 VIBRATE 权限
                    val haptic = LocalHapticFeedback.current
                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        onReorder(from.index, to.index)
                    }
                    // 悬浮底栏压在内容上:把底栏实高留出来,最后一张设备卡才不会被底栏盖住
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomBarInset.current + 8.dp),
                    ) {
                        itemsIndexed(state.devices, key = { _, d -> d.first }) { index, (did, name, running) ->
                            ReorderableItem(reorderState, key = did) { isDragging ->
                                val armed = draggingDid == did && overDelete
                                // 没背景图时不再"一设备一浮卡":卡片首尾相连成一块面板,设备之间用分割线分
                                // (与选课页的结果列表同一套);有背景图时保留浮卡 + 毛玻璃的观感
                                val flatRow = !bgEnabled
                                val rowShape = if (flatRow) RoundedCornerShape(
                                    topStart = if (index == 0) 20.dp else 0.dp,
                                    topEnd = if (index == 0) 20.dp else 0.dp,
                                    bottomStart = if (index == state.devices.lastIndex) 20.dp else 0.dp,
                                    bottomEnd = if (index == state.devices.lastIndex) 20.dp else 0.dp,
                                ) else RoundedCornerShape(16.dp)
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
                                        },
                                ) {
                                    Card(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = if (flatRow) 0.dp else 6.dp,
                                            )
                                            // 卡片衬底交给 glassSurface(它要画在 Card 自己的 surface 之下),
                                            // 所以下面把 containerColor 设成透明
                                            .glassSurface(
                                                bgEnabled = bgEnabled,
                                                backdrop = glassBackdrop,
                                                shape = rowShape,
                                                tintOverride = if (armed) MaterialTheme.colorScheme.errorContainer else null,
                                            )
                                            .then(
                                                // 0dp 宽的 border 不是"不画":Skia 把 strokeWidth=0 当 hairline,
                                                // 常态会留一圈 1px 红框。不 armed 时干脆不挂描边。
                                                // 描边挂在衬底之后,否则会被半透明衬底盖掉一半
                                                if (armed) Modifier.border(
                                                    BorderStroke(2.dp, deleteRed()),
                                                    rowShape,
                                                ) else if (focusedDid == did) Modifier.border(
                                                    // 扫码定位到的那台:一圈主色描边,列表再长也能一眼找到
                                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                                    rowShape,
                                                ) else Modifier
                                            ),
                                        shape = rowShape,
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = if (isDragging || armed) 8.dp else 0.dp,
                                        ),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    ) {
                                        Row(
                                            // 左内边距让给手柄的 48dp 热区,右侧与原来的 16dp 一致
                                            Modifier.fillMaxWidth()
                                                .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // 长按"非按钮区域"即可拖动:热区包住手柄、名称与编号,
                                            // 「出水」按钮是它的兄弟节点,所以在按钮上长按不会起拖拽
                                            Row(
                                                Modifier
                                                    .weight(1f)
                                                    .longPressDraggableHandle(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // 手柄保留成视觉提示;它本身不再是唯一热区
                                                Box(
                                                    Modifier.size(48.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Filled.DragHandle,
                                                        contentDescription = "长按拖动排序",
                                                        tint = MaterialTheme.colorScheme.outline,
                                                    )
                                                }
                                                // 出水中让水滴呼吸:光靠 tint 变色在远处/强光下看不出来
                                                DropIcon(running)
                                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                                    Text(
                                                        name.ifBlank { "饮水设备" },
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium,
                                                        // Card 的 containerColor 改成透明后 contentColor 不再兜底,显式给色
                                                        color = if (armed) MaterialTheme.colorScheme.onErrorContainer
                                                        else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    Text(
                                                        when {
                                                            armed -> "松手即从本应用移除"
                                                            focusedDid == did -> "已定位 · 编号 $did"
                                                            else -> "编号 $did"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = when {
                                                            armed -> MaterialTheme.colorScheme.error
                                                            focusedDid == did -> MaterialTheme.colorScheme.primary
                                                            else -> MaterialTheme.colorScheme.outline
                                                        },
                                                    )
                                                }
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
                            // 设备之间的分割线:与标题下、选课结果卡里同色且同长(见 HeaderDivider)。
                            // 放在 ReorderableItem 之外,拖动某张卡时它不会跟着动
                            // 这里在 ReorderableItem 之外,拿不到它内部的 flatRow,直接用函数参数判断
                            if (!bgEnabled && index != state.devices.lastIndex) HeaderDivider()
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
                    initialDid = addDid,
                    onScan = {
                        // 关闭对话框 → 进扫码页;扫完 LaunchedEffect 会再次弹窗回填
                        showAddDialog = false
                        onScan()
                    },
                    onDismiss = {
                        showAddDialog = false
                        addDid = null
                    },
                    onConfirm = { did, name ->
                        showAddDialog = false
                        addDid = null
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
    // 编号只来自扫码:直接取扫码结果,不再提供手动输入(避免绕过扫码添加)
    val did = initialDid.orEmpty()
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加设备") },
        text = {
            Column {
                OutlinedTextField(
                    value = did.ifBlank { "(没读到编号)" },
                    onValueChange = {},
                    readOnly = true,
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
                        "编号来自刚扫的机身码,换设备请重新扫码",
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
