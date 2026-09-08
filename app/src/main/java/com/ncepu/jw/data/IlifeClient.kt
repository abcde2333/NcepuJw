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
 * 慧生活798 客户端(协议对齐 nocookies111/life-798 的 IlifeApi.java)。
 *
 * 双平台 ApplicationType(严格对齐参考实现):
 *  - "1,1" 设备控制平台:登录 → appToken;用于 /ui/app/master、/dev/start|end
 *  - "1,5" 账户服务平台:发短信 /acc/login/code 用它(life-798 的 post() 默认值)
 * 响应结构:登录 token 位于 data.al.token;uid 位于 data.al.uid / view-info 的 data.id
 */
class IlifeClient {

    companion object {
        const val BASE = "https://i.ilife798.com/api/v1"
        private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Html5Plus/1.0 (Immersed/20) uni-app"
        const val APP_TYPE_MAIN = "1,5"   // 账户服务
        const val APP_TYPE_APP = "1,1"    // 设备控制

        /** 生成图形验证码的 s 参数(life-798 为 10 位随机串) */
        fun newCaptchaKey(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..10).map { chars.random() }.joinToString("")
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

        /**
         * 设备控制操作(start/end)专用:-2 等在设备语境里并非"验证码错误",
         * 多为频控/需人机验证/上一笔未结束等,统一以服务器真实 msg 为准,避免误导。
         */
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
            else -> if (msg.isNotBlank()) msg else "操作失败(code=$code),请稍候再试"
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Result(val code: Int, val msg: String, val json: JSONObject?) {
        val ok: Boolean get() = code == 0
    }

    private fun request(
        method: String,
        url: String,
        bodyJson: JSONObject? = null,
        token: String? = null,
        appType: String = APP_TYPE_MAIN,
    ): Result {
        val builder = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("ApplicationType", appType)
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

    /** 发送短信验证码(life-798:post() 默认 ApplicationType=1,5) */
    suspend fun sendSms(phone: String, captchaInput: String, s: String): Result =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("un", phone).put("authCode", captchaInput).put("s", s)
            request("POST", "$BASE/acc/login/code", body, appType = APP_TYPE_MAIN)
        }

    /** 短信验证码登录(设备控制平台,ApplicationType=1,1),token 在 data.al.token */
    suspend fun login(phone: String, smsCode: String): Result = withContext(Dispatchers.IO) {
        val body = JSONObject().put("openCode", "").put("un", phone).put("authCode", smsCode).put("cid", "")
        request("POST", "$BASE/acc/login", body, appType = APP_TYPE_APP)
    }

    /** 验证 token 是否有效(view-info):设备 token 必须配 1,1 头,否则被误判过期 */
    suspend fun viewInfo(token: String): Result = withContext(Dispatchers.IO) {
        request("GET", "$BASE/acc/view-info", null, token, appType = APP_TYPE_APP)
    }

    /** 收藏设备列表(设备控制 token) */
    suspend fun devices(token: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val r = request("GET", "$BASE/ui/app/master", null, token, appType = APP_TYPE_APP)
        val favos = r.json?.optJSONObject("data")?.optJSONArray("favos") ?: return@withContext emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until favos.length()) {
            val o = favos.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isNotBlank()) out += id to o.optString("name", "")
        }
        out.reversed()
    }

    /**
     * 设备实时状态(专用接口 ui/app/dev/status)。
     * status:99=空闲,非99=正在出水;out=本次累计出水量(升)。参考 Super798App。
     */
    data class DevStatus(val status: Int, val out: Double, val vel: Double) {
        val drinking: Boolean get() = status != 99
    }

    suspend fun deviceStatus(token: String, did: String): DevStatus? = withContext(Dispatchers.IO) {
        val r = request(
            "GET", "$BASE/ui/app/dev/status?did=$did&more=true&promo=false",
            null, token, appType = APP_TYPE_APP,
        )
        if (!r.ok) return@withContext null
        val gene = r.json?.optJSONObject("data")?.optJSONObject("device")?.optJSONObject("gene")
            ?: return@withContext null
        DevStatus(
            status = gene.optInt("status", 99),
            out = if (gene.has("out")) gene.optDouble("out", 0.0) else 0.0,
            vel = if (gene.has("vel")) gene.optDouble("vel", 0.0) else 0.0,
        )
    }

    /** 启动饮水机 */
    suspend fun start(token: String, did: String): Result = withContext(Dispatchers.IO) {
        request("GET", "$BASE/dev/start?did=$did&upgrade=true&ptype=91&rcp=false", null, token, appType = APP_TYPE_APP)
    }

    /** 结束出水 */
    suspend fun end(token: String, did: String): Result = withContext(Dispatchers.IO) {
        request("GET", "$BASE/dev/end?did=$did", null, token, appType = APP_TYPE_APP)
    }

    /** 从登录响应 JSON 提取 token 与 uid/eid(data.al.token) */
    fun extractToken(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optString("token").orEmpty()

    fun extractUid(loginJson: JSONObject): String =
        loginJson.optJSONObject("data")?.optJSONObject("al")?.optString("uid").orEmpty()
}
