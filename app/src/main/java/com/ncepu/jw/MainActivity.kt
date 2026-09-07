package com.ncepu.jw

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.Grade
import com.ncepu.jw.data.JwClient
import com.ncepu.jw.data.JwException
import com.ncepu.jw.data.IlifeClient
import com.ncepu.jw.data.UjingClient
import okhttp3.OkHttpClient
import org.json.JSONObject
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.ncepu.jw.data.Semester
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.ThemeMode
import com.ncepu.jw.reminder.ReminderScheduler
import com.ncepu.jw.ui.AppBottomBar
import com.ncepu.jw.ui.BackgroundCropScreen
import com.ncepu.jw.ui.ExamScreen
import com.ncepu.jw.ui.GradeScreen
import com.ncepu.jw.ui.LoginScreen
import com.ncepu.jw.ui.ProfileScreen
import com.ncepu.jw.ui.PyfaScreen
import com.ncepu.jw.ui.ScheduleScreen
import com.ncepu.jw.ui.SelectionScreen
import com.ncepu.jw.ui.SettingsScreen
import com.ncepu.jw.ui.WaterScreen
import com.ncepu.jw.ui.WaterScanScreen
import com.ncepu.jw.ui.WaterDeviceIdParser
import com.ncepu.jw.ui.WasherScreen
import com.ncepu.jw.ui.WasherUiState
import com.ncepu.jw.ui.WaterUiState
import com.ncepu.jw.ui.applyBackgroundBlur
import com.ncepu.jw.ui.theme.NcepuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 外观状态(实时驱动 UI) */
data class Appearance(
    val navShape: NavBarShape,
    val navMaterial: NavMaterial,
    val bgHas: Boolean,
    val bgBlur: Float,
    val bgDim: Float,
    val weekStartMillis: Long,
    val bgVersion: Int,
)

class AppViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val client = JwClient()
    val settings = SettingsStore(app)
    private val client2 = OkHttpClient()

    // 提醒设置(状态驱动:设置页改完立即生效,不用退出重进)
    var reminderEnabled by mutableStateOf(settings.reminderEnabled)
    var examReminderEnabled by mutableStateOf(settings.examReminderEnabled)
    var leadMinutes by mutableStateOf(settings.leadMinutes)
    var sectionTimes by mutableStateOf(settings.sectionTimes)
    var scheduleSource by mutableStateOf(settings.scheduleSource) // AUTO/MANUAL(导入的 XLS)

    var account by mutableStateOf("")
    var password by mutableStateOf("")
    var name by mutableStateOf<String?>(null)

    var loginLoading by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var loggedIn by mutableStateOf(false)

    var semesters: List<Semester> = Semester.options(8)
    var schedSem by mutableStateOf(Semester.current())
    var schedLoading by mutableStateOf(false)
    var schedError by mutableStateOf<String?>(null)
    var allCourses by mutableStateOf<List<Course>>(emptyList())  // 学期全量(xskb_list.do)
    var courses by mutableStateOf<List<Course>>(emptyList())     // 当前显示(周视图=本地过滤)
    var schedMode by mutableStateOf("WEEK")      // WEEK=周视图(本地过滤) / ALL=学期全量
    var officialWeek by mutableStateOf(0)        // 官方当前周(教务系统计算,加载时取一次)
    // 注意:以下状态必须在 init 之前声明(Kotlin 按声明顺序初始化,
    // init/其协程会读写它们;声明在 init 之后会导致启动期 NPE 崩溃)
    var schedDiag by mutableStateOf("")

    // ---------- 应用内更新(镜像站加速) ----------
    var updateState by mutableStateOf<com.ncepu.jw.update.Updater.State>(
        com.ncepu.jw.update.Updater.State.Idle
    )
    var selectedWeek by mutableStateOf(0)        // 当前查看的周
    var schedLoaded by mutableStateOf(false)

    var gradeSem by mutableStateOf(Semester.current())
    var gradeLoading by mutableStateOf(false)
    var gradeError by mutableStateOf<String?>(null)
    var grades by mutableStateOf<List<Grade>>(emptyList())

    var selLoading by mutableStateOf(false)
    var selError by mutableStateOf<String?>(null)
    var xkRounds by mutableStateOf<List<com.ncepu.jw.data.XkRound>>(emptyList())
    var selectedCourses by mutableStateOf<List<com.ncepu.jw.data.SelectedCourse>>(emptyList())
    var selectionLoaded by mutableStateOf(false)
    private var selCacheTime by mutableStateOf(0L)

    companion object {
        /** 选课缓存视为过期的时间(超时后进入选课页才再次请求) */
        private const val SEL_STALE_MS = 10 * 60_000L
    }

    var pyfaLoading by mutableStateOf(false)
    var pyfaError by mutableStateOf<String?>(null)
    var pyfa by mutableStateOf<com.ncepu.jw.data.PyfaData?>(null)

    var examSem by mutableStateOf(Semester.current())
    var examLoading by mutableStateOf(false)
    var examError by mutableStateOf<String?>(null)
    var exams by mutableStateOf<List<com.ncepu.jw.data.Exam>>(emptyList())

    // U净洗衣机
    val ujing = UjingClient()
    var washerState by mutableStateOf(WasherUiState())
    var washerPhone by mutableStateOf("")
    var washerSmsCode by mutableStateOf("")
    var washerSmsCooldown by mutableStateOf(0)   // 短信发送冷却(秒)
    private var washerToken by mutableStateOf("")
    private val washerScanned = mutableMapOf<String, Pair<Int, String>>() // deviceId → (deviceTypeId, storeId)

    // 慧生活798 饮水机
    val ilife = IlifeClient()
    var waterState by mutableStateOf(WaterUiState())
    var waterPhone by mutableStateOf("")
    var waterSmsCode by mutableStateOf("")
    var waterCaptchaInput by mutableStateOf("")
    var waterSmsCooldown by mutableStateOf(0)    // 短信发送冷却(秒)
    var waterScanResult by mutableStateOf<String?>(null)
    private var waterToken by mutableStateOf("")
    private var waterCaptchaKey = IlifeClient.newCaptchaKey()
    var skippedLogin by mutableStateOf(false)   // 跳过教务登录(离线/仅用饮水机)

    init {
        // 首屏直出:同步预载(SharedPreferences 读毫秒级),保证首帧之前
        // 课表/选课缓存已就绪——异步预载存在首帧竞态,导入 XLS 后重启尤其明显
        runCatching { fallBackToCache() }
        runCatching {
            settings.loadCachedSelection()?.let { sel ->
                if (xkRounds.isEmpty()) {
                    xkRounds = sel.rounds
                    selectedCourses = sel.selected
                    selectionLoaded = true
                    selCacheTime = sel.time
                }
            }
        }
        // 启动时自动检查更新(12 小时节流,镜像站加速)
        viewModelScope.launch(Dispatchers.IO) { checkForUpdate(force = false) }
        // 冷却倒计时(饮水/洗衣机短信共用)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (waterSmsCooldown > 0) waterSmsCooldown--
                if (washerSmsCooldown > 0) washerSmsCooldown--
            }
        }
    }

    fun addWaterDevice(did: String, name: String) {
        settings.addWaterDevice(did, name)
        waterScanResult = null
        loadWaterDevices()
    }

    fun removeWaterDevice(did: String) {
        settings.removeWaterDevice(did)
        loadWaterDevices()
    }

    fun refreshCaptcha() {
        waterCaptchaKey = IlifeClient.newCaptchaKey()
        val url = ilife.captchaUrl(waterCaptchaKey)
        viewModelScope.launch {
            val bmp: android.graphics.Bitmap? = withContext(Dispatchers.IO) {
                runCatching {
                    val req = okhttp3.Request.Builder().url(url).build()
                    client2.newCall(req).execute().use { r ->
                        r.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
            }
            waterState = waterState.copy(
                captchaKey = waterCaptchaKey,
                captchaBmp = bmp?.asImageBitmap(),
            )
        }
    }

    fun sendWaterSms() {
        if (waterSmsCooldown > 0) return
        waterState = waterState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ilife.sendSms(waterPhone.trim(), waterCaptchaInput.trim(), waterCaptchaKey)
            if (r.ok) waterSmsCooldown = 60
            waterState = waterState.copy(
                loading = false,
                message = if (r.ok) "短信已发送" else "发送失败:${IlifeClient.readable(r.code, r.msg)}",
            )
        }
    }

    fun doWaterLogin() {
        waterState = waterState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ilife.login(waterPhone.trim(), waterSmsCode.trim())
            if (!r.ok) {
                refreshCaptcha()
                waterState = waterState.copy(
                    loading = false,
                    message = "登录失败:${IlifeClient.readable(r.code, r.msg)}",
                )
                return@launch
            }
            waterToken = ilife.extractToken(r.json ?: JSONObject())
            if (waterToken.isBlank()) {
                refreshCaptcha()
                waterState = waterState.copy(
                    loading = false,
                    message = "登录返回异常:未获取到凭证,请重试",
                )
                return@launch
            }
            settings.waterToken = waterToken
            waterSmsCode = ""
            waterCaptchaInput = ""
            waterState = waterState.copy(loggedIn = true, loading = false, message = null)
            loadWaterDevices()
        }
    }

    fun loadWaterDevices(silent: Boolean = false) {
        if (waterToken.isBlank()) waterToken = settings.waterToken
        if (waterToken.isBlank()) {
            waterState = waterState.copy(loggedIn = false)
            refreshCaptcha()
            return
        }
        // silent:列表已上屏的静默刷新,不清 message、不闪 loading
        waterState = waterState.copy(
            loggedIn = true,
            loading = !silent || waterState.devices.isEmpty(),
            message = if (silent) waterState.message else null,
        )
        viewModelScope.launch {
            try {
                // 账号状态刷新:view-info 验证 token(设备平台 1,1)
                val check = ilife.viewInfo(waterToken)
                if (!check.ok) {
                    settings.waterToken = ""
                    waterToken = ""
                    waterState = waterState.copy(
                        loggedIn = false,
                        loading = false,
                        devices = emptyList(),
                        message = "登录已过期,请重新登录",
                    )
                    refreshCaptcha()
                    return@launch
                }
                val accountName = check.json?.optJSONObject("data")?.let { d ->
                    d.optString("name", "").ifBlank { d.optString("nickName", "").ifBlank { d.optString("id", "") } }
                }
                val remote = try { ilife.devices(waterToken) } catch (_: Exception) { emptyList() }
                // 合并手动添加的设备(不覆盖远程)
                val manual = settings.waterDevices.filter { m -> remote.none { it.first == m.first } }
                val all = (remote + manual).ifEmpty {
                    // 远程失败且无手动设备时仍显示手动设备
                    manual
                }
                val merged = all.map { (did, name) ->
                    val prev = waterState.devices.firstOrNull { it.first == did }
                    Triple(did, name, prev?.third ?: false)
                }
                waterState = waterState.copy(
                    devices = merged,
                    loading = false,
                    message = if (merged.isEmpty()) {
                        accountName?.let { "已登录:$accountName · 暂无设备,可手动添加" } ?: "暂无设备,可手动添加"
                    } else null,
                )
            } catch (e: Exception) {
                waterState = waterState.copy(loading = false, message = "加载失败:${e.message}")
            }
        }
    }

    fun washerRequestCaptcha() {
        if (washerSmsCooldown > 0) return
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.requestCaptcha(washerPhone.trim())
            if (r.ok) washerSmsCooldown = 60
            washerState = washerState.copy(
                loading = false,
                message = if (r.ok) "验证码已发送" else "发送失败:" + UjingClient.readable(r.code, r.msg),
            )
        }
    }

    fun doWasherLogin() {
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.login(washerPhone.trim(), washerSmsCode.trim())
            if (r.ok) {
                washerToken = ujing.extractToken(r.json)
                settings.washerToken = washerToken
                washerState = washerState.copy(
                    loggedIn = washerToken.isNotBlank(),
                    loading = false,
                    savedWashers = settings.washerDevices,
                )
            } else {
                washerState = washerState.copy(
                    loading = false,
                    message = "登录失败:" + UjingClient.readable(r.code, r.msg),
                )
            }
        }
    }

    fun washerScan(qrRaw: String) {
        if (washerToken.isBlank()) {
            washerState = washerState.copy(message = "请先登录 U净账号")
            return
        }
        val content = qrRaw.trim()
        if (content.isBlank()) {
            washerState = washerState.copy(message = "二维码内容为空")
            return
        }
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            try {
                // scanWasherCode 必须收到二维码原始内容(服务端自行解析)
                val scan = ujing.scanWasher(washerToken, content)
                if (!scan.ok) {
                    washerState = washerState.copy(loading = false, message = "识别失败:" + UjingClient.readable(scan.code, scan.msg))
                    return@launch
                }
                // scan.json 已是 data 层;设备信息在 data.result
                val result = scan.json?.optJSONObject("result") ?: org.json.JSONObject()
                val deviceId = result.optString("deviceId", "")
                val enabled = result.optBoolean("createOrderEnabled", false)
                val reason = result.optString("reason", "")
                val status = result.optString("status", "")
                if (deviceId.isBlank()) {
                    washerState = washerState.copy(loading = false, message = "未识别到设备编号,请确认扫的是洗衣机机身码")
                    return@launch
                }
                if (!enabled) {
                    washerState = washerState.copy(
                        loading = false,
                        message = "该设备暂不可下单" + (if (reason.isNotBlank()) ":$reason" else "") +
                            (if (status.isNotBlank()) "(状态 $status)" else ""),
                    )
                    return@launch
                }
                // 下单必需的 deviceTypeId 来自扫码结果(非套餐接口)
                val deviceTypeId = result.optInt("deviceTypeId", 0)
                val err = loadWasherProgram(deviceId, deviceTypeId, "", status)
                if (err != null) {
                    washerState = washerState.copy(loading = false, message = err)
                }
            } catch (e: Exception) {
                washerState = washerState.copy(loading = false, message = "识别异常:" + e.message)
            }
        }
    }

    /** 选择已保存的洗衣机:直接拉套餐,不走扫码接口 */
    fun washerSelectSaved(did: String) {
        if (washerToken.isBlank()) {
            washerState = washerState.copy(message = "请先登录 U净账号")
            return
        }
        val saved = settings.washerDevices.firstOrNull { it.did == did }
        washerState = washerState.copy(
            loading = true, message = null, scannedDevice = did,
            models = emptyList(), selectedModelId = null,
            selectedAdditions = emptyMap(),
        )
        viewModelScope.launch {
            try {
                val err = loadWasherProgram(did, saved?.deviceTypeId ?: 0, saved?.storeId ?: "")
                if (err != null) {
                    washerState = washerState.copy(loading = false, message = err)
                }
            } catch (e: Exception) {
                washerState = washerState.copy(loading = false, message = "获取设备信息失败:" + e.message)
            }
        }
    }

    /**
     * 拉取设备套餐并更新选中状态。返回 null=成功,非 null=错误信息。
     * 同时持久化 deviceTypeId/storeId/状态,并探测设备是否忙碌。
     */
    private suspend fun loadWasherProgram(
        deviceId: String,
        knownTypeId: Int,
        knownStoreId: String,
        scannedStatus: String = "",
    ): String? {
        val info = ujing.programInfo(washerToken, deviceId)
        if (!info.ok) {
            return "套餐获取失败:" + UjingClient.readable(info.code, info.msg)
        }
        val storeId = info.json?.optString("storeId", knownStoreId) ?: knownStoreId
        washerScanned[deviceId] = Pair(knownTypeId, storeId)
        // 记住这台设备(含下单必需信息),下次免扫码
        settings.addWasherDevice(deviceId, info.json?.optString("deviceNo", "") ?: "", knownTypeId, storeId, scannedStatus)
        washerState = washerState.copy(savedWashers = settings.washerDevices)
        val models = UjingClient.Parsers.parseModels(info.json)
        val defaultId = UjingClient.Parsers.defaultModelId(models)
        washerState = washerState.copy(
            loading = false,
            scannedDevice = deviceId,
            deviceSummary = (info.json?.optString("storeName", "") ?: "") + " " +
                (info.json?.optString("deviceTypeName", "") ?: ""),
            models = models,
            selectedModelId = defaultId,
            selectedAdditions = models.firstOrNull { it.id == defaultId }
                ?.additions?.associate { it.key to null } ?: emptyMap(),
            selectedTemperatureId = 1,
            message = null,
        )
        return null
    }

    /** 活跃订单状态(30 准备中 / 40 运行中)→ 设备忙碌 */
    private val activeOrderDevices = mutableSetOf<String>()

    fun updateActiveOrder(deviceId: String, status: String) {
        activeOrderDevices.clear()
        if (status == "30" || status == "40") activeOrderDevices.add(deviceId)
        settings.updateWasherStatus(deviceId, if (status == "30" || status == "40") "忙碌" else "空闲")
        washerState = washerState.copy(savedWashers = settings.washerDevices)
    }

    /** 是否使用手动导入的 XLS 课表(开启=不联网刷新;关闭=恢复联网获取) */
    fun setUseImportedXls(enabled: Boolean) {
        scheduleSource = if (enabled) "MANUAL" else "AUTO"
        settings.scheduleSource = if (enabled) "MANUAL" else "AUTO"
        if (enabled) {
            // 立即应用导入的数据
            fallBackToCache()
            schedMode = "WEEK"
            if (selectedWeek <= 0) selectedWeek = officialWeek.coerceAtLeast(1)
        } else if (loggedIn) {
            loadSchedule(force = true)  // 关闭导入,恢复联网获取
        }
    }

    fun washerCreateOrder() {
        val deviceId = washerState.scannedDevice ?: return
        val scanned = washerScanned[deviceId]
            ?: settings.washerDevices.firstOrNull { it.did == deviceId }
                ?.takeIf { it.deviceTypeId > 0 }
                ?.let { Pair(it.deviceTypeId, it.storeId) }
        if (scanned == null) {
            washerState = washerState.copy(message = "请先识别设备")
            return
        }
        val model = washerState.models.firstOrNull { it.id == washerState.selectedModelId }
            ?: washerState.models.firstOrNull { it.id == 1 }
            ?: washerState.models.firstOrNull()
            ?: return
        // 加购:只把"已选档位"(非不添加)放进下单字段(key 即接口返回的 wp_xxx 字段名)
        val extras = model.additions.mapNotNull { g ->
            washerState.selectedAdditions[g.key]?.let { g.key to it }
        }.toMap()
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.createOrder(
                washerToken, deviceId, scanned.first, scanned.second, model.id,
                temperatureId = washerState.selectedTemperatureId,
                extras = extras,
            )
            if (!r.ok) {
                washerState = washerState.copy(loading = false, message = "下单失败:" + UjingClient.readable(r.code, r.msg))
                return@launch
            }
            val orderId = r.json?.optString("orderId", "") ?: ""
            val detail = ujing.orderDetail(washerToken, orderId)
            val order = UjingClient.Parsers.parseOrder(detail.json)
            updateActiveOrder(deviceId, order.status)
            washerState = washerState.copy(loading = false, currentOrder = order)
        }
    }

    fun washerPay(activity: android.app.Activity) {
        val order = washerState.currentOrder ?: return
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.paymentArguments(washerToken, order.orderId)
            val payInfo = r.json?.optJSONObject("payInfo")
            val orderInfo = payInfo?.optString("orderInfo", "") ?: ""
            if (!r.ok || orderInfo.isBlank()) {
                washerState = washerState.copy(
                    loading = false,
                    message = "支付参数获取失败:" + UjingClient.readable(r.code, r.msg),
                )
                return@launch
            }
            // PayTask 阻塞调用,需在后台线程;orderInfo 是支付宝 SDK 签名订单串
            val result = withContext(Dispatchers.IO) {
                com.alipay.sdk.app.PayTask(activity).payV2(orderInfo, true)
            }
            val paid = result.contains("resultStatus={9000}")
            val refreshed = ujing.orderDetail(washerToken, order.orderId)
            val newOrder = UjingClient.Parsers.parseOrder(refreshed.json)
            updateActiveOrder(washerState.scannedDevice ?: "", newOrder.status)
            washerState = washerState.copy(
                loading = false,
                currentOrder = newOrder,
                message = if (paid) {
                    if (washerState.autoStartAfterPay) "支付成功,3 秒后自动启动洗衣机" else "支付成功"
                } else "支付未完成,可在订单中重试",
            )
            if (paid && washerState.autoStartAfterPay) {
                kotlinx.coroutines.delay(3000)
                ujing.startOrder(washerToken, order.orderId)
                val d2 = ujing.orderDetail(washerToken, order.orderId)
                washerState = washerState.copy(currentOrder = UjingClient.Parsers.parseOrder(d2.json))
            }
        }
    }

    fun washerRefresh() {
        val order = washerState.currentOrder ?: return
        washerState = washerState.copy(loading = true)
        viewModelScope.launch {
            val d = ujing.orderDetail(washerToken, order.orderId)
            washerState = washerState.copy(loading = false, currentOrder = UjingClient.Parsers.parseOrder(d.json))
        }
    }

    fun washerStart() {
        val order = washerState.currentOrder ?: return
        viewModelScope.launch {
            ujing.startOrder(washerToken, order.orderId)
            washerRefresh()
        }
    }

    fun refreshWasherSaved() {
        // 恢复已保存的登录态(重启后免登录)
        if (washerToken.isBlank()) washerToken = settings.washerToken
        washerState = washerState.copy(
            loggedIn = washerToken.isNotBlank(),
            savedWashers = settings.washerDevices,
        )
    }

    /** 每台设备在途的操作(防连点竞态:迟到的 start 响应不得覆盖之后的 end) */
    private val waterOps = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun startWaterDevice(did: String) = toggleWaterDevice(did, start = true)

    fun endWaterDevice(did: String) = toggleWaterDevice(did, start = false)

    private fun toggleWaterDevice(did: String, start: Boolean) {
        if (waterOps[did]?.isActive == true) return
        // 乐观更新:点击立即切换显示,失败再回滚
        waterState = waterState.copy(
            loading = true,
            message = null,
            devices = waterState.devices.map {
                if (it.first == did) Triple(it.first, it.second, start) else it
            },
        )
        waterOps[did] = viewModelScope.launch {
            try {
                val r = if (start) ilife.start(waterToken, did) else ilife.end(waterToken, did)
                val verb = if (start) "启动" else "结束"
                waterState = waterState.copy(
                    loading = false,
                    message = if (r.ok) {
                        if (start) "设备已启动,请接水" else "已结束出水"
                    } else "$verb 失败:${IlifeClient.readable(r.code, r.msg)}",
                    devices = waterState.devices.map {
                        if (it.first == did) Triple(it.first, it.second, if (r.ok) start else !start) else it
                    },
                )
            } catch (e: Exception) {
                waterState = waterState.copy(
                    loading = false,
                    message = "${if (start) "启动" else "结束"}异常:${e.message}",
                    devices = waterState.devices.map {
                        if (it.first == did) Triple(it.first, it.second, !start) else it
                    },
                )
            } finally {
                waterOps.remove(did)
            }
        }
    }

    fun tryAutoLogin() {
        if (skippedLogin) return
        val creds = settings.loadCredentials()
        if (creds != null && !loggedIn) {
            account = creds.first; password = creds.second
            // 按存储的凭据类型走对应登录链路(统一认证密码 ≠ 教务密码)
            if (settings.credentialType() == "sso") doSsoLogin() else doLogin()
        }
    }

    fun doLogin() {
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            val err = client.login(account.trim(), password)
            loginLoading = false
            if (err == null) {
                loggedIn = true
                name = client.studentName
                settings.storeCredentials(account.trim(), password, "jwxt")
                // 课表策略:仅缓存为空时联网加载;刷新只发生在切学期/手动刷新
                if (allCourses.isEmpty()) loadSchedule()
                loadGrades()
            } else {
                loginError = err
            }
        }
    }

    /** 统一身份认证协议直登(账号密码走 ids.ncepu.edu.cn) */
    fun doSsoLogin() {
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            val err = client.ssoLogin(account.trim(), password)
            loginLoading = false
            if (err == null) {
                loggedIn = true
                name = client.studentName
                // 记住统一认证凭据,重启时自动走协议直登
                settings.storeCredentials(account.trim(), password, "sso")
                loadSchedule(force = allCourses.isNotEmpty(), silent = allCourses.isNotEmpty())
                loadGrades()
            } else {
                loginError = err
            }
        }
    }

    /** 统一身份认证(WebView)登录成功:导入会话 cookie 并校验 */
    fun completeWebLogin(cookieHeader: String) {
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            client.importCookies(cookieHeader)
            val verified = client.verifyWebLogin()
            loginLoading = false
            if (verified == null) {
                loginError = "统一认证会话无效,请重试"
                return@launch
            }
            loggedIn = true
            if (verified.isNotBlank()) name = verified
            loadSchedule(force = allCourses.isNotEmpty(), silent = allCourses.isNotEmpty())
            loadGrades()
        }
    }

    /**
     * 加载学期全量课表:本地按周过滤,不再按周请求(避免滑动多次后周次漂移)。
     * XLS / HTML / 官方周次三个请求并发;silent=true 用于缓存已上屏后的后台刷新
     * (不显示 loading,失败不打扰,完成后保留用户当前所在周)。
     */
    fun loadSchedule(force: Boolean = false, silent: Boolean = false) {
        if (schedLoaded && !force) {
            schedMode = "WEEK"
            selectedWeek = officialWeek
            courses = allCourses
            return
        }
        // 使用导入的 XLS:不联网刷新,直接回退缓存(登录与否均如此;force 才覆盖)
        if (settings.scheduleSource == "MANUAL" && !force) {
            fallBackToCache()
            return
        }
        // 未登录:请求必然失败(无会话,302),不浪费请求也不触发风控,直接回退缓存
        if (!loggedIn) {
            if (!fallBackToCache() && !silent) {
                schedError = "请先在「我的」登录教务系统后加载课表"
            }
            return
        }
        if (!silent) {
            schedLoading = true
            schedError = null
        }
        viewModelScope.launch {
            try {
                // 双源并发:XLS 导出(格式规整)+ 课表页 HTML 解析;
                // 任一源都可能残缺(实测服务器 HTML 曾只剩周一二),取星期覆盖更全的一份
                val xlsDef = async { runCatching { client.fetchScheduleXls(schedSem) }.getOrNull() }
                var htmlErr: Throwable? = null
                val htmlDef = async {
                    runCatching { client.fetchCourses(schedSem) }
                        .onFailure { htmlErr = it }
                        .getOrNull()
                }
                val weekDef = async {
                    runCatching { client.fetchHomeWeek(thisWeekThursdayText()).week.takeIf { it > 0 } }
                        .getOrNull()
                }
                val xls = xlsDef.await()?.takeIf { it.isNotEmpty() }
                val html = htmlDef.await()?.takeIf { it.isNotEmpty() }
                if (xls == null && html == null) {
                    throw htmlErr ?: JwException("课表获取失败,请稍后重试")
                }
                val xlsDays = xls?.map { it.day }?.distinct()?.size ?: 0
                val htmlDays = html?.map { it.day }?.distinct()?.size ?: 0
                val full = if (htmlDays > xlsDays) html!! else xls ?: html!!
                val hadShown = schedLoaded
                allCourses = full
                courses = full
                schedLoaded = true
                name = client.studentName ?: name
                settings.cacheCourses(full)
                // 手动刷新/切学期重新联网后,恢复自动数据源(覆盖手动导入的固定数据)
                if (force) settings.scheduleSource = "AUTO"
                // 官方当前周:周四锚定(与三请求并发);拿不到则本地推断
                officialWeek = weekDef.await() ?: localCurrentWeek()
                // 已有界面在显示时(静默刷新)保留用户所在周,否则跳到当前周
                selectedWeek = if (hadShown) selectedWeek.coerceIn(1, 25) else officialWeek
                schedMode = "WEEK"
                if (settings.reminderEnabled) {
                    ReminderScheduler.reschedule(getApplication())
                }
                if (!silent || full.isEmpty()) {
                    schedError = if (full.isEmpty()) "本学期暂无课表(接口返回为空)" else null
                }
            } catch (e: Exception) {
                // 界面上还没有数据时优先回退本地缓存;失败且无缓存才显示错误
                if (allCourses.isEmpty()) {
                    if (!fallBackToCache()) schedError = e.message ?: "加载失败"
                } else if (!silent) {
                    schedError = e.message ?: "加载失败"
                }
            } finally {
                schedLoading = false
            }
        }
    }

    /** force=false 时 12 小时只自动检查一次 */
    fun checkForUpdate(force: Boolean = false) {
        if (updateState is com.ncepu.jw.update.Updater.State.Checking ||
            updateState is com.ncepu.jw.update.Updater.State.Downloading
        ) return
        val now = System.currentTimeMillis()
        if (!force && now - settings.lastUpdateCheck < 12 * 3600_000L) return
        updateState = com.ncepu.jw.update.Updater.State.Checking
        viewModelScope.launch {
            settings.lastUpdateCheck = now
            val info = com.ncepu.jw.update.Updater.fetchLatest()
            if (info == null) {
                updateState = com.ncepu.jw.update.Updater.State.Failed("检查失败,请检查网络")
                return@launch
            }
            updateState =
                if (info.versionCode > com.ncepu.jw.update.Updater.currentVersionCode(getApplication())) {
                    com.ncepu.jw.update.Updater.State.Available(info)
                } else {
                    com.ncepu.jw.update.Updater.State.Latest(info.versionName)
                }
        }
    }

    fun downloadUpdate() {
        val info = (updateState as? com.ncepu.jw.update.Updater.State.Available)?.info ?: return
        if (updateState is com.ncepu.jw.update.Updater.State.Downloading) return
        updateState = com.ncepu.jw.update.Updater.State.Downloading(0)
        viewModelScope.launch {
            try {
                val file = com.ncepu.jw.update.Updater.downloadApk(getApplication(), info) { p ->
                    updateState = com.ncepu.jw.update.Updater.State.Downloading(p)
                }
                updateState = com.ncepu.jw.update.Updater.State.Downloaded(file, info.versionName)
            } catch (e: Exception) {
                updateState = com.ncepu.jw.update.Updater.State.Failed("下载失败:${e.message}")
            }
        }
    }

    fun onUpdateAction() {
        when (val s = updateState) {
            is com.ncepu.jw.update.Updater.State.Available -> downloadUpdate()
            is com.ncepu.jw.update.Updater.State.Downloaded -> com.ncepu.jw.update.Updater.install(getApplication(), s.file)
            is com.ncepu.jw.update.Updater.State.Failed, is com.ncepu.jw.update.Updater.State.Idle -> checkForUpdate(force = true)
            else -> {}
        }
    }

    private fun schedDiagText(): String =
        "cache=${settings.loadCachedCourses().size}, source=${settings.scheduleSource}, " +
            "loggedIn=$loggedIn, credType=${settings.credentialType()}"

    /** 未登录/加载失败时回退本地缓存课表;返回是否已有可显示的课表 */
    private fun fallBackToCache(): Boolean {
        if (allCourses.isNotEmpty()) { schedDiag = ""; return true }
        val cached = settings.loadCachedCourses()
        if (cached.isEmpty()) { schedDiag = schedDiagText() + ", cacheFile=空"; return false }
        allCourses = cached
        courses = cached
        schedLoaded = true
        schedError = null
        schedDiag = ""
        if (officialWeek <= 0) officialWeek = localCurrentWeek()
        if (selectedWeek <= 0) selectedWeek = officialWeek
        return true
    }

    private fun localCurrentWeek(): Int =
        SettingsStore.currentWeek(System.currentTimeMillis(), settings.weekStartMillis).coerceIn(1, 30)

    private fun thisWeekThursdayText(): String {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_MONTH, -((dow + 5) % 7)) // 本周一
        cal.add(Calendar.DAY_OF_MONTH, 3)                 // 周四
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    /** 切换查看的周:纯本地过滤,瞬时完成 */
    fun selectWeek(week: Int) {
        selectedWeek = week.coerceIn(1, 25)
        schedMode = "WEEK"
    }

    fun showAllSemester() {
        schedMode = "ALL"
        selectedWeek = 0
    }

    fun backToCurrentWeek() {
        schedMode = "WEEK"
        selectedWeek = officialWeek.coerceAtLeast(1)
    }

    fun changeSemester(sem: Semester) {
        schedSem = sem
        settings.scheduleSource = "AUTO"  // 切学期重新联网获取
        loadSchedule(force = true)
    }

    /** 手动导入课表 XLS(教务"打印课表"导出的 .xls);导入后不再自动刷新 */
    fun importScheduleXls(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<android.app.Application>().contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                } ?: throw JwException("无法读取文件")
                if (bytes.size < 8 || bytes[0] != 0xD0.toByte() || bytes[1] != 0xCF.toByte()) {
                    throw JwException("不是有效的 XLS 文件(请用教务「打印课表」导出的 .xls)")
                }
                val parsed = com.ncepu.jw.data.ScheduleXlsParser.parse(bytes)
                if (parsed.isEmpty()) throw JwException("文件中未解析出课程")
                allCourses = parsed
                courses = parsed
                schedLoaded = true
                schedError = null
                schedMode = "WEEK"
                if (officialWeek <= 0) officialWeek = localCurrentWeek()
                selectedWeek = officialWeek
                settings.scheduleSource = "MANUAL"
                settings.cacheCourses(parsed)
                if (settings.reminderEnabled) {
                    ReminderScheduler.reschedule(getApplication())
                }
                android.widget.Toast.makeText(
                    getApplication(), "已导入 ${parsed.size} 条课程记录", android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                schedError = e.message ?: "导入失败"
                android.widget.Toast.makeText(
                    getApplication(), "导入失败:${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun loadGrades() {
        gradeLoading = true
        gradeError = null
        viewModelScope.launch {
            try {
                val page = client.fetchGrades(gradeSem)
                grades = page.items.filter { it.term == gradeSem.key }
                name = client.studentName ?: name
                gradeError = null
            } catch (e: Exception) {
                grades = emptyList()
                gradeError = e.message ?: "加载失败"
            } finally {
                gradeLoading = false
            }
        }
    }

    /**
     * 选课中心:接口有风控,做本地缓存 + 时间频控。
     * - 缓存未过期(SEL_STALE_MS 内)时切换到选课页不再发请求;
     * - 缓存过期才静默刷新(列表已上屏,不闪 loading);失败不打扰。
     * force=true(下拉刷新/重试)绕过频控。
     */
    fun loadSelection(force: Boolean = false) {
        if (selLoading) return
        val fresh = System.currentTimeMillis() - selCacheTime < SEL_STALE_MS
        if (selectionLoaded && fresh && !force) return
        val silent = selectionLoaded || xkRounds.isNotEmpty()
        if (!silent) {
            selLoading = true
            selError = null
        }
        viewModelScope.launch {
            try {
                val rounds = client.fetchXkRounds()
                val selected = client.fetchSelectedCourses(gradeSem)
                xkRounds = rounds
                selectedCourses = selected
                selectionLoaded = true
                selCacheTime = System.currentTimeMillis()
                selError = null
                withContext(Dispatchers.IO) { settings.cacheSelection(rounds, selected) }
            } catch (e: Exception) {
                if (!silent || xkRounds.isEmpty()) selError = e.message ?: "加载失败"
            } finally {
                selLoading = false
            }
        }
    }

    fun loadPyfa() {
        if (pyfa != null || pyfaLoading) return
        pyfaLoading = true
        pyfaError = null
        viewModelScope.launch {
            try {
                pyfa = client.fetchPyfa()
            } catch (e: Exception) {
                pyfaError = e.message ?: "加载失败"
            } finally {
                pyfaLoading = false
            }
        }
    }

    fun loadExams() {
        examLoading = true
        examError = null
        viewModelScope.launch {
            try {
                exams = client.fetchExams(examSem)
                settings.cacheExams(exams)
                ReminderScheduler.reschedule(getApplication())
            } catch (e: Exception) {
                examError = e.message ?: "加载失败"
            } finally {
                examLoading = false
            }
        }
    }

    fun logout() {
        loggedIn = false
        account = ""; password = ""
        courses = emptyList(); grades = emptyList()
        xkRounds = emptyList(); selectedCourses = emptyList(); selectionLoaded = false; selCacheTime = 0
        allCourses = emptyList(); schedLoaded = false
        pyfa = null; pyfaError = null
        exams = emptyList(); examError = null
        loginError = null
        ReminderScheduler.reschedule(getApplication())
    }
}

/** 未登录提示(课表/成绩页) */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge:内容延伸到状态栏/导航条后面,背景与暗化全屏覆盖
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val settings = SettingsStore(this)
        // 提醒通知渠道:高重要性(横幅+声音+震动),App 启动即建
        com.ncepu.jw.reminder.ReminderScheduler.ensureChannel(this)

        setContent {
            var themeMode by mutableStateOf(settings.themeMode)
            var presetKey by mutableStateOf(settings.themePreset)
            var dynamicColor by mutableStateOf(settings.dynamicColor)
            var fontScale by mutableStateOf(settings.fontScale)
            var appearance by mutableStateOf(
                Appearance(
                    navShape = settings.navShape,
                    navMaterial = settings.navMaterial,
                    bgHas = settings.backgroundFile() != null,
                    bgBlur = settings.bgBlur,
                    bgDim = settings.bgDim,
                    weekStartMillis = settings.weekStartMillis,
                    bgVersion = 0,
                )
            )

            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)
            ) {
                NcepuTheme(
                    themeMode = themeMode,
                    presetKey = presetKey,
                    dynamicColor = dynamicColor,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        App(
                            themeMode = themeMode,
                            presetKey = presetKey,
                            dynamicColor = dynamicColor,
                            fontScale = fontScale,
                            appearance = appearance,
                            onThemeModeChange = { settings.themeMode = it; themeMode = it },
                            onPresetChange = { settings.themePreset = it; presetKey = it },
                            onDynamicColorChange = { settings.dynamicColor = it; dynamicColor = it },
                            onFontScaleChange = { settings.fontScale = it; fontScale = it },
                            onAppearanceChange = { appearance = it },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun App(
        vm: AppViewModel = viewModel(),
        themeMode: ThemeMode,
        presetKey: String,
        dynamicColor: Boolean,
        fontScale: Float,
        appearance: Appearance,
        onThemeModeChange: (ThemeMode) -> Unit,
        onPresetChange: (String) -> Unit,
        onDynamicColorChange: (Boolean) -> Unit,
        onFontScaleChange: (Float) -> Unit,
        onAppearanceChange: (Appearance) -> Unit,
    ) {
        val ctx = LocalContext.current
        val navController = rememberNavController()

        // 状态栏图标颜色跟随主题
        val isDarkNow = themeMode == ThemeMode.DARK ||
            (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
        LaunchedEffect(isDarkNow) {
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !isDarkNow
        }

        LaunchedEffect(Unit) {
            if (!vm.skippedLogin) vm.tryAutoLogin()
            if (vm.settings.waterToken.isNotBlank() || vm.skippedLogin) {
                vm.loadWaterDevices()
            } else {
                vm.refreshCaptcha()
            }
        }

        val notifPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { ReminderScheduler.reschedule(ctx) }

        val evalLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { vm.loadGrades() }

        val xkLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { vm.loadSelection() }

        // 统一身份认证登录:WebView 完成 SSO 后回传教务会话
        val ssoLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val cookies = WebViewActivity.ssoCookies
            WebViewActivity.ssoCookies = null
            if (result.resultCode == android.app.Activity.RESULT_OK && !cookies.isNullOrBlank()) {
                vm.completeWebLogin(cookies)
            }
        }

        var pendingCropUri by mutableStateOf<android.net.Uri?>(null)

        val xlsPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri -> uri?.let { vm.importScheduleXls(it) } }

        val bgPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                pendingCropUri = uri
                navController.navigate("bgcrop")
            }
        }

        fun applyReminder(enabled: Boolean) {
            vm.settings.reminderEnabled = enabled
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ReminderScheduler.reschedule(ctx)
            }
        }

        // 教务登录改为"我的"中的入口路由;主界面始终可达(饮水机不依赖教务登录)
        NavHost(
            navController = navController,
            startDestination = "main",
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(320),
                    initialOffset = { it / 6 },
                ) + fadeIn(tween(320))
            },
                    exitTransition = { fadeOut(tween(220)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(320),
                            targetOffset = { it / 6 },
                        ) + fadeOut(tween(320))
                    },
                ) {
                    composable("main") {
                        MainScaffold(
                            vm = vm,
                            appearance = appearance,
                            isDark = themeMode == ThemeMode.DARK ||
                                (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme()),
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenPyfa = {
                                vm.loadPyfa()
                                navController.navigate("pyfa")
                            },
                            onOpenExams = { navController.navigate("exams") },
                            onOpenWaterScan = { navController.navigate("waterscan") },
                            onOpenWasher = { navController.navigate("washer") },
                            onOpenWasherScan = { navController.navigate("washerscan") },
                            onOpenJwxtLogin = { navController.navigate("login") },
                            onOpenGradesNav = {
                                if (!vm.loggedIn) navController.navigate("login")
                                else {
                                    if (vm.grades.isEmpty()) vm.loadGrades()
                                    navController.navigate("grades")
                                }
                            },
                            onEvaluate = {
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                evalLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(
                                            WebViewActivity.EXTRA_URL,
                                            JwClient.DEFAULT_BASE + "/jsxsd/newxspj/zhxspj_list.do",
                                        )
                                        putExtra(WebViewActivity.EXTRA_TITLE, "教学评价")
                                    }
                                )
                            },
                            onEnterRound = { round ->
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                xkLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(
                                            WebViewActivity.EXTRA_URL,
                                            JwClient.DEFAULT_BASE + round.url,
                                        )
                                        putExtra(WebViewActivity.EXTRA_TITLE, round.name.ifBlank { "选课" })
                                    }
                                )
                            },
                        )
                    }
                    composable("pyfa") {
                        PyfaScreen(
                            loading = vm.pyfaLoading,
                            error = vm.pyfaError,
                            data = vm.pyfa,
                            onRetry = { vm.loadPyfa() },
                        )
                    }
                    composable("waterscan") {
                        WaterScanScreen(
                            onResult = { raw ->
                                // 从二维码内容提取 did,回填到添加对话框状态
                                vm.waterScanResult = WaterDeviceIdParser.normalize(raw)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    composable("washer") {
                        // 每次进入洗衣页时恢复已保存的登录态与设备列表
                        androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshWasherSaved() }
                        WasherScreen(
                            state = vm.washerState,
                            onRemoveWasher = { did ->
                                vm.settings.removeWasherDevice(did)
                                vm.washerState = vm.washerState.copy(
                                    savedWashers = vm.settings.washerDevices,
                                )
                            },
                            phone = vm.washerPhone,
                            smsCode = vm.washerSmsCode,
                            smsCooldown = vm.washerSmsCooldown,
                            onPhoneChange = { vm.washerPhone = it },
                            onSmsCodeChange = { vm.washerSmsCode = it },
                            onSendSms = { vm.washerRequestCaptcha() },
                            onLogin = { vm.doWasherLogin() },
                            onScanOrInput = { vm.washerScan(it) },
                            onSelectSaved = { vm.washerSelectSaved(it) },
                            onScan = { navController.navigate("washerscan") },
                            onSelectModel = { id ->
                                val models = vm.washerState.models
                                vm.washerState = vm.washerState.copy(
                                    selectedModelId = id,
                                    // 加购组跟随模式切换,重置为"不添加"
                                    selectedAdditions = models.firstOrNull { it.id == id }
                                        ?.additions?.associate { it.key to null } ?: emptyMap(),
                                )
                            },
                            onSelectTemperature = { id ->
                                vm.washerState = vm.washerState.copy(selectedTemperatureId = id)
                            },
                            onSelectAddition = { key, optId ->
                                vm.washerState = vm.washerState.copy(
                                    selectedAdditions = vm.washerState.selectedAdditions + (key to optId),
                                )
                            },
                            onCreateOrder = { vm.washerCreateOrder() },
                            onPay = { vm.washerPay(this@MainActivity) },
                            onAutoStartChange = { vm.washerState = vm.washerState.copy(autoStartAfterPay = it) },
                            onRefreshOrder = { vm.washerRefresh() },
                            onStartWash = { vm.washerStart() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("washerscan") {
                        WaterScanScreen(
                            onResult = { raw ->
                                // scanWasherCode 需要二维码原始内容,这里不做归一化
                                vm.washerScan(raw)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    composable("exams") {
                        ExamScreen(
                            semesters = vm.semesters,
                            selected = vm.examSem,
                            loading = vm.examLoading,
                            error = vm.examError,
                            exams = vm.exams,
                            onSemesterChange = { vm.examSem = it; vm.loadExams() },
                            onRetry = { vm.loadExams() },
                        )
                    }
                    composable("grades") {
                        GradeScreen(
                            semesters = vm.semesters,
                            selected = vm.gradeSem,
                            loading = vm.gradeLoading,
                            error = vm.gradeError,
                            grades = vm.grades,
                            onSemesterChange = { vm.gradeSem = it; vm.loadGrades() },
                            onRetry = { vm.loadGrades() },
                            onEvaluate = {
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                evalLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(
                                            WebViewActivity.EXTRA_URL,
                                            JwClient.DEFAULT_BASE + "/jsxsd/newxspj/zhxspj_list.do",
                                        )
                                        putExtra(WebViewActivity.EXTRA_TITLE, "教学评价")
                                    }
                                )
                            },
                        )
                    }
                    composable("bgcrop") {
                        pendingCropUri?.let { uri ->
                            BackgroundCropScreen(
                                uri = uri,
                                onCancel = {
                                    pendingCropUri = null
                                    navController.popBackStack()
                                },
                                onDone = {
                                    pendingCropUri = null
                                    onAppearanceChange(
                                        appearance.copy(bgHas = true, bgVersion = appearance.bgVersion + 1)
                                    )
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen(
                            themeMode = themeMode,
                            presetKey = presetKey,
                            dynamicColor = dynamicColor,
                            fontScale = fontScale,
                            navShape = appearance.navShape,
                            navMaterial = appearance.navMaterial,
                            hasBackground = appearance.bgHas,
                            bgBlur = appearance.bgBlur,
                            bgDim = appearance.bgDim,
                            reminderEnabled = vm.reminderEnabled,
                            examReminderEnabled = vm.examReminderEnabled,
                            leadMinutes = vm.leadMinutes,
                            sectionTimes = vm.sectionTimes,
                            weekStartMillis = appearance.weekStartMillis,
                            exactAlarmGranted = isExactAlarmGranted(ctx),
                            scheduleSource = vm.scheduleSource,
                            onImportScheduleXls = { xlsPicker.launch("*/*") },
                            onUseImportedChange = { vm.setUseImportedXls(it) },
                            update = vm.updateState,
                            currentVersion = com.ncepu.jw.update.Updater.currentVersionName(ctx),
                            onCheckUpdate = { vm.checkForUpdate(force = true) },
                            onUpdateAction = { vm.onUpdateAction() },
                            onThemeModeChange = onThemeModeChange,
                            onPresetChange = onPresetChange,
                            onDynamicColorChange = onDynamicColorChange,
                            onFontScaleChange = onFontScaleChange,
                            onNavShapeChange = { s ->
                                vm.settings.navShape = s
                                // 液态玻璃只在悬浮形状下可用;切回标准时自动改实色
                                val m = if (s == com.ncepu.jw.data.NavBarShape.STANDARD &&
                                    appearance.navMaterial == NavMaterial.LIQUID
                                ) NavMaterial.SOLID else appearance.navMaterial
                                if (m != appearance.navMaterial) vm.settings.navMaterial = m
                                onAppearanceChange(appearance.copy(navShape = s, navMaterial = m))
                            },
                            onNavMaterialChange = { m ->
                                vm.settings.navMaterial = m
                                onAppearanceChange(appearance.copy(navMaterial = m))
                            },
                            onPickBackground = { bgPicker.launch("image/*") },
                            onClearBackground = {
                                vm.settings.clearScheduleBackground()
                                onAppearanceChange(
                                    appearance.copy(bgHas = false, bgVersion = appearance.bgVersion + 1)
                                )
                            },
                            onBgBlurChange = { f ->
                                vm.settings.bgBlur = f
                                onAppearanceChange(appearance.copy(bgBlur = f))
                            },
                            onBgDimChange = { f ->
                                vm.settings.bgDim = f
                                onAppearanceChange(appearance.copy(bgDim = f))
                            },
                            onReminderToggle = { enabled ->
                                vm.reminderEnabled = enabled
                                applyReminder(enabled)
                            },
                            onExamReminderToggle = { enabled ->
                                vm.examReminderEnabled = enabled
                                vm.settings.examReminderEnabled = enabled
                                ReminderScheduler.reschedule(ctx)
                            },
                            onLeadChange = { min ->
                                vm.leadMinutes = min
                                vm.settings.leadMinutes = min
                                ReminderScheduler.reschedule(ctx)
                            },
                            onTimesChange = { times ->
                                vm.sectionTimes = times
                                vm.settings.sectionTimes = times
                                ReminderScheduler.reschedule(ctx)
                            },
                            onWeekStartChange = { millis ->
                                vm.settings.weekStartMillis = millis
                                onAppearanceChange(appearance.copy(weekStartMillis = millis))
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("login") {
                        // 登录成功:自动返回上一页(课表/成绩已开始加载)
                        LaunchedEffect(vm.loggedIn) {
                            if (vm.loggedIn) navController.popBackStack()
                        }
                        LoginScreen(
                            account = vm.account,
                            password = vm.password,
                            loading = vm.loginLoading,
                            error = vm.loginError,
                            onAccountChange = { vm.account = it },
                            onPasswordChange = { vm.password = it },
                            onLogin = { vm.doLogin() },
                            onSkip = { navController.popBackStack() },
                            onSsoLogin = { vm.doSsoLogin() },
                            onSsoWebLogin = {
                                ssoLauncher.launch(
                                    android.content.Intent(ctx, WebViewActivity::class.java)
                                        .putExtra(WebViewActivity.EXTRA_SSO, true)
                                )
                            },
                        )
                    }
                }
    }

    private fun isExactAlarmGranted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 解析本地背景图(采样,避免大图 OOM) */
    private fun loadBgBitmap(file: java.io.File): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        bmp.asImageBitmap()
    }.getOrNull()

    @Composable
    private fun MainScaffold(
        vm: AppViewModel,
        appearance: Appearance,
        isDark: Boolean,
        onOpenSettings: () -> Unit,
        onOpenPyfa: () -> Unit,
        onOpenExams: () -> Unit,
        onOpenWaterScan: () -> Unit,
        onOpenWasherScan: () -> Unit,
        onOpenWasher: () -> Unit,
        onOpenJwxtLogin: () -> Unit,
        onOpenGradesNav: () -> Unit,
        onEvaluate: () -> Unit,
        onEnterRound: (com.ncepu.jw.data.XkRound) -> Unit,
    ) {
        // rememberSaveable:进扫码等子路由时 main 离开组合,返回后需恢复所选 tab(否则回到课表)
        var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
        val ctx = LocalContext.current

        // 背景层:View 容器常驻渲染树(背景图 + 暗化一体)。
        // 暗化用 FrameLayout 前景实现,必然覆盖全容器(含状态栏/导航条区域)。
        val bgHost = remember { FrameLayout(ctx) }
        val bgImageView = remember { ImageView(ctx) }
        val dimDrawable = remember {
            android.graphics.drawable.ColorDrawable(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = appearance.bgDim.coerceIn(0f, 0.85f)).toArgb()
            )
        }
        var showBg by remember(appearance.bgHas, appearance.bgVersion) {
            mutableStateOf(appearance.bgHas)
        }
        // 液态玻璃折射源:背景图的 Compose 隐形副本(仅进入 backdrop 采样层,不参与显示)
        val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        var bgBmp by remember(appearance.bgHas, appearance.bgVersion) {
            mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        }

        remember(showBg) {
            bgHost.removeAllViews()
            bgHost.foreground = if (showBg) dimDrawable else null
            if (showBg) {
                bgHost.addView(
                    bgImageView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            true
        }
        val densityValue = LocalDensity.current.density
        // 解码只依赖背景图本身;调模糊滑杆不再从磁盘重解 bitmap
        LaunchedEffect(appearance.bgHas, appearance.bgVersion) {
            if (!appearance.bgHas) {
                bgImageView.setImageDrawable(null)
                return@LaunchedEffect
            }
            val bmp = withContext(Dispatchers.IO) {
                vm.settings.backgroundFile()?.let { loadBgBitmap(it) }
            }
            bgImageView.setImageBitmap(bmp?.asAndroidBitmap())
            bgBmp = bmp
        }
        // 模糊只重设 RenderEffect,与解码解耦
        LaunchedEffect(appearance.bgHas, appearance.bgBlur, densityValue) {
            applyBackgroundBlur(bgImageView, if (appearance.bgHas) appearance.bgBlur else 0f, densityValue)
        }
        LaunchedEffect(appearance.bgDim, appearance.bgVersion) {
            dimDrawable.alpha = (appearance.bgDim.coerceIn(0f, 0.85f) * 255).toInt()
        }

        Box(Modifier.fillMaxSize()) {
            // 背景容器(最底层)
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { bgHost },
                modifier = Modifier.fillMaxSize(),
            )
            // 液态玻璃折射源:隐形壁纸副本(alpha 0 只进采样层)。有背景图才折射壁纸。
            if (showBg) {
                val bmp = bgBmp
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bmp,
                        null,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0f }
                            .layerBackdrop(glassBackdrop),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
            Scaffold(
                containerColor = if (showBg) Color.Transparent else MaterialTheme.colorScheme.background,
                bottomBar = {
                    AppBottomBar(
                        shape = appearance.navShape,
                        material = appearance.navMaterial,
                        tab = tab,
                        onSelect = { t ->
                            tab = t
                            when (t) {
                            0 -> if (vm.allCourses.isEmpty() && !vm.skippedLogin) vm.loadSchedule()
                            1 -> vm.loadWaterDevices(silent = vm.waterState.devices.isNotEmpty())
                            2 -> if (vm.loggedIn) vm.loadSelection()
                            }
                        },
                        isDark = isDark,
                        backdrop = glassBackdrop,
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState)
                                AnimatedContentTransitionScope.SlideDirection.Start
                            else AnimatedContentTransitionScope.SlideDirection.End
                            (slideIntoContainer(dir, tween(280), initialOffset = { it / 12 })
                                + fadeIn(tween(280)))
                                .togetherWith(
                                    slideOutOfContainer(dir, tween(280), targetOffset = { -it / 12 })
                                        + fadeOut(tween(200))
                                )
                        },
                        label = "tab",
                    ) { t ->
                        when (t) {
                            0 -> ScheduleScreen(
                                loading = vm.schedLoading,
                                error = vm.schedError,
                                allCourses = vm.allCourses,
                                diag = vm.schedDiag,
                                mode = vm.schedMode,
                                weekStartMillis = appearance.weekStartMillis,
                                officialWeek = vm.officialWeek,
                                semesters = vm.semesters,
                                selected = vm.schedSem,
                                sectionTimes = vm.settings.sectionTimes,
                                bgEnabled = showBg,
                                onSelectWeek = { vm.selectWeek(it) },
                                onShowAll = { vm.showAllSemester() },
                                onBackToWeek = { vm.backToCurrentWeek() },
                                onSemesterChange = { vm.changeSemester(it) },
                                onRetry = { vm.loadSchedule(force = true) },
                                onRefresh = { vm.loadSchedule(force = true) },
                                onOpenExams = onOpenExams,
                            )
                            1 -> WaterScreen(
                                state = vm.waterState,
                                phone = vm.waterPhone,
                                smsCode = vm.waterSmsCode,
                                captchaInput = vm.waterCaptchaInput,
                                smsCooldown = vm.waterSmsCooldown,
                                onPhoneChange = { vm.waterPhone = it },
                                onSmsCodeChange = { vm.waterSmsCode = it },
                                onCaptchaInputChange = { vm.waterCaptchaInput = it },
                                onRefreshCaptcha = { vm.refreshCaptcha() },
                                onSendSms = { vm.sendWaterSms() },
                                onLogin = { vm.doWaterLogin() },
                                onRefreshDevices = { vm.loadWaterDevices() },
                                onStartDevice = { vm.startWaterDevice(it) },
                                onEndDevice = { vm.endWaterDevice(it) },
                                onAddDevice = { did, name -> vm.addWaterDevice(did, name) },
                                onRemoveDevice = { did -> vm.removeWaterDevice(did) },
                                onScan = onOpenWaterScan,
                                scanResult = vm.waterScanResult,
                            )
                            2 -> SelectionScreen(
                                loggedIn = vm.loggedIn,
                                loading = vm.selLoading,
                                error = vm.selError,
                                rounds = vm.xkRounds,
                                courses = vm.selectedCourses,
                                onEnterRound = onEnterRound,
                                onRetry = { vm.loadSelection() },
                            )
                            else -> ProfileScreen(
                                account = vm.account,
                                name = vm.name,
                                loggedIn = vm.loggedIn,
                                update = vm.updateState,
                                onUpdateAction = { vm.onUpdateAction() },
                                onOpenJwxtLogin = onOpenJwxtLogin,
                                onOpenSettings = onOpenSettings,
                                onOpenPyfa = onOpenPyfa,
                                onOpenWasher = onOpenWasher,
                                onOpenGrades = onOpenGradesNav,
                                onLogout = {
                                    vm.settings.clearCredentials()
                                    vm.logout()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
