package com.sohva.tv.feature.sport.provider

import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/** One API-Sports answer: the body (at most 4 MiB) and the remaining daily quota the provider reported. */
class SportsAnswer(val body: Buffer, val quotaRemaining: Int?)

/**
 * API-Sports over HTTPS (spec 60 §7): one host per sport, the viewer's key in the
 * `x-apisports-key` header only, redirects not followed, bodies over 4 MiB refused. The app's
 * shared client (connect 20 s, read 90 s); no IPTV data is ever sent. Cancelling the caller
 * cancels the call.
 */
class SportsHttp(base: OkHttpClient, private val hosts: (SportType) -> HttpUrl = ::productionHost) {
    private val client: OkHttpClient = base.newBuilder()
        .apply { interceptors().clear() }
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** A day listing (§7 table). */
    fun dayUrl(sport: SportType, date: String, zone: String): HttpUrl =
        hosts(sport).newBuilder().addPathSegments(DAY_PATH.getValue(sport)).addQueryParameter("date", date).addQueryParameter("timezone", zone).build()

    /** The competition catalogue; football asks for current seasons only (SPORT-FR-06). */
    fun competitionsUrl(sport: SportType): HttpUrl = hosts(sport).newBuilder().addPathSegment("leagues")
        .apply { if (sport == SportType.FOOTBALL) addQueryParameter("current", "true") }.build()

    fun incidentsUrl(fixtureId: String): HttpUrl =
        hosts(SportType.FOOTBALL).newBuilder().addPathSegments("fixtures/events").addQueryParameter("fixture", fixtureId).build()

    suspend fun get(url: HttpUrl, key: String): SportsAnswer = coroutineScope {
        val request = Request.Builder().url(url).get()
            .header("x-apisports-key", key)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val call = client.newCall(request)
        val canceller = launch(Dispatchers.Unconfined) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw SportsException(SportsProblem.HTTP, response.code)
                val quota = response.header("x-ratelimit-requests-remaining")?.trim()?.toIntOrNull()
                val source = response.body.source()
                val buffer = Buffer()
                while (buffer.size <= MAX_BODY) {
                    if (source.read(buffer, 8_192) == -1L) break
                }
                if (buffer.size > MAX_BODY) throw SportsException(SportsProblem.TOO_LARGE)
                SportsAnswer(buffer, quota)
            }
        } catch (e: IOException) {
            ensureActive()
            throw SportsException(SportsProblem.UNAVAILABLE, cause = e)
        } finally {
            canceller.cancel()
        }
    }

    companion object {
        const val MAX_BODY: Long = 4L * 1024 * 1024
        const val USER_AGENT: String = "SohvaTV/0.1 (Android TV; personal use)"

        private val DAY_PATH = SportType.entries.associateWith {
            when (it) {
                SportType.FOOTBALL -> "fixtures"
                SportType.MMA -> "fights"
                SportType.FORMULA_1 -> "races"
                else -> "games"
            }
        }

        fun productionHost(sport: SportType): HttpUrl = when (sport) {
            SportType.FOOTBALL -> "https://v3.football.api-sports.io/"
            SportType.NBA -> "https://v2.nba.api-sports.io/"
            else -> "https://v1.${sport.provider}.api-sports.io/"
        }.toHttpUrl()
    }
}
