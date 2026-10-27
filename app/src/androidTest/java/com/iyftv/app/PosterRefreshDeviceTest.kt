package com.iyftv.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.history.WatchRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** A title in 继续观看 whose saved poster no longer loads gets a fresh one from the site. */
@RunWith(AndroidJUnit4::class)
class PosterRefreshDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IyfTvApp

    @Test fun brokenPosterIsReplaced(): Unit = runBlocking {
        val tv = app.source.categories().first { it.name == "电视剧" }
        val title = app.source.list(tv, 1).items.first()
        val dead = "https://example.invalid/old-poster.jpg"
        app.history.upsert(WatchRecord(title.key, title.title, dead, "ep", "第1集", 60_000, 3_600_000, System.currentTimeMillis() + 86_400_000))
        val prefs = instrumentation.targetContext.getSharedPreferences("home", Context.MODE_PRIVATE)
        prefs.edit().putInt("selected_tab_v2", 0).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                var image: String? = dead
                for (i in 1..20) {
                    delay(1_000)
                    image = app.history.get(title.key)?.imageUrl
                    if (image != dead) break
                }
                assertNotNull(image)
                assertNotEquals("the dead poster was not replaced", dead, image)
            }
        } finally {
            app.history.delete(title.key)
            prefs.edit().putInt("selected_tab_v2", 1).commit()
        }
    }
}
