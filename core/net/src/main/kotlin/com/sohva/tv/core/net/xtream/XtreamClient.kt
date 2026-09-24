package com.sohva.tv.core.net.xtream

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.net.http.ProviderHttp
import com.sohva.tv.core.net.http.ProviderRequest
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import java.io.EOFException
import java.io.IOException
import okhttp3.HttpUrl
import okio.BufferedSource

/**
 * The Xtream Codes API of spec 10 §4.12. Lists are read element by element from the socket and
 * handed to a sink, never collected (SRC-L-03): a 56,164-channel live list costs one record at a time.
 *
 * When a bulk list answers HTTP 5xx or ends before its array closes (providers truncate large lists
 * and say 512), the list is read again category by category, each with one retry (SRC-FR-96). Items
 * read before the truncation reach the sink twice; the importer's key-based upsert absorbs that.
 */
class XtreamClient(private val http: ProviderHttp, private val account: XtreamAccount) {
    private val urls = XtreamUrls(account)

    suspend fun accountInfo(): XtreamAccountInfo = http.get(urls.api(), ProviderRequest.XTREAM) { body ->
        parse(body) { XtreamParsers.account(it) }
    }

    suspend fun categories(list: XtreamList): List<XtreamCategory> {
        val out = ArrayList<XtreamCategory>()
        val complete = http.get(urls.api(list.categoriesAction), ProviderRequest.XTREAM) { body ->
            readArray(body, XtreamParsers::category) { out.add(it) }
        }
        if (!complete) throw AppException(AppError.XtreamResponseInvalid)
        return out
    }

    suspend fun liveStreams(sink: (XtreamLiveStream) -> Unit): Unit = readList(XtreamList.LIVE, XtreamParsers::live, sink)

    suspend fun films(sink: (XtreamFilm) -> Unit): Unit = readList(XtreamList.FILMS, XtreamParsers::film, sink)

    suspend fun series(sink: (XtreamSeries) -> Unit): Unit = readList(XtreamList.SERIES, XtreamParsers::series, sink)

    /** A series' episodes sorted by season and number (SRC-FR-92). */
    suspend fun episodes(seriesId: String): List<XtreamEpisode> {
        if (!SourceRules.isValidId(seriesId)) throw AppException(AppError.SeriesIdInvalid)
        val episodes = http.get(urls.api("get_series_info", "series_id" to seriesId), ProviderRequest.XTREAM) { body ->
            parse(body) { XtreamParsers.seriesInfo(it) }
        }
        return episodes.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    /** The guide of this account: `xmltv.php`, read by the XMLTV importer (SRC-FR-86). */
    fun guideUrl(): HttpUrl = urls.guide()

    private suspend fun <T : Any> readList(list: XtreamList, item: (JsonReader) -> T?, sink: (T) -> Unit) {
        val complete = try {
            http.get(urls.api(list.listAction), ProviderRequest.XTREAM) { body -> readArray(body, item, sink) }
        } catch (e: AppException) {
            val error = e.error
            if (error is AppError.XtreamHttp && error.status in 500..599) false else throw e
        }
        if (complete) return
        for (category in categories(list)) {
            if (!readCategory(list, category, item, sink) && !readCategory(list, category, item, sink)) {
                throw AppException(AppError.XtreamResponseInvalid)
            }
        }
    }

    /** One category's list; false when it failed in a way worth one retry. */
    private suspend fun <T : Any> readCategory(list: XtreamList, category: XtreamCategory, item: (JsonReader) -> T?, sink: (T) -> Unit): Boolean =
        try {
            http.get(urls.api(list.listAction, "category_id" to category.id), ProviderRequest.XTREAM) { body -> readArray(body, item, sink) }
        } catch (e: AppException) {
            val error = e.error
            if (error is AppError.XtreamHttp || error is AppError.TransportFailed) false else throw e
        }

    private companion object {
        /**
         * Reads a top-level array, one element at a time. True when the array closed; false when
         * the body ended or broke after the array had begun. Anything but an array is invalid.
         */
        fun <T : Any> readArray(body: BufferedSource, item: (JsonReader) -> T?, sink: (T) -> Unit): Boolean {
            val reader = JsonReader.of(body)
            reader.isLenient = true
            try {
                if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) throw AppException(AppError.XtreamResponseInvalid)
                reader.beginArray()
            } catch (_: JsonEncodingException) {
                throw AppException(AppError.XtreamResponseInvalid)
            } catch (_: EOFException) {
                throw AppException(AppError.XtreamResponseInvalid)
            }
            try {
                while (reader.hasNext()) {
                    item(reader)?.let(sink)
                }
                reader.endArray()
                return true
            } catch (_: EOFException) {
                return false
            } catch (_: JsonEncodingException) {
                return false
            }
        }

        /** Parses a whole small document (account, series info); broken JSON is invalid. */
        fun <T> parse(body: BufferedSource, read: (JsonReader) -> T): T {
            val reader = JsonReader.of(body)
            reader.isLenient = true
            return try {
                read(reader)
            } catch (e: IOException) {
                if (e is JsonEncodingException || e is EOFException) throw AppException(AppError.XtreamResponseInvalid)
                throw e
            } catch (_: JsonDataException) {
                throw AppException(AppError.XtreamResponseInvalid)
            }
        }
    }
}
