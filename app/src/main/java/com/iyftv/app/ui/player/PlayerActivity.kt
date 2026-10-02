package com.iyftv.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.ImageButton
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.iyftv.app.IyfTvApp
import com.iyftv.app.data.history.PlayerChoice
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
        styleControls(playerView)
        // The control bar opens with the progress bar focused, so left/right seek straight away.
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) focusProgress()
        })
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
        if (app.playerChoice.get(d.key) == PlayerChoice.Player.Web) {
            // The video servers refused the app's player for this title before.
            openWebPlayer(startMs)
            return
        }
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
                playerView.player = EpisodePlayer(newPlayer)
                newPlayer.addListener(listener)
            }

        errorView.visibility = View.GONE
        p.setMediaItem(StreamPlayer.mediaItem(stream, "${d.title} ${ep.name}"), startMs)
        p.prepare()
        p.playWhenReady = true
        startSaving()
    }

    private fun openWebPlayer(position: Long) {
        val d = detail ?: return
        val ep = d.episodes[episodeIndex]
        val note = if (app.playerChoice.get(d.key) == PlayerChoice.Player.Web) null
        else "视频服务器拒绝了播放器，改用网页播放（${attempts.lastOrNull().orEmpty()}）"
        app.playerChoice.set(d.key, PlayerChoice.Player.Web)
        startActivity(WebPlayerActivity.intent(this, d.key, ep.key, position, d.title, d.imageUrl, ep.name, note))
        finish()
    }

    /** A remote key while the control bar is hidden opens it on the progress bar (and does nothing else). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val opensBar = event.keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        if (opensBar) Log.i("PlayerKeys", "key ${event.keyCode} action ${event.action} player=${player != null} fullyVisible=${playerView.isControllerFullyVisible} useController=${playerView.useController}")
        if (opensBar && player != null && !playerView.isControllerFullyVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                playerView.showController()
                focusProgress()
                playerView.postDelayed({ Log.i("PlayerKeys", "after show: ${progressBarState} focus=${playerView.findFocus()?.javaClass?.simpleName}") }, 400)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Focuses the progress bar now and again once the bar has laid out (the controller may refocus play/pause). */
    private fun focusProgress() {
        val bar = progressBar() ?: return
        bar.requestFocus()
        bar.post { bar.requestFocus() }
        bar.postDelayed({ if (playerView.isControllerFullyVisible && !bar.isFocused && playerView.findFocus() !is DefaultTimeBar) bar.requestFocus() }, 300)
    }

    private fun progressBar(): DefaultTimeBar? = playerView.findViewById(androidx.media3.ui.R.id.exo_progress)

    /**
     * Makes the focused control obvious from across the room: an amber frame and a larger
     * size on buttons, and an amber bar while the progress bar has focus.
     */
    private fun styleControls(root: View) {
        val density = resources.displayMetrics.density
        fun frame() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(0x55FFB400)
                setStroke((3 * density).toInt(), FOCUS_COLOR)
            })
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        when (root) {
            is DefaultTimeBar -> {
                root.background = frame()
                root.setKeyTimeIncrement(10_000)
                root.setOnFocusChangeListener { _, focused ->
                    val c = if (focused) FOCUS_COLOR else Color.WHITE
                    root.setPlayedColor(c)
                    root.setScrubberColor(c)
                }
            }
            is ImageButton, is android.widget.Button -> {
                root.background = frame()
                root.setOnFocusChangeListener { v, focused ->
                    val scale = if (focused) 1.3f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                }
            }
            is ViewGroup -> for (i in 0 until root.childCount) styleControls(root.getChildAt(i))
        }
    }

    /** Lets the control bar's previous and next buttons move between the title's episodes. */
    private inner class EpisodePlayer(player: Player) : ForwardingPlayer(player) {
        private fun hasNextEpisode() = detail?.let { episodeIndex + 1 < it.episodes.size } == true

        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addIf(Player.COMMAND_SEEK_TO_NEXT, hasNextEpisode())
            .removeIf(Player.COMMAND_SEEK_TO_NEXT, !hasNextEpisode())
            .build()

        override fun isCommandAvailable(command: Int): Boolean =
            if (command == Player.COMMAND_SEEK_TO_NEXT) hasNextEpisode() else super.isCommandAvailable(command)

        override fun hasNextMediaItem() = hasNextEpisode()

        override fun seekToNext() {
            if (hasNextEpisode()) switchEpisode(1)
        }

        override fun seekToPrevious() {
            if (episodeIndex > 0 && currentPosition < 3_000) switchEpisode(-1) else seekTo(0)
        }
    }

    private fun switchEpisode(step: Int) {
        save()
        lifecycleScope.launch { runCatching { playEpisode(episodeIndex + step, 0) }.onFailure(::fail) }
    }

    /** The episode being played, and the player the control bar drives; for tests. */
    val currentEpisode get() = episodeIndex
    val controlsPlayer: Player? get() = playerView.player
    fun hideControls() = playerView.hideController()
    val focusedControl: View? get() = playerView.findFocus()
    val progressBarView: View? get() = progressBar()
    val progressBarState: String get() = progressBar()?.let {
        "shown=${it.isShown} vis=${it.visibility} focusable=${it.isFocusable} enabled=${it.isEnabled} w=${it.width} " +
            "touchMode=${it.isInTouchMode} fullyVisible=${playerView.isControllerFullyVisible}"
    } ?: "no progress bar"

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                profiles?.keep()
                detail?.let { app.playerChoice.set(it.key, PlayerChoice.Player.App) }
            }
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
                // Every link and request style failed (in Australia the video servers refuse
                // anything but a browser): hand over to the website's own player.
                openWebPlayer(position)
                return
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
        private const val FOCUS_COLOR = 0xFFFFB400.toInt()
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_EPISODE = "episode"

        fun intent(context: Context, videoKey: String, episodeKey: String?) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO, videoKey)
                .putExtra(EXTRA_EPISODE, episodeKey)
    }
}
