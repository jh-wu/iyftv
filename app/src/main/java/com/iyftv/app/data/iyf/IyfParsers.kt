package com.iyftv.app.data.iyf

import com.iyftv.app.data.model.Episode
import com.iyftv.app.data.model.VideoDetail
import com.iyftv.app.data.model.VideoSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /** Every object in the tree that looks like a title card, in document order. */
    fun videos(root: JsonElement): List<VideoSummary> =
        objects(root)
            .mapNotNull { o ->
                val key = o.str("key", "mediaKey", "videoKey", "vid") ?: return@mapNotNull null
                val title = o.str("title", "name", "videoName") ?: return@mapNotNull null
                val image = o.str("image", "imgPath", "coverImgUrl", "img", "pic")
                    ?: return@mapNotNull null
                VideoSummary(
                    key = key,
                    title = title,
                    imageUrl = absolute(image),
                    subtitle = o.str("lastName", "updateweekly", "contxt", "regional", "year"),
                )
            }
            .distinctBy { it.key }
            .toList()

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
                info?.str("year", "addTime")?.take(4),
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

    /** The best HLS/MP4 URL in a play response, preferring the first `flvPathList` entry. */
    fun streamUrl(root: JsonElement): String? {
        val preferred = arraysNamed(root, "flvPathList").flatMap { it.asSequence() }
            .flatMap { strings(it) }
            .firstOrNull(::isMediaUrl)
        return preferred ?: strings(root).firstOrNull(::isMediaUrl)
    }

    fun isMediaUrl(s: String) =
        s.startsWith("http") && (s.contains(".m3u8") || s.contains(".mp4"))

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
