package com.streammate.tv.feature.catalogue

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.M3uCatalogueImportService
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3uCatalogueImportServiceTest {
    private lateinit var database: StreamMateDatabase

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("m3u-vod", "M3U VOD", "M3U", true, 1, 0, 1_000L),
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun importsM3uMoviesAndNamedEpisodesIntoSeparateCatalogueRooms() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 group-title="Movies",Example Movie (2024)
            http://provider.example/movie/user/pass/1.mkv
            #EXTINF:-1 group-title="Series",Example Show S01E01 - Pilot
            http://provider.example/series/user/pass/2.mkv
            #EXTINF:-1 group-title="Series",Example Show S01E02 - Second
            http://provider.example/series/user/pass/3.mkv
        """.trimIndent()
        val service = M3uCatalogueImportService(
            sourceClient = TextSource(playlist),
            parser = M3uParser(),
            dao = database.catalogueDao(),
            secretCipher = PrefixCipher,
            clock = { 2_000L },
        )
        val source = IptvSourceConfiguration(
            id = "m3u-vod",
            name = "M3U VOD",
            type = IptvSourceType.M3U,
            importScope = IptvImportScope.VOD,
            m3uUrl = "http://provider.example/get.m3u",
        )

        val summary = service.refresh(source)
        val repository = CatalogueRepository(database.catalogueDao())
        val movie = repository.observeMovies().first().single()
        val series = repository.observeSeries().first().single()
        val episodes = repository.observeEpisodes(series.sourceId, series.seriesId).first()

        assertEquals(1, summary.movies)
        assertEquals(1, summary.series)
        assertEquals("Example Movie (2024)", movie.name)
        assertEquals("Example Show", series.name)
        assertEquals(listOf("Pilot", "Second"), episodes.map { it.name })
        assertTrue(requireNotNull(repository.playable(movie.contentKey)).encryptedStreamUrl.startsWith("encrypted:"))
    }
}

private class TextSource(private val playlist: String) : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
        ByteArrayInputStream(playlist.toByteArray()).use { block(it) }
}

private object PrefixCipher : SecretCipher {
    override fun encrypt(plainText: String): String = "encrypted:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("encrypted:")
}
