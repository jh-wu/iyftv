package com.iyftv.app.data.iyf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IyfParsersTest {

    private val listJson = """
        {"ret":200,"data":{"info":[{"recordcount":80,"result":[
          {"key":"abc","title":"长相思","image":"//img.example/a.jpg","lastName":"第39集"},
          {"key":"def","title":"繁花","image":"https://img.example/b.jpg"},
          {"key":"abc","title":"长相思","image":"//img.example/a.jpg"},
          {"key":"nope","title":"No image"}
        ]}]}}
    """.trimIndent()

    @Test fun videos_areFoundByShapeAndDeduplicated() {
        val videos = IyfParsers.videos(IyfParsers.parse(listJson))
        assertEquals(listOf("abc", "def"), videos.map { it.key })
        assertEquals("https://img.example/a.jpg", videos[0].imageUrl)
        assertEquals("第39集", videos[0].subtitle)
    }

    @Test fun recordCount_isRead() {
        assertEquals(80, IyfParsers.recordCount(IyfParsers.parse(listJson)))
    }

    @Test fun detail_combinesInfoAndPlaylist() {
        val detail = IyfParsers.parse(
            """{"data":{"info":[{"key":"abc","title":"长相思","imgPath":"https://i/a.jpg","contxt":"简介","regional":"大陆","year":"2024"}]}}"""
        )
        val playlist = IyfParsers.parse(
            """{"data":{"info":[{"playList":[{"key":"e1","name":"第1集"},{"key":"e2","name":"第2集"}]}]}}"""
        )
        val d = IyfParsers.detail("abc", detail, playlist)
        assertEquals("长相思", d.title)
        assertEquals("简介", d.description)
        assertEquals(listOf("e1", "e2"), d.episodes.map { it.key })
        assertEquals("大陆 · 2024", d.meta)
    }

    @Test fun streamUrl_prefersFlvPathList() {
        val play = IyfParsers.parse(
            """{"data":{"info":[{"poster":"https://x/p.m3u8.jpg","flvPathList":[{"isHls":true,"result":"https://cdn/v/index.m3u8?t=1"}]}]}}"""
        )
        assertEquals("https://cdn/v/index.m3u8?t=1", IyfParsers.streamUrl(play))
    }

    @Test fun streamUrl_nullWhenAbsent() {
        assertNull(IyfParsers.streamUrl(IyfParsers.parse("""{"data":{"info":[]}}""")))
    }
}
