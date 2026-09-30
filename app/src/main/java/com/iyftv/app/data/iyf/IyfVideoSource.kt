package com.iyftv.app.data.iyf

import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.model.Category
import com.iyftv.app.data.model.Page
import com.iyftv.app.data.model.Stream
import com.iyftv.app.data.model.VideoDetail
import com.iyftv.app.data.model.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class IyfVideoSource(
    private val http: OkHttpClient,
    private val sniffer: StreamSniffer?,
) : VideoSource {

    private val keysLock = Mutex()
    private var keys: IyfKeys? = null

    override suspend fun categories(): List<Category> = IyfConfig.categories

    override suspend fun list(category: Category, page: Int): Page<VideoSummary> {
        val root = api(IyfConfig.LIST_PATH, IyfConfig.listQuery(category.id, page))
        return toPage(root, page)
    }

    override suspend fun search(query: String, page: Int): Page<VideoSummary> {
        val root = api(IyfConfig.SEARCH_PATH, IyfConfig.searchQuery(query, page))
        return toPage(root, page)
    }

    override suspend fun detail(videoKey: String): VideoDetail {
        val detail = api(IyfConfig.DETAIL_PATH, IyfConfig.detailQuery(videoKey))
        val playlist = runCatching { api(IyfConfig.PLAYLIST_PATH, IyfConfig.playlistQuery(videoKey)) }.getOrNull()
        return IyfParsers.detail(videoKey, detail, playlist)
    }

    override suspend fun stream(videoKey: String, episodeKey: String): Stream {
        val fromApi = runCatching {
            IyfParsers.streamUrl(api(IyfConfig.PLAY_PATH, IyfConfig.playQuery(episodeKey)))
        }.getOrNull()
        val url = fromApi
            ?: sniffer?.sniff(IyfConfig.playPageUrl(videoKey, episodeKey))
            ?: throw IOException("No stream found for $videoKey / $episodeKey")
        return Stream(url, IyfConfig.defaultHeaders)
    }

    private fun toPage(root: JsonElement, page: Int): Page<VideoSummary> {
        val items = IyfParsers.videos(root)
        val total = IyfParsers.recordCount(root)
        val hasMore = if (total != null) page * IyfConfig.PAGE_SIZE < total else items.size >= IyfConfig.PAGE_SIZE
        return Page(items, hasMore)
    }

    /** Calls the API; if the site rejects the signature (its keys rotate), re-reads them once. */
    private suspend fun api(path: String, query: String): JsonElement {
        repeat(2) { attempt ->
            val k = keys(refresh = attempt > 0)
            val root = IyfParsers.parse(get("${IyfConfig.API_HOST}$path?${IyfSigner.sign(query, k)}"))
            if (!IyfParsers.isSignatureError(root)) return root
        }
        throw IOException("iyf.tv rejected the request signature")
    }

    private suspend fun keys(refresh: Boolean): IyfKeys = keysLock.withLock {
        keys?.takeUnless { refresh }
            ?: (IyfSigner.parseKeys(get("${IyfConfig.WEB_HOST}/"))
                ?: throw IOException("Could not find API keys on the iyf.tv homepage"))
                .also { keys = it }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            IyfConfig.defaultHeaders.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            resp.body?.string() ?: throw IOException("Empty body for $url")
        }
    }
}
