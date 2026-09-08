package com.ncepu.jw.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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

        fun start(ctx: Context, orderId: String) {
            if (orderId.isBlank()) return
            val i = Intent(ctx, WasherWatchService::class.java).apply {
                action = ACTION_WATCH
                putExtra(EXTRA_ORDER, orderId)
            }
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WasherWatchService::class.java))
        }
    }

    private val client = UjingClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cleanNotified = false
    private var doneNotified = false
    private var lastStatus = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()
        startForeground(FG_ID, ongoingNotif("洗衣监控中"))
        val token = SettingsStore(this).washerToken
        val orderId = intent?.getStringExtra(EXTRA_ORDER).orEmpty()
        if (token.isBlank() || orderId.isBlank()) { stopSelf(); return START_NOT_STICKY }
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
        }
        watchJob = scope.launch {
            var miss = 0
            while (isActive) {
                val r = runCatching { client.orderDetail(token, orderId) }.getOrNull()
                val o = UjingClient.Parsers.parseOrder(r?.json)
                if (o.orderId.isBlank()) {
                    if (++miss >= 4) break
                } else {
                    miss = 0
                    onStatus(orderId, o.status, o.selfCleanEnable)
                    updateOngoing(statusLabel(o))
                    if (o.status in setOf("50", "60")) break
                }
                delay(20_000)
            }
            stopSelf()
        }
    }

    private fun statusLabel(o: com.ncepu.jw.data.WasherOrderInfo): String {
        val t = o.statusText.ifBlank { UjingClient.statusText(o.status) }
        return if (o.remainTimeSeconds > 0) "$t · 剩余 ${o.remainTimeSeconds / 60} 分" else t
    }

    private fun onStatus(orderId: String, status: String, selfCleanOrdered: Boolean) {
        // 自洁完成(35):仅提醒一次
        if (status == "35" && !cleanNotified && selfCleanOrdered) {
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
        scope.cancel()
        super.onDestroy()
    }
}
