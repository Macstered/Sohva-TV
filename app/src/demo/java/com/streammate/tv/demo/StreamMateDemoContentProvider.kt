package com.streammate.tv.demo

import android.content.Context
import com.streammate.tv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.AppLocale
import com.streammate.tv.app.DemoContentProvider
import com.streammate.tv.app.StartupScreen
import com.streammate.tv.core.database.CatalogueGenreEntity
import com.streammate.tv.core.database.IptvChannelEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.PlaybackProgressEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.TvProgrammeEntity
import com.streammate.tv.core.database.VodEpisodeEntity
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.database.VodSeriesEntity
import com.streammate.tv.core.database.XmlTvChannelEntity
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.model.FootballIncident
import com.streammate.tv.core.model.FootballIncidentKind
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.SportsCompetition
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.core.security.MetadataSettings
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.core.security.SportsApiSettings
import com.streammate.tv.iptv.m3u.ChannelNameNormalizer
import com.streammate.tv.sports.repository.FootballIncidentsSnapshot
import com.streammate.tv.sports.repository.SportsEventsSnapshot
import com.streammate.tv.sports.repository.SportsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class StreamMateDemoContentProvider : DemoContentProvider {
    private val demoSportsRepository = DemoSportsRepository()

    override val sportsRepository: SportsRepository = demoSportsRepository

    override suspend fun seed(
        context: Context,
        database: StreamMateDatabase,
        secretCipher: SecretCipher,
        secretSettingsStore: SecretSettingsStore,
        preferencesRepository: AppPreferencesRepository,
    ) {
        // Screenshot output is intentionally stable even when the Shield's
        // system language changes between test sessions.
        AppLocale.apply(context, "en")
        val now = System.currentTimeMillis()
        val snapshotId = DEMO_SNAPSHOT_ID
        val source = IptvSourceConfiguration(
            id = DEMO_SOURCE_ID,
            name = "Northstar Demo Library",
            type = IptvSourceType.M3U,
            enabled = true,
            connectionLimit = 4,
            priority = 100,
            importScope = IptvImportScope.BOTH,
            m3uUrl = "https://demo.invalid/northstar/playlist.m3u",
            xmlTvUrl = "https://demo.invalid/northstar/guide.xml",
        )
        secretSettingsStore.saveSources(listOf(source))
        secretSettingsStore.saveMetadataSettings(MetadataSettings())
        secretSettingsStore.saveSportsApiSettings(SportsApiSettings())
        secretSettingsStore.clearParentalPin()

        val channelIds = demoChannels.map { globalChannelId(it.id) }
        preferencesRepository.restore(
            AppPreferences(
                favouriteEventIds = setOf(DemoSportsRepository.LIVE_EVENT_ID),
                favouriteChannelIds = setOf(channelIds[0], channelIds[6]),
                recentChannelIds = listOf(channelIds[6], channelIds[4], channelIds[0]),
                lastChannelId = channelIds[6],
                lastGuideSourceId = DEMO_SOURCE_ID,
                startupScreen = StartupScreen.HOME,
                followedSports = setOf(SportType.FOOTBALL, SportType.ICE_HOCKEY),
                followedCompetitionKeys = setOf(
                    SportsCompetition.preferenceKey(SportType.FOOTBALL, DEMO_FOOTBALL_LEAGUE_ID),
                    SportsCompetition.preferenceKey(SportType.FOOTBALL, DEMO_FOOTBALL_CUP_ID),
                    SportsCompetition.preferenceKey(SportType.ICE_HOCKEY, DEMO_HOCKEY_LEAGUE_ID),
                ),
            ),
        )

        val guideDao = database.guideDao()
        guideDao.upsertSourceState(
            IptvSourceStateEntity(
                sourceId = source.id,
                name = source.name,
                type = source.type.name,
                enabled = source.enabled,
                connectionLimit = source.connectionLimit,
                priority = source.priority,
                updatedAtEpochMillis = now,
            ),
        )

        val channelEntities = demoChannels.mapIndexed { index, channel ->
            IptvChannelEntity(
                sourceId = DEMO_SOURCE_ID,
                snapshotId = snapshotId,
                channelId = globalChannelId(channel.id),
                tvgId = channel.id,
                name = channel.name,
                normalizedName = ChannelNameNormalizer.normalize(channel.name),
                groupTitle = channel.group,
                logoUrl = drawableUrl(context, channel.logoResource),
                encryptedStreamUrl = secretCipher.encrypt("https://demo.invalid/live/${channel.id}.m3u8"),
                userAgent = null,
                referrer = null,
                lastSeenEpochMillis = now,
                playlistOrder = index,
            )
        }
        guideDao.upsertChannels(channelEntities)
        guideDao.upsertXmlTvChannels(
            demoChannels.map { channel ->
                XmlTvChannelEntity(
                    sourceId = DEMO_SOURCE_ID,
                    snapshotId = snapshotId,
                    xmltvChannelId = channel.id,
                    displayName = channel.name,
                    iconUrl = drawableUrl(context, channel.logoResource),
                )
            },
        )
        val programmes = demoProgrammes(now)
        guideDao.upsertProgrammes(programmes)
        guideDao.activatePlaylistSnapshot(DEMO_SOURCE_ID, snapshotId, channelEntities.size, now)
        guideDao.activateEpgSnapshot(DEMO_SOURCE_ID, snapshotId, programmes.size, now)

        val catalogueDao = database.catalogueDao()
        val moviePosterUrls = listOf(
            drawableUrl(context, R.drawable.demo_movie_signal),
            drawableUrl(context, R.drawable.demo_movie_lighthouse),
        )
        val seriesPosterUrls = listOf(
            drawableUrl(context, R.drawable.demo_series_harbor),
            drawableUrl(context, R.drawable.demo_series_north),
        )
        val movies = demoMovies.mapIndexed { index, movie ->
            VodMovieEntity(
                sourceId = DEMO_SOURCE_ID,
                snapshotId = snapshotId,
                movieId = movie.id,
                name = movie.name,
                normalizedName = ChannelNameNormalizer.normalize(movie.name),
                categoryName = movie.category,
                posterUrl = moviePosterUrls[index % moviePosterUrls.size],
                encryptedStreamUrl = secretCipher.encrypt("https://demo.invalid/movies/${movie.id}.mp4"),
                year = movie.year,
                rating = movie.rating,
                plot = movie.plot,
            )
        }
        val series = demoSeries.mapIndexed { index, show ->
            VodSeriesEntity(
                sourceId = DEMO_SOURCE_ID,
                snapshotId = snapshotId,
                seriesId = show.id,
                name = show.name,
                normalizedName = ChannelNameNormalizer.normalize(show.name),
                categoryName = show.category,
                posterUrl = seriesPosterUrls[index % seriesPosterUrls.size],
                backdropUrl = seriesPosterUrls[(index + 1) % seriesPosterUrls.size],
                year = show.year,
                rating = show.rating,
                plot = show.plot,
            )
        }
        catalogueDao.upsertMovies(movies)
        catalogueDao.upsertSeries(series)
        demoSeries.forEachIndexed { showIndex, show ->
            catalogueDao.replaceSeriesEpisodes(
                sourceId = DEMO_SOURCE_ID,
                seriesId = show.id,
                episodes = (1..4).map { episodeNumber ->
                    val episodeId = "${show.id}-s1e$episodeNumber"
                    VodEpisodeEntity(
                        sourceId = DEMO_SOURCE_ID,
                        seriesId = show.id,
                        episodeId = episodeId,
                        seasonNumber = 1,
                        episodeNumber = episodeNumber,
                        name = episodeNames[(showIndex + episodeNumber - 1) % episodeNames.size],
                        encryptedStreamUrl = secretCipher.encrypt(
                            "https://demo.invalid/series/$episodeId.mp4",
                        ),
                        plot = episodePlots[(showIndex + episodeNumber - 1) % episodePlots.size],
                        durationSeconds = 2_700 + episodeNumber * 180,
                        thumbnailUrl = seriesPosterUrls[showIndex % seriesPosterUrls.size],
                    )
                },
            )
        }
        catalogueDao.activateCatalogueSnapshot(
            DEMO_SOURCE_ID,
            snapshotId,
            movies.size + series.size,
            now,
        )

        database.metadataDao().upsertGenres(
            buildList {
                demoMovies.forEach { movie -> add(CatalogueGenreEntity(movieContentKey(movie.id), movie.genre.wireValue)) }
                demoSeries.forEach { show -> add(CatalogueGenreEntity(seriesContentKey(show.id), show.genre.wireValue)) }
            },
        )
        catalogueDao.upsertProgress(
            PlaybackProgressEntity(
                contentKey = movieContentKey("signal-at-dawn"),
                sourceId = DEMO_SOURCE_ID,
                contentType = "movie",
                itemId = "signal-at-dawn",
                positionMillis = 43 * 60_000L,
                durationMillis = 112 * 60_000L,
                completed = false,
                lastWatchedEpochMillis = now - 25 * 60_000L,
                workKey = movieContentKey("signal-at-dawn"),
            ),
        )
        catalogueDao.upsertProgress(
            PlaybackProgressEntity(
                contentKey = episodeContentKey("harbor-9-s1e2"),
                sourceId = DEMO_SOURCE_ID,
                contentType = "episode",
                itemId = "harbor-9-s1e2",
                positionMillis = 18 * 60_000L,
                durationMillis = 49 * 60_000L,
                completed = false,
                lastWatchedEpochMillis = now - 2 * 60 * 60_000L,
            ),
        )

        demoSportsRepository.updateArtwork(
            summit = drawableUrl(context, R.drawable.demo_mark_summit),
            aurora = drawableUrl(context, R.drawable.demo_mark_northstar),
            harbour = drawableUrl(context, R.drawable.demo_mark_meridian),
            frostholm = drawableUrl(context, R.drawable.demo_mark_pulse),
        )
    }

    override fun playbackArtworkUrl(context: Context): String =
        drawableUrl(context, R.drawable.demo_live_football)

    private fun demoProgrammes(now: Long): List<TvProgrammeEntity> {
        val hourStart = DemoTime.currentHourStart(now)
        return buildList {
            demoChannels.forEachIndexed { channelIndex, channel ->
                for (slot in -3..7) {
                    val start = hourStart + slot * HOUR_MILLIS
                    val title = when {
                        channel.id == "summit-sports" && slot == 0 -> "Aurora City vs Harbour United"
                        channel.id == "summit-sports" && slot == 2 -> "Northbridge FC vs Solstice Rovers"
                        channel.id == "summit-sports" && slot == 4 -> "Frostholm Lynx vs Glacier Bay"
                        else -> channel.programmes[(slot + channelIndex + 30) % channel.programmes.size]
                    }
                    add(
                        TvProgrammeEntity(
                            sourceId = DEMO_SOURCE_ID,
                            snapshotId = DEMO_SNAPSHOT_ID,
                            programmeId = "${channel.id}-${start / HOUR_MILLIS}",
                            xmltvChannelId = channel.id,
                            startEpochMillis = start,
                            stopEpochMillis = start + HOUR_MILLIS,
                            title = title,
                            subtitle = if (slot == 0) "Now on ${channel.name}" else null,
                            description = programmeDescriptions[(slot + channelIndex + 30) % programmeDescriptions.size],
                            categories = channel.group,
                        ),
                    )
                }
            }
        }
    }

    private fun drawableUrl(context: Context, resourceId: Int): String =
        "android.resource://${context.packageName}/$resourceId"

    private fun globalChannelId(localId: String): String = "$DEMO_SOURCE_ID:$localId"
    private fun movieContentKey(movieId: String): String = "vod:movie:$DEMO_SOURCE_ID:$movieId"
    private fun episodeContentKey(episodeId: String): String = "vod:episode:$DEMO_SOURCE_ID:$episodeId"
    private fun seriesContentKey(seriesId: String): String = "series:$DEMO_SOURCE_ID:$seriesId"

    private data class DemoChannel(
        val id: String,
        val name: String,
        val group: String,
        val logoResource: Int,
        val programmes: List<String>,
    )

    private data class DemoTitle(
        val id: String,
        val name: String,
        val category: String,
        val genre: CatalogueGenre,
        val year: Int,
        val rating: String,
        val plot: String,
    )

    private companion object {
        const val DEMO_SOURCE_ID = "demo-northstar"
        const val DEMO_SNAPSHOT_ID = "demo-current"
        const val HOUR_MILLIS = 60 * 60_000L
        const val DEMO_FOOTBALL_LEAGUE_ID = "demo-premier"
        const val DEMO_FOOTBALL_CUP_ID = "demo-cup"
        const val DEMO_HOCKEY_LEAGUE_ID = "demo-ice"

        val demoChannels = listOf(
            DemoChannel("northstar-one", "Northstar One", "General", R.drawable.demo_mark_northstar, listOf("Morning Current", "Cityline Today", "Studio Eleven", "The Late Edition")),
            DemoChannel("meridian-two", "Meridian Two", "General", R.drawable.demo_mark_meridian, listOf("Brightside Kitchen", "Hidden Routes", "Open Studio", "Night Stories")),
            DemoChannel("pulse-news", "Pulse News", "News", R.drawable.demo_mark_pulse, listOf("Pulse at the Hour", "Market Window", "The Civic Desk", "World Briefing")),
            DemoChannel("vista-nature", "Vista Nature", "Documentary", R.drawable.demo_mark_northstar, listOf("Wild Frontiers", "Ocean Atlas", "Quiet Forests", "Planet Workshop")),
            DemoChannel("ember-cinema", "Ember Cinema", "Movies", R.drawable.demo_mark_pulse, listOf("Signal at Dawn", "The Last Lighthouse", "Glass Horizon", "Midnight Orchard")),
            DemoChannel("orbit-kids", "Orbit Kids", "Family", R.drawable.demo_mark_meridian, listOf("Junior Inventors", "Cloudberry Club", "Pocket Planets", "Story Train")),
            DemoChannel("summit-sports", "Summit Sports", "Sports", R.drawable.demo_mark_summit, listOf("Goal Line Live", "Summit Replay", "Ice Desk", "Matchday Review")),
            DemoChannel("harbor-local", "Harbor Local", "Local", R.drawable.demo_mark_meridian, listOf("Harbor Morning", "Community Table", "Coastline Weather", "After Eight")),
        )

        val demoMovies = listOf(
            DemoTitle("signal-at-dawn", "Signal at Dawn", "Premieres", CatalogueGenre.SCIENCE_FICTION, 2026, "8.1", "A radio astronomer follows an impossible transmission into the mountains before sunrise."),
            DemoTitle("last-lighthouse", "The Last Lighthouse", "Premieres", CatalogueGenre.MYSTERY, 2025, "7.7", "A cartographer returns to an abandoned beacon and finds its lamp has begun signalling inland."),
            DemoTitle("paper-moons", "Paper Moons", "Family", CatalogueGenre.FAMILY, 2024, "7.4", "Two siblings build a cardboard observatory and discover the whole neighbourhood wants to help."),
            DemoTitle("emberline", "Emberline", "Premieres", CatalogueGenre.THRILLER, 2026, "7.9", "A night-train engineer races a wildfire through a valley cut off from the outside world."),
            DemoTitle("atlas-of-rain", "Atlas of Rain", "Documentaries", CatalogueGenre.DOCUMENTARY, 2023, "8.4", "A patient journey through the people and landscapes shaped by the world's monsoon routes."),
            DemoTitle("parallel-summer", "Parallel Summer", "Family", CatalogueGenre.COMEDY, 2025, "7.2", "Old friends accidentally book the same lake house and try to divide one holiday in two."),
            DemoTitle("midnight-orchard", "Midnight Orchard", "Premieres", CatalogueGenre.DRAMA, 2024, "7.8", "An estranged family gathers for one final harvest under an unusually bright autumn moon."),
            DemoTitle("glass-horizon", "Glass Horizon", "Premieres", CatalogueGenre.ADVENTURE, 2026, "8.0", "A rookie pilot charts a path across a frozen sea that reflects a second sky."),
            DemoTitle("northbound", "Northbound", "Documentaries", CatalogueGenre.DOCUMENTARY, 2022, "8.2", "Four seasons aboard the small trains that connect remote northern communities."),
            DemoTitle("echoes-in-blue", "Echoes in Blue", "Premieres", CatalogueGenre.ROMANCE, 2025, "7.6", "A sound designer and a marine biologist meet while recording an endangered reef."),
            DemoTitle("quiet-engine", "The Quiet Engine", "Family", CatalogueGenre.ANIMATION, 2024, "7.5", "A tiny maintenance robot leaves its depot to return a lost star to the night sky."),
            DemoTitle("wild-meridian", "Wild Meridian", "Documentaries", CatalogueGenre.DOCUMENTARY, 2025, "8.3", "Field researchers follow one longitude from rainforest canopy to polar ice."),
            DemoTitle("lantern-code", "The Lantern Code", "Premieres", CatalogueGenre.CRIME, 2026, "7.9", "An archivist recognises a forgotten harbour signal in a string of modern thefts."),
            DemoTitle("small-hours", "The Small Hours", "Premieres", CatalogueGenre.DRAMA, 2023, "7.3", "Five night workers cross paths in a city just before dawn."),
            DemoTitle("bright-current", "Bright Current", "Family", CatalogueGenre.ADVENTURE, 2025, "7.6", "A young crew races homemade solar boats through an island chain."),
            DemoTitle("winter-radio", "Winter Radio", "Premieres", CatalogueGenre.MYSTERY, 2024, "7.8", "A remote weather host receives calls describing storms that have not happened yet."),
        )

        val demoSeries = listOf(
            DemoTitle("harbor-9", "Harbor 9", "Northstar Originals", CatalogueGenre.MYSTERY, 2026, "8.3", "An investigator returns to a ferry terminal where the ninth berth appears only in heavy rain."),
            DemoTitle("archive-room", "The Archive Room", "Northstar Originals", CatalogueGenre.DRAMA, 2025, "8.0", "A municipal archive quietly solves the unfinished stories hidden inside donated boxes."),
            DemoTitle("atlas-high", "Atlas High", "Family Series", CatalogueGenre.FAMILY, 2024, "7.6", "Students turn an old map room into a club for impossible geography."),
            DemoTitle("lantern-district", "Lantern District", "Mystery", CatalogueGenre.CRIME, 2026, "8.1", "A neighbourhood mediator discovers that every dispute points to the same empty address."),
            DemoTitle("second-sunrise", "Second Sunrise", "Northstar Originals", CatalogueGenre.SCIENCE_FICTION, 2025, "8.2", "An orbital repair crew witnesses dawn twice and loses twelve minutes between them."),
            DemoTitle("little-orbit", "Little Orbit", "Family Series", CatalogueGenre.ANIMATION, 2024, "7.8", "Curious friends run a delivery service across a cheerful miniature solar system."),
            DemoTitle("wild-signal", "Wild Signal", "Documentary Series", CatalogueGenre.DOCUMENTARY, 2025, "8.5", "Biologists decode the calls, colours and vibrations animals use to stay connected."),
            DemoTitle("north-of-tomorrow", "North of Tomorrow", "Northstar Originals", CatalogueGenre.ADVENTURE, 2026, "8.4", "Two explorers test a glass observatory built at the edge of permanent ice."),
            DemoTitle("greenhouse", "The Greenhouse", "Family Series", CatalogueGenre.COMEDY, 2025, "7.7", "Three generations attempt to keep a very opinionated community garden alive."),
            DemoTitle("field-notes", "Field Notes", "Documentary Series", CatalogueGenre.DOCUMENTARY, 2023, "8.3", "Working scientists share the tiny observations that changed their biggest ideas."),
            DemoTitle("paper-kingdom", "Paper Kingdom", "Family Series", CatalogueGenre.FANTASY, 2026, "8.0", "A model-maker finds that every folded city continues growing overnight."),
            DemoTitle("zero-hour-cafe", "Zero Hour Café", "Mystery", CatalogueGenre.MYSTERY, 2025, "7.9", "The last café open each night attracts customers who all seem to be waiting for tomorrow."),
            DemoTitle("coastline-lab", "Coastline Lab", "Documentary Series", CatalogueGenre.DOCUMENTARY, 2024, "8.1", "Inventors along the shore test practical answers to a changing sea."),
            DemoTitle("common-ground", "Common Ground", "Northstar Originals", CatalogueGenre.DRAMA, 2025, "7.8", "Residents of one apartment courtyard rebuild their routines after a long closure."),
            DemoTitle("bright-objects", "Bright Objects", "Mystery", CatalogueGenre.SCIENCE_FICTION, 2026, "8.2", "An observatory technician catalogues lights that appear only in old photographs."),
            DemoTitle("weekend-makers", "Weekend Makers", "Family Series", CatalogueGenre.REALITY, 2024, "7.5", "Neighbourhood teams have two days to transform discarded materials into useful inventions."),
        )

        val programmeDescriptions = listOf(
            "A fresh edition with original reporting and thoughtful conversation.",
            "Stories from fictional communities, presented for the Sohva TV demo library.",
            "A calm hour of discoveries, ideas and practical inspiration.",
            "New voices meet familiar themes in this original demo programme.",
        )
        val episodeNames = listOf("Low Tide", "The Unmarked Door", "A Line of Light", "Borrowed Weather", "Northern Glass", "The Long Return")
        val episodePlots = listOf(
            "A routine handover reveals a detail everyone else has overlooked.",
            "The team follows a quiet clue across the city before the next ferry arrives.",
            "An unexpected visitor changes the meaning of an old record.",
            "A difficult choice becomes clearer when the weather finally breaks.",
        )
    }
}

internal object DemoTime {
    private const val HOUR_MILLIS = 60 * 60_000L
    fun currentHourStart(now: Long): Long = now - now % HOUR_MILLIS
}

internal class DemoSportsRepository : SportsRepository {
    private var summitLogoUrl: String? = null
    private var auroraLogoUrl: String? = null
    private var harbourLogoUrl: String? = null
    private var frostholmLogoUrl: String? = null

    fun updateArtwork(summit: String, aurora: String, harbour: String, frostholm: String) {
        summitLogoUrl = summit
        auroraLogoUrl = aurora
        harbourLogoUrl = harbour
        frostholmLogoUrl = frostholm
    }

    override suspend fun footballEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = snapshot(
        demoEvents(zoneId).filter { event ->
            event.sport == SportType.FOOTBALL &&
                event.competitionId in selectedCompetitionIds &&
                Instant.ofEpochMilli(event.startEpochMillis).atZone(zoneId).toLocalDate() == date
        },
    )

    override suspend fun aflEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = snapshot(emptyList())

    override suspend fun hockeyEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = snapshot(
        demoEvents(zoneId).filter { event ->
            event.sport == SportType.ICE_HOCKEY &&
                event.competitionId in selectedCompetitionIds &&
                Instant.ofEpochMilli(event.startEpochMillis).atZone(zoneId).toLocalDate() == date
        },
    )

    override suspend fun footballIncidents(eventId: String): FootballIncidentsSnapshot =
        FootballIncidentsSnapshot(
            incidents = if (eventId == LIVE_EVENT_ID) liveIncidents else emptyList(),
            cacheState = "hit",
            source = "Sohva TV Demo",
            quotaRemaining = null,
        )

    override suspend fun competitions(sport: SportType): List<SportsCompetition> = when (sport) {
        SportType.FOOTBALL -> listOf(
            SportsCompetition(sport, "demo-premier", "Aurora Premier Division", "North Coast", "League", summitLogoUrl),
            SportsCompetition(sport, "demo-cup", "Meridian Cup", "North Coast", "Cup", summitLogoUrl),
        )
        SportType.ICE_HOCKEY -> listOf(
            SportsCompetition(sport, "demo-ice", "Polar Ice League", "North Coast", "League", frostholmLogoUrl),
        )
        else -> emptyList()
    }

    private fun demoEvents(zoneId: ZoneId): List<TodayEvent> {
        val hourStart = DemoTime.currentHourStart(System.currentTimeMillis())
        return listOf(
            event(
                id = LIVE_EVENT_ID,
                sport = SportType.FOOTBALL,
                competitionId = "demo-premier",
                competition = "Aurora Premier Division",
                home = "Aurora City",
                away = "Harbour United",
                start = hourStart,
                zoneId = zoneId,
                status = TodayEventStatus.LIVE,
                statusLabel = "LIVE · 67′",
                score = "2–1",
                homeLogo = auroraLogoUrl,
                awayLogo = harbourLogoUrl,
                detailsAvailable = true,
            ),
            event(
                id = "demo-northbridge-solstice",
                sport = SportType.FOOTBALL,
                competitionId = "demo-cup",
                competition = "Meridian Cup",
                home = "Northbridge FC",
                away = "Solstice Rovers",
                start = hourStart + 2 * HOUR_MILLIS,
                zoneId = zoneId,
                status = TodayEventStatus.SCHEDULED,
                statusLabel = "UPCOMING",
                score = null,
                homeLogo = summitLogoUrl,
                awayLogo = auroraLogoUrl,
            ),
            event(
                id = "demo-frostholm-glacier",
                sport = SportType.ICE_HOCKEY,
                competitionId = "demo-ice",
                competition = "Polar Ice League",
                home = "Frostholm Lynx",
                away = "Glacier Bay",
                start = hourStart + 4 * HOUR_MILLIS,
                zoneId = zoneId,
                status = TodayEventStatus.SCHEDULED,
                statusLabel = "TONIGHT",
                score = null,
                homeLogo = frostholmLogoUrl,
                awayLogo = harbourLogoUrl,
            ),
            event(
                id = "demo-redwood-coastline",
                sport = SportType.FOOTBALL,
                competitionId = "demo-premier",
                competition = "Aurora Premier Division",
                home = "Redwood Athletic",
                away = "Coastline Wanderers",
                start = hourStart - 3 * HOUR_MILLIS,
                zoneId = zoneId,
                status = TodayEventStatus.FINISHED,
                statusLabel = "FINAL",
                score = "1–1",
                homeLogo = auroraLogoUrl,
                awayLogo = summitLogoUrl,
            ),
        )
    }

    private fun event(
        id: String,
        sport: SportType,
        competitionId: String,
        competition: String,
        home: String,
        away: String,
        start: Long,
        zoneId: ZoneId,
        status: TodayEventStatus,
        statusLabel: String,
        score: String?,
        homeLogo: String?,
        awayLogo: String?,
        detailsAvailable: Boolean = false,
    ): TodayEvent {
        val localStart = Instant.ofEpochMilli(start).atZone(zoneId)
        return TodayEvent(
            id = id,
            sport = sport,
            competitionId = competitionId,
            competition = competition,
            competitionLogoUrl = summitLogoUrl,
            home = home,
            homeLogoUrl = homeLogo,
            away = away,
            awayLogoUrl = awayLogo,
            startEpochMillis = start,
            startMinuteOfDay = localStart.hour * 60 + localStart.minute,
            startLabel = localStart.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)),
            status = status,
            statusLabel = statusLabel,
            score = score,
            matchingChannels = 0,
            detailsAvailable = detailsAvailable,
            isFavourite = id == LIVE_EVENT_ID,
        )
    }

    private fun snapshot(events: List<TodayEvent>) = SportsEventsSnapshot(
        events = events,
        cacheState = "hit",
        source = "Sohva TV Demo",
        quotaRemaining = null,
    )

    companion object {
        const val LIVE_EVENT_ID = "demo-aurora-harbour"
        const val HOUR_MILLIS = 60 * 60_000L

        val liveIncidents = listOf(
            FootballIncident("demo-inc-1", LIVE_EVENT_ID, 14, null, FootballIncidentKind.GOAL, "Right-footed finish", null, "Aurora City", "Mara Venn", "Ivo Lark"),
            FootballIncident("demo-inc-2", LIVE_EVENT_ID, 39, null, FootballIncidentKind.CARD, "Caution", "Late challenge", "Harbour United", "Tomas Reef", null),
            FootballIncident("demo-inc-3", LIVE_EVENT_ID, 52, null, FootballIncidentKind.GOAL, "Header", null, "Harbour United", "Niko Vale", "Ada Shore"),
            FootballIncident("demo-inc-4", LIVE_EVENT_ID, 66, null, FootballIncidentKind.GOAL, "Long-range strike", null, "Aurora City", "Ivo Lark", "Mara Venn"),
        )
    }
}
