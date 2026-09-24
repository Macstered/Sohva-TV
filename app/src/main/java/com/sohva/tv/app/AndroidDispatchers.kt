package com.sohva.tv.app

import android.os.Process
import com.sohva.tv.core.model.concurrent.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** The dispatchers of plan/03 §4.7. The only file allowed to name `Dispatchers.*`. */
class AndroidDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate

    // Two threads leave cores for the render thread and the video decoder on a 4-core box.
    override val ui: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(UI_THREADS)

    override val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(IO_THREADS)

    override val bulk: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            {
                // Background priority: imports and passes give way to the UI and the decoder.
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            },
            "SohvaBulk",
        )
    }.asCoroutineDispatcher()

    private companion object {
        const val UI_THREADS = 2
        const val IO_THREADS = 8
    }
}
