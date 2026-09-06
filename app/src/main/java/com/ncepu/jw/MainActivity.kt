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
import androidx.compose.ui.graphics.asImageBitmap
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

    fun loadWaterDevices() {
        if (waterToken.isBlank()) waterToken = settings.waterToken
        if (waterToken.isBlank()) {
            waterState = waterState.copy(loggedIn = false)
            refreshCaptcha()
            return
        }
        waterState = waterState.copy(loggedIn = true, loading = true, message = null)
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
                val info = ujing.programInfo(washerToken, deviceId)
                if (!info.ok) {
                    washerState = washerState.copy(loading = false, message = "套餐获取失败:" + UjingClient.readable(info.code, info.msg))
                    return@launch
                }
                val storeId = info.json?.optString("storeId", "") ?: ""
                washerScanned[deviceId] = Pair(deviceTypeId, storeId)
                // 记住这台设备(下次免扫码)
                settings.addWasherDevice(deviceId, info.json?.optString("deviceNo", "") ?: "")
                washerState = washerState.copy(savedWashers = settings.washerDevices)
                val models = UjingClient.Parsers.parseModels(info.json)
                washerState = washerState.copy(
                    loading = false,
                    scannedDevice = deviceId,
                    deviceSummary = (info.json?.optString("storeName", "") ?: "") + " " +
                        (info.json?.optString("deviceTypeName", "") ?: ""),
                    models = models,
                    selectedModelId = UjingClient.Parsers.defaultModelId(models),
                    message = null,
                )
            } catch (e: Exception) {
                washerState = washerState.copy(loading = false, message = "识别异常:" + e.message)
            }
        }
    }

    fun washerCreateOrder() {
        val deviceId = washerState.scannedDevice ?: return
        val scanned = washerScanned[deviceId]
        if (scanned == null) {
            washerState = washerState.copy(message = "请先识别设备")
            return
        }
        val model = washerState.models.firstOrNull { it.first == washerState.selectedModelId }
            ?: washerState.models.firstOrNull { it.first == 1 }
            ?: washerState.models.firstOrNull()
            ?: return
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.createOrder(washerToken, deviceId, scanned.first, scanned.second, model.first, temperatureId = 1)
            if (!r.ok) {
                washerState = washerState.copy(loading = false, message = "下单失败:" + UjingClient.readable(r.code, r.msg))
                return@launch
            }
            val orderId = r.json?.optString("orderId", "") ?: ""
            val detail = ujing.orderDetail(washerToken, orderId)
            val order = UjingClient.Parsers.parseOrder(detail.json)
            washerState = washerState.copy(loading = false, currentOrder = order)
        }
    }

    fun washerPay() {
        val order = washerState.currentOrder ?: return
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.paymentArguments(washerToken, order.orderId)
            val payInfo = r.json?.optJSONObject("payInfo")
            val orderInfo = payInfo?.optString("orderInfo", "") ?: ""
            if (r.ok && orderInfo.isNotBlank()) {
                washerState = washerState.copy(loading = false, payUrl = "已生成支付宝参数")
                openAlipay(orderInfo)
            } else {
                val h5 = payInfo?.optString("h5_url", "") ?: ""
                washerState = washerState.copy(
                    loading = false,
                    message = if (h5.isNotBlank()) "请用浏览器打开 H5 支付链接完成支付" else "支付参数失败:" + UjingClient.readable(r.code, r.msg),
                )
            }
        }
    }

    private fun openAlipay(orderInfo: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("alipays://platformapi/startapp?saId=10000007&orderSuffix=" +
                java.net.URLEncoder.encode(orderInfo, "UTF-8"))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<android.app.Application>().startActivity(intent)
        } catch (_: Exception) {
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
            doLogin()
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
                settings.storeCredentials(account.trim(), password)
                loadSchedule()
                loadGrades()
            } else {
                loginError = err
            }
        }
    }

    /**
     * 加载学期全量课表(GET /jsxsd/xskb/xskb_list.do?xnxq01id=,一次请求),
     * 周次切换全部本地过滤完成,不再按周请求(避免滑动多次后周次漂移)。
     */
    fun loadSchedule(force: Boolean = false) {
        if (schedLoaded && !force) {
            schedMode = "WEEK"
            selectedWeek = officialWeek
            courses = allCourses
            return
        }
        schedLoading = true
        schedError = null
        viewModelScope.launch {
            try {
                // 双源:XLS 导出(格式规整)+ 课表页 HTML 解析。
                // 任一源都可能残缺(实测服务器 HTML 曾只剩周一二),取星期覆盖更全的一份;
                // 两者都失败时抛出真实的错误(会话失效/评教拦截等)
                val xls = try { client.fetchScheduleXls(schedSem) } catch (_: Exception) { null }
                var htmlErr: Exception? = null
                val html = try { client.fetchCourses(schedSem) } catch (e: Exception) { htmlErr = e; null }
                val xlsClean = xls?.takeIf { it.isNotEmpty() }
                val htmlClean = html?.takeIf { it.isNotEmpty() }
                if (xlsClean == null && htmlClean == null) {
                    throw htmlErr ?: JwException("课表获取失败,请稍后重试")
                }
                val xlsDays = xlsClean?.map { it.day }?.distinct()?.size ?: 0
                val htmlDays = htmlClean?.map { it.day }?.distinct()?.size ?: 0
                val full = if (htmlDays > xlsDays) htmlClean!! else xlsClean ?: htmlClean!!
                allCourses = full
                courses = full
                schedLoaded = true
                name = client.studentName ?: name
                settings.cacheCourses(full)
                // 官方当前周:用首页周课表接口取一次(周四锚定,规避周定义边界)
                officialWeek = try {
                    client.fetchHomeWeek(thisWeekThursdayText()).week.takeIf { it > 0 }
                        ?: localCurrentWeek()
                } catch (_: Exception) {
                    localCurrentWeek()
                }
                selectedWeek = officialWeek
                schedMode = "WEEK"
                if (settings.reminderEnabled) {
                    ReminderScheduler.reschedule(getApplication())
                }
                schedError = if (full.isEmpty()) "本学期暂无课表(接口返回为空)" else null
            } catch (e: Exception) {
                schedError = e.message ?: "加载失败"
            } finally {
                schedLoading = false
            }
        }
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
        loadSchedule(force = true)
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

    fun loadSelection() {
        selLoading = true
        selError = null
        viewModelScope.launch {
            try {
                xkRounds = client.fetchXkRounds()
                selectedCourses = client.fetchSelectedCourses(gradeSem)
                selectionLoaded = true
                selError = null
            } catch (e: Exception) {
                selError = e.message ?: "加载失败"
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
        xkRounds = emptyList(); selectedCourses = emptyList(); selectionLoaded = false
        allCourses = emptyList(); schedLoaded = false
        pyfa = null; pyfaError = null
        exams = emptyList(); examError = null
        loginError = null
        ReminderScheduler.reschedule(getApplication())
    }
}

/** 未登录提示(课表/成绩页) */
@Composable
private fun LoginRequired(onGoLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("未登录教务系统", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(8.dp))
        Text(
            "课表、成绩等教务功能需要登录后使用\n饮水机功能可在底部“饮水”标签直接使用\n如校外使用教务，请先连接 EasyConnect",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.padding(20.dp))
        androidx.compose.material3.Button(onClick = onGoLogin) {
            Text("去登录教务系统")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge:内容延伸到状态栏/导航条后面,背景与暗化全屏覆盖
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val settings = SettingsStore(this)

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

        var pendingCropUri by mutableStateOf<android.net.Uri?>(null)

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
                            onOpenWater = { navController.navigate("water") },
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
                            onScan = { navController.navigate("washerscan") },
                            onSelectModel = { id, _ ->
                                vm.washerState = vm.washerState.copy(selectedModelId = id)
                            },
                            onCreateOrder = { vm.washerCreateOrder() },
                            onPay = { vm.washerPay() },
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
                    composable("water") {
                        WaterScreen(
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
                            onAddDevice = { did, name ->
                                vm.addWaterDevice(did, name)
                                vm.waterScanResult = null
                            },
                            onRemoveDevice = { did -> vm.removeWaterDevice(did) },
                            onScan = { navController.navigate("waterscan") },
                            scanResult = vm.waterScanResult,
                            onBack = { navController.popBackStack() },
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
                            reminderEnabled = vm.settings.reminderEnabled,
                            examReminderEnabled = vm.settings.examReminderEnabled,
                            leadMinutes = vm.settings.leadMinutes,
                            sectionTimes = vm.settings.sectionTimes,
                            weekStartMillis = appearance.weekStartMillis,
                            exactAlarmGranted = isExactAlarmGranted(ctx),
                            onThemeModeChange = onThemeModeChange,
                            onPresetChange = onPresetChange,
                            onDynamicColorChange = onDynamicColorChange,
                            onFontScaleChange = onFontScaleChange,
                            onNavShapeChange = { s ->
                                vm.settings.navShape = s
                                onAppearanceChange(appearance.copy(navShape = s))
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
                            onReminderToggle = { applyReminder(it) },
                            onExamReminderToggle = { enabled ->
                                vm.settings.examReminderEnabled = enabled
                                ReminderScheduler.reschedule(ctx)
                            },
                            onLeadChange = { min ->
                                vm.settings.leadMinutes = min
                                ReminderScheduler.reschedule(ctx)
                            },
                            onTimesChange = { times ->
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
        onOpenWater: () -> Unit,
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
        // 背景图解码在 IO 线程,避免阻塞主线程
        LaunchedEffect(appearance.bgHas, appearance.bgVersion, appearance.bgBlur, densityValue) {
            if (!appearance.bgHas) {
                bgImageView.setImageDrawable(null)
                return@LaunchedEffect
            }
            val bmp = withContext(Dispatchers.IO) {
                vm.settings.backgroundFile()?.let { loadBgBitmap(it) }
            }
            bgImageView.setImageBitmap(bmp?.asAndroidBitmap())
            applyBackgroundBlur(bgImageView, appearance.bgBlur, densityValue)
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
                                1 -> vm.loadWaterDevices()
                                2 -> if (!vm.selectionLoaded) vm.loadSelection()
                            }
                        },
                        isDark = isDark,
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
                            0 -> if (!vm.loggedIn) LoginRequired(onGoLogin = onOpenJwxtLogin) else ScheduleScreen(
                                loading = vm.schedLoading,
                                error = vm.schedError,
                                allCourses = vm.allCourses,
                                mode = vm.schedMode,
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
                                onBack = { },
                            )
                            2 -> SelectionScreen(
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
                                onOpenJwxtLogin = onOpenJwxtLogin,
                                onOpenSettings = onOpenSettings,
                                onOpenPyfa = onOpenPyfa,
                                onOpenWater = onOpenWater,
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
