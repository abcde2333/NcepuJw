package com.ncepu.jw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 慧生活798 客户端(逆向自 ilife798 App 的 uni-app 接口,
 * 协议参考 nocookies111/life-798 与 KynixInHK/anti-ad-ilife-798)。
 *
 * 认证:手机号 + 图形验证码 → 短信验证码 → token(ApplicationType=1,1 为设备控制平台)
 * 设备:GET /ui/app/master → data.favos[] = 收藏设备(id/name)
 * 开水:GET /dev/start?did=<id>&upgrade=true&ptype=91&rcp=false
 * 关水:GET /dev/end?did=<id>
 * 所有请求需带头:ApplicationType: 1,1 与 Authorization: <token>
 */
class IlifeClient {

    companion object {
        const val BASE = "https://i.ilife798.com/api/v1"
        private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Html5Plus/1.0 (Immersed/20) uni-app"
        private const val APP_TYPE = "1,1"

        /** 生成图形验证码的 s 参数(随机串) */
        fun newCaptchaKey(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..16).map { chars.random() }.joinToString("")
        }

        /** 错误码 → 用户可读消息 */
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
            else -> msg.ifBlank { "错误 code=$code" }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Result(val code: Int, val msg: String, val json: JSONObject?) {
        val ok: Boolean get() = code == 0
    }

    private fun request(method: String, url: String, bodyJson: JSONObject? = null, token: String? = null): Result {
        val builder = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("ApplicationType", APP_TYPE)
            .header("Accept-Language", "zh-Hans-CN;q=1")
        if (!token.isNullOrBlank()) builder.header("Authorization", token)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        when (method) {
            "POST" -> builder.post((bodyJson?.toString() ?: "{}").toRequestBody(mediaType))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            val json = try { JSONObject(bodyText) } catch (_: Exception) { null }
            if (json != null) {
                return Result(json.optInt("code", -999), json.optString("msg", ""), json)
            }
            return Result(-999, "HTTP ${resp.code}", null)
        }
    }

    /** 图形验证码图片地址(展示给用户输入) */
    fun captchaUrl(s: String): String = "$BASE/captcha/?s=${java.net.URLEncoder.encode(s, "UTF-8")}&r=${System.currentTimeMillis()}"

    /** 发送短信验证码 */
    suspend fun sendSms(phone: String, captchaInput: String, s: String): Result =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("un", phone).put("authCode", captchaInput).put("s", s)
            request("POST", "$BASE/acc/login/code", body)
        }

    /** 短信验证码登录,返回 token(设备控制平台) */
    suspend fun login(phone: String, smsCode: String): Result = withContext(Dispatchers.IO) {
        val body = JSONObject().put("openCode", "").put("un", phone).put("authCode", smsCode).put("cid", "")
        request("POST", "$BASE/acc/login", body)
    }

    /** 验证 token 是否有效(view-info) */
    suspend fun viewInfo(token: String): Result = withContext(Dispatchers.IO) {
        request("GET", "$BASE/acc/view-info", null, token)
    }

    /** 收藏设备列表(登录后) */
    suspend fun devices(token: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val r = request("GET", "$BASE/ui/app/master", null, token)
        val favos = r.json?.optJSONObject("data")?.optJSONArray("favos") ?: return@withContext emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until favos.length()) {
            val o = favos.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isNotBlank()) out += id to o.optString("name", "")
        }
        out.reversed()
    }

    /** 启动饮水机 */
    suspend fun start(token: String, did: String): Result = withContext(Dispatchers.IO) {
        request("GET", "$BASE/dev/start?did=$did&upgrade=true&ptype=91&rcp=false", null, token)
    }

    /** 结束出水 */
    suspend fun end(token: String, did: String): Result = withContext(Dispatchers.IO) {
        request("GET", "$BASE/dev/end?did=$did", null, token)
    }

    /** 从登录响应 JSON 提取 token 与 uid/eid(data.al.token) */
    fun extractToken(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optString("token").orEmpty()

    fun extractUid(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optString("uid").orEmpty()
}
