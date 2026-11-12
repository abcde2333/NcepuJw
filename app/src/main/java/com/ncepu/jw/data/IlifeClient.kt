package com.ncepu.jw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 慧生活798 客户端。协议对齐 ilife798/ilife798(KMP 参考实现)并按其 2026-11 实测结论修正:
 *
 * 通道与版本号必须严格配对,任一错配都会被判「验签失败」/「请升级最新版应用」:
 *   App 通道      ApplicationType "1,1" + VersionCode 3.1.9   + SIGN_SALT      → 设备控制(开停水)与 stype:1 积分任务
 *   积分通道      ApplicationType "1,5" + 登录 2.0.83 / 业务 2.0.178 + MINI_SIGN_SALT → stype:5 积分任务
 *
 * 登录 body 只有 authCode / un 两个字段(sign 走 query),带 cid/openCode 会在会话建立阶段被拒。
 * 两套 token 的任务列表互补,完整积分任务需要合并。
 */
class IlifeClient {

    companion object {
        const val BASE = "https://i.ilife798.com/api/v1"

        /** App 通道版本号 */
        const val VERSION_CODE = "3.1.9"

        /** 积分通道:登录与业务接口用两个不同的版本号 */
        const val MINI_LOGIN_VERSION = "2.0.83"
        const val MINI_VERSION = "2.0.178"

        const val APP_TYPE_APP = "1,1"
        const val APP_TYPE_MAIN = "1,5"

        private const val UA = "Android_ilife798_" + VERSION_CODE

        /** 生成图形验证码的 s 参数 */
        fun newCaptchaKey(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..10).map { chars.random() }.joinToString("")
        }

        fun readable(code: Int, msg: String): String = when (code) {
            0 -> "成功"
            -2 -> "验证码错误"
            -99 -> "登录已过期,请重新登录"
            -21 -> msg.ifBlank { "需要设备控制登录,请用同一手机号再登录一次" }
            -88 -> "未签约代扣协议,请先在慧生活 App 完成签约"
            -87 -> "签约已过期,请重新签约"
            -52 -> "账户欠费,请充值后使用"
            -20 -> "未绑定一卡通账号,请先在慧生活 App 绑定"
            -19 -> "设备准备中,请稍候再试"
            -82 -> "需要支付"
            -98 -> "操作太频繁,请稍后再试"
            else -> msg.ifBlank { "错误 code=" + code }
        }

        fun readableDevice(code: Int, msg: String): String = when (code) {
            0 -> "成功"
            -99 -> "登录已过期,请重新登录"
            -21 -> "需要设备控制登录,请用同一手机号再登录一次"
            -88 -> "未签约代扣协议,请先在慧生活 App 完成签约"
            -87 -> "签约已过期,请重新签约"
            -52 -> "账户欠费,请充值后使用"
            -20 -> "未绑定一卡通账号,请先在慧生活 App 绑定"
            -19 -> "设备准备中,请稍候再试"
            -82 -> "需要支付"
            else -> if (msg.isNotBlank()) msg else "操作失败(code=" + code + "),请稍候再试"
        }

        /** 设备消费账单标题:设备名 + 消费 */
        fun billTitle(msg: String, cata: Int): String =
            if (msg.isNotBlank()) msg else BillNames.productTypeName(cata)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 服务端时间 - 本地时间(积分任务签名必须用服务端时间,否则 30 秒时间桶错位) */
    @Volatile
    var timeOffsetMillis: Long = 0L
        private set

    data class Result(val code: Int, val msg: String, val json: JSONObject?) {
        val ok: Boolean get() = code == 0

        /** 只有服务端明确判定登录态无效才算掉线:网络故障与频控不能清 token */
        val authFailed: Boolean get() = code == -99 || msg == "HTTP 401" || msg == "HTTP 403"
    }

    /** 积分任务(limit=每日上限,done=今日已完成;refId 即 adId) */
    data class Mission(
        val adId: String,
        val name: String,
        val score: Int,
        val limit: Int,
        val done: Int,
        val dailySignin: Boolean = false,
        /** 该任务所属通道的 token;签名按通道选盐,token 与通道必须同进同出 */
        val token: String = "",
        /** true = App 通道(1,1),false = 小程序积分通道(1,5) */
        val app: Boolean = false,
    ) {
        val finished: Boolean get() = done >= limit
        val remain: Int get() = (limit - done).coerceAtLeast(0)
    }

    /** 积分总览 + 任务列表(同一次响应里的 accScoreRsp 才代表今天的进度) */
    data class MissionPage(
        val missions: List<Mission> = emptyList(),
        val validScore: Int = 0,
        val totalScore: Int = 0,
        val dailyAdId: String = "",
        val dailyScore: Int = 5,
        val signedToday: Boolean = false,
        val weekMask: Int = 0,
        /** 签到参数是哪个通道下发的:sign 必须用同一通道的 token 与盐 */
        val dailyToken: String = "",
        val dailyApp: Boolean = false,
    )

    /** 一条账单 */
    data class Bill(
        val id: String,
        val cata: Int,
        val type: Int,
        val msg: String,
        val status: Int,
        val dir: Int,
        val payment: Double,
        val time: Long,
        /** 抵扣金额(积分抵扣;列表接口通常不带,靠 bill/view-full 补) */
        val discount: Double = 0.0,
    ) {
        val title: String get() = BillNames.productTypeName(cata).let { kind ->
            if (msg.isNotBlank()) msg else kind
        }
        val statusLine: String get() = BillNames.billStatusName(status, dir) + " · " + BillNames.paymentTypeName(type)
        /** 计入消费统计:支出、有金额、非充值 */
        val countsAsSpending: Boolean get() = dir == 1 && payment > 0.0 && cata != 1
    }

    // ---- 底层请求 ----

    private fun request(
        method: String,
        url: String,
        bodyJson: JSONObject? = null,
        token: String? = null,
        appType: String = APP_TYPE_APP,
        versionCode: String = VERSION_CODE,
    ): Result {
        val builder = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("VersionCode", versionCode)
            .header("ApplicationType", appType)
            .header("Accept-Language", "zh-Hans-CN;q=1")
        if (!token.isNullOrBlank()) builder.header("Authorization", token)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        when (method) {
            "POST" -> builder.post((bodyJson?.toString() ?: "{}").toRequestBody(mediaType))
            else -> builder.get()
        }
        http.newCall(builder.build()).execute().use { resp ->
            // Date 头 → 服务端时间偏移。解析失败保持旧值,不用本地时间兜底
            runCatching {
                val date = resp.header("Date") ?: return@runCatching
                val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("GMT")
                val server = fmt.parse(date)?.time ?: return@runCatching
                timeOffsetMillis = server - System.currentTimeMillis()
            }
            val text = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                null
            }
            if (json != null) return Result(json.optInt("code", -999), json.optString("msg", ""), json)
            return Result(-999, "HTTP " + resp.code, null)
        }
    }

    private fun dataArray(r: Result): JSONArray? = r.json?.optJSONArray("data")

    private fun dataObject(r: Result): JSONObject? = r.json?.optJSONObject("data")

    // ---- 登录 ----

    /** 图形验证码图片地址 */
    fun captchaUrl(s: String): String =
        BASE + "/captcha/?s=" + java.net.URLEncoder.encode(s, "UTF-8") + "&r=" + System.currentTimeMillis()

    /** 发送短信验证码。[mini] 决定通道头(1,5 用于积分登录) */
    suspend fun sendSms(phone: String, captchaInput: String, s: String, mini: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("un", phone).put("authCode", captchaInput).put("s", s)
            request(
                "POST",
                BASE + "/acc/login/code",
                body,
                appType = if (mini) APP_TYPE_MAIN else APP_TYPE_APP,
                versionCode = if (mini) MINI_LOGIN_VERSION else VERSION_CODE,
            )
        }

    /**
     * 短信验证码登录。body 只有 authCode/un,sign 走 query —— 这是 2026-11 服务端加验签后的唯一可行形态。
     * [mini] = true 走积分通道(1,5 + 2.0.83 + 小程序盐),否则设备通道(1,1 + 3.1.9 + App 盐)。
     */
    suspend fun login(phone: String, smsCode: String, mini: Boolean = false): Result = withContext(Dispatchers.IO) {
        val sign = IlifeSign.login(phone, smsCode, mini)
        val body = JSONObject().put("authCode", smsCode).put("un", phone)
        request(
            "POST",
            BASE + "/acc/login?sign=" + sign,
            body,
            appType = if (mini) APP_TYPE_MAIN else APP_TYPE_APP,
            versionCode = if (mini) MINI_LOGIN_VERSION else VERSION_CODE,
        )
    }

    /** 设备控制 token 是否有效 */
    suspend fun viewInfo(token: String): Result = withContext(Dispatchers.IO) {
        request("GET", BASE + "/acc/view-info", null, token, appType = APP_TYPE_APP)
    }

    /** 收藏设备列表(设备控制 token) */
    suspend fun devices(token: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val r = request("GET", BASE + "/ui/app/master", null, token, appType = APP_TYPE_APP)
        val favos = dataObject(r)?.optJSONArray("favos") ?: return@withContext emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until favos.length()) {
            val o = favos.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isNotBlank()) out += id to o.optString("name", "")
        }
        out.reversed()
    }

    /** 设备实时状态:status 99=空闲,非 99=正在出水 */
    data class DevStatus(val status: Int) {
        val drinking: Boolean get() = status != 99
    }

    suspend fun deviceStatus(token: String, did: String): DevStatus? = withContext(Dispatchers.IO) {
        val r = request(
            "GET", BASE + "/ui/app/dev/status?did=" + did + "&more=true&promo=false",
            null, token, appType = APP_TYPE_APP,
        )
        if (!r.ok) return@withContext null
        val gene = dataObject(r)?.optJSONObject("device")?.optJSONObject("gene") ?: return@withContext null
        DevStatus(status = gene.optInt("status", 99))
    }

    suspend fun start(token: String, did: String): Result = withContext(Dispatchers.IO) {
        request("GET", BASE + "/dev/start?did=" + did + "&upgrade=true&ptype=91&rcp=false", null, token, appType = APP_TYPE_APP)
    }

    suspend fun end(token: String, did: String): Result = withContext(Dispatchers.IO) {
        request("GET", BASE + "/dev/end?did=" + did, null, token, appType = APP_TYPE_APP)
    }

    // ---- 积分 ----

    /**
     * 积分任务列表。progress 只认同响应的 accScoreRsp:
     * acc-score 的 daily 是上一次结算快照,拿它判断会把昨天读成今天。
     */
    suspend fun missions(token: String, uid: String, isApp: Boolean = false): MissionPage? = withContext(Dispatchers.IO) {
        val r = request(
            "GET", BASE + "/acc/score/mission-lst", null, token,
            appType = if (isApp) APP_TYPE_APP else APP_TYPE_MAIN,
            versionCode = if (isApp) VERSION_CODE else MINI_VERSION,
        )
        if (!r.ok) return@withContext null
        val data = dataObject(r) ?: return@withContext null
        val progress = data.optJSONObject("accScoreRsp")
        val signTime = IlifeSign.toMillis(progress?.optJSONObject("daily")?.optLong("ltime", 0L) ?: 0L)
        val now = System.currentTimeMillis() + timeOffsetMillis
        val week = IlifeSign.weekMask(progress?.optJSONObject("daily")?.optInt("week", 0) ?: 0, signTime, now)
        val signedToday = (week and IlifeSign.todayBit(now)) != 0

        val doneMap = HashMap<String, Int>()
        val limits = progress?.optJSONArray("limits")
        if (limits != null) {
            for (i in 0 until limits.length()) {
                val o = limits.optJSONObject(i) ?: continue
                val refId = o.optString("refId", "")
                if (refId.isBlank()) continue
                doneMap[refId] = o.optInt("limit", 0)
            }
        }

        val out = ArrayList<Mission>()
        val seen = HashSet<String>()
        val arr = data.optJSONArray("missions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val refId = o.optString("refId", "")
            if (refId.isBlank() || !seen.add(refId)) continue
            val limit = o.optInt("limit", 0)
            if (limit <= 0) continue
            val raw = doneMap[refId]
            out += Mission(
                adId = refId,
                name = o.optString("name", "").ifBlank { o.optString("title", "任务") },
                score = o.optInt("score", 0),
                limit = limit,
                done = when {
                    raw == null -> 0
                    raw < 0 -> limit
                    else -> raw
                },
                token = token,
                app = isApp,
            )
        }
        val dailyRsp = data.optJSONObject("dailyRSP")
        val dailyAdId = dailyRsp?.optString("adId", "").orEmpty()
        val dailyScore = dailyRsp?.optInt("score", 5) ?: 5

        MissionPage(
            missions = out,
            validScore = progress?.optInt("validScore", 0) ?: 0,
            totalScore = progress?.optInt("totalScore", 0) ?: 0,
            dailyAdId = dailyAdId,
            dailyScore = dailyScore,
            signedToday = signedToday,
            weekMask = week,
            dailyToken = if (dailyAdId.isBlank()) "" else token,
            dailyApp = isApp,
        )
    }

    /** 积分汇总(累计值,跨天不重置) */
    suspend fun pointsSummary(token: String, isApp: Boolean = false): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val r = request(
            "GET", BASE + "/acc/score/acc-score", null, token,
            appType = if (isApp) APP_TYPE_APP else APP_TYPE_MAIN,
            versionCode = if (isApp) VERSION_CODE else MINI_VERSION,
        )
        if (!r.ok) return@withContext null
        val d = dataObject(r) ?: return@withContext null
        d.optInt("validScore", 0) to d.optInt("totalScore", 0)
    }

    /**
     * 执行一条积分任务。sign 用服务端校准时间。
     *
     * 请求体按通道分叉(官方抓包实测):
     *   App 通道(1,1)   → {adId, type:101, addScore, addScoreType};视频任务 adId=1705776998
     *                     的 addScoreType 是 2,其余是 4;URL 额外带 &s=1 走强校验。
     *   小程序通道(1,5) → 只发 {adId, type:101};多带 addScore 会被判参数错误。
     */
    suspend fun sendMission(
        token: String, uid: String, adId: String, score: Int, isApp: Boolean = false,
    ): Result = withContext(Dispatchers.IO) {
        val sign = IlifeSign.task(adId, token, uid, isApp, System.currentTimeMillis() + timeOffsetMillis)
        val body = JSONObject().put("adId", adId).put("type", 101)
        if (isApp) {
            body.put("addScore", score)
            body.put("addScoreType", if (adId == "1705776998") 2 else 4)
        }
        val url = BASE + "/acc/score/score-send?sign=" + sign + if (isApp) "&s=1" else ""
        request(
            "POST", url, body, token,
            appType = if (isApp) APP_TYPE_APP else APP_TYPE_MAIN,
            versionCode = if (isApp) VERSION_CODE else MINI_VERSION,
        )
    }

    /**
     * 每日签到。字段名按通道变化(写错服务端静默忽略,表现为「成功但不生效」):
     *   小程序通道(1,5) → weekDay(驼峰),只发 {weekDay, adId}
     *   App 通道(1,1)   → weekday(全小写),并带 addScore / addScoreType=1
     */
    suspend fun signIn(
        token: String, uid: String, adId: String, score: Int, weekDay: Int, isApp: Boolean = false,
    ): Result = withContext(Dispatchers.IO) {
        val sign = IlifeSign.task(adId, token, uid, isApp, System.currentTimeMillis() + timeOffsetMillis)
        val body = JSONObject()
        if (isApp) {
            body.put("weekday", weekDay).put("adId", adId)
                .put("addScore", score).put("addScoreType", 1)
        } else {
            body.put("weekDay", weekDay).put("adId", adId)
        }
        val url = BASE + "/acc/score/score-send?sign=" + sign + if (isApp) "&s=1" else ""
        request(
            "POST", url, body, token,
            appType = if (isApp) APP_TYPE_APP else APP_TYPE_MAIN,
            versionCode = if (isApp) VERSION_CODE else MINI_VERSION,
        )
    }

    /** 自动积分抵扣开关(acc/upt useScore) */
    suspend fun setUseScore(token: String, enabled: Boolean): Result = withContext(Dispatchers.IO) {
        val body = JSONObject().put("useScore", if (enabled) 1 else 0)
        request("POST", BASE + "/acc/upt", body, token, appType = APP_TYPE_APP, versionCode = VERSION_CODE)
    }

    // ---- 账单 ----

    /** 账单分页(status 3=已付款)。返回(记录, 总数) */
    suspend fun bills(token: String, page: Int = 0, size: Int = 50, status: Int = 3): Pair<List<Bill>, Int> =
        withContext(Dispatchers.IO) {
            val url = BASE + "/bill/lst-owner?page=" + page + "&size=" + size +
                "&hasCount=" + (page == 0) + "&status=" + status
            val r = request("GET", url, null, token, appType = APP_TYPE_APP, versionCode = VERSION_CODE)
            val arr = dataArray(r) ?: return@withContext emptyList<Bill>() to 0
            val total = r.json?.optInt("size", arr.length()) ?: arr.length()
            val out = ArrayList<Bill>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val time = o.optLong("utime", 0L).takeIf { it > 0L } ?: o.optLong("ctime", 0L)
                out += Bill(
                    id = o.optString("id", ""),
                    cata = o.optInt("cata", 0),
                    type = o.optInt("type", 0),
                    msg = o.optString("msg", ""),
                    status = o.optInt("status", 0),
                    dir = o.optInt("dir", 1),
                    payment = o.optDouble("payment", 0.0),
                    time = IlifeSign.toMillis(time),
                    discount = o.optDouble("discount", 0.0).takeIf { it > 0.0 }
                        ?: o.optJSONObject("promo")?.optDouble("value", 0.0) ?: 0.0,
                )
            }
            out to total
        }

    /**
     * 单笔账单的抵扣金额。
     * 列表接口(bill/lst-owner)不保证返回 discount,明细接口(bill/view-full)的 data.bill.discount 才是准的;
     * 读不到一律返回 0 表示这笔记账没有抵扣。
     */
    suspend fun billDiscount(token: String, id: String): Double = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext 0.0
        try {
            val url = BASE + "/bill/view-full?id=" + java.net.URLEncoder.encode(id, "UTF-8")
            val r = request("GET", url, null, token, appType = APP_TYPE_APP, versionCode = VERSION_CODE)
            r.json?.optJSONObject("data")?.optJSONObject("bill")?.optDouble("discount", 0.0) ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    // ---- 登录响应解析 ----

    fun extractToken(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optString("token").orEmpty()

    fun extractUid(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optString("uid").orEmpty()

    fun extractStype(loginJson: JSONObject): Int =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optInt("stype", 0) ?: 0
}
