package com.sohva.tv.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

enum class AddonSourceStatus { LOADING, READY, FAILED }

class AddonSourceResult<T>(
    val installationId: String, val providerName: String, val position: Int,
    val status: AddonSourceStatus, val items: List<T> = emptyList(), val failure: AddonFailure? = null,
    val revision: Long = -1,
) { override fun toString() = "AddonSourceResult(status=$status, count=${items.size})" }

/** Cold, cancelable flows; caller explicitly selects a video. No startup requests or persisted URLs. */
class AddonSourceRepository(
    private val client: AddonClient, private val store: AddonStore,
    private val access: AddonManagementAccess = AddonManagementAccess { false },
) {
    fun streams(profileId: String, video: AddonMediaKey): Flow<AddonSourceResult<AddonStream>> =
        resolve(profileId, "stream", video, emptyMap(), AddonSourceParser::streams)

    fun subtitles(profileId: String, video: AddonMediaKey, extras: Map<String, String> = emptyMap()): Flow<AddonSourceResult<AddonSubtitle>> =
        resolve(profileId, "subtitles", video, extras.toMap(), AddonSourceParser::subtitles)

    private fun <T> resolve(
        profileId: String, resource: String, video: AddonMediaKey, extras: Map<String, String>, parse: (String) -> List<T>,
    ): Flow<AddonSourceResult<T>> = channelFlow {
        authorize(profileId)
        if (extras.keys.any { it !in setOf("videoHash", "videoSize", "filename") } ||
            extras.values.any { it.length > 1024 || it.any(Char::isISOControl) } ||
            extras["videoHash"]?.matches(Regex("[a-fA-F0-9]{16}")) == false ||
            extras["videoSize"]?.let { it.toLongOrNull()?.let { size -> size < 0 } ?: true } == true) fail(AddonFailure.INVALID_REQUEST)
        val installations = store.list(profileId).filter { it.enabled && it.manifest.supports(resource, video.type, video.id) }
        // Emit all loading slots first. Completion arrives independently; retain installation order in UI.
        installations.forEach { send(AddonSourceResult(it.installationId, it.manifest.name, it.position, AddonSourceStatus.LOADING)) }
        installations.forEach { original ->
            launch(Dispatchers.IO) {
                try {
                    checked(profileId, original)
                    val document = client.resource(original.endpoint, original.manifest, resource, video.type, video.id, extras)
                    val items = parse(document.body)
                    checked(profileId, original)
                    send(AddonSourceResult(original.installationId, original.manifest.name, original.position, AddonSourceStatus.READY, items, revision = original.revision))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: AddonException) {
                    // A profile switch cancels the entire resolver, not just one provider.
                    authorize(profileId)
                    send(AddonSourceResult(original.installationId, original.manifest.name, original.position, AddonSourceStatus.FAILED, failure = error.failure))
                }
            }
        }
    }
    private suspend fun checked(profileId: String, original: InstalledAddon) {
        authorize(profileId)
        val current = store.list(profileId).firstOrNull { it.installationId == original.installationId && it.enabled } ?: fail(AddonFailure.NOT_FOUND)
        if (current.revision != original.revision) fail(AddonFailure.CONFLICT)
    }
    private suspend fun authorize(profileId: String) {
        validateProfile(profileId)
        if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
    }
}
