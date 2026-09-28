package com.sohva.tv.core.data

import com.sohva.tv.core.data.backup.BackupPayload
import com.sohva.tv.core.data.backup.BackupReader
import com.sohva.tv.core.data.backup.BackupWriter
import com.sohva.tv.core.model.backup.BackupException
import com.sohva.tv.core.model.backup.BackupProblem
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 71 §11 "Unit": fixture files in beta 23's format (hand-written, not by the code under
 * test) read into the typed model; §6.4's defaults and refusals; the writer's output reads back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCodecTest {
    private fun fixture(name: String): String = javaClass.getResource("/backup/$name")!!.readText()

    private fun read(json: String): BackupPayload = BackupReader.read(Buffer().writeUtf8(json))

    private fun problem(json: String): Pair<BackupProblem, String?> {
        try {
            read(json)
        } catch (e: BackupException) {
            return e.problem to e.field
        }
        fail("read succeeded")
        error("unreachable")
    }

    @Test
    fun aFormatTwoFileReadsIntoTheTypedModel() {
        val p = read(fixture("beta23-format2.json"))
        assertEquals(2, p.formatVersion)
        assertEquals(listOf("src-1", "src-2"), p.sources.map { it.source.id })
        assertEquals("fictional-pass", p.sources[1].secrets.xtreamPassword)
        assertEquals("2468", p.parentalPin)
        val prefs = p.preferences
        assertEquals("Europe/Helsinki", prefs.timeZoneId)
        assertEquals("LAST_CHANNEL", prefs.startupScreen)
        assertEquals("kanagawa", prefs.colorTheme)
        assertEquals("COMPACT", prefs.interfaceScale)
        assertEquals("STABILITY", prefs.bufferProfile)
        assertFalse(prefs.autoPlayNext)
        assertTrue(prefs.pictureInPicture)
        assertNull(prefs.autoFrameRate)
        // Names cut to 24, colours clamped (§6.4).
        assertEquals(24, prefs.profiles[1].name.length)
        assertEquals(5, prefs.profiles[1].color)
        assertEquals(listOf("Adult"), prefs.hiddenLive)
        // Unusable groups dropped; the kept ones stored as the preferences keep them.
        val groups = prefs.customGroupsJson!!
        assertTrue(groups.contains("\"eighties\"") && !groups.contains("broken"))
        assertTrue(groups.contains("\"fromYear\":1980"))
        assertEquals(listOf("name:kids"), p.profileData.getValue("p1790000000000").allowedLive)
        assertEquals(listOf("src-1:c7", "src-1:c42"), p.profileData.getValue("default").recentChannelIds)
        val logo = p.channelPreferences[0]
        assertEquals(3, logo.sortOrder)
        assertEquals(12, logo.channelNumber)
        assertNotNull(logo.customLogoData)
        assertEquals(1, p.channelLists.size)
        assertEquals(2, p.channelListMembers.size)
        assertEquals(3, p.rules.size)
        assertEquals(2L, p.rules[2].position)
        assertEquals(2, p.aliases.size)
    }

    @Test
    fun aFormatOneFileTakesTheDefaultsAndHasNoOrganisation() {
        val p = read(fixture("beta23-format1.json"))
        assertEquals(1, p.formatVersion)
        assertTrue(p.rules.isEmpty() && p.aliases.isEmpty())
        // No colour theme: Original (a null here); no timeZoneFollowsDevice: the zone is a chosen one.
        assertNull(p.preferences.colorTheme)
        assertEquals("Europe/Stockholm", p.preferences.timeZoneId)
        assertEquals("CHANNEL_KEYS_ONLY", p.preferences.remoteChannelKeyMode)
        assertNull(p.preferences.remoteMappings)
        // Without profileData the lists beside the preferences are the active profile's.
        assertEquals(listOf("src-1:c2"), p.profileData.getValue("default").lockedChannelIds)
    }

    @Test
    fun faultsAreRefusedWithTheirOwnProblem() {
        val good = fixture("beta23-format2.json")
        assertEquals(BackupProblem.FORMAT_VERSION to null, problem(good.replace("\"formatVersion\":2", "\"formatVersion\":3")))
        assertEquals(BackupProblem.PIN, problem(good.replace("\"2468\"", "\"24\"")).first)
        assertEquals(BackupProblem.STARTUP, problem(good.replace("\"LAST_CHANNEL\"", "\"SPORT\"")).first)
        assertEquals(BackupProblem.REMOTE, problem(good.replace("\"DPAD_AND_CHANNEL_KEYS\"", "\"ARROWS\"")).first)
        assertEquals(BackupProblem.STRUCTURE to "playbackBufferProfile", problem(good.replace("\"STABILITY\"", "\"HUGE\"")))
        assertEquals(BackupProblem.STRUCTURE to "followedSports", problem(good.replace("[\"FOOTBALL\",\"ICE_HOCKEY\"]", "[\"CURLING\"]")))
        assertEquals(BackupProblem.MISSING_SOURCE, problem(good.replace("\"sourceId\":\"src-2\"", "\"sourceId\":\"src-9\"")).first)
        assertEquals(BackupProblem.DUPLICATE_PREFERENCE, problem(good.replace("\"channelId\":\"src-2:xtream-5\",\"sourceId\"", "\"channelId\":\"src-1:c42\",\"sourceId\"")).first)
        assertEquals(BackupProblem.MISSING_LIST, problem(good.replace("\"channelId\":\"src-2:xtream-5\",\"sortOrder\":1", "\"channelId\":\"src-2:xtream-5\",\"sortOrder\":1,\"x\":0").replace("{\"listId\":\"4f1c2a8e-0000-4000-8000-000000000001\",\"channelId\":\"src-2", "{\"listId\":\"other\",\"channelId\":\"src-2")).first)
        assertEquals(BackupProblem.MISSING_FIELD to "timeZoneId", problem(good.replace("\"timeZoneId\":\"Europe/Helsinki\",", "")))
        assertEquals(BackupProblem.BLANK_FIELD to "channelId", problem(good.replace("\"channelId\":\"src-1:c42\",\"sourceId\"", "\"channelId\":\" \",\"sourceId\"")))
        assertEquals(BackupProblem.STRUCTURE to "organization.position", problem(good.replace("\"position\":2", "\"position\":-1")))
        assertEquals(BackupProblem.MISSING_FIELD to "organization", problem(good.replace(",\"organization\":", ",\"organisation\":")))
        assertEquals(BackupProblem.STRUCTURE, problem("{\"formatVersion\":").first)
    }

    /**
     * Spec 02 HOME-FR-91: a profile's Home layout travels as an optional text in its profile data;
     * a file without it (beta 23's, or a profile left at the default) reads as no layout, and the
     * writer adds nothing for the default, so format 2 stays what beta 23 reads.
     */
    @Test
    fun theHomeLayoutIsAnOptionalProfileField() {
        val original = read(fixture("beta23-format2.json"))
        assertTrue(original.profileData.values.all { it.homeLayout == null })
        val layout = "recent-channels,-watch-next,continue-watching,todays-sport,recommended"
        val kids = original.profileData.keys.first { it != "default" }
        val changed = original.copy(profileData = original.profileData.mapValues { (id, kept) -> if (id == kids) kept.copy(homeLayout = layout) else kept })
        val out = Buffer()
        BackupWriter.write(changed, deviceZone = "Europe/Oslo", exportedAt = 1L, sink = out)
        val json = out.readUtf8()
        assertEquals(1, Regex("\"homeLayout\"").findAll(json).count())
        val again = read(json)
        assertEquals(layout, again.profileData.getValue(kids).homeLayout)
        assertEquals(null, again.profileData.getValue("default").homeLayout)
    }

    @Test
    fun whatTheWriterWritesReadsBack() {
        val original = read(fixture("beta23-format2.json"))
        val out = Buffer()
        BackupWriter.write(original, deviceZone = "Europe/Oslo", exportedAt = 1L, sink = out)
        val json = out.readUtf8()
        val again = read(json)
        assertEquals(original.sources, again.sources)
        assertEquals(original.preferences, again.preferences)
        assertEquals(original.profileData, again.profileData)
        assertEquals(original.channelLists, again.channelLists)
        assertEquals(original.rules, again.rules)
        assertEquals(original.channelPreferences.map { it.copy(customLogoData = null) }, again.channelPreferences.map { it.copy(customLogoData = null) })
        // Never a null for booleans or arrays; formatVersion 2; the TV's zone when it is followed.
        assertTrue(json.startsWith("{\"formatVersion\":2,"))
        val following = original.copy(preferences = original.preferences.copy(timeZoneId = null))
        val out2 = Buffer().also { BackupWriter.write(following, "Europe/Oslo", 1L, it) }.readUtf8()
        assertTrue(out2.contains("\"timeZoneId\":\"Europe/Oslo\",\"timeZoneFollowsDevice\":true"))
        assertNull(read(out2).preferences.timeZoneId)
    }
}
