package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.live.TemplateSealing
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Catch-up templates written plain by an earlier build are sealed once, in pages; sealed and empty ones stay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemplateSealingTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var pauses = 0
    private val sealing = TemplateSealing(db, { "v2:sealed:$it" }, Dispatchers.Unconfined) { pauses++ }

    @After
    fun close() = db.close()

    private fun channel(local: String, template: String?) = ChannelEntity(
        key = "s:$local", sourceId = "s", groupId = null, name = local, sortName = SortNames.of(local), providerName = local, providerGroupId = null,
        providerLogoUrl = null, tvgId = null, epgId = null,
        logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null, playlistOrder = 0, providerNumber = null,
        number = null, displayRank = 0, visible = true, catchupType = "default", catchupSource = template, catchupDays = 3,
        catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
    )

    private fun templates(): List<String?> =
        db.openHelper.readableDatabase.query("SELECT catchup_source FROM channel ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getString(0)) }
        }

    @Test
    fun plainTemplatesAreSealedOnceAndTheRestIsLeftAlone() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 60, 0, 0))
        val rows = ArrayList<ChannelEntity>()
        rows += channel("plain", "http://provider.example/user/secret/{utc}.ts")
        rows += channel("none", null)
        rows += channel("empty", "")
        rows += channel("sealed", "v2:00:11")
        // More than one page of plain rows.
        for (i in 0 until TemplateSealing.PAGE + 5) rows += channel("p$i", "?utc={utc}&n=$i")
        db.channelImport().insert(rows)

        assertEquals(TemplateSealing.PAGE + 6, sealing.run())
        val after = templates()
        assertEquals("v2:sealed:http://provider.example/user/secret/{utc}.ts", after[0])
        assertNull(after[1])
        assertEquals("", after[2])
        assertEquals("v2:00:11", after[3])
        assertEquals("v2:sealed:?utc={utc}&n=0", after[4])
        assertEquals(0, after.count { it != null && it.isNotEmpty() && !it.startsWith("v2:") })
        assertEquals("a pause between pages", 2, pauses)

        // Done is remembered: a later plain row would only come from a build without this pass.
        assertEquals(0, sealing.run())
        assertEquals(2, pauses)
    }

    @Test
    fun aStoredTemplateOpensWhetherSealedOrFromAnEarlierBuild() {
        val open: (String) -> String = { it.removePrefix("v2:sealed:") }
        assertEquals("http://provider.example/{utc}", TemplateSealing.open("v2:sealed:http://provider.example/{utc}", open))
        assertEquals("http://provider.example/{utc}", TemplateSealing.open("http://provider.example/{utc}", open))
        assertNull(TemplateSealing.open(null, open))
        assertNull(TemplateSealing.open(" ", open))
        // A sealed value this television can no longer open is no template, not a crash.
        assertNull(TemplateSealing.open("v2:00:11") { error("the key is gone") })
    }
}
