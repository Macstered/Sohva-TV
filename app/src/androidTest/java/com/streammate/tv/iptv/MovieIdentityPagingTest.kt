package com.streammate.tv.iptv

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.iptv.repository.OrganizationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The film-identity pass reads the catalogue in pages so a large provider's
 * catalogue never sits in memory whole. Copies of one film on different pages
 * must still end up as one identity.
 */
@RunWith(AndroidJUnit4::class)
class MovieIdentityPagingTest {

    @Test
    fun copiesOnDifferentPagesShareOneIdentity() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, StreamMateDatabase::class.java).build()
        try {
            for (source in listOf("one", "two", "three")) {
                db.guideDao().upsertSourceState(IptvSourceStateEntity(source, source, "xtream", true, 1, 0, 1))
                db.catalogueDao().upsertMovies(listOf(
                    movie(source, "1", "Heat", 1995),
                    movie(source, "2", "Alien", 1979),
                ))
                db.catalogueDao().activateCatalogueSnapshot(source, "snap", 1, 2)
            }
            val repository = OrganizationRepository(db.organizationDao())

            // One film per page: every copy of Heat lands on its own page.
            repository.registerAllMovieIdentities(pageSize = 1)

            val aliases = listOf("vod:movie:one:1", "vod:movie:two:1", "vod:movie:three:1", "vod:movie:one:2", "vod:movie:three:2")
            val identities = db.organizationDao().identities(aliases)
            assertEquals(aliases.toSet(), identities.keys)
            assertEquals(1, listOf("vod:movie:one:1", "vod:movie:two:1", "vod:movie:three:1").map(identities::getValue).toSet().size)
            assertEquals(1, listOf("vod:movie:one:2", "vod:movie:three:2").map(identities::getValue).toSet().size)
            assertEquals(2, identities.values.toSet().size)

            // A second pass finds everything registered and changes nothing.
            repository.registerAllMovieIdentities(pageSize = 4)
            assertEquals(identities, db.organizationDao().identities(aliases))
        } finally {
            db.close()
        }
    }

    private fun movie(source: String, id: String, name: String, year: Int) = VodMovieEntity(
        source, "snap", id, name, name.lowercase(), "Films", posterUrl = null, encryptedStreamUrl = "encrypted",
        year = year, rating = null, plot = null, organizationGroupKey = "id:$id",
    )
}
