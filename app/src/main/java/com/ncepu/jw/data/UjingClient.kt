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

        /** 订单状态 → 中文(常见值) */
        fun statusText(status: String): String = when (status) {
            "0" -> "已创建"
            "10" -> "待支付"
            "20" -> "已支付,待启动"
            "30" -> "准备中"
            "40" -> "运行中"
            "50" -> "已完成"
            "60" -> "已取消"
            else -> "状态 $status"
        }

        /** 分 → "1.50" */
        fun fen2yuan(fen: Int): String {
            val neg = fen < 0
            val a = kotlin.math.abs(fen)
            return (if (neg) "-" else "") + (a / 100) + "." + ((a % 100) / 10) + (a % 10)
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
            // 对齐 FlandreSY transport:成功时剥壳,只把 data 层交给调用方
            val data = json.optJSONObject("data")
            Result(code, msg, if (code == 0) data else null)
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

    /** 下单:POST orders/create */
    suspend fun createOrder(
        token: String,
        deviceId: String,
        deviceTypeId: Int,
        storeId: String,
        washModelId: Int,
        temperatureId: Int = 1,
    ): Result = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("type", 1)
            .put("deviceTypeId", deviceTypeId)
            .put("deviceId", deviceId)
            .put("deviceWashModelId", washModelId)
            .put("storeId", storeId)
            .put("washTemperatureId", temperatureId)
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

    /** 启动洗衣机:GET orders/{id}/control/start */
    suspend fun startOrder(token: String, orderId: String): Result = withContext(Dispatchers.IO) {
        send("GET", "orders/$orderId/control/start", appCode = "BA", token = token)
    }

    /** 停止:GET orders/{id}/control/stop */
    suspend fun stopOrder(token: String, orderId: String): Result = withContext(Dispatchers.IO) {
        send("GET", "orders/$orderId/control/stop", appCode = "BA", token = token)
    }

    object Parsers {
        /**
         * 从套餐 info(data 层)提取洗涤模式列表。
         * 字段:deviceWashModel[] → workModelId / workModelName / basePrice(分) / time(分钟)
         */
        fun parseModels(info: JSONObject?): List<Triple<Int, String, String>> {
            val out = mutableListOf<Triple<Int, String, String>>()
            if (info == null) return out
            val arr = info.optJSONArray("deviceWashModel") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("workModelId", 0)
                val name = o.optString("workModelName", "")
                val fen = o.optInt("basePrice", 0)
                val minutes = o.optInt("time", 0)
                if (id != 0 && name.isNotBlank()) {
                    val price = "¥${fen2yuan(fen)}" + (if (minutes > 0) " · ${minutes}分钟" else "")
                    out += Triple(id, name, price)
                }
            }
            return out
        }

        /** 默认套餐:优先 workModelId=1,否则第一个(对齐 legacy defaultWashModelId) */
        fun defaultModelId(models: List<Triple<Int, String, String>>): Int {
            if (models.isEmpty()) return 0
            return (models.firstOrNull { it.first == 1 } ?: models.first()).first
        }

        /** 从订单详情(data 层)提取展示字段 */
        fun parseOrder(detail: JSONObject?): WasherOrderInfo {
            if (detail == null) return WasherOrderInfo("", "", "", "", "", 0)
            val fen = detail.optInt("payPrice", -1)
            val pay = if (fen >= 0) fen2yuan(fen) else detail.optString("payPrice", "")
            return WasherOrderInfo(
                orderId = detail.str("orderId").ifBlank { detail.str("orderNo") },
                deviceNo = detail.str("deviceNo"),
                status = detail.str("status"),
                statusText = detail.str("statusRemark").ifBlank { statusText(detail.str("status")) },
                payPrice = pay,
                remainTimeSeconds = detail.optInt("remainTime", 0),
            )
        }
    }
}

private fun JSONObject.str(key: String): String = optString(key, "").trim()

/** 洗衣订单展示信息 */
data class WasherOrderInfo(
    val orderId: String,
    val deviceNo: String,
    val status: String,
    val statusText: String,
    val payPrice: String,
    val remainTimeSeconds: Int,
)
