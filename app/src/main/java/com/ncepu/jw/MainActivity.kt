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
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import com.ncepu.jw.data.Webvpn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.WasherOrderInfo
import com.ncepu.jw.update.WasherWatchService
import com.ncepu.jw.data.Grade
import com.ncepu.jw.data.JwClient
import com.ncepu.jw.data.JwException
import com.ncepu.jw.data.BillUi
import com.ncepu.jw.data.BillUiState
import com.ncepu.jw.data.IlifeClient
import com.ncepu.jw.data.IlifeSign
import com.ncepu.jw.data.PointsUiState
import com.ncepu.jw.data.WaterSubActions
import com.ncepu.jw.data.UjingClient
import okhttp3.OkHttpClient
import org.json.JSONObject
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.ncepu.jw.data.Semester
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.ThemeMode
import com.ncepu.jw.reminder.ReminderScheduler
import com.ncepu.jw.ui.AboutScreen
import com.ncepu.jw.ui.AppBottomBar
import com.ncepu.jw.ui.LocalBottomBarInset
import com.ncepu.jw.ui.LocalHeaderContentColor
import com.ncepu.jw.ui.LocalHeaderPalette
import com.ncepu.jw.ui.BackgroundCropScreen
import com.ncepu.jw.ui.ExamScreen
import com.ncepu.jw.ui.GradeScreen
import com.ncepu.jw.ui.LoginScreen
import com.ncepu.jw.ui.ProfileScreen
import com.ncepu.jw.ui.PyfaScreen
import com.ncepu.jw.ui.QrScanRouter
import com.ncepu.jw.ui.ScanTarget
import com.ncepu.jw.ui.ScheduleScreen
import com.ncepu.jw.ui.SelectionScreen
import com.ncepu.jw.ui.SettingsScreen
import com.ncepu.jw.ui.WaterScreen
import com.ncepu.jw.ui.WaterScanScreen
import com.ncepu.jw.ui.WaterDeviceIdParser
import com.ncepu.jw.ui.HEADER_BAND_BOTTOM
import com.ncepu.jw.ui.HeaderPaletteSampler
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

    // 校外模式(WebVPN)
    var webvpnEnabled by mutableStateOf(settings.webvpnEnabled)
    var widgetStyle by mutableStateOf(settings.widgetStyle)
    // 小部件深链:非空时 MainScaffold 据此切到对应 tab(1=饮水),消费后置空
    var pendingTab by mutableStateOf<Int?>(null)
    // 短信验证码输入(校外登录 与 校内统一认证 MFA 共用)
    var smsPrompt by mutableStateOf(false)
    private var smsDeferred: kotlinx.coroutines.CompletableDeferred<String>? = null

    /** 弹短信码输入框并挂起等待用户提交;返回输入的码(空=取消) */
    private suspend fun requestSmsCode(): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val d = kotlinx.coroutines.CompletableDeferred<String>()
            smsDeferred = d; smsPrompt = true
            try { d.await() } finally { smsPrompt = false; smsDeferred = null }
        }

    fun submitSms(code: String) { smsDeferred?.complete(code.trim()) }
    fun cancelSms() { smsDeferred?.complete("") }

    var semesters: List<Semester> = Semester.options(8)
    var schedSem by mutableStateOf(Semester.current())
    var schedLoading by mutableStateOf(false)
    var schedError by mutableStateOf<String?>(null)
    /** 课表最近一次成功拉取时间(刷新失败时提示用;0=未知) */
    var schedUpdatedAt by mutableStateOf(0L)
    var allCourses by mutableStateOf<List<Course>>(emptyList())  // 学期全量(xskb_list.do)
    var courses by mutableStateOf<List<Course>>(emptyList())     // 当前显示(周视图=本地过滤)
    var officialWeek by mutableStateOf(0)        // 官方当前周(教务系统计算,加载时取一次)
    // 注意:以下状态必须在 init 之前声明(Kotlin 按声明顺序初始化,
    // init/其协程会读写它们;声明在 init 之后会导致启动期 NPE 崩溃)
    var schedDiag by mutableStateOf("")

    // ---------- 应用内更新(镜像站加速) ----------
    var updateState by mutableStateOf<com.ncepu.jw.update.Updater.State>(
        com.ncepu.jw.update.Updater.State.Idle
    )
    var updatePrompt by mutableStateOf(false)   // 更新提醒弹窗开关
    var updateTargetName by mutableStateOf("") // 目标版本名(下载中展示)
    private var updatePendingInfo: com.ncepu.jw.update.Updater.Info? = null // 供下载失败重试
    var schedLoaded by mutableStateOf(false)

    var gradeSem by mutableStateOf(Semester.current())
    var gradeLoading by mutableStateOf(false)
    var gradeError by mutableStateOf<String?>(null)
    /** 成绩请求序号:切学期时旧响应作废(见 loadGrades) */
    private var gradeReqId = 0
    var grades by mutableStateOf<List<Grade>>(emptyList())
    /** 全量成绩(所有学期一次拉回),学期下拉据此只列「真的出过分」的学期 */
    var allGrades by mutableStateOf<List<Grade>>(emptyList())
    /** 成绩页可选学期:仅含有成绩的学期,倒序 */
    var gradeSemesters by mutableStateOf<List<Semester>>(emptyList())
    private var gradeFetchedAt = 0L
    /** 当前成绩来自本地缓存(登录失败/离线):页面据此提示"正在查看缓存" */
    var gradeFromCache by mutableStateOf(false)
    /** 成绩缓存写入时间(0 = 从未缓存过) */
    var gradesCachedAt by mutableStateOf(0L)

    var selLoading by mutableStateOf(false)
    var selError by mutableStateOf<String?>(null)
    var xkRounds by mutableStateOf<List<com.ncepu.jw.data.XkRound>>(emptyList())
    var selectedCourses by mutableStateOf<List<com.ncepu.jw.data.SelectedCourse>>(emptyList())
    var selectionLoaded by mutableStateOf(false)
    private var selCacheTime by mutableStateOf(0L)
    /** 缓存里选课结果所属学期:换了学期才允许用空结果覆盖旧列表 */
    private var selCacheSem by mutableStateOf("")
    /** 考试安排最近一次成功拉取时间:进页面自动刷新时用它节流(见 loadExams) */
    private var examFetchedAt = 0L

    companion object {
        /** 选课缓存视为过期的时间(超时后进入选课页才再次请求) */
        private const val SEL_STALE_MS = 10 * 60_000L

    /** 全量成绩的新鲜期:期内切学期不再请求 */
    private const val GRADE_STALE_MS = 10 * 60_000L
        /** 考试安排视为新鲜的时间:进页面自动刷新在此之内直接跳过 */
        private const val EXAM_STALE_MS = 5 * 60_000L
    }

    var pyfaLoading by mutableStateOf(false)
    var pyfaError by mutableStateOf<String?>(null)
    var pyfa by mutableStateOf<com.ncepu.jw.data.PyfaData?>(null)

    var examSem by mutableStateOf(Semester.current())
    var examLoading by mutableStateOf(false)
    var examError by mutableStateOf<String?>(null)
    var exams by mutableStateOf<List<com.ncepu.jw.data.Exam>>(emptyList())
    /** 当前考试列表来自本地缓存:没连校园网/拉取失败时用缓存垫底,页面据此提示"离线缓存" */
    var examFromCache by mutableStateOf(false)
    /** 缓存写入时间(0 = 缓存里没有时间戳),给"更新于"用 */
    var examsCachedAt by mutableStateOf(0L)

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

    /** 每次"扫码命中饮水机"自增一次:同一台设备重复扫时 waterScanResult 的值没变,靠它重新触发定位 */
    var waterScanTick by mutableStateOf(0)

    /** 统一扫码分流拿不准时的原始码(非空则弹"饮水机 / 洗衣机"选择框,见 ScanTargetDialog) */
    var scanPendingRaw by mutableStateOf<String?>(null)
    private var waterToken by mutableStateOf("")
    private var waterCaptchaKey = IlifeClient.newCaptchaKey()

    // ---------- 饮水页的积分 / 账单两个子页 ----------
    /** 子页下标:0 饮水 / 1 积分 / 2 账单 */
    var waterSubTab by mutableStateOf(0)

    /** 积分会话 token(小程序通道 1,5),与 waterToken(设备会话 1,1)不通用 */
    private var pointsToken by mutableStateOf("")
    private var pointsUid by mutableStateOf("")
    private var pointsCaptchaKey = IlifeClient.newCaptchaKey()
    var pointsState by mutableStateOf(PointsUiState())
    var billState by mutableStateOf(BillUiState())

    /** 账单 id -> 抵扣金额:bill/lst-owner 不带 discount,补过一次详情就缓存住,刷新不重复打 */
    private val billDeducts = HashMap<String, Double>()

    /** 本月起点(毫秒),算本月积分抵扣合计用 */
    private var billMonthStart = 0L

    /** 每次进账单页最多补几笔详情(逐笔串行 120ms,别一次打满 50 个请求) */
    private val BILL_DEDUCT_PROBE = 12
    var skippedLogin by mutableStateOf(false)   // 跳过教务登录(离线/仅用饮水机)

    init {
        // 校外模式:进程启动即把开关下发到 JwClient 拦截器(后续教务请求据此改写)
        runCatching { client.webvpnEnabled = settings.webvpnEnabled }
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
                    selCacheSem = sel.sem
                }
            }
        }
        // 考试安排:同步预载缓存垫底(没连校园网时进考试页直接有内容),
        // 进页面后仍会自动静默刷新一次,拿到新数据就覆盖
        runCatching { primeExamsFromCache() }
        runCatching { primeGradesFromCache() }   // 成绩同理:先上缓存,联网成功再覆盖
        // 积分/抵扣开关的持久值先读进来:进饮水页第一帧就按真实开关态显示,不用等网络
        runCatching {
            pointsState = pointsState.copy(
                autoDaily = settings.waterAutoPoints,
                autoDeduct = settings.waterAutoDeduct,
                phone = settings.waterPointsPhone,
            )
        }
        // 启动时自动检查更新(12 小时节流,镜像站加速)
        viewModelScope.launch(Dispatchers.IO) { checkForUpdate(force = false) }
        // 启动即同步节假日(内部按 7 天/换年节流):只靠"刷课表后同步"会让装完新版没刷课表的用户
        // 在法定假日照常收到上课提醒
        refreshHolidays()
        // 应用存活期间定时复检(每小时醒一次,实际请求仍受 12 小时节流)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60 * 60_000)
                checkForUpdate(force = false)
            }
        }
        // 冷却倒计时(饮水/洗衣机短信共用)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (waterSmsCooldown > 0) waterSmsCooldown--
                if (pointsState.smsCooldown > 0) {
                    pointsState = pointsState.copy(smsCooldown = pointsState.smsCooldown - 1)
                }
                if (pointsState.captchaCooldown > 0) {
                    pointsState = pointsState.copy(captchaCooldown = pointsState.captchaCooldown - 1)
                }
                if (washerSmsCooldown > 0) washerSmsCooldown--
            }
        }
    }

    fun addWaterDevice(did: String, name: String) {
        settings.addWaterDevice(did, name)
        waterScanResult = null
        loadWaterDevices()
    }

    /**
     * 记录一次"扫码命中饮水机"的编号(归一化后),并让 waterScanTick 自增。
     * 重复扫同一台也要能重新定位,所以三处扫码出口都走这里,不要直接写 waterScanResult。
     */
    fun setWaterScan(raw: String) {
        waterScanResult = WaterDeviceIdParser.normalize(raw)
        waterScanTick++
    }

    /**
     * 饮水页处理完扫码信号后回调。扫码是"一次性事件":不清掉的话,离开饮水页再回来时
     * LaunchedEffect 会拿着同一个 waterScanResult 重跑,设备被重复高亮 / 添加框被重复弹出。
     */
    fun consumeWaterScan() {
        waterScanResult = null
    }

    /**
     * 启动成功的涟漪/震动放完后回调。successTick 同样是"一次性事件":
     * 不清掉的话,切回饮水页重新组合时 LaunchedEffect 会重放,于是每次进页面都震两下。
     */
    fun consumeWaterBurst() {
        if (waterState.successTick != 0) waterState = waterState.copy(successTick = 0)
    }

    /** 改饮水机备注:只动本地显示名(设备号是机身码,改不了),不动顺序与在途状态 */
    fun renameWaterDevice(did: String, name: String) {
        val clean = name.trim().ifBlank { "饮水设备" }
        settings.renameWaterDevice(did, clean)
        waterState = waterState.copy(
            devices = waterState.devices.map {
                if (it.first == did) Triple(it.first, clean, it.third) else it
            },
        )
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
            // 官方登录接口已失效:这里不再兜底去拉图形验证码,否则每次进饮水页都会白打一次请求
            waterState = waterState.copy(loggedIn = false)
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
                    if (check.authFailed) {
                        expireWaterLogin()
                    } else {
                        // 网络故障/网关页/业务码:保留 token,只提示,避免误判过期把登录态删掉
                        waterState = waterState.copy(
                            loading = false,
                            message = if (check.code == -999) {
                                "网络异常(${check.msg}),请检查网络后重试"
                            } else {
                                "服务暂时不可用:" + IlifeClient.readable(check.code, check.msg)
                            },
                        )
                    }
                    return@launch
                }
                val accountName = check.json?.optJSONObject("data")?.let { d ->
                    d.optString("name", "").ifBlank { d.optString("nickName", "").ifBlank { d.optString("id", "") } }
                }
                val remote = try { ilife.devices(waterToken) } catch (_: Exception) { emptyList() }
                // 合并手动添加的设备(不覆盖远程)
                val manual = settings.waterDevices.filter { m -> remote.none { it.first == m.first } }
                // 长按排序后的自定义顺序应用;新出现的 did 追加在后
                val all = settings.applyWaterOrder((remote + manual).distinctBy { it.first }) { it.first }
                // 逐台查真实状态:非99=出水中(恢复按钮的出水状态)
                val merged = all.map { (did, name) ->
                    val st = try { ilife.deviceStatus(waterToken, did) } catch (_: Exception) { null }
                    Triple(did, name, st?.drinking ?: (waterState.devices.firstOrNull { it.first == did }?.third ?: false))
                }
                // 锁定当前显示顺序(含新设备),供下次稳定排序
                if (merged.isNotEmpty() && settings.waterOrder != merged.map { it.first }) {
                    settings.waterOrder = merged.map { it.first }
                }
                waterState = waterState.copy(
                    devices = merged,
                    loading = false,
                    message = if (merged.isEmpty()) {
                        accountName?.let { "已登录:$accountName · 暂无设备,可手动添加" } ?: "暂无设备,可手动添加"
                    } else null,
                )
                ensureWaterPoller()
            } catch (e: Exception) {
                waterState = waterState.copy(loading = false, message = "加载失败:${e.message}")
            }
        }
    }

    /**
     * 服务端确认 token 失效(authFailed)时才调用:清本地登录态并回到登录页。
     * 非鉴权失败(网络抖动/频控/网关页)一律走上面的提示分支,不动 token。
     */
    private fun expireWaterLogin() {
        settings.waterToken = ""
        waterToken = ""
        waterState = waterState.copy(
            loggedIn = false,
            loading = false,
            devices = emptyList(),
            message = "登录已过期,请重新登录",
        )
    }

    // ================= 饮水页 · 积分 =================

    /** 金额文案:退款方向(dir=2)前面加 +,支出不加符号 */
    private fun moneyText(v: Double, refund: Boolean = false): String =
        (if (refund) "+¥" else "¥") + String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(v))

    /**
     * 拉一张图形验证码。默认受冷却限制(连点会被服务端当刷码),
     * 登录失败/短信失败这种"旧码已作废"的场景传 force = true 绕过。
     */
    fun newPointsCaptcha(force: Boolean = false) {
        if (!force && pointsState.captchaCooldown > 0) return
        pointsCaptchaKey = IlifeClient.newCaptchaKey()
        pointsState = pointsState.copy(captchaCooldown = 10)
        val url = ilife.captchaUrl(pointsCaptchaKey)
        viewModelScope.launch {
            val bmp: android.graphics.Bitmap? = withContext(Dispatchers.IO) {
                runCatching {
                    val req = okhttp3.Request.Builder().url(url).build()
                    client2.newCall(req).execute().use { r ->
                        r.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
            }
            pointsState = pointsState.copy(captchaBmp = bmp?.asImageBitmap())
        }
    }

    /** 积分通道发短信:走小程序通道(1,5),与设备通道的短信接口不是同一套版本号 */
    fun pointsSendSms() {
        if (pointsState.smsCooldown > 0 || pointsState.loading) return
        pointsState = pointsState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ilife.sendSms(
                pointsState.phone.trim(),
                pointsState.captchaInput.trim(),
                pointsCaptchaKey,
                mini = true,
            )
            pointsState = pointsState.copy(
                loading = false,
                smsSent = r.ok,
                // 成功 60 秒;失败也给 30 秒,防连点把账号打成风控
                smsCooldown = if (r.ok) 60 else 30,
                captchaInput = if (r.ok) pointsState.captchaInput else "",
                message = if (r.ok) "短信已发送" else "发送失败:" + IlifeClient.readable(r.code, r.msg),
            )
            // 短信接口会吃掉这张图形码,失败后换一张再让用户重试
            if (!r.ok) newPointsCaptcha(force = true)
        }
    }

    /** 积分通道登录:body 只有 {authCode, un},签名走 query(sign),多带字段会被判"请升级最新版" */
    fun doPointsLogin() {
        pointsState = pointsState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ilife.login(pointsState.phone.trim(), pointsState.smsCode.trim(), mini = true)
            if (!r.ok) {
                newPointsCaptcha(force = true)
                pointsState = pointsState.copy(
                    loading = false,
                    message = "登录失败:" + IlifeClient.readable(r.code, r.msg),
                )
                return@launch
            }
            val json = r.json ?: JSONObject()
            val tk = ilife.extractToken(json)
            val uid = ilife.extractUid(json)
            if (tk.isBlank()) {
                newPointsCaptcha(force = true)
                pointsState = pointsState.copy(loading = false, message = "登录返回异常:未获取到凭证")
                return@launch
            }
            pointsToken = tk
            pointsUid = uid
            settings.waterPointsToken = tk
            settings.waterPointsUid = uid
            settings.waterPointsPhone = pointsState.phone.trim()
            pointsState = pointsState.copy(
                loggedIn = true, loading = false, message = null,
                smsCode = "", captchaInput = "", smsSent = false, smsCooldown = 0,
                captchaCooldown = 0,
            )
            // 自动抵扣是账号级开关,登录后按本地开关态对齐一次服务端
            if (pointsState.autoDeduct) runCatching { ilife.setUseScore(tk, true) }
            loadPoints()
        }
    }

    /** 拉积分任务列表。进度只认 mission-lst 里的 accScoreRsp,acc-score 的 daily 是上次结算快照 */
    /** 积分任务运行中的 Job;点停止时取消 */
    private var pointsJob: kotlinx.coroutines.Job? = null

    /** 按 token 限流:同一 token 两次 score-send 至少间隔 30 秒(服务端 -98 频控) */
    private val pointsNextAllowed = HashMap<String, Long>()

    private fun reservePointsDelay(token: String): Long {
        val now = System.currentTimeMillis()
        val allowedAt = pointsNextAllowed[token] ?: now
        val scheduledAt = maxOf(now, allowedAt)
        pointsNextAllowed[token] = scheduledAt + 30_000L
        return scheduledAt - now
    }

    /**
     * 载入任务:双通道合并。积分通道(1,5)与设备通道(1,1)的任务列表互补,
     * 同一 adId 只留一条,且 token 与通道必须同进同出(签名按通道选盐)。
     */
    private suspend fun loadPointsMerged(): IlifeClient.MissionPage? {
        if (pointsToken.isBlank()) pointsToken = settings.waterPointsToken
        if (pointsUid.isBlank()) pointsUid = settings.waterPointsUid
        val pt = pointsToken
        val at = waterToken.ifBlank { settings.waterToken }
        val channels = ArrayList<Pair<String, Boolean>>()
        if (pt.isNotBlank()) channels += pt to false
        if (at.isNotBlank() && at != pt) channels += at to true
        if (channels.isEmpty()) return null
        val merged = LinkedHashMap<String, IlifeClient.Mission>()
        var weekMask = 0
        var dailyAdId = ""
        var dailyScore = 5
        var dailyToken = ""
        var dailyApp = false
        var loaded = 0
        var validScore: Int? = null
        var totalScore: Int? = null
        for ((tok, isApp) in channels) {
            val page = runCatching { ilife.missions(tok, pointsUid, isApp) }.getOrNull() ?: continue
            loaded++
            if (dailyAdId.isBlank() && page.dailyAdId.isNotBlank()) {
                dailyAdId = page.dailyAdId
                dailyScore = page.dailyScore
                dailyToken = page.dailyToken.ifBlank { tok }
                dailyApp = page.dailyApp
            }
            weekMask = weekMask or page.weekMask
            validScore = validScore?.let { maxOf(it, page.validScore) } ?: page.validScore
            totalScore = totalScore?.let { maxOf(it, page.totalScore) } ?: page.totalScore
            for (m in page.missions) {
                // 0 分任务不下发奖励,纯占列表位置,过滤掉
                if (m.adId.isBlank() || m.dailySignin || m.score <= 0) continue
                val old = merged[m.adId]
                if (old == null) {
                    merged[m.adId] = m
                } else {
                    val keep = old.limit > 0 || m.limit <= 0
                    merged[m.adId] = old.copy(
                        name = old.name.ifBlank { m.name },
                        score = maxOf(old.score, m.score),
                        limit = if (keep) old.limit else m.limit,
                        done = maxOf(old.done, m.done),
                        token = if (keep) old.token else m.token,
                        app = if (keep) old.app else m.app,
                    )
                }
            }
        }
        if (loaded == 0) return null
        val now = System.currentTimeMillis() + ilife.timeOffsetMillis
        val signedToday = (weekMask and IlifeSign.todayBit(now)) != 0
        val list = ArrayList<IlifeClient.Mission>()
        // 签到不是任务列表里的一项,按下发参数补一条伪任务,列表才完整
        if (dailyAdId.isNotBlank()) {
            list += IlifeClient.Mission(
                adId = dailyAdId,
                name = "每日签到",
                score = dailyScore,
                limit = 1,
                done = if (signedToday) 1 else 0,
                dailySignin = true,
            )
        }
        list += merged.values
        return IlifeClient.MissionPage(
            missions = list,
            validScore = validScore ?: 0,
            totalScore = totalScore ?: 0,
            dailyAdId = dailyAdId,
            dailyScore = dailyScore,
            signedToday = signedToday,
            weekMask = weekMask,
            dailyToken = dailyToken,
            dailyApp = dailyApp,
        )
    }

    fun loadPoints(silent: Boolean = false) {
        if (pointsToken.isBlank()) pointsToken = settings.waterPointsToken
        if (pointsUid.isBlank()) pointsUid = settings.waterPointsUid
        if (pointsToken.isBlank()) {
            pointsState = pointsState.copy(
                loggedIn = false,
                hint = "用慧生活798 账号登录,积分与饮水是同一个手机号",
            )
            return
        }
        pointsState = pointsState.copy(
            loggedIn = true,
            loading = !silent,
            message = if (silent) pointsState.message else null,
        )
        viewModelScope.launch {
            try {
                val page = loadPointsMerged()
                if (page == null) {
                    pointsState = pointsState.copy(loading = false, message = "积分接口无响应,请稍后重试")
                    return@launch
                }
                pointsState = pointsState.copy(
                    loading = false,
                    missions = page.missions,
                    validScore = page.validScore,
                    totalScore = page.totalScore,
                    signedToday = page.signedToday,
                    doneCount = page.missions.count { it.finished },
                )
                maybeAutoRunPoints(page, page.missions.count { !it.finished })
            } catch (e: Exception) {
                pointsState = pointsState.copy(loading = false, message = "加载失败:" + e.message)
            }
        }
    }

    /** 每日自动运行:同一天只跑一次(用本地日期做幂等键),开关关着就什么都不做 */
    private fun maybeAutoRunPoints(page: IlifeClient.MissionPage, pendingCount: Int) {
        if (!pointsState.autoDaily) return
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        if (settings.waterPointsDay == day) return
        if (pendingCount == 0 && page.signedToday) {
            settings.waterPointsDay = day
            return
        }
        if (!pointsState.running) runAllPoints()
    }

    fun stopPoints() {
        pointsJob?.cancel()
        pointsJob = null
        pointsState = pointsState.copy(
            running = false,
            runningName = "",
            logs = pointsState.logs + pointsLogLine("已停止"),
        )
    }

    private fun pointsLogLine(text: String): String {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        return "[" + t + "] " + text
    }

    /**
     * 跑今日全部积分任务。按钮即开关:运行中再点会调用 stopPoints()。
     * 节奏对齐官方 App:同 token 两次请求间隔 30 秒,-98 等 60 秒重试一次仍失败就跳过该任务;
     * 收益不按标称分累加,每步都回查 acc-score 的 totalScore 增量做核验。
     */
    fun runAllPoints() {
        if (pointsState.running) {
            stopPoints()
            return
        }
        val tk = pointsToken.ifBlank { settings.waterPointsToken.also { pointsToken = it } }
        if (pointsUid.isBlank()) pointsUid = settings.waterPointsUid
        if (tk.isBlank()) {
            pointsState = pointsState.copy(message = "请先登录积分账号")
            return
        }
        pointsState = pointsState.copy(running = true, runningName = "", message = null, logs = emptyList())
        pointsNextAllowed.clear()
        pointsJob = viewModelScope.launch {
            val logs = ArrayList<String>()
            fun log(s: String) {
                logs += pointsLogLine(s)
                if (logs.size > 120) logs.removeAt(0)
                pointsState = pointsState.copy(logs = logs.toList())
            }
            var baseline: Int? = runCatching { ilife.pointsSummary(tk)?.second }.getOrNull()
            var verified = 0
            suspend fun verifyPoints(why: String) {
                val total = runCatching { ilife.pointsSummary(tk)?.second }.getOrNull()
                if (total == null) {
                    log("[" + why + "] 到账核验失败,稍后统一核对")
                    return
                }
                val base = baseline
                if (base == null) {
                    baseline = total
                    return
                }
                val delta = total - base
                if (delta > 0) {
                    verified += delta
                    baseline = total
                    log("[" + why + "] 到账核验 +" + delta + " 分,本轮累计 " + verified + " 分")
                } else {
                    log("[" + why + "] 请求成功但积分未增加,可能已计过账")
                }
            }
            try {
                log("正在加载任务列表…")
                val page = loadPointsMerged()
                if (page == null) {
                    log("任务列表加载失败")
                    pointsState = pointsState.copy(running = false, runningName = "", message = "任务列表加载失败")
                    return@launch
                }
                val signMission = page.missions.firstOrNull { it.dailySignin }
                if (signMission != null && !signMission.finished && page.dailyAdId.isNotBlank()) {
                    // 签到参数由哪个通道下发,签名就用哪个通道的 token 与盐
                    val signTok = page.dailyToken.ifBlank { tk }
                    val signApp = page.dailyApp
                    val wait = reservePointsDelay(signTok)
                    if (wait > 0) {
                        log("等待 " + (wait / 1000) + " 秒后签到…")
                        kotlinx.coroutines.delay(wait)
                    }
                    // 签到的周几是 1..7(周一=1),不是 0..6
                    val weekDay = ((java.util.Calendar.getInstance(java.util.Locale.CHINA)
                        .get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                    log("每日签到:执行中…")
                    var r = ilife.signIn(signTok, pointsUid, page.dailyAdId, page.dailyScore, weekDay, signApp)
                    if (r.code == -98) {
                        log("每日签到:请求频繁,等待 60 秒重试…")
                        kotlinx.coroutines.delay(60_000L)
                        r = ilife.signIn(signTok, pointsUid, page.dailyAdId, page.dailyScore, weekDay, signApp)
                    }
                    if (r.ok) {
                        log("每日签到:请求成功,正在核验到账")
                        verifyPoints("每日签到")
                    } else {
                        log("每日签到失败:" + IlifeClient.readable(r.code, r.msg))
                    }
                } else {
                    log("每日签到:今日已签到")
                }
                val todo = page.missions.filter {
                    !it.dailySignin && !it.finished && it.limit > 0 && it.score > 0 &&
                        // 金融营销类任务直接跳过
                        listOf("免费权益", "借贷", "贷款").none { k -> k in it.name }
                }
                log("共 " + todo.size + " 个可执行任务")
                for (m in todo) {
                    pointsState = pointsState.copy(runningName = m.name)
                    val execToken = m.token.ifBlank { tk }
                    var round = m.done
                    var skipRest = false
                    while (round < m.limit && !skipRest) {
                        round++
                        val wait = reservePointsDelay(execToken)
                        if (wait > 0) {
                            log("[" + m.name + "] 等待 " + (wait / 1000) + " 秒…")
                            kotlinx.coroutines.delay(wait)
                        }
                        log("[" + m.name + "] (" + round + "/" + m.limit + ") 执行中…")
                        var r = ilife.sendMission(execToken, pointsUid, m.adId, m.score, m.app)
                        if (r.code == -98) {
                            log("[" + m.name + "] 请求频繁,等待 60 秒重试…")
                            kotlinx.coroutines.delay(60_000L)
                            r = ilife.sendMission(execToken, pointsUid, m.adId, m.score, m.app)
                        }
                        if (r.ok) {
                            log("[" + m.name + "] 请求成功,正在核验到账")
                            verifyPoints(m.name)
                        } else if (r.code == -98) {
                            log("[" + m.name + "] 请求频繁,跳过剩余任务")
                            skipRest = true
                        } else {
                            log("[" + m.name + "] 失败:" + IlifeClient.readable(r.code, r.msg))
                        }
                    }
                }
                val latest = runCatching { loadPointsMerged() }.getOrNull()
                if (latest != null) {
                    pointsState = pointsState.copy(
                        missions = latest.missions,
                        validScore = latest.validScore,
                        totalScore = latest.totalScore,
                        signedToday = latest.signedToday,
                        doneCount = latest.missions.count { it.finished },
                    )
                }
                log("任务请求结束,已核验本轮增加 " + verified + " 分")
                settings.waterPointsDay = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                    .format(java.util.Date())
            } catch (e: kotlinx.coroutines.CancellationException) {
                log("任务已停止")
            } catch (e: Exception) {
                log("异常:" + e.message)
                pointsState = pointsState.copy(message = "执行中断:" + e.message)
            } finally {
                pointsState = pointsState.copy(running = false, runningName = "")
                pointsJob = null
            }
        }
    }

    fun setAutoDailyPoints(on: Boolean) {
        settings.waterAutoPoints = on
        pointsState = pointsState.copy(autoDaily = on)
        if (on) {
            loadPoints(silent = true)
        }
    }

    /** 自动积分抵扣:账号级开关,下发 acc/upt;失败要回滚开关态,否则界面骗人 */
    fun setAutoDeductPoints(on: Boolean) {
        val tk = pointsToken.ifBlank { settings.waterPointsToken }
        settings.waterAutoDeduct = on
        pointsState = pointsState.copy(autoDeduct = on)
        if (tk.isBlank()) return
        viewModelScope.launch {
            val r = ilife.setUseScore(tk, on)
            if (!r.ok) {
                settings.waterAutoDeduct = !on
                pointsState = pointsState.copy(
                    autoDeduct = !on,
                    message = "抵扣开关设置失败:" + IlifeClient.readable(r.code, r.msg),
                )
            }
        }
    }

    // ================= 饮水页 · 账单 =================

    /** 拉账单(bill/lst-owner,status=3 已付款),顺带算今日/昨日/本月三档消费 */
    fun loadBills(silent: Boolean = false) {
        if (waterToken.isBlank()) waterToken = settings.waterToken
        if (waterToken.isBlank()) {
            billState = billState.copy(loggedIn = false, loading = false)
            return
        }
        billState = billState.copy(
            loggedIn = true,
            loading = !silent,
            message = if (silent) billState.message else null,
        )
        viewModelScope.launch {
            try {
                val (list, total) = ilife.bills(waterToken, 0, 50)
                val cal = java.util.Calendar.getInstance(java.util.Locale.CHINA)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val monthStart = cal.timeInMillis
                billMonthStart = monthStart
                var today = 0.0
                var yesterday = 0.0
                var month = 0.0
                var monthDeduct = 0.0
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                val items = list.map { b ->
                    if (b.countsAsSpending) {
                        if (b.time >= todayStart) today += b.payment
                        else if (b.time >= yesterdayStart) yesterday += b.payment
                        if (b.time >= monthStart) month += b.payment
                    }
                    // 抵扣:列表接口带就用列表的,没带就用之前补过详情的缓存
                    val deduct = if (billDeducts.containsKey(b.id)) {
                        billDeducts[b.id] ?: 0.0
                    } else {
                        b.discount
                    }
                    if (deduct > 0.0 && b.time >= monthStart) monthDeduct += deduct
                    BillUi(
                        id = b.id,
                        title = b.title,
                        timeText = if (b.time > 0L) fmt.format(java.util.Date(b.time)) else "",
                        statusLine = b.statusLine,
                        amountText = moneyText(b.payment, refund = b.dir == 2),
                        deductText = if (deduct > 0.0) "积分抵扣 " + moneyText(deduct) else null,
                        timeMillis = b.time,
                    )
                }
                billState = billState.copy(
                    loading = false,
                    items = items,
                    total = total,
                    monthText = moneyText(month),
                    todayText = moneyText(today),
                    yesterdayText = moneyText(yesterday),
                    monthDeductText = if (monthDeduct > 0.0) moneyText(monthDeduct) else null,
                    message = if (items.isEmpty()) "暂无账单记录" else null,
                )
                // 列表接口不保证带 discount:补前几笔详情,补到了再把抵扣标与本月合计刷进去
                probeBillDiscounts()
            } catch (e: Exception) {
                billState = billState.copy(loading = false, message = "加载失败:" + e.message)
            }
        }
    }

    /**
     * 逐笔补积分抵扣金额。
     * 只有 bill/view-full 的 data.bill.discount 是准的,一次最多补 [BILL_DEDUCT_PROBE] 笔(120ms 间隔串行),
     * 补过的 id 记进 [billDeducts],刷新时不再重复打。
     */
    private suspend fun probeBillDiscounts() {
        val todo = billState.items.filter { !billDeducts.containsKey(it.id) }.take(BILL_DEDUCT_PROBE)
        if (todo.isEmpty()) return
        var changed = false
        for (b in todo) {
            val d = try {
                ilife.billDiscount(waterToken, b.id)
            } catch (e: Exception) {
                0.0
            }
            billDeducts[b.id] = d
            if (d > 0.0) changed = true
            kotlinx.coroutines.delay(120)
        }
        if (!changed) return
        var monthDeduct = 0.0
        val items = billState.items.map { b ->
            val d = billDeducts[b.id] ?: 0.0
            if (d > 0.0 && b.timeMillis >= billMonthStart) monthDeduct += d
            b.copy(deductText = if (d > 0.0) "积分抵扣 " + moneyText(d) else b.deductText)
        }
        billState = billState.copy(
            items = items,
            monthDeductText = if (monthDeduct > 0.0) moneyText(monthDeduct) else null,
        )
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
                // 扫码即可得知占用状态;存规范徽标(空闲/使用中/故障/离线),修"工作中的机器被标成空闲"
                val badge = UjingClient.scanBadge(enabled, status, reason)
                val deviceTypeId = result.optInt("deviceTypeId", 0)
                if (!enabled) {
                    // 仍把该机器按真实状态记入"我的设备"(存原始二维码供重扫),不进入下单流程
                    settings.addWasherDevice(deviceId, "", deviceTypeId, "", badge, qr = content)
                    washerState = washerState.copy(
                        loading = false,
                        savedWashers = settings.washerDevices,
                        message = "该设备" + badge + "暂不可下单" + (if (reason.isNotBlank()) ":$reason" else "") +
                            (if (status.isNotBlank()) "(状态 $status)" else ""),
                    )
                    return@launch
                }
                val err = loadWasherProgram(deviceId, deviceTypeId, "", badge, scannedQr = content)
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
        scannedQr: String = "",
    ): String? {
        val info = ujing.programInfo(washerToken, deviceId)
        if (!info.ok) {
            return "套餐获取失败:" + UjingClient.readable(info.code, info.msg)
        }
        val storeId = info.json?.optString("storeId", knownStoreId) ?: knownStoreId
        washerScanned[deviceId] = Pair(knownTypeId, storeId)
        val typeName = info.json?.optString("deviceTypeName", "") ?: ""
        val isDryer = typeName.contains("烘干") || typeName.contains("干衣")
        // 记住这台设备(含下单必需信息),下次免扫码
        settings.addWasherDevice(deviceId, info.json?.optString("deviceNo", "") ?: "", knownTypeId, storeId, scannedStatus, isDryer, scannedQr)
        washerState = washerState.copy(savedWashers = settings.washerDevices)
        val models = UjingClient.Parsers.parseModels(info.json)
        val defaultId = UjingClient.Parsers.defaultModelId(models)
        val defaultModel = models.firstOrNull { it.id == defaultId }
        washerState = washerState.copy(
            loading = false,
            scannedDevice = deviceId,
            isDryer = isDryer,
            dryTimeMinutes = defaultModel?.timeMinutes?.takeIf { it > 0 } ?: 60,
            deviceSummary = (info.json?.optString("storeName", "") ?: "") + " " + typeName,
            models = models,
            selectedModelId = defaultId,
            // 各选项组默认:必选型(温度/自洁)取首项,加购型不添加
            selectedAdditions = defaultModel?.additions
                ?.associate { it.key to com.ncepu.jw.data.UjingClient.Parsers.defaultSelection(it) }
                ?: emptyMap(),
            message = null,
        )
        return null
    }

    /**
     * 活跃订单状态 → 设备忙碌。含 20 待启动 / 22 自洁启动中 / 30 自洁中 / 35 自洁完成
     * (5 分钟启动窗口内机器仍被本单占用)/ 40 运行中 / 60·61 故障中
     * (故障单仍占着机器,可点「继续洗衣」恢复);10 待支付不算占用。
     */
    private val activeOrderDevices = mutableSetOf<String>()

    private fun washerBusyStatus(status: String) =
        status in setOf("20", "22", "30", "35", "40", "60", "61")

    fun updateActiveOrder(deviceId: String, status: String) {
        activeOrderDevices.clear()
        if (deviceId.isBlank()) return
        val busy = washerBusyStatus(status)
        if (busy) activeOrderDevices.add(deviceId)
        // 故障中 → 设备卡片直接标「故障」,别还写着"使用中"
        settings.updateWasherStatus(
            deviceId,
            when {
                UjingClient.isFaultStatus(status) -> "故障"
                busy -> "使用中"
                else -> "空闲"
            },
        )
        washerState = washerState.copy(savedWashers = settings.washerDevices)
    }

    /** 修改已保存洗衣机/烘干机的备注,并刷新列表 */
    fun setWasherNote(deviceId: String, note: String) {
        settings.updateWasherNote(deviceId, note)
        washerState = washerState.copy(savedWashers = settings.washerDevices)
    }

    /** 是否使用手动导入的 XLS 课表(开启=不联网刷新;关闭=恢复联网获取) */
    fun setUseImportedXls(enabled: Boolean) {
        scheduleSource = if (enabled) "MANUAL" else "AUTO"
        settings.scheduleSource = if (enabled) "MANUAL" else "AUTO"
        if (enabled) {
            // 立即应用导入的数据
            fallBackToCache()
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
        // 各选项组已选档位按 key 作为下单字段(washTemperatureId/selfCleanId/wp_detergentGearId 等)
        val extras = model.additions.mapNotNull { g ->
            washerState.selectedAdditions[g.key]?.let { g.key to it }
        }.toMap()
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val dryer = washerState.isDryer
            val r = ujing.createOrder(
                washerToken, deviceId, scanned.first, scanned.second, model.id,
                temperatureId = 1,   // 加热机器由 extras 的 washTemperatureId 覆盖
                extras = extras,
                type = if (dryer) 2 else 1,
                dryTime = if (dryer) washerState.dryTimeMinutes * 10 else 0,  // 报告:分计时=分钟×10
            )
            if (!r.ok) {
                washerState = washerState.copy(loading = false, message = "下单失败:" + UjingClient.readable(r.code, r.msg))
                return@launch
            }
            val orderId = r.json?.optString("orderId", "") ?: ""
            val detail = ujing.orderDetail(washerToken, orderId)
            val order = washerOrder(detail.json)
            val selfCleanOn = (model.additions.firstOrNull { it.key == "selfCleanId" }
                ?.let { g -> washerState.selectedAdditions[g.key] ?: UjingClient.Parsers.defaultSelection(g) } ?: 0) != 0
            updateActiveOrder(deviceId, order.status)
            washerState = washerState.copy(loading = false, currentOrder = order, selfCleanOrdered = selfCleanOn)
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
            // PayTask 返回串格式随 SDK 版本而变(resultStatus=9000 / {9000} / "9000"),稳健匹配 9000
            val sdkPaid = Regex("resultStatus[=:\\s{\\\"]+9000").containsMatchIn(result.toString())
            var cur = washerOrder(ujing.orderDetail(washerToken, order.orderId).json)
            // 支付结果补查:SDK 回调可能丢失,轮询订单是否已脱离"待支付(10)"(报告 §3.6)
            var tries = 0
            while (cur.status == "10" && tries < 8) {
                kotlinx.coroutines.delay(1500); tries++
                val dd = runCatching { ujing.orderDetail(washerToken, order.orderId) }.getOrNull()
                if (dd?.json != null) cur = washerOrder(dd.json)
                else runCatching { ujing.lastPayStatus(washerToken, order.orderId) }  // 备用探测,触发后端结算落库
            }
            val paid = sdkPaid || (cur.status.isNotEmpty() && cur.status != "10")
            updateActiveOrder(washerState.scannedDevice ?: "", cur.status)
            washerState = washerState.copy(
                loading = false,
                currentOrder = cur,
                message = when {
                    paid && cur.status != "10" && !sdkPaid -> "支付已确认(补查)"
                    paid && washerState.autoStartAfterPay -> "支付成功,3 秒后自动启动洗衣机"
                    paid -> "支付成功"
                    else -> "支付未完成,可在订单中重试"
                },
            )
            if (paid && washerState.autoStartAfterPay) {
                kotlinx.coroutines.delay(3000)
                val sr = ujing.startOrder(washerToken, order.orderId)
                if (!UjingClient.commandAccepted(sr)) {
                    washerState = washerState.copy(message = "自动启动失败:" + UjingClient.commandError(sr))
                }
                val d2 = ujing.orderDetail(washerToken, order.orderId)
                washerState = washerState.copy(currentOrder = washerOrder(d2.json))
            }
            if (paid) {
                ensureWasherPoller()
                com.ncepu.jw.update.WasherWatchService.start(getApplication(), order.orderId)
            }
        }
    }

    // ---------- 订单状态轮询(前台 UI)+ 后台由 WasherWatchService 通知 ----------
    private var washerPollJob: kotlinx.coroutines.Job? = null

    /**
     * 解析订单详情并补上 deviceTypeId:详情接口不一定带这个字段,而故障码必须按机型查子表
     * (查错表会显示"未收录的故障码")。优先用扫码时记下的机型,再回退到已保存设备。
     */
    private fun washerOrder(detail: org.json.JSONObject?): WasherOrderInfo {
        val o = UjingClient.Parsers.parseOrder(detail)
        if (o.deviceTypeId != 0) return o
        val did = o.deviceId.ifBlank { washerState.scannedDevice.orEmpty() }
        val t = washerScanned[did]?.first
            ?: settings.washerDevices.firstOrNull { it.did == did }?.deviceTypeId
            ?: 0
        return if (t != 0) o.copy(deviceTypeId = t) else o
    }

    /**
     * 订单是否已结束(可收起卡片、停掉监控)。终态口径:
     *  - UjingClient.TERMINAL_STATUSES(50 完成 / 51 超时未支付 / 52 启动失败 / 53 取消 / 54 超时未启动);
     *  - 或 statusRemark 里明说完成/结束/取消/已结算。
     * **60/61 故障中不算结束** —— 卡片要留着,用户可以点「继续洗衣」把机器跑完
     * (逆向 orderDetail.js:contiuneBtn 只在 60/61 出现)。
     */
    private fun isWasherTerminal(o: WasherOrderInfo): Boolean {
        if (o.faulted) return false
        return o.terminal ||
            o.statusText.contains("完成") || o.statusText.contains("结束") ||
            o.statusText.contains("取消") || o.statusText.contains("已结算")
    }

    /**
     * 前台 UI 刷新用:仅同步订单(通知统一由 WasherWatchService 发,避免前后台重复)。
     * 订单真正结束才停掉监控服务;故障中反而要保证它还在盯(用户可能离开 App 去关好门)。
     */
    private fun applyWasherOrder(o: WasherOrderInfo) {
        if (isWasherTerminal(o)) {
            WasherWatchService.stop(getApplication())
        } else if (o.faulted && settings.watchOrderId != o.orderId) {
            // 故障单要保证后台还在盯(用户可能离开 App 去关好门);已在盯就别重复起服务
            WasherWatchService.start(getApplication(), o.orderId)
        }
    }

    private suspend fun refreshWasherOrderSilently() {
        val cur = washerState.currentOrder ?: return
        val d = runCatching { ujing.orderDetail(washerToken, cur.orderId) }.getOrNull() ?: return
        val no = washerOrder(d.json)
        applyWasherOrder(no)
        updateActiveOrder(washerState.scannedDevice ?: "", no.status)
        if (isWasherTerminal(no)) {
            // 订单完成/取消 → 自动收起订单卡片(完成通知由前台服务发)
            washerState = washerState.copy(currentOrder = null, message = "订单已结束(${no.statusText.ifBlank { "完成" }}),已自动收起")
        } else {
            washerState = washerState.copy(currentOrder = no)
        }
    }

    private fun ensureWasherPoller() {
        val o = washerState.currentOrder ?: return
        if (isWasherTerminal(o)) return
        if (washerPollJob?.isActive == true) return
        washerPollJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15000)
                if (washerState.currentOrder == null) break
                refreshWasherOrderSilently()
                if (washerState.currentOrder?.let { isWasherTerminal(it) } == true) break
            }
            washerPollJob = null
        }
    }

    fun washerRefresh() {
        val order = washerState.currentOrder ?: return
        washerState = washerState.copy(loading = true)
        viewModelScope.launch {
            val d = ujing.orderDetail(washerToken, order.orderId)
            val no = washerOrder(d.json)
            applyWasherOrder(no)
            updateActiveOrder(washerState.scannedDevice ?: "", no.status)
            if (isWasherTerminal(no)) {
                washerState = washerState.copy(loading = false, currentOrder = null, message = "订单已结束(${no.statusText.ifBlank { "完成" }}),已自动收起")
            } else {
                washerState = washerState.copy(loading = false, currentOrder = no)
                ensureWasherPoller()
            }
        }
    }

    fun washerStart() {
        val order = washerState.currentOrder ?: return
        viewModelScope.launch {
            val r = ujing.startOrder(washerToken, order.orderId)
            if (!UjingClient.commandAccepted(r)) {
                washerState = washerState.copy(message = "启动失败:" + UjingClient.commandError(r))
            }
            washerRefresh()
        }
    }

    /**
     * 故障后「继续洗衣」:GET orders/{id}/control/continue(无请求体)。
     * 官方只在 status∈{60,61} 展示该按钮 —— 面板报错、用户排障(多为门没关好)后继续把程序跑完。
     * 受理后立刻刷新状态;未受理则把服务端 errorMessage 原样弹出来。
     */
    fun washerContinue() {
        val order = washerState.currentOrder ?: return
        viewModelScope.launch {
            washerState = washerState.copy(loading = true)
            val r = ujing.continueOrder(washerToken, order.orderId)
            if (!UjingClient.commandAccepted(r)) {
                washerState = washerState.copy(
                    loading = false,
                    message = "继续洗衣失败:" + UjingClient.commandError(r),
                )
            } else {
                washerState = washerState.copy(
                    loading = false,
                    message = "已下发继续指令,请确认机器已恢复运行",
                )
                washerRefresh()
            }
        }
    }

    fun refreshWasherSaved() {
        // 恢复已保存的登录态(重启后免登录)
        if (washerToken.isBlank()) washerToken = settings.washerToken
        // 自愈:把旧版本残留的门店级"空闲 X/共 Y"等脏状态归一到"空闲"(保留 空闲/使用中/故障/离线)
        val validStatus = setOf("使用中", "空闲", "故障", "离线", "忙碌")
        settings.washerDevices.forEach { w ->
            if (w.status !in validStatus) settings.updateWasherStatus(w.did, "空闲")
        }
        washerState = washerState.copy(
            loggedIn = washerToken.isNotBlank(),
            savedWashers = settings.washerDevices,
        )
        washerResumeRunning()
        refreshWasherOccupancy()
    }

    private var washerStatusJob: kotlinx.coroutines.Job? = null

    /**
     * 利用扫码接口做状态监测:对每台已保存设备,用其设备号当 qrCode 再调 `scanWasherCode`,
     * 按返回的 createOrderEnabled/status 刷新 空闲/使用中/故障/离线 徽标(进洗衣页时一次,串行带小间隔)。
     * 设备号不能经此接口查状态时(scanWasherCode 无 createOrderEnabled)保留原状态,不误改。
     */
    private fun refreshWasherOccupancy() {
        if (washerToken.isBlank()) return
        val saved = settings.washerDevices
        if (saved.isEmpty()) return
        if (washerStatusJob?.isActive == true) return
        washerStatusJob = viewModelScope.launch {
            var changed = false
            for (w in saved) {
                // scanWasherCode 需二维码原始内容:优先用存下的原始 qr,退回设备号
                val qr = w.qr.ifBlank { w.name }
                if (qr.isBlank()) continue
                val badge = runCatching {
                    val r = ujing.scanWasher(washerToken, qr)
                    UjingClient.scanStatusFromResult(r.json?.optJSONObject("result"))
                }.getOrNull()
                if (badge != null && badge != w.status) {
                    settings.updateWasherStatus(w.did, badge); changed = true
                }
                kotlinx.coroutines.delay(250)   // 轻节流,避免对生产高频
            }
            if (changed) washerState = washerState.copy(savedWashers = settings.washerDevices)
        }
    }

    /** 进入洗衣页:拉一次进行中订单,恢复"正在跑"的订单详情/启动按钮(报告 §3.3) */
    private fun washerResumeRunning() {
        if (washerToken.isBlank() || washerState.currentOrder != null) return
        viewModelScope.launch {
            val full = runCatching { ujing.runningOrders(washerToken) }.getOrNull() ?: return@launch
            val first = UjingClient.Parsers.firstRunning(full) ?: return@launch
            val (orderId, deviceId) = first
            val d = runCatching { ujing.orderDetail(washerToken, orderId) }.getOrNull() ?: return@launch
            val order = washerOrder(d.json)
            if (order.orderId.isBlank()) return@launch
            val did = deviceId.ifBlank { washerState.scannedDevice ?: "" }
            if (did.isNotBlank()) washerState = washerState.copy(scannedDevice = did)
            washerState = washerState.copy(currentOrder = order, message = "已恢复进行中的订单")
            if (did.isNotBlank()) updateActiveOrder(did, order.status)
            ensureWasherPoller()
            WasherWatchService.start(getApplication(), order.orderId)
        }
    }

    /** 每台设备在途的操作(防连点竞态:迟到的 start 响应不得覆盖之后的 end) */
    private val waterOps = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val waterLastOpAt = HashMap<String, Long>()
    private val waterIdle = HashMap<String, Int>()
    private var waterPollJob: kotlinx.coroutines.Job? = null

    fun startWaterDevice(did: String) = toggleWaterDevice(did, start = true)

    fun endWaterDevice(did: String) = toggleWaterDevice(did, start = false)

    /** 拖动排序:把 from 位置的饮水机移到 to 位置(其余相对顺序不变),更新显示并持久化 */
    fun moveWaterDevice(from: Int, to: Int) {
        val list = waterState.devices.toMutableList()
        if (from < 0 || from >= list.size || to < 0 || to >= list.size || from == to) return
        list.add(to, list.removeAt(from))
        waterState = waterState.copy(devices = list)
        settings.waterOrder = list.map { it.first }
    }

    private fun setWaterRunning(did: String, running: Boolean) {
        waterState = waterState.copy(
            devices = waterState.devices.map {
                if (it.first == did) Triple(it.first, it.second, running) else it
            },
        )
    }

    /**
     * 出水/结束:点"出水"→按钮"结束出水"并起真实状态轮询(ui/app/dev/status);
     * 点"结束"成功或报错(机身已停)→结束;轮询发现机身停止也自动收尾。
     */
    private fun toggleWaterDevice(did: String, start: Boolean) {
        if (waterOps[did]?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - (waterLastOpAt[did] ?: 0L) < 1200L) return  // 防连点触发频控(1.2s:再长用户会以为按钮没反应)
        waterLastOpAt[did] = now
        setWaterRunning(did, start)
        waterState = waterState.copy(loading = false, message = null)
        if (start) {
            // 点下去立刻给反馈(涟漪 + 双段震动),不等网络返回:
            // 之前 successTick 在请求成功后才 +1,接口慢的时候按了像没反应
            waterIdle[did] = 0
            waterState = waterState.copy(
                successDid = did,
                successTick = waterState.successTick + 1,
            )
        }
        waterOps[did] = viewModelScope.launch {
            try {
                if (start) {
                    val r = ilife.start(waterToken, did)
                    if (r.ok) {
                        ensureWaterPoller()
                    } else {
                        setWaterRunning(did, false)
                        waterState = waterState.copy(message = "启动失败:${IlifeClient.readableDevice(r.code, r.msg)}")
                        // 真过期才清登录态回到登录页;频控/需人机验证/上一笔未结束等只提示
                        if (r.authFailed) expireWaterLogin()
                    }
                } else {
                    val r = ilife.end(waterToken, did)
                    setWaterRunning(did, false); waterIdle[did] = 0
                    waterState = waterState.copy(loading = false)
                    // 结束是一次性结果,不留在页面上:3s 后自动收起
                    flashWaterMessage(if (r.ok) "已结束出水" else "该设备可能已停止出水")
                    ensureWaterPoller()
                }
            } catch (e: Exception) {
                waterState = waterState.copy(loading = false)
                setWaterRunning(did, false)
                if (!start) flashWaterMessage("该设备可能已停止出水")
                else waterState = waterState.copy(message = "启动异常:${e.message}")
            } finally {
                waterOps.remove(did)
            }
        }
    }

    /**
     * 一次性状态小字:显示 [millis] 毫秒后自动清掉。
     * 到点先比对文本,期间若被新消息替换就不动它。
     */
    private fun flashWaterMessage(text: String, millis: Long = 3000L) {
        waterState = waterState.copy(message = text)
        viewModelScope.launch {
            kotlinx.coroutines.delay(millis)
            if (waterState.message == text) waterState = waterState.copy(message = null)
        }
    }

    /** 出水期间每 1.5s 轮询真实状态;无设备出水即停轮 */
    private fun ensureWaterPoller() {
        val anyRunning = waterState.devices.any { it.third }
        if (!anyRunning) { waterPollJob?.cancel(); waterPollJob = null; return }
        if (waterPollJob?.isActive == true) return
        waterPollJob = viewModelScope.launch {
            while (waterState.devices.any { it.third }) {
                kotlinx.coroutines.delay(1500)
                val running = waterState.devices.filter { it.third }.map { it.first }
                for (did in running) pollWaterDevice(did)
            }
            waterPollJob = null
        }
    }

    private suspend fun pollWaterDevice(did: String) {
        val st = try { ilife.deviceStatus(waterToken, did) } catch (_: Exception) { null } ?: return
        if (st.drinking) {
            waterIdle[did] = 0
            setWaterRunning(did, true)
        } else {
            val c = (waterIdle[did] ?: 0) + 1; waterIdle[did] = c
            if (c >= 5) {   // 连续空闲判定结束(设备抖动宽容,参考 Super798App)
                setWaterRunning(did, false); waterIdle[did] = 0
                runCatching { ilife.end(waterToken, did) }  // 机身已停,补发结束,忽略错误
            }
        }
    }

    fun tryAutoLogin() {
        if (skippedLogin) return
        // 校外模式用统一认证凭据;校内按上次登录类型选对应凭据(两套密码互不覆盖)
        val creds = if (webvpnEnabled || settings.credentialType() == "sso")
            settings.loadSsoCredentials() ?: settings.loadCredentials()
        else settings.loadCredentials()
        if (creds != null && !loggedIn) {
            account = creds.first; password = creds.second
            // 校外:开机不主动登录(会弹短信码);仅在真正拉教务数据时由 loadSchedule/Grades/Selection/Exams 惰性触发
            if (webvpnEnabled) return
            if (settings.credentialType() == "sso") doSsoLogin() else doLogin()
        }
    }

    fun doLogin() {
        if (webvpnEnabled) { webvpnLogin(); return }
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
        if (webvpnEnabled) { webvpnLogin(); return }
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            val err = client.ssoLogin(account.trim(), password) { requestSmsCode() }
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

    /**
     * 设置:切换校外模式。开启只准备好统一认证凭据 + 复位隧道会话标记,**不自动登录**;
     * 真正要拉教务数据(loadSchedule/loadGrades/loadSelection)时再惰性走 webvpnLogin(按需弹短信码)。
     */
    fun toggleWebvpn(on: Boolean) {
        webvpnEnabled = on
        settings.webvpnEnabled = on
        client.webvpnEnabled = on
        client.webvpnReady = false
        if (!on) return
        val sso = settings.loadSsoCredentials()
        if (sso != null) {
            account = sso.first; password = sso.second
        } else {
            // 无统一认证凭据:回到登录页(校外仅显示统一认证)由用户输入,避免误用教务密码
            password = ""; loggedIn = false
        }
    }

    /** 校外模式且隧道未就绪:触发惰性登录并让调用方中止本次拉取(webvpnLogin 成功后会自行刷新课表/成绩) */
    private fun webvpnNeedsRelogin(): Boolean {
        if (webvpnEnabled && !client.webvpnReady) { webvpnLogin(); return true }
        return false
    }

    /** 切换登录模式时按各自存储回填:有该模式凭据就填入,否则清掉密码(避免教务/统一认证密码互相串用) */
    fun onLoginModeChanged(sso: Boolean) {
        val c = if (sso) settings.loadSsoCredentials() else settings.loadCredentials()
        if (c != null) { account = c.first; password = c.second }
        else password = ""
    }

    /** 校外模式登录:统一认证(SM2)+ 短信 MFA(smsProvider 挂起等用户输入) */
    fun webvpnLogin() {
        if (account.isBlank() || password.isBlank()) { loginError = "请先输入统一认证账号和密码"; return }
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            val err = client.webvpnLogin(account.trim(), password) { requestSmsCode() }
            loginLoading = false
            if (err == null) {
                loggedIn = true
                name = client.studentName
                settings.storeCredentials(account.trim(), password, "sso")
                loadSchedule(force = allCourses.isNotEmpty(), silent = allCourses.isNotEmpty())
                loadGrades()
                if (selectionLoaded) loadSelection(force = true)   // 若惰性登录是为选课触发的,登录成功后补拉
                if (exams.isNotEmpty()) loadExams()
            } else {
                loginError = err
                client.webvpnReady = false
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
    /** 课表失败横幅的「发生序号」:每设一次错误就 +1,跨页面存活 */
    var schedBannerSeq by mutableStateOf(0)
        private set

    /** 已经展示过的序号:同一轮失败从别的页面回到课表不再重弹 */
    var schedBannerAck by mutableStateOf(0)
        private set

    /** 统一出口:设错误的同时推进序号(失败横幅据此决定弹不弹;null 不推进) */
    private fun flagSchedError(msg: String?) {
        schedError = msg
        if (msg != null) schedBannerSeq++
    }

    /** 课表页把当前横幅标记为已读 */
    fun ackSchedBanner() {
        schedBannerAck = schedBannerSeq
    }

    fun loadSchedule(force: Boolean = false, silent: Boolean = false) {
        if (schedLoaded && !force) {
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
            // 先回退缓存(有缓存就继续显示课表),再给原因。
            // 原来写成 `if (!fallBackToCache() && !silent)`:有缓存时 fallBackToCache() 返回 true,
            // 错误被整句吞掉 —— 未登录点刷新什么都不会发生。
            fallBackToCache()
            if (!silent) flagSchedError("请先在「我的」登录教务系统后加载课表")
            return
        }
        // 校外模式且隧道会话未就绪:仅"确实要联网"时才登录。
        // 非强制刷新(即打开课表)→ 有缓存就直接展示、不为拉数据而自动登录;强制刷新(force)才走隧道登录。
        if (webvpnEnabled && !client.webvpnReady) {
            if (!force) { fallBackToCache(); return }
            if (!silent) schedLoading = false
            webvpnLogin()
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
                allCourses = full
                courses = full
                schedLoaded = true
                name = client.studentName ?: name
                settings.cacheCourses(full)
                settings.scheduleUpdatedAtMillis = System.currentTimeMillis()
                schedUpdatedAt = settings.scheduleUpdatedAtMillis
                com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(getApplication())
                refreshHolidays()
                // 手动刷新/切学期重新联网后,恢复自动数据源(覆盖手动导入的固定数据)
                if (force) settings.scheduleSource = "AUTO"
                // 官方当前周:周四锚定(与三请求并发);拿不到则本地推断
                officialWeek = weekDef.await() ?: localCurrentWeek()
                // 已有界面在显示时(静默刷新)保留用户所在周,否则跳到当前周
                if (settings.reminderEnabled) {
                    ReminderScheduler.reschedule(getApplication())
                }
                if (!silent || full.isEmpty()) {
                    flagSchedError(if (full.isEmpty()) "本学期暂无课表(接口返回为空)" else null)
                }
            } catch (e: Exception) {
                // 校外模式:若因会话过期/代理回登录页导致失败,清就绪标记,下次进页自动重连(按需弹码)
                if (webvpnEnabled) {
                    val m = e.message.orEmpty()
                    if (m.contains("会话") || m.contains("登录") || m.contains("chucuole") ||
                        m.contains("重新") ) client.webvpnReady = false
                }
                // 界面上还没有数据时优先回退本地缓存;失败且无缓存才显示错误
                if (allCourses.isEmpty()) {
                    if (!fallBackToCache()) flagSchedError(e.message ?: "加载失败")
                } else if (!silent) {
                    flagSchedError(e.message ?: "加载失败")
                }
            } finally {
                schedLoading = false
            }
        }
    }

    /**
     * 同步法定节假日:拉取当年(11-12 月一并拉次年)放假日期存入本地,
     * 之后上课提醒/小部件据此跳过假期。失败保持原数据(fail-open,绝不因此屏蔽提醒)。
     * force=false 时 7 天内且同年不重复拉取。
     */
    fun refreshHolidays(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val store = settings
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            val year = cal.get(java.util.Calendar.YEAR)
            val stale = force ||
                (now - store.holidaySyncAtMillis) > 7L * 24 * 3600_000 ||
                store.holidayYear != year
            if (!stale) return@launch
            val dates = com.ncepu.jw.data.HolidayClient.fetchYear(year).getOrNull() ?: return@launch
            val merged = dates.toMutableSet()
            // 临近年末:提前把次年假期也取回,避免跨年真空期
            if (cal.get(java.util.Calendar.MONTH) + 1 >= 11) {
                com.ncepu.jw.data.HolidayClient.fetchYear(year + 1).getOrNull()?.let { merged += it }
            }
            store.skippedDates = merged
            store.holidayYear = year
            store.holidaySyncAtMillis = now
            if (store.reminderEnabled) ReminderScheduler.reschedule(getApplication())
            com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(getApplication())
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
            if (info.versionCode > com.ncepu.jw.update.Updater.currentVersionCode(getApplication())) {
                updateState = com.ncepu.jw.update.Updater.State.Available(info)
                updateTargetName = info.versionName
                updatePendingInfo = info
                if (force || info.versionCode > settings.updateSnoozeCode) updatePrompt = true
            } else {
                updateState = com.ncepu.jw.update.Updater.State.Latest(info.versionName)
            }
        }
    }

    fun downloadUpdate() {
        val info = (updateState as? com.ncepu.jw.update.Updater.State.Available)?.info
            ?: updatePendingInfo ?: return
        if (updateState is com.ncepu.jw.update.Updater.State.Downloading) return
        updateTargetName = (updateState as? com.ncepu.jw.update.Updater.State.Available)?.info?.versionName
            ?: updateTargetName
        updatePrompt = true
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

    /** "以后再说":该版本不再弹窗,出现更高版本时重新提醒 */
    fun snoozeUpdate() {
        (updateState as? com.ncepu.jw.update.Updater.State.Available)?.let {
            settings.updateSnoozeCode = it.info.versionCode
        }
        updatePrompt = false
    }

    fun onUpdateAction() {
        when (val s = updateState) {
            is com.ncepu.jw.update.Updater.State.Available -> downloadUpdate()
            is com.ncepu.jw.update.Updater.State.Downloaded -> com.ncepu.jw.update.Updater.install(getApplication(), s.file)
            is com.ncepu.jw.update.Updater.State.Failed ->
                if (updatePendingInfo != null) downloadUpdate() else checkForUpdate(force = true)
            is com.ncepu.jw.update.Updater.State.Idle -> checkForUpdate(force = true)
            else -> {}
        }
    }

    private fun schedDiagText(): String =
        "cache=${settings.loadCachedCourses().size}, source=${settings.scheduleSource}, " +
            "loggedIn=$loggedIn, credType=${settings.credentialType()}"

    /** 未登录/加载失败时回退本地缓存课表;返回是否已有可显示的课表 */
    /**
     * 把考试缓存灌进内存列表。有内容就返回 true(调用方据此决定要不要弹错误)。
     * 内存里已有数据时直接返回 true——它比缓存更新。
     */
    private fun primeExamsFromCache(): Boolean {
        if (exams.isNotEmpty()) return true
        val cached = runCatching { settings.loadCachedExams() }.getOrDefault(emptyList())
        if (cached.isEmpty()) return false
        exams = cached
        examsCachedAt = settings.examsCachedAtMillis
        examFromCache = true
        return true
    }

    private fun fallBackToCache(): Boolean {
        if (schedUpdatedAt <= 0L) schedUpdatedAt = settings.scheduleUpdatedAtMillis
        if (allCourses.isNotEmpty()) { schedDiag = ""; return true }
        val cached = settings.loadCachedCourses()
        if (cached.isEmpty()) { schedDiag = schedDiagText() + ", cacheFile=空"; return false }
        allCourses = cached
        courses = cached
        schedLoaded = true
        schedError = null
        schedDiag = ""
        if (officialWeek <= 0) officialWeek = localCurrentWeek()
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
                if (officialWeek <= 0) officialWeek = localCurrentWeek()
                settings.scheduleSource = "MANUAL"
                settings.cacheCourses(parsed)
                settings.scheduleUpdatedAtMillis = System.currentTimeMillis()
                schedUpdatedAt = settings.scheduleUpdatedAtMillis
                com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(getApplication())
                if (settings.reminderEnabled) {
                    ReminderScheduler.reschedule(getApplication())
                }
                android.widget.Toast.makeText(
                    getApplication(), "已导入 ${parsed.size} 条课程记录", android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                flagSchedError(e.message ?: "导入失败")
                android.widget.Toast.makeText(
                    getApplication(), "导入失败:${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun loadGrades() {
        primeGradesFromCache()             // 有缓存先上屏,别留空白页
        if (webvpnNeedsRelogin()) return   // 校外隧道未就绪:先惰性登录,登录后会自动重来
        // 请求学期快照 + 请求序号:快速切学期时,旧响应不能拿新学期的 key 去过滤,更不能覆盖当前结果
        val sem = gradeSem
        val reqId = ++gradeReqId
        gradeLoading = true
        gradeError = null
        viewModelScope.launch {
            try {
                // 一次拉全部学期:学期下拉要过滤空学期,必须知道每学期有没有成绩。
                // 10 分钟内的数据直接复用,切学期不再发请求。
                val fresh = allGrades.isNotEmpty() && System.currentTimeMillis() - gradeFetchedAt < GRADE_STALE_MS
                if (!fresh) {
                    val page = client.fetchAllGrades()
                    if (reqId != gradeReqId) return@launch   // 过期响应:丢弃
                    gradeFetchedAt = System.currentTimeMillis()
                    if (page.items.isNotEmpty()) {
                        allGrades = page.items
                        // 全量成绩落本地缓存:下次进页面先上屏再看网络,离线也有得看
                        runCatching { settings.cacheGrades(allGrades, account) }
                        gradesCachedAt = settings.gradesCachedAtMillis
                        gradeFromCache = false
                        applyGradeSemesters()
                    } else {
                        // 服务端返回空(多半是没评教/本学期还没出分):不拿空表覆盖已有缓存
                        if (allGrades.isEmpty()) primeGradesFromCache()
                        if (allGrades.isEmpty()) {
                            gradeError = "暂无成绩:未完成教学评价时教务不返回成绩"
                        }
                    }
                }
                if (reqId != gradeReqId) return@launch
                grades = if (gradeSem.isAll) allGrades else allGrades.filter { it.term == gradeSem.key }
                name = client.studentName ?: name
                gradeError = null
            } catch (e: Exception) {
                if (reqId != gradeReqId) return@launch
                if (primeGradesFromCache()) {
                    // 有缓存就继续看缓存,不弹错页(顶部横幅给出原因与时间)
                    gradeError = null
                } else {
                    grades = emptyList()
                    gradeError = e.message ?: "加载失败"
                }
            } finally {
                if (reqId == gradeReqId) gradeLoading = false
            }
        }
    }

    /**
     * 把成绩缓存灌进内存列表(离线查看/失败回退)。
     * 内存里已有数据直接返回 true——它比缓存新。返回是否已可显示成绩。
     */
    private fun primeGradesFromCache(): Boolean {
        if (allGrades.isNotEmpty()) return true
        // 传学号:缓存属于别的账号时 loadCachedGrades 直接返回空表
        val cached = runCatching { settings.loadCachedGrades(account) }.getOrDefault(emptyList())
        if (cached.isEmpty()) return false
        allGrades = cached
        gradesCachedAt = settings.gradesCachedAtMillis
        gradeFromCache = true
        applyGradeSemesters()
        grades = if (gradeSem.isAll) allGrades else allGrades.filter { it.term == gradeSem.key }
        return true
    }

    /** 「2025-2026-1」→ Semester(2025, 1);解析不出来返回 null(补重学期等异常值直接跳过) */
    private fun semesterOfKey(term: String): Semester? {
        val m = Regex("""(\d{4})-(\d{4})-(\d)""").find(term.trim()) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val t = m.groupValues[3].toIntOrNull() ?: return null
        return Semester(y, t)
    }

    /** 从全量成绩归纳「有成绩的学期」;当前选中学期不在其中时自动落到最新一个有成绩的学期 */
    private fun applyGradeSemesters() {
        val set = LinkedHashSet<Semester>()
        for (g in allGrades) semesterOfKey(g.term)?.let { set += it }
        // 第一项固定是「全部学期」:成绩页默认看全部,学分绩才是完整口径
        gradeSemesters = listOf(Semester.ALL) +
            set.sortedWith(compareByDescending<Semester> { it.year }.thenByDescending { it.term })
        if (gradeSemesters.none { it.key == gradeSem.key }) gradeSem = Semester.ALL
    }

    /** 成绩页切学期:数据已在内存,不发请求 */
    fun selectGradeSem(s: Semester) {
        gradeSem = s
        grades = if (s.isAll) allGrades else allGrades.filter { it.term == s.key }
    }

    /**
     * 选课中心:接口有风控,做本地缓存 + 时间频控。
     * - 缓存未过期(SEL_STALE_MS 内)时切换到选课页不再发请求;
     * - 缓存过期才静默刷新(列表已上屏,不闪 loading);失败不打扰。
     * force=true(下拉刷新/重试)绕过频控。
     */
    fun loadSelection(force: Boolean = false) {
        if (selLoading) return
        if (webvpnNeedsRelogin()) return   // 校外隧道未就绪:先惰性登录,登录后自动重来
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
                // 选课结果针对当前学期,用课表学期 schedSem(默认 Semester.current()),不受成绩页切学期影响
                val selected = client.fetchSelectedCourses(schedSem)
                xkRounds = rounds
                // 没有轮次时教务的「已选课程」查询返回空表,和「真的退光了」分不出来。
                // 这时保留已有结果,否则缓存被冲成空,下次启动连旧结果都读不出来。
                val sameSem = selCacheSem.isEmpty() || selCacheSem == schedSem.key
                val keepOld = selected.isEmpty() && selectedCourses.isNotEmpty() && rounds.isEmpty() && sameSem
                if (!keepOld) selectedCourses = selected
                selectionLoaded = true
                selCacheTime = System.currentTimeMillis()
                selCacheSem = schedSem.key
                selError = null
                withContext(Dispatchers.IO) { settings.cacheSelection(rounds, selectedCourses, schedSem.key) }
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

    /**
     * 考试安排。进页面会自动调一次(silent),所以这里带一层节流:
     * - 已有数据且 EXAM_STALE_MS 内拉过 → 直接返回,来回切页不重复打接口;
     * - 已有数据时后台静默刷新:不翻全屏 loading,否则每次进页面都闪一下转圈、把列表变空;
     * - force=true(点刷新 / 切学期 / 重试)绕过节流。
     */
    fun loadExams(silent: Boolean = false, force: Boolean = false) {
        if (webvpnNeedsRelogin()) {        // 校外隧道未就绪:先惰性登录,登录后自动重来
            primeExamsFromCache()          // 等待登录期间先看缓存,别留空白页
            return
        }
        if (examLoading) return            // 请求在途:重入没有意义
        val fresh = System.currentTimeMillis() - examFetchedAt < EXAM_STALE_MS
        if (!force && exams.isNotEmpty() && fresh) return
        val background = silent || exams.isNotEmpty()
        if (!background) {
            examLoading = true
            examError = null
        }
        viewModelScope.launch {
            try {
                exams = client.fetchExams(examSem)
                examFetchedAt = System.currentTimeMillis()
                examsCachedAt = examFetchedAt
                examFromCache = false
                examError = null
                settings.cacheExams(exams)
                ReminderScheduler.reschedule(getApplication())
            } catch (e: Exception) {
                // 拉不到:先拿缓存垫底(没连校园网/服务端抽风都是这条路),
                // 再把原因交给页面——有数据时顶部挂非阻断横幅(与课表页同一套),
                // 一点内容都没有才整屏报错。
                primeExamsFromCache()
                examError = e.message ?: "加载失败"
            } finally {
                examLoading = false
            }
        }
    }

    fun logout() {
        loggedIn = false
        account = ""; password = ""
        name = null   // 否则退登后头部仍显示旧姓名,与"未登录"矛盾
        // 成绩/考试缓存带学号,退登必须一起清掉,否则换个账号登录会先看到上一个人的数据
        runCatching { settings.clearGradeCache() }
        runCatching { settings.clearExamCache() }
        allGrades = emptyList(); gradeFromCache = false; gradesCachedAt = 0L
        gradeSemesters = emptyList(); gradeSem = Semester.ALL; gradeFetchedAt = 0L; gradeError = null
        examFromCache = false; examsCachedAt = 0L
        courses = emptyList(); grades = emptyList()
        xkRounds = emptyList(); selectedCourses = emptyList(); selectionLoaded = false; selCacheTime = 0; selCacheSem = ""
        allCourses = emptyList(); schedLoaded = false
        pyfa = null; pyfaError = null
        exams = emptyList(); examError = null
        loginError = null
        ReminderScheduler.reschedule(getApplication())
        com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(getApplication())
    }
}

/** 未登录提示(课表/成绩页) */
class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    companion object { const val EXTRA_TAB = "widget_tab" }

    private fun applyTabExtra(intent: android.content.Intent?) {
        intent?.getIntExtra(EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let { vm.pendingTab = it }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyTabExtra(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTabExtra(intent)
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

        // 系统栏图标明暗跟随主题。状态栏和导航栏是一对:
        // 应用是 edge-to-edge(见 setDecorFitsSystemWindows(window, false)),
        // 导航栏底色由应用内容透出,但图标颜色必须显式声明——
        // 漏掉它,浅色主题下三键导航会是白图标压浅底、手势导航是白手势条压浅底
        val isDarkNow = themeMode == ThemeMode.DARK ||
            (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
        LaunchedEffect(isDarkNow) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkNow
                isAppearanceLightNavigationBars = !isDarkNow
            }
        }

        LaunchedEffect(Unit) {
            if (!vm.skippedLogin) vm.tryAutoLogin()
            if (vm.settings.waterToken.isNotBlank() || vm.skippedLogin) {
                vm.loadWaterDevices()
            }
            // 官方登录接口已失效:进入饮水页不再请求图形验证码,避免一次无意义的网络请求
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

        var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }

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
                            onOpenScan = { navController.navigate("scan") },
                            onOpenJwxtLogin = { navController.navigate("login") },
                            // 未登录也放行:成绩页自己提示未登录并给登录入口,同时还能看本机缓存
                            onOpenGradesNav = {
                                if (vm.loggedIn && vm.grades.isEmpty()) vm.loadGrades()
                                navController.navigate("grades")
                            },
                            onOpenAbout = { navController.navigate("about") },
                            onEvaluate = {
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                WebViewActivity.webvpnMode = vm.webvpnEnabled
                                val raw = JwClient.DEFAULT_BASE + "/jsxsd/newxspj/zhxspj_list.do"
                                evalLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(WebViewActivity.EXTRA_URL,
                                            if (vm.webvpnEnabled) Webvpn.proxied(raw) else raw)
                                        putExtra(WebViewActivity.EXTRA_TITLE, "教学评价")
                                    }
                                )
                            },
                            onEnterRound = { round ->
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                WebViewActivity.webvpnMode = vm.webvpnEnabled
                                val campus = JwClient.campusUrlOf(round.url)
                                val raw = if (vm.webvpnEnabled) Webvpn.proxied(campus) else campus
                                xkLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(WebViewActivity.EXTRA_URL, raw)
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
                            title = "扫描饮水机",
                            hint = "将饮水机二维码放入框内即可自动识别",
                            onResult = { raw ->
                                // 解析二维码里的设备编号(列表里已有这台就定位到它,否则回填添加框)
                                vm.setWaterScan(raw)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    composable("washer") {
                        // 每次进入洗衣页时恢复已保存的登录态与设备列表
                        androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshWasherSaved() }
                        WasherScreen(
                            // 自定义背景开启时卡片走毛玻璃衬底(无背景图为圆角实底)
                            bgEnabled = appearance.bgHas,
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
                                    // 切换模式重置各选项组:必选型取默认档,加购型不添加
                                    selectedAdditions = models.firstOrNull { it.id == id }
                                        ?.additions?.associate {
                                            it.key to com.ncepu.jw.data.UjingClient.Parsers.defaultSelection(it)
                                        } ?: emptyMap(),
                                )
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
                            onContinueWash = { vm.washerContinue() },
                            onDryTimeChange = { vm.washerState = vm.washerState.copy(dryTimeMinutes = it) },
                            onSetWasherNote = { did, note -> vm.setWasherNote(did, note) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("washerscan") {
                        WaterScanScreen(
                            title = "扫描洗衣机",
                            hint = "将洗衣机二维码放入框内即可自动识别",
                            onResult = { raw ->
                                // scanWasherCode 需要二维码原始内容,这里不做归一化
                                vm.washerScan(raw)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    // 统一扫码入口:按扫到的码自动分流到洗衣机/饮水机
                    composable("scan") {
                        WaterScanScreen(
                            onResult = { raw -> routeScan(raw, vm, navController) },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    composable("exams") {
                        // 每次进页面自动刷新一次:已有数据时静默(不闪转圈),5 分钟内不重复请求
                        LaunchedEffect(Unit) { vm.loadExams(silent = true) }
                        ExamScreen(
                            bgEnabled = appearance.bgHas,
                            semesters = vm.semesters,
                            selected = vm.examSem,
                            loading = vm.examLoading,
                            error = vm.examError,
                            exams = vm.exams,
                            onSemesterChange = { vm.examSem = it; vm.loadExams(force = true) },
                            onRetry = { vm.loadExams(force = true) },
                            onRefresh = { vm.loadExams(force = true) },
                            fromCache = vm.examFromCache,
                            cachedAtMillis = vm.examsCachedAt,
                        )
                    }
                    composable("grades") {
                        GradeScreen(
                            bgEnabled = appearance.bgHas,
                            semesters = vm.gradeSemesters.ifEmpty { listOf(vm.gradeSem) },
                            selected = vm.gradeSem,
                            loading = vm.gradeLoading,
                            error = vm.gradeError,
                            grades = vm.grades,
                            onSemesterChange = { vm.selectGradeSem(it) },
                            onRetry = { vm.loadGrades() },
                            fromCache = vm.gradeFromCache,
                            cachedAtMillis = vm.gradesCachedAt,
                            loggedIn = vm.loggedIn,
                            onLogin = { navController.navigate("login") },
                            onEvaluate = {
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                WebViewActivity.webvpnMode = vm.webvpnEnabled
                                val raw = JwClient.DEFAULT_BASE + "/jsxsd/newxspj/zhxspj_list.do"
                                evalLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(WebViewActivity.EXTRA_URL,
                                            if (vm.webvpnEnabled) Webvpn.proxied(raw) else raw)
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
                    composable("about") {
                        AboutScreen(
                            bgEnabled = appearance.bgHas,
                            versionName = com.ncepu.jw.update.Updater.currentVersionName(ctx),
                            versionCode = runCatching {
                                ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
                            }.getOrDefault(0L),
                            update = vm.updateState,
                            onCheckUpdate = { vm.checkForUpdate(force = true) },
                            onUpdateAction = { vm.onUpdateAction() },
                            onBack = { navController.popBackStack() },
                        )
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
                            webvpnEnabled = vm.webvpnEnabled,
                            onWebvpnChange = { vm.toggleWebvpn(it) },
                            widgetStyle = vm.widgetStyle,
                            onWidgetStyleChange = {
                                vm.widgetStyle = it
                                vm.settings.widgetStyle = it
                                com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(ctx)
                            },
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
                                com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(ctx)
                            },
                            onWeekStartChange = { millis ->
                                vm.settings.weekStartMillis = millis
                                onAppearanceChange(appearance.copy(weekStartMillis = millis))
                                com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(ctx)
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
                            ssoOnly = vm.webvpnEnabled,
                            onModeChange = { vm.onLoginModeChanged(it) },
                        )
                    }
                }
        UpdateDialog(vm)
        WebvpnSmsDialog(vm)
        ScanTargetDialog(vm) { navController.navigate("washer") }
    }

    /** 统一认证短信 MFA 输入框(校内直连与校外隧道登录共用;密码通过后挂起等待) */
    @Composable
    private fun WebvpnSmsDialog(vm: AppViewModel) {
        if (!vm.smsPrompt) return
        var code by androidx.compose.runtime.remember { mutableStateOf("") }
        // 在途保护:登录要过隧道 + 统一认证,这几秒里按钮必须锁住,
        // 连点会把同一个验证码重复提交(服务端一般直接作废)
        var sent by androidx.compose.runtime.remember { mutableStateOf(false) }
        AlertDialog(
            // 防呆:点外部/返回不关闭(避免手滑作废已发的验证码),只能"登录"或"取消"
            onDismissRequest = { },
            title = { Text("短信验证") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("统一认证需要短信验证码,已向你绑定的手机号发送。")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                        label = { Text("短信验证码(6 位)") },
                        singleLine = true,
                        isError = code.isNotEmpty() && code.length < 6,
                        supportingText = {
                            if (code.length < 6) Text("请输入完整的 6 位验证码")
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sent = true
                        vm.submitSms(code)
                    },
                    enabled = code.length == 6 && !sent,
                ) { Text(if (sent) "登录中…" else "登录") }
            },
            dismissButton = { TextButton(onClick = { vm.cancelSms() }) { Text("取消并重新登录") } },
        )
    }

    /**
     * 统一扫码分流拿不准时(见 QrScanRouter.route → ScanTarget.Ambiguous):
     * 不猜页面,让用户点一次 —— 扫饮水机跳洗衣机页就是这么来的。
     */
    @Composable
    private fun ScanTargetDialog(vm: AppViewModel, onOpenWasher: () -> Unit) {
        val raw = vm.scanPendingRaw ?: return
        AlertDialog(
            onDismissRequest = { vm.scanPendingRaw = null },
            title = { Text("这是哪种设备?") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("这个码认不出是饮水机还是洗衣机,你选一下,我进对应页面:")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        raw,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val code = raw
                    vm.scanPendingRaw = null
                    vm.setWaterScan(code)
                    vm.pendingTab = 1
                }) { Text("饮水机") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val code = raw
                    vm.scanPendingRaw = null
                    vm.washerScan(code)
                    onOpenWasher()
                }) { Text("洗衣机 / 烘干机") }
            },
        )
    }

    /** 更新提醒弹窗:按状态呈现 发现新版/下载进度/可安装/失败;关闭=按版本免打扰 */
    @Composable
    private fun UpdateDialog(vm: AppViewModel) {
        val state = vm.updateState
        val actionable = state is com.ncepu.jw.update.Updater.State.Available ||
            state is com.ncepu.jw.update.Updater.State.Downloading ||
            state is com.ncepu.jw.update.Updater.State.Downloaded ||
            state is com.ncepu.jw.update.Updater.State.Failed
        androidx.compose.runtime.LaunchedEffect(state) {
            if (!actionable) vm.updatePrompt = false
        }
        if (!vm.updatePrompt || !actionable) return
        when (state) {
            is com.ncepu.jw.update.Updater.State.Available -> AlertDialog(
                onDismissRequest = { vm.snoozeUpdate() },
                title = { Text("发现新版本 v${state.info.versionName}") },
                text = {
                    Column(Modifier.heightIn(max = 400.dp)) {
                        Text(
                            "当前版本 v${com.ncepu.jw.update.Updater.currentVersionName(
                                androidx.compose.ui.platform.LocalContext.current
                            )},建议升级到 v${state.info.versionName}。"
                        )
                        val notes = state.info.notes.trim()
                        if (notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                },
                confirmButton = { Button(onClick = { vm.downloadUpdate() }) { Text("立即更新") } },
                dismissButton = { TextButton(onClick = { vm.snoozeUpdate() }) { Text("以后再说") } },
            )
            is com.ncepu.jw.update.Updater.State.Downloading -> AlertDialog(
                onDismissRequest = { vm.updatePrompt = false },
                title = { Text("正在下载 v${vm.updateTargetName}") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("已完成 ${state.progress}%(镜像加速,失败自动换源)",
                            style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { vm.updatePrompt = false }) { Text("后台继续") } },
            )
            is com.ncepu.jw.update.Updater.State.Downloaded -> AlertDialog(
                onDismissRequest = { vm.snoozeUpdate() },
                title = { Text("下载完成") },
                text = { Text("v${state.versionName} 已下载并通过校验,点击安装完成更新。") },
                confirmButton = { Button(onClick = { vm.onUpdateAction() }) { Text("立即安装") } },
                dismissButton = { TextButton(onClick = { vm.snoozeUpdate() }) { Text("稍后") } },
            )
            is com.ncepu.jw.update.Updater.State.Failed -> AlertDialog(
                onDismissRequest = { vm.snoozeUpdate() },
                title = { Text("更新未完成") },
                text = { Text(state.message ?: "未知错误") },
                confirmButton = { Button(onClick = { vm.onUpdateAction() }) { Text("重试") } },
                dismissButton = { TextButton(onClick = { vm.snoozeUpdate() }) { Text("关闭") } },
            )
            else -> {}
        }
    }

    private fun isExactAlarmGranted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * 解析本地背景图(采样,避免大图 OOM)。
     * 返回全 App 唯一的一份 android Bitmap:ImageView 显示、Palette 取色、玻璃折射层共用同一份像素,
     * 需要 ImageBitmap 时用 asImageBitmap() 取零拷贝视图,不再逐处 asAndroidBitmap() 复制。
     */
    private fun loadBgBitmap(file: java.io.File): android.graphics.Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    /**
     * 统一扫码分流(见 QrScanRouter):
     * 饮水机 → 切到饮水 tab 并回填 did 弹添加框;其余 → 洗衣机链路(码由厂商服务端判)。
     */
    private fun routeScan(raw: String, vm: AppViewModel, nav: androidx.navigation.NavHostController) {
        when (val target = QrScanRouter.route(raw)) {
            is ScanTarget.Water -> {
                vm.setWaterScan(target.did)
                vm.pendingTab = 1
                nav.popBackStack()
            }
            is ScanTarget.Washer -> {
                vm.washerScan(target.raw)
                // 扫码页不留在返回栈里(从洗衣机页退回不该回到相机)
                nav.navigate("washer") { popUpTo("scan") { inclusive = true } }
            }
            is ScanTarget.Ambiguous -> {
                // 本地认不出是饮水机还是洗衣机:不猜(猜错就是跳错页),退出相机后弹一次选择
                vm.scanPendingRaw = target.raw
                nav.popBackStack()
            }
            is ScanTarget.Unknown -> {
                copyToClipboard(target.raw, "无法识别的二维码,内容已复制")
                nav.popBackStack()
            }
        }
    }

    /** 复制到剪贴板并提示(扫码结果兜底:用户可自行粘到别处) */
    private fun copyToClipboard(text: String, toast: String) {
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("二维码", text))
            android.widget.Toast.makeText(this, toast, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "复制失败:${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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
        onOpenScan: () -> Unit,
        onOpenJwxtLogin: () -> Unit,
        onOpenGradesNav: () -> Unit,
        onOpenAbout: () -> Unit,
        onEvaluate: () -> Unit,
        onEnterRound: (com.ncepu.jw.data.XkRound) -> Unit,
    ) {
        // rememberSaveable:进扫码等子路由时 main 离开组合,返回后需恢复所选 tab(否则回到课表)
        var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
        // 饮水页正在拖拽设备卡片:收起底栏,让底部删除条有完整空间
        var waterDragging by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        // 小部件深链:切到指定 tab(1=饮水)并加载其数据,消费后清空
        androidx.compose.runtime.LaunchedEffect(vm.pendingTab) {
            vm.pendingTab?.let { t ->
                tab = t
                if (t == 1) vm.loadWaterDevices(silent = vm.waterState.devices.isNotEmpty())
                vm.pendingTab = null
            }
        }

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
        // 唯一一份像素:解码得到的 android Bitmap。显示/取色/折射层都复用它,
        // ImageBitmap 只是零拷贝视图(旧实现每处 asAndroidBitmap() 各拷一份,1080×2400 一张就 ~10MB)
        var bgBitmap by remember(appearance.bgHas, appearance.bgVersion) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        val bgBmp: ImageBitmap? = remember(bgBitmap) { bgBitmap?.asImageBitmap() }

        // View 树的挂载/卸载是副作用:写在 remember 初始化块里不可取消、也没有回滚,
        // 换成 DisposableEffect 后随 showBg 变化正确地重挂,离开组合时清空宿主
        DisposableEffect(bgHost, bgImageView, showBg) {
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
            onDispose { bgHost.removeAllViews() }
        }
        val densityValue = LocalDensity.current.density
        // 解码只依赖背景图本身;调模糊滑杆不再从磁盘重解 bitmap
        LaunchedEffect(appearance.bgHas, appearance.bgVersion) {
            if (!appearance.bgHas) {
                bgImageView.setImageDrawable(null)
                bgBitmap = null
                return@LaunchedEffect
            }
            val bmp = withContext(Dispatchers.IO) {
                vm.settings.backgroundFile()?.let { loadBgBitmap(it) }
            }
            // 直接交给 ImageView(不复制):后续 Palette、折射层都读同一份
            bgImageView.setImageBitmap(bmp)
            bgBitmap = bmp
        }

        // 顶栏(左上角标题 / 右上角图标)颜色:开了背景图就用 Palette 从图片顶部一条算一个最易辨识的颜色。
        // 必须把 App 自己叠在图片上方的压暗层算进去(背景图 dim + 顶部 scrim),
        // 否则原图很亮、实际看起来被压得很暗时会算出黑字,直接糊在深色背景上。
        // 同一个 sampler 下发下去,内容区标题("我的设备"/"我的选课结果")再按各自高度带各判一次色。
        val headerSampler = remember(bgBitmap, showBg, appearance.bgDim) {
            if (!showBg) null else bgBitmap?.let {
                HeaderPaletteSampler(it, appearance.bgDim)
            }
        }
        var headerTextColor by remember { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
        LaunchedEffect(headerSampler) {
            headerTextColor = headerSampler?.let { sampler ->
                withContext(Dispatchers.Default) { sampler.colorFor(0f, HEADER_BAND_BOTTOM) }
            }
        }
        // 没开背景图(或图还没解完)时给 null,下游退回主题色
        val headerContentColor = if (showBg) headerTextColor else null
        // 模糊只重设 RenderEffect,与解码解耦
        LaunchedEffect(appearance.bgHas, appearance.bgBlur, densityValue) {
            applyBackgroundBlur(bgImageView, if (appearance.bgHas) appearance.bgBlur else 0f, densityValue)
        }
        LaunchedEffect(appearance.bgDim, appearance.bgVersion) {
            dimDrawable.alpha = (appearance.bgDim.coerceIn(0f, 0.85f) * 255).toInt()
        }

        // 悬浮底栏是浮在内容上方的:量出底栏实高,交给可滚动页面自己留底部空白,
        // 内容就能铺到屏幕底、从底栏玻璃下面穿过(否则底栏上方会留一条空白带)
        val barOverlaysContent = appearance.navShape == NavBarShape.FLOATING
        var bottomBarPx by remember { mutableStateOf(0) }
        val bottomBarInset = with(LocalDensity.current) { bottomBarPx.toDp() }

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
            // 背景图之上、内容之下:上下渐变压暗。只靠整屏均匀 dim 时中部发灰、
            // 顶部时间列和底部内容又不够暗,主体也糊;这里叠两端 scrim,不动用户的 dim 档位
            if (showBg) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.3f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.45f),
                            ),
                        ),
                )
            }
            Scaffold(
                containerColor = if (showBg) Color.Transparent else MaterialTheme.colorScheme.background,
                bottomBar = {
                    // 饮水页拖拽设备时收起底栏;200ms 与 WaterScreen 的 BOTTOM_BAR_EXIT_MS(220ms)配套,
                    // 底栏完全滑出后底部删除条才升起
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !(waterDragging && tab == 1),
                        enter = androidx.compose.animation.fadeIn(tween(200)) +
                            androidx.compose.animation.slideInVertically(tween(200)) { it },
                        exit = androidx.compose.animation.fadeOut(tween(200)) +
                            androidx.compose.animation.slideOutVertically(tween(200)) { it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                    Box(Modifier.fillMaxWidth().onSizeChanged { bottomBarPx = it.height }) {
                    AppBottomBar(
                        shape = appearance.navShape,
                        material = appearance.navMaterial,
                        tab = tab,
                        onSelect = { t ->
                            tab = t
                            when (t) {
                            0 -> if (vm.allCourses.isEmpty() && !vm.skippedLogin) vm.loadSchedule()
                            1 -> {
                                vm.loadWaterDevices(silent = vm.waterState.devices.isNotEmpty())
                                // 上次停在积分/账单页时,进 tab 就把对应数据补上(subTab 没变不会触发 onSubTabChange)
                                if (vm.waterSubTab == 1) vm.loadPoints(silent = true)
                                if (vm.waterSubTab == 2) vm.loadBills(silent = true)
                            }
                            2 -> if (vm.loggedIn) vm.loadSelection()
                            }
                        },
                        isDark = isDark,
                        backdrop = glassBackdrop,
                    )
                    }
                    }
                },
            ) { padding ->
                // 底栏预留不再挂在这一层,而是塞进每个页面自己的壳里(见下面 AnimatedContent):
                // 这一层的尺寸因此与 tab 无关,切换时不会变高变矮
                Box(
                    Modifier
                        .padding(
                            start = padding.calculateStartPadding(LocalLayoutDirection.current),
                            top = padding.calculateTopPadding(),
                            end = padding.calculateEndPadding(LocalLayoutDirection.current),
                        )
                        .fillMaxSize(),
                ) {
                    CompositionLocalProvider(
                        LocalBottomBarInset provides bottomBarInset,
                        LocalHeaderContentColor provides headerContentColor,
                        LocalHeaderPalette provides headerSampler,
                    ) {
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
                                // 两个状态的容器都是整屏的壳,尺寸一致,所以这里不需要 SizeTransform:
                                // 既不会逐帧重排内容(像"斜着滑进中间"),也不会把原页面压矮(像"向上抖")
                        },
                        label = "tab",
                    ) { t ->
                        // 浮动态下饮水页/选课页的列表自己能滚,不预留整块底部空档,内容从底栏玻璃下面穿过;
                        // 课表页是按高度自适应的网格、我的页不可滚动,吃掉底栏预留的高度。
                        // 这层壳始终 fillMaxSize,保证 AnimatedContent 两个状态尺寸一致。
                        val reserveBarSpace = !(barOverlaysContent && (t == 1 || t == 2))
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(bottom = if (reserveBarSpace) padding.calculateBottomPadding() else 0.dp),
                        ) {
                        when (t) {
                            0 -> ScheduleScreen(
                                loading = vm.schedLoading,
                                error = vm.schedError,
                                updatedAtMillis = vm.schedUpdatedAt,
                                allCourses = vm.allCourses,
                                diag = vm.schedDiag,
                                weekStartMillis = appearance.weekStartMillis,
                                officialWeek = vm.officialWeek,
                                sectionTimes = vm.settings.sectionTimes,
                                bgEnabled = showBg,
                                glassBackdrop = glassBackdrop,
                                onRetry = { vm.loadSchedule(force = true) },
                                onScan = onOpenScan,
                                // 导入课表(MANUAL)不做联网刷新:刷新按钮干脆不出现,
                                // 扫码于是落到最右;要重新导入去「我的 → 课表」重新选文件
                                onRefresh = if (vm.scheduleSource == "MANUAL") null
                                else ({ vm.loadSchedule(force = true) }),
                                bannerSeq = vm.schedBannerSeq,
                                bannerAck = vm.schedBannerAck,
                                onBannerAck = { vm.ackSchedBanner() },
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
                                onRenameDevice = { did, name -> vm.renameWaterDevice(did, name) },
                                onReorder = { from, to -> vm.moveWaterDevice(from, to) },
                                onScan = onOpenWaterScan,
                                scanResult = vm.waterScanResult,
                                scanTick = vm.waterScanTick,
                                onScanConsumed = { vm.consumeWaterScan() },
                                onBurstConsumed = { vm.consumeWaterBurst() },
                                bgEnabled = showBg,
                                glassBackdrop = glassBackdrop,
                        onDragActiveChange = { waterDragging = it },
                                subTab = vm.waterSubTab,
                                // 切子页时才拉数据:积分/账单一登录就有,未登录先看登录卡
                                onSubTabChange = {
                                    vm.waterSubTab = it
                                    if (it == 1) vm.loadPoints(silent = true)
                                    if (it == 2) vm.loadBills(silent = true)
                                },
                                points = vm.pointsState,
                                bill = vm.billState,
                                subActions = WaterSubActions(
                                    onPointsPhoneChange = { vm.pointsState = vm.pointsState.copy(phone = it) },
                                    onPointsSmsCodeChange = { vm.pointsState = vm.pointsState.copy(smsCode = it) },
                                    onPointsCaptchaInputChange = { vm.pointsState = vm.pointsState.copy(captchaInput = it) },
                                    onPointsRefreshCaptcha = { vm.newPointsCaptcha() },
                                    onPointsSendSms = { vm.pointsSendSms() },
                                    onPointsLogin = { vm.doPointsLogin() },
                                    onPointsRefresh = { vm.loadPoints() },
                                    onRunAllPoints = { vm.runAllPoints() },
                                    onToggleAutoDaily = { vm.setAutoDailyPoints(it) },
                                    onToggleAutoDeduct = { vm.setAutoDeductPoints(it) },
                                    onBillsRefresh = { vm.loadBills() },
                                    onBillsLogin = {
                                        vm.waterSubTab = 0
                                        vm.loadPoints(silent = true)
                                    },
                                ),
                            )
                            2 -> SelectionScreen(
                                loggedIn = vm.loggedIn,
                                loading = vm.selLoading,
                                error = vm.selError,
                                rounds = vm.xkRounds,
                                courses = vm.selectedCourses,
                                bgEnabled = showBg,
                                glassBackdrop = glassBackdrop,
                                onOpenLogin = onOpenJwxtLogin,
                                onEnterRound = onEnterRound,
                                onRetry = { vm.loadSelection() },
                            )
                            else -> ProfileScreen(
                                account = vm.account,
                                name = vm.name,
                                loggedIn = vm.loggedIn,
                                bgEnabled = showBg,
                                glassBackdrop = glassBackdrop,
                                update = vm.updateState,
                                onUpdateAction = { vm.onUpdateAction() },
                                onOpenJwxtLogin = onOpenJwxtLogin,
                                onOpenSettings = onOpenSettings,
                                onOpenPyfa = onOpenPyfa,
                                onOpenWasher = onOpenWasher,
                                onOpenGrades = onOpenGradesNav,
                                onOpenAbout = onOpenAbout,
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
    }
}
