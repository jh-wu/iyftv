package com.iyftv.app.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
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

    /** The error with its whole cause chain, so a TV toast says more than "Source error". */
    fun describe(e: Throwable): String = generateSequence(e) { it.cause }.take(4)
        .joinToString(" ← ") { t ->
            val code = (t as? androidx.media3.common.PlaybackException)?.errorCodeName?.let { "[$it] " }.orEmpty()
            "$code${t.javaClass.simpleName}: ${t.message}"
        }
}
