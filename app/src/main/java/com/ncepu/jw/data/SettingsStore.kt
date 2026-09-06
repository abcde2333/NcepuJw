package com.ncepu.jw.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 底部导航栏形状:标准(贴底)/ 悬浮(胶囊) */
enum class NavBarShape { STANDARD, FLOATING }

/** 底部导航栏材质:实色 / 液态玻璃(真折射)/ 高斯模糊 */
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
        const val KEY_EXAM_CACHE = "exam_cache"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"

        /** 六大节默认起始时间(大节=两小节连上),依华电实际作息:
         *  1节08:00 / 3节10:00 / 5节14:30 / 7节16:30 / 9节19:30;
         *  华电排课最多到第10节,11-12节仅作占位 */
        val DEFAULT_TIMES = listOf("08:00", "10:00", "14:30", "16:30", "19:30", "21:00")

        /** 大节行 → 索引(与课表网格一致):1-2节=0, 3-4节=1, ... */
        fun sectionRowIndex(startSection: Int): Int = when (startSection) {
            1, 2 -> 0
            3, 4 -> 1
            5, 6 -> 2
            7, 8 -> 3
            9, 10 -> 4
            else -> 5
        }

        /** 当前教学周(1-based) */
        fun currentWeek(now: Long, weekStartMillis: Long): Int {
            val day = 24L * 3600_000
            return ((now - weekStartMillis) / (7 * day)).toInt() + 1
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
            if (saved > 0) return saved
            val cal = java.util.Calendar.getInstance()
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val year = cal.get(java.util.Calendar.YEAR)
            val anchor = java.util.Calendar.getInstance().apply {
                clear()
                set(if (month >= 8 || month <= 1) year else year - 1, if (month >= 8 || month <= 1) 8 else 2, 1, 0, 0, 0)
            }
            // 移到周一(周一=2)
            val dow = anchor.get(java.util.Calendar.DAY_OF_WEEK)
            val diff = if (dow >= java.util.Calendar.MONDAY) dow - java.util.Calendar.MONDAY
                       else dow + 6 - java.util.Calendar.MONDAY
            anchor.add(java.util.Calendar.DAY_OF_MONTH, -diff)
            return anchor.timeInMillis
        }
        set(v) = prefs.edit().putLong(KEY_WEEK_START, v).apply()

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

    // ---------- 登录凭据 ----------

    fun storeCredentials(account: String, password: String) {
        prefs.edit().putString(KEY_ACCOUNT, account).putString(KEY_PASSWORD, password).apply()
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_PASSWORD).apply()
    }

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
        prefs.edit().putString(KEY_COURSE_CACHE, arr.toString()).apply()
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
}
