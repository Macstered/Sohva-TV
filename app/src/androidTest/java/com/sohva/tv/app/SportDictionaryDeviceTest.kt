package com.sohva.tv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.ProviderReference
import com.sohva.tv.core.model.sport.ReferenceKind
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.DictionaryNames
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import com.sohva.tv.feature.sport.dictionary.AssetPairingDictionary
import com.sohva.tv.feature.sport.dictionary.DictionaryAsset
import com.sohva.tv.feature.sport.pairing.PairingCache
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated cache directories; no household sources or API requests. */
@RunWith(AndroidJUnit4::class)
class SportDictionaryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val kickoff = LocalDate.now(ZoneOffset.UTC).atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun game(id: String, sport: SportType, home: String, homeId: String, away: String, awayId: String,
                     provider: String = "api-sports"): SportEvent = SportEvent(
        id, sport, "", "", null,
        Side(home, null, ProviderReference(provider, sport.provider, ReferenceKind.TEAM, homeId)),
        Side(away, null, ProviderReference(provider, sport.provider, ReferenceKind.TEAM, awayId)),
        kickoff, 15 * 60 + 30, EventStatus.SCHEDULED, null, null, null,
    )

    private fun match(game: SportEvent, names: DictionaryNames, text: String): List<Confidence> =
        StreamMatcher(listOf(game), emptyMap(), emptyMap(), names).apply {
            add(Candidate("channel", text, "programme", text, null, null, kickoff, MatchSource.GUIDE))
        }.finish().getValue(game.id).map { it.automatic }

    @Test
    fun packagedReviewedAssetMatchesLanguagesNicknamesAndKeepsCategoriesSeparate() = runBlocking(Dispatchers.IO) {
        val folder = File(instrumentation.targetContext.cacheDir, "dictionary-device-reviewed")
        try {
            val resolver = AssetPairingDictionary(DictionaryAsset({ instrumentation.targetContext.assets.open(it) }, { folder }))
            val senior = game("senior", SportType.FOOTBALL, "Finland", "1099", "Germany", "25")
            val women = game("women", SportType.FOOTBALL, "Finland W", "1771", "Germany W", "1719")
            val hockey = game("hockey", SportType.ICE_HOCKEY, "Finland", "1324", "Germany", "1325")
            val clubs = game("clubs", SportType.ICE_HOCKEY, "IFK Helsinki", "170", "Hameenlinna", "169")
            val names = resolver.resolve(listOf(senior, women, hockey, clubs))
            assertEquals(4, names.events.size)
            for (text in listOf("Suomi - Saksa", "Huuhkajat - Deutschland", "Finlande - Allemagne")) {
                assertEquals(text, listOf(Confidence.AVAILABLE), match(senior, names, text))
                assertFalse("baseline lacks reviewed translation $text", Confidence.AVAILABLE in match(senior, DictionaryNames.NONE, text))
            }
            assertEquals(listOf(Confidence.AVAILABLE), match(women, names, "Helmarit - Germany W"))
            assertEquals(listOf(Confidence.AVAILABLE), match(hockey, names, "Leijonat - Saksa"))
            assertEquals(listOf(Confidence.AVAILABLE), match(clubs, names, "HIFK - HPK"))
            for (text in listOf("Suomi U21 - Saksa U21", "Suomi W - Saksa W")) {
                assertFalse(text, Confidence.AVAILABLE in match(senior, names, text))
            }
            // The reviewed Germany B identity belongs to hockey, not football.
            assertFalse(Confidence.AVAILABLE in match(hockey, names, "Finland - Germany B"))
            val unknown = senior.copy(home = senior.home.copy(reference = senior.home.reference!!.copy(externalId = "99999999")))
            assertNull(resolver.resolve(listOf(unknown)).events.getValue(unknown.id).homeId)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun fullSizedAssetHandles512EventsAndCacheWithoutLoadingTheCatalogue() = runBlocking(Dispatchers.IO) {
        val folder = File(instrumentation.targetContext.cacheDir, "dictionary-device-capacity")
        try {
            val asset = DictionaryAsset({ instrumentation.context.assets.open("testDictionary/$it") }, { folder })
            val coldWallStart = android.os.SystemClock.elapsedRealtime()
            val coldCpuStart = android.os.Debug.threadCpuTimeNanos()
            assertEquals(true, asset.read { _, _ -> true })
            val coldCpuMs = (android.os.Debug.threadCpuTimeNanos() - coldCpuStart) / 1_000_000
            val coldWallMs = android.os.SystemClock.elapsedRealtime() - coldWallStart
            val resolver = AssetPairingDictionary(asset)
            val games = (0 until 512).map { i -> game("fixture:$i", SportType.FOOTBALL, "Northbridge $i", "${i * 2 + 1}", "Harbor $i", "${i * 2 + 2}", "fixture-provider") }
            val runtime = Runtime.getRuntime()
            runtime.gc()
            val before = runtime.totalMemory() - runtime.freeMemory()
            val names = resolver.resolve(games)
            assertFalse(resolver.lastStats.boundedFallback)
            assertEquals(512, names.events.size)
            val matcher = StreamMatcher(games, emptyMap(), emptyMap(), names)
            for (i in 0 until 512) {
                val text = "Fixture Alternate ${i * 2} - Fixture Alternate ${i * 2 + 1}"
                matcher.add(Candidate("c$i", text, "p$i", text, null, null, kickoff, MatchSource.GUIDE))
            }
            val results = matcher.finish()
            assertTrue(results.values.all { list -> list.single().automatic == Confidence.AVAILABLE })
            val cache = PairingCache(File(folder, "capacity.bin"))
            cache.write(names.revision, games, results, kickoff, names.events)
            assertEquals(results, PairingCache(File(folder, "capacity.bin")).read(names.revision, games, kickoff, names.events))
            runtime.gc()
            val retained = runtime.totalMemory() - runtime.freeMemory() - before
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "Dictionary capacity events=512 accountedBytes=${resolver.lastStats.accountedBytes} retainedBytes=$retained aliasPages=${resolver.lastStats.aliasPages} coldInstallCpuMs=$coldCpuMs coldInstallWallMs=$coldWallMs\n")
            })
            assertTrue("retained dictionary/matcher/results $retained bytes", retained <= 4L * 1024 * 1024)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun runtimeQueriesUseIndexesOnAndroidSqlite() = runBlocking(Dispatchers.IO) {
        val folder = File(instrumentation.targetContext.cacheDir, "dictionary-device-plans")
        try {
            val asset = DictionaryAsset({ instrumentation.targetContext.assets.open(it) }, { folder })
            val checked = asset.read { db, _ ->
                val queries = listOf(
                    "SELECT uuid FROM binding WHERE provider='api-sports' AND product='football' AND kind='team' AND external_id IN ('1099','25')",
                    "SELECT DISTINCT a.uuid FROM alias a CROSS JOIN identity i ON i.uuid=a.uuid WHERE a.normalised='finland' AND i.sport='football' AND i.kind='team' AND a.valid_from<=0 AND a.valid_to>=0 LIMIT 2",
                    "SELECT normalised,valid_from,valid_to FROM alias WHERE uuid='fixture' AND (normalised,valid_from,valid_to)>('',-719162,-719162) ORDER BY normalised,valid_from,valid_to LIMIT 256",
                    "SELECT base,qualified,valid_from,valid_to FROM guard WHERE sport='football' AND kind='team' AND base IN ('suomi','finland','finlande') AND (base,qualified,valid_from,valid_to)>('','',-719162,-719162) ORDER BY base,qualified,valid_from,valid_to LIMIT 256",
                    "SELECT competition,season,valid_from,valid_to FROM membership WHERE participant='fixture' AND (competition,season,valid_from,valid_to)>('','',-719162,-719162) ORDER BY competition,season,valid_from,valid_to LIMIT 256",
                )
                for (query in queries) {
                    val plan = db.rawQuery("EXPLAIN QUERY PLAN $query", null).use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString("; ")
                    }
                    assertTrue(plan, plan.contains("SEARCH"))
                    assertFalse(plan, plan.contains("SCAN"))
                    assertFalse(plan, plan.contains("TEMP B-TREE"))
                }
                queries.size
            }
            assertEquals(5, checked)
        } finally {
            folder.deleteRecursively()
        }
    }
}
