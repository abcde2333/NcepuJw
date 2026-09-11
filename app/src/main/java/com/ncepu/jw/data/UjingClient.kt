package com.ncepu.jw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * U净(洗衣机)客户端。协议参考 amamiyakazuki/FlandreSY 的 IoUjingTransport +
 * UjingHttpAdapter(对齐旧版 UjingApi.java 抓包)。
 *
 * 网关:https://phoenix.ujing.online/api/v1/
 * 认证:手机号 + 短信验证码(GET captcha → POST login)→ Bearer token
 * 必带头:x-app-code(ZA=账号 / BA=业务)、x-app-version、x-mobile-model 等
 *
 * 响应壳:顶层 {code, message, data}。code!=0 为业务错误(message 字段);
 * 成功时 data 层才是有效负载 — send() 直接剥掉外壳返回 data 层,
 * 与 FlandreSY 的 transport 行为一致(所有调用方拿到的 json 均为 data 层)。
 *
 * 洗衣链路:POST devices/scanWasherCode(扫码,qrCode 传原始二维码内容!)
 *          → GET app/washer/devices/program/info(套餐)
 *          → POST orders/create(下单) → GET payment/arguments(支付宝参数)
 *          → GET orders/{id}/detail(状态) → GET orders/{id}/control/start|stop(启停)
 */
class UjingClient {

    companion object {
        const val BASE = "https://phoenix.ujing.online/api/v1/"
        private const val APP_VERSION = "2.4.14"
        private const val MODEL = "HBN-AL00"
        private const val BRAND = "HUAWEI"
        private const val UA = "okhttp/4.3.1"
        private const val WEEX = "1.1.68"

        fun readable(code: Int, msg: String): String = when (code) {
            0 -> "成功"
            -2 -> "验证码错误"
            -99 -> "登录已过期,请重新登录"
            -21 -> msg.ifBlank { "需要设备控制登录" }
            else -> msg.ifBlank { "错误 code=$code" }
        }

        /** 订单状态 → 中文(依据 orderDetail.js 的 ORDER_STATUSTEXT) */
        fun statusText(status: String): String = when (status) {
            "0" -> "已创建"
            "10" -> "待支付"
            "20" -> "已支付,待启动"
            "21" -> "启动中"
            "22" -> "自洁启动中"
            "24" -> "正在投放洗衣液"
            "30" -> "自洁中"
            "35" -> "自洁完成"
            "40" -> "运行中(洗涤)"
            "50" -> "订单完成"
            "60" -> "已取消"
            else -> "状态 $status"
        }

        /** 「启动洗衣」按钮可点的状态:待启动20 / 自洁启动中22 / 自洁完成35(报告:仅这三个) */
        fun canStartWash(status: String): Boolean = status in setOf("20", "22", "35")

        /** 分 → "1.50" */
        fun fen2yuan(fen: Int): String {
            val neg = fen < 0
            val a = kotlin.math.abs(fen)
            return (if (neg) "-" else "") + (a / 100) + "." + ((a % 100) / 10) + (a % 10)
        }

        /**
         * 控制指令"受理"判定:成功可能是 code==0,也可能是设备控制专用的 code==1703
         * 且内层 data.errorCode==0(指令下发成功,不代表运行结果)。报告 §2.3/§3.4。
         * 注:r.json 已是 data 层(send() 剥壳,1703 也透传)。
         */
        fun commandAccepted(r: Result): Boolean =
            r.code == 0 || (r.code == 1703 && r.json?.optInt("errorCode", -1) == 0)

        /** 1703 时取内层 errorMessage 作为可读原因 */
        fun commandError(r: Result): String {
            val em = r.json?.optString("errorMessage", "")?.trim().orEmpty()
            return em.ifBlank { if (r.msg.isNotBlank()) r.msg else "指令失败(code=${r.code})" }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cookies = mutableMapOf<String, String>()

    /** json = 响应 data 层(失败时为 null) */
    data class Result(val code: Int, val msg: String, val json: JSONObject?) {
        val ok: Boolean get() = code == 0
    }

    private suspend fun send(
        method: String,
        path: String,
        appCode: String,
        bodyJson: JSONObject? = null,
        token: String? = null,
        query: Map<String, String> = emptyMap(),
    ): Result = withContext(Dispatchers.IO) {
        val sb = StringBuilder(BASE + path)
        if (query.isNotEmpty()) {
            sb.append("?")
            query.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append("&")
                sb.append(k).append("=").append(java.net.URLEncoder.encode(v, "UTF-8"))
            }
        }
        val builder = Request.Builder().url(sb.toString())
            .header("x-mobile-brand", BRAND)
            .header("x-mobile-id", "")
            .header("x-app-code", appCode)
            .header("x-app-version", APP_VERSION)
            .header("x-mobile-model", MODEL)
            .header("accept-encoding", "identity")
            .header("User-Agent", UA)
        if (WEEX.isNotEmpty()) builder.header("weex-version", WEEX)
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        val cookie = synchronized(cookies) { cookies.entries.joinToString("; ") { "${it.key}=${it.value}" } }
        if (cookie.isNotEmpty()) builder.header("Cookie", cookie)

        if (bodyJson != null) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            builder.post(bodyJson.toString().toRequestBody(mediaType))
        }
        client.newCall(builder.build()).execute().use { resp ->
            resp.headers("set-cookie").forEach { sc ->
                val kv = sc.split(";").firstOrNull() ?: return@forEach
                val eq = kv.indexOf("=")
                if (eq > 0) synchronized(cookies) {
                    cookies[kv.take(eq).trim()] = kv.substring(eq + 1).trim()
                }
            }
            val text = resp.body?.string().orEmpty()
            val json = try { JSONObject(text) } catch (_: Exception) { null }
            if (json == null) return@use Result(-999, "HTTP ${resp.code}", null)
            val code = json.optInt("code", -999)
            val msg = json.optString("message", json.optString("msg", ""))
            // 对齐 FlandreSY transport:成功(含设备控制受理码 1703)时剥壳,把 data 层交给调用方
            val data = json.optJSONObject("data")
            Result(code, msg, if (code == 0 || code == 1703) data else null)
        }
    }

    /** 请求短信验证码 */
    suspend fun requestCaptcha(mobile: String): Result = withContext(Dispatchers.IO) {
        send(
            "GET", "captcha", appCode = "ZA",
            query = mapOf(
                "mobile" to mobile, "type" to "1",
                "sessionId" to "AFS_SWITCH_OFF", "token" to "AFS_SWITCH_OFF", "sig" to "AFS_SWITCH_OFF",
            ),
        )
    }

    /** 短信验证码登录 */
    suspend fun login(mobile: String, captcha: String): Result = withContext(Dispatchers.IO) {
        send(
            "POST", "login", appCode = "ZA",
            bodyJson = JSONObject().put("mobile", mobile).put("captcha", captcha),
        )
    }

    /** token 位于 data 层(FlandreSY: _str(data,'token')) */
    fun extractToken(loginData: JSONObject?): String {
        if (loginData == null) return ""
        var t = loginData.optString("token", "").orEmpty()
        if (t.isNotBlank()) return t
        // 兜底:有些响应 data 是字符串 token 本身
        return t
    }

    fun extractUserId(loginData: JSONObject?): String =
        loginData?.optString("userId", "").orEmpty()

    /**
     * 扫码识别洗衣机:POST devices/scanWasherCode {qrCode}
     * 注意:qrCode 必须传二维码原始内容(App 内已尽量不改写;手输设备号时也原样传)。
     * data.result = {deviceId, deviceTypeId, createOrderEnabled, reason, status}
     */
    suspend fun scanWasher(token: String, qrCode: String): Result = withContext(Dispatchers.IO) {
        send(
            "POST", "devices/scanWasherCode", appCode = "BA",
            bodyJson = JSONObject().put("qrCode", qrCode.trim()),
            token = token,
        )
    }

    /** 拉取设备套餐:GET app/washer/devices/program/info?deviceId= */
    suspend fun programInfo(token: String, deviceId: String): Result = withContext(Dispatchers.IO) {
        send(
            "GET", "app/washer/devices/program/info", appCode = "BA",
            token = token, query = mapOf("deviceId" to deviceId),
        )
    }

    /**
     * 下单:POST orders/create(extras = 加购档位,键为接口返回的加购组 key,如 wp_detergentGearId)。
     * type:洗衣机=1、烘干机=2(报告 §3.5);dryTime:烘干机分计时收费时传"分钟×10",其余传 0 省略。
     */
    suspend fun createOrder(
        token: String,
        deviceId: String,
        deviceTypeId: Int,
        storeId: String,
        washModelId: Int,
        temperatureId: Int = 1,
        extras: Map<String, Int> = emptyMap(),
        type: Int = 1,
        dryTime: Int = 0,
    ): Result = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("type", type)
            .put("deviceTypeId", deviceTypeId)
            .put("deviceId", deviceId)
            .put("deviceWashModelId", washModelId)
            .put("storeId", storeId)
        if (type != 2) body.put("washTemperatureId", temperatureId)  // 温度仅洗衣机
        if (dryTime > 0) body.put("dryTime", dryTime)
        for ((k, v) in extras) body.put(k, v)
        send("POST", "orders/create", appCode = "BA", bodyJson = body, token = token)
    }

    /** 订单详情:GET orders/{id}/detail */
    suspend fun orderDetail(token: String, orderId: String): Result = withContext(Dispatchers.IO) {
        send("GET", "orders/$orderId/detail", appCode = "BA", token = token)
    }

    /** 支付参数:GET payment/arguments(channel=alipay) */
    suspend fun paymentArguments(token: String, orderId: String): Result = withContext(Dispatchers.IO) {
        send(
            "GET", "payment/arguments", appCode = "BA", token = token,
            query = mapOf(
                "channel" to "alipay", "orderId" to orderId, "couponId" to "",
                "isUseRedPacket" to "false", "redPacketId" to "0",
                "alipayF2FNoAds" to "false", "branchType" to "0",
                "jumpToAliMini" to "false", "payVersion" to "1",
            ),
        )
    }

    /**
     * 云端设备控制:GET orders/{id}/control/{action}
     * action: start/stop/continue/restart/selfClean(报告 §3.4)。
     */
    suspend fun control(token: String, orderId: String, action: String): Result = withContext(Dispatchers.IO) {
        send("GET", "orders/$orderId/control/$action", appCode = "BA", token = token)
    }

    /** 启动洗衣机 */
    suspend fun startOrder(token: String, orderId: String): Result = control(token, orderId, "start")

    /** 暂停 */
    suspend fun stopOrder(token: String, orderId: String): Result = control(token, orderId, "stop")

    /** 进行中订单列表(报告 §3.3):data 可能为数组或 {list/rows},故返回原始 JSON */
    suspend fun runningOrders(token: String): org.json.JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(BASE + "orders/running")
                .header("x-mobile-brand", BRAND).header("x-mobile-id", "")
                .header("x-app-code", "BA").header("x-app-version", APP_VERSION)
                .header("x-mobile-model", MODEL).header("accept-encoding", "identity")
                .header("User-Agent", UA).header("weex-version", WEEX)
                .header("Authorization", "Bearer $token").build()
            client.newCall(req).execute().use { org.json.JSONObject(it.body?.string().orEmpty()) }
        }.getOrNull()
    }

    /** 最近一次支付状态(静默轮询) */
    suspend fun lastPayStatus(token: String, orderId: String): Result = withContext(Dispatchers.IO) {
        send("GET", "app/payment/$orderId/lastPayStatus", appCode = "BA", token = token)
    }

    object Parsers {
        /** 从 running 原始响应取第一个进行中订单 orderId + deviceId(结构未验证,多路兜底) */
        fun firstRunning(full: org.json.JSONObject?): Pair<String, String>? {
            val data = full?.opt("data")
            val arr = when (data) {
                is org.json.JSONArray -> data
                is JSONObject -> data.optJSONArray("list") ?: data.optJSONArray("rows")
                    ?: data.optJSONArray("records") ?: data.optJSONArray("orders")
                else -> null
            } ?: return null
            val o = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.firstOrNull {
                it.optString("status", "") !in setOf("50", "60")
            } ?: return null
            val id = o.optString("orderId").ifBlank { o.optString("id") }
            return if (id.isBlank()) null else id to o.optString("deviceId", "")
        }

        /**
         * 从套餐 info(data 层)提取洗涤模式(含各模式可用的加购组)。
         * 字段:deviceWashModel[] → workModelId / workModelName / basePrice(分) / time(分钟)
         * 加购:additionDevices(直接数组或嵌套 {washingPartnerFeature:[...]})
         *   → [{key: "wp_detergentGearId", name: "洗衣液", options: [{id, name, price(分)}]}]
         */
        fun parseModels(info: JSONObject?): List<WasherModel> {
            if (info == null) return emptyList()
            val arr = info.optJSONArray("deviceWashModel") ?: return emptyList()
            val out = mutableListOf<WasherModel>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("workModelId", 0)
                val name = o.optString("workModelName", "")
                if (id == 0 || name.isBlank()) continue
                out += WasherModel(
                    id = id,
                    name = name,
                    priceFen = o.optInt("basePrice", 0),
                    timeMinutes = o.optInt("time", 0),
                    additions = parseAdditions(o) + parseAdditionParams(o),
                )
            }
            return out
        }

        /** 单个模式的加购组(key 即下单字段名,如 wp_detergentGearId / wp_disinfectantGearId) */
        private fun parseAdditions(modelJson: JSONObject): List<WasherAdditionGroup> {
            val direct = modelJson.opt("additionDevices")
            val arr = when (direct) {
                is org.json.JSONArray -> direct
                is JSONObject -> direct.optJSONArray("washingPartnerFeature")
                else -> null
            } ?: return emptyList()
            val groups = mutableListOf<WasherAdditionGroup>()
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val key = g.optString("key", "").trim()
                if (key.isBlank()) continue
                val opts = mutableListOf<WasherAdditionOption>()
                g.optJSONArray("options")?.let { oa ->
                    for (j in 0 until oa.length()) {
                        val oo = oa.optJSONObject(j) ?: continue
                        val oid = oo.optInt("id", -1)
                        val oname = oo.optString("name", "")
                        if (oid >= 0 && oname.isNotBlank()) {
                            opts += WasherAdditionOption(oid, oname, oo.optInt("price", 0))
                        }
                    }
                }
                if (opts.isNotEmpty()) {
                    groups += WasherAdditionGroup(key, g.optString("name", key), opts, purchasable = true)
                }
            }
            return groups
        }

        /**
         * additionParams:必选型选项组(温度 washTemperatureId / 筒自洁 selfCleanId …)。
         * 只有支持加热的机器模型才会带 washTemperatureId 组 → 温度选择自然只在其上出现。
         */
        private fun parseAdditionParams(modelJson: JSONObject): List<WasherAdditionGroup> {
            val arr = modelJson.optJSONArray("additionParams") ?: return emptyList()
            val groups = mutableListOf<WasherAdditionGroup>()
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val key = g.optString("key", "").trim()
                if (key.isBlank()) continue
                val opts = mutableListOf<WasherAdditionOption>()
                g.optJSONArray("options")?.let { oa ->
                    for (j in 0 until oa.length()) {
                        val oo = oa.optJSONObject(j) ?: continue
                        val oid = oo.optInt("id", -1)
                        val oname = oo.optString("name", "")
                        if (oid >= 0 && oname.isNotBlank()) {
                            opts += WasherAdditionOption(oid, oname, oo.optInt("price", 0))
                        }
                    }
                }
                if (opts.isEmpty()) continue
                val name = g.optString("title").ifBlank { g.optString("name", key) }
                groups += WasherAdditionGroup(key, name, opts, purchasable = false)
            }
            return groups
        }

        /** 组默认选中:必选型取首项(温度=常温、筒自洁=不使用),加购型 null(不添加) */
        fun defaultSelection(group: WasherAdditionGroup): Int? =
            if (group.purchasable) null else group.options.firstOrNull()?.id

        /** 默认套餐:优先 workModelId=1,否则第一个(对齐 legacy defaultWashModelId) */
        fun defaultModelId(models: List<WasherModel>): Int {
            if (models.isEmpty()) return 0
            return (models.firstOrNull { it.id == 1 } ?: models.first()).id
        }

        /**
         * 从订单详情(data 层)提取展示字段。
         * payPrice:服务端给的是“元”(数值或字符串,如 2.1),格式化为 ¥x.xx(对齐参考实现)。
         */
        fun parseOrder(detail: JSONObject?): WasherOrderInfo {
            if (detail == null) return WasherOrderInfo("", "", "", "", "", 0)
            val rawPay = detail.opt("payPrice")
            val payText = when (rawPay) {
                null -> ""
                is Number -> "¥" + String.format(java.util.Locale.US, "%.2f", rawPay.toDouble())
                else -> {
                    val t = rawPay.toString().trim()
                    val d = t.toDoubleOrNull()
                    if (t.isEmpty()) "" else if (d != null) "¥" + String.format(java.util.Locale.US, "%.2f", d) else t
                }
            }
            return WasherOrderInfo(
                orderId = detail.str("orderId").ifBlank { detail.str("orderNo") },
                deviceNo = detail.str("deviceNo"),
                status = detail.str("status"),
                statusText = detail.str("statusRemark").ifBlank { statusText(detail.str("status")) },
                payPrice = payText,
                remainTimeSeconds = detail.optInt("remainTime", 0),
                deviceId = detail.str("deviceId"),
                cleanSelfEndTime = detail.optLong("cleanSelfEndTime", 0L),
                selfCleanEnable = detail.optBoolean("selfCleanEnable", false) ||
                    detail.optString("selfCleanEnable", "") == "true",
            )
        }
    }
}

private fun JSONObject.str(key: String): String = optString(key, "").trim()

/** 加购项的一个档位(标准量/大量等);价格单位分 */
data class WasherAdditionOption(val id: Int, val name: String, val priceFen: Int)

/** 加购/选项组;key 即下单字段名。purchasable=true 加购型(带"不添加");false 必选型(默认选首项) */
data class WasherAdditionGroup(
    val key: String,
    val name: String,
    val options: List<WasherAdditionOption>,
    val purchasable: Boolean = true,
)

/** 一个洗涤模式(basePrice/time 单位分/分钟),additions 为该模式的选项组(含温度/自洁/加购) */
data class WasherModel(
    val id: Int,
    val name: String,
    val priceFen: Int,
    val timeMinutes: Int,
    val additions: List<WasherAdditionGroup> = emptyList(),
) {
    val priceText: String
        get() = "¥${UjingClient.fen2yuan(priceFen)}" + (if (timeMinutes > 0) " · ${timeMinutes}分钟" else "")
}

/** 洗衣订单展示信息 */
data class WasherOrderInfo(
    val orderId: String,
    val deviceNo: String,
    val status: String,
    val statusText: String,
    val payPrice: String,
    val remainTimeSeconds: Int,
    val deviceId: String = "",
    val cleanSelfEndTime: Long = 0L,   // 自洁结束时间戳(后端算好下发;非空即已自洁完成)
    val selfCleanEnable: Boolean = false,
)
