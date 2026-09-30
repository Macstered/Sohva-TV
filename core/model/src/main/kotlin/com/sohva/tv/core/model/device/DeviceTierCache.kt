package com.sohva.tv.core.model.device

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One system read per process, shared by startup, artwork and playback (plan/07 §2.2). */
class DeviceTierCache(private val read: suspend () -> DeviceTierInputs) {
    private val mutex = Mutex()
    private var loaded = false

    // A background playback service can exist before the first screen reads the system inputs.
    // Until then it uses safe limits without doing Binder or settings reads on the main thread.
    @Volatile
    var current: DeviceTier = DeviceTier(MemoryTier.LOW, reducedMotion = true)
        private set

    /** Compose's own scrolling and transitions use this too, without another system read. */
    @Volatile
    var animationScale: Float = 0f
        private set

    suspend fun load(): DeviceTier = mutex.withLock {
        if (!loaded) {
            val inputs = read()
            val tier = DeviceTier.decide(inputs)
            animationScale = if (tier.reducedMotion) 0f else inputs.animatorDurationScale
            current = tier
            loaded = true
        }
        current
    }
}
