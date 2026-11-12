package com.ncepu.jw.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 饮水页三个子页(饮水 / 积分 / 账单)的界面状态契约。
 * 状态由 AppViewModel 持有,UI 只读 + 回调,不自己发请求。
 */
data class PointsUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val running: Boolean = false,
    /** 当前正在执行的任务名,空 = 没在跑 */
    val runningName: String = "",
    val message: String? = null,
    val validScore: Int = 0,
    val totalScore: Int = 0,
    val signedToday: Boolean = false,
    val missions: List<IlifeClient.Mission> = emptyList(),
    /** 今日已完成条数 / 总条数,给标题右侧用 */
    val doneCount: Int = 0,
    /** 每日自动运行积分任务 */
    val autoDaily: Boolean = false,
    /** 自动积分抵扣 */
    val autoDeduct: Boolean = false,
    /** 运行日志,最新在末尾 */
    val logs: List<String> = emptyList(),
    /** 积分通道登录表单 */
    val phone: String = "",
    val smsCode: String = "",
    val captchaInput: String = "",
    val captchaBmp: ImageBitmap? = null,
    val smsSent: Boolean = false,
    val smsCooldown: Int = 0,
    /** 建议文案:未登录时提示用哪个手机号登录积分通道 */
    val hint: String = "",
)

/** 账单一条(给 UI 的扁平模型,时间已格式化) */
data class BillUi(
    val id: String,
    val title: String,
    val timeText: String,
    val statusLine: String,
    val amountText: String,
    /** 抵扣小标,例如「积分抵扣 ¥0.14」;没有抵扣时为 null */
    val deductText: String? = null,
    /** 发生时间(毫秒),用于按本月汇总抵扣 */
    val timeMillis: Long = 0L,
)

data class BillUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val monthText: String = "¥0.00",
    val todayText: String = "¥0.00",
    val yesterdayText: String = "¥0.00",
    val items: List<BillUi> = emptyList(),
    val total: Int = 0,
    /** 本月积分抵扣合计(形如「¥0.28」);本月没有抵扣时为 null */
    val monthDeductText: String? = null,
)

/**
 * 三个子页(积分/账单)要的回调集合。
 * 单独成类是为了不让 WaterScreen 的形参膨胀到二十多个 lambda;默认空实现,
 * 预览/复用时不传也能编译。
 */
class WaterSubActions(
    // 积分登录表单
    val onPointsPhoneChange: (String) -> Unit = {},
    val onPointsSmsCodeChange: (String) -> Unit = {},
    val onPointsCaptchaInputChange: (String) -> Unit = {},
    val onPointsRefreshCaptcha: () -> Unit = {},
    val onPointsSendSms: () -> Unit = {},
    val onPointsLogin: () -> Unit = {},
    // 积分任务
    val onPointsRefresh: () -> Unit = {},
    val onRunAllPoints: () -> Unit = {},
    val onToggleAutoDaily: (Boolean) -> Unit = {},
    val onToggleAutoDeduct: (Boolean) -> Unit = {},
    // 账单
    val onBillsRefresh: () -> Unit = {},
    val onBillsLogin: () -> Unit = {},
)
