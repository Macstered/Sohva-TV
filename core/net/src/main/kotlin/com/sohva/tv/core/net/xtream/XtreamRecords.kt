package com.sohva.tv.core.net.xtream

/** The three Xtream lists and their category calls (spec 10 SRC-FR-68). */
enum class XtreamList(internal val categoriesAction: String, internal val listAction: String) {
    LIVE("get_live_categories", "get_live_streams"),
    FILMS("get_vod_categories", "get_vod_streams"),
    SERIES("get_series_categories", "get_series"),
}

data class XtreamAccountInfo(
    val username: String?,
    val status: String?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    /** `server_info.timezone` as given; catch-up falls back to the device zone when it is unusable. */
    val serverTimeZone: String?,
)

data class XtreamCategory(val id: String, val name: String)

data class XtreamLiveStream(
    val streamId: String,
    val name: String,
    val categoryId: String?,
    /** The stream file extension, already checked against `[a-z0-9]{1,8}` and defaulted (SRC-FR-70). */
    val extension: String,
    val epgChannelId: String?,
    val iconUrl: String?,
    /** Catch-up days 1…365 when `tv_archive` is on and has a duration, else null (SRC-FR-71). */
    val catchupDays: Int?,
    val num: Int?,
)

data class XtreamFilm(
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val extension: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
)

data class XtreamSeries(
    val seriesId: String,
    val name: String,
    val categoryId: String?,
    val coverUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
)

data class XtreamEpisode(
    val id: String,
    val season: Int,
    val episode: Int,
    val extension: String,
    /** The cleaned title; null when the viewer should read the translated "Episode n". */
    val title: String?,
    val plot: String?,
    val durationSeconds: Int?,
    val imageUrl: String?,
)
