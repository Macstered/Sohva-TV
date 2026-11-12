package com.sohva.tv.feature.sport.dictionary

import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.pairing.DictionaryNames

fun interface PairingDictionary {
    /** Called only when Today needs results, off the main thread; no network or API quota. */
    suspend fun resolve(events: List<SportEvent>): DictionaryNames

    companion object {
        val NONE: PairingDictionary = PairingDictionary { DictionaryNames.NONE }
    }
}
