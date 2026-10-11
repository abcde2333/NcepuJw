package com.ncepu.jw.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ncepu.jw.R
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.UjingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 洗衣进度前台服务:App 退到后台/被清理时仍轮询 orders/{id}/detail,
 * 在"自洁完成(35)"和"订单完成(50)"时发通知。WorkManager 最小 15 分钟粒度
 * 抓不住自洁后 5 分钟启动窗口,故用前台服务(约每 20s 轮询)。
 */
class WasherWatchService : Service() {

    companion object {
        private const val ACTION_WATCH = "watch"
        private const val EXTRA_ORDER = "orderId"
        private const val FG_CHANNEL = "washer_watch_ongoing"
        private const val NOTIFY_CHANNEL = "washer_notice"
        private const val FG_ID = 77001

        /** 故障持续这么久(90 次 × 60s ≈ 1.5 小时)仍未恢复就不再后台轮询,避免无休止唤醒 */
        private const val FAULT_GIVE_UP_POLLS = 90

        fun start(ctx: Context, orderId: String) {
            if (orderId.isBlank()) return
            SettingsStore(ctx).watchOrderId = orderId   // 供进程回收后 START_STICKY 恢复
            val i = Intent(ctx, WasherWatchService::class.java).apply {
                action = ACTION_WATCH
                putExtra(EXTRA_ORDER, orderId)
            }
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            SettingsStore(ctx).watchOrderId = ""
            ctx.stopService(Intent(ctx, WasherWatchService::class.java))
        }
    }

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NcepuJw:WasherWatch")
            .apply { setReferenceCounted(false) }
    }

    private val client = UjingClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cleanNotified = false
    private var doneNotified = false
    private var faultNotified = false
    private var faultPolls = 0
    private var lastStatus = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()
        startForeground(FG_ID, ongoingNotif("洗衣监控中"))
        val store = SettingsStore(this)
        val token = store.washerToken
        // 正常启动带 EXTRA_ORDER;进程被回收后 START_STICKY 重启时 intent 为 null → 从持久化恢复
        val orderId = intent?.getStringExtra(EXTRA_ORDER).takeUnless { it.isNullOrBlank() }
            ?: store.watchOrderId
        if (token.isBlank() || orderId.isBlank()) { stopSelf(); return START_NOT_STICKY }
        store.watchOrderId = orderId
        watch(token, orderId)
        return START_STICKY
    }

    private var watchedId = ""
    private var watchJob: kotlinx.coroutines.Job? = null

    private fun watch(token: String, orderId: String) {
        if (orderId == watchedId && watchJob?.isActive == true) return
        if (orderId != watchedId) {
            // 服务实例复用时切换到新订单:终止旧轮询,重置已通知标记
            watchJob?.cancel()
            watchedId = orderId
            cleanNotified = false; doneNotified = false; lastStatus = ""
            faultNotified = false; faultPolls = 0
        }
        watchJob = scope.launch {
            var miss = 0
            while (isActive) {
                // 灭屏/Doze 下短暂持锁,确保这次网络轮询 + 通知刷新能落地(而非被挂起)
                runCatching { if (!wakeLock.isHeld) wakeLock.acquire(10_000L) }
                try {
                    val r = runCatching { client.orderDetail(token, orderId) }.getOrNull()
                    val o = orderOf(r?.json)
                    if (o.orderId.isBlank()) {
                        if (++miss >= 4) break
                    } else {
                        miss = 0
                        onStatus(orderId, o)
                        updateOngoing(statusLabel(o))
                        // 50/51/52/53/54 才是终态;60/61 故障中要继续盯(用户可能去关好门再点「继续洗衣」)
                        if (o.terminal) break
                        if (faultPolls >= FAULT_GIVE_UP_POLLS) break
                    }
                } finally {
                    runCatching { if (wakeLock.isHeld) wakeLock.release() }
                }
                // 故障时放慢到 1 分钟一次(不知道用户何时排障),正常 20 秒
                delay(if (faultPolls > 0) 60_000L else 20_000L)
            }
            SettingsStore(this@WasherWatchService).watchOrderId = ""
            stopSelf()
        }
    }

    /**
     * 解析订单详情并补 deviceTypeId:详情接口不一定带该字段,而故障码要按机型查子表,
     * 故用已保存设备里扫码时记下的机型兜底。
     */
    private fun orderOf(json: org.json.JSONObject?): com.ncepu.jw.data.WasherOrderInfo {
        val o = UjingClient.Parsers.parseOrder(json)
        if (o.deviceTypeId != 0 || o.deviceId.isBlank()) return o
        val t = SettingsStore(this).washerDevices.firstOrNull { it.did == o.deviceId }?.deviceTypeId ?: 0
        return if (t != 0) o.copy(deviceTypeId = t) else o
    }

    private fun statusLabel(o: com.ncepu.jw.data.WasherOrderInfo): String {
        // 故障/暂停:直接显示原因(如 "E3 故障 · 请关闭门盖"),比只写"故障中"有用得多
        if (o.faulted) return o.faultLine ?: "设备故障中,请查看机器面板"
        val t = o.statusText.ifBlank { UjingClient.statusText(o.status) }
        return if (o.remainTimeSeconds > 0) "$t · 剩余 ${o.remainTimeSeconds / 60} 分" else t
    }

    private fun onStatus(orderId: String, o: com.ncepu.jw.data.WasherOrderInfo) {
        val status = o.status
        if (o.faulted) {
            faultPolls++
            if (!faultNotified) {
                faultNotified = true
                notify(NOTIFY_CHANNEL, faultNoticeId(orderId), "洗衣故障:${o.errorCode.ifBlank { "未知" }}",
                    (o.faultLine ?: "设备故障中") + "\n" + o.faultDetail +
                        "\n处理后在 App 内点「继续洗衣」可接着洗完。")
            }
            lastStatus = status
            return
        }
        if (faultNotified) {   // 故障恢复 → 收掉故障通知,便于下次再提醒
            faultNotified = false
            faultPolls = 0
            getSystemService(NotificationManager::class.java)?.cancel(faultNoticeId(orderId))
        }
        // 自洁完成(35):仅提醒一次
        if (status == "35" && !cleanNotified && o.selfCleanEnable) {
            cleanNotified = true
            notify(NOTIFY_CHANNEL, "washer_clean_$orderId".hashCode(), "筒自洁完成",
                "请在 5 分钟内点击「启动洗衣」开始洗涤,否则订单会自动取消")
        }
        if (status == "50" && !doneNotified) {
            doneNotified = true
            notify(NOTIFY_CHANNEL, "washer_done_$orderId".hashCode(), "洗衣完成", "订单已结束,请及时取出衣物")
        }
        lastStatus = status
    }

    private fun faultNoticeId(orderId: String) = "washer_fault_$orderId".hashCode()

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(FG_CHANNEL, "洗衣监控", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(NOTIFY_CHANNEL, "洗衣提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun ongoingNotif(text: String): Notification =
        NotificationCompat.Builder(this, FG_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("U净洗衣")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateOngoing(text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        getSystemService(NotificationManager::class.java)?.notify(FG_ID, ongoingNotif(text))
    }

    private fun notify(channelId: String, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    override fun onDestroy() {
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
        scope.cancel()
        super.onDestroy()
    }
}
