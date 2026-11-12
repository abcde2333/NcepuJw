package com.ncepu.jw.data

import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale

/**
 * 慧生活798 请求签名(协议对齐 ilife798/ilife798 的 Signer.kt)。
 *
 * 登录签名:md5("authCode=<验证码>&un=<手机号>" + 盐)
 * 任务签名:md5(adId + 时间桶 + token 后 8 位 + uid 后 8 位 + 盐),时间桶 = 30 * (nowMs / 30000)
 *
 * 平台盐矩阵(服务端按 UA 前缀选盐,两盐不可互换):
 *   ApplicationType "1,1"(App 通道)      + VersionCode 3.1.9     → SIGN_SALT
 *   ApplicationType "1,5"(小程序积分通道) + 登录 2.0.83 / 业务 2.0.178 → MINI_SIGN_SALT
 */
object IlifeSign {

    /** App 通道盐(1,1) */
    const val SIGN_SALT = "AbCdEfGhIjKlMnOpQrStUvWxYz12345"

    /** 小程序积分通道盐(1,5) */
    const val MINI_SIGN_SALT = "K9xP2QnR5T8wY3fA7cE1gJ4mL6oU0sZ"

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /** 登录签名。[mini] = true 走积分通道盐。 */
    fun login(phone: String, smsCode: String, mini: Boolean): String =
        md5("authCode=" + smsCode + "&un=" + phone + if (mini) MINI_SIGN_SALT else SIGN_SALT)

    /** 积分任务签名。[nowMs] 必须是服务端校准后的时间,本地时钟偏差会让时间桶错位。 */
    fun task(adId: String, token: String, uid: String, isApp: Boolean, nowMs: Long): String {
        val bucket = 30L * (nowMs / 30000L)
        val tokenTail = if (token.length > 8) token.substring(token.length - 8) else token
        val uidTail = if (uid.length > 8) uid.substring(uid.length - 8) else uid
        val salt = if (isApp) SIGN_SALT else MINI_SIGN_SALT
        return md5("" + adId + bucket + tokenTail + uidTail + salt)
    }

    /** 服务端部分时间戳是秒级,统一归一到毫秒 */
    fun toMillis(value: Long): Long = when {
        value <= 0L -> 0L
        value < 10_000_000_000L -> value * 1000
        else -> value
    }

    /** 周一 00:00 的毫秒时间戳 */
    fun weekStart(now: Long): Long {
        val cal = Calendar.getInstance(Locale.CHINA)
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val offsetDays = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return cal.timeInMillis - offsetDays * DAY_MILLIS
    }

    /**
     * 签到位图只对「签到时间所在的那一周」有效:服务端跨周不清零(周一仍返回 127),
     * 直接用今天位判断会把周一读成「今日已签到」。
     */
    fun weekMask(week: Int, signTimeMillis: Long, now: Long): Int =
        if (week == 0) 0 else if (signTimeMillis <= 0L) week else if (signTimeMillis < weekStart(now)) 0 else week

    /** 今天在签到位图里的位(周一=bit0) */
    fun todayBit(now: Long): Int {
        val cal = Calendar.getInstance(Locale.CHINA)
        cal.timeInMillis = now
        return 1 shl ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    }
}
