package com.iyftv.app

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.ui.player.WebPlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Watches the website's player for a few minutes in the middle of an episode, where the
 * site shows ads, and logs what the page shows (tag WebPlayerDiag; CI prints it).
 * The episode must keep playing with sound throughout.
 */
@RunWith(AndroidJUnit4::class)
class WebPlayerAdsDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IyfTvApp

    @Test fun episodeStaysOnTopWithSound(): Unit = runBlocking {
        val hit = app.source.search("沉默的证人", 1).items.first()
        val detail = app.source.detail(hit.key)
        val ep = detail.episodes.first()
        val intent = WebPlayerActivity.intent(app, detail.key, ep.key, 0, detail.title, detail.imageUrl, ep.name, null)
        ActivityScenario.launch<WebPlayerActivity>(intent).use { scenario ->
            var state: String? = null
            for (i in 0 until 60) {
                delay(2_000)
                state = WebPlayerActivity.lastState
                if (state?.contains("MAIN") == true) break
            }
            assertTrue("episode never started: $state", state?.contains("MAIN") == true)
            // Jump into the middle, then watch.
            scenario.onActivity { it.runScript("(function(){var v=window.__iyftvMain;if(v)v.currentTime=v.duration*0.4})()") }
            val samples = mutableListOf<String>()
            repeat(60) {
                delay(3_000)
                WebPlayerActivity.lastState?.let { s ->
                    if (samples.lastOrNull() != s) samples += s
                    Log.i("WebPlayerDiag", s)
                }
            }
            // The site's ad clip may appear in at most one sample before it is skipped.
            val adSamples = samples.count { it.contains("src\\\":\\\"http") }
            assertTrue("ad clip stayed on screen in $adSamples samples", adSamples <= 1)
            val last = samples.last()
            assertTrue("episode lost its place on top: $last", last.contains("MAIN"))
            val mainEntry = Regex("\"[^\"]*MAIN\"").find(last)?.value.orEmpty()
            assertFalse("episode is muted: $mainEntry", mainEntry.contains(" muted"))
        }
    }
}
