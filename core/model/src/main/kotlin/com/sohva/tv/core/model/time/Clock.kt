package com.sohva.tv.core.model.time

/** Wall and monotonic time, replaceable by a virtual clock in tests (plan/03 §4.15). */
interface Clock {
    /** Milliseconds since the epoch; may jump when the TV sets its clock. */
    fun wallMillis(): Long

    /** Nanoseconds from an arbitrary origin; only for measuring durations. */
    fun monotonicNanos(): Long
}

object SystemClock : Clock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun monotonicNanos(): Long = System.nanoTime()
}
