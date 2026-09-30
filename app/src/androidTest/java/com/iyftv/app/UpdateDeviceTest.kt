package com.iyftv.app

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iyftv.app.data.update.ApkInstaller
import com.iyftv.app.data.update.GitHubReleases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The update path against the real GitHub release, up to the point the system installer takes over. */
@RunWith(AndroidJUnit4::class)
class UpdateDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IyfTvApp

    @Test fun downloadsLatestReleaseAndPreparesInstall() = runBlocking {
        val release = app.updates.latest()
        println("latest release: build ${release.build}, installed build ${app.updates.currentBuild}, ${release.apkUrl}")
        assertEquals(GitHubReleases.APK_NAME, release.apkUrl.substringAfterLast('/'))

        val file = app.updates.download(release, ApkInstaller.updateFile(app, release)) {}
        assertTrue("downloaded APK should be a valid iyfTV package", ApkInstaller.isValid(app, file))

        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        assertEquals("content", uri.scheme)
        ApkInstaller.cleanUp(app)
    }
}
