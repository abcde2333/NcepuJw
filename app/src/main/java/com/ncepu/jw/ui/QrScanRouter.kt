package com.ncepu.jw.ui

/** 统一扫码的分流结果 */
sealed interface ScanTarget {
    /** 饮水机(did 已归一化) */
    data class Water(val raw: String, val did: String) : ScanTarget

    /** 洗衣机/烘干机:厂商接口 `devices/scanWasherCode` 需要二维码原文，本地不做解析 */
    data class Washer(val raw: String) : ScanTarget

    /** 本地没有可靠特征:交给用户选一次(见 ScanTargetDialog),不猜 */
    data class Ambiguous(val raw: String) : ScanTarget

    /** 完全无法判断(空码):界面提示原文，不猜 */
    data class Unknown(val raw: String) : ScanTarget
}

/**
 * 一个扫码入口 → 饮水机/洗衣机两类的自动分流。
 *
 * 依据(见 `D:\zcodeworkspace\uapps\U净-洗衣机接口逆向分析.md`):
 * · 饮水机:码可以由本地解析 —— `did|deviceId|device_id` 键、`hnkzy.com`/`ilife798` 链接、
 *   或**机身码常见形态:6~32 位纯数字**(与 `WaterDeviceIdParser.normalize` 的规则一致)。
 * · 洗衣机:解析在**厂商服务端**(`POST devices/scanWasherCode {qrCode}`),本地只认特征字样
 *   (U净/Super798/洗烘业务词/接口名)。
 * · 两者都不像:**弹一次选择框**(`ScanTarget.Ambiguous`),不默认猜。
 *
 * 为什么不再"默认洗衣机":饮水机机身码若是别的形态(字母数字混合、未列出的链接),默认兜到洗衣机
 * 就等于"扫饮水机跳洗衣机页"——2026-10-14 用户实际踩到过。宁可多一次点击,也不要静默跳错页。
 * 分流只认 `did|deviceId|device_id` 这类明确键,避免把洗衣机链接里的 `id=` 误判成饮水机。
 */
object QrScanRouter {
    private val waterKeyPattern = Regex(
        """(?:[?&#;,\s{]|^)["']?(?:did|deviceId|device_id)["']?\s*(?:=|:)""",
        RegexOption.IGNORE_CASE,
    )

    /** 机身码常见形态:6~32 位纯数字(裸码,整串就是码) */
    private val bareNumberPattern = Regex("""^\d{6,32}$""")

    /** 洗衣机侧特征:U净/洗烘系列的业务词、链接、接口名 */
    private val washerHintPattern = Regex(
        """ujing|u净|super798|scanWasherCode|scanCode|洗衣|烘干|washer|laundry""",
        RegexOption.IGNORE_CASE,
    )

    fun route(raw: String): ScanTarget {
        val s = raw.trim()
        if (s.isEmpty()) return ScanTarget.Unknown(s)

        if (looksLikeWater(s)) {
            val did = WaterDeviceIdParser.normalize(s)
            if (did.isNotBlank()) return ScanTarget.Water(raw = s, did = did)
        }
        if (washerHintPattern.containsMatchIn(s)) return ScanTarget.Washer(s)

        // 认不出:不猜,弹选择框(码不对时对应页面会显示服务端返回的错误,但页面不能是错的)
        return ScanTarget.Ambiguous(s)
    }

    private fun looksLikeWater(s: String): Boolean {
        if (s.contains("hnkzy.com", ignoreCase = true)) return true
        if (s.contains("ilife798", ignoreCase = true)) return true
        if (bareNumberPattern.matches(s)) return true
        return waterKeyPattern.containsMatchIn(s)
    }
}
