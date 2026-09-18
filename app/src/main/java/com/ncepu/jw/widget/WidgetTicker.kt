package com.ncepu.jw.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.parseWeeks
import com.ncepu.jw.reminder.ReminderReceiver
import com.ncepu.jw.ui.sectionSlots
import java.util.Calendar

/**
 * 到"下一节课下课点"定时刷新小部件,让"今天"列里已上完的课及时消失。
 * 每次刷新后重排下一个下课点,自我续接;今天无待上课程则不再排。
 */
object WidgetTicker {

    private const val REQ = 778899

    fun scheduleNext(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(context)
            am.cancel(pi) // 覆盖式:先撤旧的再排新的

            val store = SettingsStore(context)
            // 法定节假日:今天无课,无需"下课点"刷新,撤掉待触发的 tick 直接返回
            val todayCal = Calendar.getInstance()
            val todayIso = "%04d-%02d-%02d".format(
                todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH),
            )
            if (store.isHoliday(todayIso)) return
            val courses = store.loadCachedCourses()
            if (courses.isEmpty()) return
            val now = Calendar.getInstance()
            val week = SettingsStore.currentWeek(now.timeInMillis, store.weekStartMillis).coerceIn(1, 30)
            val todayDow = now.dayOfWeekIso()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val slotMap = sectionSlots(store.sectionTimes).associate { (no, s, e) -> no to (s to e) }

            // 今天待上课程里,结束时间 > 现在 的最近一个下课点(分钟)
            val nextEndMin = courses
                .filter { it.day == todayDow && (parseWeeks(it.weeks)?.contains(week) ?: true) }
                .mapNotNull { c -> slotMap[c.sections.last]?.second?.let { minutesOf(it) } }
                .filter { it > nowMin }
                .minOrNull() ?: return

            val trigger = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, nextEndMin / 60)
                set(Calendar.MINUTE, nextEndMin % 60)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }
            if (trigger.timeInMillis <= System.currentTimeMillis()) return
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, 60_000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pi)
            }
        }
    }

    private fun minutesOf(hhmm: String): Int? {
        val p = hhmm.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull(); val m = p[1].toIntOrNull() ?: return null
        return if (h != null) h * 60 + m else null
    }

    private fun pending(context: Context): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("widget_tick", true)
        return PendingIntent.getBroadcast(
            context, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
