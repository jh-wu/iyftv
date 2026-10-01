package com.iyftv.app

import android.util.Log
import android.webkit.CookieManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.iyf.IyfSigner
import com.iyftv.app.ui.player.WebPlayerActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONTokener
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which API calls, playlists and segments the website's own player requests, with
 * their headers and cookies, next to the links the app gets for the same episode, so the two
 * can be compared (tag StreamTrace; CI prints it). Diagnostic only: it asserts nothing.
 */
@RunWith(AndroidJUnit4::class)
class StreamTraceDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IyfTvApp

    private fun log(s: String) = s.chunked(3000).forEach { Log.i("StreamTrace", it) }

    private suspend fun appFetch(url: String, headers: Map<String, String>, limit: Int = 1500): String = withContext(Dispatchers.IO) {
        runCatching {
            app.http.newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()).execute().use {
                "HTTP ${it.code} ${it.header("Content-Type")}\n" + (it.body?.string()?.take(limit) ?: "")
            }
        }.getOrElse { "ERR $it" }
    }

    private suspend fun pageFetch(scenario: ActivityScenario<WebPlayerActivity>, url: String): String {
        val key = "f" + url.hashCode().toUInt()
        scenario.onActivity {
            it.runScript(
                "(function(){window.__tf=window.__tf||{};fetch('$url').then(function(r){return r.text().then(function(t){" +
                    "window.__tf['$key']='HTTP '+r.status+'\\n'+t.slice(0,1500)})}).catch(function(e){window.__tf['$key']='ERR '+e})})()",
            )
        }
        repeat(30) {
            delay(500)
            val result = CompletableDeferred<String>()
            scenario.onActivity { it.evalScript("(window.__tf||{})['$key']||''") { v -> result.complete(v) } }
            val v = JSONTokener(result.await()).nextValue() as? String
            if (!v.isNullOrEmpty()) return v
        }
        return "timeout"
    }

    @Test fun traceWebsiteStream(): Unit = runBlocking {
        val hit = app.source.search("沉默的证人", 1).items.first()
        val detail = app.source.detail(hit.key)
        val ep = detail.episodes.first()
        val mine = app.source.stream(detail.key, ep.key)
        log("APP stream ${mine.url}\nalternates ${mine.alternates}\nheaders ${mine.headers}")
        log("APP fetch of its own playlist: " + appFetch(mine.url, mine.headers))

        val trace = mutableListOf<String>()
        WebPlayerActivity.trace = trace
        try {
            val intent = WebPlayerActivity.intent(app, detail.key, ep.key, 0, detail.title, detail.imageUrl, ep.name, null)
            ActivityScenario.launch<WebPlayerActivity>(intent).use { scenario ->
                for (i in 0 until 40) {
                    delay(2_000)
                    if (WebPlayerActivity.lastState?.contains("MAIN") == true) break
                }
                delay(5_000)
                val seen = synchronized(trace) { trace.toList() }
                log("PAGE made ${seen.size} traced requests")
                seen.take(60).forEach { log("PAGE $it") }
                val urls = seen.map { it.lineSequence().first().substringAfter(' ') }
                for (cookieHost in listOf("https://www.iyf.tv", "https://m10.iyf.tv")) {
                    log("COOKIES $cookieHost: ${CookieManager.getInstance().getCookie(cookieHost)}")
                }
                urls.firstOrNull { it.contains("/v3/video/play") }?.let { log("SITE play API answer (fetched by app): " + appFetch(it, mine.headers, 5000)) }
                // Does the app sign a playlist link exactly as the page did?
                val pageKeys = CompletableDeferred<String>()
                scenario.onActivity {
                    it.evalScript("(document.documentElement.outerHTML.match(/\"pConfig\"\\s*:\\s*\\{[^}]*\\}/)||[''])[0]") { v -> pageKeys.complete(v) }
                }
                val keys = IyfSigner.parseKeys(JSONTokener(pageKeys.await()).nextValue() as? String ?: "")
                urls.firstOrNull { it.contains(".m3u8") && it.contains("&vv=") }?.let { u ->
                    val unsigned = u.substringBefore("&vv=")
                    val mine = keys?.let { k -> IyfSigner.signUrl(unsigned, k) }
                    log("SIGN CHECK keys=${keys != null} same=${mine == u}\npage ${u.substringAfter("&vv=")}\napp  ${mine?.substringAfter("&vv=")}")
                }
                val playlists = urls.filter { it.contains(".m3u8") }.distinct().take(3)
                for (u in playlists) {
                    log("PLAYLIST $u\n--- fetched in page: " + pageFetch(scenario, u))
                    log("PLAYLIST $u\n--- fetched by app: " + appFetch(u, mine.headers))
                }
                urls.firstOrNull { Regex("\\.ts(\\?|$)").containsMatchIn(it) }?.let { seg ->
                    log("SEGMENT $seg\n--- fetched by app: " + appFetch(seg, mine.headers, 0))
                }
            }
        } finally {
            WebPlayerActivity.trace = null
        }
    }
}
