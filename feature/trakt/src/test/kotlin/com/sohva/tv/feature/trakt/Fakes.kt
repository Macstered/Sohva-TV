package com.sohva.tv.feature.trakt

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.trakt.store.TraktAccountStore
import com.sohva.tv.feature.trakt.store.TraktCipher
import com.sohva.tv.feature.trakt.store.TraktPrefs

/** Prefs in a map; values as stored, so a test can check nothing is kept in plain text. */
class FakePrefs : TraktPrefs {
    val values = LinkedHashMap<String, String>()

    override fun get(key: String): String? = synchronized(values) { values[key] }

    override fun put(key: String, value: String?) {
        synchronized(values) { if (value == null) values.remove(key) else values[key] = value }
    }

    override fun keys(): Set<String> = synchronized(values) { values.keys.toSet() }
}

/** Reverses and marks the text: enough to show values are not stored as given. */
class FakeCipher : TraktCipher {
    override fun encrypt(plaintext: String): String = "enc:" + plaintext.reversed()

    override fun decrypt(value: String): String = value.removePrefix("enc:").reversed()
}

class FakeClock(var now: Long = 1_800_000_000_000L) : Clock {
    override fun wallMillis(): Long = now

    override fun monotonicNanos(): Long = now * 1_000_000
}

class FakeLog : DiagnosticsLog {
    val lines = ArrayList<String>()

    override fun info(event: String, message: String) {
        lines += "$event: $message"
    }

    override fun error(event: String, message: String?, error: Throwable?) {
        lines += "$event: $message"
    }

    override fun snapshot(): List<String> = lines.toList()
}

fun store(prefs: FakePrefs = FakePrefs()): TraktAccountStore = TraktAccountStore(prefs, FakeCipher())
