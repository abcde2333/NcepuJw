package com.ncepu.jw

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
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
 */
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"

        /** App 会话 cookie(name=value; ...),由宿主在启动前赋值 */
        var webSession: String? = null
    }

    private var progress by mutableStateOf(0f)
    private var pageTitle by mutableStateOf("教务网页")
    private var canGoBack by mutableStateOf(false)
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: JwClient.DEFAULT_BASE
        pageTitle = intent.getStringExtra(EXTRA_TITLE) ?: "教务网页"

        // 同步会话 cookie
        webSession?.let { session ->
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                for (pair in session.split("; ")) {
                    setCookie(JwClient.DEFAULT_BASE, pair)
                }
                flush()
            }
        }

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
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AndroidWebView(
        url: String,
        onProgress: (Float) -> Unit,
        onTitle: (String) -> Unit,
        onCanGoBack: (Boolean) -> Unit,
        onCreated: (WebView) -> Unit,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = false  // 全部在内部打开

                        override fun onPageFinished(view: WebView, title: String?) {
                            onTitle(view.title ?: "")
                            onCanGoBack(view.canGoBack())
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
