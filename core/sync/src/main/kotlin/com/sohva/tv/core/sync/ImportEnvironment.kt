package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.concurrent.PauseGate
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.core.net.http.ProviderHttp

/** Seals a stream address for storage (spec 73: stream addresses are stored encrypted). */
fun interface StreamSealer {
    fun seal(address: String): String
}

/**
 * Names shown for entries that name themselves nowhere, in the interface language (spec 10
 * SRC-FR-58 and SRC-FR-71 rebuild rules). Stored with the row; a language change reaches them at
 * the next import, because the name is part of the row's content hash.
 */
interface FallbackNames {
    fun channel(number: Int): String

    fun episode(number: Int): String
}

/** What every importer needs; built once by the app graph. */
class ImportEnvironment(
    val db: SohvaDatabase,
    val http: ProviderHttp,
    val sealer: StreamSealer,
    val clock: Clock,
    val dispatchers: AppDispatchers,
    val pauseGate: PauseGate,
    val log: DiagnosticsLog,
    val names: FallbackNames,
    /** The copy preference that decides which copy stands for a film (spec 40 VOD-FR-29). */
    val preferredCopy: suspend () -> PreferredCopy = { PreferredCopy.NONE },
)
