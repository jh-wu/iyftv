package com.iyftv.app

import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue

/**
 * Whether iyf.tv answers this machine. Since late October 2026 it shows GitHub's runners
 * Cloudflare's "Just a moment..." page (HTTP 403), and each live-site test then spent about
 * two minutes timing out. Those tests are skipped, with the reason, instead.
 */
object LiveSite {
    private val blockedReason: String? by lazy {
        val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
        runCatching {
            http.newCall(Request.Builder().url("https://www.iyf.tv/").header("User-Agent", "Mozilla/5.0").build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    r.code == 403 && body.contains("Just a moment") -> "Cloudflare challenge (HTTP 403)"
                    !r.isSuccessful -> "HTTP ${r.code}"
                    else -> null
                }
            }
        }.getOrElse { "unreachable: $it" }.also { Log.i("LiveSite", "iyf.tv from this machine: ${it ?: "ok"}") }
    }

    /** Skips the calling test when the site blocks this machine. */
    fun assume() = assumeTrue("iyf.tv blocks this machine: $blockedReason", blockedReason == null)
}
