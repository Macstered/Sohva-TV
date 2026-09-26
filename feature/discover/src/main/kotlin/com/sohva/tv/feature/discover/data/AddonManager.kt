package com.sohva.tv.feature.discover.data

import com.sohva.tv.feature.discover.net.AddonClient
import com.sohva.tv.feature.discover.protocol.AddonEndpoint
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonManifest
import com.sohva.tv.feature.discover.store.DiscoverAccess
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.InstallationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Installing and managing addons (spec 50 §4.6). Access is checked before and after every network
 * or storage step (ADDON-FR-03); writes are bound to the revision read before them (FR-30, -31).
 */
class AddonManager(
    private val access: DiscoverAccess,
    private val store: InstallationStore,
    private val client: AddonClient,
    private val io: CoroutineDispatcher,
    /** The protocol's HTTP opt-in (FR-06): no screen sets it; device tests' local addon servers do. */
    private val allowHttp: () -> Boolean = { false },
) {
    /** Parses a typed or imported URL under the current HTTP rule. */
    fun endpoint(input: String, baseAllowed: Boolean): AddonEndpoint = AddonEndpoint.parse(input, baseAllowed, allowHttp())

    suspend fun list(profile: String): List<Installation> {
        check(profile)
        return store.list(profile).also { check(profile) }
    }

    /** A manifest from its provider; configuration-required manifests are refused (FR-13). */
    suspend fun manifest(endpoint: AddonEndpoint): AddonManifest = withContext(io) {
        val manifest = AddonManifest.parse(client.get(endpoint.manifestUrl).body)
        if (manifest.configurationRequired) throw AddonException(AddonFailure.CONFIGURATION_REQUIRED)
        manifest
    }

    /** FR-29: the same normalised URL returns its installation unchanged; else fetch, check, store. */
    suspend fun install(profile: String, input: String): Installation {
        check(profile)
        val endpoint = endpoint(input, baseAllowed = true)
        store.list(profile).firstOrNull { it.endpoint == endpoint }?.let { return it }
        val manifest = manifest(endpoint)
        check(profile)
        return store.add(profile, endpoint, manifest).also { check(profile) }
    }

    /** Installs a manifest fetched earlier (the import commit reuses its preview, FR-41). */
    suspend fun installFetched(profile: String, endpoint: AddonEndpoint, manifest: AddonManifest): Installation {
        check(profile)
        return store.add(profile, endpoint, manifest).also { check(profile) }
    }

    /** FR-30: the stored manifest is replaced even at the same version; a failure keeps the last good one. */
    suspend fun refresh(profile: String, id: String): Installation {
        val inst = find(profile, id)
        val manifest = manifest(inst.endpoint)
        check(profile)
        return store.update(inst, manifest = manifest)
    }

    suspend fun setEnabled(profile: String, id: String, enabled: Boolean): Installation {
        val inst = find(profile, id)
        return store.update(inst, enabled = enabled)
    }

    /** FR-32 "Priority ↑": swaps with the one above. */
    suspend fun raise(profile: String, id: String) {
        val all = list(profile)
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) throw AddonException(AddonFailure.NOT_FOUND)
        if (i == 0) return
        val order = all.toMutableList().apply { add(i - 1, removeAt(i)) }
        store.reposition(profile, order)
    }

    /** FR-31: [ids] must name exactly the profile's installations. */
    suspend fun reorder(profile: String, ids: List<String>) {
        val all = list(profile)
        if (ids.size != all.size || ids.toSet() != all.map { it.id }.toSet()) throw AddonException(AddonFailure.CONFLICT)
        store.reposition(profile, ids.map { id -> all.first { it.id == id } })
    }

    suspend fun remove(profile: String, id: String) {
        check(profile)
        store.remove(profile, id)
    }

    private suspend fun find(profile: String, id: String): Installation {
        check(profile)
        return store.find(profile, id) ?: throw AddonException(AddonFailure.NOT_FOUND)
    }

    private suspend fun check(profile: String) {
        if (!access.allowed(profile)) throw AddonException(AddonFailure.ACCESS_DENIED)
    }
}
