package com.sohva.tv.feature.trakt.protocol

/** Why a Trakt call failed (spec 51 §4.12); never the response body or a raw error. */
enum class TraktFailure {
    NETWORK,
    RATE_LIMITED,
    SERVICE,
    REAUTHORIZE,
    CONFIGURATION,
    INVALID_RESPONSE,
    NOT_FOUND,
    REJECTED,
}

class TraktException(val failure: TraktFailure, val retryAfterSeconds: Long? = null) : Exception(failure.name)

/**
 * The build's application credentials (TRAKT-FR-01), from the ignored local properties file; blank
 * when the build has none. They never reach a log.
 */
class TraktCredentials(val clientId: String, val clientSecret: String) {
    val configured: Boolean get() = usable(clientId) && usable(clientSecret)

    override fun toString(): String = "TraktCredentials(configured=$configured)"

    companion object {
        val NONE: TraktCredentials = TraktCredentials("", "")

        /** Printable ASCII, no quotes or backslashes (FR-01), so it can never inject a header. */
        fun usable(value: String): Boolean = value.isNotEmpty() && value.all { it in ' '..'~' && it != '"' && it != '\\' && it != ' ' }
    }
}

/** An account's tokens (FR-03); [expiresAt] is wall-clock milliseconds. */
class TraktTokens(val access: String, val refresh: String, val expiresAt: Long) {
    override fun toString(): String = "TraktTokens(expiresAt=$expiresAt)"
}

/** A device code to show (FR-02 step 1). */
class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val expiresInSeconds: Long, val intervalSeconds: Long) {
    override fun toString(): String = "DeviceCode(expires=$expiresInSeconds, interval=$intervalSeconds)"
}

/** One poll's answer (FR-02 step 4). */
sealed interface DevicePoll {
    class Granted(val tokens: TraktTokens) : DevicePoll {
        override fun toString(): String = "Granted"
    }

    data object Pending : DevicePoll

    data class SlowDown(val intervalSeconds: Long) : DevicePoll

    data object InvalidCode : DevicePoll

    data object AlreadyUsed : DevicePoll

    data object Expired : DevicePoll

    data object Denied : DevicePoll
}

/** The account behind the tokens (FR-02 step 5): a stable uuid and the name shown. */
class TraktIdentity(val uuid: String, val username: String) {
    override fun toString(): String = "TraktIdentity"
}

/** Trakt, TMDB, IMDb and TVDB ids; at least one (FR-12). */
data class TraktIds(val trakt: Long? = null, val tmdb: Long? = null, val imdb: String? = null, val tvdb: Long? = null) {
    val any: Boolean get() = trakt != null || tmdb != null || imdb != null || tvdb != null
}

/** What is scrobbled (FR-12): a movie, or an episode of a show by season and number. */
sealed interface TraktItem {
    data class Movie(val ids: TraktIds) : TraktItem

    data class Episode(val show: TraktIds, val season: Int, val number: Int) : TraktItem
}

enum class ScrobbleAction(val path: String) { START("start"), PAUSE("pause"), STOP("stop") }

/** The newest activity times (FR-22 step 1), milliseconds; 0 when unknown. */
data class LastActivities(val moviesWatched: Long, val moviesPaused: Long, val episodesWatched: Long, val episodesPaused: Long) {
    val newest: Long get() = maxOf(moviesWatched, moviesPaused, episodesWatched, episodesPaused)
}

/** A paused item from `sync/playback` (FR-22 step 3). */
data class PausedItem(val item: TraktItem, val progress: Double, val pausedAt: Long)

data class WatchedMovie(val ids: TraktIds, val plays: Int, val lastWatchedAt: Long)

data class WatchedEpisode(val season: Int, val number: Int, val plays: Int, val lastWatchedAt: Long)

data class WatchedShow(val ids: TraktIds, val lastWatchedAt: Long, val episodes: List<WatchedEpisode>)

/** A show's next episode (FR-25), or none when the show is finished. */
data class ShowProgress(val nextSeason: Int?, val nextNumber: Int?, val nextTitle: String?, val lastWatchedAt: Long)

enum class TraktKind { MOVIE, SHOW }

/** A title for Home's rows (FR-27). */
data class TraktTitle(
    val kind: TraktKind,
    val ids: TraktIds,
    val title: String,
    val year: Int?,
    val overview: String?,
    val poster: String?,
    val fanart: String?,
)
