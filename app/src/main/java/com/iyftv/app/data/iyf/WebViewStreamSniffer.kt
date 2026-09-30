package com.iyftv.app.data.iyf

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Loads the watch page in an off-screen WebView and returns the first HLS
 * URL the page's own player requests (MP4s there are pre-roll ads). This keeps playback working even if the
 * signed play API changes, because the site's JavaScript does the signing.
 */
class WebViewStreamSniffer(
    private val context: Context,
    private val timeoutMillis: Long = 20_000,
) : StreamSniffer {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun sniff(pageUrl: String): String? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { cont ->
                    val view = WebView(context.applicationContext)
                    webView = view
                    view.settings.javaScriptEnabled = true
                    view.settings.domStorageEnabled = true
                    view.settings.mediaPlaybackRequiresUserGesture = false
                    view.settings.userAgentString = IyfConfig.USER_AGENT
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            v: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            if (IyfParsers.isHlsUrl(url)) {
                                v.post { if (cont.isActive) cont.resume(url) }
                            }
                            return null
                        }
                    }
                    view.loadUrl(pageUrl)
                }
            }
        } finally {
            webView?.apply { stopLoading(); destroy() }
        }
    }
}
