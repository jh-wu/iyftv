package com.iyftv.app

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.history.PlayerChoice
import com.iyftv.app.data.history.WatchRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Long-pressing a title in 继续观看 opens a menu that resets its player or removes it. */
@RunWith(AndroidJUnit4::class)
class TitleMenuDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp

    private suspend fun press(key: Int) {
        instrumentation.sendKeyDownUpSync(key)
        delay(1_000)
    }

    /** Holds OK the way a remote does: a press, repeats flagged as a long press, then the release. */
    private suspend fun longPressOk() {
        val t = SystemClock.uptimeMillis()
        val down = KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
        instrumentation.sendKeySync(down)
        Thread.sleep(500)
        for (r in 1..6) {
            instrumentation.sendKeySync(KeyEvent.changeTimeRepeat(down, SystemClock.uptimeMillis(), r, if (r == 1) KeyEvent.FLAG_LONG_PRESS else 0))
            Thread.sleep(50)
        }
        instrumentation.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        delay(1_500)
    }

    @Test fun longPressMenuResetsPlayerAndRemovesTitle(): Unit = runBlocking {
        val key = "menu-test-title"
        app.history.upsert(
            WatchRecord(key, "菜单测试", null, "ep1", "第1集", 120_000, 3_600_000, System.currentTimeMillis() + 86_400_000),
        )
        app.playerChoice.set(key, PlayerChoice.Player.Web)
        instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE)
            .edit().putInt("selected_tab_v2", 0).commit()
        instrumentation.setInTouchMode(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                delay(8_000)
                press(KeyEvent.KEYCODE_DPAD_DOWN) // from the 继续观看 tab to the newest title
                longPressOk()
                // Releasing OK after the long press must not pick the first menu item.
                assertNotNull("the long press release removed the title", app.history.get(key))
                assertEquals(PlayerChoice.Player.Web, app.playerChoice.get(key))

                press(KeyEvent.KEYCODE_DPAD_DOWN)   // 重置播放器
                press(KeyEvent.KEYCODE_DPAD_CENTER)
                assertNull("the player was not reset", app.playerChoice.get(key))
                assertNotNull(app.history.get(key))

                // Focus is back on the title; its menu opens on 删除.
                app.playerChoice.set(key, PlayerChoice.Player.App)
                longPressOk()
                press(KeyEvent.KEYCODE_DPAD_CENTER) // 从继续观看中删除
                delay(1_000)
                assertNull("the title was not removed", app.history.get(key))
                assertEquals("the open tab changed", 0,
                    instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE).getInt("selected_tab_v2", -1))
            }
        } finally {
            app.history.delete(key)
            app.playerChoice.clear(key)
            instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE)
                .edit().putInt("selected_tab_v2", 1).commit()
        }
    }
}
