package com.sohva.tv.feature.sport

import android.database.sqlite.SQLiteDatabase
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryAssetTest {
    @get:Rule val directory = TemporaryFolder()
    private val day = LocalDate.now(ZoneOffset.UTC)
    private val kickOff = day.atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val game = SportEvent("fixture:1", SportType.FOOTBALL, "", "Fixture Cup", null,
        Side("Finland", null, ref("101")), Side("Germany", null, ref("102")), kickOff, 15 * 60 + 30, EventStatus.SCHEDULED, null, null, null)
    private val install get() = File(directory.root, "installed")
    private val revision = "a".repeat(64)

    private fun ref(id: String, provider: String = "fixture-provider", product: String = "football"): ProviderReference =
        ProviderReference(provider, product, ReferenceKind.TEAM, id)

    private fun fixture(): Pair<ByteArray, ByteArray> {
        val file = directory.newFile()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "tools/team_dictionary/schema.sql").isFile }
            File(root, "tools/team_dictionary/schema.sql").readText().split(';').filter { it.isNotBlank() }.forEach(db::execSQL)
            for ((id, name) in listOf("home" to "suomi", "away" to "deutschland")) {
                db.execSQL("INSERT INTO identity VALUES (?, 'football', 'team', 'senior-men')", arrayOf(id))
                db.execSQL("INSERT INTO alias VALUES (?, ?, -719162, 2932896)", arrayOf(id, name))
            }
            for ((id, identity) in listOf("101" to "home", "102" to "away")) {
                db.execSQL("INSERT INTO binding VALUES ('fixture-provider','football','team',?,?)", arrayOf(id, identity))
                db.execSQL("INSERT INTO binding VALUES ('fixture-second','football','team',?,?)", arrayOf(id, identity))
            }
            db.execSQL("INSERT INTO alias VALUES ('home','finland',-719162,2932896)")
            db.execSQL("INSERT INTO alias VALUES ('away','germany',-719162,2932896)")
            val date = day.toEpochDay()
            db.execSQL("INSERT INTO alias VALUES ('home','old town',?,?)", arrayOf(date - 60, date - 30))
            db.execSQL("INSERT INTO alias VALUES ('home','old town',?,?)", arrayOf(date + 30, date + 60))
            db.execSQL("INSERT INTO alias VALUES ('home','united',-719162,2932896)")
            db.execSQL("INSERT INTO alias VALUES ('away','united',-719162,2932896)")
            db.execSQL("INSERT INTO collision VALUES ('football','team','finland')")
            for (base in listOf("finland", "suomi", "germany", "deutschland")) {
                db.execSQL("INSERT INTO guard VALUES ('football','team',?,?, -719162,2932896)", arrayOf(base, "$base u21"))
            }
        }
        return manifest(file.readBytes()) to file.readBytes()
    }

    private fun manifest(database: ByteArray, format: Int = 1, hash: String? = null): ByteArray = JSONObject().apply {
        put("format", format)
        put("normalisation", 1)
        put("revision", revision)
        put("bytes", database.size)
        put("sha256", hash ?: MessageDigest.getInstance("SHA-256").digest(database).joinToString("") { "%02x".format(it) })
    }.toString().toByteArray()

    private fun asset(manifest: ByteArray, database: ByteArray, opened: MutableList<String> = ArrayList()): DictionaryAsset = DictionaryAsset(
        { name -> opened += name; ByteArrayInputStream(if (name.endsWith(".json")) manifest else database) }, { install },
    )

    private fun match(names: DictionaryNames, title: String): Confidence? = StreamMatcher(listOf(game), emptyMap(), emptyMap(), names).apply {
        add(Candidate("fixture:channel", "Fixture", "fixture:programme", title, null, null, 0, MatchSource.NAME))
    }.finish().getValue(game.id).singleOrNull()?.automatic

    @Test
    fun identitiesFromTwoProvidersSelectTheSameAliasesAndNothingLoadsBeforeUse() = runBlocking {
        val (manifest, database) = fixture()
        val opened = ArrayList<String>()
        val resolver = AssetPairingDictionary(asset(manifest, database, opened))
        assertTrue(opened.isEmpty())
        assertFalse(install.exists())
        val first = resolver.resolve(listOf(game))
        assertEquals(Confidence.AVAILABLE, match(first, "Suomi - Deutschland"))
        val second = game.copy(home = game.home.copy(reference = ref("101", "fixture-second")))
        assertEquals(first.events[game.id]?.homeId, resolver.resolve(listOf(second)).events[game.id]?.homeId)
        val before = opened.count { it.endsWith(".db") }
        resolver.resolve(listOf(game))
        assertEquals("installed revision is reused", before, opened.count { it.endsWith(".db") })
    }

    @Test
    fun unknownExplicitReferencesNeverAcquireAnIdentityByName() = runBlocking {
        val (manifest, database) = fixture()
        val resolver = AssetPairingDictionary(asset(manifest, database))
        val unknown = game.copy(home = game.home.copy(reference = ref("999")))
        val names = resolver.resolve(listOf(unknown))
        assertNull(names.events[game.id]?.homeId)
        assertTrue(names.events.getValue(game.id).home.isEmpty())
        assertNull(match(names, "Suomi - Deutschland"))
    }

    @Test
    fun emptyTodayDoesNotReadOrInstallAnAsset() = runBlocking {
        val resolver = AssetPairingDictionary(DictionaryAsset({ throw AssertionError("unnecessary asset read") }, { throw AssertionError("unnecessary directory read") }))
        assertEquals(DictionaryNames.NONE, resolver.resolve(emptyList()))
    }

    @Test
    fun missingUnsupportedOrCorruptAssetsReturnTheConservativeFallback() = runBlocking {
        val missing = DictionaryAsset({ throw FileNotFoundException() }, { install })
        assertEquals(DictionaryNames.NONE, AssetPairingDictionary(missing).resolve(listOf(game)))
        val (manifest, database) = fixture()
        assertEquals(DictionaryNames.NONE, AssetPairingDictionary(asset(manifest(database, format = 99), database)).resolve(listOf(game)))
        assertEquals(DictionaryNames.NONE, AssetPairingDictionary(asset(manifest(database, hash = "b".repeat(64)), database)).resolve(listOf(game)))
        assertFalse(File(install, "$revision.db").exists())
        assertFalse(File(install, "$revision.tmp").exists())
    }

    @Test
    fun interruptedInstallLeavesNoVerifiedOrTemporaryFileAndCanRetry() = runBlocking {
        val (manifest, database) = fixture()
        val interrupted = DictionaryAsset({ name ->
            if (name.endsWith(".json")) ByteArrayInputStream(manifest) else object : InputStream() {
                override fun read(): Int = throw CancellationException("fixture cancellation")
            }
        }, { install })
        try {
            AssetPairingDictionary(interrupted).resolve(listOf(game))
            throw AssertionError("cancellation was swallowed")
        } catch (expected: CancellationException) {
            assertFalse(File(install, "$revision.tmp").exists())
            assertFalse(File(install, "$revision.verified").exists())
        }
        assertEquals(Confidence.AVAILABLE, match(AssetPairingDictionary(asset(manifest, database)).resolve(listOf(game)), "Suomi - Deutschland"))
    }

    @Test
    fun datedAliasesSharedNamesAndAbsentYouthFixturesRemainConservative() = runBlocking {
        val (manifest, database) = fixture()
        val resolver = AssetPairingDictionary(asset(manifest, database))
        val names = resolver.resolve(listOf(game))
        assertNull(match(names, "Old Town - Deutschland"))
        assertNull(match(names, "United - United"))
        assertNull(match(names, "Suomi U21 - Deutschland U21"))
        val legacy = game.copy(home = game.home.copy(reference = null), away = game.away.copy(reference = null))
        val oldNames = resolver.resolve(listOf(legacy)).events.getValue(game.id)
        assertNull("known collision survives the small slice", oldNames.homeId)
        assertEquals("away", oldNames.awayId)
    }

    @Test
    fun aggregateBudgetFallsBackAsAWholeForALargeActiveSet() = runBlocking {
        val (manifest, database) = fixture()
        val resolver = AssetPairingDictionary(asset(manifest, database))
        val events = (0 until 2_048).map { game.copy(id = "fixture:$it") }
        val names = resolver.resolve(events)
        assertTrue(resolver.lastStats.boundedFallback)
        assertTrue(names.events.isEmpty())
        assertTrue(names.revision.endsWith(":bounded"))
        assertTrue(resolver.lastStats.accountedBytes < 2 * 1024 * 1024 + 1_024)
    }

    @Test
    fun sharedDatedCupAndLeagueMembershipsSupportLegacyContextWithoutBecomingPermanent() = runBlocking {
        val (_, original) = fixture()
        val path = directory.newFile("memberships.db").apply { writeBytes(original) }
        val today = day.toEpochDay()
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            for ((id, alias) in listOf("cup" to "fixture cup", "league" to "fixture league", "expired" to "old league", "permanent" to "permanent league")) {
                db.execSQL("INSERT INTO identity VALUES (?, 'football', 'competition', 'open')", arrayOf(id))
                db.execSQL("INSERT INTO alias VALUES (?, ?, -719162, 2932896)", arrayOf(id, alias))
                for (participant in listOf("home", "away")) {
                    val from = if (id == "permanent") -719162 else today - 100
                    val to = when (id) { "expired" -> today - 1; "permanent" -> 2932896; else -> today + 100 }
                    db.execSQL("INSERT INTO membership VALUES (?, ?, 'fixture-season', ?, ?)", arrayOf<Any>(participant, id, from, to))
                }
            }
        }
        val bytes = path.readBytes()
        val resolver = AssetPairingDictionary(asset(manifest(bytes), bytes))
        val legacyCompetition = game.copy(competition = "Unknown fixture competition")
        val names = resolver.resolve(listOf(legacyCompetition))
        assertEquals(setOf("cup", "league"), names.events.getValue(game.id).supportingCompetitions)
        assertEquals(setOf("cup@$today", "league@$today"), names.competitions.keys)
        val explicitUnknown = legacyCompetition.copy(competitionReference = ProviderReference("fixture-provider", "football", ReferenceKind.COMPETITION, "999"))
        assertTrue(resolver.resolve(listOf(explicitUnknown)).events.getValue(game.id).supportingCompetitions.isEmpty())
    }
}
