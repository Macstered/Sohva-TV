package com.sohva.tv.trakt

/** External identifiers Trakt knows a title by. At least one is needed to address it. */
class TraktIds(val trakt: Long? = null, val tmdb: Long? = null, val imdb: String? = null, val tvdb: Long? = null) {
    val any: Boolean get() = trakt != null || tmdb != null || imdb != null || tvdb != null
    override fun toString() = "TraktIds(trakt=$trakt, tmdb=$tmdb, imdb=$imdb, tvdb=$tvdb)"
}

/** A movie or one episode of a show, addressed by ids only. Titles are never used for matching. */
sealed class TraktItem {
    abstract val ids: TraktIds
    class Movie(override val ids: TraktIds) : TraktItem() {
        init { require(ids.any) { "A movie needs an id" } }
        override fun toString() = "Movie($ids)"
    }
    /** [ids] identify the show; the episode is addressed by season and number. */
    class Episode(override val ids: TraktIds, val season: Int, val number: Int) : TraktItem() {
        init { require(ids.any && season >= 0 && number >= 0) { "An episode needs a show id and numbers" } }
        override fun toString() = "Episode($ids S${season}E$number)"
    }
}

enum class TraktScrobbleAction(val path: String) { START("start"), PAUSE("pause"), STOP("stop") }

/** What Trakt did with a scrobble: `pause`, `start` or `scrobble` (recorded as watched). */
class TraktScrobbleResult(val action: String, val progress: Double, val recorded: String? = null)

/** One paused title from the account's playback list. */
class TraktPlaybackItem(val id: Long, val item: TraktItem, val progress: Double, val pausedAtMillis: Long, val title: String?)

class TraktWatchedMovie(val ids: TraktIds, val plays: Int, val lastWatchedAtMillis: Long)
class TraktWatchedEpisode(val season: Int, val number: Int, val plays: Int, val lastWatchedAtMillis: Long)
class TraktWatchedShow(val ids: TraktIds, val episodes: List<TraktWatchedEpisode>, val title: String? = null, val year: Int? = null, val lastWatchedAtMillis: Long = 0)

/** A movie or show as Trakt describes it, with the pictures it mirrors from TMDB. */
class TraktTitle(
    val kind: String, val ids: TraktIds, val title: String, val year: Int?,
    val overview: String?, val poster: String?, val fanart: String?,
) { override fun toString() = "TraktTitle($kind $ids)" }

/** Where a show stands for the account: the next episode to watch, if any. */
class TraktShowProgress(val nextSeason: Int?, val nextNumber: Int?, val nextTitle: String?, val lastWatchedAtMillis: Long)

/** Newest change time per activity type; a cheap "did anything change since last sync" check. */
class TraktActivities(val moviesWatchedAt: Long, val moviesPausedAt: Long, val episodesWatchedAt: Long, val episodesPausedAt: Long) {
    val latest: Long get() = maxOf(moviesWatchedAt, moviesPausedAt, episodesWatchedAt, episodesPausedAt)
}
