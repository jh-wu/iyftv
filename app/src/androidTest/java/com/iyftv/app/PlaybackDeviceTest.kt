package com.iyftv.app

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.model.Stream
import com.iyftv.app.ui.player.StreamPlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Actually plays live streams with the app's player setup, so playback errors show up in CI. */
@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp

    @Test fun playsSearchedSeries() = runBlocking {
        val hit = app.source.search("沉默的证人", 1).items.first()
        playFirstEpisode(hit.key, "search 沉默的证人 → ${hit.title}")
    }

    @Test fun playsFirstTvSeries() = runBlocking {
        val tv = app.source.list(app.source.categories().first { it.name == "电视剧" }, 1).items.first()
        playFirstEpisode(tv.key, "电视剧 → ${tv.title}")
    }

    private suspend fun playFirstEpisode(videoKey: String, label: String) {
        val detail = app.source.detail(videoKey)
        val episode = detail.episodes.first()
        val stream = app.source.stream(detail.key, episode.key)
        val problem = play(stream)
        assertNull("$label / ${episode.name} (${episode.key}) ${stream.url}\n$problem", problem)
    }

    /** Null once playback has run for a few seconds, otherwise what went wrong. */
    private fun play(stream: Stream): String? {
        var player: ExoPlayer? = null
        var error: PlaybackException? = null
        instrumentation.runOnMainSync {
            player = StreamPlayer.create(app, app.http, stream.headers).apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) { error = e }
                })
                volume = 0f
                setMediaItem(StreamPlayer.mediaItem(stream))
                prepare()
                playWhenReady = true
            }
        }
        try {
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                var position = 0L
                var state = 0
                instrumentation.runOnMainSync { position = player!!.currentPosition; state = player!!.playbackState }
                error?.let { return listOfNotNull(StreamPlayer.describe(it), StreamPlayer.httpDetails(it)).joinToString("\n") }
                if (position > 3_000) return null
                if (state == Player.STATE_ENDED) return "ended at $position ms"
                Thread.sleep(500)
            }
            var state = 0
            instrumentation.runOnMainSync { state = player!!.playbackState }
            return "no progress after 60s (state $state)"
        } finally {
            instrumentation.runOnMainSync { player?.release() }
        }
    }
}
