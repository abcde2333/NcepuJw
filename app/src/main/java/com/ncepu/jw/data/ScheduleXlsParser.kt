package com.ncepu.jw.data

import jxl.Workbook
import java.io.ByteArrayInputStream

/**
 * 华电教务"学生个人课表"XLS 导出解析(xskb_print.do)。
 *
 * 表结构(实测):
 *  - 行2 = 表头(星期一~星期日),行3~7 = 第一~第五大节,首列 = 大节标签+时间
 *  - 课程单元格 = 7 行一段:课程号-序号 / 课程名 / (分组) / 教师 / 周次(2-9[周]) / 教室 / [01-02]节
 *  - 同格多段直接拼接(每段以 [NN-NN]节 行结束);跨大节的课在多行重复(按 key 去重)
 */
object ScheduleXlsParser {

    private val SEC_LINE = Regex("""\[[\d\-]+\]节""")
    private val WEEKS_LINE = Regex("""[\d,\-]+\[周]""")
    private val SEC_NUMS = Regex("""\d+""")

    fun parse(bytes: ByteArray): List<Course> {
        val wb = Workbook.getWorkbook(ByteArrayInputStream(bytes))
        val out = mutableListOf<Course>()
        val seen = mutableSetOf<String>()
        try {
            val sh = wb.getSheet(0)

            // 定位表头行(含 7 个"星期X")并建立 列 → 星期 映射
            var headerRow = -1
            val dayCols = mutableMapOf<Int, Int>()
            for (r in 0 until sh.rows) {
                var found = 0
                dayCols.clear()
                for (c in 0 until sh.columns) {
                    val t = sh.getCell(c, r).contents.trim()
                    if (t.startsWith("星期")) {
                        found++
                        dayCols[c] = when (t.removePrefix("星期").trim()) {
                            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4
                            "五" -> 5; "六" -> 6; "日", "天" -> 7
                            else -> 0
                        }
                    }
                }
                if (found >= 7) {
                    headerRow = r
                    break
                }
            }
            if (headerRow < 0) return emptyList()

            for (r in headerRow + 1 until sh.rows) {
                val label = sh.getCell(0, r).contents
                if (label.contains("备注")) break
                for (c in 1 until sh.columns) {
                    val day = dayCols[c] ?: continue
                    if (day == 0) continue
                    val text = sh.getCell(c, r).contents
                    if (text.isBlank()) continue
                    for (seg in splitSegments(text)) {
                        val course = parseSegment(seg, day) ?: continue
                        val key = "${course.day}|${course.name}|${course.sections}|${course.weeks}"
                        if (seen.add(key)) out += course
                    }
                }
            }
        } finally {
            wb.close()
        }
        return out
    }

    /** 单元格 → 段列表(每段以 [NN-NN]节 行结束) */
    private fun splitSegments(text: String): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        for (raw in text.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            cur += line
            if (SEC_LINE.matches(line)) {
                segments += cur
                cur = mutableListOf()
            }
        }
        return segments
    }

    private fun parseSegment(seg: List<String>, day: Int): Course? {
        if (seg.size < 4) return null
        val name = seg.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val weeksIdx = seg.indexOfFirst { WEEKS_LINE.containsMatchIn(it) }
        if (weeksIdx < 0) return null
        val weeks = seg[weeksIdx]
        val secIdx = seg.indexOfFirst { SEC_LINE.containsMatchIn(it) }
        if (secIdx < 0) return null
        val nums = SEC_NUMS.findAll(seg[secIdx]).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return null
        val sections = nums.first()..nums.last()
        val room = seg.getOrNull(secIdx - 1)?.takeIf { secIdx >= 1 } ?: ""
        val teacher = seg.getOrNull(weeksIdx - 1)?.takeIf { weeksIdx >= 3 } ?: ""
        val group = seg.firstOrNull { it.startsWith("(") && it.endsWith(")") }?.trim('(', ')') ?: ""
        return Course(
            name = name,
            day = day,
            sections = sections,
            weeks = weeks,
            teacher = teacher,
            room = room,
            credit = "",
            group = group,
        )
    }
}
