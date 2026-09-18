package com.ncepu.jw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 中国法定节假日同步(timor.tech 公开接口,与拾光课程表同源)。
 * 拉全年 holiday==true 的日期集合,供上课提醒与小部件跳过假日。
 * 失败返回 Result.failure,调用方保留旧缓存(fail-open:拉不到绝不误屏蔽正常提醒)。
 */
object HolidayClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 拉取指定年份的全年法定假日 → ISO 日期串集合(yyyy-MM-dd)。 */
    suspend fun fetchYear(year: Int): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://timor.tech/api/holiday/year/$year")
                .header("User-Agent", UA)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("空响应")
                val root = JSONObject(body)
                if (root.optInt("code", -1) != 0) error("接口返回 code!=0")
                val holiday = root.optJSONObject("holiday") ?: return@use emptySet()
                val out = mutableSetOf<String>()
                val keys = holiday.keys()
                while (keys.hasNext()) {
                    val item = holiday.optJSONObject(keys.next()) ?: continue
                    // holiday=true 为放假日;false 为调休补班日(不纳入跳过)
                    if (item.optBoolean("holiday")) {
                        val date = item.optString("date")
                        if (date.length == 10) out += date
                    }
                }
                out
            }
        }
    }
}
