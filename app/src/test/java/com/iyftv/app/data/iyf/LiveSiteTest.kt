package com.iyftv.app.data.iyf

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the real client against iyf.tv. Skipped unless IYF_LIVE=1, so normal
 * builds never depend on the site; CI's probe job turns it on.
 */
class LiveSiteTest {

    private val source = IyfVideoSource(OkHttpClient(), sniffer = null)

    @Test fun browseSearchDetailAndStream() = runBlocking {
        assumeTrue(System.getenv("IYF_LIVE") == "1")

        for (category in source.categories()) {
            val page = source.list(category, 1)
            println("${category.name}: ${page.items.size} items, first=${page.items.firstOrNull()?.title}")
            assertTrue("${category.name} is empty", page.items.isNotEmpty())
        }

        val results = source.search("繁花", 1)
        println("search: ${results.items.map { it.title }}")
        assertTrue("search is empty", results.items.isNotEmpty())

        val tv = source.list(source.categories().first { it.name == "电视剧" }, 1).items.first()
        val detail = source.detail(tv.key)
        println("detail: ${detail.title} / ${detail.meta} / ${detail.episodes.size} episodes")
        assertTrue("no episodes for ${tv.key}", detail.episodes.isNotEmpty())

        val stream = source.stream(detail.key, detail.episodes.first().key)
        println("stream: ${stream.url.take(80)}")
        assertTrue(stream.url.contains(".m3u8"))
    }
}
