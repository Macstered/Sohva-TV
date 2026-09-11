package com.streammate.tv.app

import androidx.compose.runtime.Composable

/** Lazy UI boundary: constructing the entry point opens no stores and sends no requests. */
interface AddonFeature {
    @Composable fun Screen(container: StreamMateContainer, onBack: () -> Unit)

    companion object {
        fun load(policy: AppRuntimePolicy): AddonFeature? {
            if (!policy.addonsAllowed) return null
            return com.streammate.tv.addons.DiscoverAddonFeature()
        }
    }
}
