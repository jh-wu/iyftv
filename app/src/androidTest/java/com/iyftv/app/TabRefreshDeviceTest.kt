package com.iyftv.app

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.model.Category
import com.iyftv.app.data.model.ListFilter
import com.iyftv.app.data.model.Page
import com.iyftv.app.data.model.VideoSummary
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pressing OK on a category tab loads its first page again. */
@RunWith(AndroidJUnit4::class)
class TabRefreshDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp
    private val prefs get() = instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    /** The real site, counting first-page loads. */
    private class Counting(private val real: VideoSource) : VideoSource by real {
        val firstPages = AtomicInteger()
        override suspend fun list(category: Category, page: Int, filter: ListFilter): Page<VideoSummary> {
            if (page == 1) firstPages.incrementAndGet()
            return real.list(category, page, filter)
        }
    }

    @Test fun pressingTheTabReloadsIt(): Unit = runBlocking {
        val real = app.source
        val counting = Counting(real)
        app.source = counting
        prefs.edit().putInt("selected_tab_v2", 1).commit()
        instrumentation.setInTouchMode(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                delay(8_000) // focus starts on the 电影 tab, its first page loaded
                val before = counting.firstPages.get()
                assertTrue("电影 never loaded", before >= 1)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                delay(4_000)
                assertEquals("pressing the tab did not reload it", before + 1, counting.firstPages.get())
                assertEquals(1, prefs.getInt("selected_tab_v2", -1))
            }
        } finally {
            app.source = real
        }
    }
}
