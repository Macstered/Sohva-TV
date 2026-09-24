package com.sohva.tv.core.model.device

/** What the memory tier decision reads, recorded in diagnostics to calibrate it (plan/07 §2.2). */
data class DeviceTierInputs(
    val isLowRamDevice: Boolean,
    val memoryClassMb: Int,
    val totalMemBytes: Long,
    val animatorDurationScale: Float,
)

enum class MemoryTier { STANDARD, LOW }

/** The decision, made once at start off the main thread. */
data class DeviceTier(val memory: MemoryTier, val reducedMotion: Boolean) {
    companion object {
        /** The Shield's heap limit is 192 MB, so the threshold is strictly below it (plan/07 §4.12). */
        const val LOW_MEMORY_CLASS_BELOW_MB: Int = 192

        /** Puts 2 GB boxes in the low tier and the 3 GB Shield in the standard tier. */
        const val LOW_TOTAL_MEM_AT_MOST_BYTES: Long = 2_684_354_560L // 2.5 GiB

        /**
         * Low tier when the platform says low-RAM, the heap class is under 192 MB, or the device
         * has at most 2.5 GiB. Reduced motion follows the low tier, a zero animator scale, and the
         * viewer's own switch (decision A8).
         */
        fun decide(inputs: DeviceTierInputs, viewerReducedMotion: Boolean = false): DeviceTier {
            val low = inputs.isLowRamDevice ||
                inputs.memoryClassMb < LOW_MEMORY_CLASS_BELOW_MB ||
                inputs.totalMemBytes <= LOW_TOTAL_MEM_AT_MOST_BYTES
            val motion = low || inputs.animatorDurationScale == 0f || viewerReducedMotion
            return DeviceTier(if (low) MemoryTier.LOW else MemoryTier.STANDARD, motion)
        }
    }
}
