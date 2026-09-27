package com.sohva.tv.core.model

/** Which build is running; resolved by :app from its build type, never from the package name. */
enum class BuildKind { RELEASE, DEBUG, LAB, DEMO }

/**
 * Build-time availability of the optional features (plan/03 §4.10). A feature that is off has no
 * graph, no routes and no contributions. Screens never check package names.
 */
data class FeatureFlags(
    val sport: Boolean,
    val discover: Boolean,
    /**
     * Trakt's Accounts panel, sync and scrobbles. Present in every build: without application
     * credentials it says "not configured" (spec 51 TRAKT-08); the demo build runs it offline (FR-37).
     */
    val trakt: Boolean,
    val reminders: Boolean,
    val publicUpdates: Boolean,
    val demoContent: Boolean,
    /** The background metadata enrichment (spec 41 META-FR-62): never in the Lab build. */
    val metadataWorker: Boolean = true,
) {
    companion object {
        fun resolve(kind: BuildKind): FeatureFlags = FeatureFlags(
            sport = true,
            discover = kind != BuildKind.DEMO,
            trakt = true,
            reminders = kind != BuildKind.LAB,
            publicUpdates = kind == BuildKind.RELEASE,
            demoContent = kind == BuildKind.DEMO,
            metadataWorker = kind != BuildKind.LAB,
        )
    }
}
