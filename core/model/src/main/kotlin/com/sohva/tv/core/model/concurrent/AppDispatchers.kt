package com.sohva.tv.core.model.concurrent

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The only place dispatchers are named (plan/03 §4.7). Direct use of `Dispatchers.IO` or
 * `Dispatchers.Default` elsewhere is a lint error. Database reads and writes run on Room's own
 * executors, configured in :core:data.
 */
interface AppDispatchers {
    /** Composition, focus and trivial state reduction. Nothing else. */
    val main: CoroutineDispatcher

    /** Preparing visible data: mapping, sorting and formatting one page. Two threads. */
    val ui: CoroutineDispatcher

    /** Network calls, small file and preference reads, Keystore work. */
    val io: CoroutineDispatcher

    /** One background-priority thread for imports (download and parse) and every other bulk pass. */
    val bulk: CoroutineDispatcher

    /**
     * A second background-priority thread that writes an import's batches while [bulk] parses the
     * next one (spec 10 SRC-L-08). Nothing else runs here.
     */
    val bulkWrite: CoroutineDispatcher
}
