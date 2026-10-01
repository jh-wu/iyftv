package com.iyftv.app.data.iyf

import com.iyftv.app.data.model.Category

/**
 * Hosts, endpoints and catalog ids for iyf.tv, as used by its web client.
 * `tools/probe_site.py` (run in CI) checks them against the live site.
 */
object IyfConfig {
    const val WEB_HOST = "https://www.iyf.tv"

    /**
     * Domains the site is served under. The web client builds its API host as
     * `m10.{current domain}`, so a mirror is tried when the main one can't be reached.
     */
    val SITE_DOMAINS = listOf("iyf.tv", "yfsp.tv", "ifsp.tv")

    fun webHost(domain: String) = "https://www.$domain"
    fun apiHost(domain: String) = "https://m10.$domain"

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
        Category("0,1,8", "短剧"),
    )

    fun listQuery(cid: String, page: Int) =
        "cinema=1&page=$page&size=$PAGE_SIZE&orderby=0&desc=1&cid=$cid&isserial=-1&isIndex=-1&isfree=-1"

    fun searchQuery(keyword: String, page: Int) =
        "tags=${encode(keyword)}&orderby=4&page=$page&size=$PAGE_SIZE&desc=1&isserial=-1"

    fun detailQuery(videoKey: String) =
        "cinema=1&device=1&player=CkPlayer&tech=HLS&country=HU&lang=cns&v=1&id=$videoKey&region=GL."

    fun playlistQuery(videoKey: String) =
        "cinema=1&vid=$videoKey&lsk=1&taxis=0&cid=0,1,4,133"

    /** [region] is what the website sends: the visitor's region code from the homepage, else "GL.". */
    fun playQuery(episodeKey: String, region: String = GLOBAL_REGION) =
        "cinema=1&id=$episodeKey&a=0&lang=none&usersign=1&region=$region&device=1&isMasterSupport=1"

    const val GLOBAL_REGION = "GL."

    /** Domains the video servers answer on; the same path works on each. */
    val VIDEO_DOMAINS = listOf("latensiorb.vip", "globenete.vip", "pipecdn.vip")

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
