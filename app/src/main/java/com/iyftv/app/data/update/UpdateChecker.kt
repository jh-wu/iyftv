package com.iyftv.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** A published build of the app. [build] matches the installed app's versionCode. */
data class Release(
    val build: Int,
    val title: String,
    val apkUrl: String,
    val notes: String?,
    val sizeBytes: Long?,
)

object GitHubReleases {
    const val REPO = "jh-wu/iyftv"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val APK_NAME = "iyftv.apk"

    private val TAG = Regex("build-(\\d+)")
    private val json = Json { ignoreUnknownKeys = true }

    /** CI tags each release `build-<run number>`, and the same number is the APK's versionCode. */
    fun buildNumber(tag: String): Int? = TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun parse(body: String): Release? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val build = root.string("tag_name")?.let(::buildNumber) ?: return null
        val asset = root["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.let { assets -> assets.firstOrNull { it.string("name") == APK_NAME } ?: assets.firstOrNull { it.string("name")?.endsWith(".apk") == true } }
            ?: return null
        val url = asset.string("browser_download_url") ?: return null
        return Release(
            build = build,
            title = root.string("name")?.takeIf { it.isNotBlank() } ?: "build $build",
            apkUrl = url,
            notes = root.string("body")?.let(::releaseNotes),
            sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull,
        )
    }

    /** The commit message part of the release body, without CI's install boilerplate. */
    fun releaseNotes(body: String): String? =
        body.lines()
            .filterNot { it.startsWith("Built from ") || it.startsWith("Install: ") || it.startsWith("Co-Authored-By:") || it.startsWith("Claude-Session:") }
            .joinToString("\n").trim()
            .takeIf { it.isNotEmpty() }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}

class UpdateChecker(
    private val http: OkHttpClient,
    val currentBuild: Int,
    private val latestUrl: String = GitHubReleases.LATEST_URL,
) {
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(latestUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("GitHub HTTP ${resp.code}")
            GitHubReleases.parse(body) ?: throw IOException("No APK in the latest release")
        }
    }

    /** The latest release if it is newer than the installed build, else null. */
    suspend fun newer(): Release? = latest().takeIf { it.build > currentBuild }

    suspend fun download(release: Release, dest: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes ?: -1L
            var done = 0L
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Download incomplete ($done of $total bytes)")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("Could not save the update")
        dest
    }
}
