package com.ncepu.jw.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 校外模式(深信服 WebVPN 免校园网访问教务/统一认证)。
 *
 * 代理方案(逆向 webvpn-converter,华北电力大学 host=myvpn.ncepu.edu.cn, key=iv="wrdvpnisthebest!"):
 *   校内 URL  https://<host><path>  →  https://myvpn.ncepu.edu.cn/https/<enc(host)><path>
 *   enc(host) = hex(iv_utf8) + AES-CFB128(key,iv, host补'0'到16倍数)[:host.length]
 * 认证走统一认证 authserver(CAS),密码后强制短信 MFA → 半自动。
 * 隧道认证后,教务内网 cookie 由 Sangfor 服务端按 wengine 会话自动附带,客户端只需持有 myvpn 的 wengine/route cookie。
 */
object Webvpn {

    const val MYVPN = "https://myvpn.ncepu.edu.cn"
    private const val KEY_STR = "wrdvpnisthebest!"          // 16 字节,与 iv 同
    /** 需要走隧道的校内域(教务新旧域 + 统一认证) */
    val campusHosts = setOf("jwxt.ncepu.edu.cn", "jwxt.hcc.edu.cn", "ids.ncepu.edu.cn")

    fun encHost(host: String): String {
        val key = KEY_STR.toByteArray(Charsets.UTF_8)
        val pad = 16 - (host.length % 16)
        val padded = (host + "0".repeat(pad)).toByteArray(Charsets.UTF_8)
        val ct = aesCfb128(key, key, padded).copyOfRange(0, host.length)
        return key.toHex() + ct.toHex()
    }

    /** AES-CFB 全块(128 位反馈)加密;不同 Android provider 接受的变换名不同,逐个试,最后回退 BC */
    private fun aesCfb128(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        for (tf in listOf("AES/CFB128/NoPadding", "AES/CFB/NoPadding")) {
            try {
                val c = Cipher.getInstance(tf)
                c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                return c.doFinal(data)
            } catch (_: Exception) {
                // 该 provider 不接受此变换名 → 试下一个
            }
        }
        run {
            val c = Cipher.getInstance("AES/CFB128/NoPadding", org.bouncycastle.jce.provider.BouncyCastleProvider())
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return c.doFinal(data)
        }
    }

    /** 把一个具体校内 URL 包成 myvpn 代理 URL;非校内 host 或解析失败原样返回 */
    fun proxied(url: String): String {
        val u = runCatching { url.toHttpUrl() }.getOrNull() ?: return url
        if (u.host !in campusHosts) return url
        val sb = StringBuilder(MYVPN).append("/https/").append(encHost(u.host)).append(u.encodedPath)
        u.encodedQuery?.let { if (it.isNotEmpty()) sb.append('?').append(it) }
        return sb.toString()
    }

    /** 教务 jwxt 代理前缀(供 WebView 成功判定:URL 是否落在代理后的教务域) */
    val jwxtProxiedPrefix: String get() = "$MYVPN/https/${encHost("jwxt.ncepu.edu.cn")}/"

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/**
 * 应用层拦截器:开启时把校内域请求改写为 myvpn 代理 URL(含 Referer);关闭时原样透传。
 * 只改写命中 campusHosts 的请求;myvpn 自身(webvpnLogin 直接构造的绝对代理 URL)不受影响。
 */
class WebvpnInterceptor : Interceptor {
    @Volatile var enabled: Boolean = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (!enabled || req.url.host !in Webvpn.campusHosts) return chain.proceed(req)
        val newUrl = Webvpn.proxied(req.url.toString())
        var nb = req.newBuilder().url(newUrl)
        req.header("Referer")?.let { ref ->
            if (runCatching { ref.toHttpUrl().host in Webvpn.campusHosts }.getOrDefault(false))
                nb = nb.header("Referer", Webvpn.proxied(ref))
        }
        return chain.proceed(nb.build())
    }
}
