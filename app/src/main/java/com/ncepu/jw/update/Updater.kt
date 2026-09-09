package com.ncepu.jw.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 应用内更新:GitHub Release 分发,国内镜像站加速。
 *
 * 元数据:仓库根 latest.json {versionCode, versionName, apkUrl}
 *   依次尝试 jsDelivr CDN → raw 镜像 → raw 直连;
 * APK 下载:apkUrl 依次尝试 ghfast.top / gh-proxy.com 加速前缀 → GitHub 直连。
 */
object Updater {

    const val REPO_OWNER = "abcde2333"
    const val REPO_NAME = "NcepuJw"

    /** 元数据候选(前者国内可达性最好;fetchLatest 并行取各源最大版本,过期源不会误导) */
    private val METADATA_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/$REPO_OWNER/$REPO_NAME@main/latest.json",
        "https://fastgit.cc/https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/latest.json",
        "https://ghfast.top/https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/latest.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/latest.json",
        "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/latest.json",
    )

    /** 下载加速前缀(空串 = GitHub 直连兜底)。fastgit 实测直传二进制、且跟随 raw 最新,列首位 */
    private val DOWNLOAD_PREFIXES = listOf(
        "https://fastgit.cc/",
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "",
    )

    data class Info(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val apkHash: String = "",
        val notes: String = "",          // 该版本更新日志(纯文本,来自 latest.json,供更新弹窗展示)
    )

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data class Latest(val versionName: String) : State()
        data class Available(val info: Info) : State()
        data class Downloading(val progress: Int) : State()
        data class Downloaded(val file: File, val versionName: String) : State()
        data class Failed(val message: String) : State()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // 总超时:慢镜像(能连上但速度极低)及时放弃,换下一个下载源
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    // 元数据查询专用:小 JSON,短超时,单个镜像挂了不拖慢整体(并行取)
    private val metaClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun currentVersionCode(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else pi.versionCode.toLong()
    } catch (_: Exception) {
        0L
    }

    fun currentVersionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /**
     * 拉取最新版本元数据:并行查询所有镜像,取 versionCode **最大**者。
     * 关键修复——jsDelivr 会长时间缓存 @main 的 latest.json,旧的"取第一个有效源"
     * 逻辑会被过期缓存带偏(明明有新版本却报"已是最新")。改为各源取最大,任一镜像
     * 刷新后即可发现新版;再附 5 分钟时间桶的 `?z=` 参数绕开 CDN 缓存。全部失败返回 null。
     */
    suspend fun fetchLatest(): Info? = coroutineScope {
        val z = System.currentTimeMillis() / 300_000   // 5 分钟一个桶
        METADATA_URLS.map { url ->
            async(Dispatchers.IO) { fetchLatestOne("$url?z=$z") }
        }.awaitAll().filterNotNull().maxByOrNull { it.versionCode }
    }

    private fun fetchLatestOne(url: String): Info? = try {
        val body = metaClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()
        }
        if (body.isNullOrBlank()) null
        else {
            val o = JSONObject(body)
            val code = o.optLong("versionCode", 0)
            val name = o.optString("versionName", "")
            val apk = o.optString("apkUrl", "")
            if (code > 0 && name.isNotBlank() && apk.isNotBlank())
                Info(code, name, apk, o.optString("apkHash", ""), o.optString("notes", ""))
            else null
        }
    } catch (_: Exception) {
        null
    }

    /** 下载 APK(镜像链)到 update 目录;成功返回文件,失败抛异常。onProgress 0-100 */
    suspend fun downloadApk(ctx: Context, info: Info, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            // 外部存储不可用时回退内部存储(FileProvider 已声明 files-path)
            val base = ctx.getExternalFilesDir(null) ?: File(ctx.filesDir, "update")
            val dir = File(base, "update").apply { mkdirs() }
            val dst = File(dir, "NcepuJw-${info.versionName}.apk")
            var lastError: Exception? = null
            for (prefix in DOWNLOAD_PREFIXES) {
                try {
                    val req = Request.Builder()
                        .url(prefix + info.apkUrl)
                        .header("User-Agent", "NcepuJw-Updater")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        val total = resp.body?.contentLength() ?: -1L
                        val src = resp.body?.byteStream() ?: throw IllegalStateException("空响应")
                        val tmp = File(dst.absolutePath + ".tmp")
                        // 完整性校验:边写边算 sha256(latest.json 携带,镜像投毒/传输损坏在此拦截)
                        val expected = info.apkHash.trim()
                        val md = if (expected.isNotBlank())
                            java.security.MessageDigest.getInstance("SHA-256") else null
                        src.use { input ->
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                var read: Int
                                var done = 0L
                                var lastPct = -1
                                while (input.read(buf).also { read = it } != -1) {
                                    out.write(buf, 0, read)
                                    md?.update(buf, 0, read)
                                    done += read
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            onProgress(pct.coerceIn(0, 100))
                                        }
                                    }
                                }
                                out.flush()
                            }
                        }
                        // 校验失败删临时文件并抛错;通过则原子化落位
                        if (md != null) {
                            val hex = md.digest().joinToString("") { "%02x".format(it) }
                            if (!hex.equals(expected, ignoreCase = true)) {
                                tmp.delete()
                                throw IllegalStateException("APK 完整性校验失败,已取消安装")
                            }
                        }
                        if (!tmp.renameTo(dst)) {
                            tmp.copyTo(dst, overwrite = true)
                            tmp.delete()
                        }
                        return@withContext dst
                    }
                } catch (e: Exception) {
                    lastError = e
                    File(dst.absolutePath + ".tmp").delete()
                }
            }
            throw lastError ?: IllegalStateException("所有下载源均失败")
        }

    /** 拉起系统安装器 */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
