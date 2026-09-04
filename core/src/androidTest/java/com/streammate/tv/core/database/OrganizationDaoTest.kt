package com.streammate.tv.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizationDaoTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), StreamMateDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrationPreservesCustomizationsAndFinnishGroupKeys() {
        val name = "organization-migration-22"
        helper.createDatabase(name, 22).apply {
            execSQL("INSERT INTO channel_preferences(channelId,sourceId,customName,customGroupTitle,hidden,sortOrder,manualXmltvChannelId,updatedAtEpochMillis) VALUES('one:c','one','My channel','ÄÄNI',1,3,'epg',1)")
            execSQL("INSERT INTO vod_movies(sourceId,snapshotId,movieId,name,normalizedName,categoryName,categoryKey,posterUrl,encryptedStreamUrl,year,rating,plot) VALUES('one','old','m','Film','film','ÄÄNI','ääni',NULL,'encrypted',2000,NULL,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 23, true, StreamMateDatabase.MIGRATION_22_23).use { db ->
            db.query("SELECT customName,hidden,sortOrder,manualXmltvChannelId,customOrganizationGroupKey FROM channel_preferences").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("My channel", c.getString(0)); assertEquals(1, c.getInt(1))
                assertEquals(3, c.getInt(2)); assertEquals("epg", c.getString(3)); assertEquals("name:ääni", c.getString(4))
            }
            db.query("SELECT organizationGroupKey,organizationNameKey FROM vod_movies").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("name:ääni", c.getString(0)); assertEquals("name:ääni", c.getString(1))
            }
        }
    }

    @Test fun sourceScopedVisibilityCountsAndPlaybackLookupAgree() = runBlocking {
        val db = database()
        try {
            seed(db)
            val dao = db.organizationDao()
            dao.change(listOf(OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "one", "id:10"), enabled = false, changeEnabled = true)))
            assertEquals(listOf("two"), db.catalogueDao().observeMovieCards().first().map { it.sourceId })
            assertEquals(1, db.catalogueDao().observeMovieCount().first())
            // A current playback session can still resolve its hidden movie.
            assertNotNull(db.catalogueDao().activeMovie("one", "a"))
            dao.change(listOf(OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "one", "id:10"), enabled = true, changeEnabled = true)))
            assertEquals(2, db.catalogueDao().observeMovieCards().first().size)
        } finally { db.close() }
    }

    @Test fun aliasMergePreservesGlobalHideAndMemberRank() = runBlocking {
        val db = database()
        try {
            seed(db)
            val dao = db.organizationDao()
            dao.registerFilmAliases(listOf(listOf("vod:movie:one:a", "work:first"), listOf("vod:movie:two:a", "work:second")))
            val first = dao.aliases().first { it.alias == "vod:movie:one:a" }.identity
            val second = dao.aliases().first { it.alias == "vod:movie:two:a" }.identity
            dao.change(listOf(
                OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, itemKey = second), enabled = false, changeEnabled = true),
                OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "two", "id:10", second), position = 3, changePosition = true),
            ))
            dao.registerFilmAliases(listOf(listOf("vod:movie:one:a", "vod:movie:two:a", "work:tmdb:1")))
            val aliases = dao.aliases().associate { it.alias to it.identity }
            assertEquals(aliases["vod:movie:one:a"], aliases["vod:movie:two:a"])
            assertTrue(db.catalogueDao().observeMovieCards().first().isEmpty())
            assertTrue(dao.rules().any { it.position == 3L && it.itemKey == aliases["vod:movie:one:a"] })
            val snapshot = dao.snapshot()
            dao.restore(OrganizationSnapshot())
            dao.restore(snapshot)
            assertEquals(snapshot, dao.snapshot())
        } finally { db.close() }
    }

    @Test fun partialUpdatesKeepUnrelatedFieldsAndSnapshotRefreshKeepsOverrides() = runBlocking {
        val db = database()
        try {
            seed(db)
            val dao = db.organizationDao()
            val key = OrganizationKey(LibraryRoom.MOVIES, "one", "id:10", "vod:movie:one:a")
            dao.change(listOf(OrganizationChange(key, enabled = false, changeEnabled = true)))
            dao.change(listOf(OrganizationChange(key, position = 7, changePosition = true)))
            assertEquals(false, dao.rules().first().enabled)
            db.catalogueDao().upsertMovies(listOf(movie("one", "new").copy(categoryName = "Renamed")))
            db.catalogueDao().activateCatalogueSnapshot("one", "new", 1, 2)
            assertEquals(7L, dao.rules().first().position)
            assertEquals(listOf("two"), db.catalogueDao().observeMovieCards().first().map { it.sourceId })
        } finally { db.close() }
    }

    @Test fun firstAliasRegistrationMigratesExistingRawCopyPreferences() = runBlocking {
        val db = database()
        try {
            seed(db)
            val dao = db.organizationDao()
            dao.change(listOf(OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, itemKey = "vod:movie:one:a"), enabled = false, changeEnabled = true)))
            dao.registerFilmAliases(listOf(listOf("vod:movie:one:a", "vod:movie:two:a", "work:film")))
            assertEquals(0, db.catalogueDao().observeMovieCount().first())
            assertNotNull(db.catalogueDao().activeMovie("two", "a"))
        } finally { db.close() }
    }

    @Test fun disabledParentsApplyToHistorySearchGenresAndNewImports() = runBlocking {
        val db = database()
        try {
            seed(db)
            db.metadataDao().replaceGenres("vod:movie:one:a", listOf(CatalogueGenreEntity("vod:movie:one:a", "drama")))
            db.organizationDao().change(listOf(
                OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "one", "id:10"), enabled = false, changeEnabled = true),
                OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "two", "id:10"), enabled = false, changeEnabled = true),
            ))
            assertTrue(db.catalogueDao().observeMovieCardsMatching("Film").first().isEmpty())
            assertTrue(db.catalogueDao().observeMovieGenreCounts().first().isEmpty())
            assertTrue(db.catalogueDao().observeMovieCategories().first().isEmpty())
            db.catalogueDao().upsertMovies(listOf(movie("one", "next").copy(movieId = "new")))
            db.catalogueDao().activateCatalogueSnapshot("one", "next", 1, 3)
            assertEquals(0, db.catalogueDao().observeMovieCount().first())
            assertEquals(2, db.organizationDao().observeMovies().first().size)
        } finally { db.close() }
    }

    @Test fun customizationSurvivesDatabaseReopenAndSourceDisableEnable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "organization-restart-" + java.util.UUID.randomUUID()
        var db = Room.databaseBuilder(context, StreamMateDatabase::class.java, name).build()
        try {
            seed(db)
            db.organizationDao().change(listOf(OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "one", "id:10"), enabled = false, changeEnabled = true)))
            db.close()
            db = Room.databaseBuilder(context, StreamMateDatabase::class.java, name).build()
            assertEquals(listOf("two"), db.catalogueDao().observeMovieCards().first().map { it.sourceId })
            db.guideDao().upsertSourceState(IptvSourceStateEntity("two", "two", "xtream", false, 1, 0, 3))
            assertEquals(0, db.catalogueDao().observeMovieCount().first())
            db.guideDao().upsertSourceState(IptvSourceStateEntity("two", "two", "xtream", true, 1, 0, 4))
            assertEquals(1, db.catalogueDao().observeMovieCount().first())
            assertEquals(false, db.organizationDao().rules().single().enabled)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun concurrentImportAndManualMovePreserveNewItemsAndOverrides() = runBlocking {
        val db = database()
        try {
            seed(db)
            kotlinx.coroutines.coroutineScope {
                val move = launch {
                    db.organizationDao().change(listOf(
                        OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "one", "id:10"), sort = LibrarySort.MANUAL, changeSort = true),
                        OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, "one", "id:10", "vod:movie:one:a"), position = 2, changePosition = true),
                    ))
                }
                val refresh = launch {
                    db.catalogueDao().upsertMovies(listOf(movie("one", "fresh"), movie("one", "fresh").copy(movieId = "new")))
                    db.catalogueDao().activateCatalogueSnapshot("one", "fresh", 2, 4)
                }
                move.join(); refresh.join()
            }
            assertEquals(3, db.catalogueDao().observeMovieCards().first().size)
            assertEquals(2L, db.organizationDao().rules().first { it.itemKey.isNotEmpty() }.position)
        } finally { db.close() }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, StreamMateDatabase::class.java).build()
    private suspend fun seed(db: StreamMateDatabase) {
        for (source in listOf("one", "two")) {
            db.guideDao().upsertSourceState(IptvSourceStateEntity(source, source, "xtream", true, 1, 0, 1))
            db.catalogueDao().upsertMovies(listOf(movie(source, "old")))
            db.catalogueDao().activateCatalogueSnapshot(source, "old", 1, 1)
        }
    }
    private fun movie(source: String, snapshot: String) = VodMovieEntity(source, snapshot, "a", "Film", "film", "Films", posterUrl = null, encryptedStreamUrl = "encrypted", year = 2000, rating = "7", plot = null, organizationGroupKey = "id:10")
}
