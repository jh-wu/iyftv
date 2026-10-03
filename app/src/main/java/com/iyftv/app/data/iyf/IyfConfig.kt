package com.iyftv.app.data.iyf

import com.iyftv.app.data.model.Category
import com.iyftv.app.data.model.FilterGroup
import com.iyftv.app.data.model.ListFilter

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

    fun listQuery(cid: String, page: Int, filter: ListFilter = emptyMap()) =
        "cinema=1&page=$page&size=$PAGE_SIZE&orderby=${SORT_ORDERS[filter[SORT.id]] ?: 0}&desc=1&cid=$cid&isserial=-1&isIndex=-1&isfree=-1" +
            FILTERS.mapNotNull { g -> filter[g.id]?.let { "&${g.id}=${encode(it)}" } }.joinToString("")

    /**
     * How a category is sorted: newest uploads first (list/Search `orderby=0`, the default),
     * or highest score first (`orderby=3`).
     */
    val SORT = FilterGroup("sort", "按上传时间", listOf("按评分"))
    private val SORT_ORDERS = mapOf("按评分" to 3)

    /**
     * The list filters the website offers (its /v3/list/GetSearchCondition), sent to
     * list/Search as `region`, `language` and `year`.
     */
    val FILTERS = listOf(
        FilterGroup("region", "全部地区", listOf("大陆", "香港", "台湾", "日本", "韩国", "欧美", "英国", "泰国", "其它")),
        FilterGroup(
            "language", "全部语言",
            listOf("国语", "粤语", "英语", "韩语", "日语", "西班牙语", "法语", "德语", "意大利语", "泰国语", "其它"),
        ),
        FilterGroup("year", "全部年份", listOf("今年", "去年", "更早", "90年代", "80年代", "怀旧")),
    )

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
