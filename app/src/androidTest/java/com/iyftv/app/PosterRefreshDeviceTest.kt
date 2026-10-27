package com.iyftv.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.VideoSource
import com.iyftv.app.data.history.WatchRecord
import com.iyftv.app.data.model.VideoDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** A title in 继续观看 whose saved poster no longer loads gets the one its page shows now. */
@RunWith(AndroidJUnit4::class)
class PosterRefreshDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp

    private class FreshPoster(private val real: VideoSource, private val key: String, private val poster: String) : VideoSource by real {
        override suspend fun detail(videoKey: String): VideoDetail =
            if (videoKey == key) VideoDetail(key, "海报测试", poster, null, null, emptyList(), null) else real.detail(videoKey)
    }

    @Test fun brokenPosterIsReplaced(): Unit = runBlocking {
        val key = "poster-test-title"
        val dead = "https://example.invalid/old-poster.jpg"
        val fresh = "https://example.invalid/new-poster.jpg"
        val real = app.source
        app.source = FreshPoster(real, key, fresh)
        app.history.upsert(WatchRecord(key, "海报测试", dead, "ep", "第1集", 60_000, 3_600_000, System.currentTimeMillis() + 86_400_000))
        val prefs = instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE)
        prefs.edit().putInt("selected_tab_v2", 0).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                var image: String? = dead
                for (i in 1..15) {
                    delay(1_000)
                    image = app.history.get(key)?.imageUrl
                    if (image != dead) break
                }
                assertEquals("the dead poster was not replaced", fresh, image)
            }
        } finally {
            app.source = real
            app.history.delete(key)
            prefs.edit().putInt("selected_tab_v2", 1).commit()
        }
    }
}
