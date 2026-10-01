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
        ActivityScenario.launch<WebPlayerActivity>(intent).use {
            var position = 0L
            repeat(60) {
                delay(2_000)
                position = app.history.get(detail.key)?.positionMs ?: 0L
                if (position > 0) return@use
                android.util.Log.i("WebPlayerDeviceTest", "page: ${WebPlayerActivity.lastState}")
            }
            assertTrue("web player recorded no progress for ${detail.title} ${ep.name}; page: ${WebPlayerActivity.lastState}", position > 0)
        }
    }
}
