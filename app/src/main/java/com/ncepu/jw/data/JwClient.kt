package com.ncepu.jw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 华电教务系统(强智老版 jsxsd 部署)客户端。
 *
 * 登录(逆向自登录页 JS):
 * 1. GET /                                — 领 JSESSIONID
 * 2. POST /Logon.do?method=logon&flag=sess — 返回 "scode#sxh"
 * 3. 按 QiangzhiCrypto 生成 encoded
 * 4. POST /Logon.do?method=logon(userAccount/userPassword/encoded)
 *    成功:302 → /jsxsd/xk/LoginToXk?... → ... → /jsxsd/framework/xsMain.jsp
 *    失败:200 返回登录页 HTML(可能含 alert 提示)
 * 5. 跳转链必须手动跟随到底,jsxsd 会话才建立
 *
 * 数据接口(均无 csrftoken 要求):
 * - 课表:GET  /jsxsd/xskb/xskb_list.do?xnxq01id=2026-2027-1
 *          返回服务端渲染 HTML,table#kbtable,行=大节 列=星期,格内 div.kbcontent
 * - 成绩:GET  /jsxsd/kscj/cjcx_frm(必须先访问,注册会话状态)
 *         POST /jsxsd/kscj/cjcx_list {xnm=2025-2026, xqm=1}
 *          返回 HTML table#dataList,一次返回全部学期,按"开课学期"列过滤
 */
class JwClient(private val baseUrl: String = DEFAULT_BASE) {

    companion object {
        const val DEFAULT_BASE = "https://jwxt.ncepu.edu.cn"
        private val ALERT_REGEX = Regex("""alert\s*\(\s*['"](.+?)['"]\s*\)""", RegexOption.DOT_MATCHES_ALL)
        private const val NAME_BLACKLIST = "性别|民族|籍贯|出生|政治|面貌|姓名|学院|专业|班级|国籍"
    }

    private val cookieStore = LinkedHashMap<String, Cookie>()

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(cookieStore) { for (c in cookies) cookieStore[c.name] = c }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                synchronized(cookieStore) { cookieStore.values.toList() }
        })
        .build()

    var studentName: String? = null
        private set

    var loggedIn: Boolean = false
        private set

    /** 当前会话 cookie 串(供 WebView 同步登录态) */
    fun cookieHeader(): String = synchronized(cookieStore) {
        cookieStore.values.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun baseRequest(url: HttpUrl, referer: String = "$baseUrl/"): Request.Builder =
        Request.Builder().url(url)
            .header("Referer", referer)
            .header("User-Agent", UA)

    /** GET,不跟跳 */
    private fun get(path: String, referer: String = "$baseUrl/"): Response =
        client.newCall(baseRequest(baseUrl.toHttpUrl().resolve(path)!!, referer).build()).execute()

    /** POST 表单,不跟跳 */
    private fun postForm(path: String, form: Map<String, String>, referer: String = "$baseUrl/"): Response {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return client.newCall(baseRequest(baseUrl.toHttpUrl().resolve(path)!!, referer).post(body).build()).execute()
    }

    /** 手动跟随重定向,返回最终(非 302)响应与最终路径 */
    private fun follow(request: Request): Pair<Response, String> {
        var req = request
        var path = request.url.encodedPath
        repeat(8) {
            val resp = client.newCall(req).execute()
            val loc = resp.header("Location")
            if (resp.isRedirect && loc != null) {
                resp.close()
                val next = baseUrl.toHttpUrl().resolve(loc) ?: return resp to path
                path = next.encodedPath
                req = baseRequest(next).build()
                return@repeat
            }
            return resp to path
        }
        throw JwException("重定向次数过多")
    }

    /** 请求是否为"会话失效/错误"页 */
    private fun isSessionLost(html: String): Boolean =
        html.contains("chucuole") || (html.contains("method=exit") && html.length < 2000) ||
            (html.contains("method=logon", true) && html.contains("<html", true))

    /**
     * 登录。成功返回 null;失败返回提示信息。
     */
    suspend fun login(account: String, password: String): String? = withContext(Dispatchers.IO) {
        try {
            get("/").use { it.body?.string() }

            val precheck = postForm("/Logon.do?method=logon&flag=sess", emptyMap()).use { r ->
                if (!r.isSuccessful) throw JwException("登录预检失败 HTTP ${r.code}")
                r.body?.string()?.trim().orEmpty()
            }
            if (!precheck.contains("#")) throw JwException("预检响应异常: ${precheck.take(60)}")

            val encoded = QiangzhiCrypto.encodeCredentials(account, password, precheck)
            postForm("/Logon.do?method=logon", mapOf(
                "userAccount" to account,
                "userPassword" to password,
                "encoded" to encoded,
            )).use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isRedirect) {
                    // 跟随 302 链建立 jsxsd 会话
                    val loc = r.header("Location")
                    var ok = false
                    if (loc != null) {
                        val next = baseUrl.toHttpUrl().resolve(loc)
                        if (next != null) {
                            val (final, finalPath) = follow(baseRequest(next).build())
                            final.use { it.body?.string() }
                            ok = finalPath.contains("/jsxsd/")
                        }
                    }
                    loggedIn = ok
                    if (ok) studentName = tryFetchName()
                    return@use if (ok) null else "登录跳转异常,请重试"
                }
                loggedIn = false
                val msg = ALERT_REGEX.find(text)?.groupValues?.get(1)?.trim()
                msg?.ifBlank { null } ?: "登录失败,请检查账号密码"
            }
        } catch (e: JwException) {
            loggedIn = false
            e.message
        } catch (e: Exception) {
            loggedIn = false
            "网络错误: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** 从学籍卡片页提取姓名,失败返回 null(UI 回退显示学号) */
    private fun tryFetchName(): String? = try {
        val html = get("/jsxsd/grxx/xsxx").use { it.body?.string().orEmpty() }
        Regex("""姓名[\s\S]{0,600}?>\s*([\u4e00-\u9fa5]{2,4})<""").find(html)
            ?.groupValues?.get(1)
            ?.takeIf { !it.matches(Regex("""(?:$NAME_BLACKLIST)""")) }
    } catch (_: Exception) { null }

    /** 查询课表(周视图),失败抛 JwException */
    suspend fun fetchCourses(sem: Semester): List<Course> = withContext(Dispatchers.IO) {
        val html = get("/jsxsd/xskb/xskb_list.do?xnxq01id=${sem.key}").use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            if (!body.contains("kbtable")) throw JwException("课表页面异常(HTTP ${r.code})")
            body
        }
        parseScheduleHtml(html)
    }

    /** 查询成绩(全部学期),调用方按 Grade.term 过滤 */
    suspend fun fetchGrades(sem: Semester): GradesPage = withContext(Dispatchers.IO) {
        // 入口页必须先访问,否则 cjcx_list 返回"出错了"页
        get("/jsxsd/kscj/cjcx_frm", "$baseUrl/jsxsd/framework/xsMain.jsp").use { it.body?.string() }
        val html = postForm(
            "/jsxsd/kscj/cjcx_list",
            mapOf("xnm" to "${sem.year}-${sem.year + 1}", "xqm" to "${sem.term}"),
            "$baseUrl/jsxsd/kscj/cjcx_frm",
        ).use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            if (!body.contains("dataList")) {
                // 评教期拦截等场景:服务端以 alert 提示,透出原文
                val alert = ALERT_REGEX.find(body)?.groupValues?.get(1)?.trim()
                throw JwException(alert?.takeIf { it.isNotBlank() } ?: "成绩页面异常(HTTP ${r.code}),若提示评教请先完成评教")
            }
            body
        }
        GradesPage(parseGradeHtml(html))
    }

    // ---------- HTML 解析 ----------

    /** 剥离 HTML 注释(教务页面里常有注释掉的 td/a,会干扰索引解析) */
    private fun stripComments(html: String): String =
        html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private val TD_RE = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
    private val TH_RE = Regex("""<th[^>]*>([\s\S]*?)</th>""", RegexOption.IGNORE_CASE)
    private val TR_RE = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
    private val DIV_RE = Regex("""<div id="[^"]*" class="kbcontent\d*">([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
    private val ROW_LABEL_RE = Regex("""第\s*([一二三四五六七八九十])\s*大节""")

    /** 清理单元格 HTML 为纯文本行 */
    private fun cellLines(html: String): List<String> =
        html.replace(Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]*>"""), "")
            .replace("&nbsp;", " ")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "-" }

    private fun tableSection(html: String, tableId: String): String {
        val start = html.indexOf("""id="$tableId"""")
        if (start < 0) return ""
        val from = html.lastIndexOf("<table", start).coerceAtLeast(0)
        val end = html.indexOf("</table>", start)
        return if (end > from) html.substring(from, end + 8) else ""
    }

    /**
     * 解析课表页:table#kbtable(转置表格)。
     * 表头行(星期一~日,th);数据行 = 行标签 th("第X大节") + 7 个 td(周一~日格子);
     * 每格 div.kbcontent 内容按 "----" 分组,每组 [课程号,课程名,周次,教室,(教师)]
     */
    internal fun parseScheduleHtml(html: String): List<Course> {
        val table = tableSection(stripComments(html), "kbtable")
        if (table.isEmpty()) return emptyList()
        val out = mutableListOf<Course>()

        for (rowHtml in TR_RE.findAll(table)) {
            val row = rowHtml.groupValues[1]
            val thLabel = TH_RE.findAll(row)
                .map { cellLines(it.groupValues[1]).joinToString(" ") }
                .firstOrNull { it.contains("大节") } ?: continue
            val m = ROW_LABEL_RE.find(thLabel) ?: continue
            val bigSection = when (m.groupValues[1]) {
                "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4
                "五" -> 5; "六" -> 6; "七" -> 7
                else -> continue
            }
            val sections = (bigSection * 2 - 1)..(bigSection * 2)

            val tds = TD_RE.findAll(row).map { it.groupValues[1] }.toList()
            for ((idx, td) in tds.withIndex()) {
                if (idx >= 7) break
                val divs = DIV_RE.findAll(td).map { it.groupValues[1] }.toList()
                for (div in divs) {
                    parseScheduleCell(div, idx + 1, sections)?.let { out += it }
                }
            }
        }
        // 同天同节同名课合并周次
        return out.groupBy { Triple(it.day, it.sections, it.name) }.map { (_, list) ->
            if (list.size == 1) list[0] else list[0].copy(
                weeks = list.map { c -> c.weeks }.filter { it.isNotBlank() }
                    .distinct().joinToString(","),
            )
        }
    }

    private val GROUP_SPLIT_RE = Regex("""-{4,}""")

    private fun parseScheduleCell(divHtml: String, day: Int, sections: IntRange): Course? {
        val text = divHtml
            .replace(Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]*>"""), "")
            .replace("&nbsp;", " ")
            .trim()
        if (text.isEmpty()) return null

        val groups = GROUP_SPLIT_RE.split(text)
            .map { g -> g.split("\n").map { it.trim() }.filter { it.isNotEmpty() } }
            .filter { it.size >= 2 }
        if (groups.isEmpty()) return null

        // 每组结构:[课程号-序号, 课程名, 周次, 教室, (教师)?]
        val name = groups[0].getOrNull(1) ?: return null
        val weeks = groups.mapNotNull { g ->
            g.getOrNull(2)?.takeIf { it.contains("周") }
        }.distinct().joinToString(",")
        val room = groups[0].getOrNull(3)?.takeIf { !it.contains("周") } ?: ""
        val teacher = groups[0].getOrNull(4)?.takeIf { it.length in 2..4 } ?: ""

        return Course(
            name = name,
            day = day,
            sections = sections,
            weeks = weeks,
            teacher = teacher,
            room = room,
            credit = "",
        )
    }

    /**
     * 解析成绩页:table#dataList,首行表头(15 列):
     * 序号|开课学期|课程编号|课程名称|成绩|成绩标识|学分|总学时|绩点|补重学期|考核方式|考试性质|课程属性|课程性质|通选课类别
     */
    internal fun parseGradeHtml(html: String): List<Grade> {
        val table = tableSection(stripComments(html), "dataList")
        if (table.isEmpty()) return emptyList()
        val out = mutableListOf<Grade>()

        for (rowHtml in TR_RE.findAll(table)) {
            val row = rowHtml.groupValues[1]
            if (row.contains("<th", true)) continue
            val tds = TD_RE.findAll(row).map { it.groupValues[1] }.toList()
            if (tds.size < 14) continue
            val c = { i: Int -> cellLines(tds[i]).joinToString(" ").trim() }
            val name = c(3)
            if (name.isEmpty()) continue
            val term = c(1)
            out += Grade(
                course = name,
                score = c(4),
                credit = c(6),
                gradePoint = c(8),
                type = c(13),
                examType = c(11),
                term = term,
            )
        }
        return out
    }

    // ---------- 选课 ----------

    /** 选课中心轮次列表 */
    suspend fun fetchXkRounds(): List<XkRound> = withContext(Dispatchers.IO) {
        val html = get("/jsxsd/xsxk/xklc_list").use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            body
        }
        parseXkRounds(html)
    }

    /** 已选课程结果 */
    suspend fun fetchSelectedCourses(sem: Semester): List<SelectedCourse> = withContext(Dispatchers.IO) {
        get("/jsxsd/xkgl/xsxkjgcx").use { it.body?.string() }
        val html = postForm(
            "/jsxsd/xkgl/loadXsxkjgList",
            mapOf("xnxqid" to sem.key),
            "$baseUrl/jsxsd/xkgl/xsxkjgcx",
        ).use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            body
        }
        parseSelectedCourses(html)
    }

    internal fun parseXkRounds(html: String): List<XkRound> {
        val out = mutableListOf<XkRound>()
        val now = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)

        val clean = stripComments(html)
        for (rowHtml in TR_RE.findAll(clean)) {
            val row = rowHtml.groupValues[1]
            if (!row.contains("进入选课")) continue
            val tds = TD_RE.findAll(row).map { cellLines(it.groupValues[1]).joinToString(" ").trim() }.toList()
            if (tds.size < 4) continue
            val link = Regex("""href="([^"]*(?:xsxk_index|xklc_view)[^"]*)"""", RegexOption.IGNORE_CASE)
                .find(row)?.groupValues?.get(1) ?: continue
            // 时间列:按日期特征定位(防止注释/空列导致索引漂移)
            val timeText = tds.firstOrNull { Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}~\d{4}-\d{2}-\d{2}""").containsMatchIn(it) }
                ?: tds[2]
            val range = Regex("""(\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s*~\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2})""").find(timeText)
            val startText = range?.groupValues?.get(1).orEmpty()
            val endText = range?.groupValues?.get(2).orEmpty()
            val dailyText = Regex("""每天[:：]\s*[（(]([\d:~\-—]+)[)）]""").find(timeText)?.groupValues?.get(1).orEmpty()
            val status: String = run {
                val s = runCatching { fmt.parse(startText) }.getOrNull()?.time
                val e = runCatching { fmt.parse(endText) }.getOrNull()?.time
                when {
                    s == null || e == null -> "时间未知"
                    now < s -> "未开始"
                    now > e -> "已结束"
                    else -> "进行中"
                }
            }
            out += XkRound(
                term = tds.getOrNull(0) ?: "",
                name = tds.getOrNull(1) ?: "",
                startText = startText,
                endText = endText,
                dailyText = dailyText,
                status = status,
                ongoing = status == "进行中",
                url = link,
            )
        }
        return out
    }

    internal fun parseSelectedCourses(html: String): List<SelectedCourse> {
        val table = tableSection(stripComments(html), "dataList")
        val scope = table.ifEmpty { html }
        val out = mutableListOf<SelectedCourse>()

        for (rowHtml in TR_RE.findAll(scope)) {
            val row = rowHtml.groupValues[1]
            if (row.contains("<th", true) || row.contains("jValue")) continue
            val tds = TD_RE.findAll(row).map { it.groupValues[1] }.toList()
            if (tds.size < 10) continue
            val c = { i: Int -> cellLines(tds[i]).joinToString(" ").trim() }
            val name = c(2)
            if (name.isEmpty()) continue
            out += SelectedCourse(
                code = c(1),
                name = name,
                seq = c(3),
                group = c(4),
                teacher = c(5),
                hours = c(6),
                credit = c(7),
                attr = c(8),
                nature = c(9),
            )
        }
        return out
    }

    // ---------- 首页"我的周课表"(官方按周过滤) ----------

    /**
     * 按日期查询该周课表(教务首页"我的周课表"同源)。
     * rq 为该周内任意日期(YYYY-MM-DD),返回数据带官方周次。
     */
    suspend fun fetchHomeWeek(dateText: String): HomeWeek = withContext(Dispatchers.IO) {
        val html = postForm(
            "/jsxsd/framework/main_index_loadkb.jsp",
            mapOf("rq" to dateText),
            "$baseUrl/jsxsd/framework/xsMain_new.jsp",
        ).use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            body
        }
        parseHomeWeek(html)
    }

    /** 解析首页周课表片段:行=大节,列=星期;课程信息在格内 <p title='...'> */
    internal fun parseHomeWeek(input: String): HomeWeek {
        val html = stripComments(input)
        var week = 0
        val courses = mutableListOf<Course>()
        val titleRe = Regex("""title\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE)
        val timeRe = Regex("""第(\d+)周\s*星期([一二三四五六日天])\s*\[([\d\-]+)\]节""")
        val cnDay = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "日" to 7, "天" to 7,
        )

        for (rowHtml in TR_RE.findAll(html)) {
            val row = rowHtml.groupValues[1]
            val tds = TD_RE.findAll(row).map { it.groupValues[1] }.toList()
            if (tds.size < 2) continue

            for (idx in 1 until tds.size.coerceAtMost(8)) {
                val title = titleRe.find(tds[idx])?.groupValues?.get(1) ?: continue
                val fields = title.split(Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE))
                    .map { f ->
                        val p = f.split("：", limit = 2)
                        (p.getOrNull(0)?.trim() ?: "") to (p.getOrNull(1)?.trim() ?: "")
                    }
                val get = { k: String ->
                    fields.firstOrNull { it.first == k }?.second.orEmpty()
                }
                val name = get("课程名称")
                if (name.isEmpty()) continue
                val timeStr = get("上课时间")
                val tm = timeRe.find(timeStr) ?: continue
                val day = cnDay[tm.groupValues[2]] ?: continue
                val secs = tm.groupValues[3].split("-").mapNotNull { it.toIntOrNull() }
                if (secs.isEmpty()) continue
                val weekNo = tm.groupValues[1].toIntOrNull() ?: continue
                if (week == 0) week = weekNo

                courses += Course(
                    name = name,
                    day = day,
                    sections = secs.first()..secs.last(),
                    weeks = "第${weekNo}周",
                    teacher = "",
                    room = get("上课地点"),
                    credit = get("课程学分"),
                )
            }
        }
        return HomeWeek(week, courses)
    }

    // ---------- 考试安排 ----------

    /** 考试安排(按学期)。当前学期无考试安排时返回空列表 */
    suspend fun fetchExams(sem: Semester): List<Exam> = withContext(Dispatchers.IO) {
        // 入口页需要学期参数,否则服务端直接返回出错页
        get("/jsxsd/xsks/xsksap_query?xnxqid=${sem.key}").use { it.body?.string() }
        val html = postForm(
            "/jsxsd/xsks/xsksap_list",
            mapOf("xnxqid" to sem.key),
            "$baseUrl/jsxsd/xsks/xsksap_query?xnxqid=${sem.key}",
        ).use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            body
        }
        parseExamHtml(html)
    }

    internal fun parseExamHtml(html: String): List<Exam> {
        val out = mutableListOf<Exam>()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        val timeRe = Regex("""(\d{4}-\d{2}-\d{2})\s*(\d{2}:\d{2})\s*~\s*(\d{2}:\d{2})""")

        for (rowHtml in TR_RE.findAll(html)) {
            val row = rowHtml.groupValues[1]
            if (row.contains("<th", true)) continue
            val tds = TD_RE.findAll(row).map { it.groupValues[1] }.toList()
            if (tds.size < 10) continue
            val c = { i: Int -> cellLines(tds[i]).joinToString(" ").trim() }
            val name = c(3)
            if (name.isEmpty() || name.contains("未查询到")) continue
            var start = 0L; var end = 0L
            val tm = timeRe.find(c(4))
            if (tm != null) {
                try {
                    start = fmt.parse("${tm.groupValues[1]} ${tm.groupValues[2]}")?.time ?: 0L
                    end = fmt.parse("${tm.groupValues[1]} ${tm.groupValues[3]}")?.time ?: 0L
                } catch (_: Exception) {}
            }
            out += Exam(
                type = c(1),
                code = c(2),
                name = name,
                timeText = c(4),
                startMillis = start,
                endMillis = end,
                room = c(5),
                teacher = c(6),
                note = c(8),
                seat = c(9),
            )
        }
        return out
    }

    // ---------- 培养方案 ----------

    /** 培养方案明细页(整页大文档,服务端渲染) */
    suspend fun fetchPyfa(): PyfaData = withContext(Dispatchers.IO) {
        val html = get("/jsxsd/pyfa/topyfamx").use { r ->
            val body = r.body?.string().orEmpty()
            if (isSessionLost(body)) throw JwException("会话已失效,请重新登录")
            if (!body.contains("培养方案")) throw JwException("培养方案页面异常(HTTP ${r.code})")
            body
        }
        parsePyfa(html)
    }

    /** 全页纯文本行(去脚本/样式/标签) */
    private fun pageLines(html: String): List<String> =
        html.replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(p|div|tr|td|th|table|h[1-6]|li)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]*>"""), " ")
            .replace("&nbsp;", " ")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    internal fun parsePyfa(html: String): PyfaData {
        val lines = pageLines(html)

        // 专业名与年级
        val major = lines.firstOrNull { it.contains("培养方案及教学进程") }
            ?.substringBefore("培养方案及教学进程")?.trim().orEmpty()
        val grade = lines.firstOrNull { it.contains("版本号及适用年级") }
            ?.let { Regex("""[:：]\s*(\d{4})""").find(it)?.groupValues?.get(1) } ?: ""

        // 章节文本:按 "N、标题" 行切分
        val sectionStarts = mutableListOf<Pair<Int, String>>()
        lines.forEachIndexed { idx, line ->
            val m = Regex("""^[一二三四五六七八九十]+、\s*(\S{2,12})$""").find(line)
            if (m != null) sectionStarts += idx to m.groupValues[1]
        }
        fun sectionBody(title: String): List<String> {
            val startIdx = sectionStarts.firstOrNull { it.second == title } ?: return emptyList()
            val endIdx = sectionStarts.firstOrNull { it.first > startIdx.first }?.first ?: lines.size
            return lines.subList(startIdx.first + 1, endIdx)
                // 表格碎片行(短、数字/符号)过滤,保留正文段落
                .filter { it.length >= 14 || it.startsWith("目标") || it.startsWith("（") }
        }
        val goals = sectionBody("培养目标")
        val requirements = sectionBody("专业培养基本要求")
        val core = sectionBody("专业核心课程").joinToString("\n")

        // 教学进程主表:12 列行
        val courses = mutableListOf<PyfaCourse>()
        var rm: MatchResult? = TR_RE.find(html)
        while (rm != null) {
            val row = rm.groupValues[1]
            val tds = TD_RE.findAll(row).map { it.groupValues[1] }.toList()
            if (tds.size == 12) {
                val c = { i: Int -> cellLines(tds[i]).joinToString(" ").trim() }
                val code = c(1)
                val name = c(2)
                if (name.isNotEmpty() && code.matches(Regex("""[A-Za-z0-9]+"""))) {
                    courses += PyfaCourse(
                        category = c(0),
                        code = code,
                        name = name,
                        credit = c(4),
                        hoursText = c(5).replace(Regex("""[^0-9]"""), "").trim(),
                        lecture = c(6),
                        experiment = c(7),
                        practice = c(10),
                        term = c(11).filter { it.isDigit() }.ifEmpty { c(11) },
                    )
                }
            }
            rm = rm.next()
        }
        return PyfaData(major, grade, goals, requirements, core, courses)
    }
}

class JwException(message: String) : Exception(message)

private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
