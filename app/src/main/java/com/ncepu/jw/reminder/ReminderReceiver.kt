package com.ncepu.jw.reminder

import android.Manifest
import android.app.Notification
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

/** 闹钟到点:发上课/考试提醒通知;收到刷新闹钟时重排未来 3 天;收到 dismiss 时定向取消 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("dismiss", false)) {
            // "知道了"按钮:精确取消这一条通知(不波及其他)
            val id = intent.getIntExtra("notif_id", 0)
            if (id != 0) NotificationManagerCompat.from(context).cancel(id)
            return
        }
        if (intent.getBooleanExtra("widget_tick", false)) {
            // 到下课点:刷新小部件(隐藏已上完的课)并排下一个下课点
            com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(context)
            com.ncepu.jw.widget.WidgetTicker.scheduleNext(context)
            return
        }
        if (intent.getBooleanExtra("refresh", false)) {
            ReminderScheduler.reschedule(context)
            com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(context)  // 每日刷新同步小部件(今天/明天/周次)
            return
        }
        val lead = SettingsStore(context).leadMinutes
        val notifId = intent.getIntExtra("notif_id", (System.currentTimeMillis().toInt() and 0x7FFFFFFF))
        val group = intent.getStringExtra("group")
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
            notify(
                context, notifId, group,
                examNotification(context, notifId, title, text),
            )
        } else {
            // 上课提醒
            val name = intent.getStringExtra("name") ?: return
            val room = intent.getStringExtra("room") ?: ""
            val row = intent.getIntExtra("row", 0)
            val start = intent.getStringExtra("start") ?: ""
            notify(
                context, notifId, group,
                buildNotification(context, notifId, name, room, row, start, lead),
            )
        }
    }

    private fun examNotification(context: Context, id: Int, title: String, text: String): Notification {
        return NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.replace("\n", "  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(context, id))
            .addAction(0, "知道了", dismissIntent(context, id))
            .build()
    }

    private fun buildNotification(
        context: Context,
        id: Int,
        course: String,
        room: String,
        row: Int,
        start: String,
        lead: Int,
    ): Notification {
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
            .setContentIntent(tapIntent(context, id))
            .addAction(0, "知道了", dismissIntent(context, id))
            .build()
    }

    /** 点击通知正文 → 打开 App(课表);requestCode 用通知 id 保证多条互不覆盖 */
    private fun tapIntent(context: Context, id: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_TAB, 0)
        }
        return PendingIntent.getActivity(
            context, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** "知道了" → 回自身广播,精确 cancel 这一条 */
    private fun dismissIntent(context: Context, id: Int): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java)
            .putExtra("dismiss", true)
            .putExtra("notif_id", id)
        return PendingIntent.getBroadcast(
            context, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(context: Context, id: Int, group: String?, notification: Notification) {
        ReminderScheduler.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val withGroup = if (group != null)
            NotificationCompat.Builder(context, notification)
                .setGroup(group)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .build()
        else notification
        manager.notify(id, withGroup)
        if (group != null) postSummary(context, manager, group)
    }

    /** 同一天多条提醒折叠成一组:静默摘要(GROUP_ALERT_CHILDREN 保证只有子通知响铃) */
    private fun postSummary(context: Context, manager: NotificationManagerCompat, group: String) {
        val sid = group.hashCode() and 0x7FFFFFFF
        val summary = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("课程提醒")
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(group)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(context, sid))
            .build()
        manager.notify(sid, summary)
    }
}

/** 系统重启后闹钟丢失,重建 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.reschedule(context)
            com.ncepu.jw.widget.ScheduleWidgetProvider.updateAll(context)
            com.ncepu.jw.widget.WidgetTicker.scheduleNext(context)
        }
    }
}
