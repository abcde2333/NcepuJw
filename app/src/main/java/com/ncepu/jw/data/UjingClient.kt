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
 * 洗衣链路:POST devices/scanWasherCode(扫码) → GET app/washer/devices/program/info(套餐)
 *          → POST orders/create(下单) → GET payment/arguments(支付宝参数,App 外支付)
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
            "10" -> "待支付"
            "20" -> "已支付,待启动"
            "30" -> "准备中"
            "40" -> "运行中"
            "50" -> "已完成"
            "60" -> "已取消"
            else -> "状态 $status"
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cookies = mutableMapOf<String, String>()

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
            if (json != null) Result(json.optInt("code", -999), json.optString("msg", ""), json)
            else Result(-999, "HTTP ${resp.code}", null)
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

    /** 短信验证码登录,返回 token */
    suspend fun login(mobile: String, captcha: String): Result = withContext(Dispatchers.IO) {
        send(
            "POST", "login", appCode = "ZA",
            bodyJson = JSONObject().put("mobile", mobile).put("captcha", captcha),
        )
    }

    fun extractToken(loginJson: JSONObject): String {
        val data = loginJson.optJSONObject("data")
        return data?.optString("token", "").orEmpty().ifBlank {
            data?.optString("userId", "").orEmpty().let { if (it.isBlank()) "" else "" }
        }
    }

    fun extractUserId(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optString("userId", "").orEmpty()

    /** 扫码识别洗衣机:POST devices/scanWasherCode {qrCode} */
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
        temperatureId: Int,
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
        /** 从套餐 info JSON 提取洗涤模式列表(id/name/price) */
        fun parseModels(info: JSONObject): List<Triple<Int, String, String>> {
            val out = mutableListOf<Triple<Int, String, String>>()
            val arr = info.optJSONArray("washModelList")
                ?: info.optJSONArray("models")
                ?: info.optJSONArray("washModels")
                ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("id", o.optInt("washModelId", 0))
                val name = o.optString("name", o.optString("washModelName", ""))
                val price = o.optString("price", o.optString("payPrice", ""))
                if (id != 0 && name.isNotBlank()) out += Triple(id, name, price)
            }
            return out
        }

        /** 从订单详情 JSON 提取展示字段 */
        fun parseOrder(detail: JSONObject): WasherOrderInfo = WasherOrderInfo(
            orderId = detail.str("orderId").ifBlank { detail.str("orderNo") },
            deviceNo = detail.str("deviceNo"),
            status = detail.str("status"),
            statusText = detail.str("statusRemark").ifBlank { statusText(detail.str("status")) },
            payPrice = detail.optString("payPrice", ""),
            remainTimeSeconds = detail.optInt("remainTime", 0),
        )
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
