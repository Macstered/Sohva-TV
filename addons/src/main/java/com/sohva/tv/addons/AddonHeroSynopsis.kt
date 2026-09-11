package com.sohva.tv.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Foreground, focused-title enrichment only. The caller cancels when focus leaves the title.
 * Reuses the details route, never a catalog scan, stream/subtitle lookup or translation service.
 * Only synopsis text changes: catalog identity, artwork and playback hints remain untouched. */
class AddonHeroSynopsis(
    private val cached: suspend (String, String, AddonMediaKey) -> AddonMedia?,
    private val details: suspend (String, String, AddonMediaKey) -> AddonMedia?,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class RequestKey(val profile: String, val installation: String, val media: AddonMediaKey) {
        override fun toString() = "HeroRequest([redacted])"
    }
    private val network = Mutex()
    // No payload cache here, including for no-store responses. Bound only failed-attempt keys.
    private val failed = LinkedHashMap<RequestKey, Long>()

    fun resolve(profile: String, installation: String, preview: AddonMedia): Flow<AddonMedia> = flow {
        delay(120) // Do not churn the hero while the remote is moving quickly.
        val saved = try { withTimeoutOrNull(200) { cached(profile, installation, preview.key) } }
        catch (error: AddonException) { if (error.revoked()) throw error else null }
        val cachedSynopsis = saved.synopsisFor(preview)
        if (cachedSynopsis != null) { emit(preview.withSynopsis(cachedSynopsis)); return@flow }

        val key = RequestKey(profile, installation, preview.key)
        // Known failures can show the fallback directly, without repeating a blank flash.
        if (network.withLock { recentlyFailed(key) }) { emit(preview); return@flow }
        // Keep artwork/title visible, but never flash catalog-language text while pending.
        emit(preview.withSynopsis(null))

        delay(350) // Metadata is requested only after the user rests on this title.
        network.withLock {
            if (recentlyFailed(key)) { emit(preview); return@withLock }
            val resolved = try { withTimeoutOrNull(8_000) { details(profile, installation, preview.key) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) { if (error.revoked()) throw error else null }
            val synopsis = resolved.synopsisFor(preview)
            if (synopsis != null) {
                failed.remove(key)
                emit(preview.withSynopsis(synopsis))
            } else {
                failed.remove(key)
                failed[key] = clock()
                if (failed.size > 64) failed.remove(failed.keys.first())
                emit(preview)
            }
        }
    }

    // Called only while holding the network mutex, like writes to the failure map.
    private fun recentlyFailed(key: RequestKey): Boolean = failed[key]?.let {
        clock() - it in 0 until 30_000
    } ?: false

    private fun AddonException.revoked() = failure in setOf(
        AddonFailure.ACCESS_DENIED, AddonFailure.CONFLICT, AddonFailure.NOT_FOUND)

    private fun AddonMedia?.synopsisFor(preview: AddonMedia): String? = this
        ?.takeIf { it.key.type == preview.key.type }?.description?.takeIf { it.isNotBlank() }

    private fun AddonMedia.withSynopsis(synopsis: String?): AddonMedia =
        if (synopsis == description) this else AddonMedia(
            key, name, poster, posterShape, background, synopsis, releaseInfo, videos,
            defaultVideoId, logo, genres, runtime, imdbRating, cast)
}
