package com.sohva.tv.app.player

/**
 * The demo build's still pictures (spec 30 PLAY-FR-25, PLAY-44): which of the demo source set's
 * pictures stands in for a stream. A channel shows the football frame; a film one of the two film
 * posters and an episode one of the two series posters, the same one every time for a title.
 * Pure naming; the pictures exist only in the demo build (`app/src/demo/res`).
 */
object DemoPictures {
    private val FILMS = listOf("demo_movie_lighthouse", "demo_movie_signal")
    private val SERIES = listOf("demo_series_harbor", "demo_series_north")

    fun nameFor(key: String): String = when {
        key.startsWith("vod:movie:") -> FILMS[Math.floorMod(key.hashCode(), FILMS.size)]
        key.startsWith("vod:episode:") -> SERIES[Math.floorMod(key.hashCode(), SERIES.size)]
        else -> "demo_live_football"
    }
}
