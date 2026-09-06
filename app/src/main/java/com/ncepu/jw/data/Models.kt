package com.ncepu.jw.data

import androidx.compose.runtime.Immutable

/**
 * 数据模型(华电教务系统 = 强智老版 jsxsd 部署)
 * UI 层大量持有这些类型:@Immutable 让 Compose 把它们视为稳定类型,
 * 状态变化时未变的课程卡片/列表项可以跳过重组(否则 List<Course> 永远不稳定,
 * 课表翻页等场景整网格重组掉帧)。
 */

/** 学年学期。key 格式 "2026-2027-1",课表与成绩接口通用 */
data class Semester(val year: Int, val term: Int) {
    val key: String get() = "$year-${year + 1}-$term"
    val displayName: String get() = "$year-${year + 1} 第${term}学期"

    companion object {
        /** 依当前日期推断默认学年学期:9月起为第1学期,1-2月仍属上一年第1学期,3-6月第2学期,7-8月第3学期 */
        fun current(): Semester {
            val cal = java.util.Calendar.getInstance()
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val year = cal.get(java.util.Calendar.YEAR)
            return when {
                month >= 9 -> Semester(year, 1)
                month <= 2 -> Semester(year - 1, 1)
                month <= 6 -> Semester(year - 1, 2)
                else -> Semester(year - 1, 3)
            }
        }

        /** 从当前学期往前推 num 个,供下拉选择 */
        fun options(num: Int = 8): List<Semester> {
            val list = mutableListOf<Semester>()
            var s = current()
            repeat(num) {
                list += s
                s = if (s.term == 1) Semester(s.year, 2)
                else if (s.term == 2) Semester(s.year, 3)
                else Semester(s.year - 1, 1)
            }
            return list
        }
    }
}

/** 单门课程的一条课表记录 */
@Immutable
data class Course(
    val name: String,       // 课程名
    val day: Int,           // 1-7 星期一~日
    val sections: IntRange, // 节次范围,如 1..2
    val weeks: String,      // 周次说明,如 "2-9(周)"(可能多段合并)
    val teacher: String,    // 教师(该部署常为空)
    val room: String,       // 教室
    val credit: String,     // 学分
    val group: String = "", // 分组名称(如课堂派班)
    val attr: String = "",  // 课程属性(必修/选修)
)

/** 单门课程成绩 */
@Immutable
data class Grade(
    val course: String,     // 课程名称
    val score: String,      // 成绩
    val credit: String,     // 学分
    val gradePoint: String, // 绩点(该部署常为空)
    val type: String,       // 课程性质(必修/公共基础教育等)
    val examType: String,   // 考试性质(正常考试/补考等)
    val term: String        // 开课学期,如 "2025-2026-1"
)

@Immutable
data class GradesPage(
    val items: List<Grade>, // 全部学期成绩,调用方按 term 过滤
)

/** 选课中心的一个选课轮次 */
@Immutable
data class XkRound(
    val term: String,       // 学年学期,如 2026-2027-1
    val name: String,       // 轮次名称,如 "2026-2027-1学期辅修选课"
    val startText: String,  // 开始时间,如 2026-09-01 09:00
    val endText: String,    // 截止时间,如 2026-09-11 15:00
    val dailyText: String,  // 每日开放时段,如 08:00-20:00
    val status: String,     // 未开始 / 进行中 / 已结束
    val ongoing: Boolean,   // 是否进行中
    val url: String,        // 进入选课的相对链接
)

/** 已选课程(选课结果) */
@Immutable
data class SelectedCourse(
    val code: String,       // 课程编号
    val name: String,       // 课程名称
    val seq: String,        // 课序号
    val group: String,      // 分组名称
    val teacher: String,    // 任课老师
    val hours: String,      // 总学时
    val credit: String,     // 学分
    val attr: String,       // 课程属性(必修/选修)
    val nature: String,     // 课程性质
)

/** 培养方案中的课程(教学进程表) */
@Immutable
data class PyfaCourse(
    val category: String,   // 类别(公共基础教育/学科门类基础/专业基础/…)
    val code: String,       // 课程编号
    val name: String,       // 课程名称
    val credit: String,     // 学分
    val hoursText: String,  // 总学时(含考核方式标记)
    val lecture: String,    // 讲课学时
    val experiment: String, // 实验学时
    val practice: String,   // 实践学时
    val term: String,       // 开设学期(1-8)
)

/** 培养方案整体 */
@Immutable
data class PyfaData(
    val major: String,          // 专业名
    val grade: String,          // 适用年级
    val goals: List<String>,    // 培养目标段落
    val requirements: List<String>, // 培养基本要求段落
    val coreCourses: String,    // 专业核心课程文本
    val courses: List<PyfaCourse>,
)

/** 一场考试 */
@Immutable
data class Exam(
    val type: String,       // 考试类型(期中考试/结课考试…)
    val code: String,       // 课程编号
    val name: String,       // 课程名称
    val timeText: String,   // 考试时间原文,如 2026-06-16 07:50~09:50
    val startMillis: Long,  // 开始时间(解析失败为 0)
    val endMillis: Long,
    val room: String,       // 考试地点
    val teacher: String,    // 任课老师
    val note: String,       // 说明
    val seat: String,       // 座位号
)

/** 教务系统首页"我的周课表"(官方按周过滤,周次零误差) */
@Immutable
data class HomeWeek(
    val week: Int,          // 官方周次(从课程"上课时间:第N周"提取)
    val courses: List<Course>,
)
