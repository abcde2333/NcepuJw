package com.ncepu.jw.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.SettingsStore
import java.util.Calendar

/**
 * 上课提醒调度。
 *
 * 策略:每次调用 [reschedule] 为"今天起 3 天内"的每节有课的大节设置一个精确闹钟
 * (同一时段多门课只取第一门),另设每天 04:03 的刷新闹钟滚动窗口;
 * 设备重启后由 BootReceiver 重建。
 */
object ReminderScheduler {

    /** 课程闹钟 requestCode = dateId*16 + sectionRow,稳定可覆盖 */
    private const val REFRESH_REQUEST_CODE = 10001
    const val CHANNEL_ID = "class_reminder"

    /**
     * 显式创建通知渠道(高重要性:横幅 + 声音 + 震动,纯通知方式提醒)。
     * 不创建的话首次 notify 会按系统默认(无横幅)自动建渠道,后续改不回来。
     */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID, "上课/考试提醒", android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "上课与考试的提前提醒通知"
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun calendarDayOfWeekToCourseDay(dow: Int): Int = when (dow) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7
    }

    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val store = SettingsStore(context)

        cancelAllPending(context, am, store)

        val courses = if (store.reminderEnabled) store.loadCachedCourses() else emptyList()
        val times = store.sectionTimes
        val lead = store.leadMinutes
        val now = System.currentTimeMillis()

        val day = Calendar.getInstance()
        for (offset in 0 until 3) {
            day.add(Calendar.DAY_OF_MONTH, if (offset == 0) 0 else 1)
            val dow = calendarDayOfWeekToCourseDay(day.get(Calendar.DAY_OF_WEEK))
            val dayId = day.get(Calendar.YEAR) * 10000 +
                (day.get(Calendar.MONTH) + 1) * 100 + day.get(Calendar.DAY_OF_MONTH)

            // 该天每个大节的第一门课(11-12 节及以后不排提醒)
            val byRow = LinkedHashMap<Int, Course>()
            for (c in courses) {
                if (c.day != dow) continue
                val row = SettingsStore.sectionRowIndex(c.sections.first)
                if (row < 0 || row >= times.size) continue
                if (!byRow.containsKey(row)) byRow[row] = c
            }

            for ((row, course) in byRow) {
                val trigger = courseTriggerMillis(day, times[row], lead) ?: continue
                if (trigger <= now) continue
                setExact(context, am, trigger, dayId * 16 + row, course, row, times[row])
            }
        }

        // 考试提醒:未来 35 天内的每场考试
        if (store.examReminderEnabled) {
            val exams = store.loadCachedExams()
            for ((idx, exam) in exams.withIndex()) {
                if (exam.startMillis <= now) continue
                if (exam.startMillis > now + 35L * 24 * 3600_000) continue
                val trigger = exam.startMillis - lead * 60_000L
                if (trigger <= now) continue
                setExamAlarm(context, am, trigger, exam, idx)
            }
        }

        // 每天 04:03 的窗口滚动刷新(任一提醒开启才需要)
        if (store.reminderEnabled || store.examReminderEnabled) {
            val refresh = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 4); set(Calendar.MINUTE, 3)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
            }
            val pi = refreshPendingIntent(context)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setWindow(AlarmManager.RTC_WAKEUP, refresh.timeInMillis, 10 * 60_000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, refresh.timeInMillis, pi)
            }
        }
    }

    /** 考试闹钟:requestCode 与课程区间错开(+100 起) */
    private fun setExamAlarm(
        context: Context,
        am: AlarmManager,
        triggerAt: Long,
        exam: com.ncepu.jw.data.Exam,
        idx: Int,
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("exam", true)
            putExtra("name", exam.name)
            putExtra("room", exam.room)
            putExtra("seat", exam.seat)
            putExtra("start", exam.startMillis)
        }
        val requestCode = ((triggerAt / 60000).toInt() and 0x3FFFFFFF) * 16 + 100 + (idx % 8)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 5 * 60_000L, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun courseTriggerMillis(
        dayBase: Calendar,
        startTime: String,
        leadMinutes: Int,
    ): Long? {
        val m = Regex("""(\d{1,2})[:：](\d{2})""").find(startTime) ?: return null
        val hh = m.groupValues[1].toInt().also { if (it !in 0..23) return null }
        val mm = m.groupValues[2].toInt().also { if (it !in 0..59) return null }
        return Calendar.getInstance().apply {
            timeInMillis = dayBase.timeInMillis
            set(Calendar.HOUR_OF_DAY, hh)
            set(Calendar.MINUTE, mm)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -leadMinutes)
        }.timeInMillis
    }

    private fun setExact(
        context: Context,
        am: AlarmManager,
        triggerAt: Long,
        requestCode: Int,
        course: Course,
        row: Int,
        startTime: String,
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("name", course.name)
            putExtra("room", course.room)
            putExtra("teacher", course.teacher)
            putExtra("row", row)
            putExtra("start", startTime)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 5 * 60_000L, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun refreshPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REFRESH_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).putExtra("refresh", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 覆盖式取消:按同样规则重设 3 天窗口为"空"即可清除旧的(FLAG_UPDATE_CURRENT 覆盖) */
    private fun cancelAllPending(context: Context, am: AlarmManager, store: SettingsStore) {
        // 3 天窗口内的旧闹钟一律用空 intent 覆盖取消
        val day = Calendar.getInstance()
        for (offset in 0 until 3) {
            day.add(Calendar.DAY_OF_MONTH, if (offset == 0) 0 else 1)
            val dayId = day.get(Calendar.YEAR) * 10000 +
                (day.get(Calendar.MONTH) + 1) * 100 + day.get(Calendar.DAY_OF_MONTH)
            for (row in 0 until 6) {
                val pi = PendingIntent.getBroadcast(
                    context, dayId * 16 + row,
                    Intent(context, ReminderReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pi != null) am.cancel(pi)
            }
        }
        val refresh = refreshPendingIntent(context)
        am.cancel(refresh)
    }

    /** 设置节次起始时间字符串(含校验) */
    fun normalizeTime(input: String, fallback: String): String {
        val m = Regex("""(\d{1,2})[:：]?(\d{0,2})""").find(input.trim()) ?: return fallback
        val hh = m.groupValues[1].toInt().takeIf { it in 0..23 } ?: return fallback
        val mm = m.groupValues[2].ifEmpty { "00" }.padEnd(2, '0').toInt()
            .takeIf { it in 0..59 } ?: 0
        return "%02d:%02d".format(hh, mm)
    }
}
