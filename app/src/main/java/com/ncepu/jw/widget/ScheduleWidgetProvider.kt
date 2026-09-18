package com.ncepu.jw.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ncepu.jw.MainActivity
import com.ncepu.jw.R
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.Semester
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.parseWeeks
import com.ncepu.jw.ui.sectionSlots
import java.util.Calendar

/**
 * 桌面小部件「今日课表」(静态 RemoteViews)。
 *
 * 头部:学期缩写 + 日期/周次/星期 + 饮水快捷按钮;主体:今天/明天两列固定行(最多 5 节)。
 * 不使用 RemoteViewsService/ListView —— 数据由 Provider 一次性铺进布局,
 * 进程被 MIUI 冻结后仍可保留上次渲染,规避"载入窗口小部件时出现问题"。
 * 样式(深色/浅色/半透明)由 SettingsStore.widgetStyle 全局决定;法定节假日显示"放假无课"。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        runCatching {
            ids.forEach { mgr.updateAppWidget(it, buildViews(context)) }
            WidgetTicker.scheduleNext(context)
        }
    }

    companion object {
        /** 每列最多显示的行数(与 widget_schedule.xml 里 row0..row4 一致) */
        private const val MAX_ROWS = 5

        /** App 侧主动推送刷新。 */
        fun updateAll(context: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val cn = ComponentName(context, ScheduleWidgetProvider::class.java)
                val ids = mgr.getAppWidgetIds(cn)
                if (ids.isEmpty()) return
                val views = buildViews(context)
                ids.forEach { mgr.updateAppWidget(it, views) }
                WidgetTicker.scheduleNext(context)
            }
        }

        /** 单元格:一门课 / 一句提示 / 空 */
        private sealed class Cell {
            object Blank : Cell()
            data class Cls(val name: String, val detail: String) : Cell()
            data class Empty(val text: String) : Cell()
        }

        private fun buildViews(context: Context): RemoteViews {
            val store = SettingsStore(context)
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            val now = Calendar.getInstance()
            val week = SettingsStore.currentWeek(now.timeInMillis, store.weekStartMillis).coerceIn(1, 30)
            val dow = now.dayOfWeekIso()
            val weekName = "周" + when (dow) { 1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日" }
            val sem = Semester.current()
            views.setTextViewText(R.id.tv_semester, "${sem.year}${if (sem.term == 2) "春" else if (sem.term == 3) "夏" else "秋"}")
            views.setTextViewText(R.id.tv_date, "${now.get(Calendar.MONTH) + 1}.${now.get(Calendar.DAY_OF_MONTH)}  第${week}周 $weekName")

            // ---- 计算今天/明天两列 ----
            val courses = store.loadCachedCourses()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val slotMap = sectionSlots(store.sectionTimes).associate { (no, s, e) -> no to (s to e) }

            val todayIso = isoDate(now)
            val tomorrowCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val tomorrowIso = isoDate(tomorrowCal)
            val todayDow = dow
            val tomorrowDow = (dow % 7) + 1
            // 今天是周日:明天(周一)已进入下一教学周,按 week+1 过滤,否则显示的是本周已过去的周一
            val tomorrowWeek = if (todayDow == 7) (week + 1).coerceAtMost(30) else week

            val todayHoliday = store.isHoliday(todayIso)
            val tomorrowHoliday = store.isHoliday(tomorrowIso)

            val today = if (todayHoliday) listOf(Cell.Empty("放假无课"))
                else classesOn(courses, todayDow, week, slotMap, afterMin = nowMin)
            val tomorrow = if (tomorrowHoliday) listOf(Cell.Empty("放假无课"))
                else classesOn(courses, tomorrowDow, tomorrowWeek, slotMap, afterMin = null)

            // 空列补一句提示
            val todayCells = today.ifEmpty { listOf(Cell.Empty("今天没有课啦")) }
            val tomorrowCells = tomorrow.ifEmpty { listOf(Cell.Empty("明天没有课啦")) }

            // ---- 铺进固定行 ----
            val c = colors(store.widgetStyle)
            views.setTextViewText(R.id.h_today, if (todayHoliday) "今天 · 放假" else "今天")
            views.setTextViewText(R.id.h_tomorrow, if (tomorrowHoliday) "明天 · 放假" else "明天")
            views.setTextColor(R.id.h_today, c.label)
            views.setTextColor(R.id.h_tomorrow, c.label)
            for (i in 0 until MAX_ROWS) {
                bindSide(context, views, "l$i", todayCells.getOrNull(i) ?: Cell.Blank, c)
                bindSide(context, views, "r$i", tomorrowCells.getOrNull(i) ?: Cell.Blank, c)
                val rowVisible = todayCells.getOrNull(i) != null || tomorrowCells.getOrNull(i) != null
                setRowVisibility(context, views, i, rowVisible)
            }

            applyStyle(views, store.widgetStyle)

            // 整卡点击 → 打开 App(课表);饮水按钮 → 打开 App 并定位到饮水 tab(1)
            views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, 0))
            views.setOnClickPendingIntent(R.id.btn_water, tapIntent(context, 1))
            return views
        }

        /** 某天某周次的课程 → 单元格列表(今天过滤已上完的课) */
        private fun classesOn(
            courses: List<Course>,
            day: Int,
            week: Int,
            slotMap: Map<Int, Pair<String, String>>,
            afterMin: Int?,
        ): List<Cell> =
            courses.filter { it.day == day && (parseWeeks(it.weeks)?.contains(week) ?: true) }
                .sortedBy { it.sections.first }
                .map { c ->
                    val start = slotMap[c.sections.first]?.first ?: ""
                    val end = slotMap[c.sections.last]?.second ?: ""
                    Triple(c, start, end)
                }
                .filter { (_, _, end) -> afterMin == null || (minutesOf(end) ?: Int.MAX_VALUE) >= afterMin }
                .map { (c, start, end) ->
                    val time = if (start.isNotEmpty() && end.isNotEmpty()) "$start-$end"
                        else "第${c.sections.first}-${c.sections.last}节"
                    val room = c.room
                    val detail = listOf(time, room).filter { it.isNotBlank() }.joinToString(" ")
                    Cell.Cls(c.name, detail)
                }

        private fun bindSide(
            context: Context,
            views: RemoteViews,
            prefix: String,
            cell: Cell,
            c: Colors,
        ) {
            val nameId = id(context, "${prefix}_name")
            val detailId = id(context, "${prefix}_detail")
            val emptyId = id(context, "${prefix}_empty")
            when (cell) {
                is Cell.Cls -> {
                    views.setViewVisibility(nameId, View.VISIBLE)
                    views.setViewVisibility(detailId, View.VISIBLE)
                    views.setViewVisibility(emptyId, View.GONE)
                    views.setTextViewText(nameId, cell.name)
                    views.setTextColor(nameId, c.primary)
                    views.setTextViewText(detailId, cell.detail)
                    views.setTextColor(detailId, c.secondary)
                }
                is Cell.Empty -> {
                    views.setViewVisibility(nameId, View.GONE)
                    views.setViewVisibility(detailId, View.GONE)
                    views.setViewVisibility(emptyId, View.VISIBLE)
                    views.setTextViewText(emptyId, cell.text)
                    views.setTextColor(emptyId, c.empty)
                }
                Cell.Blank -> {
                    views.setViewVisibility(nameId, View.GONE)
                    views.setViewVisibility(detailId, View.GONE)
                    views.setViewVisibility(emptyId, View.GONE)
                }
            }
        }

        private fun setRowVisibility(context: Context, views: RemoteViews, i: Int, visible: Boolean) {
            views.setViewVisibility(id(context, "row$i"), if (visible) View.VISIBLE else View.GONE)
        }

        private fun id(context: Context, name: String): Int =
            context.resources.getIdentifier(name, "id", context.packageName)

        private fun isoDate(cal: Calendar): String = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )

        private fun minutesOf(hhmm: String): Int? {
            val p = hhmm.split(":")
            if (p.size != 2) return null
            val h = p[0].toIntOrNull(); val m = p[1].toIntOrNull() ?: return null
            return if (h != null) h * 60 + m else null
        }

        private fun tapIntent(context: Context, tab: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_TAB, tab)
            }
            return PendingIntent.getActivity(
                context, tab, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** 三套样式(深色/浅色/半透明):背景 + 头部文字色;正文单元格颜色由 colors() 提供 */
        private fun applyStyle(views: RemoteViews, style: String) {
            val (bg, primary) = when (style) {
                "light" -> Pair(R.drawable.widget_bg_light, 0xFF1B1B22.toInt())
                "translucent" -> Pair(R.drawable.widget_bg_translucent, 0xFFFFFFFF.toInt())
                else -> Pair(R.drawable.widget_bg_dark, 0xFFFFFFFF.toInt())
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bg)
            views.setTextColor(R.id.tv_semester, primary)
            views.setTextColor(R.id.tv_date, primary)
            views.setTextColor(R.id.btn_water, primary)
        }

        private data class Colors(
            val primary: Int, val secondary: Int, val label: Int, val empty: Int,
        )

        private fun colors(style: String): Colors = when (style) {
            "light" -> Colors(0xFF1B1B22.toInt(), 0xCC1B1B22.toInt(), 0x991B1B22.toInt(), 0x801B1B22.toInt())
            else -> Colors(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x80FFFFFF.toInt())
        }
    }
}

/** ISO 星期序号:1=周一 … 7=周日。widget 包内 Provider/WidgetTicker 共用。 */
internal fun Calendar.dayOfWeekIso(): Int = when (get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
    Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
    else -> 7
}
