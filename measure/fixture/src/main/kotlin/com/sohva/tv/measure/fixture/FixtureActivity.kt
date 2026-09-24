package com.sohva.tv.measure.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.DatabaseFactory
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.runBlocking

/**
 * Writes the owner-scale guide (plan/07 §6.1: 56,164 channels in 800 groups, about 165,000
 * programmes around now) once, shows "fixture-ready" and finishes. Fictional
 * names and reserved addresses only; streams are not playable.
 */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply { text = "fixture-busy" }
        setContentView(label)
        Thread({
            val started = System.currentTimeMillis()
            val written = seed()
            runOnUiThread {
                label.text = "fixture-ready"
                label.contentDescription = "fixture-ready $written in ${System.currentTimeMillis() - started} ms"
                // Gone again, so the app's own launch shows Home rather than this screen.
                label.postDelayed({ finish() }, FINISH_DELAY_MS)
            }
        }, "fixture-seed").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun seed(): Int {
        val db = DatabaseFactory.create(applicationContext)
        try {
            if (runBlocking { db.sources().get(SOURCE) } != null) return 0
            val now = System.currentTimeMillis()
            val base = (now / HALF_HOUR) * HALF_HOUR - 12 * HOUR
            var index = 0
            var programmes = 0
            for (g in 0 until GROUPS) {
                val perGroup = CHANNELS / GROUPS + if (g < CHANNELS % GROUPS) 1 else 0
                val groupId = db.groupImport().insert(
                    ContentGroupEntity(
                        sourceId = SOURCE, room = "LIVE", groupKey = "g$g", name = "${PREFIXES[g % PREFIXES.size]} | Group ${g + 1}",
                        providerOrder = g, itemCount = perGroup, shown = true, position = g, sortMode = null,
                    ),
                )
                val channels = ArrayList<ChannelEntity>(perGroup)
                val guide = ArrayList<ProgrammeEntity>()
                for (c in 0 until perGroup) {
                    val name = "${PREFIXES[g % PREFIXES.size]} | ${NAMES[index % NAMES.size]} ${TAGS[index % TAGS.size]} ${index + 1}".trim()
                    val epg = "owner.$index"
                    channels += ChannelEntity(
                        key = "$SOURCE:$index", sourceId = SOURCE, groupId = groupId, name = name, sortName = SortNames.of(name),
                        tvgId = epg, epgId = epg, logoUrl = null, streamUrlEnc = "not-playable", userAgent = null, referrer = null,
                        playlistOrder = index, providerNumber = index + 1, number = index + 1, displayRank = index * 1024L,
                        visible = true, catchupType = null, catchupSource = null, catchupDays = null, catchupTz = null,
                        xtreamStreamId = null, contentHash = 1, generation = 1,
                    )
                    if (index < WITH_GUIDE) {
                        var t = base + (index % 4) * 10 * MINUTE
                        var n = 0
                        while (n < PROGRAMMES_EACH) {
                            val length = LENGTHS[(index + n) % LENGTHS.size] * MINUTE
                            guide += ProgrammeEntity(
                                sourceId = SOURCE, snapshot = 1, epgId = epg, startAt = t, stopAt = t + length,
                                title = TITLES[(index * 7 + n) % TITLES.size], subtitle = null,
                                description = if (n % 2 == 0) "A fictional programme of the owner-scale fixture." else null,
                                categories = CATEGORIES[(index + n) % CATEGORIES.size], programmeKey = "%016x".format((index.toLong() shl 20) + n),
                            )
                            t += length
                            n++
                        }
                    }
                    index++
                }
                db.runInTransaction {
                    db.channelImport().insert(channels)
                    if (guide.isNotEmpty()) db.guideImport().insertProgrammes(guide)
                }
                programmes += guide.size
            }
            db.sourceStatus().upsert(SourceStatusEntity(SOURCE, "epg", "success", now, now, null, null, null, programmes, 0, 1, 1, 3 * HOUR))
            db.sourceStatus().upsert(SourceStatusEntity(SOURCE, "playlist", "success", now, now, null, null, null, index, 0, 1, null, null))
            // The source row last: the guide opens once a source with channels exists.
            runBlocking { db.sources().upsert(SourceEntity(SOURCE, "Owner-scale fixture", "M3U", true, 0, 1, "LIVE_TV", 0, now, now)) }
            return index + programmes
        } finally {
            db.close()
        }
    }

    private companion object {
        const val SOURCE = "owner-fixture"
        const val FINISH_DELAY_MS = 300L
        const val CHANNELS = 56_164
        const val GROUPS = 800
        const val WITH_GUIDE = 4_140
        const val PROGRAMMES_EACH = 40
        const val MINUTE = 60_000L
        const val HALF_HOUR = 30 * MINUTE
        const val HOUR = 60 * MINUTE
        val PREFIXES = listOf("FI", "SE", "UK", "DE", "NO", "DK", "US", "4K")
        val NAMES = listOf("Northstar", "Meridian", "Pulse", "Summit", "Harbor", "Lumen", "Cobalt", "Ember", "Aurora Nordic Documentary")
        val TAGS = listOf("HD", "FHD", "", "4K", "SD", "")
        val TITLES = listOf("Morning Signal", "Harbor Routes", "North Horizon", "Glass Kitchen", "Silent Weather", "Hidden Lighthouse", "Studio Eleven")
        val CATEGORIES = listOf("News", "Sport", "Kids", "Film", "Music", null)
        val LENGTHS = longArrayOf(30, 45, 60, 25, 90)
    }
}
