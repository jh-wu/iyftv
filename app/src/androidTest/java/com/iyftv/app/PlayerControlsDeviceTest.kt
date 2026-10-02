package com.iyftv.app

import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.ui.player.PlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The app player's control bar opens on the progress bar, and next moves to the next episode. */
@RunWith(AndroidJUnit4::class)
class PlayerControlsDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IyfTvApp

    @Test fun progressFocusedAndNextEpisodeEnabled(): Unit = runBlocking {
        val series = app.source.list(app.source.categories().first { it.name == "电视剧" }, 1).items
            .map { app.source.detail(it.key) }.first { it.episodes.size > 1 }
        app.playerChoice.set(series.key, PlayerChoice.Player.App)
        app.history.delete(series.key)
        ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(app, series.key, series.episodes.first().key)).use { scenario ->
            var ready = false
            for (i in 0 until 30) {
                delay(2_000)
                scenario.onActivity { ready = it.controlsPlayer?.playbackState == Player.STATE_READY }
                if (ready) break
            }
            assertTrue("first episode never became ready", ready)

            var nextEnabled = false
            scenario.onActivity {
                nextEnabled = it.controlsPlayer?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) == true
                it.hideControls()
            }
            delay(500)
            // OK on the remote, as on a TV (this also leaves touch mode, where nothing takes focus).
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue("next episode is disabled on episode 1 of ${series.episodes.size}", nextEnabled)
            delay(1_000)
            var focusOnProgress = false
            var focused: String? = null
            scenario.onActivity {
                focusOnProgress = it.focusedControl != null && it.focusedControl === it.progressBarView
                focused = it.focusedControl?.let { v -> "${v.javaClass.simpleName} ${runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()}" }
            }
            assertTrue("the control bar did not open on the progress bar (focus: $focused)", focusOnProgress)

            scenario.onActivity { it.controlsPlayer?.seekToNext() }
            delay(3_000)
            var episode = -1
            scenario.onActivity { episode = it.currentEpisode }
            assertEquals("next did not move to episode 2", 1, episode)
        }
    }
}
