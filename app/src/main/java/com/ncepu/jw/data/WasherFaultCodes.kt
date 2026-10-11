package com.ncepu.jw.data

/**
 * U净洗衣机故障码字典 —— 逆向自 uapps 的 weex bundle orderDetail.js
 * (模块内 `WASHER_ERROR_CONFIGURATIONS` 与 `getWasherErrorByDeviceType`)。
 *
 * 结构:`fault_warn.<家族>.<原始上报码> = {errorCode, shortMsg, errorMsg}`
 *  - 键 **raw** = 设备上报的原始码,查表必须用它 —— 有 16 个别名键
 *    (DB 的 E52/E53/E55…E5F 全部归到 E50,DC 的 E40→E4、E50→E5、E9/E90→C9);
 *  - **canonical** = 官方 UI 展示用的规范码;
 *  - **short** = 徽标/一行文案;**full** = 详情全文(可直接上屏)。
 * 共 85 条:DA 波轮 21、DB 滚筒 33、DC 干衣机 14、LG 13kg(DRUM 14 / DRYER 3)。
 *
 * 家族选择与官方 switch 一致:1/4/8/11→DA、2/9/12→DB、3/10/13→DC、
 * 14→LG.DRUM、15→LG.DRYER,未列出的 deviceTypeId(如 6 DRYER_LARGE)落回 DA。
 */
object WasherFaultCodes {

    /** WASHER_DEVICE_TYPE(官方枚举) */
    const val PULSATOR = 1
    const val DRUM = 2
    const val DRYER = 3
    const val SHOE_WASHER = 4
    const val DRYER_LARGE = 6
    const val PULSATOR_OTT = 8
    const val DRUM_10KG = 9
    const val DRYER_9KG = 10
    const val PULSATOR_65KG = 11
    const val DRUM_10KG_NEW = 12
    const val DRYER_10KG = 13
    const val DRUM_13KG_LG = 14
    const val DRYER_13KG_LG = 15

    /** 波轮系(官方 isDeviceLockError 用) */
    val PULSATOR_TYPES = setOf(PULSATOR, PULSATOR_OTT, PULSATOR_65KG)

    /** 滚筒系 */
    val DRUM_TYPES = setOf(DRUM, DRUM_10KG, DRUM_10KG_NEW)

    /** 干衣机系(官方 DRYER_DEVICE_TYPES,含 DRYER_LARGE) */
    val DRYER_TYPES = setOf(DRYER, DRYER_LARGE, DRYER_9KG, DRYER_10KG, DRYER_13KG_LG)

    /** 官方 HIDE_STOP_AND_CONTINUE_BTN_DEVICE_TYPES:LG 13kg 两型不显示「停止/继续」 */
    val HIDE_STOP_AND_CONTINUE_TYPES = setOf(DRUM_13KG_LG, DRYER_13KG_LG)

    /** 一个故障码:raw=设备上报码(别名键) canon=展示用规范码 */
    data class Fault(val raw: String, val canonical: String, val short: String, val full: String)


    /** DA 波轮系(21 条) */
    private val DA: Map<String, Fault> = mapOf(
        "E1" to Fault("E1", "E1", "进水异常", "请检查是否洗衣房停水水龙头是否未开，请联系管理员处理"),
        "E2" to Fault("E2", "E2", "排水异常", "请检查设备排水管是否放下/排水管是否堵塞，请联系管理员处理"),
        "E3" to Fault("E3", "E3", "请关闭门盖", "洗衣机门未关，请关紧门盖后，点击页面按钮继续运行设备"),
        "E4" to Fault("E4", "E4", "设备故障", "故障代码：E4（载重不平衡），请联系管理员处理"),
        "Fd" to Fault("Fd", "Fd", "设备故障", "故障代码：Fd（门锁闭合异常），请联系管理员处理"),
        "F8" to Fault("F8", "F8", "设备故障", "故障代码：F8（水位传感器故障），请联系管理员处理"),
        "F0" to Fault("F0", "F0", "设备故障", "故障代码：F0（电源开关发生故障），请联系管理员处理"),
        "C0" to Fault("C0", "C0", "设备故障", "故障代码：C0 (变频器/变频电机故障），请联系管理员处理"),
        "C1" to Fault("C1", "C1", "设备故障", "故障代码：C1 (变频器/变频电机故障），请联系管理员处理"),
        "C2" to Fault("C2", "C2", "设备故障", "故障代码：C2 (变频器/变频电机故障），请联系管理员处理"),
        "C3" to Fault("C3", "C3", "设备故障", "故障代码：C3 (变频器/变频电机故障），请联系管理员处理"),
        "C4" to Fault("C4", "C4", "设备故障", "故障代码：C4 (变频器/变频电机故障），请联系管理员处理"),
        "C5" to Fault("C5", "C5", "设备故障", "故障代码：C5 (变频器/变频电机故障），请联系管理员处理"),
        "C6" to Fault("C6", "C6", "设备故障", "故障代码：C6 (变频器/变频电机故障），请联系管理员处理"),
        "C7" to Fault("C7", "C7", "设备故障", "故障代码：C7 (变频器/变频电机故障），请联系管理员处理"),
        "C8" to Fault("C8", "C8", "设备故障", "故障代码：C8 (变频器/变频电机故障），请联系管理员处理"),
        "CL" to Fault("CL", "CL", "设备故障", "故障代码：CL（零部件异常），请联系管理员处理"),
        "F1" to Fault("F1", "F1", "设备故障", "故障代码：F1（3D传感器报警），请联系管理员处理"),
        "F4" to Fault("F4", "F4", "设备故障", "故障代码：F4（电机速度反馈缺失），请联系管理员处理"),
        "F6" to Fault("F6", "F6", "设备故障", "故障代码：F6（温度传感器故障），请联系管理员处理"),
        "E98" to Fault("E98", "E98", "请关门/继续", "波轮机门没关、设备暂停，请联系管理员处理。"),
    )

    /** DB 滚筒系(33 条) */
    private val DB: Map<String, Fault> = mapOf(
        "E10" to Fault("E10", "E10", "进水异常", "请检查是否洗衣房停水/水龙头是否未开，或者是否排水管高度过低或排水管口放在水里，请联系管理员处理。"),
        "E12" to Fault("E12", "E12", "洗衣机溢水", "E12: 溢水保护：洗衣机内的水位超过了溢水水位，请联系管理员处理"),
        "E21" to Fault("E21", "E21", "排水异常", "排水超时或排水堵塞，请联系管理员处理"),
        "E30" to Fault("E30", "E30", "请关闭机门", "洗衣机门未关，请关紧门盖后，点击页面按钮继续运行设备"),
        "E31" to Fault("E31", "E31", "设备故障", "故障代码：E31（洗衣机门锁解锁异常），请联系管理员处理"),
        "E33" to Fault("E33", "E33", "设备故障", "故障代码：E33（水位传感器故障），请联系管理员处理"),
        "E34" to Fault("E34", "E34", "设备故障", "故障代码：E34（洗涤加热温度传感器异常），请联系管理员处理"),
        "E35" to Fault("E35", "E35", "设备故障", "故障代码：E35（洗涤加热温度传感器异常），请联系管理员处理"),
        "E36" to Fault("E36", "E36", "设备故障", "故障代码：E36（机洗涤加热管故障），请联系管理员处理"),
        "E37" to Fault("E37", "E37", "设备故障", "故障代码：E37（烘干进风口温度传感器故障），请联系管理员处理"),
        "E38" to Fault("E38", "E38", "设备故障", "故障代码：E38（烘干进风口温度传感器故障），请联系管理员处理"),
        "E3A" to Fault("E3A", "E3A", "设备故障", "故障代码：E38（烘干进风口温度传感器故障），请联系管理员处理"),
        "E50" to Fault("E50", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E52" to Fault("E52", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E53" to Fault("E53", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E55" to Fault("E55", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E56" to Fault("E56", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E57" to Fault("E57", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E58" to Fault("E58", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E5A" to Fault("E5A", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E5B" to Fault("E5B", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E5C" to Fault("E5C", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E5D" to Fault("E5D", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E5E" to Fault("E5E", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E5F" to Fault("E5F", "E50", "设备故障", "故障代码：E50 (变频器或者电机发生故障)，请联系管理员处理"),
        "E60" to Fault("E60", "E60", "设备故障", "故障代码：E60（电机堵转报警），请联系管理员处理"),
        "E61" to Fault("E61", "E61", "设备故障", "故障代码：E61（测速信号丢失报警），请联系管理员处理"),
        "E62" to Fault("E62", "E62", "设备故障", "故障代码：E62（电机可控硅坏报警），请联系管理员处理"),
        "E64" to Fault("E64", "E64", "设备故障", "故障代码：E64(变频器和电机通讯发生故障)，请联系管理员处理"),
        "E70" to Fault("E70", "E70", "设备故障", "故障代码：E70(控制板电源键卡死) ，请联系管理员处理"),
        "E80" to Fault("E80", "E80", "设备故障", "故障代码：E80（洗衣机通讯异常），请联系管理员处理"),
        "EF" to Fault("EF", "EF", "设备故障", "故障代码：EF（设备异常），请联系管理员处理"),
        "E98" to Fault("E98", "E98", "请关门/继续", "滚筒机门没关、设备暂停，请联系管理员处理。"),
    )

    /** DC 干衣机(14 条) */
    private val DC: Map<String, Fault> = mapOf(
        "E02" to Fault("E02", "E02", "请清理水盒", "请清理机器左上方的水盒中的存水。请联系管理员处理"),
        "E20" to Fault("E20", "E20", "机器水满故障", "请清理左上方水盒或底部排水管，请联系管理员处理"),
        "E30" to Fault("E30", "E30", "设备故障", "故障代码：E30(干衣机加热管错误异常)，请联系管理员处理"),
        "E32" to Fault("E32", "E32", "设备故障", "故障代码：E32(干衣机湿度传感器异常)，请联系管理员处理"),
        "E33" to Fault("E33", "E33", "设备故障", "故障代码：E33(干衣机温度传感器异常)，请联系管理员处理)"),
        "E80" to Fault("E80", "E80", "请关门通电", "干衣机门未关闭，或已断电，请联系管理员处理。"),
        "E82" to Fault("E82", "E82", "通信故障", "故障代码：E82主控板和显控版之间通讯异常，请联系管理员处理。"),
        "E98" to Fault("E98", "E98", "请关门/通电", "干衣机门没关、设备断电，请联系管理员处理。"),
        "E40" to Fault("E40", "E4", "设备故障", "故障代码：E4(干衣机湿度传感器异常)，请联系管理员处理"),
        "E4" to Fault("E4", "E4", "设备故障", "故障代码：E4(干衣机湿度传感器异常)，请联系管理员处理"),
        "E90" to Fault("E90", "C9", "通信故障", "故障代码：C9(通信故障)，请联系管理员处理"),
        "E9" to Fault("E9", "C9", "通信故障", "故障代码：C9(通信故障)，请联系管理员处理"),
        "E50" to Fault("E50", "E5", "设备故障", "故障代码：E5(温度传感器故障，请联系管理员处理)"),
        "E5" to Fault("E5", "E5", "设备故障", "故障代码：E5(温度传感器故障，请联系管理员处理)"),
    )

    /** LG 13kg 洗衣机(deviceTypeId=14)(14 条) */
    private val LG_DRUM: Map<String, Fault> = mapOf(
        "DE2" to Fault("DE2", "DE2", "门锁错误", "设备故障，请联系商家处理"),
        "IE" to Fault("IE", "IE", "供水错误", "设备故障，请联系商家处理"),
        "OE" to Fault("OE", "OE", "排水错误", "设备故障，请联系商家处理"),
        "UE" to Fault("UE", "UE", "不平衡错误", "设备故障，请联系商家处理"),
        "FE" to Fault("FE", "FE", "水溢出错误", "设备故障，请联系商家处理"),
        "PE" to Fault("PE", "PE", "水位传感器错误", "设备故障，请联系商家处理"),
        "TE" to Fault("TE", "TE", "温度传感器错误", "设备故障，请联系商家处理"),
        "LE" to Fault("LE", "LE", "电机转速错误", "设备故障，请联系商家处理"),
        "CE" to Fault("CE", "CE", "过电流错误", "设备故障，请联系商家处理"),
        "HE" to Fault("HE", "HE", "加热器错误", "设备故障，请联系商家处理"),
        "3E2" to Fault("3E2", "3E2", "3D UB错误", "设备故障，请联系商家处理"),
        "DCE" to Fault("DCE", "DCE", "直流电路过电压错误", "设备故障，请联系商家处理"),
        "3E1" to Fault("3E1", "3E1", "3D感应传感器错误", "设备故障，请联系商家处理"),
        "DE1" to Fault("DE1", "DE1", "门未关好", "洗衣机门未关，请关紧门盖后，点击机器【开始】按钮继续运行"),
    )

    /** LG 13kg 烘干机(deviceTypeId=15)(3 条) */
    private val LG_DRYER: Map<String, Fault> = mapOf(
        "DE1" to Fault("DE1", "DE1", "门未关好", "烘干机门未关，请关紧门盖后继续服务"),
        "HE" to Fault("HE", "HE", "烘干机加热错误", "设备故障，请联系商家处理"),
        "DUCT" to Fault("DUCT", "DUCT", "排风阻塞", "设备故障，请联系商家处理"),
    )

    private fun tableOf(deviceTypeId: Int): Map<String, Fault> = when (deviceTypeId) {
        DRUM, DRUM_10KG, DRUM_10KG_NEW -> DB
        DRYER, DRYER_9KG, DRYER_10KG -> DC
        DRUM_13KG_LG -> LG_DRUM
        DRYER_13KG_LG -> LG_DRYER
        else -> DA          // 含 1/4/8/11 波轮系与未列出的畸形值(对齐官方默认 a.fault_warn.DA)
    }

    /** 查表:code 用设备上报的原始码(大小写敏感,DA 里有 `Fd` 这种小写键) */
    fun lookup(deviceTypeId: Int, code: String): Fault? {
        val c = code.trim()
        if (c.isEmpty()) return null
        return tableOf(deviceTypeId)[c]
    }

    /** 一行简称(徽标用);未知码返回空串 */
    fun shortOf(deviceTypeId: Int, code: String): String = lookup(deviceTypeId, code)?.short.orEmpty()

    /** 详情全文;未知码返回空串 */
    fun fullOf(deviceTypeId: Int, code: String): String = lookup(deviceTypeId, code)?.full.orEmpty()

    /** 字典条目总数(85) */
    val size: Int get() = DA.size + DB.size + DC.size + LG_DRUM.size + LG_DRYER.size

    /** 官方 isDeviceLockError:波轮 E3 / 滚筒 E30 是「门未关好」而非普通故障 */
    fun isLockError(deviceTypeId: Int, code: String): Boolean =
        (deviceTypeId in PULSATOR_TYPES && code == "E3") ||
            (deviceTypeId in DRUM_TYPES && code == "E30")

    /**
     * 官方 isPauseStatus:status≥60 且 E98(新国标机型)且非干衣机 → 机器因门没关而暂停,
     * 面板显示「机器暂停中」而不是错误码。
     */
    fun isPauseStatus(status: String, deviceTypeId: Int, code: String, newGBStd: Boolean): Boolean =
        (status.toIntOrNull() ?: 0) >= 60 && code == "E98" && newGBStd &&
            deviceTypeId !in DRYER_TYPES

    /** 是否处于故障中(官方 ORDER_STATUSTEXT:60/61 都是「故障中」) */
    fun isFaultStatus(status: String): Boolean = status == "60" || status == "61"

    /**
     * 官方 `contiuneBtn`(原文拼写如此):
     * `!hideStopAndContinueBtn && status∈{60,61} && (deviceTypeId!=10 || errorCode∉{E20,E30,E33,E82})`
     * —— 末条是 9kg 烘干机的水满/门/通信类故障,继续也没意义。
     */
    fun canContinueWash(status: String, deviceTypeId: Int, code: String): Boolean =
        deviceTypeId !in HIDE_STOP_AND_CONTINUE_TYPES && isFaultStatus(status) &&
            (deviceTypeId != DRYER_9KG || code !in DRYER_NO_CONTINUE_CODES)

    /** 官方 stopBtn:运行中(30/40)或故障中(60/61)且非储物柜单 */
    fun canStop(status: String, deviceTypeId: Int, boxTypeId: Boolean): Boolean =
        deviceTypeId !in HIDE_STOP_AND_CONTINUE_TYPES && !boxTypeId &&
            (status == "30" || status == "40" || isFaultStatus(status))

    /** 9kg 烘干机这几条故障官方不给「继续」 */
    private val DRYER_NO_CONTINUE_CODES = setOf("E20", "E30", "E33", "E82")

    /**
     * 故障/暂停一句话(卡片与通知共用,对齐官方 errCode 区块的写法):
     *  - 暂停态 → 「机器暂停中,关好门后点继续」
     *  - 门未关好(波轮 E3/滚筒 E30)→ 「Exx-门未关好」
     *  - 其他 → 「Exx 故障-一键简称」
     * 非故障态返回 null。
     */
    fun faultText(status: String, deviceTypeId: Int, code: String, newGBStd: Boolean): String? {
        if (!isFaultStatus(status)) return null
        if (isPauseStatus(status, deviceTypeId, code, newGBStd)) return "机器暂停中:关好门后点「继续洗衣」"
        if (code.isBlank()) return "设备故障中,请查看机器面板"
        val f = lookup(deviceTypeId, code)
        if (isLockError(deviceTypeId, code)) return "$code-门未关好,请关紧门盖后再点「继续洗衣」"
        val short = f?.short ?: "设备故障"
        return "$code 故障 · $short"
    }

    /**
     * 故障详情全文(优先字典 errorMsg);门未关好给出手工提示,未知码提示看面板。
     */
    fun faultDetail(deviceTypeId: Int, code: String): String {
        if (code.isBlank()) return "未上报故障码,请查看机器面板提示。"
        if (isLockError(deviceTypeId, code)) {
            return "洗衣机门未关,请关紧门盖后,点击页面按钮继续运行设备"
        }
        val f = lookup(deviceTypeId, code)
        return f?.full ?: "未收录的故障码 $code,请查看机器面板或联系管理员。"
    }
}
