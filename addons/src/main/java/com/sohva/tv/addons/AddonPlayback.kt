package com.sohva.tv.addons

/** Selection is memory-only; history must never contain a resolved stream or subtitle URL. */
class AddonPlaybackSelection(
    val installationId: String, val revision: Long, val video: AddonMediaKey, val stream: AddonStream,
) { override fun toString() = "AddonPlaybackSelection([redacted])" }

class AddonPlaybackAccess(private val client: AddonClient, private val store: AddonStore, private val access: AddonManagementAccess) {
    suspend fun check(profileId: String, selection: AddonPlaybackSelection) {
        if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
        val installed = store.list(profileId).firstOrNull { it.installationId == selection.installationId && it.enabled }
            ?: fail(AddonFailure.NOT_FOUND)
        if (installed.revision != selection.revision) fail(AddonFailure.CONFLICT)
        if (!installed.manifest.supports("stream", selection.video.type, selection.video.id) ||
            selection.stream.kind != AddonStreamKind.HTTP || selection.stream.url == null) fail(AddonFailure.UNSUPPORTED_RESOURCE)
    }
    /** Refresh only an unambiguous matching source; never silently switch provider/quality. */
    suspend fun refresh(profileId: String, previous: AddonPlaybackSelection): AddonPlaybackSelection {
        check(profileId, previous)
        val installed = store.list(profileId).first { it.installationId == previous.installationId }
        val response = client.resource(installed.endpoint, installed.manifest, "stream", previous.video.type, previous.video.id)
        val matches = AddonSourceParser.streams(response.body).filter { candidate ->
            candidate.kind == AddonStreamKind.HTTP && candidate.name == previous.stream.name &&
                if (previous.stream.filename != null) candidate.filename == previous.stream.filename else candidate.description == previous.stream.description
        }
        val stream = matches.singleOrNull() ?: fail(AddonFailure.CONFLICT)
        check(profileId, previous)
        return AddonPlaybackSelection(previous.installationId, previous.revision, previous.video, stream)
    }
}
