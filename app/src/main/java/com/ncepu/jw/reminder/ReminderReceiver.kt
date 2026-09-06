package com.ncepu.jw.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ncepu.jw.MainActivity
import com.ncepu.jw.R
import com.ncepu.jw.data.SettingsStore

/** 闹钟到点:发上课提醒通知;收到刷新闹钟时重排未来 3 天 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("refresh", false)) {
            ReminderScheduler.reschedule(context)
            return
        }
        val lead = SettingsStore(context).leadMinutes
        if (intent.getBooleanExtra("exam", false)) {
            // 考试提醒
            val name = intent.getStringExtra("name") ?: return
            val room = intent.getStringExtra("room") ?: ""
            val seat = intent.getStringExtra("seat") ?: ""
            val start = intent.getLongExtra("start", 0L)
            val timeStr = if (start > 0) {
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(start))
            } else ""
            val title = if (lead > 0) "考试提醒 · $lead 分钟后开始" else "考试即将开始"
            val text = buildString {
                append(name)
                if (timeStr.isNotBlank()) append(" · ").append(timeStr).append(" 开考")
                if (room.isNotBlank()) append("\n地点:").append(room)
                if (seat.isNotBlank()) append("  座位:").append(seat)
            }
            notify(context, examNotification(context, title, text))
        } else {
            // 上课提醒
            val name = intent.getStringExtra("name") ?: return
            val room = intent.getStringExtra("room") ?: ""
            val row = intent.getIntExtra("row", 0)
            val start = intent.getStringExtra("start") ?: ""
            notify(context, buildNotification(context, name, room, row, start, lead))
        }
    }

    private fun examNotification(context: Context, title: String, text: String): Notification {
        val tap = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.replace("\n", "  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
    }

    private fun buildNotification(
        context: Context,
        course: String,
        room: String,
        row: Int,
        start: String,
        lead: Int,
    ): Notification {
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (lead > 0) "$lead 分钟后上课 · $start" else "即将上课 · $start"
        val text = buildString {
            append("第").append(row * 2 + 1).append("-").append(row * 2 + 2).append("节 ")
            append(course)
            if (room.isNotBlank()) append(" @ ").append(room)
        }
        return NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
    }

    private fun notify(context: Context, notification: Notification) {
        ReminderScheduler.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(System.currentTimeMillis().toInt() and 0x7FFFFFFF, notification)
    }
}

/** 系统重启后闹钟丢失,重建 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.reschedule(context)
        }
    }
}
