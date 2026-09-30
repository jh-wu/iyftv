package com.iyftv.app.data.iyf

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Loads the homepage in an off-screen WebView (which passes browser checks
 * that a plain HTTP client may not) and reads `window.pConfig` from it.
 * Cookies the page sets are shared with OkHttp through [WebViewCookieJar].
 */
class WebViewKeyFetcher(
    private val context: Context,
    private val timeoutMillis: Long = 30_000,
) : BrowserKeyFetcher {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun fetch(pageUrl: String): IyfKeys? = withContext(Dispatchers.Main) {
        val view = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = IyfConfig.USER_AGENT
            webViewClient = WebViewClient()
            loadUrl(pageUrl)
        }
        try {
            withTimeoutOrNull(timeoutMillis) {
                var keys: IyfKeys? = null
                while (keys == null) {
                    delay(1_000)
                    keys = IyfSigner.parseKeys("\"pConfig\":" + view.eval("JSON.stringify(window.pConfig || null)"))
                }
                keys
            }
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    private suspend fun WebView.eval(js: String): String = suspendCancellableCoroutine { cont ->
        evaluateJavascript(js) { raw ->
            // evaluateJavascript returns a JSON-encoded string; unwrap it.
            val text = runCatching { kotlinx.serialization.json.Json.decodeFromString<String>(raw) }.getOrDefault("")
            if (cont.isActive) cont.resume(text)
        }
    }
}
