package com.iyftv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.iyf.IyfVideoSource
import com.iyftv.app.data.iyf.WebViewCookieJar
import com.iyftv.app.data.iyf.WebViewKeyFetcher
import com.iyftv.app.data.iyf.WebViewStreamSniffer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the site client on a real Android runtime (emulator in CI) against the
 * live site. The JVM unit tests can't catch Android-only behaviour such as
 * the ICU regex engine.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSiteTest {

    @Test fun browseSearchDetailAndStream() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val http = OkHttpClient.Builder().cookieJar(WebViewCookieJar()).build()
        val source = IyfVideoSource(http, WebViewStreamSniffer(context), WebViewKeyFetcher(context))

        for (category in source.categories()) {
            val page = source.list(category, 1)
            println("DeviceSiteTest ${category.name}: ${page.items.size} items")
            assertTrue("${category.name} is empty", page.items.isNotEmpty())
        }
        assertTrue(source.search("繁花", 1).items.isNotEmpty())

        val tv = source.list(source.categories().first { it.name == "电视剧" }, 1).items.first()
        val detail = source.detail(tv.key)
        assertTrue(detail.episodes.isNotEmpty())
        val stream = source.stream(detail.key, detail.episodes.first().key)
        println("DeviceSiteTest stream: ${stream.url.take(80)}")
        assertTrue(stream.url.contains(".m3u8"))
    }
}
