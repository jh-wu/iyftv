package com.iyftv.app.update

import com.iyftv.app.data.update.GitHubReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleasesTest {

    // Trimmed from https://api.github.com/repos/jh-wu/iyftv/releases/latest
    private val latest = """
        {"url":"https://api.github.com/repos/jh-wu/iyftv/releases/1","tag_name":"build-12","name":"iyfTV build 12",
         "body":"Add online updates\n\nChecks GitHub for a newer build.\n\nCo-Authored-By: someone\n\nBuilt from abc123. Install: https://github.com/jh-wu/iyftv/releases/latest/download/iyftv.apk",
         "assets":[{"name":"iyftv.apk","size":13195261,
           "browser_download_url":"https://github.com/jh-wu/iyftv/releases/download/build-12/iyftv.apk"}]}
    """.trimIndent()

    @Test fun parsesLatestRelease() {
        val r = GitHubReleases.parse(latest)!!
        assertEquals(12, r.build)
        assertEquals("iyfTV build 12", r.title)
        assertEquals("https://github.com/jh-wu/iyftv/releases/download/build-12/iyftv.apk", r.apkUrl)
        assertEquals(13195261L, r.sizeBytes)
        assertEquals("Add online updates\n\nChecks GitHub for a newer build.", r.notes)
    }

    @Test fun buildNumberFromTag() {
        assertEquals(11, GitHubReleases.buildNumber("build-11"))
        assertNull(GitHubReleases.buildNumber("v1.0"))
        assertNull(GitHubReleases.buildNumber("build-11-rc"))
    }

    @Test fun oldReleaseNotesAreOnlyBoilerplate() {
        assertNull(GitHubReleases.releaseNotes("Built from 6eef2d4. Install: https://github.com/jh-wu/iyftv/releases/latest/download/iyftv.apk"))
    }

    @Test fun rejectsReleaseWithoutApkOrTag() {
        assertNull(GitHubReleases.parse("""{"tag_name":"build-3","assets":[]}"""))
        assertNull(GitHubReleases.parse("""{"tag_name":"latest","assets":[{"name":"iyftv.apk","browser_download_url":"https://x/y.apk"}]}"""))
        assertNull(GitHubReleases.parse("""{"message":"Not Found"}"""))
        assertNull(GitHubReleases.parse("<html>"))
    }
}
