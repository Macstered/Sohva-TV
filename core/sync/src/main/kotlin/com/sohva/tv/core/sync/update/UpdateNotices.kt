package com.sohva.tv.core.sync.update

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.update.UpdateState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The small announcement, never a release body or download. */
data class UpdateNotice(val version: String, val build: Int)

interface UpdateNoticeMemory {
    suspend fun lastNotifiedBuild(): Int
    suspend fun setLastNotifiedBuild(build: Int)
}

/** One pending notice per app, backed by one persisted build number. */
class UpdateNotices(
    private val enabled: Boolean,
    private val installedBuild: Int,
    private val memory: UpdateNoticeMemory,
    private val scope: CoroutineScope,
    private val log: DiagnosticsLog,
) {
    private val _pending = MutableStateFlow<UpdateNotice?>(null)
    val pending: StateFlow<UpdateNotice?> = _pending.asStateFlow()
    private val started = AtomicBoolean(false)
    private val seen = AtomicInteger(0)
    private val writing = Mutex()

    /** Called after the first frame, in the same IO scope as the existing checker. */
    fun start(updates: StateFlow<UpdateState>): Unit {
        if (!enabled || !started.compareAndSet(false, true)) return
        scope.launch {
            // Download progress never reopens preferences or recomposes the notice.
            updates.map { it.update }.distinctUntilChangedBy { it?.build }.collect { offered ->
                if (offered == null || offered.build <= installedBuild) {
                    _pending.value = null
                } else {
                    try {
                        seen.accumulateAndGet(memory.lastNotifiedBuild(), ::maxOf)
                        _pending.update {
                            if (offered.build > seen.get()) UpdateNotice(offered.version, offered.build) else null
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        _pending.value = null
                        log.error("update", "notice memory could not be read", failure)
                    }
                }
            }
        }
    }

    /** Called only after the notice draws; recreation cannot repeat it during a slow disk write. */
    fun shown(build: Int): Unit {
        if (!enabled || build <= installedBuild) return
        val previous = seen.getAndAccumulate(build, ::maxOf)
        _pending.update { it?.takeIf { notice -> notice.build > build } }
        if (build <= previous) return
        scope.launch {
            try {
                writing.withLock {
                    val stored = memory.lastNotifiedBuild()
                    if (build > stored) memory.setLastNotifiedBuild(build)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                log.error("update", "notice memory could not be saved", failure)
            }
        }
    }
}
