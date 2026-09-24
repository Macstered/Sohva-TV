package com.sohva.tv.app

import com.sohva.tv.core.model.BuildKind

/** Build facts for FeatureFlags; decided by the build type, never by the package name. */
object BuildInfo {
    val KIND: BuildKind = BuildKind.valueOf(BuildConfig.BUILD_KIND)
    const val TRAKT_CONFIGURED: Boolean = BuildConfig.TRAKT_CONFIGURED
}
