package com.iyftv.app

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Picking a sort order in the 电影 tab keeps 电影 open (it used to jump to 继续观看). */
@RunWith(AndroidJUnit4::class)
class SortKeepsTabDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    private suspend fun press(key: Int) {
        instrumentation.sendKeyDownUpSync(key)
        delay(1_000)
    }

    @Test fun choosingScoreSortStaysOnTheTab(): Unit = runBlocking {
        prefs.edit().putInt("selected_tab_v2", 1).commit()
        instrumentation.setInTouchMode(false)
        ActivityScenario.launch(MainActivity::class.java).use {
            delay(8_000) // categories and the first page load from the live site
            press(KeyEvent.KEYCODE_DPAD_DOWN)   // from the 电影 tab to the sort button
            press(KeyEvent.KEYCODE_DPAD_CENTER) // open the sort list
            press(KeyEvent.KEYCODE_DPAD_DOWN)   // 按评分
            press(KeyEvent.KEYCODE_DPAD_CENTER) // pick it
            delay(3_000)
            assertEquals("the open tab changed after picking a sort", 1, prefs.getInt("selected_tab_v2", -1))
        }
    }
}
