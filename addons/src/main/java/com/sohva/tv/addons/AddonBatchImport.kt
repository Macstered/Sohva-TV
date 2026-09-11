package com.sohva.tv.addons

import kotlinx.coroutines.CancellationException

enum class AddonImportStatus { READY, ALREADY_INSTALLED, DUPLICATE_INPUT, FAILED, INSTALLED }

class AddonImportEntry internal constructor(
    val line: Int, val status: AddonImportStatus, val failure: AddonFailure? = null,
    val manifest: AddonManifest? = null,
    internal val endpoint: AddonEndpoint? = null, internal val body: String? = null,
) { override fun toString() = "AddonImportEntry(line=$line, status=$status)" }

class AddonImportPreview internal constructor(internal val profileId: String, val entries: List<AddonImportEntry>) {
    /** Exclude unselected new additions without changing source order or existing entries. */
    fun selecting(lines: Set<Int>): AddonImportPreview = AddonImportPreview(profileId,
        entries.filter { it.status != AddonImportStatus.READY || it.line in lines })
    override fun toString() = "AddonImportPreview([redacted])"
}

/** Preview performs validation only. Commit is explicit, ordered and isolates partial failures. */
class AddonBatchImport(
    private val client: AddonClient, private val store: AddonStore,
    private val access: AddonManagementAccess = AddonManagementAccess { false },
) {
    suspend fun preview(profileId: String, text: String, allowInsecureHttp: Boolean = false): AddonImportPreview {
        authorize(profileId)
        AddonImportText.validate(text)
        val lines = text.lineSequence().mapIndexedNotNull { index, line ->
            line.trim().takeIf { it.isNotEmpty() }?.let { index + 1 to it }
        }.toList()
        val existing = store.list(profileId).map { it.endpoint.fingerprint }.toSet()
        val seen = mutableSetOf<String>()
        val entries = lines.map { (line, url) ->
            try {
                authorize(profileId)
                val endpoint = AddonEndpoint.parse(url, allowInsecureHttp, allowBaseUrl = true)
                when {
                    !seen.add(endpoint.fingerprint) -> AddonImportEntry(line, AddonImportStatus.DUPLICATE_INPUT)
                    endpoint.fingerprint in existing -> AddonImportEntry(line, AddonImportStatus.ALREADY_INSTALLED)
                    else -> {
                        val body = client.manifest(endpoint).body
                        val manifest = AddonManifestParser.parse(body)
                        if (manifest.configurationRequired) fail(AddonFailure.CONFIGURATION_REQUIRED)
                        authorize(profileId)
                        AddonImportEntry(line, AddonImportStatus.READY, manifest = manifest, endpoint = endpoint, body = body)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) {
                if (error.failure == AddonFailure.ACCESS_DENIED) throw error
                AddonImportEntry(line, AddonImportStatus.FAILED, error.failure)
            }
        }
        authorize(profileId)
        return AddonImportPreview(profileId, entries)
    }
    suspend fun commit(preview: AddonImportPreview): List<AddonImportEntry> {
        authorize(preview.profileId)
        return preview.entries.map { entry ->
            if (entry.status != AddonImportStatus.READY) return@map entry
            try {
                authorize(preview.profileId)
                val endpoint = checkNotNull(entry.endpoint)
                val existing = store.list(preview.profileId).any { it.endpoint.fingerprint == endpoint.fingerprint }
                if (!existing) store.install(preview.profileId, endpoint, checkNotNull(entry.body))
                AddonImportEntry(entry.line, if (existing) AddonImportStatus.ALREADY_INSTALLED else AddonImportStatus.INSTALLED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) {
                if (error.failure == AddonFailure.ACCESS_DENIED) throw error
                AddonImportEntry(entry.line, AddonImportStatus.FAILED, error.failure)
            }
        }
    }
    private suspend fun authorize(profileId: String) {
        validateProfile(profileId)
        if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
    }
}
