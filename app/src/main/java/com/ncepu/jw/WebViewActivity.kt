package com.ncepu.jw

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ncepu.jw.data.JwClient
import com.ncepu.jw.ui.theme.NcepuTheme

/**
 * 内嵌教务网页(评教等)。启动前需设置 [webSession],把 App 会话 cookie 同步给 WebView。
 * SSO 模式([EXTRA_SSO]):打开统一身份认证登录链路,成功落地教务主界面后
 * 把 WebView 里的教务会话 cookie 写入 [ssoCookies] 并返回 RESULT_OK。
 */
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SSO = "sso"

        /** App 会话 cookie(name=value; ...),由宿主在启动前赋值 */
        var webSession: String? = null

        /** SSO 登录成功后回传的教务会话 cookie,宿主读取后清空 */
        var ssoCookies: String? = null

        /** 校外模式:URL 已是 myvpn 代理形式,会话 cookie 需种到 myvpn 域 */
        var webvpnMode: Boolean = false
    }

    private var progress by mutableStateOf(0f)
    private var pageTitle by mutableStateOf("教务网页")
    private var canGoBack by mutableStateOf(false)
    private var webView: WebView? = null
    private var ssoDone = false

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sso = intent.getBooleanExtra(EXTRA_SSO, false)
        val url = if (sso) "${JwClient.DEFAULT_BASE}/Logon.do?method=logonByHbdldx"
        else intent.getStringExtra(EXTRA_URL) ?: JwClient.DEFAULT_BASE
        pageTitle = if (sso) "统一身份认证登录" else intent.getStringExtra(EXTRA_TITLE) ?: "教务网页"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (!sso) {
            webSession?.let { session ->
                val dom = if (webvpnMode) com.ncepu.jw.data.Webvpn.MYVPN else JwClient.DEFAULT_BASE
                for (pair in session.split("; ")) {
                    cookieManager.setCookie(dom, pair)
                }
            }
        } else {
            // 统一认证壳页 window.onload 依赖全新 COOKIE_INFO(缺失/陈旧时 JS 抛错 → iframe 不加载 → 白屏);
            // 且 Android UA 会加载移动版登录页。清空 cookie + 桌面 UA,走与协议直登同构的 login-normal.html
            cookieManager.removeAllCookies(null)
        }
        cookieManager.flush()

        setContent {
            NcepuTheme {
                BackHandler(enabled = canGoBack) { webView?.goBack() }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(pageTitle) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (canGoBack) webView?.goBack() else finish()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        if (progress in 0.01f..0.99f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AndroidWebView(
                            url = url,
                            onProgress = { progress = it },
                            onTitle = { if (it.isNotBlank()) pageTitle = it },
                            onCanGoBack = { canGoBack = it },
                            onCreated = { webView = it },
                            onPageDone = if (sso) {
                                { finishedUrl -> if (ssoDone || checkSsoSuccess(finishedUrl)) finish() }
                            } else null,
                        )
                    }
                }
            }
        }
    }

    /** 统一认证链路落地教务主界面 = 登录成功:抓取教务会话 cookie */
    private fun checkSsoSuccess(finishedUrl: String): Boolean {
        if (!finishedUrl.contains("jwxt.ncepu.edu.cn")) return false
        if (finishedUrl.contains("Logon.do")) return false  // 登录链路中间态
        val cookies = CookieManager.getInstance().getCookie("https://jwxt.ncepu.edu.cn") ?: return false
        if (!cookies.split(";").any { it.trim().startsWith("JSESSIONID=") }) return false
        ssoCookies = cookies
        setResult(RESULT_OK)
        ssoDone = true
        return true
    }

    @Composable
    private fun AndroidWebView(
        url: String,
        onProgress: (Float) -> Unit,
        onTitle: (String) -> Unit,
        onCanGoBack: (Boolean) -> Unit,
        onCreated: (WebView) -> Unit,
        onPageDone: ((String) -> Unit)? = null,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    if (intent.getBooleanExtra(EXTRA_SSO, false)) {
                        settings.userAgentString = (
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    }
                    // 统一认证页是 https 壳 + iframe 加载登录表单:必须允许混合内容与第三方 cookie
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            // 统一认证回跳用的是教务别名域(公网不可解析),重写到正式域名
                            val u = request.url
                            if (u.host == "jwxt.hcc.edu.cn") {
                                val fixed = u.buildUpon().authority("jwxt.ncepu.edu.cn").build()
                                view.loadUrl(fixed.toString())
                                return true
                            }
                            return false  // 其余全部在内部打开
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            if (url.contains("jwxt.hcc.edu.cn")) {
                                view.loadUrl(url.replace("jwxt.hcc.edu.cn", "jwxt.ncepu.edu.cn"))
                                return true
                            }
                            return false
                        }

                        // 校园服务器证书链不完整时放行(仅限学校域名)
                        override fun onReceivedSslError(
                            view: WebView,
                            handler: android.webkit.SslErrorHandler,
                            error: android.net.http.SslError,
                        ) {
                            if (error.url?.contains("ncepu.edu.cn") == true) handler.proceed()
                            else handler.cancel()
                        }

                        override fun onPageFinished(view: WebView, title: String?) {
                            onTitle(view.title ?: "")
                            onCanGoBack(view.canGoBack())
                            onPageDone?.invoke(view.url ?: "")
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            onProgress(newProgress / 100f)
                        }
                    }
                    loadUrl(url)
                    onCreated(this)
                }
            },
        )
    }
}
