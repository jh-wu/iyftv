package com.iyftv.app

import android.app.Instrumentation
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.ui.player.PlayerActivity
import com.iyftv.app.ui.player.WebPlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Each title opens in the player that worked for it last time. */
@RunWith(AndroidJUnit4::class)
class PlayerChoiceDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp

    private suspend fun firstTvSeries() =
        app.source.list(app.source.categories().first { it.name == "电视剧" }, 1).items.first().key

    @Test fun rememberedWhenTheAppPlayerPlays(): Unit = runBlocking {
        val key = firstTvSeries()
        app.getSharedPreferences("player_choice", 0).edit().remove(key).commit()
        ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(app, key, null)).use {
            repeat(30) {
                if (app.playerChoice.get(key) != null) return@repeat
                delay(2_000)
            }
            assertEquals(PlayerChoice.Player.App, app.playerChoice.get(key))
        }
    }

    @Test fun webTitlesOpenInTheWebPlayer(): Unit = runBlocking {
        val key = firstTvSeries()
        app.playerChoice.set(key, PlayerChoice.Player.Web)
        val monitor = Instrumentation.ActivityMonitor(WebPlayerActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(app, key, null)).use {
                val web = instrumentation.waitForMonitorWithTimeout(monitor, 60_000)
                assertNotNull("a title remembered as web did not open the web player", web)
                web?.finish()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            app.getSharedPreferences("player_choice", 0).edit().remove(key).commit()
        }
    }
}
