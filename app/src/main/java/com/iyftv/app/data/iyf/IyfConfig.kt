package com.iyftv.app.data.iyf

import com.iyftv.app.data.model.Category

/**
 * Hosts, endpoints and catalog ids for iyf.tv.
 *
 * NOTE: these were written without live access to the site (the build
 * environment could not reach it) and must be checked against the browser's
 * network tab. They are kept in one place so fixing them is a one-file change.
 */
object IyfConfig {
    const val WEB_HOST = "https://www.iyf.tv"
    const val API_HOST = "https://m10.iyf.tv"

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    const val PAGE_SIZE = 36

    val categories = listOf(
        Category("0,1,3", "电影"),
        Category("0,1,4", "电视剧"),
        Category("0,1,5", "综艺"),
        Category("0,1,6", "动漫"),
        Category("0,1,7", "纪录片"),
    )

    fun listQuery(cid: String, page: Int) =
        "cinema=1&page=$page&size=$PAGE_SIZE&orderby=0&desc=1&cid=$cid&isserial=-1&isIndex=-1&isfree=-1"

    fun searchQuery(keyword: String, page: Int) =
        "tags=${encode(keyword)}&orderby=4&page=$page&size=$PAGE_SIZE&desc=1&isserial=-1"

    fun detailQuery(videoKey: String) =
        "cinema=1&device=1&player=CkPlayer&tech=HLS&country=HU&lang=cns&v=1&id=$videoKey&region=GL."

    fun playlistQuery(videoKey: String) =
        "cinema=1&vid=$videoKey&lsk=1&taxis=0&cid=0,1,4,133"

    fun playQuery(episodeKey: String) =
        "cinema=1&id=$episodeKey&a=0&lang=none&usersign=1&region=GL.&device=1&isMasterSupport=1"

    const val LIST_PATH = "/api/list/Search"
    const val SEARCH_PATH = "/v3/list/briefsearch"
    const val DETAIL_PATH = "/v3/video/detail"
    const val PLAYLIST_PATH = "/v3/video/languagesplaylist"
    const val PLAY_PATH = "/v3/video/play"

    /** The site's own watch page; used as a fallback to find the stream URL. */
    fun playPageUrl(videoKey: String, episodeKey: String) =
        "$WEB_HOST/play/$videoKey?id=$episodeKey"

    val defaultHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to "$WEB_HOST/",
        "Origin" to WEB_HOST,
    )

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
