package com.iyftv.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.iyftv.app.IyfTvApp
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Stream
import com.iyftv.app.data.model.VideoDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen playback of one title. Resumes from the saved position, saves
 * progress while playing, and moves on to the next episode when one ends.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val app get() = application as IyfTvApp
    private lateinit var playerView: PlayerView
    private lateinit var errorView: TextView
    private var player: ExoPlayer? = null
    private var detail: VideoDetail? = null
    private var episodeIndex = 0
    private var saveJob: Job? = null
    private var currentStream: Stream? = null
    private val fallbackUrls = ArrayDeque<String>()
    private var refetched = false
    private var profiles: StreamPlayer.HeaderProfiles? = null
    private val attempts = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
        }
        errorView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(0xE0000000.toInt())
            setPadding(48, 32, 48, 32)
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(errorView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        })

        val videoKey = intent.getStringExtra(EXTRA_VIDEO) ?: return finish()
        val requestedEpisode = intent.getStringExtra(EXTRA_EPISODE)

        lifecycleScope.launch {
            try {
                val d = app.source.detail(videoKey)
                if (d.episodes.isEmpty()) error("没有可播放的剧集")
                detail = d
                val record = app.history.get(videoKey)
                val episodeKey = requestedEpisode ?: record?.episodeKey
                episodeIndex = d.episodes.indexOfFirst { it.key == episodeKey }.coerceAtLeast(0)
                val start = record
                    ?.takeIf { it.episodeKey == d.episodes[episodeIndex].key && !it.isFinished }
                    ?.positionMs ?: 0L
                playEpisode(episodeIndex, start)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private suspend fun playEpisode(index: Int, startMs: Long, retry: Boolean = false) {
        val d = detail ?: return
        val ep = d.episodes[index]
        episodeIndex = index
        val stream = app.source.stream(d.key, ep.key)
        currentStream = stream
        fallbackUrls.clear()
        fallbackUrls.addAll(stream.alternates)
        if (!retry) {
            refetched = false
            attempts.clear()
        }

        // Episodes of one title share headers, so the player is built once.
        val headerProfiles = profiles ?: StreamPlayer.headerProfiles(this, stream.headers).also { profiles = it }
        val p = player ?: StreamPlayer.create(this, app.http, headerProfiles)
            .also { newPlayer ->
                player = newPlayer
                playerView.player = newPlayer
                newPlayer.addListener(listener)
            }

        errorView.visibility = View.GONE
        p.setMediaItem(StreamPlayer.mediaItem(stream, "${d.title} ${ep.name}"), startMs)
        p.prepare()
        p.playWhenReady = true
        startSaving()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) profiles?.keep()
            if (state == Player.STATE_ENDED) {
                save()
                val d = detail ?: return
                if (episodeIndex + 1 < d.episodes.size) {
                    lifecycleScope.launch { runCatching { playEpisode(episodeIndex + 1, 0) }.onFailure(::fail) }
                } else {
                    finish()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w("PlayerActivity", "playback error ${StreamPlayer.describe(error)}")
            val p = player
            val stream = currentStream
            val position = p?.currentPosition ?: 0L
            // Network and stream-format errors: try the episode's other URLs, then a freshly
            // requested one (stream links are signed and short-lived), before giving up.
            val headerProfiles = profiles
            val status = StreamPlayer.httpStatus(error)
            attempts += "${Uri.parse(p?.currentMediaItem?.localConfiguration?.uri?.toString() ?: "").host} ${headerProfiles?.name}: ${status ?: error.errorCodeName}"
            if (p != null && stream != null && headerProfiles != null && error.errorCode in 2000..3999) {
                // Refused (403): same link, different request headers.
                if (status == 403 && headerProfiles.next()) {
                    p.prepare()
                    return
                }
                val next = fallbackUrls.removeFirstOrNull()
                if (next != null) {
                    headerProfiles.reset()
                    p.setMediaItem(StreamPlayer.mediaItem(stream.copy(url = next)), position)
                    p.prepare()
                    return
                }
                if (!refetched) {
                    refetched = true
                    headerProfiles.reset()
                    lifecycleScope.launch { runCatching { playEpisode(episodeIndex, position, retry = true) }.onFailure(::fail) }
                    return
                }
            }
            fail(error)
        }
    }

    private fun startSaving() {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (player?.isPlaying == true) save()
            }
        }
    }

    private fun save() {
        val p = player ?: return
        val d = detail ?: return
        val ep = d.episodes.getOrNull(episodeIndex) ?: return
        val duration = p.duration.takeIf { it > 0 } ?: 0L
        val position = if (p.playbackState == Player.STATE_ENDED) duration else p.currentPosition
        if (position <= 0 && duration <= 0) return
        val record = WatchRecord(
            videoKey = d.key,
            title = d.title,
            imageUrl = d.imageUrl,
            episodeKey = ep.key,
            episodeName = ep.name,
            positionMs = position,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
        )
        lifecycleScope.launch { app.history.upsert(record) }
    }

    private fun fail(e: Throwable) {
        Log.w("PlayerActivity", "playback failed", e)
        val details = StreamPlayer.httpDetails(e)
        val tried = attempts.takeIf { it.size > 1 }?.joinToString("\n", prefix = "尝试过：\n")
        errorView.text = listOfNotNull("播放失败：${StreamPlayer.describe(e)}", details, tried, "按返回键退出")
            .joinToString("\n\n")
        errorView.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onStop() {
        save()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        saveJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_EPISODE = "episode"

        fun intent(context: Context, videoKey: String, episodeKey: String?) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO, videoKey)
                .putExtra(EXTRA_EPISODE, episodeKey)
    }
}
