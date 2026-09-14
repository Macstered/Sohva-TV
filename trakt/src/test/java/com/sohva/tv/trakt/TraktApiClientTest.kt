package com.sohva.tv.trakt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TraktApiClientTest {
    private val tokens = TraktTokens("access", "refresh", 4_102_444_800_000L)

    @Test fun scrobbleSendsMovieAndEpisodeBodiesAndReadsTheResult(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktApiClient("client", server.url("/"))
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":1,"action":"pause","progress":42.5,"movie":{"ids":{"tmdb":603}}}"""))
            val movie = client.scrobble(tokens, TraktItem.Movie(TraktIds(tmdb = 603)), TraktScrobbleAction.PAUSE, 42.5)
            assertEquals("pause", movie.action)
            val movieRequest = server.takeRequest()
            assertEquals("/scrobble/pause", movieRequest.path)
            assertEquals("Bearer access", movieRequest.getHeader("Authorization"))
            assertEquals("client", movieRequest.getHeader("trakt-api-key"))
            val movieBody = Json.parseToJsonElement(movieRequest.body.readUtf8()).jsonObject
            assertEquals(603L, movieBody.getValue("movie").jsonObject.getValue("ids").jsonObject.getValue("tmdb").jsonPrimitive.long)
            assertEquals(42.5, movieBody.getValue("progress").jsonPrimitive.double, 0.0)

            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":2,"action":"scrobble","progress":95.0}"""))
            val episode = client.scrobble(tokens, TraktItem.Episode(TraktIds(tmdb = 1399, imdb = "tt0944947"), 1, 2), TraktScrobbleAction.STOP, 95.0)
            assertEquals("scrobble", episode.action)
            val episodeBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("tt0944947", episodeBody.getValue("show").jsonObject.getValue("ids").jsonObject.getValue("imdb").jsonPrimitive.content)
            assertEquals(2, episodeBody.getValue("episode").jsonObject.getValue("number").jsonPrimitive.int)

            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"watched_at":"2026-09-14T10:00:00.000Z","expires_at":"2026-09-14T10:10:00.000Z"}"""))
            assertEquals("scrobble", client.scrobble(tokens, TraktItem.Movie(TraktIds(tmdb = 603)), TraktScrobbleAction.STOP, 99.0).action)
        }
    }

    @Test fun failuresMapToTraktExceptions(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktApiClient("client", server.url("/"))
            for ((code, failure) in listOf(401 to TraktFailure.REAUTHORIZE, 429 to TraktFailure.RATE_LIMITED, 503 to TraktFailure.SERVICE)) {
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "7"))
                val error = runCatching { client.lastActivities(tokens) }.exceptionOrNull() as TraktException
                assertEquals(failure, error.failure)
                if (code == 429) assertEquals(7L, error.retryAfterSeconds)
            }
        }
    }

    @Test fun playbackAndWatchedListsParseIdsAndTimes(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktApiClient("client", server.url("/"))
            server.enqueue(MockResponse().setBody("""[
                {"progress":25.5,"paused_at":"2026-09-14T10:00:00.000Z","id":11,"type":"movie","movie":{"title":"Matrix","year":1999,"ids":{"trakt":1,"slug":"the-matrix","imdb":"tt0133093","tmdb":603}}},
                {"progress":50.0,"paused_at":"2026-09-13T10:00:00.000Z","id":12,"type":"episode","episode":{"season":1,"number":2,"title":"Two","ids":{"trakt":5,"tmdb":9}},"show":{"title":"Show","ids":{"trakt":7,"tmdb":1399}}},
                {"progress":50.0,"paused_at":"2026-09-13T10:00:00.000Z","id":13,"type":"movie","movie":{"title":"No ids","ids":{"slug":"x"}}}
            ]"""))
            val playback = client.playback(tokens)
            assertEquals("/sync/playback?limit=500", server.takeRequest().path)
            assertEquals(2, playback.size)
            assertEquals(603L, (playback[0].item as TraktItem.Movie).ids.tmdb)
            assertEquals("Matrix", playback[0].title)
            assertEquals(1_789_380_000_000L, playback[0].pausedAtMillis)
            val episode = playback[1].item as TraktItem.Episode
            assertEquals(1399L, episode.ids.tmdb); assertEquals(1, episode.season); assertEquals(2, episode.number)
            assertEquals("Show · Two", playback[1].title)

            server.enqueue(MockResponse().setBody("""[{"plays":2,"last_watched_at":"2026-09-14T10:00:00.000Z","movie":{"ids":{"tmdb":603}}}]"""))
            val movies = client.watchedMovies(tokens)
            assertEquals(2, movies.single().plays); assertEquals(603L, movies.single().ids.tmdb)

            server.enqueue(MockResponse().setBody("""[{"plays":3,"show":{"ids":{"tmdb":1399}},"seasons":[{"number":1,"episodes":[{"number":1,"plays":1,"last_watched_at":"2026-09-14T10:00:00.000Z"},{"number":2,"plays":2,"last_watched_at":"2026-09-14T11:00:00.000Z"}]}]}]"""))
            val shows = client.watchedShows(tokens)
            assertEquals(listOf(1, 2), shows.single().episodes.map { it.number })
            assertEquals(1399L, shows.single().ids.tmdb)

            server.enqueue(MockResponse().setBody("""{"all":"2026-09-14T11:00:00.000Z","movies":{"watched_at":"2026-09-14T10:00:00.000Z","paused_at":"2026-09-14T11:00:00.000Z"},"episodes":{"watched_at":"2026-09-01T10:00:00.000Z","paused_at":null}}"""))
            assertEquals(1_789_383_600_000L, client.lastActivities(tokens).latest)
        }
    }

    @Test fun recommendationsAndShowProgressParseTitlesImagesAndNextEpisodes(): Unit = runBlocking {
        MockWebServer().use { server ->
            val client = TraktApiClient("client", server.url("/"))
            server.enqueue(MockResponse().setBody("""[{"title":"Heat","year":1995,"overview":"Cops and robbers.","ids":{"trakt":1,"tmdb":949,"imdb":"tt0113277"},"images":{"poster":["walter.trakt.tv/p.jpg"],"fanart":["walter.trakt.tv/f.jpg"]}},{"title":"No ids","ids":{"slug":"x"}}]"""))
            val movies = client.recommendations(tokens, "movies", 10)
            assertEquals("/recommendations/movies?limit=10&extended=full,images", server.takeRequest().path)
            val heat = movies.single()
            assertEquals("movie", heat.kind); assertEquals(949L, heat.ids.tmdb); assertEquals("https://walter.trakt.tv/p.jpg", heat.poster)
            assertEquals("https://walter.trakt.tv/f.jpg", heat.fanart); assertEquals("Cops and robbers.", heat.overview)

            server.enqueue(MockResponse().setBody("""{"aired":10,"completed":3,"last_watched_at":"2026-09-14T10:00:00.000Z","next_episode":{"season":1,"number":4,"title":"Four","ids":{"trakt":9}}}"""))
            val progress = client.showProgress(tokens, "1399")!!
            assertEquals("/shows/1399/progress/watched?hidden=false&specials=false&count_specials=false", server.takeRequest().path)
            assertEquals(1, progress.nextSeason); assertEquals(4, progress.nextNumber); assertEquals("Four", progress.nextTitle)

            server.enqueue(MockResponse().setBody("""{"aired":10,"completed":10,"last_watched_at":"2026-09-14T10:00:00.000Z","next_episode":null}"""))
            assertNull(client.showProgress(tokens, "1399")!!.nextSeason)

            server.enqueue(MockResponse().setBody("""{"title":"Show","year":2011,"overview":"Dragons.","ids":{"trakt":7,"tmdb":1399},"images":{"poster":[],"fanart":["walter.trakt.tv/s.jpg"]}}"""))
            val show = client.showSummary(tokens, "7")!!
            assertEquals("show", show.kind); assertNull(show.poster); assertEquals("https://walter.trakt.tv/s.jpg", show.fanart)
        }
    }
}
