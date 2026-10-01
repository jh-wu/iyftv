package com.iyftv.app.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iyftv.app.data.model.Stream
import okhttp3.OkHttpClient

/** How the app turns a [Stream] into something ExoPlayer can play; shared with the device tests. */
@OptIn(UnstableApi::class)
object StreamPlayer {

    fun create(context: Context, http: OkHttpClient, headers: Map<String, String>): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(http).setDefaultRequestProperties(headers)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .build()
    }

    fun mediaItem(stream: Stream, title: String? = null): MediaItem =
        MediaItem.Builder()
            .setUri(stream.url)
            .apply { if (stream.url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .apply { if (title != null) setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()) }
            .build()

    /**
     * What the server said when it refused a request: status, which URL (with the query
     * values that tie a link to an IP or region), and the start of the reply body.
     */
    fun httpDetails(e: Throwable): String? {
        val http = generateSequence(e) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull() ?: return null
        val uri = http.dataSpec.uri
        val tied = uri.queryParameterNames.filter { it in setOf("vCustomParameter", "vendtime", "lb") }
            .joinToString(" ") { "$it=${uri.getQueryParameter(it)}" }
        val headers = http.headerFields.filterKeys { it != null && it.lowercase() in setOf("server", "content-type", "via", "x-cache", "cf-ray") }
            .entries.joinToString(" ") { "${it.key}: ${it.value.joinToString()}" }
        val body = http.responseBody.decodeToString().replace(Regex("\\s+"), " ").trim().take(300)
        return listOf(
            "HTTP ${http.responseCode} ${http.responseMessage.orEmpty()}".trim(),
            "${uri.host}${uri.path}",
            tied,
            headers,
            body.ifEmpty { "(empty body)" },
        ).filter { it.isNotBlank() }.joinToString("\n")
    }

    /** The error with its whole cause chain, so a TV toast says more than "Source error". */
    fun describe(e: Throwable): String = generateSequence(e) { it.cause }.take(4)
        .joinToString(" ← ") { t ->
            val code = (t as? androidx.media3.common.PlaybackException)?.errorCodeName?.let { "[$it] " }.orEmpty()
            "$code${t.javaClass.simpleName}: ${t.message}"
        }
}
