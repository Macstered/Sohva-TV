package com.sohva.tv.core.model

import com.sohva.tv.core.model.update.Release
import com.sohva.tv.core.model.update.ReleaseAsset
import com.sohva.tv.core.model.update.ReleaseNotes
import com.sohva.tv.core.model.update.Releases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 72 §11 "Unit": the feed's selection, profiles, checksum lines and notes. */
class ReleasesTest {
    private fun asset(name: String) = ReleaseAsset(name, "https://downloads.example/$name", 1)

    private fun release(tag: String, build: Int?, draft: Boolean = false, prerelease: Boolean = true, apk: Boolean = true, profiles: Boolean = true): Release {
        val base = "sohva-tv-${tag.removePrefix("v")}"
        val assets = buildList {
            add(asset(Releases.CHECKSUMS))
            add(asset("$base-tester-pack.zip"))
            if (profiles) {
                add(asset("$base.api28.dm"))
                add(asset("$base.api31.dm"))
            }
            if (apk) add(asset("$base.apk"))
        }
        val body = if (build == null) "# Sohva TV\n\nNo build line." else "# Sohva TV ${tag.removePrefix("v")}\n\nAndroid build **$build**. Prerelease."
        return Release(tag, body, draft, prerelease, assets)
    }

    @Test
    fun theHighestPublishedBuildAboveTheInstalledOneIsOffered() {
        val feed = listOf(
            release("v0.2.0-beta.3", 103, draft = true),
            release("v0.2.0-beta.2", 102, prerelease = false),
            release("v0.2.0-beta.1", 101),
            release("v0.1.0-beta.23", 57),
        )
        val offered = Releases.select(feed, installed = 100, sdk = 30)!!
        assertEquals("0.2.0-beta.2", offered.version)
        assertEquals(102, offered.build)
        assertEquals("sohva-tv-0.2.0-beta.2.apk", offered.apk.name)
        assertEquals(Releases.CHECKSUMS, offered.checksums!!.name)
        assertEquals("sohva-tv-0.2.0-beta.2.api28.dm", offered.profile!!.name)
        assertNull(Releases.select(feed, installed = 102, sdk = 30))
    }

    @Test
    fun releasesWithoutABuildLineOrAnApkAreNeverOffered() {
        assertNull(Releases.select(listOf(release("v9", null), release("v8", 200, apk = false)), installed = 100, sdk = 34))
        // A bolded build before the real line would win: the contract forbids it (§7.5 rule 2).
        assertEquals(51, Releases.statedBuild("since build **51**, Android build **57**"))
    }

    @Test
    fun theProfileFollowsTheDevicesAndroidAndIsOptional() {
        assertEquals("a.api28.dm", Releases.profileName("a.apk", 30))
        assertEquals("a.api28.dm", Releases.profileName("a.apk", 28))
        assertEquals("a.api31.dm", Releases.profileName("a.apk", 31))
        assertEquals("a.api31.dm", Releases.profileName("a.apk", 35))
        assertNull(Releases.profileName("a.apk", 27))
        val offered = Releases.select(listOf(release("v0.2.0", 101, profiles = false)), installed = 100, sdk = 34)!!
        assertNull(offered.profile)
    }

    @Test
    fun theInstalledBuildsOwnReleaseSkipsDrafts() {
        val feed = listOf(release("v1-draft", 57, draft = true), release("v0.1.0-beta.23", 57))
        assertEquals("v0.1.0-beta.23", Releases.own(feed, 57)!!.tag)
    }

    @Test
    fun checksumLinesInEveryWrittenForm() {
        val a = "a".repeat(64)
        val b = "B".repeat(64)
        val sums = "$a  sohva.apk\r\n${b} *sohva.api31.dm\r\n${"c".repeat(63)}  short.apk\n${"d".repeat(64)}  sohva.apk\n"
        assertEquals(a, Releases.digest(sums, "sohva.apk"))
        assertEquals("b".repeat(64), Releases.digest(sums, "sohva.api31.dm"))
        assertNull(Releases.digest(sums, "short.apk"))
        assertNull(Releases.digest(sums, "other.apk"))
    }

    @Test
    fun theChangedSectionBecomesPlainLines() {
        val body = """
            # Sohva TV 0.2.0

            Android build **101**.

            ## Changed since beta 1
            - **Guide** opens faster, see
              [the notes](https://example.org/notes).
            * Uses `less` memory.

            A paragraph that
            wraps.
            ### Fixes
            - One fix.

            ## Known issues
            - Not shown.
        """.trimIndent()
        assertEquals(
            "• Guide opens faster, see the notes.\n• Uses less memory.\nA paragraph that wraps.\nFixes\n• One fix.",
            ReleaseNotes.of(body, "en"),
        )
    }

    @Test
    fun bodiesWithoutAChangedSectionShowWholeAndLanguageSectionsFollowTheInterface() {
        assertEquals("Title\nAndroid build 5.", ReleaseNotes.of("# Title\n\nAndroid build **5**.", "en"))
        assertNull(ReleaseNotes.of("   \n", "en"))
        val bilingual = "# Sohva TV\n\nAndroid build **57**.\n\n## English\n- Lighter.\n\n## Suomi\n- Kevyempi.\n"
        assertEquals("• Lighter.", ReleaseNotes.of(bilingual, "en-GB"))
        assertEquals("• Kevyempi.", ReleaseNotes.of(bilingual, "fi"))
        assertEquals("• Lighter.", ReleaseNotes.of(bilingual, "sv"))
        assertEquals(ReleaseNotes.MAX_LENGTH, ReleaseNotes.of("x".repeat(5_000), "en")!!.length)
    }
}
