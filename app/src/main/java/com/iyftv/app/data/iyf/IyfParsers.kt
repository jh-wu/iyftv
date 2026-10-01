package com.iyftv.app.data.iyf

import com.iyftv.app.data.model.Episode
import com.iyftv.app.data.model.VideoDetail
import com.iyftv.app.data.model.VideoSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns iyf.tv API responses into app models.
 *
 * The parsers look for objects by the fields they carry rather than by fixed
 * paths, so small changes in response nesting don't break the app.
 */
object IyfParsers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): JsonElement = json.parseToJsonElement(body)

    /**
     * Every object in the tree that looks like a title card, in document order.
     * Category lists carry the title's key in `key`; search results carry it in
     * `contxt` (which elsewhere holds the synopsis), so that one is only taken
     * when it looks like a key.
     */
    fun videos(root: JsonElement): List<VideoSummary> =
        objects(root)
            .mapNotNull { o ->
                val key = o.str("key", "mediaKey")
                    ?: o.str("contxt")?.takeIf { KEY_PATTERN.matches(it) }
                    ?: return@mapNotNull null
                val title = o.str("title", "name", "videoName") ?: return@mapNotNull null
                val image = o.str("image", "imgPath", "coverImgUrl", "img", "pic")
                    ?: return@mapNotNull null
                VideoSummary(
                    key = key,
                    title = title,
                    imageUrl = absolute(image),
                    subtitle = o.str("lastName", "updateweekly", "regional", "year"),
                )
            }
            .distinctBy { it.key }
            .toList()

    /** The API answers a bad `vv` with HTTP 200 and `data.code == 1` ("用户签名错误"). */
    fun isSignatureError(root: JsonElement): Boolean {
        val data = (root as? JsonObject)?.get("data") as? JsonObject ?: return false
        return (data["code"] as? JsonPrimitive)?.intOrNull == 1
    }

    /** The site's own error message when `data.code` is non-zero. */
    fun errorMessage(root: JsonElement): String? {
        val data = (root as? JsonObject)?.get("data") as? JsonObject ?: return null
        val code = (data["code"] as? JsonPrimitive)?.intOrNull ?: return null
        if (code == 0) return null
        return (data["msg"] as? JsonPrimitive)?.contentOrNull?.ifBlank { null } ?: "error code $code"
    }

    /** Total result count, when the response reports one. */
    fun recordCount(root: JsonElement): Int? =
        objects(root).firstNotNullOfOrNull { o ->
            (o["recordcount"] ?: o["recordCount"] ?: o["total"])?.let { (it as? JsonPrimitive)?.intOrNull }
        }

    fun detail(videoKey: String, detailRoot: JsonElement, playlistRoot: JsonElement?): VideoDetail {
        val info = objects(detailRoot).firstOrNull { it.str("title") != null && it.str("key", "mediaKey") == videoKey }
            ?: objects(detailRoot).firstOrNull { it.str("title") != null }
        val episodes = playlistRoot?.let(::episodes).orEmpty()
            .ifEmpty { episodes(detailRoot) }
        return VideoDetail(
            key = videoKey,
            title = info?.str("title") ?: "",
            imageUrl = info?.str("imgPath", "image", "coverImgUrl")?.let(::absolute),
            description = info?.str("contxt", "introduce", "description", "desc"),
            meta = listOfNotNull(
                info?.str("videoType", "cidMapper"),
                info?.str("regional", "area"),
                info?.str("post_Year", "year"),
                info?.str("directors", "director")?.let { "导演 $it" },
                info?.str("starring", "actor")?.let { "主演 $it" },
            ).joinToString(" · ").ifBlank { null },
            episodes = episodes,
        )
    }

    /** Episodes come as a `playList` array of objects with a key and a name. */
    fun episodes(root: JsonElement): List<Episode> =
        arraysNamed(root, "playList", "playlist", "episodes")
            .flatMap { arr -> arr.mapNotNull { it as? JsonObject } }
            .mapNotNull { o ->
                val key = o.str("key", "episodeKey", "id") ?: return@mapNotNull null
                Episode(key, o.str("name", "title", "episodeName") ?: key)
            }
            .distinctBy { it.key }
            .toList()

    /**
     * The HLS URL in a play response. `flvPathList` also holds a short MP4 pre-roll
     * ad (`isHls: false`), so only HLS entries are taken.
     */
    /**
     * The visitor's region code the homepage embeds (`"switch-region":[{"ipCountry":"AU","regionCode":"AU",..}]`).
     * The website passes it to the play API, which picks stream servers by it.
     */
    fun siteRegion(html: String): String? =
        Regex("\"switch-region\"\\s*:\\s*\\[\\s*\\{[^\\}]*?\"regionCode\"\\s*:\\s*\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun streamUrl(root: JsonElement): String? = streamUrls(root).firstOrNull()

    /** Every HLS URL in a play response, best first, for falling back when one fails. */
    fun streamUrls(root: JsonElement): List<String> {
        val hlsEntries = objects(root).filter { (it["isHls"] as? JsonPrimitive)?.booleanOrNull == true }
        return (hlsEntries.mapNotNull { it.str("result") }.filter(::isHlsUrl) + strings(root).filter(::isHlsUrl))
            .distinct()
            .toList()
    }

    /**
     * [urls] followed by the same links on the video service's other domains. In
     * Australia the play API hands the app a globenete.vip link that Cloudflare
     * refuses, while browsers there play the same file from latensiorb.vip.
     */
    fun withOtherVideoHosts(urls: List<String>): List<String> {
        val swapped = urls.flatMap { url ->
            val m = VIDEO_HOST.find(url) ?: return@flatMap emptyList()
            IyfConfig.VIDEO_DOMAINS.filter { it != m.groupValues[2] }
                .map { url.replaceRange(m.groups[2]!!.range, it) }
        }
        return (urls + swapped).distinct()
    }

    private val VIDEO_HOST = Regex("^https?://([A-Za-z0-9]+-e\\d+)\\.([a-z0-9]+\\.vip)/")

    fun isHlsUrl(s: String) = s.startsWith("http") && s.contains(".m3u8")

    private val KEY_PATTERN = Regex("[A-Za-z0-9_-]{8,16}")

    private fun absolute(url: String) = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> IyfConfig.WEB_HOST + url
        else -> url
    }

    private fun JsonObject.str(vararg names: String): String? =
        names.firstNotNullOfOrNull { n ->
            (this[n] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        }

    private fun objects(e: JsonElement): Sequence<JsonObject> = sequence {
        when (e) {
            is JsonObject -> { yield(e); e.values.forEach { yieldAll(objects(it)) } }
            is JsonArray -> e.forEach { yieldAll(objects(it)) }
            else -> Unit
        }
    }

    private fun arraysNamed(e: JsonElement, vararg names: String): Sequence<JsonArray> =
        objects(e).flatMap { o -> names.asSequence().mapNotNull { o[it] as? JsonArray } }

    private fun strings(e: JsonElement): Sequence<String> = sequence {
        when (e) {
            is JsonPrimitive -> if (e.isString) yield(e.content)
            is JsonObject -> e.values.forEach { yieldAll(strings(it)) }
            is JsonArray -> e.forEach { yieldAll(strings(it)) }
        }
    }
}
