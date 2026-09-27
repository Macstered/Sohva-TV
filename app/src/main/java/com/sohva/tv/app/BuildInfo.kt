package com.sohva.tv.app

import com.sohva.tv.core.model.BuildKind

/** Build facts for FeatureFlags; decided by the build type, never by the package name. */
object BuildInfo {
    val KIND: BuildKind = BuildKind.valueOf(BuildConfig.BUILD_KIND)

    /** Trakt's application id and secret from the ignored local file; blank without it (spec 51 FR-01). */
    const val TRAKT_CLIENT_ID: String = BuildConfig.TRAKT_CLIENT_ID
    const val TRAKT_CLIENT_SECRET: String = BuildConfig.TRAKT_CLIENT_SECRET
}
