package com.iyftv.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.ui.player.WebPlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The website's own player, in the fallback web player, gets past the ad into the episode. */
@RunWith(AndroidJUnit4::class)
class WebPlayerDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IyfTvApp

    @Test fun websitePlayerPlaysAndRecordsProgress() = runBlocking {
        val hit = app.source.search("沉默的证人", 1).items.first()
        val detail = app.source.detail(hit.key)
        val ep = detail.episodes.first()
        app.history.delete(detail.key)
        val intent = WebPlayerActivity.intent(app, detail.key, ep.key, 0, detail.title, detail.imageUrl, ep.name, null)
        ActivityScenario.launch<WebPlayerActivity>(intent).use { scenario ->
            var position = 0L
            for (i in 0 until 60) {
                delay(2_000)
                position = app.history.get(detail.key)?.positionMs ?: 0L
                if (position > 0) break
                android.util.Log.i("WebPlayerDeviceTest", "page: ${WebPlayerActivity.lastState}")
            }
            assertTrue("web player recorded no progress for ${detail.title} ${ep.name}; page: ${WebPlayerActivity.lastState}", position > 0)
            // A remote key brings up the control bar.
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.waitForIdleSync()
            var shown = false
            scenario.onActivity { shown = it.controlsShown }
            assertTrue("control bar did not appear after a remote key", shown)
            // Up from the buttons reaches the progress bar, where right moves the video on.
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)
            instrumentation.waitForIdleSync()
            var onBar = false
            scenario.onActivity { onBar = it.currentFocus is android.widget.SeekBar }
            assertTrue("progress bar can't be reached with the remote", onBar)
        }
    }
}
