package com.iyftv.app.ui.player

import android.content.Context
import android.util.Log
import android.webkit.WebSettings
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.cronet.CronetUtil
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iyftv.app.data.model.Stream
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.concurrent.Executors

/** How the app turns a [Stream] into something ExoPlayer can play; shared with the device tests. */
@OptIn(UnstableApi::class)
object StreamPlayer {

    /**
     * Ways to request a video server: a network stack plus request headers. Some servers
     * sit behind Cloudflare, which refuses (HTTP 403) requests that don't look like a
     * browser, judging by the TLS handshake as well as the headers. Cronet is Chrome's own
     * network stack, so it is tried first; OkHttp variants follow. On a 403 the player
     * moves to the next one, and the one that works is remembered for the session.
     */
    data class Profile(val name: String, val headers: Map<String, String>, val cronet: Boolean = false)

    class HeaderProfiles(val all: List<Profile>) {
        var index = remembered.coerceIn(all.indices)
        val name get() = all[index].name
        val current get() = all[index]
        fun next(): Boolean = (index + 1 < all.size).also { if (it) index++ }
        fun reset() { index = 0 }
        fun keep() { remembered = index }

        companion object {
            private var remembered = 0
        }
    }

    private var cronet: CronetEngine? = null
    private var cronetTried = false
    private val cronetExecutor = Executors.newSingleThreadExecutor()

    /** Chrome's network stack from Google Play services, or null when the device lacks it. */
    fun cronetEngine(context: Context): CronetEngine? = synchronized(this) {
        if (!cronetTried) {
            cronetTried = true
            cronet = runCatching { CronetUtil.buildCronetEngine(context.applicationContext) }
                .onFailure { Log.w("StreamPlayer", "Cronet unavailable", it) }
                .getOrNull()
        }
        cronet
    }

    fun headerProfiles(context: Context, siteHeaders: Map<String, String>): HeaderProfiles {
        val referer = siteHeaders.filterKeys { it == "Referer" || it == "Origin" }
        val webViewUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        val hasCronet = cronetEngine(context) != null
        return HeaderProfiles(
            listOfNotNull(
                webViewUa?.takeIf { hasCronet }?.let { Profile("chrome-net webview", referer + ("User-Agent" to it), cronet = true) },
                Profile("chrome-net desktop", siteHeaders, cronet = true).takeIf { hasCronet },
                Profile("desktop", siteHeaders),
                webViewUa?.let { Profile("webview", referer + ("User-Agent" to it)) },
                Profile("app", mapOf("User-Agent" to "iyfTV (Android TV)", "Referer" to (siteHeaders["Referer"] ?: ""))),
                Profile("bare", emptyMap()),
            ),
        )
    }

    fun create(context: Context, http: OkHttpClient, headers: Map<String, String>): ExoPlayer =
        create(context, http, HeaderProfiles(listOf(Profile("default", headers))))

    fun create(context: Context, http: OkHttpClient, profiles: HeaderProfiles): ExoPlayer {
        // The profile is read per request, so switching it applies to the next load.
        val dataSource = DataSource.Factory {
            val profile = profiles.current
            val engine = if (profile.cronet) cronetEngine(context) else null
            if (engine != null) {
                CronetDataSource.Factory(engine, cronetExecutor)
                    .setUserAgent(profile.headers["User-Agent"])
                    .setDefaultRequestProperties(profile.headers - "User-Agent")
                    .createDataSource()
            } else {
                OkHttpDataSource.Factory(http).setDefaultRequestProperties(profile.headers).createDataSource()
            }
        }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .build()
    }

    fun httpStatus(e: Throwable): Int? = generateSequence(e) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

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
        val html = http.responseBody.decodeToString()
        // Cloudflare's block page names the reason in its title and an "Error 10xx" code.
        val title = Regex("<title>([^<]*)</title>").find(html)?.groupValues?.get(1)?.trim()
        val cfCode = Regex("(?:Error|error code:?)\\s*(1\\d{3})").find(html)?.groupValues?.get(1)
        val body = listOfNotNull(title, cfCode?.let { "Cloudflare error $it" }).joinToString(" · ")
            .ifEmpty { html.replace(Regex("\\s+"), " ").trim().take(300) }
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
