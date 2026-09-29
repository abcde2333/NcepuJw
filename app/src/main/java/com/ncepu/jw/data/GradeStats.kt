package com.ncepu.jw.data

/**
 * 成绩汇总口径,对齐教务「成绩预览」单（已修总学分 / 平均学分绩 / 平均课程绩点）。
 *
 * 规则:等级制先折成百分制（优 95 / 良 85 / 中 75 / 及格·通过 60），再统一算
 * 加权平均与绩点；绩点 =（分 - 50)/10，低于 60 记 0（优 95 自然得 4.5，与教务一致）。
 * 分母是全部计入课程的学分之和。
 *
 * 用一份真实成绩单核过:总学分 65.5（必修 59.5 + 公选 6.0）、平均学分绩 76.24、
 * 平均课程绩点 2.62 三项全部吻合。及格/通过档该份样本里没有,取值是按同系推断。
 */
object GradeStats {

    /** 成绩字符串 → 百分制数值；缺考/未出/免修等无法折算时返回 null */
    fun toScore(raw: String): Double? {
        val s = raw.trim()
        s.toDoubleOrNull()?.let { return it }
        return when {
            s.contains("优") -> 95.0
            s.contains("良") -> 85.0
            s.contains("中") -> 75.0
            s.contains("及格") || s.contains("通过") -> 60.0
            else -> null
        }
    }

    fun toPoint(score: Double): Double = if (score < 60.0) 0.0 else (score - 50.0) / 10.0

    data class Summary(
        val avgScore: Double?,
        val avgPoint: Double?,
        val credits: Double,
        val counted: Int,
    )

    /** 学分或成绩无法解析的课程整条不计入（与教务"已修"口径一致） */
    fun summarize(grades: List<Grade>): Summary {
        var creditSum = 0.0
        var weightedScore = 0.0
        var weightedPoint = 0.0
        var counted = 0
        for (g in grades) {
            val credit = g.credit.toDoubleOrNull() ?: continue
            val score = toScore(g.score) ?: continue
            if (credit <= 0.0) continue
            creditSum += credit
            weightedScore += score * credit
            weightedPoint += toPoint(score) * credit
            counted++
        }
        if (creditSum <= 0.0) return Summary(null, null, 0.0, 0)
        return Summary(weightedScore / creditSum, weightedPoint / creditSum, creditSum, counted)
    }
}
