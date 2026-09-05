package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdatesTest {
    private val releases = """
        [
          {
            "tag_name": "v0.1.0-beta.3", "name": "Sohva TV 0.1.0-beta.3", "draft": true, "prerelease": true,
            "body": "# Sohva TV 0.1.0-beta.3\n\nAndroid build **4**.",
            "assets": [{"name": "sohva-tv-0.1.0-beta.3.apk", "browser_download_url": "https://example.invalid/b3.apk", "size": 10}]
          },
          {
            "tag_name": "v0.1.0-beta.2", "name": "Sohva TV 0.1.0-beta.2", "draft": false, "prerelease": true,
            "body": "# Sohva TV 0.1.0-beta.2\n\nAndroid build **3**. This is an early, non-commercial Android TV test release.",
            "assets": [
              {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.invalid/sums.txt", "size": 753},
              {"name": "sohva-tv-0.1.0-beta.2-tester-pack.zip", "browser_download_url": "https://example.invalid/pack.zip", "size": 13494822},
              {"name": "sohva-tv-0.1.0-beta.2.apk", "browser_download_url": "https://example.invalid/b2.apk", "size": 14464296}
            ]
          },
          {
            "tag_name": "v0.1.0-beta.1", "name": "Sohva TV 0.1.0-beta.1", "draft": false, "prerelease": true,
            "body": "# Sohva TV 0.1.0-beta.1\n\nAndroid build **2**.",
            "assets": [{"name": "sohva-tv-0.1.0-beta.1.apk", "browser_download_url": "https://example.invalid/b1.apk", "size": 14463156}]
          },
          {
            "tag_name": "v9.9.9", "name": "No build stated", "draft": false, "prerelease": false,
            "body": "A release whose body forgot the build line.",
            "assets": [{"name": "sohva-tv-9.9.9.apk", "browser_download_url": "https://example.invalid/x.apk", "size": 1}]
          }
        ]
    """.trimIndent()

    @Test
    fun `releases parse with their assets and stated build`() {
        val parsed = AppUpdates.parseReleases(releases)
        assertEquals(4, parsed.size)
        val beta2 = parsed.first { it.tagName == "v0.1.0-beta.2" }
        assertEquals(3, beta2.versionCode)
        assertEquals("0.1.0-beta.2", beta2.versionName)
        assertEquals("sohva-tv-0.1.0-beta.2.apk", beta2.apk?.name)
        assertEquals(14464296L, beta2.apk?.sizeBytes)
        assertEquals("SHA256SUMS.txt", beta2.checksums?.name)
        assertNull(parsed.first { it.tagName == "v9.9.9" }.versionCode)
    }

    @Test
    fun `the newest published build above the installed one is offered`() {
        val parsed = AppUpdates.parseReleases(releases)
        val fromBeta1 = AppUpdates.selectUpdate(parsed, installedVersionCode = 2)
        assertEquals(3, fromBeta1?.versionCode)
        assertEquals("0.1.0-beta.2", fromBeta1?.versionName)
        assertEquals("https://example.invalid/b2.apk", fromBeta1?.apk?.downloadUrl)
        assertEquals("https://example.invalid/sums.txt", fromBeta1?.checksums?.downloadUrl)
    }

    @Test
    fun `drafts, releases without a build line, and older builds are never offered`() {
        val parsed = AppUpdates.parseReleases(releases)
        // The draft beta 3 (build 4) is invisible; beta 2 is what build 3 already is.
        assertNull(AppUpdates.selectUpdate(parsed, installedVersionCode = 3))
        assertNull(AppUpdates.selectUpdate(parsed, installedVersionCode = 99))
    }

    @Test
    fun `the checksum file yields the digest for one file name`() {
        val sums = """
            1f38b9d4a4ab8b5125cca185afb8b56d7132072be1b4dbb7c109d40dedbcb1b6  sohva-tv-0.1.0-beta.2.apk
            9ED786C73182E71BABB8E666BEC70BCB432C4A3469FA84554892C21A749FD044 *sohva-tv-0.1.0-beta.2-tester-pack.zip
            not-a-digest  README.md
        """.trimIndent()
        assertEquals(
            "1f38b9d4a4ab8b5125cca185afb8b56d7132072be1b4dbb7c109d40dedbcb1b6",
            AppUpdates.expectedChecksum(sums, "sohva-tv-0.1.0-beta.2.apk"),
        )
        assertEquals(
            "9ed786c73182e71babb8e666bec70bcb432c4a3469fa84554892c21a749fd044",
            AppUpdates.expectedChecksum(sums, "sohva-tv-0.1.0-beta.2-tester-pack.zip"),
        )
        assertNull(AppUpdates.expectedChecksum(sums, "README.md"))
        assertNull(AppUpdates.expectedChecksum(sums, "missing.apk"))
    }
}
