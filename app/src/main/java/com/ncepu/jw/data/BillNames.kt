package com.ncepu.jw.data

/** 账单字段 → 中文(对齐 ilife798/ilife798 的 BillNames.kt) */
object BillNames {

    fun productTypeName(cata: Int): String = when (cata) {
        -1 -> "无限制"
        1 -> "钱包充值"
        2 -> "家政服务"
        3 -> "E袋洗"
        4 -> "零售商品"
        5 -> "押金"
        6 -> "设备消费"
        7 -> "设备充值"
        9 -> "VIP会员卡"
        10 -> "权益商品"
        11 -> "缴费服务"
        12 -> "积分抽奖"
        else -> "其他消费"
    }

    fun paymentTypeName(type: Int): String = when (type) {
        11 -> "微信(APP)"
        12 -> "微信(公众号)"
        13 -> "微信(小程序)"
        21 -> "支付宝(APP)"
        22 -> "支付宝(生活号)"
        23 -> "支付宝(小程序)"
        24 -> "支付宝(扫脸)"
        31 -> "翼支付"
        41 -> "云闪付(APP)"
        43 -> "云闪付(小程序)"
        51 -> "招商银行"
        52 -> "农行支付"
        91 -> "商家钱包"
        92 -> "余额"
        93 -> "一卡通"
        else -> "其他"
    }

    fun billStatusName(status: Int, dir: Int): String {
        if (dir == 2) return if (status == 3) "已退款" else "退款中"
        return when (status) {
            1 -> "未付款"
            2 -> "待确认"
            3 -> "已付款"
            4 -> "付款失败"
            5 -> "核算中"
            9 -> "已取消"
            else -> "未知状态"
        }
    }
}
