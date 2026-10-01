package com.iyftv.app

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.ui.player.WebPlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
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
            // Jump into the middle with the sound off, as the site leaves it after an ad, then watch.
            scenario.onActivity { it.runScript("(function(){var v=window.__iyftvMain;if(v)v.currentTime=v.duration*0.4;v.muted=true;v.volume=0})()") }
            val samples = mutableListOf<String>()
            repeat(60) {
                delay(3_000)
                WebPlayerActivity.lastState?.let { s ->
                    samples += s
                    Log.i("WebPlayerDiag", s)
                }
            }
            val mains = samples.map(::mainEntry)
            Log.i("WebPlayerDiag", "ad samples: ${mains.count { it.contains(".mp4 ") }}, ad requests refused: ${WebPlayerActivity.adsBlocked}")
            val adSamples = mains.count { it.contains(".mp4 ") }
            assertTrue("the ad replaced the episode in $adSamples samples", adSamples <= 1)
            val times = mains.mapNotNull { Regex(" t([0-9.]+)/").find(it)?.groupValues?.get(1)?.toDouble() }
            assertTrue("episode did not keep playing: ${times.first()} -> ${times.last()}", times.last() - times.first() > 120)
            val last = mains.last()
            assertTrue("episode lost its place on top: ${samples.last()}", last.isNotEmpty())
            assertFalse("episode is still on the ad clip: $last", last.contains(".mp4 "))
            assertFalse("episode is muted: $last", last.contains(" muted"))
            assertTrue("episode is not at full volume: $last", last.contains(" vol1 "))
        }
    }

    /** The probe entry of the video the app treats as the episode, or "" if none. */
    private fun mainEntry(state: String): String {
        val json = JSONTokener(state).nextValue() as? String ?: return ""
        val all = JSONObject(json).optJSONArray("all") ?: return ""
        return (0 until all.length()).map { all.optString(it) }.firstOrNull { it.endsWith(" MAIN") }.orEmpty()
    }
}
