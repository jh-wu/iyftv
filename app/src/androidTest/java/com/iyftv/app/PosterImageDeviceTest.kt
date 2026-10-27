package com.iyftv.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Older titles J reported without posters in 继续观看: their posters must load. */
@RunWith(AndroidJUnit4::class)
class PosterImageDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IyfTvApp

    private suspend fun status(http: OkHttpClient, url: String?): String = withContext(Dispatchers.IO) {
        if (url == null) return@withContext "no url"
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { "${it.code} ${it.header("Content-Type")} ${it.body?.contentLength()}" }
        }.getOrElse { "error $it" }
    }

    @Test fun oldTitlesHavePosters(): Unit = runBlocking {
        val plain = OkHttpClient()
        val problems = mutableListOf<String>()
        for (name in listOf("绝望写手", "无神")) {
            val hits = app.source.search(name, 1).items
            Log.i(TAG, "$name: ${hits.size} hits ${hits.take(5).map { "${it.title}|${it.key}|${it.imageUrl}" }}")
            for (hit in hits.filter { it.title.contains(name) }.take(3)) {
                val d = app.source.detail(hit.key)
                val listStatus = status(plain, hit.imageUrl)
                val detailStatus = status(plain, d.imageUrl)
                Log.i(TAG, "${hit.title}: search ${hit.imageUrl} -> $listStatus; detail '${d.title}' ${d.imageUrl} -> $detailStatus")
                if (!detailStatus.startsWith("200")) problems += "${hit.title}: detail image ${d.imageUrl} -> $detailStatus (search image ${hit.imageUrl} -> $listStatus)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object { const val TAG = "ImageProbe" }
}
