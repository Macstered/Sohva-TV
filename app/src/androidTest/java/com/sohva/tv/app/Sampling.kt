package com.sohva.tv.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/** Samples the Java heap every 50 ms on its own thread and keeps the peak (plan/07 §6 "meminfo sampling"). */
class HeapSampler : Thread("HeapSampler") {
    private val peak = AtomicLong()

    @Volatile private var running = true

    override fun run() {
        val runtime = Runtime.getRuntime()
        while (running) {
            peak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory()) { a, b -> maxOf(a, b) }
            sleep(50)
        }
    }

    fun finish() {
        running = false
        join()
    }

    fun max(): Long = peak.get()
}

/** Posts to the main thread every 16 ms and records the longest wait between two runs. */
class Heartbeat {
    private val handler = Handler(Looper.getMainLooper())
    private val longest = AtomicLong()
    private val phaseLongest = AtomicLong()

    @Volatile private var last = 0L

    @Volatile private var running = true
    private val tick = object : Runnable {
        override fun run() {
            val t = SystemClock.uptimeMillis()
            if (last != 0L) {
                longest.accumulateAndGet(t - last) { a, b -> maxOf(a, b) }
                phaseLongest.accumulateAndGet(t - last) { a, b -> maxOf(a, b) }
            }
            last = t
            if (running) handler.postDelayed(this, 16)
        }
    }

    fun start() = handler.post(tick)

    fun finish() {
        running = false
    }

    fun longestGapMs(): Long = longest.get()

    /** The longest gap since the last call, for one phase of a run. */
    fun phase(): Long = phaseLongest.getAndSet(0)
}
