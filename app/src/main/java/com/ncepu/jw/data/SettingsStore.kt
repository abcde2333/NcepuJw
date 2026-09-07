package com.ncepu.jw.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** 已保存的洗衣机(含下单必需的 program 信息与最近状态) */
data class SavedWasher(
    val did: String,
    val name: String,
    val deviceTypeId: Int = 0,
    val storeId: String = "",
    val status: String = "",
)

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 底部导航栏形状:标准(贴底)/ 悬浮(胶囊) */
enum class NavBarShape { STANDARD, FLOATING }

/** 底部导航栏材质:实色 / 液态玻璃(Kyant0 backdrop,悬浮形) / 高斯模糊(半透明+背景模糊) */
enum class NavMaterial { SOLID, LIQUID, BLUR }

/** 一天的节次起始时间,如 "08:00" 表示第 1 节 8:00 上课 */
data class SectionTime(val row: Int, val time: String)

/**
 * 轻量设置存储:SharedPreferences。
 * 课表/考试以 JSON 缓存,供上课提醒的 Receiver 在 App 进程死后读取。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("jw", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_PRESET = "theme_preset"          // 主题预设名(见 ThemePresets)
        const val KEY_FONT = "font_scale"              // 1.0
        const val KEY_NAV_SHAPE = "nav_bar_shape"      // NavBarShape 名
        const val KEY_NAV_MATERIAL = "nav_bar_material" // NavMaterial 名
        const val KEY_BG = "schedule_bg"               // 课表背景图文件名
        const val KEY_BG_BLUR = "schedule_bg_blur"     // dp
        const val KEY_BG_DIM = "schedule_bg_dim"       // 0..1
        const val KEY_REMIND = "reminder_enabled"      // 上课提醒
        const val KEY_EXAM_REMIND = "exam_reminder_enabled" // 考试提醒
        const val KEY_LEAD = "reminder_lead_minutes"
        const val KEY_TIMES = "section_times"
        const val KEY_WEEK_START = "week_start_millis" // 第一周周一 00:00
        const val KEY_COURSE_CACHE = "course_cache"
        const val KEY_WATER_TOKEN = "water_token"
        const val KEY_WATER_DEVICES = "water_devices"
        const val KEY_WASHER_TOKEN = "washer_token"
        const val KEY_WASHER_DEVICES = "washer_devices"
        const val KEY_EXAM_CACHE = "exam_cache"
        const val KEY_SEL_CACHE = "selection_cache"
        const val KEY_SCHED_SOURCE = "schedule_source" // AUTO=联网 / MANUAL=手动导入
        const val KEY_UPDATE_CHECK = "update_last_check"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        const val KEY_CRED_TYPE = "credential_type" // jwxt=教务密码 / sso=统一身份认证密码

        /** 五大节默认起始时间(大节=两小节连上),依华电实际作息:
         *  1节08:00 / 3节10:00 / 5节14:30 / 7节16:30 / 9节19:30 */
        val DEFAULT_TIMES = listOf("08:00", "10:00", "14:30", "16:30", "19:30")

        /** 大节行 → 索引(与课表网格一致):1-2节=0, 3-4节=1, ... ;超出 1-10 节返回 -1 */
        fun sectionRowIndex(startSection: Int): Int = when (startSection) {
            1, 2 -> 0
            3, 4 -> 1
            5, 6 -> 2
            7, 8 -> 3
            9, 10 -> 4
            else -> -1
        }

        /** 当前教学周(1-based) */
        fun currentWeek(now: Long, weekStartMillis: Long): Int {
            // 按本地日历"天"对齐再相除:weekStart 可能带 8 小时时区偏移
            // (DatePicker 给的是 UTC 零点),直接除毫秒会在每天 0-8 点把周次算成上一周
            fun localMidnight(t: Long): Long = java.util.Calendar.getInstance().apply {
                timeInMillis = t
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val days = (localMidnight(now) - localMidnight(weekStartMillis)) / (24L * 3600_000)
            return (days / 7).toInt() + 1
        }
    }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(v) = prefs.edit().putString(KEY_THEME, v.name).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, true)
        set(v) = prefs.edit().putBoolean(KEY_DYNAMIC, v).apply()

    var themePreset: String
        get() = prefs.getString(KEY_PRESET, null) ?: "NCEPU"
        set(v) = prefs.edit().putString(KEY_PRESET, v).apply()

    /** 课表数据源:AUTO=联网获取 / MANUAL=手动导入的 XLS(导入后不再自动刷新) */
    var scheduleSource: String
        get() = prefs.getString(KEY_SCHED_SOURCE, null) ?: "AUTO"
        set(v) = prefs.edit().putString(KEY_SCHED_SOURCE, v).apply()

    /** 上次自动检查更新的时间(12 小时节流) */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK, 0L)
        set(v) = prefs.edit().putLong(KEY_UPDATE_CHECK, v).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_FONT, v).apply()

    var navShape: NavBarShape
        get() = runCatching { NavBarShape.valueOf(prefs.getString(KEY_NAV_SHAPE, null) ?: "") }
            .getOrDefault(NavBarShape.STANDARD)
        set(v) = prefs.edit().putString(KEY_NAV_SHAPE, v.name).apply()

    var navMaterial: NavMaterial
        get() = runCatching { NavMaterial.valueOf(prefs.getString(KEY_NAV_MATERIAL, null) ?: "") }
            .getOrDefault(NavMaterial.SOLID)
        set(v) = prefs.edit().putString(KEY_NAV_MATERIAL, v.name).apply()

    var bgBlur: Float
        get() = prefs.getFloat(KEY_BG_BLUR, 0f)
        set(v) = prefs.edit().putFloat(KEY_BG_BLUR, v).apply()

    var bgDim: Float
        get() = prefs.getFloat(KEY_BG_DIM, 0.25f)
        set(v) = prefs.edit().putFloat(KEY_BG_DIM, v).apply()

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMIND, false)
        set(v) = prefs.edit().putBoolean(KEY_REMIND, v).apply()

    var examReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXAM_REMIND, true)
        set(v) = prefs.edit().putBoolean(KEY_EXAM_REMIND, v).apply()

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD, 10)
        set(v) = prefs.edit().putInt(KEY_LEAD, v).apply()

    var sectionTimes: List<String>
        get() {
            val raw = prefs.getString(KEY_TIMES, null) ?: return DEFAULT_TIMES
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrNull()?.takeIf { it.size == DEFAULT_TIMES.size } ?: DEFAULT_TIMES
        }
        set(v) = prefs.edit().putString(KEY_TIMES, JSONArray(v).toString()).apply()

    /** 第一周周一 00:00;未设置时按学期推断(9月学期取9月1日所在周一,春季取3月1日所在周一) */
    var weekStartMillis: Long
        get() {
            val saved = prefs.getLong(KEY_WEEK_START, 0L)
            // 归一化到本地当天 00:00(兼容旧数据里 UTC 零点=北京 8 点的存量)
            if (saved > 0) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = saved
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
            val cal = java.util.Calendar.getInstance()
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val year = cal.get(java.util.Calendar.YEAR)
            // 锚定年份与学期归属对齐(Semester.current):
            //   9-12 月 → 当年 9 月 1 日(秋季学期)
            //   1-2 月  → 去年 9 月 1 日(仍是上年秋季学期,1 月不能用当年 9 月=未来时间)
            //   3-7 月  → 当年 3 月 1 日(春季学期)
            val (anchorYear, anchorMonth0) = when {
                month >= 8 -> year to 8        // Calendar 0-based:8 = 九月
                month <= 2 -> (year - 1) to 8
                else -> year to 2              // 2 = 三月
            }
            val anchor = java.util.Calendar.getInstance().apply {
                clear()
                set(anchorYear, anchorMonth0, 1, 0, 0, 0)
            }
            // 移到周一(周一=2)
            val dow = anchor.get(java.util.Calendar.DAY_OF_WEEK)
            val diff = if (dow >= java.util.Calendar.MONDAY) dow - java.util.Calendar.MONDAY
                       else dow + 6 - java.util.Calendar.MONDAY
            anchor.add(java.util.Calendar.DAY_OF_MONTH, -diff)
            return anchor.timeInMillis
        }
        set(v) {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = v
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            prefs.edit().putLong(KEY_WEEK_START, cal.timeInMillis).apply()
        }

    /** 课表背景图:从相册复制到内部存储,返回是否成功 */
    fun setScheduleBackground(uri: Uri): Boolean = try {
        val input = appContext.contentResolver.openInputStream(uri) ?: return false
        val dirPath = appContext.filesDir.absolutePath
        val filePath = "$dirPath/schedule_bg.jpg"
        val file = java.io.File(filePath)
        input.use { i -> java.io.File(dirPath).mkdirs(); file.outputStream().use { o -> i.copyTo(o) } }
        prefs.edit().putString(KEY_BG, file.name).apply()
        true
    } catch (_: Exception) {
        false
    }

    fun backgroundFile(): java.io.File? {
        val name = prefs.getString(KEY_BG, null) ?: return null
        val path = appContext.filesDir.absolutePath + "/" + name
        val f = java.io.File(path)
        return if (f.exists()) f else null
    }

    fun clearScheduleBackground() {
        backgroundFile()?.delete()
        prefs.edit().remove(KEY_BG).apply()
    }

    // ---------- 慧生活798(饮水机) ----------

    var waterToken: String
        get() = prefs.getString(KEY_WATER_TOKEN, null) ?: ""
        set(v) = prefs.edit().putString(KEY_WATER_TOKEN, v).apply()

    var washerToken: String
        get() = prefs.getString(KEY_WASHER_TOKEN, null) ?: ""
        set(v) = prefs.edit().putString(KEY_WASHER_TOKEN, v).apply()

    /** 扫过码的洗衣机(did → deviceNo) */
    var washerDevices: List<SavedWasher>
        get() {
            val raw = prefs.getString(KEY_WASHER_DEVICES, null) ?: return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    SavedWasher(
                        did = o.optString("did"),
                        name = o.optString("name"),
                        deviceTypeId = o.optInt("deviceTypeId", 0),
                        storeId = o.optString("storeId", ""),
                        status = o.optString("status", ""),
                    )
                }
            }.getOrDefault(emptyList())
        }
        set(v) {
            val arr = org.json.JSONArray()
            v.forEach { w ->
                arr.put(
                    org.json.JSONObject()
                        .put("did", w.did)
                        .put("name", w.name)
                        .put("deviceTypeId", w.deviceTypeId)
                        .put("storeId", w.storeId)
                        .put("status", w.status)
                )
            }
            prefs.edit().putString(KEY_WASHER_DEVICES, arr.toString()).apply()
        }

    fun addWasherDevice(did: String, name: String, deviceTypeId: Int = 0, storeId: String = "", status: String = "") {
        val old = washerDevices.firstOrNull { it.did == did }
        val list = washerDevices.filter { it.did != did }.toMutableList()
        list.add(
            SavedWasher(
                did = did, name = name.ifBlank { old?.name ?: "" },
                deviceTypeId = if (deviceTypeId > 0) deviceTypeId else (old?.deviceTypeId ?: 0),
                storeId = storeId.ifBlank { old?.storeId ?: "" },
                status = status.ifBlank { old?.status ?: "" },
            )
        )
        washerDevices = list
    }

    fun updateWasherStatus(did: String, status: String) {
        val list = washerDevices.map {
            if (it.did == did) it.copy(status = status) else it
        }
        washerDevices = list
    }

    fun removeWasherDevice(did: String) {
        washerDevices = washerDevices.filter { it.did != did }
    }

    /** 手动添加的饮水设备(did → 名称),存 JSON */
    var waterDevices: List<Pair<String, String>>
        get() {
            val raw = prefs.getString(KEY_WATER_DEVICES, null) ?: return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    o.optString("did") to o.optString("name")
                }
            }.getOrDefault(emptyList())
        }
        set(v) {
            val arr = org.json.JSONArray()
            v.forEach { (did, name) ->
                arr.put(org.json.JSONObject().put("did", did).put("name", name))
            }
            prefs.edit().putString(KEY_WATER_DEVICES, arr.toString()).apply()
        }

    fun addWaterDevice(did: String, name: String) {
        val list = waterDevices.filter { it.first != did }.toMutableList()
        list.add(did to name)
        waterDevices = list
    }

    fun removeWaterDevice(did: String) {
        waterDevices = waterDevices.filter { it.first != did }
    }

    // ---------- 登录凭据 ----------

    /** type: "jwxt"=教务密码 / "sso"=统一身份认证密码(重启时走对应登录链路) */
    fun storeCredentials(account: String, password: String, type: String = "jwxt") {
        prefs.edit().putString(KEY_ACCOUNT, account).putString(KEY_PASSWORD, password)
            .putString(KEY_CRED_TYPE, type).apply()
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_PASSWORD).remove(KEY_CRED_TYPE).apply()
    }

    fun credentialType(): String = prefs.getString(KEY_CRED_TYPE, null) ?: "jwxt"

    fun loadCredentials(): Pair<String, String>? {
        val acc = prefs.getString(KEY_ACCOUNT, null) ?: return null
        val pwd = prefs.getString(KEY_PASSWORD, null) ?: return null
        return acc to pwd
    }

    // ---------- 课表缓存 ----------

    fun cacheCourses(courses: List<Course>) {
        val arr = JSONArray()
        for (c in courses) {
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("day", c.day)
                put("start", c.sections.first)
                put("end", c.sections.last)
                put("weeks", c.weeks)
                put("teacher", c.teacher)
                put("room", c.room)
            })
        }
        prefs.edit().putString(KEY_COURSE_CACHE, arr.toString()).commit()
    }

    fun loadCachedCourses(): List<Course> {
        val raw = prefs.getString(KEY_COURSE_CACHE, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Course(
                    name = o.optString("name"),
                    day = o.optInt("day"),
                    sections = o.optInt("start")..o.optInt("end"),
                    weeks = o.optString("weeks"),
                    teacher = o.optString("teacher"),
                    room = o.optString("room"),
                    credit = "",
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---------- 考试缓存(提醒用) ----------

    fun cacheExams(exams: List<Exam>) {
        val arr = JSONArray()
        for (e in exams) {
            arr.put(JSONObject().apply {
                put("type", e.type)
                put("name", e.name)
                put("timeText", e.timeText)
                put("start", e.startMillis)
                put("room", e.room)
                put("seat", e.seat)
            })
        }
        prefs.edit().putString(KEY_EXAM_CACHE, arr.toString()).apply()
    }

    fun loadCachedExams(): List<Exam> {
        val raw = prefs.getString(KEY_EXAM_CACHE, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Exam(
                    type = o.optString("type"),
                    code = "",
                    name = o.optString("name"),
                    timeText = o.optString("timeText"),
                    startMillis = o.optLong("start"),
                    endMillis = 0,
                    room = o.optString("room"),
                    teacher = "",
                    note = "",
                    seat = o.optString("seat"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 选课缓存:轮次 + 已选课程 + 抓取时间(选课接口有风控,频控在调用方做) */
    data class SelCache(val time: Long, val rounds: List<XkRound>, val selected: List<SelectedCourse>)

    fun cacheSelection(rounds: List<XkRound>, selected: List<SelectedCourse>) {
        val root = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("rounds", JSONArray().apply {
                for (r in rounds) put(JSONObject().apply {
                    put("term", r.term); put("name", r.name)
                    put("startText", r.startText); put("endText", r.endText)
                    put("dailyText", r.dailyText); put("status", r.status)
                    put("ongoing", r.ongoing); put("url", r.url)
                })
            })
            .put("selected", JSONArray().apply {
                for (s in selected) put(JSONObject().apply {
                    put("code", s.code); put("name", s.name); put("seq", s.seq)
                    put("group", s.group); put("teacher", s.teacher)
                    put("hours", s.hours); put("credit", s.credit)
                    put("attr", s.attr); put("nature", s.nature)
                })
            })
        prefs.edit().putString(KEY_SEL_CACHE, root.toString()).commit()
    }

    fun loadCachedSelection(): SelCache? {
        val raw = prefs.getString(KEY_SEL_CACHE, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val roundsArr = root.optJSONArray("rounds") ?: JSONArray()
            val selArr = root.optJSONArray("selected") ?: JSONArray()
            val rounds = (0 until roundsArr.length()).mapNotNull { i ->
                val o = roundsArr.optJSONObject(i) ?: return@mapNotNull null
                XkRound(
                    term = o.optString("term"), name = o.optString("name"),
                    startText = o.optString("startText"), endText = o.optString("endText"),
                    dailyText = o.optString("dailyText"), status = o.optString("status"),
                    ongoing = o.optBoolean("ongoing"), url = o.optString("url"),
                )
            }
            val selected = (0 until selArr.length()).mapNotNull { i ->
                val o = selArr.optJSONObject(i) ?: return@mapNotNull null
                SelectedCourse(
                    code = o.optString("code"), name = o.optString("name"), seq = o.optString("seq"),
                    group = o.optString("group"), teacher = o.optString("teacher"),
                    hours = o.optString("hours"), credit = o.optString("credit"),
                    attr = o.optString("attr"), nature = o.optString("nature"),
                )
            }
            SelCache(root.optLong("time"), rounds, selected)
        }.getOrNull()?.takeIf { it.rounds.isNotEmpty() }
    }
}
