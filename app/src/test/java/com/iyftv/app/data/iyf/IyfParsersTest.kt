package com.iyftv.app.data.iyf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are trimmed copies of live iyf.tv responses (captured by tools/probe_site.py). */
class IyfParsersTest {

    private val listJson = """
        {"ret":200,"data":{"code":0,"msg":"","info":[{"recordcount":15026,"result":[
          {"atypeName":"电视剧","videoClassID":"0,1,4,152","image":"https://static.iyf.tv/upload/video/a.jpg",
           "key":"xRNzZ3jqQ26","title":"消失的裂痕","lastName":"06集全","year":2026,
           "contxt":"一名母亲在儿子失踪三年后深陷创伤。","lastKey":"FTyi6tzQ1Im"},
          {"image":"//static.iyf.tv/b.jpg","key":"abcdEFGH123","title":"繁花"},
          {"key":"noImage0001","title":"No image"}
        ]}]},"msg":""}
    """.trimIndent()

    private val searchJson = """
        {"ret":200,"data":{"code":0,"msg":"","info":[{"recordcount":7,"result":[
          {"lastName":"全集","contxt":"nnbCytuwQzf","imgPath":"https://static.iyf.tv/upload/video/c.gif",
           "cidMapper":"0,1,8,158|0,1,8,211","title":"雾散繁花","id":48288,"shortDes":"本剧以温情的傲江小镇为故事舞台"}
        ]}]},"msg":""}
    """.trimIndent()

    @Test fun listItems_useKeyAndImage_notSynopsis() {
        val videos = IyfParsers.videos(IyfParsers.parse(listJson))
        assertEquals(listOf("xRNzZ3jqQ26", "abcdEFGH123"), videos.map { it.key })
        assertEquals("06集全", videos[0].subtitle)
        assertEquals("https://static.iyf.tv/b.jpg", videos[1].imageUrl)
        assertEquals(15026, IyfParsers.recordCount(IyfParsers.parse(listJson)))
    }

    @Test fun searchItems_takeKeyFromContxt() {
        val videos = IyfParsers.videos(IyfParsers.parse(searchJson))
        assertEquals(listOf("nnbCytuwQzf"), videos.map { it.key })
        assertEquals("雾散繁花", videos[0].title)
        assertEquals("https://static.iyf.tv/upload/video/c.gif", videos[0].imageUrl)
    }

    @Test fun detail_combinesInfoAndPlaylist() {
        val detail = IyfParsers.parse(
            """{"ret":200,"data":{"code":0,"info":[{"id":48288,"post_Year":"2026","channel":"短剧","videoType":"都市",
               "contxt":"本剧以温情的傲江小镇为故事舞台。","title":"雾散繁花","imgPath":"https://static.iyf.tv/c.gif",
               "key":"nnbCytuwQzf","publisher":{"title":"红豆生南","key":"mBhkXTlQorfMQ3Btppbkc0"},
               "stars":[],"directors":[],"regional":"大陆"}]}}"""
        )
        val playlist = IyfParsers.parse(
            """{"ret":200,"data":{"code":0,"info":[{"pageSize":50,"playList":[
               {"id":1504729,"key":"Er9rjQrnUIA","name":"01"},{"id":1504730,"key":"EPWbgtUTZMA","name":"02"}]}]}}"""
        )
        val d = IyfParsers.detail("nnbCytuwQzf", detail, playlist)
        assertEquals("雾散繁花", d.title)
        assertEquals("本剧以温情的傲江小镇为故事舞台。", d.description)
        assertEquals("都市 · 大陆 · 2026", d.meta)
        assertEquals(listOf("Er9rjQrnUIA", "EPWbgtUTZMA"), d.episodes.map { it.key })
    }

    @Test fun streamUrl_skipsPreRollMp4AndTakesHls() {
        val play = IyfParsers.parse(
            """{"ret":200,"data":{"code":0,"info":[{"flvPathList":[
               {"isHls":false,"result":"https://s1-a1.global-cdn.me/vod/086F8B75362-06022.mp4","duration":20},
               {"isHls":true,"result":"https://sss111-e1.pipecdn.vip/x/chunklist.m3u8?vendtime=1&vhash=a","bitrate":1080}
            ],"key":"nnbCytuwQzf"}]}}"""
        )
        assertEquals("https://sss111-e1.pipecdn.vip/x/chunklist.m3u8?vendtime=1&vhash=a", IyfParsers.streamUrl(play))
    }

    @Test fun streamUrl_nullWhenOnlyAds() {
        val play = IyfParsers.parse("""{"data":{"info":[{"flvPathList":[{"isHls":false,"result":"https://ad/x.mp4"}]}]}}""")
        assertNull(IyfParsers.streamUrl(play))
    }
}
