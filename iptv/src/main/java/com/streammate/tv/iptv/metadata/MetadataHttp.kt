package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

internal suspend fun OkHttpClient.getMetadataJson(
    request: Request,
    providerName: String,
    retryRateLimit: Boolean = false,
): JsonElement {
    var attempt = 0
    while (true) {
        val result = withContext(Dispatchers.IO) {
            newCall(request).execute().use { response ->
                if (response.code == 429 && retryRateLimit && attempt == 0) {
                    return@use MetadataHttpResult.RateLimited(
                        response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 5) ?: 2,
                    )
                }
                if (!response.isSuccessful) {
                    throw LocalizedException(
                        CoreR.string.error_metadata_http,
                        listOf(providerName, response.code),
                    )
                }
                val body = response.body
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_METADATA_RESPONSE_BYTES) {
                    throw LocalizedException(
                        CoreR.string.error_metadata_response_too_large,
                        listOf(providerName),
                    )
                }
                val encoded = body.string()
                if (encoded.toByteArray(Charsets.UTF_8).size > MAX_METADATA_RESPONSE_BYTES) {
                    throw LocalizedException(
                        CoreR.string.error_metadata_response_too_large,
                        listOf(providerName),
                    )
                }
                MetadataHttpResult.Body(encoded)
            }
        }
        when (result) {
            is MetadataHttpResult.Body -> return JSON.parseToJsonElement(result.value)
            is MetadataHttpResult.RateLimited -> {
                attempt += 1
                delay(result.retryAfterSeconds * 1_000L)
            }
        }
    }
}

internal fun JsonObject.string(name: String): String? = this[name]
    ?.jsonPrimitive
    ?.contentOrNull
    ?.trim()
    ?.take(MAX_METADATA_TEXT_LENGTH)
    ?.takeIf(String::isNotBlank)

internal fun JsonObject.integer(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

internal fun JsonObject.decimal(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull

internal fun String?.httpsUrlOrNull(): String? = this
    ?.takeIf { value -> value.startsWith("https://", ignoreCase = true) && value.length <= MAX_URL_LENGTH }

private sealed interface MetadataHttpResult {
    data class Body(val value: String) : MetadataHttpResult
    data class RateLimited(val retryAfterSeconds: Long) : MetadataHttpResult
}

private const val MAX_METADATA_RESPONSE_BYTES = 2 * 1024 * 1024
private const val MAX_METADATA_TEXT_LENGTH = 8_000
private const val MAX_URL_LENGTH = 2_048
private val JSON = Json { ignoreUnknownKeys = true }
