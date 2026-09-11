package com.sohva.tv.addons

/** Explicitly supplied profile authorization. The host must include its PIN/restriction policy. */
fun interface AddonManagementAccess {
    suspend fun allowed(profileId: String): Boolean
}

class AddonManager(
    private val client: AddonClient,
    private val store: AddonStore,
    private val access: AddonManagementAccess = AddonManagementAccess { false },
) {
    suspend fun list(profileId: String): List<InstalledAddon> {
        authorize(profileId)
        return store.list(profileId)
    }

    suspend fun install(profileId: String, configuredUrl: String, allowInsecureHttp: Boolean = false): InstalledAddon {
        authorize(profileId)
        val endpoint = AddonEndpoint.parse(configuredUrl, allowInsecureHttp, allowBaseUrl = true)
        store.list(profileId).firstOrNull { it.endpoint.fingerprint == endpoint.fingerprint }?.let { return it }
        val document = client.manifest(endpoint)
        val manifest = AddonManifestParser.parse(document.body)
        if (manifest.configurationRequired) fail(AddonFailure.CONFIGURATION_REQUIRED)
        authorize(profileId) // Profile/PIN state may have changed during the request.
        return store.install(profileId, endpoint, document.body)
    }

    suspend fun refresh(profileId: String, installationId: String): Boolean {
        authorize(profileId)
        val existing = store.list(profileId).firstOrNull { it.installationId == installationId } ?: fail(AddonFailure.NOT_FOUND)
        val document = client.manifest(existing.endpoint)
        val manifest = AddonManifestParser.parse(document.body)
        if (manifest.configurationRequired) fail(AddonFailure.CONFIGURATION_REQUIRED)
        authorize(profileId)
        // Content is replaced even if the advertised version didn't change. A stale
        // response cannot resurrect a deleted installation or overwrite a newer edit.
        return store.refresh(profileId, installationId, existing.revision, document.body)
    }

    suspend fun setEnabled(profileId: String, installationId: String, enabled: Boolean) {
        authorize(profileId)
        store.setEnabled(profileId, installationId, enabled)
    }
    suspend fun remove(profileId: String, installationId: String) {
        authorize(profileId)
        store.remove(profileId, installationId)
    }
    suspend fun reorder(profileId: String, installationIds: List<String>) {
        authorize(profileId)
        store.reorder(profileId, installationIds)
    }
    private suspend fun authorize(profileId: String) {
        validateProfile(profileId)
        if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
    }
}
