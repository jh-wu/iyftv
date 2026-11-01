package com.iyftv.app

import android.app.Activity
import android.app.Instrumentation
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.Episode
import com.iyftv.app.data.model.VideoDetail
import com.iyftv.app.ui.player.PlayerActivity
import com.iyftv.app.ui.player.WebPlayerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 从头播放 starts a part-watched episode at 0:00, while 继续播放 resumes it. Uses a title
 * remembered for the website's player, so the app hands straight over to WebPlayerActivity
 * and the start it asks for can be read from that hand-over.
 */
@RunWith(AndroidJUnit4::class)
class PlayFromStartDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp
    private val key = "from-start-test"

    private class OneEpisode(real: VideoSource, private val key: String) : VideoSource by real {
        override suspend fun detail(videoKey: String) =
            VideoDetail(key, "从头测试", null, null, null, listOf(Episode("ep1", "第1集"), Episode("ep2", "第2集")))
    }

    /** Starts PlayerActivity and returns the web player hand-over it makes. */
    private fun handOver(fromStart: Boolean): Activity {
        val monitor = instrumentation.addMonitor(WebPlayerActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(app, key, "ep1", fromStart)).use {
                val web: Activity? = instrumentation.waitForMonitorWithTimeout(monitor, 15_000)
                assertNotNull("the web player did not open", web)
                return web!!.also { it.finish() }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun fromStartIgnoresSavedPosition(): Unit = runBlocking {
        val real = app.source
        app.source = OneEpisode(real, key)
        app.playerChoice.set(key, PlayerChoice.Player.Web)
        app.history.upsert(WatchRecord(key, "从头测试", null, "ep1", "第1集", 600_000, 3_600_000, System.currentTimeMillis()))
        try {
            val resumed = handOver(fromStart = false)
            assertEquals("继续播放 should resume", 600_000L, resumed.intent.getLongExtra("start", -1))
            assertEquals(false, resumed.intent.getBooleanExtra("from_start", true))

            val restarted = handOver(fromStart = true)
            assertEquals("从头播放 should start at 0", 0L, restarted.intent.getLongExtra("start", -1))
            assertEquals(true, restarted.intent.getBooleanExtra("from_start", false))
        } finally {
            app.source = real
            app.playerChoice.clear(key)
            app.history.delete(key)
        }
    }
}
